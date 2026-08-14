#!/usr/bin/env python3
"""Front view of the rig, textured, with the game's eye plane drawn across it.

Answers "where will the red line in F3+B actually cut the model", which is
otherwise only findable by transforming and looking. Eye height is a fixed
fraction of the HITBOX (1.62 / 1.8) while the model is sized separately, so the
entity scale cancels and the plane lands at

    rigY = 1.62 * 16 / renderScale

— which depends on the render scale alone, and not at all on where the eyes are
painted. Moving the head in Blockbench does not move the camera.

    python3 tools/preview_eyeline.py        # writes art/eyeline_preview.png
"""

import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from verify_model import rzyx  # noqa: E402  (needs the path above)

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BBMODEL = os.path.join(HERE, "art/dragon_form.bbmodel")
TEX_DIR = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro/textures/entity")
OUT = os.path.join(HERE, "art/eyeline_preview.png")

PLAYER_HEIGHT = 1.8          # blocks
PLAYER_EYE = 1.62            # blocks — Player.STANDING_DIMENSIONS
AUTHORED = 0.25              # exact undo of the 4x authoring
SCALE = 4                    # pixels per rig unit
GRID = 16                    # rig units per grid square


def placed(path):
    """Every cube with its world-space corners, keeping the element itself."""
    bb = json.load(open(path))
    groups = {g["uuid"]: g for g in bb.get("groups", [])}
    els = {e["uuid"]: e for e in bb["elements"]}
    out = []

    def walk(nodes, chain):
        for n in nodes:
            if isinstance(n, str):
                e = els.get(n)
                if not e:
                    continue
                f, t = np.array(e["from"], float), np.array(e["to"], float)
                pts = [np.array([a, b, c]) for a in (f[0], t[0])
                       for b in (f[1], t[1]) for c in (f[2], t[2])]
                if any(abs(v) > 1e-9 for v in e.get("rotation", [0, 0, 0])):
                    o = np.array(e.get("origin", [0, 0, 0]), float)
                    rc = rzyx(*[math.radians(v) for v in e["rotation"]])
                    pts = [o + rc @ (q - o) for q in pts]
                for (o, r) in reversed(chain):
                    pts = [o + r @ (q - o) for q in pts]
                out.append((e, np.array(pts)))
            else:
                g = groups.get(n["uuid"], {})
                walk(n.get("children", []),
                     chain + [(np.array(g.get("origin", [0, 0, 0]), float),
                               rzyx(*[math.radians(v) for v in g.get("rotation", [0, 0, 0])]))])

    walk(bb["outliner"], [])
    return out


def eye_span(cubes):
    """The rig height of the painted eyes, read off the emissive sheet."""
    lit_img = Image.open(os.path.join(TEX_DIR, "dragon_form_eyes.png")).convert("RGBA")
    alpha = lit_img.split()[3]
    lit = [(x, y) for y in range(lit_img.height) for x in range(lit_img.width)
           if alpha.getpixel((x, y)) > 0]
    best = None
    for e, c in cubes:
        u, v = e["uv_offset"]
        w, h, d = [e["to"][i] - e["from"][i] for i in range(3)]
        fx, fy = u + d, v + d                          # the north (front) face
        rows = [p[1] for p in lit if fx <= p[0] < fx + w and fy <= p[1] < fy + h]
        if not rows:
            continue
        top, bot = c[:, 1].max(), c[:, 1].min()
        span = (top - (max(rows) + 1 - fy) / h * (top - bot),
                top - (min(rows) - fy) / h * (top - bot))
        if best is None or span[1] > best[1]:
            best = span                                # highest lit face is the face
    return best


