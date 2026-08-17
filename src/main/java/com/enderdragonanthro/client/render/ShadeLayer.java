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
    /** The spirals alone, burned on top of the dizzy shell. */
    private static final ResourceLocation DIZZY_GLOW =
            EnderdragonAnthro.id("textures/entity/shade_dizzy_glow.png");

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
        if (mood != null) {
            // She narrows her eyes or closes them rather than wearing the
            // enderman's ^^ and its unhinged jaw -- one opaque pass, because
            // hers are paint on a hide and do not glow, so the plate that
            // covers the resting eyes and the expression drawn in their place
            // are the same texels. The shell is a whole head: her eyes overhang
            // the front face, and half an expression is worse than none.
            this.face.copyFrom(this.model.head());
            this.face.render(poseStack,
                    buffers.getBuffer(RenderType.entityCutoutNoCull(mood)),
                    light, OverlayTexture.NO_OVERLAY);
            if (mood == DIZZY) {
                // Spirals are a state she is in rather than a face she is
                // pulling, so they burn instead of sitting there. Additive over
                // the opaque pass, which is why the glow sheet carries only the
                // paint -- anything the shell left as hide stays hide.
                this.face.render(poseStack, buffers.getBuffer(RenderType.eyes(DIZZY_GLOW)),
                        15728640, OverlayTexture.NO_OVERLAY);
            }
        }
        poseStack.popPose();
    }
}
