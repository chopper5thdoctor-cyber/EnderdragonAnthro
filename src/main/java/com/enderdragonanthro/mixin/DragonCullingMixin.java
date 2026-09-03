package com.enderdragonanthro.mixin;

import com.enderdragonanthro.DragonRig;
import com.enderdragonanthro.client.DragonHud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stop a dragon vanishing when its hitbox leaves the view.
 *
 * SHIPPED BUG, and one only a second player could ever see. Minecraft culls an
 * entity on its BOUNDING BOX, not on what gets drawn: EntityRenderer.shouldRender
 * tests getBoundingBoxForCulling against the frustum and skips the entity
 * entirely if it misses. A dragon's box is the player's, scaled -- 2.67 blocks
 * wide at an eight-block hitbox -- while the wings reach 6.34 blocks out.
 *
 * So the model overhangs its own culling box by five blocks a side, and looking
 * slightly away from another dragon made the whole thing blink out while a
 * wingspan of it was still on screen. From inside your own head this never
 * happens: you are never outside your own frustum.
 *
 * The amount comes from {@link DragonRig#cullReach} and is measured off the rig
 * at conversion time, not typed here -- redraw the wings longer and it follows.
 *
 * The form check reads the SCALE attribute rather than
 * {@code DragonFormManager.isDragon}, and that is the difference between this
 * working and doing nothing at all: isDragon reads a flag that lives on the
 * server, so for somebody ELSE's dragon -- the only case this exists for -- it
 * is false on every client. The attribute modifier is synced, which is why
 * DragonHud has always keyed off it.
 */
@Mixin(Entity.class)
public abstract class DragonCullingMixin {
    @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
    private void enderdragonanthro$widenForWings(CallbackInfoReturnable<AABB> cir) {
        if (!((Object) this instanceof Player player) || !DragonHud.isDragonForm(player)) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue()
                .inflate(DragonRig.cullReach(((LivingEntity) player).getScale())));
    }
}
