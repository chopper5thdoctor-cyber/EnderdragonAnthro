# Enderdragon Anthro

A Fabric mod for Minecraft **1.21.1** that lets you become an **anthropomorphic Ender
Dragon** — canon 8-block height, canon 200 HP, canon palette, the dragon's abilities on
keybinds, a court of named endermen, and a pink boss bar other players see — while
keeping every normal player mechanic: inventory, tools, hunger, crafting, all of it.

Tunnel through a mountain at ninety blocks a second, breathe a third kind of fire,
read the health of everything in sight through the walls it is standing behind, and
send your court to cut a portal wide enough to walk through.

| | |
|---|---|
| **Play-test guide** | **[TESTING.md](TESTING.md)** — keys, an eight-part test plan, and what is most likely to break |
| **Design rationale** | **[DESIGN.md](DESIGN.md)** — canon reference data, the anthro translation, and the honest list of complications |
| **Making the model** | **[MODELING_GUIDE.md](MODELING_GUIDE.md)** — the Blockbench rig contract |
| **Build tooling** | **[tools/README.md](tools/README.md)** — the bbmodel → Java converter and its verifier |

---

## What works

**The form.** `Y` transforms. It survives relogging and death. You become 8 blocks tall
with 200 HP, 2.5-block step height, scaled reach, and dragon-grade melee.

Stride and jump are **proportional to size**, so speed/height holds at 1 — the same
ratio the first-person bob keeps. At the canon eight blocks that is a 4.44× stride,
up from the 3× it used to be trimmed to; expect ground travel to outrun elytra
cruising. Jump takes the *square root* of that ratio, because jump height goes as the
square of launch speed — 4.44× the strength would be a twenty-block hop. The square
root is what makes a creature 4.44× your size clear 4.44× your jump, about 5.5 blocks,
well inside the 13-block safe fall.

**The body.** A 75-cube anthro dragon built in Blockbench and compiled into the mod by
`tools/bbmodel_to_java.py`. The vanilla player body is hidden and this replaces it,
riding the player skeleton so every vanilla animation drives it for free. The head
is handed the torso's rotation, because this rig has a neck and vanilla's does not:
a player's head is a cube on a flat chest, so crouching can pitch the body and leave
the head behind without showing a seam. Here four units of neck are hidden inside the
chest, and the chest used to rotate straight off them. Eyes and mouth
render on a fullbright pass, so they glow in the dark.

**Canon toughness.** Everything you take is `damage/4 + 1`. Immune to fire, lava, every
status effect, and — for fairness, since an anthro walks among its own crystals rather
than circling them — End Crystal blasts. Flying into scenery costs nothing at all, and
a glide banks no fall damage however far you dive.

**And to cold**, which is the same claim from the other end: a thing that shrugs off
lava has no business shivering in powder snow. The freeze meter never fills, so the
frost vignette never draws over your own violet.

**And they are afraid of you.** Nothing targets a dragon unprovoked — canon — but
"unprovoked" is doing work there. Anything that can see you *runs*, and anything you
actually hit stops running and comes back at you for twenty seconds. Endermen are the
only exception, because the court is made of them. Livestock were exempt at first, on
the theory that a field emptying as you walked into it would make the overworld
unusable; played, the cows standing placidly in the middle of a stampede were the one
thing on screen saying you were not frightening.

Fear is a goal at priority 3, where a creeper keeps its fear of cats, so it competes for
the navigation and interrupts strolling like any other. It is not `AvoidEntityGoal`
itself, and the reason is worth writing down: that goal searches with
`TargetingConditions.forCombat()`, whose test calls `canBeSeenAsEnemy` — which a dragon
answers *no* to, by the very rule above. Every vanilla way of asking "what is near me
that matters" is blind to you, so the search here is ours. Bats have their own path
again, in `BatFearMixin`: a bat's movement never touches the goal selector, so its
target block is aimed away from you instead.

**Crystal Link.** Stand within 32 blocks of an End Crystal and it beams at you, healing
2 HP/s and refilling hunger. Pop a crystal that is healing you and it costs 10 HP, canon.

**Flight is literally elytra.** Rather than imitating the physics, a mixin keeps the
fall-flying flag set so the untouched vanilla elytra code path runs. Double-tap `Space`
mid-air to start, then **every further tap beats the wings** — twice a firework
rocket's push toward a 2.6 target speed, because these are wings and a rocket is a
firework. Two or three taps a second holds top speed.

