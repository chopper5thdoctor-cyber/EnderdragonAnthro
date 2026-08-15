#!/usr/bin/env python3
"""Prove the generated Java model reproduces the Blockbench model.

Usage:  python3 tools/verify_model.py [art/dragon_form.bbmodel]

Reconstructs every cube's world-space corners twice — once by walking the
generated DragonFormModel.java, once by walking the .bbmodel — and compares
them. Run it after every conversion; a silent mismatch here is a mangled
model in game.

It exists because two conversion bugs shipped without it: per-cube rotations
were dropped entirely (44 of 75 cubes flattened), and the group rotation
mapping had the wrong sign on Y and Z.
"""
import json
import math
import os
import re
import sys

import numpy as np

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(HERE, "src/main/java/com/enderdragonanthro/client/model/DragonFormModel.java")
GROUND = 96.0
TOLERANCE = 0.15


def rzyx(x, y, z):
    cx, sx, cy, sy, cz, sz = (math.cos(x), math.sin(x), math.cos(y),
                              math.sin(y), math.cos(z), math.sin(z))
    return (np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
            @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
            @ np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]))


def from_java():
    src = open(JAVA).read()
    seg = src.split("PartDefinition root = mesh.getRoot();", 1)[1] \
             .split("return LayerDefinition", 1)[0]
    stmts = re.findall(r'(?:PartDefinition\s+(\w+)\s*=\s*)?(\w+)\.addOrReplaceChild'
                       r'\("([^"]+)",(.*?)\n\s*(PartPose\.[^;]+)\);', seg, re.S)
    parts = []
    for own, parent, name, cubes, pose in stmts:
        v = [float(t) for t in re.findall(r"(-?[\d.]+)F", pose)]
        boxes = [tuple(float(t) for t in mm) for mm in re.findall(
            r"addBox\((-?[\d.]+)F,\s*(-?[\d.]+)F,\s*(-?[\d.]+)F,\s*"
            r"(-?[\d.]+)F,\s*(-?[\d.]+)F,\s*(-?[\d.]+)F\)", cubes)]
        parts.append(dict(var=own, parent=parent, name=name, off=v[:3],
                          rot=(v[3:6] if len(v) > 3 else [0.0, 0.0, 0.0]), boxes=boxes))
    byvar = {p["var"]: p for p in parts if p["var"]}

    def world(p):
        if p["parent"] == "root":
            rp, tp = np.eye(3), np.zeros(3)
        else:
            rp, tp = world(byvar[p["parent"]])
        return rp @ rzyx(*p["rot"]), rp @ np.array(p["off"]) + tp

    out = []
    for p in parts:
        r, t = world(p)
        for (x, y, z, w, h, d) in p["boxes"]:
            pts = [np.array([a, b, c]) for a in (x, x + w)
                   for b in (y, y + h) for c in (z, z + d)]
            wc = np.array([r @ q + t for q in pts])
            wc[:, 1] = GROUND - wc[:, 1]
            out.append((p["name"], np.sort(np.round(wc, 2), axis=0)))
    return out


def from_bbmodel(path):
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
                out.append((e["name"], np.sort(np.round(np.array(pts), 2), axis=0)))
            else:
                g = groups.get(n["uuid"], {})
                walk(n.get("children", []),
                     chain + [(np.array(g.get("origin", [0, 0, 0]), float),
                               rzyx(*[math.radians(v) for v in g.get("rotation", [0, 0, 0])]))])

    walk(bb["outliner"], [])
    return out


def main(path):
    java, blockbench = from_java(), from_bbmodel(path)
    print(f"cubes: java {len(java)}  bbmodel {len(blockbench)}")
    if len(java) != len(blockbench):
        sys.exit("FAIL: cube counts differ")

    used = [False] * len(blockbench)
    worst, bad = 0.0, []
    for name, jc in java:
        best, bi = 1e9, -1
        for i, (_, bc) in enumerate(blockbench):
            if used[i]:
                continue
            dist = float(np.abs(jc - bc).max())
            if dist < best:
                best, bi = dist, i
        if best < TOLERANCE:
            used[bi] = True
            worst = max(worst, best)
        else:
            bad.append((name, round(best, 2)))

    print(f"matched within {TOLERANCE}u: {len(java) - len(bad)}/{len(java)}")
    print(f"worst corner deviation: {worst:.3f} units")
    if bad:
        for name, dist in bad[:12]:
            print(f"  MISMATCH {name}: nearest cube off by {dist}u")
        sys.exit("FAIL: the generated model does not match the bbmodel")
    print("PASS: the generated model reproduces the bbmodel")
    check_symbols()


