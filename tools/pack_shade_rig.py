#!/usr/bin/env python3
"""Repack a hand-built shade rig onto a clean, higher-resolution sheet.

Takes the artist's .bbmodel, leaves every cube's geometry exactly where it is,
and rewrites only the UV: each cube gets its own island, packed so nothing
overlaps and nothing falls off the sheet, at twice the texel density.

    python3 tools/pack_shade_rig.py art/shade_source.bbmodel

Why this exists: hand-placed box UV drifts. The rig this was written for had
four cubes at negative offsets -- running off the top-left corner of the sheet
-- and several islands sitting on top of each other, which no amount of
painting can fix.

It also draws the rig oversize, which is the only way box UV gets more texels.
One texel per model unit is welded into the format and Blockbench enforces it:
raise the sheet on its own and it resizes the model to match. dragon_form
already works around this -- 142 units on a 512 sheet, AUTHORED_SCALE 4, undone
by TRUE_SCALE at render. The shades do the same at 2. See AUTHOR_SCALE.

Mirrored pairs keep sharing one island, which is what the artist meant by
giving them the same offset and setting mirror_uv.
"""

import base64
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, "art/shade_base.bbmodel")
GUIDE = os.path.join(HERE, "art/shade_guide.png")

# Box UV is welded to one texel per model unit, and Blockbench enforces it --
# push the sheet up on its own and it resizes the model to match. There is no
# way round that in the format, so the way to buy detail is to author the model
# oversize and divide it back down at render time.
#
# dragon_form.bbmodel already does exactly this: 142 units on a 512 sheet with
# AUTHORED_SCALE 4, and TRUE_SCALE = 1/4 undoes it. The shades follow the same
# pattern at 2, which puts a 47.8-unit rig at 95.6 and doubles its texels.
#
# An earlier cut of this file tried to be clever and wrote UV rectangles at
# twice the cube size, meaning to lean on CubeListBuilder's texScale overload.
# That is real in Java and useless in Blockbench, which will not author against
# it -- so the artist got a rig that fought them every time they touched the
# resolution.
AUTHOR_SCALE = 2            # geometry is drawn this many times oversize
RES = 128                   # ...which needs a sheet this big at 1 texel/unit
PAD = 1                     # a texel of gutter, so filtering cannot bleed


