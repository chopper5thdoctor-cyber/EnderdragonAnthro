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
import json
import math
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


def mottle(image, lift=DEFAULT_LIFT, threshold=DEFAULT_THRESHOLD, seed=0,
           field=None):
    """Return a copy with its black areas given a hide.

    With a `field` from body_field(), brightness follows the figure: darkest
    over the torso where the body is thickest, lightening out to the hands and
    feet. Without one it falls back to a per-island vertical sheen, which is
    only a guess at where a piece sits on the body -- and looks like one.
    """
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
            if field is None:
                shape = 1.0 - (y - top) / span          # lighter up top
                weights = (0.55, 0.30, 0.15)
            else:
                # A floor of 0.15 so the core still has grain rather than
                # going flat black, which is the thing this set out to fix.
                shape = 0.15 + 0.85 * field.get((x, y), 0.5)
                weights = (0.28, 0.17, 0.55)
            value = (weights[0] * patches[y][x]
                     + weights[1] * rng.random()
                     + weights[2] * shape)
            v = int(round(value * lift))
            # A touch of the End's violet, so it is not a grey creature.
            px[x, y] = (min(r + v, 255), min(g + int(v * 0.82), 255),
                        min(b + int(v * 1.15), 255), a)
            touched += 1
    return image, touched


def luminance(rgb):
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def solve_lift(image, peak, threshold, seed, field=None):
    """The lift whose brightest texel lands on `peak` luminance.

    Measured rather than derived: the brightest texel depends on where the
    noise field happens to top out, which depends on the sheet and the seed.
    One probe run at a reference lift gives the ratio, since the whole thing
    scales linearly in lift, and a couple of integer steps settle the rounding.
    """
    probe, _ = mottle(image.copy(), lift=100, threshold=threshold, seed=seed,
                      field=field)
    src = image.convert("RGBA").load()
    px = probe.load()
    w, h = probe.size
    black = [(x, y) for y in range(h) for x in range(w)
             if src[x, y][3] > 0 and max(src[x, y][:3]) <= threshold]
    if not black:
        return DEFAULT_LIFT
    top = max(luminance(px[x, y]) for x, y in black)
    guess = max(1, int(round(peak * 100.0 / top)))
    best, err = guess, None
    for lift in range(max(1, guess - 3), guess + 4):
        out, _ = mottle(image.copy(), lift=lift, threshold=threshold, seed=seed,
                        field=field)
        q = out.load()
        got = max(luminance(q[x, y]) for x, y in black)
        d = abs(got - peak)
        if err is None or d < err:
            best, err = lift, d
    return best


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("source")
    ap.add_argument("-o", "--out", help="default: overwrite the source")
    ap.add_argument("--lift", type=int, default=DEFAULT_LIFT,
                    help=f"brightest a mottled pixel becomes (default {DEFAULT_LIFT})")
    ap.add_argument("--threshold", type=int, default=DEFAULT_THRESHOLD,
                    help=f"treat a pixel as black at or below this (default {DEFAULT_THRESHOLD})")
    ap.add_argument("--peak", type=float, default=None,
                    help="target luminance for the brightest mottled texel, and "
                         "solve for the lift. #161616 -- the enderman's own "
                         "light tone -- is 22.0")
    ap.add_argument("--rig", default=None,
                    help="a .bbmodel; shades by distance from the body's core "
                         "instead of per-island, so the torso is darkest and "
                         "the hands and feet lightest")
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--style", choices=("soft", "vanilla"), default="soft",
                    help="soft: continuous noise. vanilla: the enderman's own "
                         "two tones, #000000 and #161616 at a 62/38 split")
    args = ap.parse_args()

    if not os.path.exists(args.source):
        sys.exit(f"no such file: {args.source}")
    image = Image.open(args.source)
    field = body_field(args.rig) if args.rig else None
    if args.peak is not None and args.style == "soft":
        args.lift = solve_lift(image, args.peak, args.threshold, args.seed, field)
    if args.style == "vanilla":
        out, touched = two_tone(image, threshold=args.threshold, seed=args.seed)
    else:
        out, touched = mottle(image, args.lift, args.threshold, args.seed, field)
    dest = args.out or args.source
    out.save(dest)
    total = image.size[0] * image.size[1]
    detail = (f"two tones #000000/#%02X%02X%02X" % VANILLA_TONE
              if args.style == "vanilla" else f"lift {args.lift}")
    print(f"{os.path.relpath(dest, HERE)}: {args.style} — {touched} black texels "
          f"of {total} ({touched * 100.0 / total:.1f}%), {detail}")



# ---------------------------------------------------------------- body-aware

# Cubes that make up the core. Everything's brightness is measured as distance
# from the centre of these, so the figure is dark where it is thickest and
# lightens out towards the extremities.
# "cube" became "bosom" when the old bosom was dropped; the collar it
# names sits proud of the chest, so it belongs in RELIEF, not here.
CORE = ("body1", "body2", "neck")