def check_symbols():
    """Every DragonFormModel.X the rest of the mod uses must still be declared.

    DragonFormModel.java is generated, so anything hand-added to it survives
    only until the next conversion. GROUND_OFFSET was added by hand once and
    vanished the next time the wings changed, taking the build with it. Adding
    a constant means adding it to the template in bbmodel_to_java.py.
    """
    generated = open(JAVA).read()
    declared = set(re.findall(r"(?:static\s+final\s+\w+|ModelPart|void|public)\s+(\w+)\s*[=({;]",
                              generated))
    declared |= set(re.findall(r"\b(\w+)\s*\(", generated))

    missing = {}
    for root, _, files in os.walk(os.path.join(HERE, "src/main/java")):
        for f in files:
            if not f.endswith(".java") or f == "DragonFormModel.java":
                continue
            path = os.path.join(root, f)
            for sym in set(re.findall(r"DragonFormModel\.(\w+)", open(path).read())):
                if sym not in declared:
                    missing.setdefault(sym, []).append(os.path.relpath(path, HERE))

    if missing:
        for sym, users in sorted(missing.items()):
            print(f"  MISSING DragonFormModel.{sym} - used by {', '.join(users)}")
        sys.exit("FAIL: the generated model is missing symbols the mod uses. "
                 "Add them to the template in tools/bbmodel_to_java.py, not to "
                 "the generated file.")
    print("PASS: every DragonFormModel symbol the mod references is declared")
    check_form_guards()


def check_form_guards():
    """Human form is meant to be vanilla, so every mixin has to ask first.

    A mixin on Player, LivingEntity, Entity or one of the renderers fires for
    everybody. Without a form check it would quietly change the game for an
    untransformed player, which is the one promise this mod makes about not
    touching anything. Guards count if they are in the mixin or one hop away in
    a mod class it imports — the first-person hand checks inside its renderer.
    """
    targets = ("Player.class", "LivingEntity.class", "Entity.class",
               "GameRenderer.class", "PlayerRenderer.class", "LivingEntityRenderer.class")
    guards = ("isDragon(", "isDragonForm(")
    mixins = os.path.join(HERE, "src/main/java/com/enderdragonanthro/mixin")
    unguarded = []
    for name in sorted(os.listdir(mixins)):
        if not name.endswith(".java"):
            continue
        body = open(os.path.join(mixins, name)).read()
        if not any(f"@Mixin({t})" in body for t in targets):
            continue
        reach = [body]
        for imported in re.findall(r"import (com\.enderdragonanthro\.[\w.]+);", body):
            path = os.path.join(HERE, "src/main/java",
                                imported.replace(".", os.sep) + ".java")
            if not os.path.exists(path):
                continue
            text = open(path).read()
            # Skip wherever the guard is DECLARED, or every mixin that merely
            # imports DragonFormManager would look guarded by its definition.
            if re.search(r"boolean is(Dragon|DragonForm)\s*\(", text):
                continue
            reach.append(text)
        if not any(g in text for text in reach for g in guards):
            unguarded.append(name)

    if unguarded:
        for name in unguarded:
            print(f"  UNGUARDED {name} injects into every player, transformed or not")
        sys.exit("FAIL: a mixin would change vanilla behaviour in human form")
    print("PASS: every player-facing mixin checks the form first")
    check_mirror_pairs()


def check_mirror_pairs():
    """A symmetric pair must mirror exactly once, not twice and not never.

    A cube's u runs from its box origin, so a left/right pair already runs its
    texture in opposite directions with no flag at all. Setting mirror_uv on one
    of them is therefore correct and setting it on both -- or neither -- is not.

    This has now shipped twice. The wings read "ng][wi" for two builds, and the
    left arm ran two of its four pieces backwards for longer than that, which is
    the sort of thing you only see in game and only if you look. Named pairs are
    cheap to check here.
    """
    import json
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from bbmodel_to_java import MIRROR_OVERRIDE

    bb = json.load(open(os.path.join(HERE, "art/dragon_form.bbmodel")))
    flags = {}
    for e in bb["elements"]:
        name = e.get("name", "")
        flags[name] = MIRROR_OVERRIDE.get(name, bool(e.get("mirror_uv")))

    bad = []
    for name, mirrored in sorted(flags.items()):
        if not name.endswith("_left"):
            continue
        twin = name[:-5] + "_right"
        if twin not in flags:
            continue
        if mirrored == flags[twin]:
            bad.append(f"  {name} and {twin} are both "
                       f"{'mirrored' if mirrored else 'unmirrored'} — "
                       f"one of the pair must be flipped, and only one")
    if bad:
        print("FAIL: mirrored pairs disagree")
        print("\n".join(bad))
        sys.exit(1)
    pairs = sum(1 for n in flags if n.endswith("_left") and n[:-5] + "_right" in flags)
    print(f"PASS: all {pairs} left/right cube pairs mirror exactly once")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "art/dragon_form.bbmodel"))