A boost is a *target* rather than a force, which is why even that settles below
terminal velocity: it pulls your speed toward a number and then stops mattering, while
falling adds 0.08 a tick forever and balances against drag at 3.92. Sustained flight is
2.7 a tick against a rocket elytra's 1.7 — and 4.5 with Explosive Intent armed, which is
the only speed here that outruns gravity.

**The court.** Four named elite endermen — **Vaëlle** (red), **Keshaire** (blue),
**Nyrelle** (green) and **Orrinne** (orange), French like Jean — scaled to 4 blocks, each addressed and ordered
separately. Everything a shade says is spoken in its own colour, so a four-shade
court reads as four voices rather than one wall of text. They never turn on you.

**The end goes with you.** In dragon form you trail portal particles the way an
enderman does, sized to an eight-block body. Transforming either way tears the end
open — a few hundred particles, a reverse-portal implosion through it and a wash of
dragon's breath — on a two-second cooldown, since `Y` is free and holdable and the
burst is not something to post to every client in view distance every tick.

---

## Keys

| Key | Ability | Cooldown |
|---|---|---|
| `Y` | Transform (dragon ⇄ human) | — |
| `R` | Dragon's Breath — a visible jet along the raytrace; the pool always lands on ground | 4 s |
| `G` | Dragon Fireball — canon lingering cloud **plus** an entity-only blast | 3 s |
| `V` | Wing Buffet — canon 3/5/7 damage, huge knockback | 5 s |
| `C` | Charge dash — canon 6/10/15. Sprinting alone also deals charge damage | 4 s |
| `B` | **Explosive Intent** toggle — tunnel through terrain, and fly faster than falling | — |
| `X` | Evasive jump — somewhere random 1000+ blocks out, under open sky | 60 s |
| `K` | Warp to a random player beyond 100 blocks | 10 s |
| `J` | Return to your anchor | 5 s |
| `H` | **Dragonsight** toggle — see the passage below | — |
| `Z` | Summon a shade (four maximum) | 10 s |
| `M` | Open **the court** — the orders screen | — |
| `N` | **Dragonfire** — hold to breathe a stream of purple fire | 0.25 s |
| double-`Space` | Start gliding (mid-air); once gliding, **every** tap beats the wings | — |

All rebindable. The HUD is a column down the left edge, each key chip with its ability
name beside it; chips flash on press, drain while recharging, and stay lit while a toggle
is armed.

`/shade collect <block>` names a quarry by typing rather than by looking at one. It
speaks to the shade you are looking at, else the first. There used to be an `N` key
that cycled a "listening" shade for this one command to read; the court screen names
every shade on its own row, so `N` was a second and worse way to say the same thing,
with state you could not see.

---

## The court's orders

`M` opens a screen listing every shade you have raised, with all four orders one click
each and a text field for the quarry. Orders used to cycle on a keypress, which meant
reaching Crystal cost you whatever the shade was carrying and re-read whatever block you
happened to be looking at on the way through; nothing is passed through now.

| Order | Behaviour |
|---|---|
| **Defend** | Holds a post 2.5 blocks off and attacks anything that targets you |
| **Attack** | Attacks whatever you look at, else the nearest hostile |
| **Collect** | Leaves, digs out a vein within 100 blocks, walks back in with it |
| **Crystal** | Finds bedrock and **forges** an End Crystal on it every 20 s |
| **Portal** | Raises a nether portal **sized for you** and lights it, then goes back to Defend |

Shades make crystals rather than spending yours — a dragon has no hands for a workbench,
so stocking a domain is the court's trade.

**Collect** needs a named quarry — pick one from the row's block picker. The shade
says its piece and **leaves the world**: no entity, nothing to lose. It searches 100
blocks in every direction and the world's full height, takes up to **64 blocks in
whole veins** (flood-filled through touching blocks, diagonals included, so a seam
comes out in one piece rather than a stack of scattered holes), and steps back out
of nowhere in front of you thirty seconds later with the haul in hand.

That thirty seconds is the cooldown, wearing the shape of a journey. The search
itself takes about three — it runs three chunk columns a tick so a hundred-block
sweep never lands as one hitch, and skips whole 16³ sections whose palette does not
mention the block, which is what makes a full-height search affordable at all.

**The haul fills as it waits.** The count climbs one block roughly every half
second, 0 to 64 across the trip, and the court screen shows it running. **Recall
takes whatever it says** — call the shade in at ten seconds and it walks back
with about twenty and stands ready.

