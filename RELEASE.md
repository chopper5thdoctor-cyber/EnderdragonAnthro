# Release checklist

**Nothing here should be done yet.** This is the list of things that exist
because the mod is still being built, and that should come out when it stops
being built — kept as a list rather than as memory, because every one of them is
the sort of thing that ships by accident.

Work through it when the git is declared complete, not before.

---

## 1. Take the build stamp out of Dragonsight

`DragonSight.BUILD` is `"sight-14"`, and opening the sight says so:

```
The sight opens. [sight-14]
```

**Why it exists.** Three separate fixes were reported broken while the jar under
test predated them, and there was no way to tell that apart from a real bug from
either side of the conversation. One keypress now says which build is loaded.

**What to remove** — `src/main/java/com/enderdragonanthro/ability/DragonSight.java`:

- the `BUILD` constant and its comment
- the `" [" + BUILD + "]"` in the message, leaving `"The sight opens."`

Nothing else reads it.

## 2. Decide where the test code lives

Three harnesses were added late and two of them ship inside the jar:

| | where it is | ships? |
|---|---|---|
| `tools/audit_injections.py` | `tools/` | no |
| `runGametest` | `src/main/java/…/gametest/` | **yes** |
| `runSmoke` | `src/main/java/…/client/smoke/` | **yes** |

Neither runs unless asked — the gametest entrypoint is only loaded under
`-Dfabric-api.gametest`, and the smoke harness under
`-Denderdragonanthro.smoke=true` — so this is tidiness, not correctness. Moving
them to their own Gradle source set keeps them out of a release jar. If they
stay, that is a decision, not an oversight, and this line records which.

`ServerPlayerSpawnGraceAccessor` exists **only** for `FakeDragon`. If the
gametests move out, it moves with them.

## 3. Drop `wing_flap_original` from the rig

`art/dragon_form.bbmodel` carries three animations:

- `wing_flap` — the one that is baked and flying
- `tailwag in flight` — the one that is baked and wagging
- `wing_flap_original` — **kept as reference when the new beat replaced it**

Clips are matched by name and anything unmatched is ignored, so it costs
nothing but a few kilobytes and a puzzled question from the next person to open
the rig. Delete it, or rename it something that says what it is.

## 4. Documents that describe an older mod

`TESTING.md` and `DESIGN.md` both predate a long stretch of work and were last
touched well before the Intent dial, the animation pipeline, the three
harnesses, Dragonsight, the gates or the powerscale. The README is current;
those two are not. Either bring them up or fold them into the README and say so
in the git history rather than leaving them to rot quietly.

## 5. Things that are deliberate, and should NOT be removed

Listed so nobody tidies them away on a later pass:

- **`require = 0` on five injections.** They are optional on purpose; the audit
  is what watches them, and it names them.
- **The heartbeat in `SmokeTest`.** It only logs under the smoke flag, and it is
  there because a harness that is waiting and a harness that is wedged look
  identical from outside — telling those apart cost three eight-minute runs.
- **The `unknown` state in `audit_injections.py`.** A target nobody exercised
  is not a pass. Making it one would be the exact habit the audit exists to
  break.
- **Every comment explaining a bug that already shipped.** They are the reason
  those bugs have not shipped twice.

## 6. Not a release item: gating the form

Recorded here so it is not lost, and explicitly **not** built yet. The
intention, in the author's words:

> I will begin trying to limit access to the Enderdragon form but not right
> now, MUCH later.

Today the form is a command and a key away, which is right for a mod that is
still being built and wrong for one being played. Nothing in the current code
assumes it stays that way: `DragonFormManager.transform` is the single door
everything else goes through, so whatever the eventual cost is — an item, a
place, a fight — it attaches there rather than being threaded through the kit.

Worth knowing before that work starts: `theFormIsIdempotent` in the gametests
asserts that transforming twice is the same as transforming once, and it will
need a companion asserting that transforming *without paying* does nothing.

---

## Before tagging

```bash
./gradlew build                 # compiles
python3 tools/verify_model.py   # rig, sheets, mixin registration
./gradlew runGametest           # the world behaves
./gradlew runSmoke              # textures, layers, clips
# and once, on a machine that can load a world:
./gradlew runSmokeWorld         # the round trips too

# then, with a client run under -Dmixin.debug.export=true:
python3 tools/audit_injections.py --strict
```

`--strict` is the release setting: it turns "that target was never exercised"
from a note into a failure, which is only reasonable when the export came from a
run that actually played.
