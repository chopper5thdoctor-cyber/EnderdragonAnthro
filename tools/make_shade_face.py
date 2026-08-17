#!/usr/bin/env python3
"""Rescale the ^^ face onto the shade's head, and cut a mask that fits it.

    python3 tools/make_shade_face.py

The ^^ was drawn for a vanilla enderman: an 8-unit head, so eight texels across
the face. A shade's head is 16 authored units, which is the same half-block
wide in game but twice the texels, and the old sheet stretched over it would be
eight fat squares. This writes the same expression at the size the shade's head
actually samples, so it can be repainted at the detail the rest of her has.

Both sheets are a single 16-cube at texOffs(0, 0), which fills a 64x32 sheet
exactly:

    up   16..32 / 0..16      down 32..48 / 0..16
    east  0..16 / 16..32     NORTH 16..32 / 16..32   <- the face
    west 32..48 / 16..32     south 48..64 / 16..32

That layout is the head island of shade_base.bbmodel moved up by 63 rows and
nothing else, which is the whole trick: a 16-cube at (0, 0) and a 16-cube at
(0, 63) lay their faces out identically. So the mask can be cut straight from
wherever the shade's own sheet is painted, and it lands exactly on top.

  shade_happy.png       the expression. Drawn through RenderType.eyes, so it
                        is additive and glows; black is invisible there.
  shade_happy_mask.png  an opaque plate in ordinary cutout, drawn first, that
                        hides the eyes already painted on her. Additive art
                        can only ever sit on top of them otherwise, which
                        reads as blush rather than as a face.
  art/shade_happy_guide.png   the same layout with the six faces outlined,
                        for painting against. Never shipped.
"""

import os

from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro/textures/entity")
SOURCE = os.path.join(ASSETS, "enderman_happy.png")
SHEET = os.path.join(ASSETS, "shade.png")

CUBE = 16                   # the shade's head, in authored units
SIZE = (4 * CUBE, 2 * CUBE)  # ...which is exactly one island
HEAD_V = 63                 # where that island sits on the shade's own sheet

# How far the mask spreads past a painted texel. One is enough to swallow the
# lashes' stray corners without eating into the hide around them.
DILATE = 1


def upscale(name, out):
    """The old 8-cube sheet, doubled -- which lands every face where the
    16-cube wants it, because both islands start at (0, 0)."""
    src = Image.open(os.path.join(ASSETS, name)).convert("RGBA")
    big = src.resize((src.width * 2, src.height * 2), Image.NEAREST)
    big.crop((0, 0, *SIZE)).save(out)
    return out


def slots():
    """The six face rectangles of a CUBE-sided head at texOffs(0, 0)."""
    return {"up": (CUBE, 0), "down": (2 * CUBE, 0), "east": (0, CUBE),
            "north": (CUBE, CUBE), "west": (2 * CUBE, CUBE), "south": (3 * CUBE, CUBE)}


def on_a_face():
    """Texels an island actually samples. The corners it does not are where
    the rig's old island labels ended up, and masking those is just litter."""
    return {(ox + i, oy + j) for (ox, oy) in slots().values()
            for j in range(CUBE) for i in range(CUBE)}


def painted(sheet):
    """Texels of the head island the artist has put colour on.

    Anything that is not hide: the mottle runs to luminance 22 and stays grey,
    so a threshold on brightness or on chroma catches the eyes and nothing else.
    """
    px = sheet.load()
    out = set()
    for (x, y) in on_a_face():
        r, g, b, a = px[x, y + HEAD_V]
        if not a:
            continue
        if 0.2126 * r + 0.7152 * g + 0.0722 * b > 30 or max(r, g, b) - min(r, g, b) > 12:
            out.add((x, y))
    return out


def nearest_hide(sheet, covered, x, y):
    """Real hide from as close to (x, y) as the paint allows.

    A single averaged tone would be flat, and a flat eye-shaped patch on a
    mottled face still reads as an eye. Borrowing the nearest unpainted texel
    keeps the grain running through the plate, so it disappears instead.
    """
    px = sheet.load()
    surface = on_a_face()
    for r in range(1, SIZE[0]):
        ring = [(x + dx, y + dy)
                for dy in range(-r, r + 1) for dx in range(-r, r + 1)
                if max(abs(dx), abs(dy)) == r]
        for p in sorted(ring, key=lambda q: (abs(q[1] - y), abs(q[0] - x))):
            if p in covered or p not in surface:
                continue
            r_, g_, b_, a_ = px[p[0], p[1] + HEAD_V]
            if a_:
                return (r_, g_, b_, 255)
    return (15, 15, 18, 255)


def mask(sheet, out):
    marks = painted(sheet)
    grown = {(x + dx, y + dy) for (x, y) in marks
             for dx in range(-DILATE, DILATE + 1)
             for dy in range(-DILATE, DILATE + 1)} & on_a_face()
    img = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    px = img.load()
    for (x, y) in sorted(grown):
        px[x, y] = nearest_hide(sheet, grown, x, y)
    img.save(out)
    return len(marks), len(grown)


def guide(out):
    img = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    px = img.load()
    for name, (ox, oy) in slots().items():
        edge = (255, 92, 92, 255) if name == "north" else (70, 70, 96, 255)
        for i in range(CUBE):
            for (x, y) in ((ox + i, oy), (ox + i, oy + CUBE - 1),
                           (ox, oy + i), (ox + CUBE - 1, oy + i)):
                px[x, y] = edge
    img.save(out)


def main():
    face = upscale("enderman_happy.png", os.path.join(ASSETS, "shade_happy.png"))
    sheet = Image.open(SHEET).convert("RGBA")
    marks, grown = mask(sheet, os.path.join(ASSETS, "shade_happy_mask.png"))
    guide(os.path.join(HERE, "art/shade_happy_guide.png"))
    print(f"{os.path.relpath(face, HERE)}: {SIZE[0]}x{SIZE[1]}, "
          f"a {CUBE}-unit head at texOffs(0, 0)")
    print(f"  mask covers {marks} painted texels, {grown} after dilation, "
          f"in hide borrowed from around them")
    print("  art/shade_happy_guide.png: face outlines, for painting against")


if __name__ == "__main__":
    main()
