#!/usr/bin/env python3
"""Mirror a cube's UV island on a sheet, without touching anything else.

    python3 tools/flip_island.py Bowtie
    python3 tools/flip_island.py Bowtie --sheet shade_vaelle.png --dry-run
    python3 tools/flip_island.py Bowtie --vertical

A cube painted the wrong way round is a two-minute fix in an image editor and a
two-minute chance to nudge a neighbouring island by a texel. The sheets are
packed tight -- 8068 painted texels across 31 cubes -- and a stray column is
invisible until it turns up on somebody's shin.

So the rectangle comes from the RIG rather than from the eye. The cube's box UV
is computed the way Minecraft computes it, each face is mirrored inside its own
rectangle, and nothing outside those rectangles is read or written.

## Which faces

Every face of the cube, each flipped in place. For a flat cube -- Bowtie, the
skirt flaps -- that is the north and south faces and the four zero-area edges,
so in practice the two you can see. Box UV already stores opposite faces as
mirror images of each other, and flipping both in place keeps that true; it
swaps which way the whole thing faces, which is the point.

## Box UV

Minecraft's layout for a cube at (u, v) with size (w, h, d):

    up     (u+d,     v,     w, d)      north  (u+d,     v+d, w, h)
    down   (u+d+w,   v,     w, d)      west   (u+d+w,   v+d, d, h)
    east   (u,       v+d,   d, h)      south  (u+d+w+d, v+d, w, h)

Zero-area faces (a flat cube's up/down/east/west) are skipped rather than
special-cased: there is nothing in them to mirror.
"""
import argparse
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RIG = os.path.join(HERE, "art/shade_base.bbmodel")
SHEETS = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro/textures/entity")


def faces(u, v, w, h, d):
    """The six face rectangles, in Minecraft's box-UV order."""
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def cubes(bb, want):
    """Every element with this name, with its UV offset and size."""
    found = []
    for e in bb.get("elements", []):
        if e.get("name") != want:
            continue
        a, b = e["from"], e["to"]
        size = tuple(round(abs(b[i] - a[i])) for i in range(3))
        found.append((tuple(e["uv_offset"]), size))
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cube", help="the cube's name in the rig, e.g. Bowtie")
    ap.add_argument("--rig", default=RIG)
    ap.add_argument("--sheet", action="append",
                    help="sheet filename under textures/entity (repeatable). "
                         "Default: every sheet with something painted there.")
    ap.add_argument("--vertical", action="store_true",
                    help="mirror top-to-bottom instead of left-to-right")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    with open(args.rig) as fh:
        bb = json.load(fh)

    found = cubes(bb, args.cube)
    if not found:
        names = sorted({e.get("name", "?") for e in bb.get("elements", [])})
        sys.exit(f"ERROR: the rig has no cube called {args.cube!r}. It has: "
                 + ", ".join(names))

    # Cubes sharing a UV offset share an island; mirror it once, not twice.
    islands = {}
    for (u, v), (w, h, d) in found:
        islands.setdefault((u, v, w, h, d), 0)
        islands[(u, v, w, h, d)] += 1
    print(f"{args.cube}: {len(found)} cube(s) on {len(islands)} island(s)")

    # Only sheets this rig is mapped to. textures/entity also holds the
    # enderman's own 64x32 sheets, and an island at (78, 232) is off the edge of
    # those -- which is a sheet that has nothing to do with this cube, not an
    # error to stop on.
    res = bb.get("resolution", {})
    want = (res.get("width"), res.get("height"))
    sheets = args.sheet or sorted(f for f in os.listdir(SHEETS) if f.endswith(".png"))
    axis = Image.FLIP_TOP_BOTTOM if args.vertical else Image.FLIP_LEFT_RIGHT
    which = "vertically" if args.vertical else "horizontally"

    for name in sheets:
        path = os.path.join(SHEETS, name)
        im = Image.open(path).convert("RGBA")
        if not args.sheet and (im.width, im.height) != want:
            continue                    # not a sheet for this rig
        px = im.load()
        touched = []
        for (u, v, w, h, d), shared in islands.items():
            for face, (x, y, fw, fh) in faces(u, v, w, h, d).items():
                if fw <= 0 or fh <= 0:
                    continue
                if x + fw > im.width or y + fh > im.height:
                    sys.exit(f"ERROR: {name} is {im.width}x{im.height} and the "
                             f"{face} face wants {x}..{x+fw}, {y}..{y+fh}")
                painted = sum(1 for yy in range(y, y + fh)
                              for xx in range(x, x + fw) if px[xx, yy][3] > 0)
                if not painted:
                    continue
                box = (x, y, x + fw, y + fh)
                im.paste(im.crop(box).transpose(axis), box)
                touched.append(f"{face} at ({x},{y}) {fw}x{fh}, {painted} painted")
        if not touched:
            continue
        print(f"  {name}")
        for line in touched:
            print(f"    flipped {which}: {line}")
        if not args.dry_run:
            im.save(path)
    if args.dry_run:
        print("  --dry-run: nothing was written")


if __name__ == "__main__":
    main()
