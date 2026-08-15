package com.enderdragonanthro.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Make the healing beam track a moving dragon smoothly.
 *
 * The beam's aim point is an {@code EntityDataAccessor<Optional<BlockPos>>} —
 * whole blocks, no fractions. Vanilla never needed better: the dragon it was
 * written for circles a crystal at a distance, where a block of slop is
 * invisible. Walk around one at arm's length and the beam lurches a block at a
 * time, and no amount of updating it more often helps, because the number being
 * sent cannot hold anything finer.
 *
 * So it is corrected where the precision still exists. The renderer already
 * knows the crystal's own interpolated position; this looks up whoever is
 * standing at the block being aimed at and re-derives the endpoint from that
 * player's interpolated position instead. Two consequences fall out of using
 * partialTick rather than the tick: the endpoint is exact, and it moves at the
 * frame rate rather than at 20 Hz.
 *
 * Client-side and cosmetic. Nothing is sent, nothing is stored, and if the
 * lookup finds nobody the vanilla arguments are passed through untouched — a
 * beam aimed at a block really is aimed at a block.
 */
@Mixin(EndCrystalRenderer.class)
public abstract class CrystalBeamSmoothMixin {
    /** How far from the aimed block to accept a player as the real target. */
    private static final double CLAIM = 2.0;

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EnderDragonRenderer;"
                            + "renderCrystalBeams(FFFFILcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            require = 0)
    private void enderdragonanthro$smoothBeam(float dx, float dy, float dz, float partialTick,
                                              int age, PoseStack poseStack,
                                              MultiBufferSource buffers, int light,
                                              EndCrystal crystal, float entityYaw,
                                              float outerPartialTick, PoseStack outerStack,
                                              MultiBufferSource outerBuffers, int outerLight) {
        Vec3 aim = enderdragonanthro$precise(crystal, outerPartialTick);
        if (aim != null) {
            Vec3 from = crystal.getPosition(outerPartialTick);
            dx = (float) (aim.x - from.x);
            dy = (float) (aim.y - from.y);
            dz = (float) (aim.z - from.z);
        }
        EnderDragonRenderer.renderCrystalBeams(dx, dy, dz, partialTick, age,
                poseStack, buffers, light);
    }

    /**
     * The exact point the blocky aim is standing in for, or null if nothing is
     * there and the block itself is genuinely the target.
     */
    private static Vec3 enderdragonanthro$precise(EndCrystal crystal, float partialTick) {
        BlockPos target = crystal.getBeamTarget();
        if (target == null || Minecraft.getInstance().level == null) {
            return null;
        }
        Vec3 centre = Vec3.atCenterOf(target);
        Player best = null;
        double nearest = CLAIM * CLAIM;
        for (Player player : Minecraft.getInstance().level.players()) {
            // Against the same point the server aimed at: the middle of the
            // body, not the feet, or a tall dragon reads as two blocks off.
            Vec3 middle = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
            double gap = middle.distanceToSqr(centre);
            if (gap < nearest) {
                nearest = gap;
                best = player;
            }
        }
        if (best == null) {
            return null;
        }
        return best.getPosition(partialTick).add(0.0, best.getBbHeight() * 0.5, 0.0);
    }
}
