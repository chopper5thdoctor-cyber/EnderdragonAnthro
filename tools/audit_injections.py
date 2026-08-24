#!/usr/bin/env python3
"""Prove every mixin injection actually landed in the class it targets.

Usage:
    ./gradlew runClient          # with -Dmixin.debug.export=true, see below
    python3 tools/audit_injections.py [--strict]

A mixin that does not apply is the quietest failure this project has. The class
compiles. The config is valid. The game starts. Nothing anywhere says the words
"did not apply" -- the feature is simply absent, and the only way anyone finds
out is by playing and noticing that powder snow still freezes.

That is not hypothetical. LivingEntityColdImmunityMixin was written, reported as
working and shipped in that state, and cold immunity never once ran.
verify_model.py now catches the specific case of a mixin missing from the
config; this catches the general one, which is an injection that is listed,
loaded, and still did not attach -- a target method renamed between versions, a
descriptor that no longer matches, an @At that finds nothing.

## How

Mixin can be asked to write out every class it transforms:

    JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dmixin.debug.export=true" \\
        xvfb-run -a ./gradlew runClient

It writes them to run/.mixin.out/class/. A handler that attached appears in the
transformed class under a mangled name that still ends in the method's own name,
so the check is: for every injection in our sources, is there a method in the
transformed target carrying that name?

## The third answer

Not every target is loaded by a client that only reaches the title screen --
InventoryScreen is not, until somebody opens an inventory. Those come back
UNKNOWN rather than PASS, which is the honest answer and the whole point: the
old habit was to read "the client started cleanly" as "everything works", and
what it actually proves is that the mixins which were *exercised* applied.

UNKNOWN is not a failure by default, because making it one would mean the audit
could only ever run against a full playthrough. --strict makes it one, for when
the export came from a run that did exercise everything.
"""
import glob
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIXIN_DIR = os.path.join(HERE, "src/main/java/com/enderdragonanthro/mixin")
CONFIG = os.path.join(HERE, "src/main/resources/enderdragonanthro.mixins.json")
EXPORT = os.path.join(HERE, "run/.mixin.out/class")

# Everything that weaves a method into the target class. @Accessor and @Invoker
# are included because a missing accessor is the same silent nothing.
INJECTORS = ("Inject", "Redirect", "ModifyVariable", "ModifyArg", "ModifyArgs",
             "ModifyConstant", "ModifyReturnValue", "WrapOperation", "WrapWithCondition",
             "Accessor", "Invoker")
ANNOTATION = re.compile(r"@(" + "|".join(INJECTORS) + r")\b")
TARGET = re.compile(r"@Mixin\(\s*(?:value\s*=\s*)?\{?\s*([\w.]+)\.class")
# The method name is the first `identifier(` after the annotation ends. Note
# the $: every handler here is named enderdragonanthro$something, and \w does
# not include $ -- which the first version of this got wrong, matched nothing,
# and backtracked into reporting `if` as the name of seventeen methods.
DECLARED = re.compile(r"([A-Za-z_$][\w$]*)\s*\(")


def annotation_end(text, at):
    """Index just past an annotation, balancing its argument parentheses."""
    i = at
    while i < len(text) and text[i] not in "(\n":
        i += 1
    if i >= len(text) or text[i] != "(":
        return i                            # a bare @Accessor, no arguments
    depth, quote = 0, None
    while i < len(text):
        c = text[i]
        if quote:
            if c == "\\":
                i += 1
            elif c == quote:
                quote = None
        elif c in "\"'":
            quote = c
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return len(text)


def sources():
    """Every injection we have written, as (mixin, target, kind, method)."""
    found = []
    for path in sorted(glob.glob(os.path.join(MIXIN_DIR, "*.java"))):
        text = open(path).read()
        mixin = os.path.basename(path)[:-5]
        hit = TARGET.search(text)
        if not hit:
            print(f"  ?? {mixin}: no @Mixin(X.class) found")
            continue
        target = hit.group(1).split(".")[-1]

        for annotated in ANNOTATION.finditer(text):
            after = annotation_end(text, annotated.start())
            name = DECLARED.search(text, after)
            if name:
                found.append((mixin, target, annotated.group(1), name.group(1)))
    return found


def transformed():
    """The methods Mixin actually wrote, keyed by target class simple name."""
    if not os.path.isdir(EXPORT):
        sys.exit(f"ERROR: no export at {os.path.relpath(EXPORT, HERE)}\n"
                 f"  Run the client once with -Dmixin.debug.export=true first:\n"
                 f'    JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dmixin.debug.export=true" \\\n'
                 f"        xvfb-run -a ./gradlew runClient")
    classes = {}
    for path in glob.glob(os.path.join(EXPORT, "**", "*.class"), recursive=True):
        name = os.path.basename(path)[:-6]
        if "$" in name:
            continue                       # inner classes are not mixin targets
        out = subprocess.run(["javap", "-p", path], capture_output=True, text=True)
        classes[name] = out.stdout
    return classes


def main(strict=False):
    listed = set()
    data = json.load(open(CONFIG))
    for key in ("mixins", "client", "server"):
        listed.update(data.get(key, []))

    weave = transformed()
    rows = sources()
    good, missing, unknown, unlisted = [], [], [], []

    for mixin, target, kind, method in rows:
        if mixin not in listed:
            unlisted.append((mixin, target, kind, method))
            continue
        body = weave.get(target)
        if body is None:
            unknown.append((mixin, target, kind, method))
        elif method in body:
            good.append((mixin, target, kind, method))
        else:
            missing.append((mixin, target, kind, method))

    print(f"{len(rows)} injections across {len(set(r[0] for r in rows))} mixins, "
          f"{len(weave)} classes transformed in this run\n")

    for label, group in (("NOT REGISTERED", unlisted), ("DID NOT APPLY", missing)):
        for mixin, target, kind, method in group:
            print(f"  {label}: {mixin} @{kind} {method} -> {target}")

    if unknown:
        seen = sorted({(m, t) for m, t, _, _ in unknown})
        print(f"  UNKNOWN: {len(unknown)} injection(s) whose target was never "
              f"class-loaded in this run, so nothing is proven either way:")
        for mixin, target in seen:
            print(f"      {mixin} -> {target}")

    print(f"\n  applied: {len(good)}   unknown: {len(unknown)}   "
          f"did not apply: {len(missing)}   unregistered: {len(unlisted)}")

    if missing or unlisted:
        sys.exit("FAIL: an injection that was supposed to attach did not")
    if unknown and strict:
        sys.exit("FAIL: --strict, and some targets were never exercised")
    print("PASS: every injection whose target was loaded is attached")


if __name__ == "__main__":
    main(strict="--strict" in sys.argv)
