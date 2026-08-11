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
| rotation (degrees) | radians, `x` and `y` negated, `z` kept |
| `canon_head_REFERENCE` | renamed `skull` — it is real geometry now |
| root `wing_left` / `wing_right` | re-parented under `body` |

The rotation mapping is the inverse of the exporter that produced the
bbmodel. It is the one thing here that cannot be verified without running the
game: if a rotated part (wings, tail, head spikes) appears mirrored, flip the
sign in `jrot()` and regenerate.
