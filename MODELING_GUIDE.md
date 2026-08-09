# Modeling Guide — custom anthro dragon model

How to build a model in Blockbench that drops into this mod and inherits
every vanilla player animation automatically.

## Tool

**Blockbench** — free, at <https://blockbench.net> (installable or runs in the
browser). The standard editor for Minecraft models.

## Project setup

- File → New → **Modded Entity** (this is the Java-edition entity format the
  mod uses — do NOT pick "Generic Model" or "Bedrock Entity").
- Texture size: **128×128** (256×256 also fine — say which when you hand it over).

## The rig contract (this is what makes animations work)

The mod animates the model by copying the vanilla player skeleton's pose into
six named bones every frame. Walking, sneaking, swimming, arm swings — all free —
**if and only if** the model follows these rules:

1. Exactly **six root groups**, named exactly:
   `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`
2. Each root group's **pivot** must sit at the vanilla joint (Blockbench coordinates):

   | Group | Pivot (x, y, z) |
   |---|---|
   | `head` | 0, 24, 0 |
   | `body` | 0, 24, 0 |
   | `right_arm` | -5, 22, 0 |
   | `left_arm` | 5, 22, 0 |
   | `right_leg` | -1.9, 12, 0 |
   | `left_leg` | 1.9, 12, 0 |

3. **Everything else goes inside those six.** Muzzle, jaw, horns → children of
   `head`. Wings, tail (chain the segments: tail1 → tail2 → tail3), spine
   spikes → children of `body`. Claws → children of the arm/leg groups.
   Child groups can have any pivot/rotation you like — they move with their parent.
4. **Cubes only** — no mesh/poly modeling (the Modded Entity format enforces this).
5. **Rotate groups, not loose cubes.** If a cube needs an angle, put it in its
   own child group and rotate the group.
6. Feet at y=0, standing height ~30 units. In-game giant size comes from the
   4.44× scale attribute, so model at vanilla-ish proportions and it will tower
   correctly. (Reference: the current code model's skull is 4 units = a 1:1
   canon dragon head at final scale.)

## Palette

Paint with the pixel-sampled canon palette (DESIGN.md §2): scales
`#0E0E0E`–`#1C1C1C`, wing membrane `#0A0A0A`–`#0F0F0F` (darkest), horns/claws
`#474747`–`#626262`, belly/chest plates `#696969`–`#8A8A8A`, eyes
`#CC00FA`/`#E079FA`. Keep the countershade ordering: membrane < scales <
horns < plates.

## Handing it over

1. Save the project — you get a **`.bbmodel`** file (it contains the geometry,
   UVs, and your painted texture, all in one).
2. On the GitHub repo page, switch to the working branch, then
   **Add file → Upload files**, drop the `.bbmodel` into an `art/` folder,
   and commit.
3. Say the word in chat. The `.bbmodel` gets converted into the mod's model
   code (`DragonFormModel`), the texture is extracted into assets, and the
   pose-copy rig drives it with every vanilla animation. Extra life —
   tail sway, wing flap on flight — can be layered on top procedurally after.

## Iterating

Upload a new version of the `.bbmodel` any time; conversion is repeatable.
Model and texture tweaks don't touch gameplay code.