**The count is real, not a timer wearing a number.** It is capped by what the
sweep actually found: a quarry with ten blocks of it inside a hundred stops the
count at ten and holds there, and one with none never leaves zero, however long
you wait. Every block counted is a position found in the world, and each is
re-checked and removed at the moment of return — so the stack you are handed is
exactly the stone that left the ground.

The rate is constant, which is what keeps that a decision instead of an exploit:
recalling early and sending it straight back out earns exactly what waiting
would have. What you buy is a shade at your side in the meantime; what you pay
is giving the order twice.

Blocks are only removed at the moment of return, so anything you mine out in the
meantime simply is not there.

This replaced a version that sent a real enderman roaming. Every failure it had came
from being a real entity in real terrain: it hopped into ungenerated chunks and
landed inside bedrock, got walled into stone, suffocated once its escape blink was
taken away, and could not be recalled because it was no longer anywhere. An errand
with no entity has none of those.

**Recall is the way to call an errand off.** It brings the shade back early with
what it has gathered so far. Orders and Pet still just report the time remaining —
there is no entity out there to give a duty to or to touch. A finished Collect
hands the stack over and drops back to **Defend**, as does an Attack order with nothing
left to hunt — standing with you is the default duty, not a fifth order you have to pick.

**They keep their distance.** A shade walks to a ring two and a half blocks out
and stops there, watching you. They used to path to your exact feet — a path
only ends when it arrives, and arriving meant standing where you were standing,
which is why they shoved. Walk into one yourself and vanilla collision still
applies; they simply no longer come to you.

**Their faces burn.** Every expression a shade wears is emissive — her resting eyes and
the pet, angry and dizzy faces alike — because an enderman's eyes are the brightest thing
in a dark room, and eyes that were not lit made the court read as somebody in an enderman
costume rather than as one of them. Each glow sheet is extracted from its own painting by
one rule, *the violet marking and not the black hide*, so the lit half cannot drift out of
register with the painted half. The two bright tiers in that paint, `#E079FA` and
`#CC00FA`, are the canon enderman emissive colours exactly — those two are the whole of
vanilla's palette on `enderman_eyes.png`, six lit pixels in two shades.

**They speak like a court.** Ten lines per order, picked at random, each in the
shade's own colour, addressing Jean as a sovereign — *"At your side, my liege."*
Four shades answering one order with one identical sentence was what made them
read as spawned mobs rather than as anyone's retinue.

**They arrive and leave like endermen.** Departing on an errand, walking back in
with a haul, and both ends of a Recall hop all tear open in portal particles —
an order of magnitude past the drift they carry idly, so it reads as an event
and not as the same shimmer.

**They answer for you.** Take two hits from the same attacker inside five seconds and
every shade within 48 blocks breaks off and goes for it, for fifteen seconds. Ones away
on business keep their cargo and their errand.

## The breath

`R` traces a ray to the ground and lays the pool where it lands — and now draws
the ray. Dragon's breath runs the whole segment from the mouth to the landing
point for half a second, tight at the mouth and spreading as it falls, so the
cloud is visibly connected to the dragon that exhaled it rather than appearing
at the far end on its own.

Anything standing in that jet takes the exhale, once, at the moment it happens.
It is given the **same `MobEffectInstance` the pool carries**, not a damage
figure of this mod's own — so what dragon's breath does to a thing is vanilla's
answer, undead included: they are healed standing in the jet exactly as they are
healed standing in the pool.

Once, not per tick. The jet stays drawn for ten ticks afterwards, but a beam
reapplying instant damage on every one of them would be twenty times the ability.

## Dragonsight

Toggle with `H`. Four things at once, and it says which build it is when it opens, which
is there because "it does not work" and "you are running last week's jar" look identical
from the outside.

**A violet wash** over the world and under the HUD — `#CC00FA`, the darker of the two
colours painted on the dragon's own eyes, so the world is seen through the same violet
they are. Drawn at the head of `Gui.render` rather than from `HudRenderCallback`, which
fires at the end: a tint you cannot read your own health through is a blindfold.

**Dark vision.** The dragon is immune to every status effect, which meant it was also
immune to the night vision its own sight grants; the exception is allowed only while the
sight is open, so a thrown potion still does nothing.

**Health on everything.** Every living thing wears its name and its health, coloured by
what is left, at 2.5× the usual tag. It is drawn as a name tag rather than as anything
of ours — the same path that puts an account name over a player's head — so it cannot be
positioned wrong. Reaching it took answering `shouldShowName` at the *call site*: it is
overridden three deep and each override ANDs the one below with a clause a nameless mob
fails, so patching any single one is multiplied away by the next.

