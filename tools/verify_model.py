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
import glob
import hashlib
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


def from_java(java=None):
    src = open(java or JAVA).read()
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
    compare(from_java(), from_bbmodel(path))
    check_symbols()


def compare(java, blockbench):
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
    check_mixins_registered()
    check_form_guards()


def check_mixins_registered():
    """Every mixin on disk has to be in the config, or it silently does nothing.

    A mixin class that is not listed compiles, passes review, and never runs.
    Nothing fails; the feature is simply absent. LivingEntityColdImmunityMixin
    was written, reported as working and shipped in that state -- freezing was
    never actually turned off, and there was no signal of any kind, because
    from the compiler's point of view the file was fine.

    Nothing else catches this. The build does not know what a mixin config is,
    and a client that reaches the title screen with zero injection failures
    proves only that the mixins which ARE listed applied.
    """
    config = os.path.join(HERE, "src/main/resources/enderdragonanthro.mixins.json")
    listed = set()
    with open(config) as fh:
        data = json.load(fh)
    for key in ("mixins", "client", "server"):
        listed.update(data.get(key, []))

    folder = os.path.join(HERE, "src/main/java/com/enderdragonanthro/mixin")
    on_disk = {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(folder, "*.java"))}

    missing = sorted(on_disk - listed)
    phantom = sorted(listed - on_disk)
    if missing or phantom:
        print("FAIL: the mixin config and the mixin package disagree")
        for name in missing:
            print(f"  {name} is on disk but not listed — it will never load")
        for name in phantom:
            print(f"  {name} is listed but has no class — the game will not start")
        sys.exit(1)
    print(f"PASS: all {len(on_disk)} mixins are registered")


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
    # slotOf counts too: it answers "is this one of the court", which is the
    # same promise in the other direction -- a mixin holding it cannot touch
    # an entity the mod did not summon.
    guards = ("isDragon(", "isDragonForm(", "slotOf(")
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
    check_mirror_rotations(bb)


def check_mirror_rotations(bb):
    """A pair must be posed as mirror images, not just painted as them.

    Mirroring across x negates the y and z rotations and leaves x alone, so
    delt_right at (0, 0, 27.5) demands delt_left at (0, 0, -27.5). It had it.
    forearm_left carried (-20, 0, 0) against a twin with no rotation at all,
    which the converter faithfully turned into its own rotated bone -- so one
    forearm sat bent forward and the first-person hand, which clones that arm,
    was bent with it.

    Geometry the corner check cannot see: it compares the generated Java to the
    .bbmodel, and both agreed. They were only ever both wrong together.
    """
    byname = {}
    for e in bb["elements"]:
        byname.setdefault(e.get("name", ""), []).append(e.get("rotation") or [0, 0, 0])

    bad = []
    for name, rots in sorted(byname.items()):
        if not name.endswith("_left"):
            continue
        twin = name[:-5] + "_right"
        if twin not in byname or len(byname[twin]) != len(rots):
            continue
        want = sorted([[r[0], -r[1], -r[2]] for r in byname[twin]])
        if sorted(rots) != want:
            bad.append(f"  {name} is posed {sorted(rots)}, but {twin} at "
                       f"{sorted(byname[twin])} mirrors to {want}")
    if bad:
        print("FAIL: mirrored pairs are posed differently")
        print("\n".join(bad))
        sys.exit(1)
    print("PASS: every left/right pair is posed as its twin's mirror")
    check_sheets()
    check_shade()


