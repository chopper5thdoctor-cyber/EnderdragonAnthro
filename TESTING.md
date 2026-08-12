# Testing guide

Everything you need to put this mod through its paces.

---

## Getting in

```
git pull
run.bat
```

`run.bat` launches the dev client and cleans up its own JVMs afterwards. If the
folder ever refuses to delete, `unlock.bat` clears whatever is holding it.

**Start in a Creative superflat world** for the first pass — you need room, spawn
eggs, and no distractions. Switch to Survival for the damage tests later.

**Before anything else, set these two options** or several things will look
broken when they are not:

| Option | Why |
|---|---|
| Options → Accessibility → **FOV Effects → 0%** | 3× sprint speed warps the screen badly otherwise |
| Press **F5** twice to third person | You cannot see your own body in first person |

---

## The keys

| Key | Ability | Cooldown |
|---|---|---|
| `Y` | Transform (dragon ⇄ human) | — |
| `R` | Dragon's Breath | 4 s |
| `G` | Dragon Fireball | 3 s |
| `V` | Wing Buffet | 5 s |
| `C` | Charge dash | 4 s |
| `B` | Crater punch **toggle** | — |
| `X` | Evasive jump (1000+ blocks) | **60 s** |
| `K` | Warp to a distant player | 10 s |
| `J` | Return to your anchor | 5 s |
| `H` | Wing boost | 1 s |
| `Z` | Summon a shade | 10 s |
| `N` | Address a shade | — |
| `M` | Give that shade its order | — |
| double-tap `Space` | Start gliding (mid-air only) | — |

The HUD along the bottom shows every ability with its name above the key. Chips
flash magenta when pressed and drain a shade while recharging. Crater stays lit
while armed. Nothing shows in human form.

`/shade collect <block>` names a quarry by typing instead of looking.

---

## Test 1 — the body

Press **Y**, then **F5**. Walk, sprint, sneak, swim, swing your arm, look around.

- Does the model track every animation without tearing apart?
- Wings folded on the back, spikes angled, obliques and delts sloped?
- **In the dark**: eyes and mouth should glow (they are on a fullbright pass).
- Your head should sit right at the top of the 8-block hitbox, horns just above.

If something is mispositioned, it is a conversion bug and I can verify it against
your `.bbmodel` in seconds — `python3 tools/verify_model.py`.

---

## Test 2 — stats and immunities (Survival)

`/gamemode survival`

- **100 hearts.** The bar shows ten rows.
- **Body resilience**: everything you take is `damage/4 + 1`. A fall that would
  kill a player should barely scratch you.
- Stand in **lava** — no burning.
- Have a witch throw a potion, or `/effect give @s poison` — **nothing applies**.
- Walk up **2-block ledges** without jumping.
- **Mobs ignore you completely.** Stand in a zombie pile at night. Creepers will
  not even swell. Endermen can be stared at safely.
- Punch a cow: it should launch. Bare-handed melee is 10 damage.

---

## Test 3 — flight

Jump off something tall, then **double-tap Space** in mid-air.

This is real elytra physics, not an imitation — a mixin keeps the fall-flying
flag set so the vanilla elytra code runs. Expect elytra handling exactly: dive
to gain speed, level out to glide.

- **H** boosts, reproducing a firework rocket's kick. Chain them.
- Landing ends the glide automatically.
- Transforming back to human mid-air should drop you out of it.

---

## Test 4 — the four attacks

Spawn a few **cows or creepers** — *not* zombies or skeletons for the breath test.

- **R (breath)** — aim at the ground far away, and at a shallow angle across
  open terrain. **The purple pool must land on the ground**, never hang at mouth
  height. That was the bug; aim at the horizon to check the fallback path.
- **G (fireball)** — now explodes on impact for entity damage with **no terrain
  damage**. This is the one to test on **skeletons and zombies**: the canon
  harming cloud *heals* undead, so the blast is what makes it work on them.
- **V (buffet)** — everything within 6 blocks launches.
- **C (charge)** — dash forward. Also note **sprinting alone deals charge damage**
  to anything you run into.

---

## Test 5 — crater punch

Press **B** (chip lights up), then **left-click a block**.

- 5×5×5 breaks instantly, **everything drops silk-touch**.
- **Obsidian and end stone do NOT break** — canon dragon limits.
- **Bedrock DOES break.** Deliberate. Do not do this at Y=-64 unless you mean it.
- Press **B** again to disarm. Verify normal block breaking returns.

---

## Test 6 — blink, warp, anchor

- **X (evade)** — dumps you 1000+ blocks away at a spot that fits your hitbox.
  60-second cooldown, the longest in the kit. You will land somewhere unexplored.
- **K (warp)** — needs a second player more than 100 blocks away. Singleplayer
  will just tell you there is no one to reach.
- **Anchor**: in dragon form you are **given a Homing Crystal automatically**
  (orange, unstackable, one at a time). Place it anywhere — no bedrock needed.
  Press **J** from anywhere to return to it.
  - Break it (anything can): it **shatters, does not explode**, and you are locked
    out for **5 minutes**, after which a fresh one appears in your inventory.
  - It deliberately does **not** feed Crystal Link — it is a waypoint, not a battery.

---

## Test 7 — Crystal Link

Place an **End Crystal** (the real one) on obsidian nearby, then hurt yourself
(`/damage @s 40`).

- A beam locks on and heals **2 HP/s**, and refills hunger at 1/s.
- Blow up the crystal while it is healing → **10 damage**, canon.
- **Its explosion cannot hurt you** while transformed. Your shades are not immune.

---

## Test 8 — the court

**Z** summons **Vael**, then **Kesh**, **Nyra**, **Orrin** — four maximum, each
with a coloured nametag showing its name and duty.

**N** addresses one (whoever you are looking at, else the next). **M** cycles
*that one's* order. So you can run all four jobs at once.

| Order | What to expect |
|---|---|
| **Defend** | Stays near you, attacks anything that targets you |
| **Attack** | Attacks whatever you look at, else the nearest hostile |
| **Collect** | Goes foraging, brings back exactly one stack, sets it down |
| **Crystal** | Finds bedrock, **makes** an end crystal every 20 s and places it |

**Naming a quarry:** look at a block while cycling into Collect, or type
`/shade collect diamond_ore`. With a quarry named, the shade **digs for it** and
teleports to that ore's real generation band (diamonds near Y −59, iron near 16,
copper near 48…) rather than searching blindly, and it **remembers the depth it
last struck the seam at** and favours it thereafter. With no quarry named it only
strips surface blocks, so it will not swiss-cheese your landscape.

They never target you, whatever happens.

---

## What is most likely to break

Two mixins target vanilla method names that could not be verified without
launching the game. Both fail **loudly at startup** with the method name in the
log rather than silently:

1. `LivingEntityGlideMixin` → `updateFallFlying`. If this fails, gliding is dead.
2. `PlayerCrystalImmunityMixin` → `Player.hurt`. If this fails, crystals hurt you.

A third, `EndCrystalTextureMixin`, is registered **optional** — if it misses, your
anchor renders as a normal end crystal and everything still works.

**If the game will not start, paste the crash log.** The method name in the error
is all I need.

Other things worth watching, none of them fatal:

- The camera clips into terrain constantly at this size. Known, not a bug.
- Worn armour renders as human-shaped pieces floating around the dragon.
- First person still shows human arms — a separate rendering system.
- You do not fit in caves, doorways, or a standard nether portal.
