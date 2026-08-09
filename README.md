# Enderdragon Anthro

A Fabric mod for Minecraft **1.21.1** that lets you become an **anthropomorphic Ender
Dragon** — canon 8-block height, canon 200 HP, canon palette, the dragon's abilities on
keybinds, and a pink boss bar other players see — while keeping every normal player
mechanic (inventory, tools, hunger, crafting, …).

Full design rationale, canon reference data, and the honest list of complications live in
**[DESIGN.md](DESIGN.md)**.

## Current status — M1 (transform core)

| Piece | State |
|---|---|
| Transform toggle (`Y`), persistent across relog & death | code complete |
| Canon stats: 4.44× scale (8 blocks tall), 200 HP, step height, scaled reach | code complete |
| Abilities: Breath `R`, Fireball `G`, Wing Buffet `V`, Charge `C`, wing flight (double-jump) | code complete |
| Body resilience (`dmg/4 + 1`), status-effect immunity, fire/lava immunity | code complete |
| Pink boss bar shown to nearby players | code complete |
| Anthro model + canon-palette texture (M2) | not started |
| Config presets Canon / Balanced (M5) | not started |

**Not yet compiled**: this scaffold was written in a sandbox that cannot reach
`maven.fabricmc.net` or Mojang's servers, so `gradle build` has not been run. Expect the
usual first-build friction (version bumps in `gradle.properties`, possibly small mapping
renames). Version numbers current as of writing; check <https://fabricmc.net/develop/>.

## Building

```bash
# with a local Gradle 8.10+ install (or generate a wrapper: gradle wrapper)
gradle build
# jar lands in build/libs/
```

Requires Java 21. Run in dev with `gradle runClient`.

## Keybinds (rebindable)

| Key | Ability |
|---|---|
| `Y` | Transform (dragon ⇄ human) |
| `R` | Dragon's Breath — lingering harming clouds along your look ray |
| `G` | Dragon Fireball — the real `dragon_fireball` projectile |
| `V` | Wing Buffet — canon 3/5/7 damage, massive AoE knockback |
| `C` | Charge — dash dealing canon 6/10/15 contact damage |
| double-`Space` | Wing flight (creative-style for M1) |

## Palette

All colors are pixel-sampled from the vanilla 1.21.11 dragon textures (see DESIGN.md §2):
near-black scales `#0E0E0E`–`#1C1C1C`, darkest-of-all wing membrane `#0A0A0A`–`#0F0F0F`,
charcoal horns/claws `#474747`–`#626262`, light gray belly plates `#696969`–`#8A8A8A`
(mapped to pecs/abs/throat/tail underside on the anthro build), emissive eyes
`#9600BC` / `#CC00FA` / `#E079FA`.

**No vanilla textures are included in this repo or the built jar** — Mojang assets can't
be redistributed. The anthro skin (M2) will be an original texture painted with the
sampled palette; vanilla particles/sounds are referenced by resource location at runtime.
