# Enderdragon Anthro

A Fabric mod for Minecraft **1.21.1** that lets you become an **anthropomorphic Ender
Dragon** — canon 8-block height, canon 200 HP, canon palette, the dragon's abilities on
keybinds, a court of named endermen, and a pink boss bar other players see — while
keeping every normal player mechanic: inventory, tools, hunger, crafting, all of it.

| | |
|---|---|
| **Play-test guide** | **[TESTING.md](TESTING.md)** — keys, an eight-part test plan, and what is most likely to break |
| **Design rationale** | **[DESIGN.md](DESIGN.md)** — canon reference data, the anthro translation, and the honest list of complications |
| **Making the model** | **[MODELING_GUIDE.md](MODELING_GUIDE.md)** — the Blockbench rig contract |
| **Build tooling** | **[tools/README.md](tools/README.md)** — the bbmodel → Java converter and its verifier |

---

## What works

**The form.** `Y` transforms. It survives relogging and death. You become 8 blocks tall
with 200 HP, a 3× stride, 2.5-block step height, scaled reach, and dragon-grade melee.

**The body.** A 75-cube anthro dragon built in Blockbench and compiled into the mod by
`tools/bbmodel_to_java.py`. The vanilla player body is hidden and this replaces it,
riding the player skeleton so every vanilla animation drives it for free. Eyes and mouth
render on a fullbright pass, so they glow in the dark.

**Canon toughness.** Everything you take is `damage/4 + 1`. Immune to fire, lava, every
status effect, and — for fairness, since an anthro walks among its own crystals rather
than circling them — End Crystal blasts. No mob will target you at all; nothing in
vanilla is hostile to the Ender Dragon.

**Crystal Link.** Stand within 32 blocks of an End Crystal and it beams at you, healing
2 HP/s and refilling hunger. Pop a crystal that is healing you and it costs 10 HP, canon.

**Flight is literally elytra.** Rather than imitating the physics, a mixin keeps the
fall-flying flag set so the untouched vanilla elytra code path runs. Double-tap `Space`
mid-air to start; `H` beats the wings — twice a firework rocket's push toward a 2.6
target speed, because these are wings and a rocket is a firework.

**The court.** Four named elite endermen — **Vael, Kesh, Nyra, Orrin** — scaled to 4
blocks, each addressed and ordered separately. They never turn on you.

---

## Keys

| Key | Ability | Cooldown |
|---|---|---|
| `Y` | Transform (dragon ⇄ human) | — |
| `R` | Dragon's Breath — raytraced so the pool always lands on ground | 4 s |
| `G` | Dragon Fireball — canon lingering cloud **plus** an entity-only blast | 3 s |
| `V` | Wing Buffet — canon 3/5/7 damage, huge knockback | 5 s |
| `C` | Charge dash — canon 6/10/15. Sprinting alone also deals charge damage | 4 s |
| `B` | Crater punch **toggle** — 5×5×5, silk-touch | — |
| `X` | Evasive jump — somewhere random 1000+ blocks out, under open sky | 60 s |
| `K` | Warp to a random player beyond 100 blocks | 10 s |
| `J` | Return to your anchor | 5 s |
| `H` | Wing boost | 1 s |
| `Z` | Summon a shade (four maximum) | 10 s |
| `N` | Address a shade — the one you look at, else the next | — |
| `M` | Open **the court** — the orders screen | — |
| double-`Space` | Start gliding (mid-air) | — |

All rebindable. The HUD is a column down the left edge, each key chip with its ability
name beside it; chips flash on press, drain while recharging, and stay lit while a toggle
is armed.

`/shade collect <block>` names a quarry by typing rather than by looking at one.

---

## The court's orders

`M` opens a screen listing every shade you have raised, with all four orders one click
each and a text field for the quarry. Orders used to cycle on a keypress, which meant
reaching Crystal cost you whatever the shade was carrying and re-read whatever block you
happened to be looking at on the way through; nothing is passed through now.

| Order | Behaviour |
|---|---|
| **Defend** | Stays close, attacks anything that targets you |
| **Attack** | Attacks whatever you look at, else the nearest hostile |
| **Collect** | Forages up to 1000 blocks, brings back exactly one stack, sets it down |
| **Crystal** | Finds bedrock and **forges** an End Crystal on it every 20 s |

