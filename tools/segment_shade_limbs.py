#!/usr/bin/env python3
"""Cut the shade's arms and legs in two, at the elbow and the knee.

Usage:  python3 tools/segment_shade_limbs.py
        python3 tools/shade_to_java.py

Runs ONCE. It refuses if it finds its own output, rather than trusting anyone
to remember that cutting halves in half is not what was wanted.

## What it touches, and why it is not shade_source.bbmodel

It cuts `art/shade_base.bbmodel`, the packed rig -- not the source that rig is
supposed to be packed from. That is deliberate and it is worth writing down,
because it looks like the wrong file.

`art/shade_source.bbmodel` and the committed `art/shade_base.bbmodel` have
drifted. Re-packing the source today produces a different shade: an extra piece
(`cube`, a rotated collar), a bosom of 4x4x4 rather than 16x8x8, and arms a unit
wider. Somebody edited one and not the other. The packed rig is the one that
ships, the one ShadeModel.java is generated from, and the one shade.png is
painted against, so it is the one with the better claim -- and cutting
the source instead would have quietly reshaped the whole body on the way past.

The drift is real and is not fixed here. Fixing it means deciding which of the
two is wanted, which is the artist's call and not a converter's.

## The three things that have to stay in step

**Geometry.** The artist's limbs lean: the arms splay 21 degrees, the legs toe
out 1.5. That lean is on the CUBE, not on the bone, which looks like an accident
and is load-bearing -- copyPose writes xRot/yRot/zRot onto the six named bones
every frame, so anything parked on a bone is overwritten by vanilla's pose. The
lean survives because it is one level down, on a sub-part copyPose has never
heard of.

So the elbow is not the middle of the cube's y range. It is where that middle
ENDS UP once the lean is applied about the shoulder:

    E = R(C - O) + O            C is the centre of the split plane
    lower cube = the old lower half, moved so its top centre sits on E
    lower bone = a child group pivoted at E, rotation zero

which lands every corner exactly where the un-split limb had it. Checked, not
asserted: the rest pose moves by less than a thousandth of a unit.

**UV.** The upper half needs nothing at all, which is worth knowing before
reaching for the packer. Box UV lays a cube's side faces out at (u+d, v+d) with
height h, so halving h at the SAME texOffs reads exactly the top band of the
island the whole limb used to read. The upper half is therefore already correct,
already painted, and already seamless with itself.

Only the lower half needs an island, and it gets one in free space -- the sheet
is 128x128 and a third of it is empty. Nothing that was already placed moves,
which matters more here than tidiness: re-packing was tried first and it moves
every island in the sheet, so the entire hand-painted skin has to be lifted and
re-laid to follow. That is a lot of ways to lose somebody's painting in order to
add two rectangles.

(For anyone who does reach for the packer later: islands are read at the PACKED
box dimensions, from offsets stored in source units. Not both in the same units.
Measured off the shipped sheet -- computing them at source scale finds 1986 of
the 8717 painted texels, and at packed scale finds 8068 of them. make_shade_
texture.py assumes source scale, so the starter sheets it generates do not line
up with the rig either. That is a real bug and it is not this script's to fix.)

**Paint.** The lower half's new island is filled from the old one: the four side
faces take the BOTTOM band of the face they came from, which is the other half
of the same continuous run of pixels the upper kept, so the joint is seamless
because the paint across it was never cut. The `down` cap -- the hand, the foot
-- comes across whole. The one face that never existed to be painted is the
lower's `up`, the inside of the elbow; it takes the parent's `down` so that a
bent arm shows skin rather than a hole.
"""
import base64
import io
import json
import math
import os
import shutil
import sys
import uuid

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pack_shade_rig import (bb_slots, dims, face_rects, source_offset,   # noqa: E402
                            RES)

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RIG = os.path.join(HERE, "art/shade_base.bbmodel")
SHIPPED = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro"
                             "/textures/entity/shade.png")

#: bone -> (new child bone, where along the limb to cut, 0 = top, 1 = bottom)
#:
#: Halfway on both. A knee usually sits a little above the midpoint on a real
#: leg, but this rig's legs already start below a long body -- a defensible
#: starting point beats a guess dressed up as anatomy, and it is one number to
#: move.
LIMBS = {
    "right_arm": ("right_forearm", 0.5),
    "left_arm": ("left_forearm", 0.5),
    "right_leg": ("right_shin", 0.5),
    "left_leg": ("left_shin", 0.5),
}


