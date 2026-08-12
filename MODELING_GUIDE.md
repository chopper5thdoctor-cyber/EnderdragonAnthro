# Modeling Guide — custom anthro dragon model

How to build a model in Blockbench that drops into this mod and inherits
every vanilla player animation automatically.

## Tool

**Blockbench** — free, at <https://blockbench.net> (installable or runs in the
browser). The standard editor for Minecraft models.

## Project setup

Easiest start: open **`art/dragon_form.bbmodel`** from this repo — it is the
current in-game model, already rigged correctly, with the texture embedded.
Sculpt on top of it rather than starting from scratch.

Starting fresh instead:

- File → New → **Modded Entity** (this is the Java-edition entity format the
  mod uses — do NOT pick "Generic Model" or "Bedrock Entity").
- Texture size: **512×512**.

## Scale: the model is authored at 4×

Box UV pins exactly one texel per model unit, so the model is built at 4× and
rendered at ¼ — that is what gives the body the same texel density as the canon
head. Practical consequences:

- The model stands **96 units** tall (feet y=0, neck joint y=96), not 24.
- Vanilla player pivots are multiplied by 4 (see the table below).
- 16 model units = 1 rendered block-quarter; the canon dragon skull (16³) comes
  out as a true 1:1 dragon head.

## The rig contract (this is what makes animations work)

The mod animates the model by copying the vanilla player skeleton's pose into
six named bones every frame. Walking, sneaking, swimming, arm swings — all free —
**if and only if** the model follows these rules:

1. Exactly **six root groups**, named exactly:
   `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`
2. Each root group's **pivot** must sit at the vanilla joint (Blockbench coordinates):

   | Group | Pivot (x, y, z) |
   |---|---|
   | `head` | 0, 96, 0 |
   | `body` | 0, 96, 0 |
   | `right_arm` | -20, 88, 0 |
   | `left_arm` | 20, 88, 0 |
   | `right_leg` | -7.6, 48, 0 |
   | `left_leg` | 7.6, 48, 0 |

3. **Everything else goes inside those six.** Muzzle, jaw, horns → children of
   `head`. Wings, tail (chain the segments: tail1 → tail2 → tail3), spine
   spikes → children of `body`. Claws → children of the arm/leg groups.
   Child groups can have any pivot/rotation you like — they move with their parent.
4. **Cubes only** — no mesh/poly modeling (the Modded Entity format enforces this).
5. **Rotate groups, not loose cubes.** If a cube needs an angle, put it in its
   own child group and rotate the group.
6. Feet at y=0, neck joint at y=96. In-game giant size comes from the 4.44×
   scale attribute — model at these proportions and it will tower correctly.
7. **`canon_head_REFERENCE` is not editable geometry.** The skull, upper lip
   and jaw are the real Ender Dragon head, drawn at runtime from the player's
   own vanilla `dragon.png` (plus the emissive `dragon_eyes.png` pass). Those
   cubes exist in the project only so you can see the head while sculpting;
   they are ignored on the way back in. Keep the space around them clear.

## Mirrored pairs: the geometry already mirrors them

A cube's `u` runs from its box origin to origin+size. The wings are authored as
a genuine pair — `wing_left` spans x 16..72, `wing_right` spans x −72..−16 — so
their low-u ends point in **opposite** directions all on their own. That already
*is* the mirroring a symmetric pair needs.

Blockbench still flags the −x twin with `mirror_uv`, and honouring that in
Minecraft mirrors the cube a **second** time. The membrane then reads `ng][wi`
instead of `[wing]`: the inner and tip panels swap end-for-end and their thin
edges meet in a seam down the middle of the wing.

Which twin the shared art was painted for is a fact about the art, not the
geometry, so there is nothing to derive it from. `MIRROR_OVERRIDE` in
`bbmodel_to_java.py` records it: the art is painted low-u = **outboard**, which
`wing_right` gets for free and `wing_left` needs a flip to reach.

**`verify_model.py` will not catch this** — it checks where corners are, not
what is painted on them. Use:

```bash
python3 tools/preview_wings.py       # writes art/wing_preview.png
```

Each wing must be one unbroken membrane — central spar, struts fanning out,
scalloped trailing edge running continuously from body to tip — and the two must
be mirror images. Two panels meeting thin-end-to-thin-end means a reversed `u`.

## Flat cubes: paint one face, leave the other empty

The wing membranes are zero-height boxes (`56 × 0 × 56`). A box of zero height
still generates **both** its up and down quads, and with zero height between
them they land on **exactly the same plane** — two full-size coplanar faces at
identical depth.

Their UV regions are the two 56×56 blocks side by side at the box's offset:

```
offset (u,v)   ->   DOWN = (u+56, v)   UP = (u+112, v)
```

Only one of them may carry art. Paint both and the two quads z-fight, and
because up and down traverse the plane in opposite senses, the second copy
also lands mis-oriented — you get a flickering, back-to-front ghost over a
wing that was fine. `entityCutoutNoCull` draws the painted quad from **both**
sides anyway, so the empty one costs you nothing.

If a flat cube ever looks wrong from underneath, the answer is never "fill the
other face."

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
