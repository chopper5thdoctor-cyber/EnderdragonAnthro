package com.enderdragonanthro.client.render;

import com.enderdragonanthro.client.DragonSightClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * Every living thing wearing its own health, while the sight is open.
 *
 * A feature layer rather than a pass of its own. The first attempt drew this
 * from WorldRenderEvents.AFTER_ENTITIES and nothing appeared — plausible in
 * principle, unverified in practice, and the wrong bet either way. A layer runs
 * inside the entity's own render, with the pose already standing at the entity
 * and a buffer source that is certain to be flushed, which is exactly the
 * ground nameplates are drawn on.
 *
 * SEE_THROUGH is what carries it through walls. That is the sense: a dragon
 * that can read the health of something it cannot see is doing what was asked
 * for, and one that has to look at it first is just reading a health bar.
 */
public class DragonSightHealthLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {
    /** How far off a mob is still worth reading. */
    private static final double RANGE = 48.0;
    /**
     * Text size. Nameplates use 0.025; this is bigger deliberately, because it
     * is read at a glance across a field rather than leant in to.
     */
    private static final float SCALE = 0.045F;
    /** How far above the head it floats, clear of any name tag. */
    private static final float HEADROOM = 0.9F;

    public DragonSightHealthLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        Minecraft client = Minecraft.getInstance();
        if (!DragonSightClient.isOpen() || entity == client.player || client.player == null) {
            return;
        }
        if (entity.distanceToSqr(client.player) > RANGE * RANGE) {
            return;
        }

        float health = entity.getHealth();
        float max = entity.getMaxHealth();
        String text = Math.round(health) + "/" + Math.round(max);
        // Green through red by what is left, so a glance reads as a threat
        // assessment rather than as a wall of numbers.
        float share = max > 0.0F ? Math.min(1.0F, Math.max(0.0F, health / max)) : 0.0F;
        int colour = 0xFF000000
                | (Math.round((1.0F - share) * 255) << 16)
                | (Math.round(share * 255) << 8);

        Font font = client.font;
        poseStack.pushPose();
        // A layer runs in MODEL space, which is not world space and is why this
        // came out under the mob's feet. LivingEntityRenderer.render does
        // scale(-1, -1, 1) and then translate(0, -1.501, 0) before any layer is
        // called, so Y points DOWN and the origin already sits 1.501 above the
        // feet. Translating up by the height walked down past the legs by very
        // nearly twice it.
        //
        // Undone rather than compensated for: step to the label's height in the
        // flipped frame, then flip back, and what is left is ordinary world
        // space where the nameplate recipe means what it says.
        poseStack.translate(0.0F, -(entity.getBbHeight() + HEADROOM - 1.501F), 0.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        // The camera's own orientation, so it turns to face wherever you are
        // looking from -- first person, third, or orbiting.
        poseStack.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-SCALE, -SCALE, SCALE);
        Matrix4f pose = poseStack.last().pose();
        font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, colour, false, pose,
                buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
