#!/usr/bin/env python3
"""Paint the court's sheets, straight from the packed rig.

Original art — no vanilla enderman texture ships here and none can. It reads
`art/shade_base.bbmodel` rather than carrying its own copy of the layout, so
re-rigging and re-packing is enough: whatever cubes the artist added get
painted, at whatever offsets the packer gave them.

Per shade:

  shade_<name>.png        the skin
  shade_<name>_eyes.png   the emissive pass, eyes only

...plus `shade_guide.png`, the same layout with every island outlined and named.
Open that beside the rig and it is obvious which rectangle is which piece.

    python3 tools/make_shade_texture.py
"""

import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pack_shade_rig import bb_slots, dims, source_offset, AUTHOR_SCALE   # noqa: E402

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, "art")
RIG = os.path.join(OUT, "shade_base.bbmodel")

CLEAR = (0, 0, 0, 0)
# The End palette, sampled in DESIGN.md section 2.
SKIN_DARK = (0x0B, 0x0B, 0x0E, 255)
SKIN_MID = (0x14, 0x14, 0x19, 255)
SKIN_LIT = (0x1E, 0x1E, 0x26, 255)

# One accent each, matching the colour their chat is spoken in.
COURT = {
    "vaelle":   (0xE0, 0x2B, 0x2B),
    "keshaire": (0x2B, 0x6B, 0xE0),
    "nyrelle":  (0x2B, 0xC4, 0x5A),
    "orrinne":  (0xE0, 0x8A, 0x1E),
}

# Where the accent goes. The rig names its own pieces, so this can speak about
# anatomy rather than about cube indices.
SPINE = {"body1", "body2"}
CROWN = {"head"}
TRIM = {"neck", "bosom"}


def rig():
    with open(RIG) as f:
        model = json.load(f)
    res = model["resolution"]
    parts = []
    for e in model["elements"]:
        # PACKED dimensions, from an offset stored in source units. Not both in
        # the same units, which is peculiar and is nonetheless what the sheet
        # measurably IS: computing islands at source scale accounts for 1986 of
        # the 8717 painted texels, and at packed scale for 8068 of them.
        #
        # This used to divide by AUTHOR_SCALE, so every island it drew was half
        # the size of the one the game reads. The guide was therefore wrong
        # everywhere, which is a poor thing for a guide to be, and wrong quietly,
        # which is worse -- it looks like a UV map either way.
        w, h, d = dims(e)
        u, v = e["uv_offset"] if "uv_offset" in e else source_offset(e)
        parts.append((e["name"], u, v, w, h, d))
    return res["width"], res["height"], parts


def faces(u, v, w, h, d):
    for face, (x, y, fw, fh) in bb_slots(u, v, w, h, d).items():
        yield face, int(round(x)), int(round(y)), int(round(fw)), int(round(fh))


