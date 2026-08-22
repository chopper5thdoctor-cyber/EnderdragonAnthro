package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.DragonPresence;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bats, which have no goals to give fear to.
 *
 * Everything else that runs from a dragon does it through the goal selector,
 * which is where fear belongs. A bat has a goal selector and puts nothing in
 * it: {@code customServerAiStep} picks a random air block within seven blocks,
 * flies at it, and picks another when it arrives or one tick in thirty at
 * random. That is the whole of a bat's mind, and a goal cannot get a word in
 * because the navigation it would steer is not the thing moving the bat.
 *
 * So the same idea is expressed in the only vocabulary a bat has: its target
 * block is set, before its own step runs, to a spot away from the dragon.
 * Vanilla then flies it there with its own drift and flap, so it still moves
 * like a bat — it just keeps choosing away.
 *
 * It gets overruled one tick in thirty by the random re-pick, which is left
 * alone deliberately: a bat that flees in a dead straight line looks like a
 * paper dart, and the occasional stray beat is what keeps it looking like a bat.
 */
@Mixin(Bat.class)
public abstract class BatFearMixin {
    /** How far ahead of itself a frightened bat aims. Its own range is 7. */
    private static final double BOLT = 10.0;
    /** Heights to try, in order — up and away reads as startled. */
    private static final int[] LIFT = {3, 1, 5, 0};

    @Shadow
    private BlockPos targetPosition;

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void enderdragonanthro$flee(CallbackInfo ci) {
        Bat bat = (Bat) (Object) this;
        Player dragon = DragonPresence.dreadedBy(bat);
        if (dragon == null) {
            return;
        }
        if (bat.isResting()) {
            // Roosting through a dragon walking underneath is not composure,
            // it is the bat not having been asked.
            bat.setResting(false);
        }

        Vec3 away = bat.position().subtract(dragon.position());
        // Directly overhead is the one case with no horizontal answer; any
        // direction will do there, so long as it is a direction.
        away = away.horizontalDistanceSqr() < 1.0E-4
                ? new Vec3(1.0, 0.0, 0.0) : new Vec3(away.x, 0.0, away.z).normalize();

        for (int lift : LIFT) {
            BlockPos flee = BlockPos.containing(
                    bat.getX() + away.x * BOLT,
                    Mth.clamp(bat.getY() + lift,
                            bat.level().getMinBuildHeight() + 1,
                            bat.level().getMaxBuildHeight() - 1),
                    bat.getZ() + away.z * BOLT);
            // An occupied block is discarded by vanilla on the very next line,
            // which would leave the bat with its old wander target and no fear
            // at all, so a free one is found here instead.
            if (bat.level().isEmptyBlock(flee)) {
                this.targetPosition = flee;
                return;
            }
        }
    }
}
