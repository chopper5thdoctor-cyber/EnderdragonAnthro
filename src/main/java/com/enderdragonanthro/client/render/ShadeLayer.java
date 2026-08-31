package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.ability.ShadeIdentity;
import com.enderdragonanthro.client.EndermanHappyClient;
import com.enderdragonanthro.client.model.ShadeModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.EnderMan;

/**
 * Draws a shade over the enderman that is really standing there.
 *
 * The enderman underneath keeps doing all the work — pathing, teleporting, the
 * carried block, the angry shake — and its own body is hidden by
 * ShadeBodyHideMixin, so what is left is the mob's behaviour wearing this rig.
 * That is also why the shade takes the vanilla pose rather than animating
 * itself: EndermanModel extends HumanoidModel, so every walk cycle and carry
 * stance vanilla already computed transfers across for the price of six
 * rotations.
 *
 * The whole layer is inside the entity's own scale, so the SCALE attribute the
 * court is summoned with lifts the rig with it and a shade stands four blocks
 * tall without this file knowing anything about it.
 */
public class ShadeLayer extends RenderLayer<EnderMan, EndermanModel<EnderMan>> {
    /**
     * One sheet per slot, if there is one, and the shared one otherwise.
     *
     * The court used to be four of the same person wearing four name colours.
     * Giving one of them her own hide was "a file and one entry here", which is
     * a small enough job to be worth removing entirely: a slot wears
     * {@code shade_<name>.png} when that file is in the pack and
     * {@code shade.png} when it is not, so painting an outfit is painting a
     * file. Nothing to register, nothing to recompile.
     *
     * <p>Resolved on resource reload rather than per frame — a texture lookup
     * is cheap and doing it sixty times a second per shade is not — and
     * resolved rather than assumed, because a ResourceLocation naming a file
     * that is not there does not fall back to anything. It draws the black and
     * magenta checks, which would be a strange reward for not having painted
     * something yet.
     */
    private static final String[] SLOT_FILES = {"vaelle", "keshanne", "nyrelle", "orrinne"};
    private static final ResourceLocation SHARED =
            EnderdragonAnthro.id("textures/entity/shade.png");
    /**
     * The paint alone, off each face, to be burned on top of it.
     *
     * An enderman's eyes are the brightest thing in a dark room and the shades'
     * were not lit at all, which made them read as somebody in an enderman
     * costume rather than as one of them. Every face she can wear gets a glow
     * sheet now, her resting one included.
     *
     * They are extracted from the same paintings rather than drawn fresh, so
     * the lit part cannot drift out of register with the painted part — the
     * rule is simply "the violet marking, not the black hide". The dizzy sheet
     * was already hand-made to that rule and the extraction reproduces it
     * pixel for pixel, which is how the rule was checked.
     *
     * The two bright tiers in the paint, #E079FA and #CC00FA, are the canon
     * enderman emissive colours exactly — eyedropped from
     * entity/enderman/enderman_eyes.png, which is the whole of vanilla's
     * palette there: six lit pixels in two shades. The darker #450052 is the
     * artist's own, and additive it reads as the socket around the blaze.
     */
    private static final ResourceLocation SHARED_GLOW =
            EnderdragonAnthro.id("textures/entity/shade_glow.png");
    private static final ResourceLocation[] SKINS = new ResourceLocation[4];
    private static final ResourceLocation[] GLOWS = new ResourceLocation[4];

    static {
        java.util.Arrays.fill(SKINS, SHARED);
        java.util.Arrays.fill(GLOWS, SHARED_GLOW);
    }

    /** Look again for per-shade art. Called on every resource reload. */
    public static void resolveSkins(net.minecraft.server.packs.resources.ResourceManager packs) {
        for (int slot = 0; slot < SLOT_FILES.length; slot++) {
            SKINS[slot] = pick(packs, "shade_" + SLOT_FILES[slot] + ".png", SHARED);
            GLOWS[slot] = pick(packs, "shade_" + SLOT_FILES[slot] + "_glow.png", SHARED_GLOW);
        }
    }

