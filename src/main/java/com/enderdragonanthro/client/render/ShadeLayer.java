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
     * One sheet per slot.
     *
     * All four point at the same painting today — the court is four of the same
     * people until there is art that says otherwise, and the tint on their names
     * is what tells them apart. Giving one of them her own hide is a file and
     * one entry here, which is the whole reason this is an array.
     */
    private static final ResourceLocation[] SKINS = new ResourceLocation[4];

    static {
        ResourceLocation base = EnderdragonAnthro.id("textures/entity/shade.png");
        for (int slot = 0; slot < SKINS.length; slot++) {
            SKINS[slot] = base;
        }
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
    private static final ResourceLocation RESTING_GLOW =
            EnderdragonAnthro.id("textures/entity/shade_glow.png");
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
            this.model.render(poseStack, buffers.getBuffer(RenderType.eyes(RESTING_GLOW)),
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
