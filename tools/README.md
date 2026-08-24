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
| the texture the **faces** reference | `dragon_form.png` (see below) |

### Which sheet ships

A `.bbmodel` can carry several textures, and a face's `texture` field is an
index into that array — so the sheet the faces are mapped to is the file's own
answer to which one the model is painted with. The converter reads that one.

It used to read `textures[0]`. A rig arrived carrying two sheets, one of them a
stale copy still holding blue block-out guides on the fist UV at (276, 161) and
the other the repainted one the faces referenced; `textures[0]` was the stale
one, and 235 pixels of pure blue shipped onto the dragon's hands. Every
geometric check passed, because the geometry was right and only the paint came
from a different file. `verify_model.py` now compares the shipped PNG to the
faces' sheet pixel for pixel.

Two notes for painting in Blockbench, both of which contributed:

- **Do not leave `layers_enabled` set.** Blockbench then draws the layer stack
  and treats `source` as a preview it may ignore, so a repaint can fail to
  reach the file the converter reads.
- **Do not compare sheets with `ImageChops.difference(a, b).getbbox()` on
  RGBA.** `getbbox` reads the alpha channel there, so two sheets whose alpha
  agrees come back "identical" however far their colours differ. Compare
  `tobytes()`, or convert to RGB first.

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

## seed_flap.py

```bash
python3 tools/seed_flap.py
```

Writes the `wing_flap` animation into `art/dragon_form.bbmodel`, so the
wingbeat can be scrubbed in Blockbench's Animate tab instead of edited as
trigonometry.

**Run this once, or never again.** It seeds the animation from the curves the
beat was born with; after that the `.bbmodel` is the source of truth and
re-running it throws away every tweak.

The beat used to be three lines in `DragonFormModel.flap` — the same curves
`EnderDragonRenderer` drives the canon dragon's wing and wing tip with, the tip
lagging two radians so the membrane snaps rather than swinging as one board.
That is a fine way to write an animation and a miserable way to tweak one:
every adjustment was a number in a Java file, compiled, launched and squinted
at.

Now: open the rig, drag a keyframe, re-run `bbmodel_to_java.py`. The animation's
**length** is the beat's duration too — it comes out as `BEAT_TICKS`, which
`DragonWings` reads, so shortening the stroke on the timeline shortens it in
game.

### The two clips the converter reads

| animation in the rig | baked to | driven by |
|---|---|---|
| `wing_flap` | `BEAT` / `BEAT_TICKS`, `flap(phase)` | one-shot per Boost, `DragonWings` |
| `tailwag in flight` | `WAG` / `WAG_TICKS`, `wag(phase, weight)` | ambient while gliding, `DragonTail` |

Matched **by name**, so swapping a clip is a rename in Blockbench and a re-run
of the converter — nothing in Java names an animation. Anything else in the rig
is ignored, which is how `wing_flap_original` survives as reference without
being built.

Both are baked into tables of 32 samples per cycle, already in Java's space and
units, six rows per bone — rotation `x/y/z` then position `x/y/z`. Baked rather
than emitted as keyframes so the interpolation is settled once, at conversion
time; the generated Java cannot drift from what Blockbench drew. Re-baking
reproduces the source curves to within **0.11°**.

Either clip missing from the rig is fine: `flap()` falls back to the
trigonometry it was born with, and `wag()` becomes a no-op.

### The values

- They are **deltas** on the pose the artist posed, in both Blockbench and
  Minecraft — Blockbench's animator offsets a bone from its rest pose, and
  `AnimationChannel.Targets.ROTATION` adds to the part's current rotation. The
  wings are folded at rest; a wing that flapped to zero would jump the moment
  the beat ended.
- **rotation** takes the same axis conversion as the geometry, backwards:
  `bb_x = -java_x`, `bb_y = +java_y`, `bb_z = -java_z`, degrees to radians.
- **position** is a translation, so only Y flips — `bb_y = -java_y`, X and Z
  unchanged, and the units are model units either way.
- Keyframe values are molang expressions in Blockbench, and only plain numbers
  can be baked. An expression, or a channel that is neither rotation nor
  position, fails the conversion loudly rather than being dropped.

### Why `wag()` takes a weight and `flap()` does not

**A looping clip cannot ease itself in.** The wag's keyframes carry the flight
carriage as well as the wag — about 80° of cumulative tail curl on top of the
rest pose, which is right, because the body pitches prone in a glide and a tail
curled "down" in model space streams *backwards* in the world.

