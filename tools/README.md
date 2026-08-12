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
