# EnderdragonAnthro — Design Specification

A Minecraft mod that lets the player choose to become an **anthropomorphic Ender Dragon**:
canon Ender Dragon height, canon color palette, all dragon abilities on keybinds, dragon
boss bar / health / hitbox — while keeping every normal player mechanic (inventory,
crafting, hunger, tools, sneaking, swimming, etc.).

---

## 1. Canon Ender Dragon reference (Java Edition)

Everything below is the vanilla data the mod mirrors.

### 1.1 Core stats

| Stat | Canon value |
|---|---|
| Entity ID | `minecraft:ender_dragon` |
| Health | **200 HP** (100 hearts) |
| Overall hitbox | **16 × 16 × 8 blocks** (W × L × H) — largest natural mob in the game |
| Boss bar | Pink/magenta, progress style, named "Ender Dragon" |
| XP dropped | 12,000 first kill, 500 subsequent kills |
| Fire/lava | Immune |
| Status effects | Immune to all potion effects |
| Damage sources | Only player-dealt damage and explosions hurt it |

### 1.2 Multi-part hitbox

The dragon is Minecraft's only multi-part entity. Its 8 sub-hitboxes
(head, neck, body, 2 wings, 3 tail segments) each register hits separately:

- **Head** — takes full damage.
- **Every other part** — takes `(damage / 4) + 1` (75%+ reduction).
- Immune to critical hits; deflects/ignites arrows while perched.

### 1.3 Attacks and behaviors

| Ability | Canon behavior | Damage (Easy / Normal / Hard) |
|---|---|---|
| **Charge / head strike** | Dives at the player, huge knockback | 6 / 10 / 15 |
| **Wing / body buffet** | Contact with wings or body launches entities far | 3 / 5 / 7 |
| **Dragon's Breath** | While perched on the portal, exhales a purple lingering cloud (like Lingering Potion of Harming II) | ~6 per tick-hit while inside cloud |
| **Dragon Fireball** | Purple projectile; on impact deposits a lingering harming cloud (no direct impact damage/knockback, cannot be deflected) | cloud: ~6 per hit |
| **Crystal healing** | White beam from End crystal; heals **1 HP per half-second** (2 HP/s). Popping a linked crystal deals 10 damage to the dragon | — |
| **Block destruction** | Destroys every block it flies through **except** obsidian, crying obsidian, end stone, bedrock, iron bars, end portal frames/blocks, command blocks, barriers (the `#dragon_immune` block tag) | — |
| **Perch** | Lands on the exit portal; melee-vulnerable but arrow-immune | — |

---

## 2. Canon color palette & anatomy (the art bible)

All colors below are **pixel-sampled from the actual vanilla textures**
(`assets/minecraft/textures/entity/enderdragon/dragon.png`, `dragon_eyes.png`,
`dragon_fireball.png`, version 1.21.11).

| Region | Canon color | Sampled hex |
|---|---|---|
| Body scales (head, back, limbs, tail top) | Near-black with subtle noise | `#0E0E0E`–`#1C1C1C` (dominant `#1A1A1A`/`#1C1C1C`) |
| Wing membrane | **Darkest tone in the sheet** — darker than the body | `#0A0A0A`–`#0F0F0F` |
| Horns, claws, dorsal/tail spikes | Mid charcoal gray | `#474747`–`#626262` |
| **Belly plates — the countershade** | Light noisy gray plating on the underside | `#696969`–`#8A8A8A` |
| Wing-bone struts | Same light gray family as belly plates | `#696969`–`#858585` |
| **Eyes (emissive)** | Glowing magenta-purple | edge `#9600BC`, core `#CC00FA`, bloom `#E079FA` |
| Mouth interior | Same magenta family as the eye bloom | `#E079FA` |
| Dragon's Breath / fireball VFX | Purple ramp | `#592463`, `#9C49AF`, `#DC75FA`, `#F3C5FF` |
| Boss bar | Pink/magenta | vanilla `BossBarColor.PINK` |

**Countershading rule:** canon value ordering is `wing membrane (darkest) < body scales <
horns/claws < belly plates (lightest)`. Every anthro texture must preserve that ordering.