Shades make crystals rather than spending yours — a dragon has no hands for a workbench,
so stocking a domain is the court's trade.

**Collect** takes a named quarry: type a block id into the row's field, or leave it blank
and the shade takes whatever you are looking at. With one named the shade digs for it,
teleporting into that ore's **real generation band** — diamond near Y −59, iron near 16,
copper near 48 — and it remembers the depth it last struck the seam at, so it sharpens the
longer it works. With no quarry named it only strips surface blocks, so it will not
swiss-cheese the landscape by accident.

**Nothing you interrupt is lost.** Change a shade's order mid-errand and it comes back,
sets down what it dug up, and only then takes the new one. A finished Collect hands the
stack over and drops back to **Defend**, as does an Attack order with nothing left to
hunt — standing with you is the default duty, not a fifth order you have to pick.

**They answer for you.** Take two hits from the same attacker inside five seconds and
every shade within 48 blocks breaks off and goes for it, for fifteen seconds. Ones away
on business keep their cargo and their errand.

## Crater punch

Toggle with `B`, then left-click. A 5×5×5 volume breaks and drops **silk-touch**.

- **Obsidian and end stone hold** — the canon dragon's own limits, and what keeps the End
  fight standing.
- **Bedrock does not.** Breaking the floor out from under yourself is a choice you are
  allowed to make.

## The homing crystal

An End Crystal underneath, rendered orange, that needs no bedrock and never explodes.
While transformed one simply appears in your keeping — you do not craft it. Unstackable,
one at a time. Place it anywhere; `J` returns you to it from any distance.

Anyone can break it. When they do it shatters rather than detonating, and you go five
minutes without an anchor before a fresh one forms. It is deliberately kept off Crystal
Link — an anchor is a waypoint, not a battery.

---

## Building

You need **Java 21** ([Adoptium](https://adoptium.net), pick Temurin 21). The Gradle
wrapper fetches everything else — Gradle, the Fabric toolchain, Minecraft, mappings — on
first run.

```bash
git clone https://github.com/chopper5thdoctor-cyber/EnderdragonAnthro.git
cd EnderdragonAnthro

./gradlew build        # Windows: gradlew.bat build   → jar in build/libs/
./gradlew runClient    # dev client with the mod loaded
```

The first build pulls roughly 1–2 GB and takes 5–15 minutes; later builds are quick.
IntelliJ IDEA Community will import the Gradle project and give you run buttons for the
same tasks.

**Updating:** `git pull` then `run.bat`. Close Minecraft first — the build locks files.

**On Windows**, `run.bat` runs the dev client and cleans up its own JVMs afterwards, and
`unlock.bat` clears anything still holding the folder (it only stops Java processes whose
command line points at this folder, so other Java apps are untouched). The Gradle daemon
and file-system watching are disabled in `gradle.properties` — between them they are why
a JVM used to outlive the console and keep the directory locked.

## Working on the model

Edit `art/dragon_form.bbmodel` in [Blockbench](https://blockbench.net), then:

```bash
python3 tools/bbmodel_to_java.py art/dragon_form.bbmodel
python3 tools/verify_model.py          # always run this
```

`DragonFormModel.java` is **generated** — never hand-edit it. The verifier rebuilds every
cube's world-space corners from both the generated Java and the `.bbmodel` and compares
them; it exists because three conversion bugs shipped without it. See
[MODELING_GUIDE.md](MODELING_GUIDE.md) for the six-bone rig contract the model must keep.

## Palette

Pixel-sampled from the vanilla dragon textures (see [DESIGN.md](DESIGN.md) §2): near-black
scales `#0E0E0E`–`#1C1C1C`, wing membrane darker still at `#0A0A0A`–`#0F0F0F`, charcoal
horns and claws `#474747`–`#626262`, light grey plating `#696969`–`#8A8A8A` mapped to the
pecs, abs, throat and tail underside, and emissive eyes `#9600BC` / `#CC00FA` / `#E079FA`.

**No vanilla assets ship in this repo or the jar** — Mojang textures cannot be
redistributed. Every texture here is original work painted in that sampled palette;
vanilla particles and sounds are referenced by resource location at runtime.

## Licence

MIT. Not affiliated with Mojang or Microsoft.