But that puts frame zero of the clip **5.5 blocks** from where the tail is
standing. The loop is seamless — last frame to first frame is exact to four
decimal places — and that is a different seam from the one that matters. The
jump is between *not playing* and *playing*, and no amount of care inside a loop
can smooth it, because a loop has no frame that is at rest.

So `DragonTail` ramps a weight 0→1 over 6 ticks when the glide starts and back
down when it ends, and `apply()` scales the deltas by it. The clip stays a clean
loop; the transition is the caller's business. `flap()` needs none of this
because the beat is a one-shot whose first and last frames *are* rest.

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

**Mirrored pairs:** every cube named `x_left` must mirror exactly once against
its `x_right` twin — not twice, and not never. A cube's `u` runs from its box
origin, so a symmetric pair already runs its texture in opposite directions with
no flag at all; setting `mirror_uv` on one of them is right and setting it on
both, or neither, is not.

This shipped twice before the check existed. The wings read `ng][wi` for two
builds. The left arm ran two of its four pieces backwards for longer, which is
what finally made it look broken from every angle including first person — and
when the check was added it immediately found a third case nobody had noticed,
in the legs. Overrides live in `MIRROR_OVERRIDE` in `bbmodel_to_java.py`.

**Mirrored poses:** a pair must be *posed* as mirror images too, not merely
painted as them. Mirroring across x negates the `y` and `z` rotations and leaves
`x` alone, so `delt_right` at `(0, 0, 27.5)` demands `delt_left` at
`(0, 0, -27.5)`.

`forearm_left` carried `(-20, 0, 0)` against a twin with no rotation, so the
converter correctly gave it its own rotated bone and one forearm sat bent
forward — including in first person, which clones that arm. The corner check
could not see it: it compares the generated Java against the `.bbmodel`, and
those two agreed. They were only ever both wrong together.

**Form guards:** every mixin on `Player`, `LivingEntity`, `Entity` or a renderer
must check `isDragon`/`isDragonForm` before doing anything. Those injections fire
for *everybody*, so an unguarded one silently changes the game for an untransformed
player — and human form being untouched vanilla is the one promise this mod makes.
A guard counts if it is in the mixin or one hop away in a mod class it imports (the
first-person hand checks inside its renderer); the file that *declares* the guard
does not count, or importing `DragonFormManager` would be enough to look safe.

## syntax_check.py

```bash
python3 tools/syntax_check.py
```

Catches the two kinds of build break that can be found **without Minecraft on
disk**. Fabric's maven is unreachable from the machine this mod is written on,
so `javac` cannot resolve one vanilla class and buries everything under
thousands of `cannot find symbol`.

The obvious workaround — drop every line with that phrase — is what let six
stale field references ship at once. `shade.awayTicks` reads exactly like
`particle.xd`, and only one of them is a mistake: `PurpleHeartParticle` extends
a class we cannot see, so none of its inherited members resolve either. Nothing
in javac's output separates a real typo from that cascade.

So it checks two things it can be certain of instead.

**Syntax.** Parse errors — a missing brace, a dropped semicolon — happen before
name resolution, so they are sound with an empty classpath and are reported
as javac prints them.

**Our own members.** For every type this repository declares whose ancestry is
*entirely ours*, so the whole member surface is known, every `x.member` where
`x` was declared with that type is checked against it. That is exactly the
shape of the six that got through. A type that extends anything vanilla is
skipped rather than guessed at.

It cannot see a wrong argument type, a bad vanilla method name, or a mixin that
does not apply. Those still need a real build.

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

## pack_shade_rig.py

```bash
python3 tools/pack_shade_rig.py art/shade_source.bbmodel   # -> art/shade_base.bbmodel
```

Takes the artist's hand-built shade rig, leaves every cube's geometry exactly
where it is, and rewrites only the UV: one island per cube, packed so nothing
overlaps and nothing runs off the sheet, at twice the texel density.

Hand-placed box UV drifts. The rig this was written for arrived with four cubes
at **negative** offsets — off the top-left corner of the sheet — and several
islands sitting on top of each other. No amount of painting fixes that.

**On resolution.** One texel per model unit is welded into box UV, and
Blockbench enforces it — raise the sheet on its own and it resizes the model to
match. That is not a bug and there is no setting for it.

