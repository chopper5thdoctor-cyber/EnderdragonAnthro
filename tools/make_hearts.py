#!/usr/bin/env python3
"""Derive the HUD heart set from the sprites that were actually drawn by hand.

Usage:
    python3 tools/make_hearts.py            # write the derived sprites
    python3 tools/make_hearts.py --check    # fail if any is out of date

Vanilla asks for ten heart sprites while a dragon is on screen, and only three
of them are drawings. The other seven are the same three under two mechanical
rules -- the half heart is the left five columns of the full one, and the
blinking heart is the ordinary one lifted 60 per channel -- which is exactly the
kind of thing that goes wrong quietly when it is done by hand ten times. One of
them ends up lifted 59, or a half is cut at four columns, and nobody notices
because a heart is nine pixels wide and it is only visible for two ticks at a
time while you are being killed.

So the drawings are committed and the derivations are computed. The three
authored sprites are:

    full.png              the ordinary purple heart
    hardcore_full.png     the veined one, for a hardcore world
    container.png         the empty socket

Both rules were measured off the original six sprites rather than invented, and
running this over them reproduces all six byte for byte, which is what --check
asserts. That makes it a check as well as a generator: it proves the art in the
jar is still the art these rules describe.

## The container the hardcore mode does not have

Vanilla names a container_hardcore and a container_hardcore_blinking, and both
are byte-identical to the ordinary pair -- verified against 1.21.1's client jar,
same SHA-256. The veins are in the heart, not in the socket it sits in. So there
is no fourth drawing and no fourth derivation; DragonHeartsMixin points those
two names at the ordinary container instead, which is what vanilla is doing
underneath anyway.
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HEARTS = os.path.join(HERE, "src/main/resources/assets/enderdragonanthro"
                            "/textures/gui/sprites/hud/heart")

#: How much brighter a heart goes on the tick it is hit. Measured, not chosen:
#: this is the exact difference between the full and full_blinking that were
#: drawn for the mod, and the container pair agrees with it.
BLINK_LIFT = 60
#: A half heart is the left half of a whole one. Nine pixels do not halve, and
#: vanilla rounds up, so the left five columns survive and the right four go.
HALF_COLUMNS = 5


def blink(image):
    """The same heart, lit.

    The outline is exempt. Pure black is the silhouette rather than a colour,
    and lifting it to grey costs the heart its edge against a bright hotbar --
    which is the one moment the blink exists to be legible in.
    """
    out = image.copy()
    for x in range(image.width):
        for y in range(image.height):
            r, g, b, a = image.getpixel((x, y))
            if a == 0 or (r, g, b) == (0, 0, 0):
                continue
            out.putpixel((x, y), (min(255, r + BLINK_LIFT),
                                  min(255, g + BLINK_LIFT),
                                  min(255, b + BLINK_LIFT), a))
    return out


def half(image):
    """The left five columns, kept exactly; the rest cleared."""
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    out.paste(image.crop((0, 0, HALF_COLUMNS, image.height)), (0, 0))
    return out


def derive():
    """Every sprite this file is responsible for, as name -> image."""
    made = {}
    for authored in ("full", "hardcore_full", "container"):
        source = Image.open(os.path.join(HEARTS, authored + ".png")).convert("RGBA")
        lit = blink(source)
        made[authored + "_blinking"] = lit
        if authored != "container":
            # A container has no half. An empty socket is an empty socket.
            stem = authored[:-len("full")]          # "" or "hardcore_"
            made[stem + "half"] = half(source)
            made[stem + "half_blinking"] = half(lit)
    return made


def main(check=False):
    bad = []
    for name, image in sorted(derive().items()):
        path = os.path.join(HEARTS, name + ".png")
        if check:
            if not os.path.exists(path):
                bad.append(f"{name}.png is missing")
                continue
            have = Image.open(path).convert("RGBA")
            if have.tobytes() != image.tobytes():
                bad.append(f"{name}.png is not what the rules say it should be")
            else:
                print(f"  ok    {name}.png")
        else:
            image.save(path)
            print(f"  wrote {name}.png")

    if bad:
        for line in bad:
            print(f"  FAIL  {line}")
        sys.exit("FAIL: the heart set does not match its own derivation rules")
    if check:
        print("PASS: every derived heart matches the sprite it comes from")


if __name__ == "__main__":
    main(check="--check" in sys.argv)
