#!/usr/bin/env python3
"""Lay the wing membranes out flat and render them, the way Minecraft samples them.

This exists because a wing can be geometrically perfect and still render with
its texture running the wrong way, and `verify_model.py` will happily pass it —
that verifier checks where the corners are, not what is painted on them. The
membranes went through two builds reading "ng][wi" instead of "[wing]" before
anyone could see why.

A correct wing is one continuous membrane: a central spar, struts fanning out,
and the scalloped trailing edge flowing unbroken from the body to the tip. If
the two panels meet thin-end-to-thin-end with a seam down the middle, the u
direction is reversed on one of them — see MIRROR_OVERRIDE in
bbmodel_to_java.py.

    python3 tools/preview_wings.py            # writes art/wing_preview.png
"""

import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bbmodel_to_java import wants_mirror  # noqa: E402  (needs the path above)

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BBMODEL = os.path.join(HERE, "art/dragon_form.bbmodel")
TEXTURE = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro"
                             "/textures/entity/dragon_form.png")
OUT = os.path.join(HERE, "art/wing_preview.png")

SCALE = 3
PANELS = {
    "left": ["wing_membrane_left", "wing_tip_membrane_left"],
    "right": ["wing_membrane_right", "wing_tip_membrane_right"],
}


def cubes_by_name(model):
    return {e.get("name"): e for e in model["elements"] if e.get("type", "cube") == "cube"}


def render(tex, cubes, names, span):
    """One wing, seen face-on, in the plane the membranes lie in."""
    lo, hi = span
    width, depth = (hi - lo) * SCALE, 64 * SCALE
    img = Image.new("RGBA", (width, depth), (255, 0, 255, 255))

    for name in names:
        e = cubes[name]
        (x0, _, z0), (x1, _, z1) = e["from"], e["to"]
        w, d = x1 - x0, z1 - z0
        u, v = e["uv_offset"]

        # ModelPart.Cube box UV: the art slot is [u+d, u+d+w]; u runs from the
        # box origin to origin+size, and `mirror` swaps those two ends.
        f, i = x0, x1
        if wants_mirror(e):
            f, i = i, f
        u1, u2 = u + d, u + d + w
        v1, v2 = v, v + d

        for px in range(width):
            wx = px / SCALE + lo
            if not min(x0, x1) <= wx < max(x0, x1):
                continue
            tu = u1 + (wx - f) / (i - f) * (u2 - u1)
            for py in range(depth):
                wz = py / SCALE
                if not z0 <= wz < z1:
                    continue
                tv = v1 + (wz - z1) / (z0 - z1) * (v2 - v1)
                c = tex.getpixel((int(tu) % tex.width, int(tv) % tex.height))
                if c[3] > 0:
                    img.putpixel((px, py), c)
    return img


def main():
    model = json.load(open(BBMODEL))
    cubes = cubes_by_name(model)
    tex = Image.open(TEXTURE).convert("RGBA")

    missing = [n for ns in PANELS.values() for n in ns if n not in cubes]
    if missing:
        sys.exit("missing cubes: " + ", ".join(missing))

    spans, rows = {}, []
    for side, names in PANELS.items():
        xs = [c for n in names for c in (cubes[n]["from"][0], cubes[n]["to"][0])]
        spans[side] = (min(xs), max(xs))
    for side, names in PANELS.items():
        rows.append(render(tex, cubes, names, spans[side]))

    gap = 12
    sheet = Image.new("RGBA", (max(r.width for r in rows),
                               sum(r.height for r in rows) + gap), (30, 30, 30, 255))
    y = 0
    for row in rows:
        sheet.paste(row, (0, y))
        y += row.height + gap
    sheet.save(OUT)
    print("wrote", os.path.relpath(OUT, HERE))
    print("top = left wing, bottom = right wing. Each should be one unbroken")
    print("membrane, and the two should be mirror images of each other.")


if __name__ == "__main__":
    main()
