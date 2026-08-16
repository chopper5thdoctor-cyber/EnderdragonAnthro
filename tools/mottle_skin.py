#!/usr/bin/env python3
"""Break up flat black on a skin, the way an enderman's hide is broken up.

Pure #000000 over a whole limb reads as a hole rather than as a surface: there
is nothing for the light to catch, so the silhouette is all you see. Vanilla
endermen are not flat black either — the hide is a near-black with a fine
scatter of slightly lifted pixels, which is what keeps them looking like a
creature in a dark room rather than a cutout.

    python3 tools/mottle_skin.py art/shade_vaelle.png
    python3 tools/mottle_skin.py in.png -o out.png --lift 24 --seed 7

Only pixels that are black are touched. Anything painted — eyes, accents, any
colour at all — is left exactly as it was, and so is alpha, so transparent
regions stay transparent and the sheet still packs the same.

The noise is three things added together, because one alone always looks wrong:

  patches   coarse value noise, bilinear-sampled, so the hide has broad
            lighter and darker areas rather than uniform static
  grain     per-pixel speckle, fine and low-amplitude, which is the part that
            actually reads as texture at Minecraft's resolution
  sheen     a gentle vertical gradient per island, lighter at the top, so
            limbs have some sense of a light source above them

Deterministic: the same file and seed give the same result every time, so
re-running after a repaint does not reshuffle the parts you kept.
"""

import argparse
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

DEFAULT_LIFT = 20          # brightest a mottled pixel may become
DEFAULT_THRESHOLD = 8      # count a pixel as "black" at or below this
PATCH = 8                  # coarse-noise cell, in texels


def value_noise(w, h, cell, rng):
    """Coarse noise, bilinear between lattice points. Blotches, not static."""
    gw, gh = w // cell + 2, h // cell + 2
    grid = [[rng.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        gy, fy = divmod(y, cell)
        ty = fy / cell
        for x in range(w):
            gx, fx = divmod(x, cell)
            tx = fx / cell
            a = grid[gy][gx] * (1 - tx) + grid[gy][gx + 1] * tx
            b = grid[gy + 1][gx] * (1 - tx) + grid[gy + 1][gx + 1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def mottle(image, lift=DEFAULT_LIFT, threshold=DEFAULT_THRESHOLD, seed=0):
    """Return a copy with its black areas given a hide."""
    image = image.convert("RGBA")
    w, h = image.size
    px = image.load()
    rng = random.Random(seed)
    patches = value_noise(w, h, PATCH, rng)

    # The vertical gradient is measured per column-run of black rather than
    # over the whole sheet, so a leg near the bottom of the sheet is not
    # uniformly darker than an arm near the top.
    touched = 0
    for x in range(w):
        column = [y for y in range(h)
                  if px[x, y][3] > 0 and max(px[x, y][:3]) <= threshold]
        if not column:
            continue
        top, bottom = min(column), max(column)
        span = max(1, bottom - top)
        for y in column:
            r, g, b, a = px[x, y]
            sheen = 1.0 - (y - top) / span              # lighter up top
            value = (0.55 * patches[y][x]
                     + 0.30 * rng.random()
                     + 0.15 * sheen)
            v = int(round(value * lift))
            # A touch of the End's violet, so it is not a grey creature.
            px[x, y] = (min(r + v, 255), min(g + int(v * 0.82), 255),
                        min(b + int(v * 1.15), 255), a)
            touched += 1
    return image, touched


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("source")
    ap.add_argument("-o", "--out", help="default: overwrite the source")
    ap.add_argument("--lift", type=int, default=DEFAULT_LIFT,
                    help=f"brightest a mottled pixel becomes (default {DEFAULT_LIFT})")
    ap.add_argument("--threshold", type=int, default=DEFAULT_THRESHOLD,
                    help=f"treat a pixel as black at or below this (default {DEFAULT_THRESHOLD})")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()

    if not os.path.exists(args.source):
        sys.exit(f"no such file: {args.source}")
    image = Image.open(args.source)
    out, touched = mottle(image, args.lift, args.threshold, args.seed)
    dest = args.out or args.source
    out.save(dest)
    total = image.size[0] * image.size[1]
    print(f"{os.path.relpath(dest, HERE)}: mottled {touched} black texels "
          f"of {total} ({touched * 100.0 / total:.1f}%), lift {args.lift}")


if __name__ == "__main__":
    main()
