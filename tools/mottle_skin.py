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

# Measured off the vanilla enderman sheet, which turns out to be far simpler
# than any of this: its hide is exactly TWO colours, #000000 and #161616, with
# no gradient between them at all.
#
#   #161616  lum 22.0   62.4% of the body
#   #000000  lum  0.0   37.1%
#
# ...and they are only mildly clumped: 62% of neighbouring texels share a tone
# where pure random at that split would give 53%, with a mean horizontal run of
# 2.2 texels. So it is a dither with a little cohesion, not smooth noise, and
# the LIGHT tone is the majority -- the body is grey with black in it, not the
# other way round.
# The grain is also mildly DIRECTIONAL -- 65% of vertical neighbours share a
# tone against 59% of horizontal ones, so it streaks very slightly downward.
# These three numbers were fitted by sweeping against all of that rather than
# picked: they land h 59%, v 61%, mean run 2.3 against vanilla's 59 / 65 / 2.17.
# Smooth noise alone is far too cohesive (84% same, runs of 5.3); half of it has
# to be per-texel randomness.
VANILLA_TONE = (0x16, 0x16, 0x16)
VANILLA_LIGHT_SHARE = 0.624
VANILLA_CELL_X = 2
VANILLA_CELL_Y = 4
VANILLA_BLEND = 0.50       # coarse noise vs per-texel random


def value_noise(w, h, cell, rng, cell_y=None):
    """Coarse noise, bilinear between lattice points. Blotches, not static.

    A separate cell_y stretches the blotches vertically, which is how the
    enderman's hide streaks.
    """
    cell_y = cell_y or cell
    gw, gh = w // cell + 2, h // cell_y + 2
    grid = [[rng.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        gy, fy = divmod(y, cell_y)
        ty = fy / cell_y
        for x in range(w):
            gx, fx = divmod(x, cell)
            tx = fx / cell
            a = grid[gy][gx] * (1 - tx) + grid[gy][gx + 1] * tx
            b = grid[gy + 1][gx] * (1 - tx) + grid[gy + 1][gx + 1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def two_tone(image, tone=VANILLA_TONE, share=VANILLA_LIGHT_SHARE,
             threshold=DEFAULT_THRESHOLD, seed=0):
    """The vanilla enderman's own scheme: two colours, mildly clumped.

    No gradient. Every black texel becomes either black or `tone`, with `share`
    of them taking the lighter one, clumped just enough to read as a hide rather
    than as television static.
    """
    image = image.convert("RGBA")
    w, h = image.size
    px = image.load()
    rng = random.Random(seed)
    coarse = value_noise(w, h, VANILLA_CELL_X, rng, VANILLA_CELL_Y)
    field = [[VANILLA_BLEND * coarse[y][x] + (1 - VANILLA_BLEND) * rng.random()
              for x in range(w)] for y in range(h)]

    black = [(x, y) for y in range(h) for x in range(w)
             if px[x, y][3] > 0 and max(px[x, y][:3]) <= threshold]
    if not black:
        return image, 0
    # Threshold the noise at the percentile that yields exactly the wanted
    # share, rather than at 0.5 and hoping.
    values = sorted(field[y][x] for x, y in black)
    cut = values[int(len(values) * (1.0 - share))]
    for x, y in black:
        a = px[x, y][3]
        px[x, y] = (tone + (a,)) if field[y][x] >= cut else (0, 0, 0, a)
    return image, len(black)


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
    ap.add_argument("--style", choices=("soft", "vanilla"), default="soft",
                    help="soft: continuous noise. vanilla: the enderman's own "
                         "two tones, #000000 and #161616 at a 62/38 split")
    args = ap.parse_args()

    if not os.path.exists(args.source):
        sys.exit(f"no such file: {args.source}")
    image = Image.open(args.source)
    if args.style == "vanilla":
        out, touched = two_tone(image, threshold=args.threshold, seed=args.seed)
    else:
        out, touched = mottle(image, args.lift, args.threshold, args.seed)
    dest = args.out or args.source
    out.save(dest)
    total = image.size[0] * image.size[1]
    detail = (f"two tones #000000/#%02X%02X%02X" % VANILLA_TONE
              if args.style == "vanilla" else f"lift {args.lift}")
    print(f"{os.path.relpath(dest, HERE)}: {args.style} — {touched} black texels "
          f"of {total} ({touched * 100.0 / total:.1f}%), {detail}")


if __name__ == "__main__":
    main()