def skin(size, parts, accent):
    """Near-black, lit from the front and above, with the shade's own accent."""
    w_sheet, h_sheet = size
    sheet = Image.new("RGBA", (w_sheet, h_sheet), CLEAR)
    px = sheet.load()
    for name, u, v, w, h, d in parts:
        for face, x0, y0, fw, fh in faces(u, v, w, h, d):
            base = {"north": SKIN_LIT, "up": SKIN_LIT,
                    "south": SKIN_DARK, "down": SKIN_DARK}.get(face, SKIN_MID)
            for x in range(x0, x0 + fw):
                for y in range(y0, y0 + fh):
                    if not (0 <= x < w_sheet and 0 <= y < h_sheet):
                        continue
                    shift = 3 if (x * 7 + y * 5) % 11 == 0 else 0
                    px[x, y] = (min(base[0] + shift, 255),
                                min(base[1] + shift, 255),
                                min(base[2] + shift, 255), 255)
            if name in SPINE and face == "south":
                for y in range(y0, y0 + fh):
                    _dot(px, x0 + fw // 2, y, accent, w_sheet, h_sheet)
                    _dot(px, x0 + fw // 2 + 1, y, accent, w_sheet, h_sheet)
            if name in CROWN and face == "up":
                for x in range(x0, x0 + fw):
                    _dot(px, x, y0 + 2, accent, w_sheet, h_sheet)
                    _dot(px, x, y0 + 3, accent, w_sheet, h_sheet)
            if name in TRIM and face in ("north", "south"):
                for x in range(x0, x0 + fw):
                    _dot(px, x, y0, accent, w_sheet, h_sheet)
    return sheet


def _dot(px, x, y, colour, w, h):
    if 0 <= x < w and 0 <= y < h:
        px[x, y] = colour + (255,)


def eyes(size, parts, accent):
    """The emissive pass: two lit eyes on the head's front face."""
    w_sheet, h_sheet = size
    sheet = Image.new("RGBA", (w_sheet, h_sheet), CLEAR)
    px = sheet.load()
    for name, u, v, w, h, d in parts:
        if name not in CROWN:
            continue
        for face, x0, y0, fw, fh in faces(u, v, w, h, d):
            if face != "north":
                continue
            row = y0 + int(fh * 0.35)
            hot = tuple(min(c + 60, 255) for c in accent)
            band = max(1, fw // 8)
            for side in (int(fw * 0.18), int(fw * 0.66)):
                for dx in range(band * 2):
                    for dy in range(max(1, band)):
                        _dot(px, x0 + side + dx, row + dy, accent, w_sheet, h_sheet)
                _dot(px, x0 + side, row, hot, w_sheet, h_sheet)
    return sheet


def guide(size, parts, over=None):
    """Every island outlined and named, for painting against.

    Transparent where it says nothing, so it can be dropped straight on top of
    the skin as a layer in whatever you paint in -- which is the whole point of
    it. `over` composites it onto a copy of the real sheet as well, for a
    single file you can open and see both at once.
    """
    w_sheet, h_sheet = size
    sheet = Image.new("RGBA", (w_sheet, h_sheet), (0, 0, 0, 0))
    draw = ImageDraw.Draw(sheet)
    tints = [(0xE0, 0x2B, 0x2B), (0x2B, 0x6B, 0xE0), (0x2B, 0xC4, 0x5A),
             (0xE0, 0x8A, 0x1E), (0xC0, 0x60, 0xE0), (0x30, 0xC0, 0xC0)]
    px = sheet.load()
    for i, (name, u, v, w, h, d) in enumerate(parts):
        for _face, x0, y0, fw, fh in faces(u, v, w, h, d):
            for x in range(x0, x0 + fw):
                _dot(px, x, y0, (0x4A, 0x4A, 0x54), w_sheet, h_sheet)
            for y in range(y0, y0 + fh):
                _dot(px, x0, y, (0x4A, 0x4A, 0x54), w_sheet, h_sheet)
        iw, ih = 2 * (d + w), d + h
        draw.rectangle([u, v, u + iw - 1, v + ih - 1],
                       outline=tints[i % len(tints)] + (255,))
        draw.text((u + 2, v + 1), name, fill=tints[i % len(tints)] + (255,))
    if over is not None:
        stacked = over.convert("RGBA").copy()
        stacked.alpha_composite(sheet)
        return sheet, stacked
    return sheet


def main():
    w, h, parts = rig()
    size = (w, h)
    written = []
    for name, accent in COURT.items():
        for suffix, image in (("", skin(size, parts, accent)),
                              ("_eyes", eyes(size, parts, accent))):
            path = os.path.join(OUT, f"shade_{name}{suffix}.png")
            image.save(path)
            written.append(path)
    shipped = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro"
                                 "/textures/entity/shade.png")
    overlay, stacked = guide(size, parts, over=Image.open(shipped))
    path = os.path.join(OUT, "shade_guide.png")
    overlay.save(path)
    written.append(path)
    path = os.path.join(OUT, "shade_guide_over_skin.png")
    stacked.save(path)
    written.append(path)

    for path in written:
        image = Image.open(path)
        print(f"wrote {os.path.relpath(path, HERE):<32} {image.width}x{image.height}")
    print(f"\n{len(parts)} pieces on a {w}x{h} sheet, "
          f"{AUTHOR_SCALE} texels per final model unit")


if __name__ == "__main__":
    main()