    private static ResourceLocation pick(net.minecraft.server.packs.resources.ResourceManager packs,
                                         String file, ResourceLocation fallback) {
        ResourceLocation own = EnderdragonAnthro.id("textures/entity/" + file);
        return packs.getResource(own).isPresent() ? own : fallback;
    }

    /** Her expressions, at the resolution a 16-unit head samples. */
    public static final ModelLayerLocation FACE_LAYER =
            new ModelLayerLocation(EnderdragonAnthro.id("shade_face"), "main");
    private static final ResourceLocation PET =
            EnderdragonAnthro.id("textures/entity/shade_pet.png");
    private static final ResourceLocation ANGRY =
            EnderdragonAnthro.id("textures/entity/shade_angry.png");
    private static final ResourceLocation DIZZY =
            EnderdragonAnthro.id("textures/entity/shade_dizzy.png");

    private static final ResourceLocation PET_GLOW =
            EnderdragonAnthro.id("textures/entity/shade_pet_glow.png");
    private static final ResourceLocation ANGRY_GLOW =
            EnderdragonAnthro.id("textures/entity/shade_angry_glow.png");
    private static final ResourceLocation DIZZY_GLOW =
            EnderdragonAnthro.id("textures/entity/shade_dizzy_glow.png");
    /** What an emissive pass is drawn at; the eyes shader ignores it anyway. */
    private static final int BURNING = 15728640;

    private final ShadeModel model;
    private final ModelPart face;

    public ShadeLayer(RenderLayerParent<EnderMan, EndermanModel<EnderMan>> parent,
                      EntityModelSet models) {
        super(parent);
        this.model = new ShadeModel(models.bakeLayer(ShadeModel.LAYER));
        this.face = models.bakeLayer(FACE_LAYER).getChild("face");
    }

    /**
     * Which face she is wearing, or null for her resting one.
     *
     * Water first, because it is the one she has no say in: an enderman in
     * water or rain takes drown damage every tick, and being reeled by it
     * outranks both fighting and being fussed over. Anger next, because a
     * shade mid-fight is not blinking fondly at you however recently you made
     * a fuss of her.
     *
     * The water test is the same condition that does the damage, not a report
     * that damage happened -- which is why an ordinary hit cannot reach it, and
     * why it costs no packet at all. isInWaterOrRain is derived from the
     * world, so the client works it out for itself; isCreepy is synced entity
     * data, the flag the vanilla renderer already shakes her by; only the pet
     * face needs anything sent, and that payload predates all of this.
     */
    private static ResourceLocation mood(EnderMan enderman) {
        if (enderman.isInWaterOrRain()) {
            return DIZZY;
        }
        if (enderman.isCreepy()) {
            return ANGRY;
        }
        return EndermanHappyClient.isHappy(enderman) ? PET : null;
    }

    /**
     * Her own animation, over the enderman's.
     *
     * copyPose has just handed the rig vanilla's pose, which is still the right
     * thing to start from — it carries the head tracking, the attack swing and
     * everything else the mob is doing that nobody wants to redraw. The clips
     * in the rig then move whichever bones they key and leave the rest of it
     * alone, so an artist can replace the walk without losing the head.
     *
     * Clips that have not been drawn bake to an empty table and play as
     * nothing, so the shade falls back to exactly the borrowed pose it had
     * before any of this existed.
     *
     * <p>The clips are per shade. A slot with none of her own uses the court's
     * shared default, so the four can diverge one animation at a time instead
     * of all at once -- and until they do, three of them cost nothing, because
     * the generated tables are shared arrays rather than copies.
     *
     * <p>The two phases come from different clocks on purpose. A walk driven by
     * time slides its feet, because the ground goes past at whatever speed the
     * mob is moving and the stride does not; limbSwing IS that distance, so a
     * stride keyed against it plants. Everything else is ambient and belongs on
     * the world clock.
     */
    private void animate(int slot, EnderMan enderman, float limbSwing,
                         float limbSwingAmount, float ageInTicks) {
        // Standing still is not "not walking": limbSwingAmount eases in and out,
        // so the two crossfade rather than swapping at a threshold.
        float stride = Math.min(1.0F, limbSwingAmount);
        play(slot, ShadeModel.Clip.IDLE, ageInTicks, 1.0F - stride);
        // limbSwing is distance travelled, and a stride is two paces, so the
        // 0.6662 here is vanilla's own — matching it is what keeps a shade's
        // feet landing where the enderman underneath thinks they are.
        play(slot, ShadeModel.Clip.WALK, limbSwing * 0.6662F / (2.0F * (float) Math.PI)
                * ShadeModel.ticks(slot, ShadeModel.Clip.WALK), stride);

        // The states that take over the arms entirely, most specific last.
        if (enderman.getCarriedBlock() != null) {
            play(slot, ShadeModel.Clip.CARRY, ageInTicks, 1.0F);
        }
        if (enderman.isCreepy()) {
            play(slot, ShadeModel.Clip.ANGRY, ageInTicks, 1.0F);
        } else if (EndermanHappyClient.isHappy(enderman)) {
            play(slot, ShadeModel.Clip.PET, ageInTicks, 1.0F);
        }
    }