def check_sheets():
    """A rig has to open painted, and a layered texture will not.

    Blockbench draws a texture from its layers when layers_enabled is set, and
    treats source as a flattened preview it is free to ignore. dragon_form's
    sheet carried that flag with a single layer holding no image data at all,
    so the file opened with the model bare -- for however long, since it
    predates every commit on this branch -- and re-embedding a repainted sheet
    into source did nothing to fix it, which is how it was finally noticed.

    Nothing here needs layers. If the flag is set, the fix is to clear it and
    let source be the sheet, which is what the artist's own exports do.
    """
    import glob
    bad = []
    for path in sorted(glob.glob(os.path.join(HERE, "art/*.bbmodel"))):
        for tex in json.load(open(path)).get("textures", []):
            name = f"{os.path.basename(path)}:{tex.get('name')}"
            if tex.get("layers_enabled") or tex.get("layers"):
                bad.append(f"  {name} is layered — Blockbench will draw the "
                           f"layers, not source")
            elif not (tex.get("source") or "").startswith("data:"):
                bad.append(f"  {name} has no embedded sheet, so it opens bare "
                           f"anywhere but the machine it was painted on")
    if bad:
        print("FAIL: a rig will not open painted")
        print("\n".join(bad))
        sys.exit(1)
    print("PASS: every rig carries its own sheet, unlayered")
    check_shipped_sheet()


def check_shipped_sheet():
    """What ships has to be what the faces are mapped to.

    A rig turned up carrying two sheets — one of them a stale copy still
    holding the blue block-out guides on the fist UV, the other the repainted
    one the faces actually referenced. The converter took textures[0] and
    shipped 235 pixels of pure blue onto the dragon's hands. Nothing caught it,
    because every geometric check passed: the geometry was right, only the
    paint was a different file.

    So this compares pixels rather than trusting the pipeline. A face's
    `texture` field is an index into the textures array, which makes "the sheet
    the faces are mapped to" the file's own answer to which one it means, and
    the shipped PNG has to equal it exactly.
    """
    import base64
    import collections
    import io
    from PIL import Image

    bb = json.load(open(os.path.join(HERE, "art/dragon_form.bbmodel")))
    votes = collections.Counter()
    for e in bb["elements"]:
        for f in (e.get("faces") or {}).values():
            if f.get("texture") is not None:
                votes[f["texture"]] += 1
    if len(votes) > 1:
        print(f"FAIL: faces are split across {len(votes)} textures {dict(votes)} — "
              f"which one is the model painted with?")
        sys.exit(1)
    want = Image.open(io.BytesIO(base64.b64decode(
        bb["textures"][votes.most_common(1)[0][0] if votes else 0]["source"]
        .split(",", 1)[1]))).convert("RGBA")
    shipped = Image.open(os.path.join(
        HERE, "src/main/resources/assets/enderdragonanthro/textures/entity/"
              "dragon_form.png")).convert("RGBA")

    if shipped.size != want.size:
        print(f"FAIL: shipped sheet is {shipped.size}, the rig's is {want.size}")
        sys.exit(1)
    # Compared band by band, NOT with ImageChops.difference(...).getbbox() on
    # RGBA: getbbox reads the alpha channel there, so two sheets whose alpha
    # agrees come back "identical" however far their colours differ. That is
    # precisely how the blue was cleared as a non-difference the first time.
    if shipped.tobytes() != want.tobytes():
        wp, sp = want.load(), shipped.load()
        off = [(x, y) for y in range(want.height) for x in range(want.width)
               if wp[x, y] != sp[x, y]]
        print(f"FAIL: the shipped sheet is not the one the rig is painted with "
              f"— {len(off)} pixels differ, first at {off[0]}")
        print("  re-run tools/bbmodel_to_java.py")
        sys.exit(1)
    print("PASS: the shipped sheet is pixel-for-pixel the one the faces are mapped to")


def check_shade():
    """The court's rig gets the same corner check the dragon's does.

    Same conversion code underneath, so the same two bugs are available: a
    dropped cube rotation would stand the arms straight down, and a sign error
    on the group rotation would splay them the wrong way. Neither is obvious in
    game, because a shade is black and four blocks away.
    """
    java = os.path.join(HERE, "src/main/java/com/enderdragonanthro/client/model/ShadeModel.java")
    rig = os.path.join(HERE, "art/shade_base.bbmodel")
    if not (os.path.exists(java) and os.path.exists(rig)):
        return
    print("\nshade rig:")
    compare(from_java(java), from_bbmodel(rig))
    check_shade_stance(rig)