def rotate_z(point, pivot, degrees):
    """Blockbench's z rotation, the only axis these limbs lean on."""
    rad = math.radians(degrees)
    cos, sin = math.cos(rad), math.sin(rad)
    dx, dy = point[0] - pivot[0], point[1] - pivot[1]
    return [pivot[0] + dx * cos - dy * sin,
            pivot[1] + dx * sin + dy * cos,
            point[2]]


def cut(element, child_name, fraction):
    """Split one limb; returns (lower element, lower bone)."""
    lo, hi = list(element["from"]), list(element["to"])
    spin = element.get("rotation") or [0.0, 0.0, 0.0]
    if any(abs(t) > 1e-9 for t in spin[:2]):
        sys.exit(f"ERROR: {element['name']} leans on x or y as well as z; "
                 "this only knows how to cut a z lean")
    lean = spin[2]
    pivot = element.get("origin") or [0.0, 0.0, 0.0]

    split = hi[1] - (hi[1] - lo[1]) * fraction
    centre = [(lo[0] + hi[0]) / 2.0, split, (lo[2] + hi[2]) / 2.0]
    joint = rotate_z(centre, pivot, lean)
    shift = [joint[i] - centre[i] for i in range(3)]

    lower = json.loads(json.dumps(element))
    lower["name"] = child_name
    lower["uuid"] = str(uuid.uuid4())
    lower["origin"] = [round(c, 4) for c in joint]
    lower["from"] = [round(lo[0] + shift[0], 4), round(lo[1] + shift[1], 4),
                     round(lo[2] + shift[2], 4)]
    lower["to"] = [round(hi[0] + shift[0], 4), round(split + shift[1], 4),
                   round(hi[2] + shift[2], 4)]
    # A fresh island. Left as it was, pack() groups the two halves together --
    # they have identical dimensions and the copy carries the same offset -- and
    # both halves would sample the same texels. Nudged by the limb's own height
    # it groups with its twin on the other side, which IS what should share.
    base = element["uv_offset"] if "uv_offset" in element else source_offset(element)
    lower["uv_offset"] = [base[0], base[1] + int(hi[1] - lo[1])]

    element["from"] = [lo[0], round(split, 4), lo[2]]

    bone = {
        "name": child_name, "uuid": str(uuid.uuid4()),
        "origin": [round(c, 4) for c in joint], "rotation": [0, 0, 0],
        "export": True, "isOpen": True, "locked": False, "visibility": True,
        "autouv": 0, "color": 0, "mirror_uv": bool(element.get("mirror_uv")),
        "shade": True, "reset": False, "children": [],
    }
    return lower, bone


def islands(element):
    """The six face rectangles this cube reads, in sheet pixels.

    PACKED dimensions, source-unit offset. That mixture is not a choice made
    here -- it is what the shipped sheet actually is, measured against it.
    """
    w, h, d = dims(element)
    u, v = (element["uv_offset"] if "uv_offset" in element
            else source_offset(element))
    return {face: tuple(int(round(c)) for c in rect)
            for face, rect in bb_slots(u, v, w, h, d).items()}


def occupancy(model):
    """Every sheet pixel some cube already reads."""
    used = [[False] * RES for _ in range(RES)]
    for e in model["elements"]:
        w, h, d = dims(e)
        u, v = e["uv_offset"] if "uv_offset" in e else source_offset(e)
        for x in range(int(u), int(u + 2 * (d + w))):
            for y in range(int(v), int(v + d + h)):
                if 0 <= x < RES and 0 <= y < RES:
                    used[y][x] = True
    return used


def free_spot(used, iw, ih):
    """Top-left-most place an island of this size fits with nothing under it."""
    for y in range(RES - ih + 1):
        for x in range(RES - iw + 1):
            if all(not used[yy][xx]
                   for yy in range(y, y + ih) for xx in range(x, x + iw)):
                for yy in range(y, y + ih):
                    for xx in range(x, x + iw):
                        used[yy][xx] = True
                return x, y
    return None


