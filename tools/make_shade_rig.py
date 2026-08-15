#!/usr/bin/env python3
"""Write art/shade_base.bbmodel — a starting rig for the court's own models.

This is ORIGINAL geometry, not Mojang's enderman. No vanilla model ships in
this repository and none can: what is useful to an artist here is not a copy of
the enderman anyway, it is a blank with the right proportions and the right bone
names, so that whatever gets sculpted on top of it drops into the same pipeline
the dragon uses.

Proportions are an enderman's in outline — very tall, very narrow, long limbs,
a small head — at vanilla scale, 16 units to the block, standing 2.9 blocks.
The mod scales a shade by 4/2.9 at runtime, so this reads as four blocks in
game without the rig having to know that.

Bone names match the contract in MODELING_GUIDE.md: head, body, right_arm,
left_arm, right_leg, left_leg under the root. Keep those six and everything
else is yours.

    python3 tools/make_shade_rig.py
"""

import json
import os
import uuid

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, "art/shade_base.bbmodel")

GROUND = 0.0          # bb y of the foot plane
TEX_W, TEX_H = 64, 64

# name, parent, pivot, (from), (to), uv
# Laid out from the ground up. An enderman is mostly leg: the hips sit at 26 of
# a 46-unit body, which is what makes the silhouette read at a distance.
PARTS = [
    ("head",      None, (0, 39, 0), (-4, 39, -4), (4, 47, 4),   (0, 0)),
    ("body",      None, (0, 26, 0), (-4, 26, -2), (4, 39, 2),   (32, 16)),
    ("right_arm", None, (-4, 38, 0), (-6, 20, -1), (-4, 38, 1), (56, 0)),
    ("left_arm",  None, (4, 38, 0), (4, 20, -1), (6, 38, 1),    (56, 0)),
    ("right_leg", None, (-2, 26, 0), (-3, 0, -1), (-1, 26, 1),  (56, 0)),
    ("left_leg",  None, (2, 26, 0), (1, 0, -1), (3, 26, 1),     (56, 0)),
]


def uid():
    return str(uuid.uuid4())


def main():
    elements, outliner, groups = [], [], []
    for name, _parent, pivot, lo, hi, uv in PARTS:
        cube_id = uid()
        elements.append({
            "name": name,
            "box_uv": True,
            "rescale": False,
            "locked": False,
            "type": "cube",
            "uuid": cube_id,
            "from": list(lo),
            "to": list(hi),
            "autouv": 0,
            "color": 0,
            "origin": list(pivot),
            "uv_offset": list(uv),
            "faces": {f: {"uv": [0, 0, 0, 0], "texture": 0} for f in
                      ("north", "east", "south", "west", "up", "down")},
        })
        group_id = uid()
        groups.append({
            "name": name,
            "origin": list(pivot),
            "rotation": [0, 0, 0],
            "uuid": group_id,
            "export": True,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "autouv": False,
        })
        outliner.append({
            "name": name,
            "origin": list(pivot),
            "rotation": [0, 0, 0],
            "uuid": group_id,
            "export": True,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "autouv": False,
            "children": [cube_id],
        })

    model = {
        "meta": {
            "format_version": "4.5",
            "model_format": "modded_entity",
            "box_uv": True,
        },
        "name": "shade_base",
        "model_identifier": "shade_base",
        "visible_box": [2, 4, 0],
        "variable_placeholders": "",
        "resolution": {"width": TEX_W, "height": TEX_H},
        "elements": elements,
        "outliner": outliner,
        "groups": groups,
        "textures": [],
    }

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump(model, f, indent=2)
    top = max(e["to"][1] for e in elements)
    print(f"wrote {os.path.relpath(OUT, HERE)}")
    print(f"  {len(elements)} cubes, {top} units tall "
          f"({top / 16.0:.2f} blocks before the 4/2.9 runtime scale)")
    print("  bones: " + ", ".join(p[0] for p in PARTS))


if __name__ == "__main__":
    main()
