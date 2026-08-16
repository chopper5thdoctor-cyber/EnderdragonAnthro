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

DENSITY is the resolution bump. Box UV gives one texel per model unit by
default, so a 48-unit shade gets 48 texels head to foot however big the sheet
is; making the PNG larger on its own buys nothing but empty space. Doubling the
UV rectangles as well as the sheet is what actually buys detail. Minecraft can
express that: CubeListBuilder.addBox has an overload taking texScale, so the
renderer for these will pass texScale(DENSITY, DENSITY).

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

DENSITY = 2                 # texels per model unit
RES = 128                   # sheet is DENSITY x the 64 it was authored against
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
        boxes.append([math.ceil(2 * (d + w) * DENSITY),
                      math.ceil((d + h) * DENSITY), members])
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
                for face, rect in face_rects(ox, oy, w * DENSITY, h * DENSITY,
                                             d * DENSITY,
                                             bool(e.get("mirror_uv"))).items()
            }
    return placed


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

    placed = pack(model)
    check(model, placed)
    model["resolution"] = {"width": RES, "height": RES}
    model["textures"] = embed_guide()
    model["name"] = "shade_base"

    with open(OUT, "w") as f:
        json.dump(model, f, indent=2)

    ys = [c for e in model["elements"] for c in (e["from"][1], e["to"][1])]
    tall = max(ys) - min(ys)
    print(f"read  {os.path.relpath(src, HERE)}")
    print(f"wrote {os.path.relpath(OUT, HERE)}")
    print(f"  {len(model['elements'])} cubes, {tall:.1f} units "
          f"({tall / 16:.2f} blocks, {tall / 16 * (4 / 2.9):.2f} in game)")
    print(f"  sheet {RES}x{RES} at {DENSITY} texels per unit")
    for ox, oy, bw, bh, members in sorted(placed, key=lambda p: (p[1], p[0])):
        names = "+".join(e["name"] for e in members)
        print(f"    {names:<22} ({ox:3d},{oy:3d}) {bw:3d}x{bh:<3d}")


if __name__ == "__main__":
    main()
