#!/usr/bin/env python3
"""Generates the mod's non-dragon textures: the homing crystal, the purple
heart particle, and the enderman's happy face.

Scripted rather than hand-painted so the box-UV slots are placed by the same
arithmetic Minecraft uses to read them. The homing crystal shipped for a while
with two faces of its core landing outside the painted area, which is exactly
the sort of thing laying the slots out by hand gets wrong.

    python3 tools/make_textures.py
"""

import os

from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro/textures")

# The anchor's palette. Warm orange so it never reads as a live End Crystal,
# which is magenta -- that difference is the whole point of the texture.
EDGE = (0x6B, 0x2C, 0x00, 255)
DEEP = (0xA8, 0x45, 0x06, 255)
MID = (0xE0, 0x7A, 0x14, 255)
LIT = (0xFF, 0xA8, 0x3C, 255)
HOT = (0xFF, 0xDD, 0xA0, 255)
CAGE = (0xF7, 0xE9, 0xD4, 255)
CAGE_DIM = (0xCF, 0xB0, 0x8A, 255)
CLEAR = (0, 0, 0, 0)


def box_slots(u, v, w, h, d):
    """The six face rectangles Minecraft reads for a box, in sheet pixels.

    Row one holds down and up (each w x d); row two holds the four sides.
    """
    return {
        "down": (u + d, v, w, d),
        "up": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def core_face(w, h):
    """The anchor's heart: a bright rune-diamond inside a bevelled edge."""
    tile = Image.new("RGBA", (w, h))
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for x in range(w):
        for y in range(h):
            if x in (0, w - 1) or y in (0, h - 1):
                c = EDGE
            else:
                reach = abs(x - cx) + abs(y - cy)
                c = HOT if reach <= 1.5 else LIT if reach <= 3.0 else MID
            tile.putpixel((x, y), c)
    return tile


def cage_face(w, h):
    """The shell: a strut frame with an X brace, open in between."""
    tile = Image.new("RGBA", (w, h), CLEAR)
    for x in range(w):
        for y in range(h):
            on_frame = x in (0, w - 1) or y in (0, h - 1)
            on_brace = (w == h) and (x == y or x + y == w - 1)
            if on_frame:
                tile.putpixel((x, y), CAGE)
            elif on_brace:
                tile.putpixel((x, y), CAGE_DIM)
    return tile


def base_face(w, h, top):
    """The pedestal: flat plate up top, banded stone on the sides."""
    tile = Image.new("RGBA", (w, h))
    for x in range(w):
        for y in range(h):
            if top:
                ring = max(abs(x - (w - 1) / 2), abs(y - (h - 1) / 2))
                c = HOT if ring <= 1 else LIT if ring <= 3 else DEEP if ring >= w / 2 - 1 else MID
            else:
                c = LIT if y == 0 else DEEP if y >= h - 1 else MID
            tile.putpixel((x, y), c)
    return tile


def crystal_entity():
    """64x32: glass shell at (0,0), core at (32,0), pedestal at (0,16).

    Those offsets are EndCrystalModel's, so the sheet has to match them
    exactly or faces read from blank space.
    """
    sheet = Image.new("RGBA", (64, 32), CLEAR)
    for name, (x, y, w, h) in box_slots(0, 0, 8, 8, 8).items():
        sheet.paste(cage_face(w, h), (x, y))
    for name, (x, y, w, h) in box_slots(32, 0, 8, 8, 8).items():
        sheet.paste(core_face(w, h), (x, y))
    for name, (x, y, w, h) in box_slots(0, 16, 12, 4, 12).items():
        sheet.paste(base_face(w, h, name in ("up", "down")), (x, y))
    return sheet


def crystal_item():
    """16x16 icon: the same octahedron, caged, read head-on."""
    icon = Image.new("RGBA", (16, 16), CLEAR)
    cx = cy = 7.5
    for x in range(16):
        for y in range(16):
            reach = abs(x - cx) + abs(y - cy)
            if reach > 7.0:
                continue
            if reach > 5.5:
                icon.putpixel((x, y), CAGE)          # outer cage
            elif reach > 4.5:
                icon.putpixel((x, y), EDGE)
            elif reach > 2.5:
                icon.putpixel((x, y), MID if (x + y) % 2 else LIT)
            elif reach > 1.0:
                icon.putpixel((x, y), LIT)
            else:
                icon.putpixel((x, y), HOT)
    # two cage struts across the waist so it reads as caged, not just a gem
    for x in range(16):
        if abs(x - cx) + abs(6 - cy) <= 7.0:
            icon.putpixel((x, 6), CAGE_DIM)
        if abs(x - cx) + abs(9 - cy) <= 7.0:
            icon.putpixel((x, 9), CAGE_DIM)
    return icon


HEART = [
    "..##.##.",
    ".#######",
    "########",
    "########",
    ".######.",
    "..####..",
    "...##...",
    "........",
]


def purple_heart():
    """8x8 particle. Endermen are magenta-eyed, so the affection matches."""
    dark = (0x6A, 0x20, 0x90, 255)
    body = (0xB1, 0x4C, 0xE0, 255)
    glow = (0xE7, 0xA8, 0xFF, 255)
    img = Image.new("RGBA", (8, 8), CLEAR)
    for y, row in enumerate(HEART):
        for x, ch in enumerate(row):
            if ch != "#":
                continue
            edge = any(
                not (0 <= x + dx < 8 and 0 <= y + dy < 8) or HEART[y + dy][x + dx] != "#"
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            )
            img.putpixel((x, y), dark if edge else glow if (x + y) % 3 == 0 else body)
    return img


def happy_face():
    """The ^^ itself, emissive, drawn over the mask below.

    Sits one row higher and tighter than the first attempt, which put two pink
    patches out on the cheeks either side of the vanilla eyes and read as
    blushing rather than as an expression.
    """
    sheet = Image.new("RGBA", (64, 32), CLEAR)
    x0, y0, _, _ = box_slots(0, 0, 8, 8, 8)["north"]       # the face
    # One purple throughout. The apex used to be near-white, which under an
    # additive render type blew out to a plain white pixel.
    bright = (0xF0, 0xA8, 0xFF, 255)
    # Three wide with two clear pixels between them: at four wide the inner
    # legs met in the middle and the pair read as one flat bar.
    for cx in (0, 5):                                      # one caret per eye
        sheet.putpixel((x0 + cx + 1, y0 + 3), bright)      # apex
        sheet.putpixel((x0 + cx, y0 + 4), bright)          # legs falling away
        sheet.putpixel((x0 + cx + 2, y0 + 4), bright)
    return sheet


def happy_mask():
    """An opaque band that hides the enderman's own eyes.

    RenderType.eyes is additive, so the ^^ on its own could only ever be drawn
    on top of the glowing eyes rather than instead of them — two bright shapes
    at once, which is why it read as blush. This pass is ordinary cutout, so it
    covers them, and it is the same near-black as an enderman so nothing shows
    but the expression.
    """
    sheet = Image.new("RGBA", (64, 32), CLEAR)
    x0, y0, _, _ = box_slots(0, 0, 8, 8, 8)["north"]
    skin = (0x0F, 0x0F, 0x12, 255)
    for x in range(8):
        for y in range(1, 6):                              # the whole eye band, and the
            # row the carets sit on after being nudged down one
            sheet.putpixel((x0 + x, y0 + y), skin)
    return sheet


def main():
    out = {
        "entity/homing_crystal.png": crystal_entity(),
        "item/homing_crystal.png": crystal_item(),
        "particle/purple_heart.png": purple_heart(),
        "entity/enderman_happy.png": happy_face(),
        "entity/enderman_happy_mask.png": happy_mask(),
    }
    for rel, img in out.items():
        path = os.path.join(ASSETS, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        print(f"wrote {rel:34s} {img.size[0]}x{img.size[1]}")


if __name__ == "__main__":
    main()
