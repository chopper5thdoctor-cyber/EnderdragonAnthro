package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.ability.ShadeIdentity;
import com.enderdragonanthro.client.model.ShadeModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
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

    private final ShadeModel model;

    public ShadeLayer(RenderLayerParent<EnderMan, EndermanModel<EnderMan>> parent,
                      EntityModelSet models) {
        super(parent);
        this.model = new ShadeModel(models.bakeLayer(ShadeModel.LAYER));
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
        poseStack.popPose();
    }
}
