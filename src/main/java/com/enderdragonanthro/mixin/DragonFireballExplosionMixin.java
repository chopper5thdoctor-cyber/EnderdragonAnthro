package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a blast to a dragon-form player's fireball.
 *
 * The canon fireball deals no impact damage at all — it only leaves a
 * harming cloud, and Instant Damage HEALS the undead. That makes it useless
 * against exactly the mobs you meet most. A blast (entities only, no terrain
 * damage) restores dragon-grade damage against skeletons and zombies while
 * leaving the canon lingering pool intact.
 */
@Mixin(DragonFireball.class)
public abstract class DragonFireballExplosionMixin {
    @Inject(method = "onHit", at = @At("TAIL"))
    private void enderdragonanthro$blast(HitResult hit, CallbackInfo ci) {
        DragonFireball self = (DragonFireball) (Object) this;
        Level level = self.level();
        if (level.isClientSide || !(self.getOwner() instanceof Player owner)
                || !DragonFormManager.isDragon(owner)) {
            return;
        }
        Vec3 at = hit.getLocation();
        level.explode(owner, at.x, at.y, at.z, 3.0F, Level.ExplosionInteraction.NONE);
    }
}
