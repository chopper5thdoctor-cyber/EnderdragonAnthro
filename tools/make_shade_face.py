#!/usr/bin/env python3
"""Cut the court's pet expression out of a second painting of her head.

    python3 tools/make_shade_face.py

A shade does not do the enderman's ^^. She closes her eyes, which is a thing
only she can do, because only she has eyes painted into her hide rather than
two glowing rectangles bolted on. Endermen keep the ^^; see enderman_happy.png.

Two sheets in, one out:

    art/femshade.png       her hide, eyes open      -- what she wears
    art/femshade_pet.png   the same sheet, eyes closed
    -> shade_pet.png       the difference, as a shell that goes over her head

Both sheets must share a hide, because the shell borrows from it: any texel the
overlay covers but does not repaint has to come back as the hide that is
already there, or the shell reads as a patch. Re-run mottle_skin.py --restyle
on one and you must re-run it on the other.

WHY A SHELL AND NOT A TEXTURE SWAP. Swapping her sheet for a second one is one
draw instead of two and tempting for that -- but it doubles the art. Per-shade
variants are coming, and four shades times two expressions is eight full-body
sheets to keep in step, against four plus one 64x32 shell shared by all of
them. The shell also cannot drift: it is cut from the difference between the
two paintings, so anything not about her eyes is transparent by construction.

WHY IT WRAPS THE WHOLE HEAD. Her eyes are painted 13..34 across a front face
that runs 16..32, so they overhang three texels onto each side of her head. An
expression on the front face alone would leave the open eyes' outer corners
showing while the rest of her closed them. The shell is a full head cube and
the overlay covers whichever faces the paint reaches.

WHY IT IS OPAQUE AND NOT ADDITIVE. The enderman's ^^ draws through
RenderType.eyes because it is replacing something that glows. Hers do not glow
-- they are paint on a hide -- so the overlay is ordinary cutout, drawn in one
pass. That also means it needs no separate mask: the plate that hides the open
eyes and the closed eyes it draws instead are the same texels.
"""

import os

from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro/textures/entity")
BASE = os.path.join(HERE, "art/femshade.png")
PET = os.path.join(HERE, "art/femshade_pet.png")
OUT = os.path.join(ASSETS, "shade_pet.png")
GUIDE = os.path.join(HERE, "art/shade_pet_guide.png")

CUBE = 16                     # her head, in authored units
SIZE = (4 * CUBE, 2 * CUBE)   # ...which is exactly one island
HEAD_V = 63                   # where that island sits on her own sheet

# How far the overlay spreads past a texel that differs. One swallows the
# lashes' stray corners without eating into the hide around them.
DILATE = 1

# What counts as paint rather than hide. The mottle tops out around luminance
# 22 and stays grey by construction, so either test alone would do; both
# together mean a white highlight survives as readily as a magenta one.
KEEP_LUM = 30
KEEP_CHROMA = 12


def slots():
    """The six face rectangles of a CUBE-sided head at texOffs(0, 0)."""
    return {"up": (CUBE, 0), "down": (2 * CUBE, 0), "east": (0, CUBE),
            "north": (CUBE, CUBE), "west": (2 * CUBE, CUBE), "south": (3 * CUBE, CUBE)}


def on_a_face():
    """Texels an island actually samples. The corners it does not are where the
    rig's old island labels ended up, and covering those is just litter."""
    return {(ox + i, oy + j) for (ox, oy) in slots().values()
            for j in range(CUBE) for i in range(CUBE)}


def is_paint(px, x, y):
    r, g, b, a = px[x, y]
    if not a:
        return False
    return (0.299 * r + 0.587 * g + 0.114 * b > KEEP_LUM
            or max(r, g, b) - min(r, g, b) > KEEP_CHROMA)


def overlay(base, pet):
    """Every texel the two paintings disagree on, drawn as the pet one.

    Grown by DILATE first, so a lash the closed eye does not reach is still
    covered rather than left poking out from under the new expression.
    """
    b, p = base.load(), pet.load()
    surface = on_a_face()
    differ = {(x, y) for (x, y) in surface
              if b[x, y + HEAD_V] != p[x, y + HEAD_V]
              or is_paint(b, x, y + HEAD_V) or is_paint(p, x, y + HEAD_V)}
    grown = {(x + dx, y + dy) for (x, y) in differ
             for dx in range(-DILATE, DILATE + 1)
             for dy in range(-DILATE, DILATE + 1)} & surface

    img = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    px = img.load()
    for (x, y) in grown:
        if p[x, y + HEAD_V][3]:
            px[x, y] = p[x, y + HEAD_V]
    img.save(OUT)
    return len(differ), len(grown)


def guide():
    img = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    px = img.load()
    for name, (ox, oy) in slots().items():
        edge = (255, 92, 92, 255) if name == "north" else (70, 70, 96, 255)
        for i in range(CUBE):
            for (x, y) in ((ox + i, oy), (ox + i, oy + CUBE - 1),
                           (ox, oy + i), (ox + CUBE - 1, oy + i)):
                px[x, y] = edge
    img.save(GUIDE)


def main():
    base = Image.open(BASE).convert("RGBA")
    pet = Image.open(PET).convert("RGBA")
    if base.size != pet.size:
        raise SystemExit("the two sheets must be the same size")
    differ, grown = overlay(base, pet)
    guide()
    faces = sorted({name for name, (ox, oy) in slots().items()
                    for j in range(CUBE) for i in range(CUBE)
                    if base.load()[ox + i, oy + j + HEAD_V]
                    != pet.load()[ox + i, oy + j + HEAD_V]})
    print(f"{os.path.relpath(OUT, HERE)}: {SIZE[0]}x{SIZE[1]}, "
          f"a {CUBE}-unit head at texOffs(0, 0)")
    print(f"  {differ} texels differ or carry paint, {grown} after dilation")
    print(f"  wraps: {', '.join(faces)}")
    print(f"  {os.path.relpath(GUIDE, HERE)}: face outlines, for painting against")


if __name__ == "__main__":
    main()
