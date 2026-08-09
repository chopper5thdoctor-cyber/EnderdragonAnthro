package com.enderdragonanthro.client.model;

import com.enderdragonanthro.EnderdragonAnthro;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The anthro dragon form, M2 first pass. Built as a scale-suit that rides
 * the vanilla player skeleton: each top-level part copies its pose from
 * the PlayerModel every frame, so walking, sneaking, swimming, swinging —
 * every vanilla animation — drives the dragon body for free. Dragon
 * anatomy (muzzle, horns, folded wings, tail) hangs off head and body as
 * children. Palette and anatomy per DESIGN.md sections 2–3.
 */
public class DragonFormModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(EnderdragonAnthro.id("dragon_form"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public DragonFormModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.6F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("snout",
                CubeListBuilder.create().texOffs(64, 0)
                        .addBox(-2.0F, -5.0F, -9.0F, 4.0F, 3.0F, 5.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("horn_right",
                CubeListBuilder.create().texOffs(64, 16)
                        .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(-2.5F, -7.5F, 2.0F, 0.7F, -0.25F, 0.0F));
        head.addOrReplaceChild("horn_left",
                CubeListBuilder.create().texOffs(64, 16)
                        .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(2.5F, -7.5F, 2.0F, 0.7F, 0.25F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.55F)),
                PartPose.ZERO);
        body.addOrReplaceChild("wing_right",
                CubeListBuilder.create().texOffs(32, 32)
                        .addBox(-0.5F, 0.0F, 0.0F, 1.0F, 13.0F, 7.0F),
                PartPose.offsetAndRotation(-3.5F, 1.0F, 2.5F, 0.35F, 0.55F, -0.12F));
        body.addOrReplaceChild("wing_left",
                CubeListBuilder.create().texOffs(32, 32)
                        .addBox(-0.5F, 0.0F, 0.0F, 1.0F, 13.0F, 7.0F),
                PartPose.offsetAndRotation(3.5F, 1.0F, 2.5F, 0.35F, -0.55F, 0.12F));
        PartDefinition tail1 = body.addOrReplaceChild("tail1",
                CubeListBuilder.create().texOffs(0, 64)
                        .addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 11.0F, 1.5F, -0.85F, 0.0F, 0.0F));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2",
                CubeListBuilder.create().texOffs(32, 64)
                        .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.5F, 0.35F, 0.0F, 0.0F));
        tail2.addOrReplaceChild("tail3",
                CubeListBuilder.create().texOffs(56, 64)
                        .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 6.5F, 0.25F, 0.0F, 0.0F));

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(96, 0)
                        .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(96, 16)
                        .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(96, 32)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(96, 48)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)),
                PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Ride the vanilla skeleton: every pose the player strikes, the dragon strikes. */
    public void copyPose(PlayerModel<AbstractClientPlayer> playerModel) {
        this.head.copyFrom(playerModel.head);
        this.body.copyFrom(playerModel.body);
        this.rightArm.copyFrom(playerModel.rightArm);
        this.leftArm.copyFrom(playerModel.leftArm);
        this.rightLeg.copyFrom(playerModel.rightLeg);
        this.leftLeg.copyFrom(playerModel.leftLeg);
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int light) {
        this.root.render(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
    }
}
