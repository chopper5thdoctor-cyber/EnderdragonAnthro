#!/usr/bin/env python3
"""Render a .bbmodel the way Minecraft samples it, so a rig can be eyeballed.

    python3 tools/preview_model.py                     # the dragon
    python3 tools/preview_model.py art/shade_base.bbmodel
    python3 tools/preview_model.py --only fist forearm --zoom 7

verify_model.py proves the generated Java matches the .bbmodel. It cannot prove
the .bbmodel is what anyone meant: a hand merged from the wrong copy, a cube
left inside the chest, an island painted onto the wrong face all pass it
happily, because Java and Blockbench agree about every one of them.

So this draws the thing. Every texel of every cube maps to a 3D point -- box UV
is one texel per model unit, which makes the inverse exact -- and those points
are pushed through the cube's own rotation and then through every group pivot
above it, exactly as verify_model walks the outliner. Painter's algorithm on
the depth axis, one square per texel. Chunky, and honest about what is actually
on the model rather than what is on the sheet.

--only filters to cubes whose name contains any of the given words, which is
how to look at a hand without the wings in the way.
"""

import argparse
import base64
import io
import json
import math
import os
import sys

from PIL import Image, ImageDraw
sys.path.insert(0, "/home/user/EnderdragonAnthro/tools")
from mottle_skin import _uv_offset, _rotate
from bbmodel_to_java import wants_mirror

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
ap.add_argument("model", nargs="?", default="art/dragon_form.bbmodel")
ap.add_argument("--only", nargs="*", default=None,
                help="only cubes whose name contains one of these")
ap.add_argument("--zoom", type=int, default=None, help="pixels per model unit")
ap.add_argument("--lift", type=float, default=1.9,
                help="brighten, so a near-black rig reads on screen")
ap.add_argument("-o", "--out", default=None)
args = ap.parse_args()

bb = json.load(open(os.path.join(HERE, args.model)))
# The sheet the rig points at, embedded in the file by whoever painted it.
src = bb["textures"][0].get("source", "")
if src.startswith("data:"):
    tex = Image.open(io.BytesIO(base64.b64decode(src.split(",", 1)[1]))).convert("RGBA")
else:
    raise SystemExit(f"{args.model} has no embedded texture to render with")
groups = {g["uuid"]: g for g in bb.get("groups", [])}
els = {e["uuid"]: e for e in bb["elements"]}


def face_points(e):
    """Every texel of a cube's island, with the 3D point it sits on -- mirrored.

    mottle_skin._face_points does the unmirrored mapping, which is all a
    distance field needs. A preview needs more: a mirrored cube reads the
    opposite side's rectangle and runs every face backwards along u, and a
    preview blind to that shows both hands identical however wrong the flag is.
    Which is exactly the bug it was asked to find.

    wants_mirror rather than the raw mirror_uv, because MIRROR_OVERRIDE is what
    the generator actually emits and the generator is what the game runs.
    """
    x0, y0, z0 = e["from"]
    x1, y1, z1 = e["to"]
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    u, v = _uv_offset(e)
    org = e.get("origin") or [0, 0, 0]
    deg = e.get("rotation") or [0, 0, 0]
    faces = {
        "up":    ((u + d, v), (w, d), lambda i, j: (x0 + i, y1, z0 + j)),
        "down":  ((u + d + w, v), (w, d), lambda i, j: (x0 + i, y0, z0 + j)),
        "east":  ((u, v + d), (d, h), lambda i, j: (x0, y1 - j, z0 + i)),
        "north": ((u + d, v + d), (w, h), lambda i, j: (x0 + i, y1 - j, z0)),
        "west":  ((u + d + w, v + d), (d, h), lambda i, j: (x1, y1 - j, z0 + i)),
        "south": ((u + d + w + d, v + d), (w, h), lambda i, j: (x0 + i, y1 - j, z1)),
    }
    if wants_mirror(e):
        # East and west trade rectangles, and every face runs backwards along u.
        faces["east"], faces["west"] = ((faces["west"][0], faces["east"][1], faces["east"][2]),
                                        (faces["east"][0], faces["west"][1], faces["west"][2]))
        faces = {n: (o, s, (lambda f, sw: lambda i, j: f(sw - 1 - i, j))(fn, s[0]))
                 for n, (o, s, fn) in faces.items()}
    out = {}
    for (ox, oy), (fw, fh), to3d in faces.values():
        for j in range(int(round(fh))):
            for i in range(int(round(fw))):
                out[(int(ox) + i, int(oy) + j)] = _rotate(to3d(i + 0.5, j + 0.5), org, deg)
    return out


