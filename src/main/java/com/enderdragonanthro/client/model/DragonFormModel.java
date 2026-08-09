package com.enderdragonanthro.client.model;

import com.enderdragonanthro.EnderdragonAnthro;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The anthro dragon — a full replacement model (the vanilla player body is
 * hidden while transformed, see PlayerBodyHideMixin).
 *
 * Proportions: the canon dragon's skull is a 16-unit (1 block) cube. At our
 * 4.44x render scale one block is ~3.6 model units, so the skull here is 4
 * units — a true 1:1 dragon head, which lands the whole body at realistic
 * ~1:8 head-to-height anthro proportions instead of Minecraft bobblehead.
 * The six top-level parts keep the vanilla player pivots and copy their
 * poses each frame, so every vanilla animation drives this body.
 * Anatomy per DESIGN.md section 3.
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

        // ---- head: 1:1 dragon skull + long muzzle + hinged jaw ----
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(48, 0).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.2F, 3.0F)   // neck
                        .texOffs(0, 0).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 4.0F, 4.0F)    // skull
                        .texOffs(16, 0).addBox(-1.5F, -5.5F, -7.0F, 3.0F, 2.0F, 5.0F)   // muzzle
                        .texOffs(70, 0).addBox(-1.1F, -5.9F, -6.7F, 0.7F, 0.5F, 1.1F)   // nostril R
                        .texOffs(70, 0).addBox(0.4F, -5.9F, -6.7F, 0.7F, 0.5F, 1.1F),   // nostril L
                PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(32, 0).addBox(-1.4F, 0.0F, -4.9F, 2.8F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, -3.5F, -2.0F, 0.12F, 0.0F, 0.0F));
        head.addOrReplaceChild("horn_right", CubeListBuilder.create()
                        .texOffs(60, 0).addBox(-0.35F, -0.35F, 0.0F, 0.7F, 0.7F, 3.2F),
                PartPose.offsetAndRotation(-1.3F, -5.7F, 1.4F, 0.75F, -0.3F, 0.0F));
        head.addOrReplaceChild("horn_left", CubeListBuilder.create()
                        .texOffs(60, 0).addBox(-0.35F, -0.35F, 0.0F, 0.7F, 0.7F, 3.2F),
                PartPose.offsetAndRotation(1.3F, -5.7F, 1.4F, 0.75F, 0.3F, 0.0F));

        // ---- body: broad chest / waist / hips, spine spikes, wings, tail ----
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-4.5F, 0.0F, -2.6F, 9.0F, 5.5F, 5.0F)     // chest
                        .texOffs(32, 16).addBox(-3.5F, 5.5F, -2.2F, 7.0F, 4.0F, 4.2F)    // waist
                        .texOffs(58, 16).addBox(-4.0F, 9.5F, -2.4F, 8.0F, 3.2F, 4.6F)    // hips
                        .texOffs(84, 16).addBox(-0.4F, 0.4F, 2.5F, 0.8F, 1.2F, 0.8F)     // spine spikes
                        .texOffs(84, 16).addBox(-0.4F, 3.4F, 2.4F, 0.8F, 1.1F, 0.8F)
                        .texOffs(84, 16).addBox(-0.4F, 6.6F, 2.0F, 0.8F, 1.0F, 0.8F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        body.addOrReplaceChild("wing_right", CubeListBuilder.create()
                        .texOffs(84, 0).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 5.5F)     // wing bone
                        .texOffs(104, 0).addBox(-0.3F, 0.0F, 0.4F, 0.6F, 8.5F, 5.2F),    // membrane
                PartPose.offsetAndRotation(-4.2F, 1.2F, 2.4F, 0.35F, 0.55F, -0.15F));
        body.addOrReplaceChild("wing_left", CubeListBuilder.create()
                        .texOffs(84, 0).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 5.5F)
                        .texOffs(104, 0).addBox(-0.3F, 0.0F, 0.4F, 0.6F, 8.5F, 5.2F),
                PartPose.offsetAndRotation(4.2F, 1.2F, 2.4F, 0.35F, -0.55F, 0.15F));
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-1.3F, -1.3F, 0.0F, 2.6F, 2.6F, 7.0F)
                        .texOffs(62, 32).addBox(-0.2F, -2.1F, 1.0F, 0.4F, 0.9F, 4.5F),   // spike ridge
                PartPose.offsetAndRotation(0.0F, 11.5F, 1.8F, -0.9F, 0.0F, 0.0F));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
                        .texOffs(24, 32).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.5F)
                        .texOffs(62, 32).addBox(-0.2F, -1.7F, 0.8F, 0.4F, 0.7F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 6.6F, 0.42F, 0.0F, 0.0F));
        tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                        .texOffs(44, 32).addBox(-0.7F, -0.7F, 0.0F, 1.4F, 1.4F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 6.2F, 0.3F, 0.0F, 0.0F));

        // ---- arms: shoulder bulge, forearm, three claw fingers ----
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(0, 48).addBox(-4.3F, -2.6F, -2.3F, 4.6F, 5.2F, 4.6F)    // shoulder
                        .texOffs(20, 48).addBox(-3.9F, 2.4F, -2.05F, 4.0F, 7.0F, 4.1F)   // forearm
                        .texOffs(40, 48).addBox(-3.4F, 9.2F, -1.9F, 0.9F, 1.9F, 0.9F)    // claws
                        .texOffs(40, 48).addBox(-2.3F, 9.2F, -1.9F, 0.9F, 1.9F, 0.9F)
                        .texOffs(40, 48).addBox(-1.2F, 9.2F, -1.9F, 0.9F, 1.9F, 0.9F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(0, 48).addBox(-0.3F, -2.6F, -2.3F, 4.6F, 5.2F, 4.6F)
                        .texOffs(20, 48).addBox(-0.1F, 2.4F, -2.05F, 4.0F, 7.0F, 4.1F)
                        .texOffs(40, 48).addBox(2.5F, 9.2F, -1.9F, 0.9F, 1.9F, 0.9F)
                        .texOffs(40, 48).addBox(1.4F, 9.2F, -1.9F, 0.9F, 1.9F, 0.9F)
                        .texOffs(40, 48).addBox(0.3F, 9.2F, -1.9F, 0.9F, 1.9F, 0.9F),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        // ---- legs: thigh, calf, three toe claws ----
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(48, 48).addBox(-2.3F, -0.2F, -2.3F, 4.6F, 6.4F, 4.6F)   // thigh
                        .texOffs(68, 48).addBox(-2.0F, 5.8F, -2.05F, 4.0F, 6.4F, 4.1F)   // calf
                        .texOffs(86, 48).addBox(-1.7F, 10.6F, -3.2F, 0.9F, 1.4F, 1.3F)   // toe claws
                        .texOffs(86, 48).addBox(-0.45F, 10.6F, -3.2F, 0.9F, 1.4F, 1.3F)
                        .texOffs(86, 48).addBox(0.8F, 10.6F, -3.2F, 0.9F, 1.4F, 1.3F),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(48, 48).addBox(-2.3F, -0.2F, -2.3F, 4.6F, 6.4F, 4.6F)
                        .texOffs(68, 48).addBox(-2.0F, 5.8F, -2.05F, 4.0F, 6.4F, 4.1F)
                        .texOffs(86, 48).addBox(-1.7F, 10.6F, -3.2F, 0.9F, 1.4F, 1.3F)
                        .texOffs(86, 48).addBox(-0.45F, 10.6F, -3.2F, 0.9F, 1.4F, 1.3F)
                        .texOffs(86, 48).addBox(0.8F, 10.6F, -3.2F, 0.9F, 1.4F, 1.3F),
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