**Clear water.** Underwater fog is pushed past the far plane. Lava is untouched, and
deliberately: water fog is atmosphere, so seeing through it is a sharper eye, while lava
fog is the fluid being opaque.

**And where the doors are** — a hit indicator around the crosshair rather than a marker
in the world, since a portal is usually behind you or under a mountain. A teal **eye**
points at the nearest stronghold, asked of the chunk generator with
`StructureTags.EYE_OF_ENDER_LOCATED` so it answers from the seed whether or not the
chunks exist. A violet **doorway** points at the nearest nether portal, which is what
keeps you from getting lost on the other side. Both carry coordinates, because a bearing
tells you which way to set off and nothing about where you are going.

The doorway points at a portal you have *seen*, not one currently loaded. Blocks only
exist in loaded chunks, so a scan-based mark went out at about fifty metres — which is
exactly backwards, since you need the bearing home most when home is far away and behind
you. The block scan is now a way of learning gates rather than of listing them: anything
seen once is written down per dimension, saved with the world, and forgotten only when a
*loaded* chunk proves it has been broken.

## Gates

A dragon is 2.67 blocks across and 8 tall. A vanilla portal's interior is 2×3, so **you
cannot fit through your own world's portals** — the frame is narrower than the body.

Order a shade to **Portal** and it raises one measured off your hitbox with a block of
clearance on every side: 5×10 of interior at the canon eight blocks, well inside
`PortalShape`'s limit of 21 each way. The measurement is taken from the *standing* pose
rather than the current one, which matters because ordering a gate is something you do
on the wing and a gliding player's hitbox is 0.6 tall before scale — that read as 2.67
and asked for a 5×5 hole.

The frame stands at the highest ground under its whole footprint, and its sill is
allowed to be buried, because a sill is a thing that replaces ground. Everything above
the sill has to be clear: a shade refuses rather than cutting a doorway through
somebody's hillside.

The far side is vanilla's problem — `PortalForcer` builds the destination at the only
size it knows — so the portal you arrive in is widened on arrival instead. That also
repairs portals that were already there and too small, which a generator patch would
not. Ignition is vanilla's on both sides: `BaseFireBlock.onPlace` runs the portal check
for any fire, so only the shape is ours.

## Dragonfire

Hold `N`. A stream of purple fire out of the mouth, 24 blocks, costing hunger — not the
`R` breath, which lobs a pool at a place and leaves it.

It leaves **a third fire block**, after the orange one and the blue one. It burns at 3 a
tick against fire's 1 and soul fire's 2, spreads and ages out on a scheduled tick the
way vanilla fire is paced, and carries vanilla's five attachment booleans so it lies
flat against leaves instead of standing in the air as a cube. Its screen overlay is a
separate sheet from the block's, because the block has to touch the ground and the
overlay wants height.

**Snow goes off as steam.** The stream clears it where it lands and a burning patch keeps
clearing it as it ticks — the layer you walk over, the block you build with, and the
powder that hides a pit, all three. Steam rather than meltwater, because water is what a
slow thaw leaves and this is not one. A snowdrift standing in the middle of a fire that
outlasts lava's was the one thing on screen arguing it was not hot.

## Explosive Intent

Toggle with `B`. It does two things, and both are what the real dragon does.

