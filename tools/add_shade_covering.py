#!/usr/bin/env python3
"""Give the shade a clothing layer, and a sheet big enough to paint it on.

Usage:  python3 tools/add_shade_covering.py
        python3 tools/shade_to_java.py

Runs ONCE. It refuses if it finds its own output, because running it twice would
put a covering on the coverings.

## What it does

Two things that have to happen together.

**The canvas doubles**, 128x128 to 256x256. Existing art does not move: texOffs
is an absolute pixel coordinate, so growing the sheet down and to the right
leaves every island exactly where it was and simply adds room. Both sheets that
the body model reads are grown -- shade.png and shade_glow.png, which share the
model's UVs and would come apart if only one of them changed size. The face
sheets are a separate 64x32 layer of their own and are left alone.

**Every cube gains a `<name>Covering`** in the same bone, the same pose, and the
same lean, inflated by INFLATE so it sits just outside the skin rather than
z-fighting with it. Each gets its own island in the new half of the sheet, and
every texel of it starts transparent -- which is the useful default, because
transparent draws as nothing on a cutout render type. Until the coverings are
painted the shade looks exactly as she did.

The inflation is real geometry rather than a CubeDeformation. Blockbench's
`inflate` would be the idiomatic choice, but verify_model.py reconstructs every
cube by reading `addBox` out of the generated Java and comparing corners against
the .bbmodel, and a seventh argument would walk straight past its regex -- the
check would keep passing while measuring the wrong box. Real geometry keeps the
two descriptions identical, and it is easier to nudge by hand besides.

## Naming

`headCovering`, `right_armCovering`, `body1Covering`, and so on: the existing
name with the word appended, exactly as asked. It reads oddly against the rig's
snake_case, which is worth one sentence of warning rather than a silent
correction to `head_covering` -- the point of the suffix is that it sorts next
to the piece it covers in Blockbench's outliner, and it does.
"""
import base64
import io
import json
import os
import shutil
import sys
import uuid

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pack_shade_rig import dims, face_rects, source_offset   # noqa: E402

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RIG = os.path.join(HERE, "art/shade_base.bbmodel")
TEXTURES = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro"
                              "/textures/entity")
#: Both sheets the BODY model samples. The face sheets are their own layer at
#: their own size and must not be touched -- growing them would misplace every
#: expression she has.
BODY_SHEETS = ("shade.png", "shade_glow.png")

SUFFIX = "Covering"
#: The new canvas. Doubling keeps every existing texOffs valid unchanged.
CANVAS = 256
#: How far a covering stands off the skin, in packed units -- which is one texel,
#: since islands are laid out at packed dimensions. Half a unit on each side, so
#: every island dimension stays a whole number and the pixel art stays square.
INFLATE = 0.5


def islands_of(element):
    """The rectangle this cube reads, as (x, y, w, h) of the whole island.

    PACKED dimensions from a source-unit offset. That mixture is not a choice
    made here; it is what the shipped sheet measurably is.
    """
    w, h, d = dims(element)
    u, v = (element["uv_offset"] if "uv_offset" in element
            else source_offset(element))
    return int(u), int(v), int(2 * (d + w)), int(d + h)


def occupancy(model, size):
    """Every sheet pixel some cube already reads."""
    used = [[False] * size for _ in range(size)]
    for e in model["elements"]:
        u, v, iw, ih = islands_of(e)
        for x in range(u, u + iw):
            for y in range(v, v + ih):
                if 0 <= x < size and 0 <= y < size:
                    used[y][x] = True
    return used


def free_spot(used, iw, ih, size, below):
    """Somewhere the island fits, preferring the new ground below `below`.

    New art goes in the new space, so that opening the sheet shows the skin
    where it always was and the wardrobe underneath it. Falls back to anywhere
    it fits rather than failing, since a cube added later may not have room in
    the bottom half.
    """
    for floor in (below, 0):
        for y in range(floor, size - ih + 1):
            for x in range(size - iw + 1):
                if all(not used[yy][xx]
                       for yy in range(y, y + ih) for xx in range(x, x + iw)):
                    for yy in range(y, y + ih):
                        for xx in range(x, x + iw):
                            used[yy][xx] = True
                    return x, y
    return None


