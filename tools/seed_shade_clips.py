#!/usr/bin/env python3
"""Write the shade's animations into art/shade_base.bbmodel, once.

Usage:  python3 tools/seed_shade_clips.py [art/shade_base.bbmodel]

A shade has never had an animation of its own. ShadeLayer draws the rig over a
real enderman and calls copyPose, so every walk cycle, idle sway and carry
stance you have ever seen on one of the court is vanilla HumanoidModel's,
borrowed for the price of six rotations. That was the right call to start with
-- it is a great deal of motion for no work -- but it means there is nothing to
open and nothing to tweak, and "edit the shade's animations" has no file to
point at.

This seeds five, so there is. It runs ONCE: after this the .bbmodel is the
source of truth and shade_to_java.py bakes whatever is in it. Re-running
overwrites, so do not, unless you mean to throw tweaks away.

## What is seeded, and how honest it is

Four of the five are the motion that is on screen today, rewritten as keyframes:

    idle     the arm sway HumanoidModel adds every tick
    walk     the stride, one full cycle
    carry    arms raised for a held block
    angry    the enderman's aggressive stance

They are faithful in shape rather than to the last decimal, and two of them
cannot be exact by construction. Vanilla's idle is two sine waves whose periods
(2pi/0.09 and 2pi/0.067 ticks) do not divide into each other, so no finite loop
holds both; this uses the slower one's period and lets the faster drift. Walk is
driven by limbSwing rather than by time, so it IS periodic and IS exact, but
seeded at full stride -- the layer scales it by limbSwingAmount, which is what
makes a shade creeping forward swing less than one at a run.

The fifth, `pet`, has no vanilla original. It is invented: she turns to the
dragon and inclines her head.

## Space

Blockbench stores keyframe rotations as DELTAS on the group's rest pose, in
degrees, in its own axis convention. All six of the shade's bones rest at zero
-- checked, not assumed -- so for this rig a delta is also an absolute angle,
and what the animator shows is exactly what plays. The conversion back to
Java's space is java_x = -bb_x, java_y = +bb_y, java_z = -bb_z, so seeding goes
the other way: bb_x = -java_x, bb_z = -java_z.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RIG = os.path.join(HERE, "art/shade_base.bbmodel")

BONES = ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")

#: Samples per clip. Enough that a spline through them is smooth and few enough
#: that the animator stays editable by hand -- the point is to be tweaked.
KEYS = 12


def deg(radians):
    return math.degrees(radians)


def bb(x=0.0, y=0.0, z=0.0):
    """Java-space radians -> the degrees Blockbench wants, axes flipped."""
    return [f"{-deg(x):.4f}", f"{deg(y):.4f}", f"{-deg(z):.4f}"]


# --------------------------------------------------------------------- clips

def idle(t):
    """HumanoidModel's ambient sway, at the slower of its two periods.

    rightArm.zRot = cos(age*0.09)*0.05 + 0.05, mirrored on the left, plus a
    smaller x wobble. The enderman halves every arm x before it draws, so the
    x term is halved here and the z term is not -- EndermanModel does not touch
    z at all.
    """
    age = t * (2.0 * math.pi / 0.09)          # one full z-sway
    return {
        "right_arm": bb(x=math.sin(age * 0.067 / 0.09) * 0.05 * 0.5,
                        z=math.cos(age * 0.09) * 0.05 + 0.05),
        "left_arm": bb(x=-math.sin(age * 0.067 / 0.09) * 0.05 * 0.5,
                       z=-(math.cos(age * 0.09) * 0.05 + 0.05)),
    }


def walk(t):
    """One stride, at full swing.

    HumanoidModel drives limbs off limbSwing; the enderman then halves the arms
    and legs, and clamps the arms to +/-0.4. Seeded with limbSwingAmount = 1, so
    this is the widest the stride ever gets and the layer scales it down.
    """
    p = t * 2.0 * math.pi
    clamp = lambda v: max(-0.4, min(0.4, v))
    return {
        "right_arm": bb(x=clamp(math.cos(p + math.pi) * 2.0 * 0.5 * 0.5)),
        "left_arm": bb(x=clamp(math.cos(p) * 2.0 * 0.5 * 0.5)),
        "right_leg": bb(x=math.cos(p) * 1.4 * 0.5),
        "left_leg": bb(x=math.cos(p + math.pi) * 1.4 * 0.5),
    }


def carry(t):
    """Arms up round a block, breathing.

    EndermanModel's carry is arm.xRot*0.5 - pi/10 on both sides. Static in
    vanilla; given a slow rise and fall here so a shade standing holding
    something is not a statue.
    """
    breathe = math.sin(t * 2.0 * math.pi) * 0.04
    return {
        "right_arm": bb(x=-math.pi / 10.0 + breathe, z=0.12),
        "left_arm": bb(x=-math.pi / 10.0 + breathe, z=-0.12),
    }


def angry(t):
    """The stance, trembling.

    Vanilla shakes the arms by a fixed 0.4 while creepy. The tremble is on a
    short period on purpose -- it should read as held rage rather than as a
    wave.
    """
    shake = math.sin(t * 2.0 * math.pi * 3.0) * 0.06
    return {
        "head": bb(x=-0.15),
        "right_arm": bb(x=-0.4 + shake, z=0.22),
        "left_arm": bb(x=-0.4 - shake, z=-0.22),
    }


def pet(t):
    """Invented: she turns in and inclines her head.

    Nothing in vanilla to borrow, because nothing in vanilla is ever pleased.
    One slow bow with a settle at the bottom.
    """
    bow = (1.0 - math.cos(t * 2.0 * math.pi)) * 0.5      # 0 -> 1 -> 0
    return {
        "head": bb(x=0.45 * bow, y=0.20 * bow),
        "body": bb(x=0.10 * bow),
        "right_arm": bb(x=-0.25 * bow, z=0.30 * bow),
        "left_arm": bb(x=-0.25 * bow, z=-0.30 * bow),
    }


#: name -> (function, length in ticks, whether it loops)
CLIPS = {
    "idle": (idle, 70, True),
    "walk": (walk, 20, True),
    "carry": (carry, 60, True),
    "angry": (angry, 20, True),
    "pet": (pet, 40, False),
}


def animation(name, fn, ticks, loops):
    """One Blockbench animation, keyed at KEYS evenly spaced points."""
    animators = {}
    for step in range(KEYS):
        t = step / KEYS
        at = round(t * ticks / 20.0, 4)                  # Blockbench works in seconds
        for bone, value in fn(t).items():
            slot = animators.setdefault(bone, {"name": bone, "type": "bone",
                                               "keyframes": []})
            slot["keyframes"].append({
                "channel": "rotation",
                "data_points": [{"x": value[0], "y": value[1], "z": value[2]}],
                "uuid": f"{name}-{bone}-{step}",
                "time": at,
                "color": -1,
                "interpolation": "catmullrom",
            })
    return {
        "uuid": f"shade-clip-{name}",
        "name": name,
        "loop": "loop" if loops else "once",
        "override": False,
        "length": round(ticks / 20.0, 4),
        "snapping": 20,
        "selected": False,
        "anim_time_update": "",
        "blend_weight": "",
        "start_delay": "",
        "loop_delay": "",
        "animators": animators,
    }


def main(path):
    with open(path) as f:
        model = json.load(f)

    named = {g["name"] for g in model.get("groups", []) if "name" in g}
    missing = [b for b in BONES if b not in named]
    if missing:
        sys.exit(f"ERROR: the rig has no bone named {missing}; "
                 "animations key off bone names and would land nowhere")

    model["animations"] = [animation(name, *spec) for name, spec in CLIPS.items()]
    with open(path, "w") as f:
        json.dump(model, f, indent=2)

    print(f"seeded {len(CLIPS)} animations into {os.path.relpath(path, HERE)}:")
    for name, (_, ticks, loops) in CLIPS.items():
        print(f"  {name:6s} {ticks:3d} ticks  {'loop' if loops else 'once'}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else RIG)
