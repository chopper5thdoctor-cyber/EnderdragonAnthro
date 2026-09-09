**Pre-alpha.** For testing, not for playing. Things will change and saves made
with it are not promised to keep working.

## Installing

Drop the jar in `.minecraft/mods` alongside **Fabric API**. Both are needed.

| | |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.0 or newer |
| Fabric API | any 1.21.1 build — **hard dependency** |
| Java | 21 |

On a server it goes on both sides: the form, the court and the HUD all need
the client half present.

## What it is

You press a key and become an eight-block anthropomorphic Ender Dragon, with
a court of four named endermen who attend you. Everything a normal player can
do — inventory, crafting, doors, sleeping — still works.

Keys are rebindable in Controls, under EnderdragonAnthro. The defaults:

| Key | |
|---|---|
| `Y` | Transform |
| `R` | Breath |
| `G` | Fireball |
| `V` | Buffet — a wing sweep that throws things back |
| `C` | Charge |
| `B` | Intent: Passive → Alert → Hostile |
| `X` | Evade |
| `K` | Warp to a distant player |
| `J` | Home |
| `H` | Dragonsight |
| `Z` | Summon a shade |
| `M` | Court screen |
| `N` | Dragonfire (hold) |

Double-tap space in the air to glide.

**The Intent dial is the thing to find first.** On Passive nothing breaks that
you did not mean to break. Alert and Hostile turn a bare-handed left click
into a crater, and Hostile makes it detonate. If the world is coming apart,
that dial is why.

## Known, and not bugs

- **The form is free.** One keypress, no cost, no gating. That is deliberate
  for testing and is not the intended play, so it should not colour any
  balance feedback.
- **A dragon on Passive hits softer than her own shades.** Passive is her
  holding back; the court is not.

## Reporting

Screenshots help more than descriptions, especially for anything about where
something is drawn. If it is a crash, the log is in `.minecraft/logs/latest.log`.