def repaint(sheet, parent_faces, lower_faces):
    """Fill the lower half's island off the parent's, band by band."""
    for face, (x, y, w, h) in lower_faces.items():
        src = parent_faces.get(face)
        if src is None:
            continue
        sx, sy, sw, sh = src
        if face == "up":
            # The inside of the elbow. Never painted, because until now there
            # was no elbow; the hand end is the closest thing to skin.
            sx, sy, sw, sh = parent_faces["down"]
        elif face != "down":
            sy = sy + (sh - h)             # the bottom band, the upper kept the top
            sh = h
        piece = sheet.crop((sx, sy, sx + sw, sy + sh))
        if piece.size != (w, h):
            piece = piece.resize((w, h), Image.NEAREST)
        sheet.paste(piece, (x, y))


def main():
    with open(RIG) as f:
        model = json.load(f)

    groups = {g["uuid"]: g for g in model.get("groups", [])}
    elements = {e["uuid"]: e for e in model["elements"]}
    have = {g.get("name") for g in model.get("groups", [])}
    already = [child for _, (child, _) in LIMBS.items() if child in have]
    if already:
        sys.exit(f"ERROR: the rig already carries {already}. The limbs are cut; "
                 "cutting again would halve the halves. Edit the rig instead.")

    sheet = Image.open(SHIPPED).convert("RGBA")
    used = occupancy(model)
    placed = {}
    pending = []

    for node in model["outliner"]:
        name = groups.get(node["uuid"], {}).get("name")
        if name not in LIMBS:
            continue
        child_name, fraction = LIMBS[name]
        target = next((elements[c] for c in node["children"]
                       if isinstance(c, str) and elements[c]["name"] == name), None)
        if target is None:
            sys.exit(f"ERROR: bone {name} has no cube of its own name to cut")

        parent_faces = islands(target)
        # Which island the PARENT reads, so the halves inherit exactly the
        # sharing the whole limbs had. Keyed on size instead, the two shins
        # were merged onto one island -- the arms genuinely share theirs and
        # the legs genuinely do not, and a shin painted twice ends up wearing
        # the other leg.
        parent_key = tuple(target["uv_offset"] if "uv_offset" in target
                           else source_offset(target))
        lower, bone = cut(target, child_name, fraction)
        pending.append((name, target, lower, bone, node, parent_faces, parent_key))

    for name, target, lower, bone, node, parent_faces, parent_key in pending:
        w, h, d = dims(lower)
        iw, ih = int(2 * (d + w)), int(d + h)
        # Mirrored twins share an island, exactly as the arms already do: the
        # same paint, read the other way round.
        spot = placed.get(parent_key) or free_spot(used, iw, ih)
        if spot is None:
            sys.exit(f"ERROR: no room on the {RES}x{RES} sheet for {lower['name']}'s "
                     f"{iw}x{ih} island")
        placed[parent_key] = spot
        lower["uv_offset"] = list(spot)
        lower["faces"] = {
            face: {"uv": rect, "texture": 0}
            for face, rect in face_rects(spot[0], spot[1], w, h, d,
                                         bool(lower.get("mirror_uv"))).items()
        }
        repaint(sheet, parent_faces, islands(lower))

        model["elements"].append(lower)
        model["groups"].append(bone)
        node["children"].append({"uuid": bone["uuid"], "isOpen": True,
                                 "children": [lower["uuid"]]})
        print(f"  {name:10s} -> {lower['name']:14s} joint "
              f"({bone['origin'][0]:7.2f},{bone['origin'][1]:7.2f},"
              f"{bone['origin'][2]:5.2f})   island {spot} {iw}x{ih}")

    sheet.save(SHIPPED)
    # The rig carries its own sheet so it opens painted, and verify_model.py
    # checks the two are the same pixels; they move together or not at all.
    buffer = io.BytesIO()
    sheet.save(buffer, format="PNG")
    model["textures"][0]["source"] = ("data:image/png;base64,"
                                      + base64.b64encode(buffer.getvalue()).decode())

    shutil.copy(RIG, RIG + ".pre-cut")
    with open(RIG, "w") as f:
        json.dump(model, f, indent=2)
    print(f"cut {len(pending)} limbs; {len(placed)} new islands, "
          f"nothing already on the sheet moved")


if __name__ == "__main__":
    main()
