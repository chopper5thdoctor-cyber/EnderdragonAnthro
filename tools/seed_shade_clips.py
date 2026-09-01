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


def up(units):
    """A vertical offset, in the units and the direction the animator sees.

    Blockbench counts y upward and the converter flips it, so this is written
    the way it looks in the editor: positive is up.
    """
    return ["0", f"{units:.4f}", "0"]


# --------------------------------------------------------------------- clips

def idle(t):
    """Standing: breathing, and shifting her weight.

    Deliberately plain -- this is the neutral one. It is what a shade nobody
    has animated yet stands in, and what the next one starts from.

    ## Why it is not vanilla's any more

    It used to be HumanoidModel's ambient sway and nothing else: two arms, a
    z-swing of three degrees, no body and no breath. On a vanilla enderman that
    is enough, because an enderman is a stick figure that mostly stands in the
    dark. On a four-block figure standing next to you in daylight it reads as a
    statue with a twitch, which is what got reported.

    ## What is in it

    A breath on the loop's own period, lifting rather than centring -- she
    never sinks below her standing height, so the feet stay planted and the
    stance height is still the one the foot-plane check pins. Head and arms
    take the same lift, because in this rig they hang off the root beside the
    body rather than under it; bobbing the body alone pulls her apart at the
    shoulders and the neck.

    A weight shift a quarter-cycle out from the breath, so the two do not stack
    into one pulse and the figure never quite repeats.

    The arms keep swinging on their own clock, twice per loop, because an idle
    whose every part shares one period reads as a machine.

    Nothing touches the head's ROTATION, and that is load-bearing: play()
    overwrites what it keys, so a head keyed here would throw away the vanilla
    head tracking underneath and she would stop looking at you. Nothing touches
    the legs either -- planted is the point.
    """
    turn = t * 2.0 * math.pi
    breath = math.sin(turn)
    shift = math.cos(turn)                      # a quarter-cycle behind
    sway = math.sin(turn * 2.0)                 # the arms, on their own
    lift = 0.5 + 0.5 * breath                   # 0..1 unit, never below rest
    return {
        "body": {"rotation": bb(x=-0.020 * breath, z=0.015 * shift),
                 "position": up(lift)},
        "head": {"position": up(lift)},
        "right_arm": {"rotation": bb(x=0.030 * sway, z=0.060 + 0.020 * breath),
                      "position": up(lift)},
        "left_arm": {"rotation": bb(x=-0.030 * sway, z=-0.060 - 0.020 * breath),
                     "position": up(lift)},
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
        "right_arm": {"rotation": bb(x=clamp(math.cos(p + math.pi) * 2.0 * 0.5 * 0.5))},
        "left_arm": {"rotation": bb(x=clamp(math.cos(p) * 2.0 * 0.5 * 0.5))},
        "right_leg": {"rotation": bb(x=math.cos(p) * 1.4 * 0.5)},
        "left_leg": {"rotation": bb(x=math.cos(p + math.pi) * 1.4 * 0.5)},
    }


def carry(t):
    """Arms up round a block, breathing.

    EndermanModel's carry is arm.xRot*0.5 - pi/10 on both sides. Static in
    vanilla; given a slow rise and fall here so a shade standing holding
    something is not a statue.
    """
    breathe = math.sin(t * 2.0 * math.pi) * 0.04
    return {
        "right_arm": {"rotation": bb(x=-math.pi / 10.0 + breathe, z=0.12)},
        "left_arm": {"rotation": bb(x=-math.pi / 10.0 + breathe, z=-0.12)},
    }


def angry(t):
    """The stance, trembling.

    Vanilla shakes the arms by a fixed 0.4 while creepy. The tremble is on a
    short period on purpose -- it should read as held rage rather than as a
    wave.
    """
    shake = math.sin(t * 2.0 * math.pi * 3.0) * 0.06
    return {
        "head": {"rotation": bb(x=-0.15)},
        "right_arm": {"rotation": bb(x=-0.4 + shake, z=0.22)},
        "left_arm": {"rotation": bb(x=-0.4 - shake, z=-0.22)},
    }


def pet(t):
    """Invented: she turns in and inclines her head.

    Nothing in vanilla to borrow, because nothing in vanilla is ever pleased.
    One slow bow with a settle at the bottom.
    """
    bow = (1.0 - math.cos(t * 2.0 * math.pi)) * 0.5      # 0 -> 1 -> 0
    return {
        "head": {"rotation": bb(x=0.45 * bow, y=0.20 * bow)},
        "body": {"rotation": bb(x=0.10 * bow)},
        "right_arm": {"rotation": bb(x=-0.25 * bow, z=0.30 * bow)},
        "left_arm": {"rotation": bb(x=-0.25 * bow, z=-0.30 * bow)},
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
    """One Blockbench animation, keyed at KEYS evenly spaced points.

    A clip function answers {bone: {channel: [x, y, z]}}. Both channels are
    real: rotation and position bake into different rows of the same table and
    the model plays both.
    """
    animators = {}
    for step in range(KEYS):
        t = step / KEYS
        at = round(t * ticks / 20.0, 4)                  # Blockbench works in seconds
        for bone, channels in fn(t).items():
            slot = animators.setdefault(bone, {"name": bone, "type": "bone",
                                               "keyframes": []})
            for channel, value in channels.items():
                slot["keyframes"].append({
                    "channel": channel,
                    "data_points": [{"x": value[0], "y": value[1], "z": value[2]}],
                    "uuid": f"{name}-{bone}-{channel}-{step}",
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


def main(path, only=None):
    """Write the clips into the rig, replacing only the ones named.

    `only` is the safety catch and the reason this is still runnable. The rig is
    the source of truth now and holds hand-drawn work -- vaelle.idle and the
    rest of her set -- so a bare re-run that rewrote `animations` wholesale
    would delete it. Anything not named is copied through untouched, and an
    animation the rig has that this tool does not know about (every per-shade
    override) is never a candidate in the first place.
    """
    with open(path) as f:
        model = json.load(f)

    named = {g["name"] for g in model.get("groups", []) if "name" in g}
    missing = [b for b in BONES if b not in named]
    if missing:
        sys.exit(f"ERROR: the rig has no bone named {missing}; "
                 "animations key off bone names and would land nowhere")

    wanted = list(CLIPS) if only is None else list(only)
    unknown = [name for name in wanted if name not in CLIPS]
    if unknown:
        sys.exit(f"ERROR: no clip called {unknown}; this tool knows "
                 + ", ".join(CLIPS))

    existing = model.get("animations", [])
    kept = [a for a in existing if a.get("name") not in wanted]
    fresh = [animation(name, *CLIPS[name]) for name in wanted]
    # In the rig's order where they already existed, so opening it after a
    # re-seed does not shuffle the animation list under the artist.
    order = {a.get("name"): i for i, a in enumerate(existing)}
    model["animations"] = sorted(
        kept + fresh, key=lambda a: order.get(a["name"], len(existing)))
    with open(path, "w") as f:
        json.dump(model, f, indent=2)

    print(f"wrote {len(fresh)} animation(s) into {os.path.relpath(path, HERE)}:")
    for name in wanted:
        _, ticks, loops = CLIPS[name]
        print(f"  {name:6s} {ticks:3d} ticks  {'loop' if loops else 'once'}")
    if kept:
        print(f"left alone: {', '.join(a.get('name', '?') for a in kept)}")


if __name__ == "__main__":
    args = sys.argv[1:]
    picked = None
    if "--only" in args:
        at = args.index("--only")
        picked = args[at + 1].split(",")
        args = args[:at] + args[at + 2:]
    main(args[0] if args else RIG, picked)