def main():
    cubes = placed(BBMODEL)
    sheet = Image.open(os.path.join(TEX_DIR, "dragon_form.png")).convert("RGBA")

    xs = np.concatenate([c[:, 0] for _, c in cubes])
    ys = np.concatenate([c[:, 1] for _, c in cubes])
    left, right, lo, hi = xs.min(), xs.max(), ys.min(), ys.max()

    pad = 20
    width = int((right - left) * SCALE) + pad * 2
    height = int((hi - lo) * SCALE) + pad * 2
    img = Image.new("RGBA", (width, height), (150, 150, 155, 255))
    draw = ImageDraw.Draw(img)

    def px(x, y):
        return (pad + (x - left) * SCALE, pad + (hi - y) * SCALE)

    for gx in range(int(left // GRID) * GRID, int(right) + GRID, GRID):
        draw.line([px(gx, lo), px(gx, hi)], fill=(120, 120, 126, 255))
    for gy in range(int(lo // GRID) * GRID, int(hi) + GRID, GRID):
        draw.line([px(left, gy), px(right, gy)], fill=(120, 120, 126, 255))

    # Back to front, drawing each cube's front face from the sheet. Rotated
    # cubes are pasted into their projected bounds rather than warped — enough
    # for placement, which is all this is for.
    for e, c in sorted(cubes, key=lambda ec: ec[1][:, 2].max()):
        u, v = e["uv_offset"]
        w, h, d = [e["to"][i] - e["from"][i] for i in range(3)]
        if w <= 0 or h <= 0:
            continue
        face = sheet.crop((int(u + d), int(v + d), int(u + d + w), int(v + d + h)))
        x0, x1 = c[:, 0].min(), c[:, 0].max()
        y0, y1 = c[:, 1].min(), c[:, 1].max()
        tl, br = px(x0, y1), px(x1, y0)
        pw, ph = max(1, int(br[0] - tl[0])), max(1, int(br[1] - tl[1]))
        face = face.resize((pw, ph), Image.NEAREST)
        # A rotated cube pasted into its projected bounds is a stretched slab,
        # and the wings swept out to the sides swamp everything. Faded, so the
        # silhouette still reads without burying the part being measured.
        if "wing" in e.get("name", ""):
            face.putalpha(face.split()[3].point(lambda a: a // 3))
        img.alpha_composite(face, (int(tl[0]), int(tl[1])))

    eyes = eye_span(cubes)
    skull = hi
    trim = PLAYER_HEIGHT * 16.0 / skull
    lines = [("painted eyes", eyes[0], (60, 230, 110, 255)),
             ("painted eyes", eyes[1], (60, 230, 110, 255))] if eyes else []
    for label, render_scale, colour in (
            ("trueProportions: false", trim, (255, 150, 30, 255)),
            ("trueProportions: true", AUTHORED, (255, 40, 40, 255))):
        lines.append((label, PLAYER_EYE * 16.0 / render_scale, colour))

    placed_labels = []
    for label, y, colour in sorted(lines, key=lambda t: -t[1]):
        if not lo <= y <= hi:
            continue
        draw.line([px(left, y), px(right, y)], fill=colour, width=2)
        ly = px(left, y)[1] - 11
        while any(abs(ly - other) < 11 for other in placed_labels):
            ly += 11                                  # stagger, do not overlap
        placed_labels.append(ly)
        draw.text((pad + 3, ly), f"{label}  y {y:.0f}", fill=colour)

    img.save(OUT)

    # The head on its own, which is the comparison that actually settles it.
    if eyes:
        top = px(left, min(hi, eyes[1] + 26))[1]
        bottom = px(left, max(lo, eyes[0] - 34))[1]
        mid = (left + right) / 2
        head = img.crop((int(px(mid - 26, 0)[0]), int(top),
                         int(px(mid + 26, 0)[0]), int(bottom)))
        head = head.resize((head.width * 3, head.height * 3), Image.NEAREST)
        head.save(OUT.replace(".png", "_head.png"))
        print("wrote", os.path.relpath(OUT.replace(".png", "_head.png"), HERE))
    print("wrote", os.path.relpath(OUT, HERE))
    print(f"rig {hi:.0f} units tall" + (f", eyes at {eyes[0]:.0f}..{eyes[1]:.0f}" if eyes else ""))
    for label, render_scale in (("true ", AUTHORED), ("false", trim)):
        y = PLAYER_EYE * 16.0 / render_scale
        off = f", {abs(y - sum(eyes) / 2):5.1f} off the eye centre" if eyes else ""
        print(f"  trueProportions {label} -> eye plane at rig y {y:6.1f}{off}")


if __name__ == "__main__":
    main()
