#!/usr/bin/env python3
"""Draw the smoke that comes off dragonfire.

Vanilla smoke is eight 8x8 frames, generic_0 through generic_7, and the
particle walks them as it ages -- a soft blob that thins and breaks up. Ours
has to do the same job in the fire's own colours, so the frames are built from
the deep end of the fire ramp rather than from grey: smoke off a purple flame
that is grey is smoke off somebody else's fire.

Written by hand rather than recoloured from vanilla's: those frames are
Mojang's art and do not belong in this repo, even as a starting point.

Run:  python3 tools/make_dragon_smoke.py
"""
import math
import os

from PIL import Image

OUT = "src/main/resources/assets/enderdragonanthro/textures/particle"
SIZE = 8
FRAMES = 8

# The bottom three bands of the fire ramp, so the smoke reads as the same fire
# gone cold rather than as a separate effect.
CORE = (0x74, 0x00, 0xB2)
EDGE = (0x38, 0x00, 0x5C)


def frame(i):
    """One puff. Later frames are wider, thinner and more broken up."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    age = i / (FRAMES - 1)                      # 0 at birth, 1 at death
    radius = 2.1 + age * 1.6                    # spreads as it rises
    centre = (SIZE - 1) / 2.0
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - centre, y - centre
            d = math.sqrt(dx * dx + dy * dy)
            if d > radius:
                continue
            # Soft edge, and the whole puff fades as it ages.
            falloff = 1.0 - (d / radius) ** 1.6
            alpha = falloff * (1.0 - age * 0.55)
            # Older smoke tears: knock holes in it on a fixed pattern so the
            # frames stay deterministic instead of re-rolling every run.
            if age > 0.45 and ((x * 7 + y * 5 + i * 3) % 11) < int(age * 4):
                alpha *= 0.25
            if alpha <= 0.06:
                continue
            mix = min(1.0, d / radius + age * 0.35)
            px[x, y] = (
                round(CORE[0] + (EDGE[0] - CORE[0]) * mix),
                round(CORE[1] + (EDGE[1] - CORE[1]) * mix),
                round(CORE[2] + (EDGE[2] - CORE[2]) * mix),
                round(255 * alpha))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for i in range(FRAMES):
        path = os.path.join(OUT, "dragon_smoke_%d.png" % i)
        frame(i).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
