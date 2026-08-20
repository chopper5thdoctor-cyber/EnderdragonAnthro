package com.enderdragonanthro.client.render;

import com.enderdragonanthro.client.DragonSightClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * What the sight looks like: a violet world, portals through the ground, and
 * every living thing wearing its own health.
 *
 * All of it is drawn rather than computed. The brightness is a night vision
 * effect the server keeps topped up, so nothing here has to fight the light
 * texture; this is only the tint, the marks and the numbers.
 *
 * Everything in the world pass is drawn with SEE_THROUGH, which is the display
 * mode nameplates use — it ignores depth, so a portal under a mountain reads
 * exactly as clearly as one in front of you. That is the whole point of a
 * sense as against a view.
 */
public final class DragonSightRender {
    /** How strong the tint is over the whole screen. Very, as asked. */
    private static final int TINT_ALPHA = 0x66;
    /** Health is drawn for anything living within this many blocks. */
    private static final double HEALTH_RANGE = 48.0;
    /** Portals are drawn however far off they were found. */
    private static final float MARK_SCALE = 0.04F;

    private DragonSightRender() {
    }

    /** The violet the whole screen is seen through. */
    public static void tint(GuiGraphics graphics) {
        if (!DragonSightClient.isOpen()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        graphics.fill(0, 0, client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                (TINT_ALPHA << 24) | DragonSightClient.EYE);
    }

    /** Portal marks and mob health, in the world, through anything. */
    public static void world(WorldRenderContext context) {
        if (!DragonSightClient.isOpen()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Player self = client.player;
        MultiBufferSource buffers = context.consumers();
        if (self == null || buffers == null || client.level == null) {
            return;
        }
        Camera camera = context.camera();
        Vec3 eye = camera.getPosition();
        PoseStack poseStack = context.matrixStack();

        marks(poseStack, buffers, camera, eye, DragonSightClient.gateways(),
                "◆ Gateway", 0xFFB14CFF);
        marks(poseStack, buffers, camera, eye, DragonSightClient.end(),
                "◆ End", 0xFF4CFFD9);

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == self
                    || entity.distanceToSqr(eye) > HEALTH_RANGE * HEALTH_RANGE) {
                continue;
            }
            float health = living.getHealth();
            float max = living.getMaxHealth();
            String text = String.format("%.0f/%.0f", health, max);
            // Green through red by how much is left, so a glance reads as a
            // threat assessment rather than as a wall of numbers.
            float share = max > 0.0F ? Math.min(1.0F, health / max) : 0.0F;
            int colour = 0xFF000000
                    | (Math.round((1.0F - share) * 255) << 16)
                    | (Math.round(share * 255) << 8);
            Vec3 at = living.getPosition(context.tickCounter()
                            .getGameTimeDeltaPartialTick(false))
                    .add(0.0, living.getBbHeight() + 0.5, 0.0);
            label(poseStack, buffers, camera, at.subtract(eye), text, colour, 0.025F);
        }
    }

    private static void marks(PoseStack poseStack, MultiBufferSource buffers, Camera camera,
                              Vec3 eye, List<BlockPos> positions, String name, int colour) {
        for (BlockPos pos : positions) {
            Vec3 at = Vec3.atCenterOf(pos);
            int metres = (int) Math.round(at.distanceTo(eye));
            label(poseStack, buffers, camera, at.subtract(eye),
                    name + "  " + metres + "m", colour, MARK_SCALE);
        }
    }

    /**
     * One line of text standing in the world, facing the camera.
     *
     * The same construction a nameplate uses: walk to the point, turn to face
     * the camera, and flip both axes because the font's y runs down the screen
     * while the world's runs up.
     */
    private static void label(PoseStack poseStack, MultiBufferSource buffers, Camera camera,
                              Vec3 offset, String text, int colour, float scale) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-scale, -scale, scale);
        Matrix4f pose = poseStack.last().pose();
        font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, colour, false, pose,
                buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