def rot(p, o, deg):
    x, y, z = (p[i] - o[i] for i in range(3))
    rx, ry, rz = (math.radians(a) for a in deg)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return (x + o[0], y + o[1], z + o[2])


points = []          # (name, (x,y,z), rgba)
def walk(nodes, chain):
    for n in nodes:
        if isinstance(n, str):
            e = els.get(n)
            if not e or not e.get("faces"):
                continue
            for (tx, ty), p in face_points(e).items():
                if not (0 <= tx < tex.width and 0 <= ty < tex.height):
                    continue
                c = tex.getpixel((tx, ty))
                if c[3] == 0:
                    continue
                q = p
                for (o, d) in reversed(chain):
                    q = rot(q, o, d)
                points.append((e["name"], q, c))
        else:
            g = groups.get(n["uuid"], {})
            walk(n.get("children", []),
                 chain + [(g.get("origin", [0, 0, 0]), g.get("rotation", [0, 0, 0]))])
walk(bb["outliner"], [])
print("points:", len(points))


def render(view, keep=None, px_per_unit=3, lift=1.0):
    pts = []
    for name, (x, y, z), c in points:
        if keep and not any(k in name for k in keep):
            continue
        if view == "front":   sx, sy, dep = x, y, -z
        elif view == "side":  sx, sy, dep = z, y, x
        else:                 sx, sy, dep = -x, y, z
        pts.append((dep, sx, sy, c))
    pts.sort(key=lambda p: p[0])
    xs = [p[1] for p in pts]; ys = [p[2] for p in pts]
    S = px_per_unit
    w = int((max(xs) - min(xs)) * S) + S + 16
    h = int((max(ys) - min(ys)) * S) + S + 16
    img = Image.new("RGBA", (w, h), (22, 18, 30, 255))
    d = ImageDraw.Draw(img)
    x0, y0 = min(xs), min(ys)
    cache = {}
    for _dep, sx, sy, c in pts:
        cx = int((sx - x0) * S) + 8
        cy = h - 8 - int((sy - y0) * S) - S
        col = cache.get(c)
        if col is None:
            col = tuple(min(255, int(v * lift)) for v in c[:3]) + (255,)
            cache[c] = col
        d.rectangle([cx, cy, cx + S - 1, cy + S - 1], fill=col)
    return img


def sheet(images, labels, out):
    gap, lab = 18, 20
    W = sum(i.width for i in images) + gap * (len(images) - 1) + 16
    H = max(i.height for i in images) + lab + 16
    s = Image.new("RGBA", (W, H), (22, 18, 30, 255))
    d = ImageDraw.Draw(s)
    x = 8
    for im, name in zip(images, labels):
        s.paste(im, (x, lab + 8))
        d.text((x, lab - 12), name, fill=(214, 206, 224, 255))
        x += im.width + gap
    s.save(out); print("wrote", out, s.size)


zoom = args.zoom or (7 if args.only else 3)
out = args.out or os.path.join(
    HERE, "art", os.path.basename(args.model).replace(".bbmodel", "") +
    ("_" + "_".join(args.only) if args.only else "") + "_preview.png")
sheet([render(v, args.only, zoom, args.lift) for v in ("front", "side", "back")],
      ["front", "side", "back"], out)
