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
 * The anthro dragon body, authored at 4x scale on a 512x512 sheet and
 * rendered at 1/4 by DragonFormLayer. Box UV pins one texel per model
 * unit, so authoring big and rendering small quadruples texel density —
 * the same trick that makes the canon head (16 units -> 1 block) crisp.
 * At 4x there is room for real anatomy: traps, delts, pecs, abs,
 * biceps, triceps, forearms, hands, quads, calves, feet, plus the
 * cheek horns and crown spike rows from the reference art.
 *
 * The six top-level parts still ride the vanilla player skeleton;
 * copyPose scales the copied pivot offsets by 4 to match the authoring
 * scale (rotations copy unchanged).
 */
public class DragonFormModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(EnderdragonAnthro.id("dragon_form"), "main");

    /** Authoring scale: model units are 4x final units. */
    public static final float AUTHOR_SCALE = 4.0F;

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

        // ---- head: neck + cheek horns + crown spikes (skull itself is the
        // canon head, rendered by DragonFormLayer on this bone) ----
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6.0F, -8.0F, -6.0F, 12.0F, 8.0F, 12.0F),  // neck
                PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("cheek_horn_right", CubeListBuilder.create()
                        .texOffs(48, 0).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F),
                PartPose.offsetAndRotation(-8.0F, -12.0F, 2.0F, 0.55F, -0.35F, 0.0F));
        head.addOrReplaceChild("cheek_horn_left", CubeListBuilder.create()
                        .texOffs(48, 0).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F),
                PartPose.offsetAndRotation(8.0F, -12.0F, 2.0F, 0.55F, 0.35F, 0.0F));
        head.addOrReplaceChild("crown_spike_right", CubeListBuilder.create()
                        .texOffs(96, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 10.0F),
                PartPose.offsetAndRotation(-3.6F, -24.0F, 3.2F, 0.75F, -0.15F, 0.0F));
        head.addOrReplaceChild("crown_spike_left", CubeListBuilder.create()
                        .texOffs(96, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 10.0F),
                PartPose.offsetAndRotation(3.6F, -24.0F, 3.2F, 0.75F, 0.15F, 0.0F));
        head.addOrReplaceChild("nape_spike_right", CubeListBuilder.create()
                        .texOffs(96, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 10.0F),
                PartPose.offsetAndRotation(-5.6F, -18.0F, 6.4F, 0.9F, -0.3F, 0.0F));
        head.addOrReplaceChild("nape_spike_left", CubeListBuilder.create()
                        .texOffs(96, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 10.0F),
                PartPose.offsetAndRotation(5.6F, -18.0F, 6.4F, 0.9F, 0.3F, 0.0F));

        // ---- body: traps, chest with 3D pecs, waist with ab plate, hips,
        // spine spikes, wings, tail ----
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(168, 72).addBox(-14.0F, -2.0F, -4.0F, 10.0F, 6.0F, 8.0F)   // trap R
                        .texOffs(168, 72).addBox(4.0F, -2.0F, -4.0F, 10.0F, 6.0F, 8.0F)     // trap L
                        .texOffs(0, 72).addBox(-20.0F, 0.0F, -10.0F, 40.0F, 20.0F, 20.0F)   // chest
                        .texOffs(124, 72).addBox(-18.0F, 2.0F, -13.0F, 16.0F, 12.0F, 4.0F)  // pec R
                        .texOffs(124, 72).addBox(2.0F, 2.0F, -13.0F, 16.0F, 12.0F, 4.0F)    // pec L
                        .texOffs(208, 72).addBox(-16.0F, 20.0F, -8.0F, 32.0F, 16.0F, 16.0F) // waist
                        .texOffs(308, 72).addBox(-10.0F, 20.0F, -11.0F, 20.0F, 16.0F, 4.0F) // ab plate
                        .texOffs(0, 120).addBox(-16.0F, 36.0F, -10.0F, 32.0F, 12.0F, 20.0F) // hips
                        .texOffs(260, 0).addBox(-2.0F, 2.0F, 10.4F, 4.0F, 4.0F, 4.0F)       // spine spikes
                        .texOffs(260, 0).addBox(-2.0F, 10.0F, 10.4F, 4.0F, 4.0F, 4.0F)
                        .texOffs(260, 0).addBox(-2.0F, 18.0F, 9.6F, 4.0F, 4.0F, 4.0F)
                        .texOffs(260, 0).addBox(-2.0F, 26.0F, 8.8F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        body.addOrReplaceChild("wing_right", CubeListBuilder.create()
                        .texOffs(128, 0).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 24.0F)      // wing bone
                        .texOffs(192, 0).addBox(-2.0F, 2.0F, 0.0F, 4.0F, 40.0F, 28.0F),     // membrane
                PartPose.offsetAndRotation(-15.2F, 4.0F, 8.8F, 0.1F, 0.9F, -0.08F));
        body.addOrReplaceChild("wing_left", CubeListBuilder.create()
                        .texOffs(128, 0).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 24.0F)
                        .texOffs(192, 0).addBox(-2.0F, 2.0F, 0.0F, 4.0F, 40.0F, 28.0F),
                PartPose.offsetAndRotation(15.2F, 4.0F, 8.8F, 0.1F, -0.9F, 0.08F));
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
                        .texOffs(108, 120).addBox(-6.0F, -6.0F, 0.0F, 12.0F, 12.0F, 28.0F)
                        .texOffs(320, 120).addBox(-2.0F, -9.2F, 6.0F, 4.0F, 4.0F, 16.0F),   // spike ridge
                PartPose.offsetAndRotation(0.0F, 44.0F, 6.0F, -0.9F, 0.0F, 0.0F));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
                        .texOffs(192, 120).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 24.0F)
                        .texOffs(320, 120).addBox(-2.0F, -7.2F, 4.0F, 4.0F, 4.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 26.4F, 0.42F, 0.0F, 0.0F));
        tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                        .texOffs(260, 120).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 24.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.4F, 0.3F, 0.0F, 0.0F));

        // ---- arms: delt, upper arm with bicep + tricep, forearm, hand, claws ----
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(0, 164).addBox(-18.0F, -10.0F, -9.0F, 18.0F, 12.0F, 18.0F)  // delt
                        .texOffs(76, 164).addBox(-16.0F, 2.0F, -8.0F, 16.0F, 14.0F, 16.0F)   // upper arm
                        .texOffs(144, 164).addBox(-14.0F, 3.0F, -11.0F, 10.0F, 10.0F, 4.0F)  // bicep (front)
                        .texOffs(176, 164).addBox(-14.0F, 2.0F, 7.0F, 10.0F, 12.0F, 4.0F)    // tricep (back)
                        .texOffs(208, 164).addBox(-15.0F, 16.0F, -7.5F, 14.0F, 18.0F, 15.0F) // forearm
                        .texOffs(268, 164).addBox(-14.5F, 34.0F, -7.0F, 13.0F, 8.0F, 14.0F)  // hand
                        .texOffs(324, 164).addBox(-13.6F, 41.0F, -8.4F, 4.0F, 7.0F, 4.0F)    // claws
                        .texOffs(324, 164).addBox(-10.0F, 41.0F, -8.4F, 4.0F, 7.0F, 4.0F)
                        .texOffs(324, 164).addBox(-6.4F, 41.0F, -8.4F, 4.0F, 7.0F, 4.0F),
                PartPose.offset(-20.0F, 8.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(0, 164).addBox(0.0F, -10.0F, -9.0F, 18.0F, 12.0F, 18.0F)
                        .texOffs(76, 164).addBox(0.0F, 2.0F, -8.0F, 16.0F, 14.0F, 16.0F)
                        .texOffs(144, 164).addBox(4.0F, 3.0F, -11.0F, 10.0F, 10.0F, 4.0F)
                        .texOffs(176, 164).addBox(4.0F, 2.0F, 7.0F, 10.0F, 12.0F, 4.0F)
                        .texOffs(208, 164).addBox(1.0F, 16.0F, -7.5F, 14.0F, 18.0F, 15.0F)
                        .texOffs(268, 164).addBox(1.5F, 34.0F, -7.0F, 13.0F, 8.0F, 14.0F)
                        .texOffs(324, 164).addBox(2.4F, 41.0F, -8.4F, 4.0F, 7.0F, 4.0F)
                        .texOffs(324, 164).addBox(6.0F, 41.0F, -8.4F, 4.0F, 7.0F, 4.0F)
                        .texOffs(324, 164).addBox(9.6F, 41.0F, -8.4F, 4.0F, 7.0F, 4.0F),
                PartPose.offset(20.0F, 8.0F, 0.0F));

        // ---- legs: thigh with quad, calf with calf bump, foot, toe claws ----
        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(0, 200).addBox(-9.0F, 0.0F, -9.0F, 18.0F, 22.0F, 18.0F)     // thigh
                .texOffs(76, 200).addBox(-7.0F, 2.0F, -11.5F, 14.0F, 12.0F, 4.0F)    // quad (front)
                .texOffs(116, 200).addBox(-8.0F, 22.0F, -8.0F, 16.0F, 16.0F, 16.0F)  // calf
                .texOffs(184, 200).addBox(-6.5F, 23.0F, 6.5F, 13.0F, 10.0F, 4.0F)    // calf bump (back)
                .texOffs(220, 200).addBox(-8.0F, 40.0F, -14.4F, 16.0F, 8.0F, 20.0F)  // foot
                .texOffs(296, 200).addBox(-7.2F, 42.0F, -17.0F, 4.0F, 6.0F, 4.0F)    // toe claws
                .texOffs(296, 200).addBox(-2.0F, 42.0F, -17.0F, 4.0F, 6.0F, 4.0F)
                .texOffs(296, 200).addBox(3.2F, 42.0F, -17.0F, 4.0F, 6.0F, 4.0F);
        root.addOrReplaceChild("right_leg", leg, PartPose.offset(-7.6F, 48.0F, 0.0F));
        root.addOrReplaceChild("left_leg", leg, PartPose.offset(7.6F, 48.0F, 0.0F));

        return LayerDefinition.create(mesh, 512, 512);
    }

    /** Ride the vanilla skeleton; pivot offsets scale up to authoring space. */
    public void copyPose(PlayerModel<AbstractClientPlayer> playerModel) {
        copyScaled(this.head, playerModel.head);
        copyScaled(this.body, playerModel.body);
        copyScaled(this.rightArm, playerModel.rightArm);
        copyScaled(this.leftArm, playerModel.leftArm);
        copyScaled(this.rightLeg, playerModel.rightLeg);
        copyScaled(this.leftLeg, playerModel.leftLeg);
    }

    private static void copyScaled(ModelPart target, ModelPart source) {
        target.copyFrom(source);
        target.x = source.x * AUTHOR_SCALE;
        target.y = source.y * AUTHOR_SCALE;
        target.z = source.z * AUTHOR_SCALE;
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int light) {
        this.root.render(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
    }

    /** Applies the head bone's current pose so the canon head can ride it. */
    public void applyHeadTransform(PoseStack poseStack) {
        this.head.translateAndRotate(poseStack);
    }
}
