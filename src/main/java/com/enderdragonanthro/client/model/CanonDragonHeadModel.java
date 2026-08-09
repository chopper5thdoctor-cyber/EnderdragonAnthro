package com.enderdragonanthro.client.model;

import com.enderdragonanthro.EnderdragonAnthro;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The canon Ender Dragon head, cube-for-cube and UV-for-UV from the
 * vanilla dragon model (256x256 texture space): skull 16^3 @(112,30),
 * upper lip 12x5x16 @(176,44), jaw 12x4x16 @(176,65) hinged at
 * (0,4,-8), nostrils 2x2x4 @(112,0), horn scales 2x4x6 @(0,0).
 *
 * It is rendered against the vanilla dragon.png / dragon_eyes.png
 * resource locations at runtime — the player's own game supplies the
 * canon pixels, so nothing is redistributed. DragonFormLayer scales
 * this by 1/4 onto the anthro's neck (16 canon units = 1 block = 4
 * anthro units).
 */
public class CanonDragonHeadModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(EnderdragonAnthro.id("canon_dragon_head"), "main");

    private final ModelPart root;

    public CanonDragonHeadModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(176, 44).addBox(-6.0F, -1.0F, -24.0F, 12.0F, 5.0F, 16.0F)   // upper lip
                        .texOffs(112, 30).addBox(-8.0F, -8.0F, -10.0F, 16.0F, 16.0F, 16.0F)  // skull
                        .mirror(false)
                        .texOffs(0, 0).addBox(-5.0F, -12.0F, -4.0F, 2.0F, 4.0F, 6.0F)        // horn scale R
                        .texOffs(112, 0).addBox(-5.0F, -3.0F, -22.0F, 2.0F, 2.0F, 4.0F)      // nostril R
                        .mirror(true)
                        .texOffs(0, 0).addBox(3.0F, -12.0F, -4.0F, 2.0F, 4.0F, 6.0F)         // horn scale L
                        .texOffs(112, 0).addBox(3.0F, -3.0F, -22.0F, 2.0F, 2.0F, 4.0F),      // nostril L
                PartPose.ZERO);
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(176, 65).addBox(-6.0F, 0.0F, -16.0F, 12.0F, 4.0F, 16.0F),
                PartPose.offset(0.0F, 4.0F, -8.0F));

        return LayerDefinition.create(mesh, 256, 256);
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int light) {
        this.root.render(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
    }
}
