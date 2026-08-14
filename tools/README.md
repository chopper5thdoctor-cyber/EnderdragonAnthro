# tools

## bbmodel_to_java.py

Converts `art/dragon_form.bbmodel` into the mod's model class and textures.
Run it after every Blockbench edit; never hand-edit `DragonFormModel.java`.

```bash
python3 tools/bbmodel_to_java.py art/dragon_form.bbmodel
```

It writes:

- `src/main/java/com/enderdragonanthro/client/model/DragonFormModel.java`
- `src/main/resources/assets/enderdragonanthro/textures/entity/dragon_form.png`
- `src/main/resources/assets/enderdragonanthro/textures/entity/dragon_form_eyes.png`
  (magenta pixels only, for the fullbright glow pass)

Conversions applied:

| Blockbench | Java |
|---|---|
| y up, ground at 0 | y down, `java_y = 96 - bb_y` |
| group origin | `PartPose` offset relative to the parent's pivot |
| cube from/to (absolute) | `addBox` offset relative to its own part's pivot |
| rotation (degrees) | radians; `x` and `z` negated, `y` kept (see below) |
| a cube with its OWN rotation | wrapped in a one-cube child part pivoted on that cube's origin |
| `mirror_uv` on a cube | `.mirror(true)` / `.mirror(false)` around it in the cube list |
| `canon_head_REFERENCE` | renamed `skull` — it is real geometry now |
| root `wing_left` / `wing_right` | re-parented under `body` |

### Rotation signs

Java model space is Blockbench's with Y flipped, so a part's rotation must
satisfy `R_java = M · R_bb · M` for `M = diag(1,-1,1)`. Conjugating each axis
by `M` gives `Rx(-x)`, `Ry(+y)`, `Rz(-z)` — X and Z flip, Y is unchanged.

### Mirrored UV

Two cubes mirrored in world space normally share one UV region, and box UV
always runs `u` along the cube's `+x`. Without honouring `mirror_uv`,
asymmetric art lands reversed on one side — the wing membranes are the
obvious casualty.

### Per-cube rotation

A `ModelPart`'s cubes are axis-aligned within it; Minecraft has no way to
rotate one cube inside a part. Any cube rotated in Blockbench therefore
becomes its own single-cube child part, pivoted on that cube's origin.
Two thirds of this model's cubes are rotated, so skipping this flattens it.

## verify_model.py

```bash
python3 tools/verify_model.py
```

Rebuilds every cube's world-space corners from the generated Java and from the
.bbmodel, then compares. **Run it after every conversion.** Both of the bugs
described above shipped because this check did not exist yet.

It checks **where the corners are, not what is painted on them** — a cube can
pass this and still render with its texture running backwards.

It runs two more checks after the geometry.

**Symbols:** every `DragonFormModel.X` the rest of the mod references is still
declared. `DragonFormModel.java` is **generated**, so anything hand-added
to it survives only until the next conversion: `GROUND_OFFSET` was added by
hand once and vanished the next time the wings changed, breaking the build.
**A new constant goes in the template in `bbmodel_to_java.py`, never in the
generated file.**

**Form guards:** every mixin on `Player`, `LivingEntity`, `Entity` or a renderer
must check `isDragon`/`isDragonForm` before doing anything. Those injections fire
for *everybody*, so an unguarded one silently changes the game for an untransformed
player — and human form being untouched vanilla is the one promise this mod makes.
A guard counts if it is in the mixin or one hop away in a mod class it imports (the
first-person hand checks inside its renderer); the file that *declares* the guard
does not count, or importing `DragonFormManager` would be enough to look safe.

## preview_wings.py

```bash
python3 tools/preview_wings.py       # writes art/wing_preview.png
```

Lays the wing membranes out flat and samples the texture the way
`ModelPart.Cube` does, so a reversed `u` is visible without launching the game.
Each wing must be one unbroken membrane and the two must be mirror images; two
panels meeting thin-end-to-thin-end mean the mirror flag is wrong on that side.

This is the check that finally explained why the membranes read `ng][wi`
instead of `[wing]` for two builds running. See `MIRROR_OVERRIDE` in
`bbmodel_to_java.py`.

## preview_eyeline.py

```bash
python3 tools/preview_eyeline.py           # writes art/eyeline_preview.png
python3 tools/preview_eyeline.py 126.5     # ...and what ratio puts the eye at rig y 126.5
```

Draws the rig front-on with the game's eye plane — the red line in F3+B — laid
across it, for both `trueProportions` settings.

Worth running after any change to the rig's height, because the two are not
connected. The eye sits at a fixed 1.62/1.8 of the **hitbox**, and the model is
sized separately, so `rigY = 1.62 * 16 / renderScale` — where the plane lands on
the model depends entirely on the render scale and not at all on where the eyes
are painted. Move the head and the camera does not follow it.

It reads `SKULL_HEIGHT` out of the generated model rather than measuring the rig
itself. Measuring took the overall top — the horn tips at 142 — where the game
builds `RENDER_SCALE` on the skull at 133, and put the line eight units wrong.