def bb_slots(u, v, w, h, d):
    """The six face rectangles, in Blockbench's naming.

    Not the naming in make_textures.box_slots: the four sides agree but "up"
    and "down" are the other way round there. Verified against Blockbench's own
    output for dragon_form.bbmodel and for the shade rig.
    """
    return {
        "up":    (u + d, v, w, d),
        "down":  (u + d + w, v, w, d),
        "east":  (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west":  (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def face_rects(u, v, w, h, d, mirror):
    """Face rectangles the way Blockbench writes them, orientation included.

    The two square slots are stored flipped, and a mirrored cube swaps east
    with west and reverses x on every face. Both were read back off the
    artist's own file rather than guessed -- every one of the twelve rectangles
    in that rig reproduces exactly.
    """
    s = bb_slots(u, v, w, h, d)
    ux, uy, uw, ud = s["up"]
    dx, dy, dw, dd = s["down"]
    base = {
        "north": _rect(s["north"]),
        "east":  _rect(s["east"]),
        "south": _rect(s["south"]),
        "west":  _rect(s["west"]),
        "up":    [ux + uw, uy + ud, ux, uy],       # both axes reversed
        "down":  [dx + dw, dy, dx, dy + dd],       # x reversed, y not
    }
    if not mirror:
        return base
    flip = lambda r: [r[2], r[1], r[0], r[3]]      # noqa: E731
    return {
        "north": flip(base["north"]), "south": flip(base["south"]),
        "east": flip(base["west"]), "west": flip(base["east"]),
        "up": flip(base["up"]), "down": flip(base["down"]),
    }


def _rect(slot):
    x, y, w, h = slot
    return [x, y, x + w, y + h]


def dims(e):
    return tuple(e["to"][i] - e["from"][i] for i in range(3))


def source_offset(e):
    """The cube's current island origin, however the file spells it."""
    if "uv_offset" in e:
        return tuple(e["uv_offset"])
    w, h, d = dims(e)
    n = e["faces"]["north"]["uv"]
    return (min(n[0], n[2]) - d, min(n[1], n[3]) - d)


def pack(model):
    """Give every distinct island a home. Shelves, tallest first."""
    els = model["elements"]
    # Cubes sharing an offset AND a size were meant to share texture.
    groups = {}
    for e in els:
        groups.setdefault((source_offset(e), dims(e)), []).append(e)

    boxes = []
    for key, members in groups.items():
        w, h, d = key[1]
        boxes.append([math.ceil(2 * (d + w)), math.ceil(d + h), members])
    boxes.sort(key=lambda b: -b[1])

    x = y = shelf = 0
    placed = []
    for bw, bh, members in boxes:
        if x + bw > RES:
            x, y = 0, y + shelf + PAD
            shelf = 0
        if y + bh > RES:
            raise SystemExit(f"will not fit on {RES}x{RES}: needs a taller sheet")
        placed.append((x, y, bw, bh, members))
        x += bw + PAD
        shelf = max(shelf, bh)

    for ox, oy, _bw, _bh, members in placed:
        for e in members:
            w, h, d = dims(e)
            e["uv_offset"] = [ox, oy]
            e["faces"] = {
                face: {"uv": rect, "texture": 0}
                for face, rect in face_rects(ox, oy, w, h, d,
                                             bool(e.get("mirror_uv"))).items()
            }
    return placed


def enlarge(model):
    """Draw the rig oversize, so box UV gives it more texels.

    Geometry only -- rotations are angles and do not scale, and the UV was
    packed in source units before this runs, which is what keeps one texel per
    ORIGINAL unit and therefore AUTHOR_SCALE texels per final unit.
    """
    for e in model["elements"]:
        for key in ("from", "to", "origin"):
            if key in e:
                e[key] = [round(c * AUTHOR_SCALE, 4) for c in e[key]]
    for g in model.get("groups", []):
        if "origin" in g:
            g["origin"] = [round(c * AUTHOR_SCALE, 4) for c in g["origin"]]
    _scale_outliner(model.get("outliner", []))


def _scale_outliner(nodes):
    for n in nodes:
        if isinstance(n, str):
            continue
        if "origin" in n:
            n["origin"] = [round(c * AUTHOR_SCALE, 4) for c in n["origin"]]
        _scale_outliner(n.get("children", []))


def check(model, placed):
    """Nothing negative, nothing off the sheet, nothing overlapping."""
    for ox, oy, bw, bh, members in placed:
        if ox < 0 or oy < 0:
            raise SystemExit(f"{members[0]['name']} packed to a negative offset")
        if ox + bw > RES or oy + bh > RES:
            raise SystemExit(f"{members[0]['name']} runs off the sheet")
    for i, a in enumerate(placed):
        for b in placed[i + 1:]:
            if (a[0] < b[0] + b[2] and b[0] < a[0] + a[2]
                    and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]):
                raise SystemExit(f"{a[4][0]['name']} overlaps {b[4][0]['name']}")


def embed_guide():
    if not os.path.exists(GUIDE):
        return []
    with open(GUIDE, "rb") as f:
        data = base64.b64encode(f.read()).decode("ascii")
    return [{
        "name": "shade_guide.png", "path": "", "folder": "", "namespace": "",
        "id": "0", "group": "", "scope": 0,
        "width": RES, "height": RES, "uv_width": RES, "uv_height": RES,
        "particle": False, "use_as_default": False, "layers_enabled": False,
        "sync_to_project": "", "file_format": "png", "render_mode": "default",
        "render_sides": "auto", "wrap_mode": "limited", "pbr_channel": "color",
        "visible": True, "internal": True, "saved": False,
        "uuid": "591e6086-d978-403b-b1bc-3c98fd9a50f6",
        "source": "data:image/png;base64," + data,
    }]


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "art/shade_source.bbmodel")
    with open(src) as f:
        model = json.load(f)

    placed = pack(model)          # UV first, in source units
    check(model, placed)
    enlarge(model)                # then blow the geometry up around it
    model["resolution"] = {"width": RES, "height": RES}
    model["textures"] = embed_guide()
    model["name"] = "shade_base"

    with open(OUT, "w") as f:
        json.dump(model, f, indent=2)

    ys = [c for e in model["elements"] for c in (e["from"][1], e["to"][1])]
    tall = max(ys) - min(ys)
    print(f"read  {os.path.relpath(src, HERE)}")
    print(f"wrote {os.path.relpath(OUT, HERE)}")
    real = tall / AUTHOR_SCALE
    print(f"  {len(model['elements'])} cubes, {tall:.1f} units drawn "
          f"= {real:.1f} true ({real / 16:.2f} blocks, "
          f"{real / 16 * (4 / 2.9):.2f} in game)")
    print(f"  sheet {RES}x{RES}, drawn {AUTHOR_SCALE}x oversize "
          f"= {AUTHOR_SCALE} texels per final unit")
    print(f"  the renderer must apply a scale of 1/{AUTHOR_SCALE} to undo it")
    for ox, oy, bw, bh, members in sorted(placed, key=lambda p: (p[1], p[0])):
        names = "+".join(e["name"] for e in members)
        print(f"    {names:<22} ({ox:3d},{oy:3d}) {bw:3d}x{bh:<3d}")


if __name__ == "__main__":
    main()