def check_shade_stance(rig):
    """And that it stands on the floor rather than in it.

    ShadeLayer lifts the rig by groundOffset and then divides it by
    AUTHOR_SCALE. Model y 24 is the ground, so the feet have to land there --
    a foot plane read off the wrong end of the rig would bury it to the knees,
    which is exactly the kind of thing that only shows up once the game is up.
    """
    src = open(os.path.join(
        HERE, "src/main/java/com/enderdragonanthro/client/model/ShadeModel.java")).read()
    foot = float(re.search(r"FOOT_PLANE\s*=\s*([\d.]+)F", src).group(1))
    author = float(re.search(r"AUTHOR_SCALE\s*=\s*([\d.]+)F", src).group(1))
    scale = 1.0 / author

    bb = json.load(open(rig))
    # The skin's lowest point, not the clothing's -- the same rule the converter
    # applies, and it has to be the same rule or this check disagrees with the
    # thing it is checking. A covering is inflated half a unit past the cube it
    # covers, so counting them here would demand she stand on the hem of her
    # trousers with her feet in the air.
    skin = [e for e in bb["elements"] if not e["name"].endswith("Covering")]
    lowest = max(GROUND - e["from"][1] for e in skin)
    if abs(lowest - foot) > TOLERANCE:
        sys.exit(f"FAIL: FOOT_PLANE is {foot} but the rig's lowest cube is at {lowest:.2f}")

    feet = foot * scale + (24.0 - foot * scale)
    head = min(GROUND - e["to"][1] for e in skin) * scale + (24.0 - foot * scale)
    print(f"PASS: feet land at model y {feet:.2f} (ground is 24), "
          f"standing {(feet - head) / 16.0:.2f} blocks before the entity's own scale")
    check_hearts()
    check_shade_colours()


def check_shade_colours():
    """A shade's name is written in the colour she is painted in.

    Two files, two languages, four numbers: make_shade_texture.py puts an accent
    on each shade's cloth and ShadeIdentity writes her name in a colour. They
    are the same four colours and nothing made them stay that way -- the chat
    tints used to be RED/BLUE/GREEN/GOLD, which are near the accents and not
    them, and near is exactly the kind of wrong nobody reports.

    Matched by ORDER rather than by name, deliberately: the tool still calls
    slot 1 "keshaire" and the mod calls her "Keshanne". That rename is real and
    is flagged below rather than worked around silently.
    """
    import re
    tool = open(os.path.join(HERE, "tools/make_shade_texture.py")).read()
    block = re.search(r"COURT\s*=\s*\{(.*?)\}", tool, re.S).group(1)
    paint = [(m.group(1), (int(m.group(2), 16) << 16) | (int(m.group(3), 16) << 8)
              | int(m.group(4), 16))
             for m in re.finditer(r'"(\w+)":\s*\(0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),'
                                  r'\s*0x([0-9A-Fa-f]{2})\)', block)]

    java = open(os.path.join(
        HERE, "src/main/java/com/enderdragonanthro/ability/ShadeIdentity.java")).read()
    tints = [int(v, 16) for v in re.search(
        r"TINT_RGB\s*=\s*\{([^}]*)\}", java).group(1).replace("0x", " ").split(",")]
    names = re.findall(r'"([^"]+)"', re.search(r"NAMES\s*=\s*\{([^}]*)\}", java).group(1))

    if len(paint) != len(tints):
        sys.exit(f"FAIL: {len(paint)} painted accents but {len(tints)} chat tints")
    bad = [f"  {names[i]} speaks in #{tints[i]:06X} and is painted #{paint[i][1]:06X}"
           for i in range(len(tints)) if tints[i] != paint[i][1]]
    if bad:
        print("FAIL: a shade's colour on screen is not the colour she is wearing")
        print("\n".join(bad))
        sys.exit(1)
    print(f"PASS: all {len(tints)} shades speak in the colour they are painted")
    drifted = [f"{paint[i][0]} / {names[i]}" for i in range(len(names))
               if paint[i][0] != names[i].lower().replace("\u00eb", "e")]
    if drifted:
        print(f"  note: the tool and the mod disagree on {len(drifted)} name(s): "
              + ", ".join(drifted))


