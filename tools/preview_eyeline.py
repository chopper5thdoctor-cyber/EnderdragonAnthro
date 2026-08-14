#!/usr/bin/env python3
"""Front view of the rig with the game's eye plane drawn on it.

Answers "where will the red line in F3+B actually cut across the model", which
is otherwise only findable by transforming and looking. The eye height is a
fixed fraction of the HITBOX (1.62 / 1.8 = 0.9) and the model is a separate
size, so the two drift apart as soon as the rig's proportions change.

    python3 tools/preview_eyeline.py        # writes art/eyeline_preview.png
"""

import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from verify_model import from_bbmodel  # noqa: E402  (needs the path above)

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BBMODEL = os.path.join(HERE, "art/dragon_form.bbmodel")
EYES_TEX = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro"
                              "/textures/entity/dragon_form_eyes.png")
OUT = os.path.join(HERE, "art/eyeline_preview.png")

PLAYER_HEIGHT = 1.8          # blocks
PLAYER_EYE = 1.62            # blocks — Player.STANDING_DIMENSIONS
AUTHORED = 0.25              # exact undo of the 4x authoring
SCALE = 3                    # pixels per rig unit


def hull(points):
    """Monotone chain, so a rotated cube draws as its real outline."""
    pts = sorted(set(map(tuple, points)))
    if len(pts) < 3:
        return pts

    def half(seq):
        out = []
        for p in seq:
            while len(out) >= 2:
                (ax, ay), (bx, by) = out[-2], out[-1]
                if (bx - ax) * (p[1] - ay) - (by - ay) * (p[0] - ax) > 0:
                    break
                out.pop()
            out.append(p)
        return out

    return half(pts)[:-1] + half(reversed(pts))[:-1]


def eye_row():
    """The rig height of the painted eyes, from the emissive sheet."""
    bb = json.load(open(BBMODEL))
    lit_img = Image.open(EYES_TEX).convert("RGBA")
    alpha = lit_img.split()[3]
    lit = [(x, y) for y in range(lit_img.height) for x in range(lit_img.width)
           if alpha.getpixel((x, y)) > 0]
    corners = {n: c for n, c in from_bbmodel(BBMODEL)}
    best = None
    for e in bb["elements"]:
        if e.get("type", "cube") != "cube" or e["name"] not in corners:
            continue
        u, v = e["uv_offset"]
        w, h, d = [e["to"][i] - e["from"][i] for i in range(3)]
        fx, fy, fw, fh = u + d, v + d, w, h          # the north (front) face
        inside = [p for p in lit if fx <= p[0] < fx + fw and fy <= p[1] < fy + fh]
        if not inside:
            continue
        c = corners[e["name"]]
        top, bot = c[:, 1].max(), c[:, 1].min()
        rows = [p[1] for p in inside]
        span = (top - (max(rows) + 1 - fy) / fh * (top - bot),
                top - (min(rows) - fy) / fh * (top - bot))
        if best is None or span[1] > best[1]:
            best = span                              # the highest lit face is the face
    return best


def main():
    cubes = from_bbmodel(BBMODEL)
    lo = min(c[:, 1].min() for _, c in cubes)
    hi = max(c[:, 1].max() for _, c in cubes)
    left = min(c[:, 0].min() for _, c in cubes)
    right = max(c[:, 0].max() for _, c in cubes)

    pad = 12
    width = int((right - left) * SCALE) + pad * 2
    height = int((hi - lo) * SCALE) + pad * 2
    img = Image.new("RGBA", (width, height), (150, 150, 155, 255))
    draw = ImageDraw.Draw(img)

    def px(x, y):
        return (pad + (x - left) * SCALE, pad + (hi - y) * SCALE)

    # back to front, so the chest reads over the wings
    for name, c in sorted(cubes, key=lambda nc: nc[1][:, 2].max()):
        shade = int(60 + 60 * (c[:, 2].max() - lo) / max(1.0, hi - lo))
        poly = hull([px(p[0], p[1]) for p in
                     [(c[i, 0], c[j, 1]) for i in (0, -1) for j in (0, -1)]])
        if len(poly) >= 3:
            draw.polygon(poly, fill=(shade, shade, shade + 4, 255),
                         outline=(40, 40, 44, 255))

    # the model's own eyes
    eyes = eye_row()
    if eyes:
        for y in eyes:
            draw.line([px(left, y), px(right, y)], fill=(80, 255, 120, 255), width=2)
        draw.text((pad + 4, px(left, eyes[1])[1] - 14),
                  f"painted eyes  rig y {eyes[0]:.0f}..{eyes[1]:.0f}", fill=(20, 90, 40, 255))

    # where the game puts the red plane, in rig units, for each setting.
    # blocks = rigY * renderScale / 16 * entityScale, and the eye is
    # 1.62 * entityScale, so entityScale cancels: rigY = 1.62 * 16 / renderScale.
    skull = hi
    trim = PLAYER_HEIGHT * 16.0 / skull
    for label, render_scale, colour in (("trueProportions: true", AUTHORED, (255, 40, 40, 255)),
                                        ("trueProportions: false", trim, (255, 150, 40, 255))):
        rig_y = PLAYER_EYE * 16.0 / render_scale
        if lo <= rig_y <= hi:
            draw.line([px(left, rig_y), px(right, rig_y)], fill=colour, width=3)
            draw.text((pad + 4, px(left, rig_y)[1] + 3),
                      f"{label}  ->  rig y {rig_y:.0f}", fill=colour)

    img.save(OUT)
    print("wrote", os.path.relpath(OUT, HERE))
    print(f"rig: {hi:.0f} units tall, eyes at {eyes[0]:.0f}..{eyes[1]:.0f}"
          if eyes else f"rig: {hi:.0f} units tall")
    for label, render_scale in (("true ", AUTHORED), ("false", trim)):
        rig_y = PLAYER_EYE * 16.0 / render_scale
        print(f"  trueProportions {label} -> eye plane at rig y {rig_y:6.1f}"
              + (f", {abs(rig_y - sum(eyes) / 2):5.1f} units off the eye centre" if eyes else ""))


if __name__ == "__main__":
    main()