The only way to buy detail is to **author the rig oversize and divide it back
down at render time**. `dragon_form.bbmodel` already does exactly this: 142
units on a 512 sheet with `AUTHORED_SCALE = 4`, undone by `TRUE_SCALE = 1/4`.
The shades follow it at `AUTHOR_SCALE = 2`, so a 47.8-unit rig is drawn at 95.6
on a 128 sheet and the renderer applies ½.

Geometry is scaled *after* packing, so the UV stays laid out in source units —
one texel per original unit, therefore `AUTHOR_SCALE` texels per final unit.

An earlier cut wrote UV rectangles at twice the cube size instead, meaning to
lean on `CubeListBuilder`'s `texScale` overload. That is real in Java and
useless in Blockbench, which will not author against it — the artist got a rig
that fought them every time they touched the resolution.

Mirrored pairs keep sharing one island — that is what the artist meant by
giving them one offset and setting `mirror_uv`.

Face rectangles are written in Blockbench's own conventions, orientation
included: the two square slots are stored flipped, and a mirrored cube swaps
east with west and reverses x on every face. That was read back off the
artist's file rather than guessed, and `face_rects` reproduces all sixty
rectangles of it exactly.

## make_shade_texture.py

```bash
python3 tools/make_shade_texture.py    # writes art/shade_*.png
```

Skins for the four, read straight out of `art/shade_base.bbmodel` rather than
from a copy of the layout — **original art, not a vanilla sheet**, which cannot
ship here. Re-rig, re-pack, re-run: whatever pieces the artist added get
painted, at whatever offsets the packer gave them.

Near-black in the DESIGN.md palette with a little noise so it does not read as
flat plastic, lit on the front and top faces and dark on the back and
underside, and one accent per shade matching the colour their chat is spoken
in: a stripe down the spine and a band across the crown. Each gets an `_eyes`
sheet for the emissive pass.

`shade_guide.png` is the same layout with every island outlined in a different
colour and every face boundary drawn. Open it beside the rig in Blockbench and
it is obvious which rectangle is which limb — the one thing a blank sheet
cannot tell you.

## mottle_skin.py

```bash
python3 tools/mottle_skin.py art/shade_vaelle.png
python3 tools/mottle_skin.py in.png -o out.png --lift 24 --seed 7
```

Breaks up flat black on a hand-painted skin. `#000000` across a whole limb reads
as a hole rather than a surface — there is nothing for light to catch, so all
you see is the silhouette. A vanilla enderman is not flat black either: the hide
is near-black with a fine scatter of slightly lifted pixels, and that is what
keeps it looking like a creature in a dark room instead of a cutout.

**Only black pixels are touched.** Anything painted keeps its exact colour, and
so does alpha, so transparent regions stay transparent and the sheet still packs
identically. `--threshold` widens what counts as black; `--lift` sets how bright
a mottled pixel may become.

Three layers, because any one of them alone looks wrong:

| | |
|---|---|
| **patches** | coarse value noise, bilinear-sampled — broad lighter and darker areas rather than uniform static |
| **grain** | per-pixel speckle, fine and low-amplitude; this is the part that actually reads as texture at Minecraft's resolution |
| **sheen** | a gentle vertical gradient, lighter at the top, measured per column-run so a leg low on the sheet is not uniformly darker than an arm high on it |

The lift carries slightly more blue than red, so the hide reads as the End's
violet-black rather than as grey.

### `--style vanilla`

Measured off the real enderman sheet rather than invented, and it is far
simpler than the soft style: the hide is **exactly two colours**, `#000000` and
`#161616`, with no gradient between them.

| | vanilla | `--style soft` |
|---|---|---|
| tones | 2 | continuous, 19 levels |
| lightest | lum 22.0 | lum 16.9 |
| split | 62.4% light / 37.1% black | centred on 9.3 |

So the body is *grey with black in it*, not black with grey in it — the lighter
tone is the majority. The two are only mildly clumped, and slightly
**directional**: 65% of vertical neighbours share a tone against 59% of
horizontal ones.

Reproducing that took fitting rather than guessing. Smooth noise on its own is
far too cohesive (84% same-tone, runs of 5.3 texels); half of it has to be
per-texel randomness. Sweeping cell size and blend against all four
measurements lands `2x4` cells at `0.50` blend:

```
vanilla enderman         light 62.7%  h 59%  v 65%  run 2.17
mine, --style vanilla    light 62.4%  h 59%  v 61%  run 2.32
```

Deterministic for a given file and `--seed`, so re-running after a repaint does
not reshuffle what you kept.
