# Working on this repo

Conventions that are not obvious from the code, and that cost something to
rediscover. Short on purpose; the long explanations live next to the code they
explain.

## Images the author attaches are real files, and they are reachable

Textures, icons and screenshots pasted into a session are **not** written to
the container's filesystem, so `find` turns up nothing and it is easy to
conclude they cannot be used and ask for them again. They can. They are stored
base64-encoded in the session transcript:

```
/root/.claude/projects/-home-user-EnderdragonAnthro/<session-id>.jsonl
```

Walk the JSON on each line for `{"type": "image", "source": {"type": "base64",
"data": ...}}` and write `base64.b64decode(data)` to a file. That recovers the
original bytes exactly — a 16x16 PNG comes back as a 16x16 PNG, alpha intact —
so a replacement texture can be committed as sent rather than eyeballed from a
rendering and redrawn. Do this before asking for a re-send.

Extract to the scratchpad, then copy into `assets/` only the ones that are
actually replacements.

## No Mojang assets, ever

Nothing vanilla gets committed or shipped: no textures, models, particles or
sounds. Vanilla art may be **referenced by resource location** at runtime, and
may be **extracted for measurement** — comparing a sheet's UV islands against
`end_crystal.png`, say — but only into the scratchpad, never into the repo.

## Generated files

`DragonFormModel.java`, `ShadeModel.java` and `DragonRig.java` are generated.
Never hand-edit them: the next conversion silently discards the change. A
constant that belongs to the model goes in `tools/bbmodel_to_java.py` or
`tools/shade_to_java.py`, in the template.

Re-generate and then run `python3 tools/verify_model.py`.

## The failure this project keeps having

Code that exists and does not run, announcing nothing. A mixin missing from
`mixins.json`. A bone the converter did not know about. A clip channel baked
and never read. A map that outlives the world it belongs to. None of them fail
a build, and none of them log anything.

So the habit is: when a fix goes in, add the check that would have caught it,
and **prove the check fails against the old code** before believing it. A check
that passes against the bug it was written for is worse than no check — see
`aGateIsBuiltFromSomethingToStandOn`, which was written wrong the first time
and passed happily against the broken build order.

`tools/verify_model.py` is the cheap end of this and runs headless.
`./gradlew runGametest` is the world. `./gradlew runSmoke` is the client.

## Running the client harness without a display

There is no GPU here and none is needed — only a window and a GL context:

```bash
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe \
  xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runSmokeWorld
```

Without it GLFW dies at init with "Failed to detect any supported platform",
which reads as a mod crash and is not one.

## Git

Develop, commit and push only to `claude/enderdragon-anthropomorphic-mod-s3ytnc`.
Do not open a pull request unless asked.
