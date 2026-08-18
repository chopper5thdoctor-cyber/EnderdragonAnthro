package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.CrystalBeamAim;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The healing beam: where it points, which way it runs, and what it drags
 * along behind it.
 *
 * The aim point on the wire is an {@code EntityDataAccessor<Optional<BlockPos>>}
 * — whole blocks, no fractions. Vanilla never needed better: the dragon it was
 * written for circles a crystal at a distance, where a block of slop is
 * invisible. Walk around one at arm's length and the beam lurches a block at a
 * time, and no amount of updating it more often helps, because the number being
 * sent cannot hold anything finer. CrystalBeamAim works out who is standing at
 * that block and the endpoint is re-derived from their interpolated position, so
 * it is exact and moves at the frame rate rather than at 20 Hz.
 *
 * Three things then have to be got right, and each was wrong in turn.
 *
 * <p><b>Direction.</b> Vanilla draws the beam backwards — the pose is walked to
 * the target first and the vector handed to renderCrystalBeams is the delta
 * NEGATED. Substituting a plain target-minus-crystal delta kept the length and
 * flipped the beam through the dragon and out the far side.
 *
 * <p><b>Flow.</b> renderCrystalBeams scrolls its texture from the pose origin
 * toward the far end, and vanilla's origin is the target — so the beam runs from
 * the dragon to the crystal, which is backwards for something that is feeding
 * you. Building it from the crystal instead puts the origin there and the flow
 * with it, which is also the more natural frame: the two-block lift the function
 * applies before drawing exists for the crystal's cube, and at the crystal end
 * it is exactly right rather than something to undo.
 *
 * <p><b>The pose.</b> That walk to the target is never popped:
 *
 * <pre>
 * poseStack.translate(dx, dy, dz);                    // to the target
 * EnderDragonRenderer.renderCrystalBeams(-dx, …);
 * super.render(…);                                    // and no popPose
 * </pre>
 *
 * EntityRenderDispatcher draws an entity's shadow inside the same pushPose that
 * wraps the call to its renderer, and the shadow comes after. So the crystal's
 * own shadow is painted at the far end of its beam: it appears only once a
 * crystal has a target, it jitters a block at a time because the target is a
 * BlockPos, it hangs in the air at the height the beam arrives at rather than on
 * the ground, and it reads far larger than it is because a one-block disc at
 * chest height is much nearer the camera than the same disc underfoot. In
 * vanilla the same leak lands the shadow somewhere over the End's void, where
 * nobody has ever looked for it. The name tag rides along with it too.
 *
 * Undoing that walk and leaving the pose on the crystal fixes all of it at once.
 *
 * Client-side and cosmetic throughout. Nothing is sent and nothing is stored.
 */
@Mixin(EndCrystalRenderer.class)
public abstract class CrystalBeamSmoothMixin {
    /**
     * The lift renderCrystalBeams applies before it draws: translate(0, 2, 0).
     * It is there for the crystal's cube, which floats that far above the
     * entity's origin, and drawing from the crystal is what makes it correct
     * rather than something to compensate for. It still has to come out of the
     * vector, or the far end overshoots what it is aimed at by the same two.
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
        BlockPos target = crystal.getBeamTarget();
        if (target == null) {
            EnderDragonRenderer.renderCrystalBeams(dx, dy, dz, partialTick, age,
                    poseStack, buffers, light);
            return;
        }

        Vec3 blocky = Vec3.atCenterOf(target);
        double cx = crystal.getX();
        double cy = crystal.getY();
        double cz = crystal.getZ();
        // The crystal's bob, recovered rather than recomputed: vanilla folds it
        // into the y argument as -(blocky.y - crystal.y) + bob, and the phase it
        // used is the one this frame was drawn with.
        double bob = dy + (blocky.y - cy);

        // Back to the crystal, and stay there. Leaving the pose here is the fix
        // for the wandering shadow, not a side effect of drawing the beam
        // differently -- everything after this call inherits it.
        poseStack.translate((float) (cx - blocky.x), (float) (cy - blocky.y),
                (float) (cz - blocky.z));

        Vec3 aim = CrystalBeamAim.of(crystal, outerPartialTick);
        Vec3 end = aim != null ? aim : blocky;

        poseStack.pushPose();
        poseStack.translate(0.0, bob, 0.0);          // ride the crystal's bob
        EnderDragonRenderer.renderCrystalBeams(
                (float) (end.x - cx),
                (float) (end.y - cy - bob - LIFT),
                (float) (end.z - cz),
                partialTick, age, poseStack, buffers, light);
        poseStack.popPose();
    }
}
