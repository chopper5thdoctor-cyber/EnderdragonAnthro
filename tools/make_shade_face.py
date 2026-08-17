#!/usr/bin/env python3
"""Cut the court's expressions out of further paintings of her head.

    python3 tools/make_shade_face.py

A shade does not do the enderman's ^^. She closes her eyes, which is a thing
only she can do, because only she has eyes painted into her hide rather than
two glowing rectangles bolted on. Endermen keep the ^^; see enderman_happy.png.

One resting sheet in, one repainting per mood, one shell out of each:

    art/femshade.png         her hide, eyes open      -- what she wears
    art/femshade_pet.png     the same sheet, eyes closed   -> shade_pet.png
    art/femshade_angry.png   the same sheet, eyes narrowed -> shade_angry.png
    art/femshade_dizzy.png   the same sheet, eyes spiralled -> shade_dizzy.png

Every sheet must share one hide, because the shells borrow from it: any texel an
overlay covers but does not repaint has to come back as the hide that is already
there, or the shell reads as a patch across her face. Re-run mottle_skin.py
--restyle on one and you must re-run it on all of them.

That is also why a mood sheet is built by writing its paint onto the resting
sheet rather than by taking a painting whole. The paintings arrive over whatever
hide draw was current when they were made, and a hide draw is noise: carrying
one across would stamp a patch of the wrong grain on exactly the part of her
anyone actually looks at.

WHY A SHELL AND NOT A TEXTURE SWAP. Swapping her sheet for another whole one is
one draw instead of two and tempting for that -- but it multiplies the art.
Per-shade variants are coming, and four shades times four expressions is sixteen
full-body sheets to keep in step, against four plus three 64x32 shells they
all share. The shells also cannot drift: each is cut from the difference between two
paintings, so anything not about her eyes is transparent by construction.

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
GUIDE = os.path.join(HERE, "art/shade_face_guide.png")

# Every mood she has, and the shell each one becomes. Adding one is a painting
# and a line here -- and a line in ShadeLayer to say when she wears it.
MOODS = {"pet": "art/femshade_pet.png",
         "angry": "art/femshade_angry.png",
         "dizzy": "art/femshade_dizzy.png"}

# Moods that also get a _glow sheet: the paint alone, on transparent, drawn as a
# second additive pass so it burns rather than sits there. Only for expressions
# that are not meant to read as pigment -- spiralled eyes are a state she is in,
# not a face she is pulling, and the End's own light is what sells that.
GLOW = ("dizzy",)

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


def overlay(base, mood, out):
    """Every texel the two paintings disagree on, drawn as the mood one.

    Grown by DILATE first, so a lash the new expression does not reach is still
    covered rather than left poking out from under it.
    """
    b, p = base.load(), mood.load()
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
    img.save(out)
    return len(differ), len(grown)


def glow(mood_img, shell_path, out):
    """The painted texels of a shell, on transparent, for an emissive pass.

    Cut from the shell rather than from the sheet, so it can never cover a texel
    the shell does not: the glow is the same rectangle lit up, and anything the
    opaque pass left as hide stays hide.
    """
    shell = Image.open(shell_path).convert("RGBA")
    sp = shell.load()
    img = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    px = img.load()
    lit = 0
    for y in range(SIZE[1]):
        for x in range(SIZE[0]):
            if sp[x, y][3] and is_paint(shell.load(), x, y):
                px[x, y] = sp[x, y]
                lit += 1
    img.save(out)
    return lit


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
    for mood, path in MOODS.items():
        sheet = Image.open(os.path.join(HERE, path)).convert("RGBA")
        if base.size != sheet.size:
            raise SystemExit(f"{path} is not the size of the resting sheet")
        out = os.path.join(ASSETS, f"shade_{mood}.png")
        differ, grown = overlay(base, sheet, out)
        b, s = base.load(), sheet.load()
        faces = sorted({name for name, (ox, oy) in slots().items()
                        for j in range(CUBE) for i in range(CUBE)
                        if b[ox + i, oy + j + HEAD_V] != s[ox + i, oy + j + HEAD_V]})
        print(f"{os.path.relpath(out, HERE)}: {SIZE[0]}x{SIZE[1]}, "
              f"a {CUBE}-unit head at texOffs(0, 0)")
        print(f"  {differ} texels differ or carry paint, {grown} after dilation")
        print(f"  wraps: {', '.join(faces)}")
        if mood in GLOW:
            gout = os.path.join(ASSETS, f"shade_{mood}_glow.png")
            lit = glow(sheet, out, gout)
            print(f"  {os.path.basename(gout)}: {lit} texels burn")
    guide()
    print(f"{os.path.relpath(GUIDE, HERE)}: face outlines, for painting against")


if __name__ == "__main__":
    main()
