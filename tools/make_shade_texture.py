#!/usr/bin/env python3
"""Write the court's texture sheets — original art, painted to the shade rig.

No vanilla enderman texture ships here and none can, so this is not a copy of
one. It is a sheet laid out to `art/shade_base.bbmodel`'s own UV islands, in
the palette DESIGN.md samples, ready to paint over or to use as it stands.

Two files per shade:

  shade_<name>.png        the skin
  shade_<name>_eyes.png   the emissive pass, eyes only

...plus `shade_guide.png`, which is the same layout with every island outlined
and labelled. Open that beside the rig in Blockbench and it is obvious which
rectangle is which limb — the thing a blank sheet cannot tell you.

    python3 tools/make_shade_texture.py
"""

import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_shade_rig import PARTS, TEX_W, TEX_H, islands   # noqa: E402

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, "art")

CLEAR = (0, 0, 0, 0)
# The End palette, sampled in DESIGN.md section 2.
SKIN_DARK = (0x0B, 0x0B, 0x0E, 255)
SKIN_MID = (0x14, 0x14, 0x19, 255)
SKIN_LIT = (0x1E, 0x1E, 0x26, 255)

# One accent each, matching the colour their chat is spoken in.
COURT = {
    "vaelle":   (0xE0, 0x2B, 0x2B),
    "keshaire": (0x2B, 0x6B, 0xE0),
    "nyrelle":  (0x2B, 0xC4, 0x5A),
    "orrinne":  (0xE0, 0x8A, 0x1E),
}


def slots(u, v, w, h, d):
    """Box UV, the way ModelPart.Cube lays it out."""
    return {
        "down":  (u + d, v, w, d),
        "up":    (u + d + w, v, w, d),
        "east":  (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west":  (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def part_boxes():
    for name, _p, _piv, lo, hi, (u, v) in PARTS:
        w, h, d = (hi[i] - lo[i] for i in range(3))
        yield name, u, v, w, h, d


def skin(accent):
    """A shaded body in near-black, with the accent along the spine and crown."""
    sheet = Image.new("RGBA", (TEX_W, TEX_H), CLEAR)
    px = sheet.load()
    for name, u, v, w, h, d in part_boxes():
        for face, (x0, y0, fw, fh) in slots(u, v, w, h, d).items():
            # Front and top catch the light; the back and underside do not.
            base = {"north": SKIN_LIT, "up": SKIN_LIT,
                    "south": SKIN_DARK, "down": SKIN_DARK}.get(face, SKIN_MID)
            for x in range(x0, x0 + fw):
                for y in range(y0, y0 + fh):
                    # A little noise so it does not read as flat plastic.
                    shift = 3 if (x * 7 + y * 5) % 11 == 0 else 0
                    px[x, y] = (min(base[0] + shift, 255),
                                min(base[1] + shift, 255),
                                min(base[2] + shift, 255), 255)
            # The accent: a stripe down the spine and a band across the crown.
            if name == "body" and face == "south":
                for y in range(y0, y0 + fh):
                    px[x0 + fw // 2, y] = accent + (255,)
            if name == "head" and face == "up":
                for x in range(x0, x0 + fw):
                    px[x, y0 + 1] = accent + (255,)
    return sheet


def eyes(accent):
    """The emissive pass. Eyes only, in the shade's own colour."""
    sheet = Image.new("RGBA", (TEX_W, TEX_H), CLEAR)
    px = sheet.load()
    for name, u, v, w, h, d in part_boxes():
        if name != "head":
            continue
        x0, y0, fw, fh = slots(u, v, w, h, d)["north"]
        row = y0 + 3
        for dx in (1, 2, 5, 6):
            px[x0 + dx, row] = accent + (255,)
        # A hotter core, so it blooms under the fullbright pass.
        for dx in (1, 6):
            px[x0 + dx, row] = tuple(min(c + 60, 255) for c in accent) + (255,)
    return sheet


def guide():
    """Every island outlined and named, for painting against."""
    sheet = Image.new("RGBA", (TEX_W, TEX_H), (0x18, 0x18, 0x1E, 255))
    draw = ImageDraw.Draw(sheet)
    tints = [(0xE0, 0x2B, 0x2B), (0x2B, 0x6B, 0xE0), (0x2B, 0xC4, 0x5A),
             (0xE0, 0x8A, 0x1E), (0xC0, 0x60, 0xE0), (0x30, 0xC0, 0xC0)]
    for i, (name, x0, y0, x1, y1) in enumerate(islands()):
        draw.rectangle([x0, y0, x1 - 1, y1 - 1], outline=tints[i % len(tints)] + (255,))
    # Face boundaries inside each island, fainter.
    px = sheet.load()
    for name, u, v, w, h, d in part_boxes():
        for _face, (fx, fy, fw, fh) in slots(u, v, w, h, d).items():
            for x in range(fx, fx + fw):
                if 0 <= fy < TEX_H:
                    px[x, fy] = (0x50, 0x50, 0x5A, 255)
            for y in range(fy, fy + fh):
                if 0 <= fx < TEX_W:
                    px[fx, y] = (0x50, 0x50, 0x5A, 255)
    return sheet


def main():
    os.makedirs(OUT, exist_ok=True)
    written = []
    for name, accent in COURT.items():
        for suffix, image in (("", skin(accent)), ("_eyes", eyes(accent))):
            path = os.path.join(OUT, f"shade_{name}{suffix}.png")
            image.save(path)
            written.append(path)
    path = os.path.join(OUT, "shade_guide.png")
    guide().save(path)
    written.append(path)

    for path in written:
        image = Image.open(path)
        print(f"wrote {os.path.relpath(path, HERE):<32} {image.width}x{image.height}")
    print(f"\nlayout ({TEX_W}x{TEX_H}), matching art/shade_base.bbmodel:")
    for name, x0, y0, x1, y1 in islands():
        print(f"  {name:<10} ({x0:2d},{y0:2d}) -> ({x1:2d},{y1:2d})")


if __name__ == "__main__":
    main()