### Canon anatomy to preserve

- Long **snout** with blocky nostril nubs and a hinged lower jaw.
- Two swept-back **horns** on the skull (plus small jaw spikes).
- Bat-style **wings**: one arm bone + finger struts, membrane between.
- Row of **dorsal spikes** from neck down the spine to the tail tip.
- **Tail** in 3 tapering segments, spiked along the top.
- Four-clawed feet.

---

## 3. Anthro translation (the player form)

Upright, bipedal, humanoid silhouette — a person-shaped dragon, not a dragon on two legs.

| Feature | Anthro design decision |
|---|---|
| **Height** | Same as the canon dragon's hitbox height: **8 blocks tall** standing. (Player is 1.8 → scale factor ≈ **4.44×** via the vanilla `generic.scale` attribute.) |
| **Color** | Canon palette above, unchanged. Near-black scales everywhere; emissive magenta-purple eyes rendered on a glow layer exactly like `dragon_eyes.png`. |
| **Countershading** | The dragon's light-gray belly plating (`#696969`–`#8A8A8A`) translates to the anthro **front torso**: segmented plates over the **pecs and down the abs** (chest and belly), continuing up the **throat/under-jaw** and down the **tail underside** to the tip, plus the palm side of the wrists. On an upright biped the countershade faces *forward*, not down — the back, shoulders, and outer limbs stay near-black scale. Plate seams follow muscle rows (pec line, ab segments) so the canon "plated underbelly" reads as anatomy. |
| **Snout** | Keep a true muzzle — shortened ~30% from canon proportions so first-person view and eating/drinking animations read correctly, but with the canon nostril nubs and jaw line. Not a flat human face. |
| **Horns** | Both canon skull horns, swept back, charcoal with lighter tips; scaled to head. |
| **Wings** | Back-mounted (scapula-anchored), canon bone-and-membrane structure. **Folded** while walking (else they'd add ~6 blocks of visual width); spread only for flight/glide/buffet animations. Cosmetic only — no wing hitbox (see complications). |
| **Tail** | Long tapering tail with canon dorsal spikes, roughly leg-length ×1.5. Animated (idle sway, counterbalance while sprinting). Cosmetic only — no collision. |
| **Muscles** | Anthro means humanoid muscle mapping: broad chest and heavy shoulder/back mass (wing anchors need lats/pecs), thick neck, powerful thighs. **Plantigrade** legs (human-style stance) rather than digitigrade — keeps every vanilla animation (walk, sneak, swim, sit) retargetable and keeps eye-height math sane. Digitigrade looks more draconic but breaks pose retargeting; revisit later as an option. |
| **Hands/feet** | Five-fingered clawed hands (players need fingers for held-item animations), four-clawed feet per canon. Claws use horn grays. |
| **Spikes** | Dorsal spike row from the back of the skull down the spine to the tail tip, shrinking as they go. |

---

## 4. Player-form gameplay spec

### 4.1 Stats while transformed

| Attribute | Value | Mechanism |
|---|---|---|
| Height / hitbox | 8.0 tall × ~2.67 wide (0.6 × 4.44) | `generic.scale` = 4.44 |
| Max health | 200 HP | `generic.max_health` modifier |
| Boss bar | Pink boss bar showing the player's name + health to nearby players | per-player `ServerBossEvent`, config toggle |
| Damage model | Flat "body resilience": incoming damage × 0.25 + 1, **except** headshot-zone hits (top ~12% of hitbox) which take full damage — approximates the canon head/body split on a single hitbox | damage event hook |
| Immunities | Fire/lava; all status effects (config: harmful only vs all) | damage/effect hooks |
| Step height | 2.5 blocks | `generic.step_height` |
| Fall damage | Reduced (safe-fall +10) | `generic.safe_fall_distance` |
| Reach | Scaled so you can reach your own feet | `generic.block_interaction_range`, `generic.entity_interaction_range` |
| Melee | Bare hand hits for the canon head-charge 10 + heavy knockback | `generic.attack_damage` +9, `generic.attack_knockback` |
| Mob aggro | No mob targets you (canon: nothing is hostile to the dragon) — includes retaliation, warden, piglins, and enderman stares | `canBeSeenAsEnemy` hook |
| Stride | Fully proportional: movement speed and jump scale with the 4.44× frame | `generic.movement_speed` ×4.44, `generic.jump_strength` |

### 4.2 Abilities → default keybinds

All server-authoritative with cooldowns; client sends intent packets only.

| Key | Ability | Behavior (mirrors canon) |
|---|---|---|
| `R` | **Dragon's Breath** | Exhale a cone; spawns lingering Harming-II-style purple `AreaEffectCloud`s at the impact area. 4 s cooldown. |
| `G` | **Dragon Fireball** | Fires the actual `minecraft:dragon_fireball` projectile; impact deposits a harming cloud. 3 s cooldown. |
| `V` | **Wing Buffet** | AoE around the player: canon wing damage (3/5/7 by difficulty) + massive knockback/launch. 5 s cooldown. |
| `C` (hold) | **Charge** | Forward dash; entities hit take canon head damage (6/10/15) + strong knockback. 4 s cooldown. |
| `Space` ×2 | **Flight** | Wing flight: hold jump to climb, powered flight like slow-fall + elytra hybrid. Config: free flight vs stamina-limited. |
| — (passive, always on) | **Crystal Link** | Within 32 blocks of an End crystal, the crystal beams at you and heals 2 HP/s (canon rate). Anyone popping your linked crystal deals you 10 damage. |
| `B` | **Block Break wake** (config-gated, off by default) | While charging/flying, break `#dragon_immune`-exempt blocks in your path. Pure grief potential — server config. |
| `Y` | **Transform** | Toggle human ⇄ dragon form (also available as an item/ritual later). |

### 4.3 Player mechanics that stay 100% vanilla

Inventory, hotbar, crafting, tools/weapons/armor (rendered scaled), hunger and eating,
XP, sneaking, sprinting, swimming, sleeping (see complications), riding (see complications),
chat, advancements, death/respawn (keep inventory rules untouched).

---

## 5. Complications (known, accepted, or open)

### 5.1 Hard technical complications

1. **Players cannot be multi-part entities.** The canon dragon's wing/tail hitboxes are
   `EnderDragonPart` children — that system is hardcoded to the dragon. A player has exactly
   one AABB. So wings and tail are **cosmetic**; you cannot be shot in the wing. Faking it
   with invisible rider entities is possible but breaks anticheats, laggy, and fragile. Decision: single scaled AABB.
2. **Hitboxes are axis-aligned boxes and never rotate.** A 2.67-wide box does not become
   wider when you spread 16-block wings. Canon dragon "size" (16×16) is mostly wings; an
   upright anthro torso at 8 blocks tall with a ~2.7-block box is actually the honest match.
3. **Head-damage split is approximate.** Vanilla damage events don't tell you *where* on the
   hitbox a hit landed; for projectiles we can raycast the impact height, for melee we
   approximate by attacker look-vector. Exact canon behavior is impossible on one box.
4. **First-person view at 7.2-block eye height.** The vanilla scale attribute moves the
   camera correctly, but leaves clip through nearby leaves/blocks, and the hand/claw
   first-person model needs full re-rendering at scale.
5. **The boss bar is per-viewer.** Boss bars are a server→client UI channel, so *other*
   players see yours; you see your own health normally (plus optionally your own bar).
   Many transformed players = boss bar spam; needs range + count limits and a config.

### 5.2 World-interaction complications (being 8 blocks tall)

6. **You do not fit anywhere.** Villages, strongholds, your own base, 2-block caves — all
   inaccessible while transformed. Suffocation checks will fight you constantly under
   ceilings. Mitigations: easy transform toggle, and/or a config "crouch-shrink" (sneaking
   drops scale to ~1.0 — non-canon but playable).
7. **Nether portals are a wall.** A standard 2×3 portal cannot pass a 2.67×8 hitbox. Either
   build 4×9+ portals, auto-shrink on portal contact, or block nether travel while transformed.
8. **Beds, boats, minecarts, horses.** Riding/sleeping at 4.44× scale renders absurdly and
   half-clips into the ground. Options: block them while transformed, or accept the jank.
   (Current decision: allowed but visually unsupported; revisit.)
9. **Doors, ladders, trapdoors, buttons** are reachable (scaled reach) but unusable as
   passages. Ladders technically work; you'll clip through ceilings while climbing.
10. **Mob AI oddities.** Melee mobs path to your feet and may never connect with your
    center; some ranged mobs aim at eye height and overshoot. You will accidentally cheese
    combat. Endermen eye-contact detection at giant eye height triggers oddly.
11. **Camera.** Third person clips into terrain constantly; we should ship a longer default
    third-person distance and a shoulder cam while transformed.

### 5.3 Balance & multiplayer complications

12. **200 HP + 75% body resistance + status immunity is objectively broken in PvP.** Canon
    numbers cannot be "fair." Ship two presets: **Canon** (the table above, for
    single-player power fantasy / boss-player events) and **Balanced** (e.g. 60 HP, 40%
    resistance, effects allowed) — server picks.
13. **Healing economy inverts.** Instant Health II heals 8 HP — 4% of a 200-HP pool. Crystal
    Link becomes the only real sustain, which is canon-flavored but means dragons camp
    crystals. Golden apple/regen scaling is a config knob.
14. **Anticheat/server flags.** Charge dashes, flight, scaled reach, and step height all
    look like cheating to Paper/Spigot anticheats and to mods like NoCheatPlus. Server-side
    implementation (attributes + server-spawned motion) avoids most of it, but flight on
    survival servers needs explicit anticheat exemptions.
15. **Block-destruction ability is grief.** Off by default, permission-gated, and respects
    the `#dragon_immune` tag plus claim/protection mods' events.

### 5.4 Rendering/compat complications

16. **Replacing the player model** conflicts with skin mods (Figura, CustomPlayerModels,
    Skin Layers 3D), capes, and elytra rendering. We render our own GeckoLib model and hide
    vanilla layers; elytra is hidden (you have real wings), capes are hidden while transformed.
17. **Emissive eyes need a glow layer.** Vanilla-style `eyes` render layer works, but shader
    packs (Iris/OptiFine) handle emissives differently — needs testing per pack.
18. **Scale-attribute interactions.** Pehkui and other scaling mods fight over the same
    attribute; declare incompatibility or add a compat handshake.
19. **Animation retargeting.** Every vanilla pose (swim, crawl, sneak, sleep, bow-draw,
      shield, eating) must be re-authored on the anthro rig; missed ones will T-pose. This is
    the single biggest art workload in the mod.
20. **Vanilla textures cannot be redistributed.** Mojang's EULA forbids shipping game
    assets, so `dragon.png` etc. must never be committed to this repo or bundled in the
    jar. The anthro skin is an **original texture painted with the sampled palette**
    (section 2); anything reused verbatim (fireball, breath particles, sounds) is
    referenced by resource location at runtime — the game already has them.

### 5.5 Version/platform decision

- **Minecraft 1.20.5+** is effectively required: it added the vanilla `generic.scale`,
  `step_height`, and interaction-range attributes (max scale 16 ≥ our 4.44). Targeting
  **1.21.x** recommended.
- Loader: **NeoForge or Fabric** — Fabric + GeckoLib recommended for the model/animation
  pipeline; final call pending.
- Bedrock is out of scope (entirely different addon system).

---

## 6. Roadmap sketch

1. **M1 — Transform core:** form state, scale/health/step attributes, persistence, transform keybind.
2. **M2 — Model & palette:** anthro GeckoLib model + canon-sampled texture, emissive eyes, walk/idle/sneak/swim retargets.
3. **M3 — Abilities:** breath, fireball, buffet, charge, flight, crystal link; cooldown HUD.
4. **M4 — Boss bar & damage model:** per-viewer boss event, resistance/immunity hooks, headshot approximation.
5. **M5 — Livability:** portal handling, camera, crouch-shrink option, config presets (Canon/Balanced), grief gating.
6. **M6 — Compat & polish:** shaders, anticheat exemptions doc, mob-AI tuning, sounds (reuse `entity.ender_dragon.*`).