    /** One clip, at whatever phase its own length puts that tick at. */
    private void play(int slot, ShadeModel.Clip which, float ticks, float weight) {
        int length = ShadeModel.ticks(slot, which);
        if (length <= 0 || weight <= 0.0F) {
            return;                    // nobody has drawn this one, for her or at all
        }
        this.model.play(ShadeModel.clip(slot, which), ticks / length, weight);
    }

    /** The lit half of a face, paired with the face it belongs to. */
    private static ResourceLocation glowFor(ResourceLocation mood) {
        if (mood == ANGRY) {
            return ANGRY_GLOW;
        }
        return mood == DIZZY ? DIZZY_GLOW : PET_GLOW;
    }

    /**
     * Her head, a whisker larger, so the expression never z-fights it.
     *
     * The box has to match the rig's head cube, because copyFrom only carries
     * the pose across -- 0.12 authored units is the 0.06 the enderman's own ^^
     * uses, once TRUE_SCALE has halved it.
     */
    public static LayerDefinition createFaceLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("face", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-8.0F, -18.0F, -8.0F, 16.0F, 16.0F, 16.0F,
                                new CubeDeformation(0.12F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light,
                       EnderMan enderman, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        int slot = ShadeIdentity.slotOf(enderman);
        if (slot < 0) {
            return;
        }
        this.model.copyPose(getParentModel());
        animate(slot, enderman, limbSwing, limbSwingAmount, ageInTicks);

        poseStack.pushPose();
        // Lift first, then shrink: groundOffset is already measured in the
        // units the rig ends up drawn in, so it belongs outside the scale.
        poseStack.translate(0.0F, ShadeModel.groundOffset(ShadeModel.TRUE_SCALE) / 16.0F, 0.0F);
        poseStack.scale(ShadeModel.TRUE_SCALE, ShadeModel.TRUE_SCALE, ShadeModel.TRUE_SCALE);
        this.model.render(poseStack,
                buffers.getBuffer(RenderType.entityCutoutNoCull(SKINS[slot])), light);

        ResourceLocation mood = mood(enderman);
        if (mood == null) {
            // Her resting face is painted on the body sheet, so the glow for it
            // goes on the body too -- same model, same UVs, nothing to line up
            // by hand. Only the eye texels are opaque on that sheet, so only
            // they light, which is exactly how vanilla's own eyes layer works.
            this.model.render(poseStack, buffers.getBuffer(RenderType.eyes(GLOWS[slot])),
                    BURNING);
        } else {
            // She narrows her eyes or closes them rather than wearing the
            // enderman's ^^ and its unhinged jaw. One opaque pass to cover the
            // resting eyes -- the plate that hides them and the expression drawn
            // in their place are the same texels -- and one additive pass for
            // the paint on top. The shell is a whole head: her eyes overhang the
            // front face, and half an expression is worse than none.
            this.face.copyFrom(this.model.head());
            this.face.render(poseStack,
                    buffers.getBuffer(RenderType.entityCutoutNoCull(mood)),
                    light, OverlayTexture.NO_OVERLAY);
            this.face.render(poseStack, buffers.getBuffer(RenderType.eyes(glowFor(mood))),
                    BURNING, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }
}
