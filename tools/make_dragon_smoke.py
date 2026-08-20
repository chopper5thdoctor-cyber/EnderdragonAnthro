#!/usr/bin/env python3
"""Draw the smoke that comes off dragonfire.

Vanilla smoke is eight 8x8 frames and the particle walks them as it ages.
Two things about Mojang's are worth copying and one is not:

  * The sequence SHRINKS. smoke.json lists generic_7 first and generic_0 last,
    so the filenames run backwards from play order, and in play order the lit
    area goes 29, 20, 14, 11, 8, 4, 2, 1 -- a puff that dwindles to a single
    texel. The counts below are those, matched frame for frame.

  * Their frames are pure white and fully opaque. The colour is not in the art
    at all: SmokeParticle passes 0.1f, 0.1f, 0.1f and the white mask is
    multiplied down to dark grey at draw time.

That second one is why ours cannot be a straight swap. A purple sprite through
the same 0.1 multiply comes out near black, so DragonSmokeParticle sets the
colour to white and lets these frames carry their own -- which they can do
better than a flat tint, since smoke off a flame is brighter at its heart.

Written by hand rather than recoloured from vanilla's: those frames are
Mojang's art and do not belong in this repo, even as a starting point.

Run:  python3 tools/make_dragon_smoke.py
"""
import math
import os

from PIL import Image

OUT = "src/main/resources/assets/enderdragonanthro/textures/particle"
SIZE = 8

# Mojang's own coverage curve, in play order.
COVERAGE = [29, 20, 14, 11, 8, 4, 2, 1]

# The bottom of the fire's ramp, so this reads as that fire gone cold.
CORE = (0x9C, 0x30, 0xD8)
EDGE = (0x3A, 0x00, 0x60)


def frame(i):
    """One puff, holding exactly the number of texels vanilla's would."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    age = i / (len(COVERAGE) - 1)
    centre = (SIZE - 1) / 2.0

    # Take the N texels nearest the middle. Picking by distance rather than by
    # a radius is what makes the count exact instead of approximate, and it
    # keeps the blob round at sizes where a circle test would go lumpy.
    ranked = sorted(
        ((math.hypot(x - centre, y - centre), x, y)
         for y in range(SIZE) for x in range(SIZE)),
        key=lambda t: (t[0], t[2], t[1]))
    chosen = ranked[:COVERAGE[i]]
    reach = max(d for d, _, _ in chosen) or 1.0

    for d, x, y in chosen:
        edge = min(1.0, d / reach)
        # Bright at the heart, dark at the rim, and the whole puff thins as it
        # goes -- but never to nothing, or the last frames vanish before the
        # particle's own fade has finished with them.
        alpha = (1.0 - 0.35 * edge) * (1.0 - age * 0.45)
        mix = min(1.0, edge * 0.8 + age * 0.4)
        px[x, y] = (
            round(CORE[0] + (EDGE[0] - CORE[0]) * mix),
            round(CORE[1] + (EDGE[1] - CORE[1]) * mix),
            round(CORE[2] + (EDGE[2] - CORE[2]) * mix),
            round(255 * alpha))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for i in range(len(COVERAGE)):
        path = os.path.join(OUT, "dragon_smoke_%d.png" % i)
        frame(i).save(path)
        print("wrote %s  (%d texels)" % (path, COVERAGE[i]))


if __name__ == "__main__":
    main()
