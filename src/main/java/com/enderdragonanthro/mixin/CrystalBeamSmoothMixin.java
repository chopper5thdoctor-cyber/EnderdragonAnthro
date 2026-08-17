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
 * So it is corrected where the precision still exists. This looks up whoever is
 * standing at the block being aimed at and re-derives the endpoint from that
 * player's interpolated position instead. Two consequences fall out of using
 * partialTick rather than the tick: the endpoint is exact, and it moves at the
 * frame rate rather than at 20 Hz.
 *
 * Getting there means matching vanilla's frame rather than inventing one. The
 * beam is drawn from the target back to the crystal, not the other way about:
 * by the time these arguments are built the pose has already been walked over
 * to the target, and the vector handed to renderCrystalBeams is the delta
 * negated, with the crystal's bob folded into its y. Substituting a plain
 * target-minus-crystal delta reversed the beam through the dragon and out the
 * far side, which is worse than the blockiness it was meant to fix.
 *
 * Client-side and cosmetic. Nothing is sent, nothing is stored, and if the
 * lookup finds nobody the vanilla arguments are passed through untouched — a
 * beam aimed at a block really is aimed at a block.
 */
@Mixin(EndCrystalRenderer.class)
public abstract class CrystalBeamSmoothMixin {
    /** How far from the aimed block to accept a player as the real target. */
    private static final double CLAIM = 2.0;
    /**
     * The lift renderCrystalBeams applies to both ends of the beam before it
     * draws anything: translate(0, 2, 0). It is there for the crystal end,
     * whose cube floats that far above the entity's origin, and vanilla wears
     * it at the far end too because the far end is a dragon twenty blocks
     * across. Undone here for the target and put back into the direction, so
     * the beam lands on what it is aimed at and still leaves the crystal at
     * the same point on the crystal.
     */
    private static final float LIFT = 2.0F;

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
        BlockPos target = crystal.getBeamTarget();
        if (aim == null || target == null) {
            EnderDragonRenderer.renderCrystalBeams(dx, dy, dz, partialTick, age,
                    poseStack, buffers, light);
            return;
        }

        // The beam is drawn backwards, from the target to the crystal: the
        // caller has already walked the pose over to the blocky target, and
        // these arguments are the delta NEGATED. Handing it the forward delta
        // -- which is what this did -- keeps the length and flips the beam
        // through the target, so it shot out of the dragon's far side.
        Vec3 blocky = Vec3.atCenterOf(target);
        // The crystal's bob, recovered rather than recomputed: vanilla folds it
        // into the y argument as -(blocky.y - crystal.y) + bob, and the phase it
        // used is the one this frame was drawn with.
        double bob = dy + (blocky.y - crystal.getY());

        poseStack.pushPose();
        poseStack.translate(aim.x - blocky.x, aim.y - blocky.y - LIFT, aim.z - blocky.z);
        EnderDragonRenderer.renderCrystalBeams(
                (float) (crystal.getX() - aim.x),
                (float) (crystal.getY() - aim.y + bob + LIFT),
                (float) (crystal.getZ() - aim.z),
                partialTick, age, poseStack, buffers, light);
        poseStack.popPose();
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
