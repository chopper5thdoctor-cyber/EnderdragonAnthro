package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.client.EndermanHappyClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EndermanModel;
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
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.EnderMan;

/**
 * The ^^ a pleased enderman wears for a few seconds.
 *
 * Drawn as an extra emissive shell over the head rather than by swapping the
 * enderman's texture: no Mojang art is copied, the vanilla eyes layer beneath
 * is left alone, and a mapping change can only cost the expression rather than
 * the whole mob.
 */
public class EndermanHappyLayer extends RenderLayer<EnderMan, EndermanModel<EnderMan>> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(EnderdragonAnthro.id("enderman_happy"), "main");
    private static final ResourceLocation FACE =
            EnderdragonAnthro.id("textures/entity/enderman_happy.png");
    private static final ResourceLocation MASK =
            EnderdragonAnthro.id("textures/entity/enderman_happy_mask.png");

    private final ModelPart face;

    public EndermanHappyLayer(RenderLayerParent<EnderMan, EndermanModel<EnderMan>> parent,
                              EntityModelSet models) {
        super(parent);
        this.face = models.bakeLayer(LAYER).getChild("face");
    }

    /** The humanoid head, a whisker larger so it never z-fights the real one. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("face", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.06F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light,
                       EnderMan enderman, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!EndermanHappyClient.isHappy(enderman)) {
            return;
        }
        this.face.copyFrom(getParentModel().head);
        // Mask first, in ordinary cutout, so the enderman's own glowing eyes are
        // actually covered. RenderType.eyes is additive — the expression drawn
        // on its own could only ever sit on top of them, which read as blush
        // rather than as a face.
        this.face.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(MASK)),
                light, OverlayTexture.NO_OVERLAY);
        this.face.render(poseStack, buffers.getBuffer(RenderType.eyes(FACE)),
                15728640, OverlayTexture.NO_OVERLAY);
    }
}
