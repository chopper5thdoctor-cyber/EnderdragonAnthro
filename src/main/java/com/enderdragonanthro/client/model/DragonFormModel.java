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
 * The anthro dragon — full replacement model (vanilla body hidden by
 * PlayerBodyHideMixin).
 *
 * The head is the canon Ender Dragon head, copied from the vanilla dragon
 * model at 1/4 size (its 16-unit skull = 1 block = 4 units at our 4.44x
 * render scale): square skull, wide flat snout in the lower half of the
 * face, nostrils on top of the snout, hinged jaw, and the two upright
 * horn nubs. All cubes are integer-sized for crisp pixel mapping, and
 * left/right parts are exact mirrors.
 *
 * The six top-level parts keep vanilla player pivots and copy their poses
 * each frame, so every vanilla animation drives this body.
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

        // ---- head: neck only — the skull/snout/jaw are the canon dragon head
        // (CanonDragonHeadModel), rendered by DragonFormLayer onto this part's
        // pose with the vanilla dragon textures ----
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(64, 0).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F),  // neck
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // ---- body: chest / waist / hips, spine spikes, folded wings, tail ----
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-5.0F, 0.0F, -2.5F, 10.0F, 5.0F, 5.0F)    // chest
                        .texOffs(32, 16).addBox(-4.0F, 5.0F, -2.0F, 8.0F, 4.0F, 4.0F)    // waist
                        .texOffs(58, 16).addBox(-4.0F, 9.0F, -2.5F, 8.0F, 3.0F, 5.0F)    // hips
                        .texOffs(86, 16).addBox(-0.5F, 0.5F, 2.6F, 1.0F, 1.0F, 1.0F)     // spine spikes
                        .texOffs(86, 16).addBox(-0.5F, 2.5F, 2.6F, 1.0F, 1.0F, 1.0F)
                        .texOffs(86, 16).addBox(-0.5F, 4.5F, 2.4F, 1.0F, 1.0F, 1.0F)
                        .texOffs(86, 16).addBox(-0.5F, 6.5F, 2.2F, 1.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        body.addOrReplaceChild("wing_right", CubeListBuilder.create()
                        .texOffs(78, 0).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F)     // wing bone
                        .texOffs(94, 0).addBox(-0.5F, 0.5F, 0.0F, 1.0F, 9.0F, 6.0F),     // folded membrane
                PartPose.offsetAndRotation(-3.8F, 1.0F, 2.2F, 0.1F, 0.9F, -0.08F));
        body.addOrReplaceChild("wing_left", CubeListBuilder.create()
                        .texOffs(78, 0).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F)
                        .texOffs(94, 0).addBox(-0.5F, 0.5F, 0.0F, 1.0F, 9.0F, 6.0F),
                PartPose.offsetAndRotation(3.8F, 1.0F, 2.2F, 0.1F, -0.9F, 0.08F));
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 7.0F)
                        .texOffs(58, 32).addBox(-0.5F, -2.3F, 1.5F, 1.0F, 1.0F, 4.0F),   // spike ridge
                PartPose.offsetAndRotation(0.0F, 11.0F, 1.5F, -0.9F, 0.0F, 0.0F));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
                        .texOffs(24, 32).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F)
                        .texOffs(58, 32).addBox(-0.5F, -1.8F, 1.0F, 1.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 6.6F, 0.42F, 0.0F, 0.0F));
        tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                        .texOffs(42, 32).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 5.6F, 0.3F, 0.0F, 0.0F));

        // ---- arms: shoulder, forearm, three claw fingers (exact mirrors) ----
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(0, 48).addBox(-4.5F, -2.0F, -2.5F, 5.0F, 5.0F, 5.0F)    // shoulder
                        .texOffs(24, 48).addBox(-4.0F, 3.0F, -2.0F, 4.0F, 7.0F, 4.0F)    // forearm
                        .texOffs(44, 48).addBox(-3.8F, 9.5F, -2.2F, 1.0F, 2.0F, 1.0F)    // claws
                        .texOffs(44, 48).addBox(-2.5F, 9.5F, -2.2F, 1.0F, 2.0F, 1.0F)
                        .texOffs(44, 48).addBox(-1.2F, 9.5F, -2.2F, 1.0F, 2.0F, 1.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(0, 48).addBox(-0.5F, -2.0F, -2.5F, 5.0F, 5.0F, 5.0F)
                        .texOffs(24, 48).addBox(0.0F, 3.0F, -2.0F, 4.0F, 7.0F, 4.0F)
                        .texOffs(44, 48).addBox(2.8F, 9.5F, -2.2F, 1.0F, 2.0F, 1.0F)
                        .texOffs(44, 48).addBox(1.5F, 9.5F, -2.2F, 1.0F, 2.0F, 1.0F)
                        .texOffs(44, 48).addBox(0.2F, 9.5F, -2.2F, 1.0F, 2.0F, 1.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        // ---- legs: thigh, calf, three toe claws ----
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(52, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F)    // thigh
                        .texOffs(72, 48).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 6.0F, 4.0F)    // calf
                        .texOffs(92, 48).addBox(-1.7F, 10.5F, -3.4F, 1.0F, 1.5F, 1.5F)   // toe claws
                        .texOffs(92, 48).addBox(-0.5F, 10.5F, -3.4F, 1.0F, 1.5F, 1.5F)
                        .texOffs(92, 48).addBox(0.7F, 10.5F, -3.4F, 1.0F, 1.5F, 1.5F),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(52, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F)
                        .texOffs(72, 48).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 6.0F, 4.0F)
                        .texOffs(92, 48).addBox(-1.7F, 10.5F, -3.4F, 1.0F, 1.5F, 1.5F)
                        .texOffs(92, 48).addBox(-0.5F, 10.5F, -3.4F, 1.0F, 1.5F, 1.5F)
                        .texOffs(92, 48).addBox(0.7F, 10.5F, -3.4F, 1.0F, 1.5F, 1.5F),
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

    /** Applies the head bone's current pose so the canon head can ride it. */
    public void applyHeadTransform(PoseStack poseStack) {
        this.head.translateAndRotate(poseStack);
    }
}
