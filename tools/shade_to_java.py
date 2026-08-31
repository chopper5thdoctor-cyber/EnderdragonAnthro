#!/usr/bin/env python3
"""Convert art/shade_base.bbmodel into the court's model class.

    python3 tools/shade_to_java.py

Same conventions as bbmodel_to_java.py -- it imports that file's coordinate
helpers rather than restating them -- but a much smaller job, because the shade
rig is six bones and nine cubes rather than seventy-five.

The six bones are deliberately the ones HumanoidModel has: EndermanModel
extends HumanoidModel, so a shade can ride the vanilla enderman's animation the
same way the dragon rides the player's. Copy the six rotations across each
frame and every walk cycle, attack swing and carry pose comes free.

Never hand-edit the generated file. A constant added there survives exactly
until the next conversion; put it in TEMPLATE below.
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bbmodel_to_java import jbox, jpivot, jrot, jy, bake, CLIP_SAMPLES   # noqa: E402
from pack_shade_rig import AUTHOR_SCALE              # noqa: E402

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RIG = os.path.join(HERE, "art/shade_base.bbmodel")
OUT = os.path.join(HERE,
                   "src/main/java/com/enderdragonanthro/client/model/ShadeModel.java")

#: The bones copyPose writes vanilla's pose onto. These six and no others --
#: they are named for HumanoidModel's, which is the whole trick.
POSED = ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")

#: Bones a clip may key, in the order the baked tables are laid out. Appended
#: rather than inserted: a clip's row is index * 6, so adding to the end leaves
#: every already-baked row where it was.
BONES = ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg",
         "right_forearm", "left_forearm", "right_shin", "left_shin")


def discover(bb):
    """Every named group in the rig, in outliner order.

    The converter used to emit only the bones it had been told about and drop
    everything else -- silently, which is this project's oldest and worst
    failure mode. An artist who groups her skirt under `SkirtFlapA` or moves the
    bosom into a `Bosom` group loses those cubes entirely: no error, no warning,
    the model simply comes out of the converter smaller than it went in, and the
    only symptom is a shade with no chest.

    So the rig decides what the bones are. BONES stays as the clip table's
    layout and POSED as the six vanilla drives; anything else the artist adds is
    a bone too, and it renders.
    """
    groups = {g["uuid"]: g for g in bb.get("groups", [])}
    found, unnamed = [], 0

    def walk(nodes):
        nonlocal unnamed
        for n in nodes:
            if isinstance(n, str):
                continue
            name = groups.get(n["uuid"], {}).get("name")
            if not name:
                unnamed += 1
                continue
            found.append(name)
            walk(n.get("children", []))

    walk(bb["outliner"])
    if unnamed:
        sys.exit(f"ERROR: {unnamed} group(s) in the rig have no name. A bone is "
                 "addressed by name -- by copyPose, by every animation, and by "
                 "ShadeModel's own fields -- so an unnamed one cannot be emitted. "
                 "Name them in Blockbench and convert again.")
    duplicates = {n for n in found if found.count(n) > 1}
    if duplicates:
        sys.exit(f"ERROR: more than one group is called {sorted(duplicates)}. "
                 "Bone names become Java fields and have to be unique.")
    return found


def field(bone):
    """right_forearm -> rightForearm, SkirtFlapA -> skirtFlapA."""
    head, *rest = bone.split("_")
    name = head[:1].lower() + head[1:] + "".join(w[:1].upper() + w[1:] for w in rest)
    # `root` is taken by the field every bone hangs from.
    return name + "Bone" if name == "root" else name

#: The clothing layer's name suffix. It hangs outside the body on purpose, so
#: anything measuring the body has to leave it out -- see FOOT_PLANE below.
COVERING = "Covering"

#: The clips the rig may carry, and the field each is baked into. A clip the
#: rig does not have comes out as an empty table and plays as nothing, which is
#: what should happen to an animation nobody has drawn yet.
CLIPS = (("idle", "IDLE"), ("walk", "WALK"), ("carry", "CARRY"),
         ("angry", "ANGRY"), ("pet", "PET"))


def clips(bb, bones):
    """Every animation in the rig, baked to sample tables and Java source.

    Baked against the bones the RIG has, not a list written here. Against a
    fixed list, an artist who adds a bone and animates it gets the geometry and
    silently loses the motion -- the clip bakes, the table has no row for it,
    and nothing says so.
    """
    out, ticks = [], []
    for clip, field in CLIPS:
        baked = bake(bb, clip, bones, require_all=False)
        rows = len(bones) * 6
        if baked is None:
            ticks.append(f"    public static final int {field}_TICKS = 0;")
            out.append(f"    private static final float[][] {field} = new float[{rows}][];")
            continue
        length, tracks = baked
        ticks.append(f"    public static final int {field}_TICKS = {length};")
        out.append(f"    private static final float[][] {field} = new float[{rows}][];")
        out.append("")
        out.append("    static {")
        for (bone, channel, axis), samples in sorted(tracks.items()):
            row = bones.index(bone) * 6 + (0 if channel == "rotation" else 3) + "xyz".index(axis)
            body = ", ".join(f"{v:.5f}F" for v in samples)
            out.append(f"        {field}[{row}] = new float[] {{{body}}};"
                       f"   // {bone}.{channel}.{axis}")
        out.append("    }")
        out.append("")
    return "\n".join(ticks) + "\n\n" + "\n".join(out)


TEMPLATE = '''package com.enderdragonanthro.client.model;

import com.enderdragonanthro.EnderdragonAnthro;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * GENERATED by tools/shade_to_java.py from art/shade_base.bbmodel.
 * Do not hand-edit: the next conversion overwrites it.
 *
 * One of the court, drawn over a vanilla enderman. The bones are named for
 * HumanoidModel's, which EndermanModel extends, so copyPose can hand this rig
 * the vanilla animation and every walk cycle and carry pose comes free.
 */
public class ShadeModel {{
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(EnderdragonAnthro.id("shade"), "main");

    /** The rig is drawn {author}x oversize to buy texel density; undo it here. */
    public static final float AUTHOR_SCALE = {author}.0F;
    public static final float TRUE_SCALE = 1.0F / AUTHOR_SCALE;
    /** Java y of the foot plane, before that scale. */
    public static final float FOOT_PLANE = {foot}F;

    private final ModelPart root;
{fields}
    public ShadeModel(ModelPart root) {{
        this.root = root;
{lookups}    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

{parts}
        return LayerDefinition.create(mesh, {tw}, {th});
    }}

    /**
     * Take the vanilla enderman's pose.
     *
     * Rotations only. Positions are this rig's own, because a shade's shoulders
     * and hips are nowhere near an enderman's -- copying those outright tears
     * the body apart, which is the same lesson the dragon's copyPose records.
     */
    public void copyPose(HumanoidModel<? extends LivingEntity> from) {{
        rot(this.head, from.head);
        rot(this.body, from.body);
        rot(this.rightArm, from.rightArm);
        rot(this.leftArm, from.leftArm);
        rot(this.rightLeg, from.rightLeg);
        rot(this.leftLeg, from.leftLeg);
        // Anything else the artist added hangs off one of these six and rides
        // along; it is deliberately not driven from vanilla, which has no
        // opinion about a skirt.
    }}

    /**
     * The head bone, already posed, for anything that rides on her face.
     *
     * Its pivot is the artist's and moves whenever the rig does -- the head
     * dropped a notch once already -- so a shell copying this part follows it,
     * where one built on typed-in numbers quietly drifts off her face.
     */
    public ModelPart head() {{
        return this.head;
    }}

    private static void rot(ModelPart target, ModelPart source) {{
        target.xRot = source.xRot;
        target.yRot = source.yRot;
        target.zRot = source.zRot;
    }}

    // ------------------------------------------------------------- animation

    /** Samples each baked clip holds across one cycle. */
    private static final int CLIP_SAMPLES = {samples};

{clips}
    /** The bones a clip can move, in the order the tables are laid out. */
    private ModelPart[] bones() {{
        return new ModelPart[] {{{bonelist}}};
    }}

    /**
     * Play one clip over whatever copyPose left behind.
     *
     * Blended from the current rotation rather than written over it, so a
     * weight below one is a crossfade between the enderman's pose and the
     * artist's -- which is what lets a shade ease into a stride instead of
     * snapping into one. At weight 0 nothing is touched at all.
     *
     * A channel the clip does not key is LEFT ALONE rather than driven to rest.
     * That is the difference between "the artist did not animate the head" and
     * "the artist wants the head at zero", and getting it the other way round
     * would throw away vanilla's head tracking the moment anyone drew a walk
     * cycle. Every bone in this rig rests at zero, so a keyed value is both a
     * delta and an absolute angle and the animator shows exactly what plays.
     */
    public void play(float[][] clip, float phase, float weight) {{
        if (weight <= 0.0F || clip.length == 0) {{
            return;
        }}
        float at = Mth.positiveModulo(phase, 1.0F) * CLIP_SAMPLES;
        int lo = (int) at;
        int hi = (lo + 1) % CLIP_SAMPLES;
        float f = at - lo;
        ModelPart[] bones = bones();
        for (int b = 0; b < bones.length; b++) {{
            ModelPart bone = bones[b];
            float[] x = clip[b * 6];
            float[] y = clip[b * 6 + 1];
            float[] z = clip[b * 6 + 2];
            if (x != null) {{
                bone.xRot = Mth.lerp(weight, bone.xRot, Mth.lerp(f, x[lo], x[hi]));
            }}
            if (y != null) {{
                bone.yRot = Mth.lerp(weight, bone.yRot, Mth.lerp(f, y[lo], y[hi]));
            }}
            if (z != null) {{
                bone.zRot = Mth.lerp(weight, bone.zRot, Mth.lerp(f, z[lo], z[hi]));
            }}
        }}
    }}

    /** The clips, by the name the rig calls them. Empty means nobody drew one. */
    public static float[][] clip(Clip which) {{
        return switch (which) {{
            case IDLE -> IDLE;
            case WALK -> WALK;
            case CARRY -> CARRY;
            case ANGRY -> ANGRY;
            case PET -> PET;
        }};
    }}

    /** How long each runs, in ticks. Zero means the rig carries no such clip. */
    public static int ticks(Clip which) {{
        return switch (which) {{
            case IDLE -> IDLE_TICKS;
            case WALK -> WALK_TICKS;
            case CARRY -> CARRY_TICKS;
            case ANGRY -> ANGRY_TICKS;
            case PET -> PET_TICKS;
        }};
    }}

    /** What a shade can be doing, as far as the rig is concerned. */
    public enum Clip {{
        IDLE, WALK, CARRY, ANGRY, PET
    }}

    /**
     * The rig puts its feet at FOOT_PLANE, which only lands on the ground at a
     * scale of exactly one over AUTHOR_SCALE. Any other scale lifts the whole
     * body, so the layer drops it back by this much before drawing.
     */
    public static float groundOffset(float scale) {{
        return 24.0F - FOOT_PLANE * scale;
    }}

    public void render(PoseStack poseStack, VertexConsumer buffer, int light) {{
        this.root.render(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
    }}
}}
'''


def main():
    with open(RIG) as f:
        bb = json.load(f)

    groups = {g["uuid"]: g for g in bb.get("groups", [])}
    els = {e["uuid"]: e for e in bb["elements"]}

    bones = discover(bb)
    FIELD = {b: field(b) for b in bones}
    lines = []
    lookups = []

    def emit(node, parent, parent_pivot):
        """One bone and everything under it.

        Recursive because the limbs are segmented now: a forearm is a bone
        inside a bone, and Java wants its offset relative to the elbow's parent
        rather than in model space, which is how Blockbench stores it.
        """
        g = groups.get(node["uuid"], {})
        name = g.get("name")
        if name not in bones:
            return
        piv = jpivot(g.get("origin", [0, 0, 0]))
        boxes, spun, nested = [], [], []
        for cid in node.get("children", []):
            if not isinstance(cid, str):
                nested.append(cid)                  # a bone, handled after
                continue
            e = els.get(cid)
            if not e:
                continue
            (x, y, z), (w, h, d) = jbox(e)
            tex = ".texOffs({}, {})".format(*[int(round(c)) for c in e["uv_offset"]]) \
                if "uv_offset" in e else ".texOffs(0, 0)"
            mirror = ".mirror(true)" if e.get("mirror_uv") else ""
            box = (f'{mirror}{tex}.addBox({x - piv[0]:.3f}F, {y - piv[1]:.3f}F, '
                   f'{z - piv[2]:.3f}F, {w:.3f}F, {h:.3f}F, {d:.3f}F)')
            rot = e.get("rotation") or [0, 0, 0]
            if any(abs(t) > 1e-9 for t in rot):
                # Minecraft cannot rotate one cube inside a part, so it becomes
                # its own child pivoted on the cube's own origin. This is also
                # what keeps the artist's lean alive: copyPose writes the six
                # named bones every frame, and anything one level down survives.
                cpiv = jpivot(e.get("origin", [0, 0, 0]))
                crot = jrot(rot)
                spun.append((e.get("name", "spun"), cpiv, crot, mirror, tex,
                             (x - cpiv[0], y - cpiv[1], z - cpiv[2]), (w, h, d)))
            else:
                boxes.append(box)

        cl = "CubeListBuilder.create()" + ("\n                        ".join([""] + boxes)
                                           if boxes else "")
        off = [piv[i] - parent_pivot[i] for i in range(3)]
        # A BONE may be rotated too, and this used to drop that on the floor --
        # every shade group was at zero rotation, so emitting only the offset
        # was silently correct for as long as nobody rotated one. The first
        # artist to tilt a group (the bow, at 30 degrees) got a bone that sat in
        # the right PLACE facing the wrong way, which verify_model caught as a
        # 3.4-unit corner error with the centres agreeing exactly -- the
        # signature of an orientation lost rather than a position miscomputed.
        # bbmodel_to_java.py has always done this; this file simply never had a
        # rotated group to do it for.
        grot = g.get("rotation") or [0, 0, 0]
        if any(abs(t) > 1e-9 for t in grot):
            jr = jrot(grot)
            pose = (f'PartPose.offsetAndRotation({off[0]:.3f}F, {off[1]:.3f}F, '
                    f'{off[2]:.3f}F, {jr[0]:.4f}F, {jr[1]:.4f}F, {jr[2]:.4f}F)')
        else:
            pose = f'PartPose.offset({off[0]:.3f}F, {off[1]:.3f}F, {off[2]:.3f}F)'
        lines.append(f'        PartDefinition {name} = {parent}.addOrReplaceChild("{name}", {cl},')
        lines.append(f'                {pose});')
        for i, (cname, cpiv, crot, mirror, tex, o, size) in enumerate(spun):
            child = f"{name}_{i}"
            lines.append(f'        {name}.addOrReplaceChild("{child}", CubeListBuilder.create()')
            lines.append(f'                        {mirror}{tex}.addBox({o[0]:.3f}F, {o[1]:.3f}F, '
                         f'{o[2]:.3f}F, {size[0]:.3f}F, {size[1]:.3f}F, {size[2]:.3f}F),')
            lines.append(f'                PartPose.offsetAndRotation({cpiv[0] - piv[0]:.3f}F, '
                         f'{cpiv[1] - piv[1]:.3f}F, {cpiv[2] - piv[2]:.3f}F, '
                         f'{crot[0]:.4f}F, {crot[1]:.4f}F, {crot[2]:.4f}F));')
        lines.append("")
        lookups.append((name, parent))
        for child in nested:
            emit(child, name, piv)

    for node in bb["outliner"]:
        emit(node, "root", (0.0, 0.0, 0.0))

    # The SKIN's lowest point, not the clothing's.
    #
    # FOOT_PLANE is what ShadeLayer stands her on, and a covering is inflated
    # half a unit past the cube it covers -- so counting the coverings would
    # measure the hem of her trousers and set her feet floating that far above
    # the floor. This is the same shape of mistake as the dragon's arm ruler,
    # which quietly started measuring a spun sub-part instead of the hand: a
    # ruler is only right while it is still measuring the thing it was aimed at,
    # and adding geometry is exactly when that stops being true.
    skin = [e for e in bb["elements"] if not e["name"].endswith(COVERING)]
    foot = max(jy(c) for e in skin for c in (e["from"][1], e["to"][1]))
    res = bb["resolution"]
    # Declared and looked up in the order they were emitted, so a child bone is
    # always fetched from a parent that already exists.
    fields = "".join(f"    private final ModelPart {FIELD[n]};\n" for n, _ in lookups)
    gets = "".join(
        f'        this.{FIELD[n]} = '
        + (f'root.getChild("{n}");\n' if p == "root"
           else f'this.{FIELD[p]}.getChild("{n}");\n')
        for n, p in lookups)
    # bones() must be the discovered list in the discovered order, because that
    # is exactly how clips() laid the tables out. One source for both, so a
    # curve cannot end up played on the wrong bone.
    bonelist = ", ".join("this." + FIELD[n] for n in bones)

    out = TEMPLATE.format(parts="\n".join(lines), tw=res["width"], th=res["height"],
                          author=int(AUTHOR_SCALE), foot=f"{foot:.3f}",
                          clips=clips(bb, bones), samples=CLIP_SAMPLES,
                          fields=fields, lookups=gets, bonelist=bonelist)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        f.write(out)
    print(f"wrote {os.path.relpath(OUT, HERE)}")
    print(f"  {len(bb['elements'])} cubes, {len(bones)} bones, "
          f"sheet {res['width']}x{res['height']}")
    print(f"  foot plane java y {foot:.2f}, drawn at 1/{int(AUTHOR_SCALE)}")


if __name__ == "__main__":
    main()