**You tunnel.** `EnderDragon` sets `noPhysics` and clears blocks inside its own hitbox
every tick, which is why its flight looks unbothered by terrain rather than like
something smashing through it. A player collides for real, so the bore is cut *ahead*
of the move instead: a capsule swept two ticks along your heading, widening with speed
— about 15 blocks across and 9 long at full pelt. Vanilla's two rules are kept exactly.
`BlockTags.DRAGON_TRANSPARENT` is passed over, `BlockTags.DRAGON_IMMUNE` survives
(bedrock, obsidian, end stone, iron bars, barriers, the End's portal furniture), and
`mobGriefing` is obeyed. Nothing drops.

It only cuts when you are moving fast enough that a wall *would* have hurt.
`LivingEntity.travel` bills a gliding player `(speedLost * 10 - 3)`, so the threshold is
vanilla's own 0.3 a tick — or a descent steeper than 45°, which is the 2.45 terminal
sink for that angle. Drift past a cliff on the way down to land and nothing breaks.

**You fly faster than you fall.** Armed, a wingbeat pulls toward 4.5 a tick instead of
2.6 — 90 blocks a second, clearing free fall's 3.92 even when the beat is lazy. Water
and lava stop slowing you above that speed, since `LivingEntity.travel` picks swimming
before elytra and a lake would otherwise kill a flight for a tick.

The risk is not bolted on. Terrain arrives faster than the server sends it, and the one
thing that will not move is the one thing you meet at full speed.

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

**On Windows**, `run.bat` runs the dev client and cleans up its own JVMs afterwards. On
a build failure it now prints a banner and **pauses** so the error stays on screen
instead of the console closing over it. Also,
`unlock.bat` clears anything still holding the folder (it only stops Java processes whose
command line points at this folder, so other Java apps are untouched). The Gradle daemon
and file-system watching are disabled in `gradle.properties` — between them they are why
a JVM used to outlive the console and keep the directory locked.

## Working on the model

Edit `art/dragon_form.bbmodel` in [Blockbench](https://blockbench.net), then:

```bash
python3 tools/bbmodel_to_java.py art/dragon_form.bbmodel
python3 tools/verify_model.py          # always run this
python3 tools/preview_wings.py         # and this, if you touched the wings
```

`python3 tools/syntax_check.py` is the one to run after editing **Java**. It
catches parse errors and references to fields and methods that do not exist on
this mod's own classes — which is what broke the last two builds. It cannot
check anything touching a vanilla class; there is no Minecraft on the machine
this is written on. See [tools/README.md](tools/README.md#syntax_checkpy).

`DragonFormModel.java` is **generated** — never hand-edit it. A constant you add
by hand survives exactly until the next conversion; put it in the template in
`bbmodel_to_java.py` instead. The verifier now fails loudly if the generated
file is missing a symbol the rest of the mod uses. The verifier rebuilds every
cube's world-space corners from both the generated Java and the `.bbmodel` and compares
them; it exists because three conversion bugs shipped without it. See
[MODELING_GUIDE.md](MODELING_GUIDE.md) for the six-bone rig contract the model must keep.

## Size

`config/enderdragonanthro.json`, written on first run:

```json
{
  "heightBlocks": 8.0,
  "trueProportions": false,
  "eyeHeightRatio": 0.0
}
```

**`trueProportions`** decides whether the rig's own height means anything.

The trim is `RENDER_SCALE = 1.8 * 16 / SKULL_HEIGHT` — the rig's height is in the
**denominator**, so with the trim on, *any* rig draws at exactly the hitbox height.
Lengthen the legs and the dragon does not get taller; the proportions shift inside
the same eight blocks and nothing else changes. That is why this defaults to `true`:
off, it silently cancels whatever you did in Blockbench.

| rig height | trim on | trim off |
|---|---|---|
| 110 units | 8.00 blocks | 7.64 blocks |
| 133 units *(current)* | 8.00 blocks | 9.24 blocks |
| 170 units | 8.00 blocks | 11.81 blocks |

The converter prints the rig height and both figures on every run, so a leg change
tells you what it did.

The cost of `true`: the model stands about 18% above its hitbox, so your camera —
which sits at 90% of the *hitbox* — ends up around chest height on the model rather
than at its head. Raising `heightBlocks` does not fix that; the ratio is fixed.

**`heightBlocks`** is the hitbox, and what every size-derived stat comes off — step
height, reach, safe fall, jump. 8.0 is the canon Ender Dragon. The canon numbers that
are *not* about size stay put: 200 HP, the damage figures, the 3× stride. Vanilla caps
the scale attribute at 16, so the ceiling is 28.8 blocks; outside that it clamps with
a warning.

**`eyeHeightRatio`** is where the eye sits as a fraction of the hitbox — the red
plane in F3+B, and the origin for the camera, block picking, line of sight and
projectiles.

**`0.0`, the default, puts it on the model's painted eyes.** The converter reads
their centre off the emissive sheet and writes it to `DragonRig.EYE_RIG_Y`; paint
them somewhere else and the camera follows. That is the whole point: vanilla's
`1.62 / 1.8 = 0.9` refers to nothing about the model, and `EntityDimensions.scale()`
multiplies height and eyeHeight together so the ratio survives any scaling — which
is why moving the head in Blockbench never moved the camera.

Set a number to override it. `tools/preview_eyeline.py <rigY>` converts a rig height
into the ratio that puts the plane there:

```
eyeHeightRatio 0 puts it on the paint, rig y 123.5:
  trueProportions true  -> ratio 1.0720
  trueProportions false -> ratio 0.9286
```

Above 1.0 puts the eye outside the hitbox. Legal, and what a head that overshoots
its box needs — but also where picking through your own ceiling begins, so it is
allowed rather than clamped away.

The first-person hand follows whichever you pick.

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
