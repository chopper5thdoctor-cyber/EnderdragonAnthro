#!/usr/bin/env python3
"""Write the wingbeat into art/dragon_form.bbmodel as a Blockbench animation.

Usage:  python3 tools/seed_flap.py [art/dragon_form.bbmodel]

The beat was born as three lines of trigonometry in DragonFormModel.flap --
the same curves EnderDragonRenderer drives the canon dragon's wing and wing
tip with. That is a fine way to write an animation and a terrible way to tweak
one: every adjustment is a number in a Java file, compiled, launched and
squinted at.

So it is moved into the rig, where it can be scrubbed. This script seeds it
ONCE from the original curves so the motion in Blockbench is the motion that
was in game to the degree; after that the .bbmodel is the source of truth and
bbmodel_to_java.py bakes whatever is in it back into flap(). Re-running this
overwrites the animation, so do not, unless you mean to throw tweaks away.

## The curves

    t     = phase * 2pi                       phase runs 0..1 over one beat
    sweep = cos(t) * 0.2                      wings forward and back
    lift  = (sin(t) + 0.125) * 0.8            wings up and down
    trail = -(sin(t + 2.0) + 0.5) * 0.75      the tip, two radians behind

The tip lagging the wing is what makes the membrane snap rather than swing as
one board, and it is the only part of this worth being careful with.

## Space

Keyframe rotations are DELTAS on the group's rest pose, in both Blockbench and
Minecraft -- Blockbench's animator offsets a bone from where it is posed, and
AnimationChannel.Targets.ROTATION adds to the part's current rotation. That
matches flap(), which adds to the folded pose the artist chose rather than
replacing it. A wing that flapped to zero would jump the moment the beat ended.

The deltas are stored the way Blockbench stores everything, so they need the
same conversion the geometry does, backwards: java_x = -bb_x, java_y = +bb_y,
java_z = -bb_z, so bb_x = -java_x and bb_z = -java_z. Degrees, not radians.
"""
import json
import math
import os
import sys
import uuid

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

NAME = "wing_flap"
BEAT_TICKS = 12                  # what DragonWings ran the beat over
TPS = 20.0
DEG = 180.0 / math.pi
# One keyframe per tick. Dense enough that the sine reads as a sine, sparse
# enough that a keyframe is a thing you grab rather than a wall of them, and
# on the timeline's own 20fps grid so they land on whole ticks.
KEYS = BEAT_TICKS


def curves(phase):
    t = phase * math.pi * 2.0
    sweep = math.cos(t) * 0.2
    lift = (math.sin(t) + 0.125) * 0.8
    trail = -(math.sin(t + 2.0) + 0.5) * 0.75
    return sweep, lift, trail


def pose(phase):
    """Blockbench-space rotation deltas, in degrees, per bone."""
    sweep, lift, trail = curves(phase)
    return {
        # java: xRot -= sweep, zRot += lift   ->  bb: x += sweep, z -= lift
        "wing_right": (sweep * DEG, 0.0, -lift * DEG),
        # java: xRot -= sweep, zRot -= lift   ->  bb: x += sweep, z += lift
        "wing_left": (sweep * DEG, 0.0, lift * DEG),
        # java: zRot += trail                 ->  bb: z -= trail
        "wing_tip_right": (0.0, 0.0, -trail * DEG),
        # java: zRot -= trail                 ->  bb: z += trail
        "wing_tip_left": (0.0, 0.0, trail * DEG),
    }


def main(path):
    bb = json.load(open(path))
    groups = {g["name"]: g["uuid"] for g in bb.get("groups", [])}
    bones = list(pose(0.0))
    missing = [b for b in bones if b not in groups]
    if missing:
        sys.exit(f"ERROR: the rig has no group(s) {missing}")

    animators = {}
    for bone in bones:
        keys = []
        for k in range(KEYS):
            x, y, z = pose(k / KEYS)[bone]
            keys.append({
                "channel": "rotation",
                # Strings, because that is how Blockbench stores them: every
                # keyframe value is a molang expression, and a number is just
                # the simplest one.
                "data_points": [{"x": f"{x:.4f}", "y": f"{y:.4f}", "z": f"{z:.4f}"}],
                "uuid": str(uuid.uuid4()),
                "time": round(k / TPS, 4),
                "color": -1,
                "interpolation": "catmullrom",
            })
        animators[groups[bone]] = {"name": bone, "type": "bone", "keyframes": keys}

    animation = {
        "uuid": str(uuid.uuid4()),
        "name": NAME,
        "loop": "loop",
        "override": False,
        # Seconds. This is the beat's duration, and the converter reads it back
        # out into BEAT_TICKS -- so lengthening the animation in Blockbench
        # lengthens the beat in game.
        "length": round(BEAT_TICKS / TPS, 4),
        "snapping": int(TPS),
        "selected": False,
        "anim_time_update": "",
        "blend_weight": "",
        "start_delay": "",
        "loop_delay": "",
        "animators": animators,
    }

    # Replaced by name, so re-running cannot leave two of them.
    bb["animations"] = [a for a in bb.get("animations", []) if a.get("name") != NAME]
    bb["animations"].append(animation)
    json.dump(bb, open(path, "w"), indent=2)
    print(f"{NAME}: {KEYS} keyframes x {len(bones)} bones, "
          f"{animation['length']}s ({BEAT_TICKS} ticks), looping")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "art/dragon_form.bbmodel"))