def check_hearts():
    """The derived heart sprites still match the ones that were drawn.

    Seven of the ten hearts are computed from three drawings by two rules that
    make_hearts.py holds. Committing the output means the rules and the files
    can drift -- somebody repaints a heart and the blinking one stays as it was,
    which shows for two ticks at a time while you are being killed and is
    therefore never seen. This re-derives and compares.
    """
    import subprocess
    out = subprocess.run([sys.executable, os.path.join(HERE, "tools/make_hearts.py"),
                          "--check"], capture_output=True, text=True)
    if out.returncode != 0:
        print(out.stdout + out.stderr)
        sys.exit("FAIL: the heart sprites do not match their derivation rules")
    print("PASS: every derived heart matches the sprite it comes from")
    check_clip_channels()
    check_world_state()
    check_dev_skins()


#: The default skin table, 18 long: nine SLIM then the same nine WIDE.
_SKIN_NAMES = ("alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri")


def _skin_model(name):
    """What model an offline player of this name gets, and which skin.

    Three steps, each read out of the 1.21.1 jar rather than remembered:

        UUIDUtil.createOfflinePlayerUUID  nameUUIDFromBytes("OfflinePlayer:"+n)
        java.util.UUID.hashCode           ((int)(hilo>>32)) ^ (int)hilo
        DefaultPlayerSkin.getSkinIndex    Math.floorMod(hashCode, 18)
    """
    digest = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30          # version 3
    digest[8] = (digest[8] & 0x3F) | 0x80          # IETF variant
    msb = int.from_bytes(digest[:8], "big")
    lsb = int.from_bytes(digest[8:], "big")

    def signed(v):
        v &= 0xFFFFFFFF
        return v - (1 << 32) if v & 0x80000000 else v

    hilo = msb ^ lsb
    index = signed(signed(hilo >> 32) ^ signed(hilo)) % 18
    return ("SLIM" if index < 9 else "WIDE"), _SKIN_NAMES[index % 9], index


def check_dev_skins():
    """The two dev players really are one wide model and one slim one.

    run.bat exists to put a second pair of eyes on things only somebody else
    can see, and half of why it names its players Jean_Wide and Jean_Slim is to
    cover the three-pixel arm -- every layer this mod hangs off an arm has only
    ever been looked at on a four-pixel one.

    But the model is not something the run config sets. It falls out of the
    NAME, through the offline UUID, through a hash. Rename a player to
    something tidier and both of them can quietly land on the same model, and
    the slim arm stops being tested with nothing to say so -- the windows still
    open, both players still render, and the coverage is simply gone.

    So the names are read back out of build.gradle and the arithmetic redone.
    """
    with open(os.path.join(HERE, "build.gradle")) as fh:
        gradle = fh.read()
    # One list, read where it is declared. The run configs and ops.json both
    # index into it, so reading it here is reading what both of them use.
    listed = re.search(r"project\.ext\.devPlayers\s*=\s*\[([^\]]*)\]", gradle)
    names = re.findall(r"'([^']+)'", listed.group(1)) if listed else []
    if len(names) < 2:
        print("FAIL: build.gradle no longer declares two names in project.ext.devPlayers")
        sys.exit(1)

    seen = {}
    for name in names:
        model, skin, index = _skin_model(name)
        seen.setdefault(model, []).append(name)
        print(f"  {name} -> index {index}, {model} ({skin})")
    if len(seen) < 2:
        only = next(iter(seen))
        print(f"FAIL: both dev players are {only}, so the other arm width is "
              "never on screen")
        print("  the model comes from the name, not the run config -- pick "
              "names whose hash lands on both halves of the table")
        sys.exit(1)
    print(f"PASS: the dev players cover both arm widths ({len(names)} named)")


#: Every generated model, and what plays its clips.
_CLIP_PLAYERS = ("src/main/java/com/enderdragonanthro/client/model/ShadeModel.java",
                 "src/main/java/com/enderdragonanthro/client/model/DragonFormModel.java")