# Pieces that sit PROUD of the body. Distance-from-core makes these the darkest
# thing on the model, because they are nearest the core -- which is backwards:
# a raised feature is what catches the light, not what hides from it. They are
# lifted to RELIEF_FLOOR instead.
RELIEF = ("bosom",)
RELIEF_FLOOR = 0.62


def _rotate(point, origin, degrees):
    """Blockbench rotation, applied about the cube's own origin."""
    x, y, z = (point[i] - origin[i] for i in range(3))
    rx, ry, rz = (math.radians(a) for a in degrees)
    # x, then y, then z -- Blockbench's order
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return (x + origin[0], y + origin[1], z + origin[2])


def _uv_offset(e):
    """The island origin, even when Blockbench left the field out.

    It omits uv_offset whenever the offset is (0, 0) after its own rounding, so
    reading only the explicit field silently drops those cubes -- which cost
    left_leg its entire island the first time this ran.
    """
    if "uv_offset" in e:
        return tuple(e["uv_offset"])
    d = e["to"][2] - e["from"][2]
    n = e["faces"]["north"]["uv"]
    return (min(n[0], n[2]) - d, min(n[1], n[3]) - d)


def _face_points(e):
    """Every texel of a cube's island, with the 3D point it sits on.

    Box UV is one texel per model unit, so the inverse mapping is exact: each
    face rectangle is the cube's own extent along two axes, and the third axis
    is pinned to whichever side of the box that face is.
    """
    x0, y0, z0 = e["from"]
    x1, y1, z1 = e["to"]
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    u, v = _uv_offset(e)
    rot = e.get("rotation") or [0, 0, 0]
    org = e.get("origin") or [0, 0, 0]
    out = {}
    # (rect origin, size, and a function from rect-local (i, j) to a 3D point)
    faces = {
        "up":    ((u + d, v), (w, d), lambda i, j: (x0 + i, y1, z0 + j)),
        "down":  ((u + d + w, v), (w, d), lambda i, j: (x0 + i, y0, z0 + j)),
        "east":  ((u, v + d), (d, h), lambda i, j: (x0, y1 - j, z0 + i)),
        "north": ((u + d, v + d), (w, h), lambda i, j: (x0 + i, y1 - j, z0)),
        "west":  ((u + d + w, v + d), (d, h), lambda i, j: (x1, y1 - j, z0 + i)),
        "south": ((u + d + w + d, v + d), (w, h), lambda i, j: (x0 + i, y1 - j, z1)),
    }
    for (ox, oy), (fw, fh), to3d in faces.values():
        for j in range(int(round(fh))):
            for i in range(int(round(fw))):
                out[(int(ox) + i, int(oy) + j)] = _rotate(to3d(i + 0.5, j + 0.5), org, rot)
    return out


def check_islands(model):
    """Two cubes may share an island only if they are a mirrored pair.

    Anything else means one piece is painting over another: the bosom sat inside
    left_leg's island for a while, so shading the bosom dark also stamped a dark
    patch onto the leg. Silent, and only visible on the model.
    """
    boxes = []
    for e in model["elements"]:
        if not e.get("faces"):
            continue
        w, h, d = (e["to"][i] - e["from"][i] for i in range(3))
        u, v = _uv_offset(e)
        boxes.append((e["name"], u, v, u + 2 * (d + w), v + d + h))
    clashes = []
    for i, a in enumerate(boxes):
        for b in boxes[i + 1:]:
            if (a[1] < b[3] and b[1] < a[3] and a[2] < b[4] and b[2] < a[4]
                    and (a[1], a[2]) != (b[1], b[2])):
                clashes.append(f"{a[0]} and {b[0]}")
    return clashes


def body_field(rig_path):
    """A 0..1 value per texel: 0 at the body's core, 1 at the furthest point."""
    with open(rig_path) as f:
        model = json.load(f)
    clashes = check_islands(model)
    if clashes:
        sys.stderr.write("WARNING: UV islands overlap and are not mirror pairs: "
                         + "; ".join(clashes) + "\n"
                         + "         shading one will paint over the other.\n")
    els = [e for e in model["elements"] if e.get("faces")]

    core = [e for e in els if e["name"] in CORE] or els
    cx = sum((e["from"][0] + e["to"][0]) / 2 for e in core) / len(core)
    cy = sum((e["from"][1] + e["to"][1]) / 2 for e in core) / len(core)
    cz = sum((e["from"][2] + e["to"][2]) / 2 for e in core) / len(core)

    field, far, relief = {}, 0.0, set()
    for e in els:
        for texel, (px, py, pz) in _face_points(e).items():
            dist = ((px - cx) ** 2 + (py - cy) ** 2 + (pz - cz) ** 2) ** 0.5
            field[texel] = dist
            far = max(far, dist)
            if e["name"] in RELIEF:
                relief.add(texel)
    if not far:
        return field
    out = {k: v / far for k, v in field.items()}
    for texel in relief:
        out[texel] = max(out[texel], RELIEF_FLOOR)
    return out

if __name__ == "__main__":
    main()