def covering(element):
    """A duplicate of one cube, inflated, ready for its own island."""
    made = json.loads(json.dumps(element))
    made["name"] = element["name"] + SUFFIX
    made["uuid"] = str(uuid.uuid4())
    made["from"] = [round(c - INFLATE, 4) for c in element["from"]]
    made["to"] = [round(c + INFLATE, 4) for c in element["to"]]
    # origin and rotation are carried over untouched: the covering has to lean
    # the way the limb leans and turn about the same pivot, or a bent elbow
    # leaves the sleeve behind.
    return made


def grow(path, size):
    """Same picture, bigger canvas, nothing moved."""
    old = Image.open(path).convert("RGBA")
    if old.size == (size, size):
        return old, False
    bigger = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bigger.paste(old, (0, 0))
    bigger.save(path)
    return bigger, True


def main():
    with open(RIG) as f:
        model = json.load(f)

    if any(e["name"].endswith(SUFFIX) for e in model["elements"]):
        sys.exit(f"ERROR: the rig already has {SUFFIX} cubes. Running again "
                 "would put a covering on the coverings. Edit the rig instead.")

    was = model["resolution"]["width"], model["resolution"]["height"]
    model["resolution"] = {"width": CANVAS, "height": CANVAS}

    for name in BODY_SHEETS:
        path = os.path.join(TEXTURES, name)
        _, changed = grow(path, CANVAS)
        print(f"  {name:22s} {was[0]}x{was[1]} -> {CANVAS}x{CANVAS}"
              f"{'' if changed else '  (already)'}")

    elements = {e["uuid"]: e for e in model["elements"]}
    used = occupancy(model, CANVAS)
    made = []

    # The outliner is walked rather than the element list, because a covering
    # belongs in the same BONE as the thing it covers -- a sleeve that is not
    # parented to the arm does not move with it.
    def walk(node):
        for child in list(node.get("children", [])):
            if isinstance(child, str):
                source = elements.get(child)
                if source is None or source["name"].endswith(SUFFIX):
                    continue
                dressed = covering(source)
                w, h, d = dims(dressed)
                iw, ih = int(2 * (d + w)), int(d + h)
                spot = free_spot(used, iw, ih, CANVAS, was[1])
                if spot is None:
                    sys.exit(f"ERROR: no room on a {CANVAS}x{CANVAS} sheet for "
                             f"{dressed['name']}'s {iw}x{ih} island")
                dressed["uv_offset"] = list(spot)
                dressed["faces"] = {
                    face: {"uv": rect, "texture": 0}
                    for face, rect in face_rects(spot[0], spot[1], w, h, d,
                                                 bool(dressed.get("mirror_uv"))).items()
                }
                model["elements"].append(dressed)
                node["children"].append(dressed["uuid"])
                made.append((dressed["name"], spot, iw, ih))
            else:
                walk(child)

    for top in model["outliner"]:
        walk(top)

    for name, spot, iw, ih in made:
        print(f"  {name:24s} island {str(spot):>12s}  {iw}x{ih}")

    sheet = Image.open(os.path.join(TEXTURES, BODY_SHEETS[0])).convert("RGBA")
    buffer = io.BytesIO()
    sheet.save(buffer, format="PNG")
    model["textures"][0]["source"] = ("data:image/png;base64,"
                                      + base64.b64encode(buffer.getvalue()).decode())

    shutil.copy(RIG, RIG + ".pre-covering")
    with open(RIG, "w") as f:
        json.dump(model, f, indent=2)
    print(f"\nadded {len(made)} covering cubes on a {CANVAS}x{CANVAS} sheet; "
          "every one of them transparent, so nothing looks different yet")
    print(f"  the rig as it was is beside it, as {os.path.basename(RIG)}.pre-covering")


if __name__ == "__main__":
    main()