def check_clip_channels():
    """Everything the bake writes has to be something the model reads.

    A clip bakes six rows per bone -- rotation x/y/z then position x/y/z -- and
    ShadeModel.play read the first three and stopped. Position keyframes were
    converted, written into the file, shipped, and dropped on the floor at draw
    time. Every position key ever drawn on a shade did nothing: the whole of
    the breath in vaelle.idle, silently, for as long as the class existed.

    Nothing could have caught that downstream. The tables were right, the
    converter was right, verify_model compared the geometry and agreed, and the
    only symptom was an animation that looked like it had not been saved.

    So: a file that plays clips has to touch all six offsets, either by naming
    them or by looping over them.
    """
    for rel in _CLIP_PLAYERS:
        path = os.path.join(HERE, rel)
        with open(path) as fh:
            text = fh.read()
        if re.search(r"for \(int k = 0; k < 6; k\+\+\)", text):
            continue                     # loops the lot; nothing to miss
        seen = {0} if re.search(r"\[b \* 6\]", text) else set()
        seen.update(int(m) for m in re.findall(r"\[b \* 6 \+ (\d)\]", text))
        missing = sorted({0, 1, 2, 3, 4, 5} - seen)
        if missing:
            rows = ["rotation.x", "rotation.y", "rotation.z",
                    "position.x", "position.y", "position.z"]
            print(f"FAIL: {rel} bakes six rows per bone and plays "
                  f"{len(seen)} of them")
            for row in missing:
                print(f"  row {row} ({rows[row]}) is written by the converter "
                      "and never read")
            sys.exit(1)
    print(f"PASS: all {len(_CLIP_PLAYERS)} clip players read every row they are given")


#: Live per-player state, in the shape it is always written in.
_STATIC_STATE = re.compile(
    r"private static final (?:Map|Set|List)<[^;=]*>\s+([A-Z_0-9]+)\s*=\s*new ")

#: Where the mod keeps that state. Anything under here is server-side and dies
#: with the world; the client packages are per-connection and reset themselves.
_STATE_ROOTS = ("ability", "transform", "boss", "block", "item", "command")


def check_world_state():
    """A static map is per process, not per world, and nothing else says so.

    Singleplayer runs the server inside the client, so a static map filled while
    you played one save is still full when you open the next one. That is how
    Vaelle came to hold slot 0 in a world she had never been in: the court was a
    static map, the second save asked for a free slot, and it was told there was
    only one left.

    The class of bug is invisible from the compiler, invisible in review, and
    invisible in play unless somebody opens two saves in one sitting. So the
    shape is checked instead: any class holding live per-player state has to
    have a forgetWorld(), and ServerMemory has to call it. Adding a map without
    a way to drop it fails here rather than in a bug report.

    Excluded on purpose: constants (Map.of and friends do not match, since they
    are not `new`), and anything under client/, which is torn down with the
    connection.
    """
    memory = os.path.join(HERE, "src/main/java/com/enderdragonanthro/ServerMemory.java")
    with open(memory) as fh:
        coordinator = fh.read()

    unswept, uncalled = [], []
    for root in _STATE_ROOTS:
        folder = os.path.join(HERE, "src/main/java/com/enderdragonanthro", root)
        for path in sorted(glob.glob(os.path.join(folder, "**/*.java"), recursive=True)):
            with open(path) as fh:
                text = fh.read()
            held = _STATIC_STATE.findall(text)
            if not held:
                continue
            name = os.path.basename(path)[:-5]
            rel = os.path.relpath(path, HERE)
            if "static void forgetWorld()" not in text:
                # DragonMinions keeps its own pair, because its state is the one
                # piece that is written down rather than dropped.
                if not ("static void forget()" in text and "static void load(" in text):
                    unswept.append(f"{rel} holds {', '.join(held)} and has no forgetWorld()")
                    continue
            if name + "." not in coordinator:
                uncalled.append(f"{rel} has a forgetWorld() that ServerMemory never calls")

    if unswept or uncalled:
        print("FAIL: state that outlives the world it belongs to")
        for line in unswept + uncalled:
            print("  " + line)
        sys.exit(1)
    print("PASS: every holder of live state is dropped when the world closes")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "art/dragon_form.bbmodel"))
