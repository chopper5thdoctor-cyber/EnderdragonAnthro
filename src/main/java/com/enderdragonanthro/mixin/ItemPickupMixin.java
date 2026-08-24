package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.DragonPickup;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leave it where it fell.
 *
 * playerTouch is the one place worth doing this: it runs server-side, it is
 * where vanilla decides an item has been collected, and cancelling at the head
 * of it means the item is never picked up, never plays the sound and never
 * animates toward the player. Filtering further in would collect the stack and
 * then throw it away.
 *
 * The switch is per player rather than per form, so it survives dropping out of
 * dragon shape and back — a preference about what you carry is not something
 * that should reset every time you transform.
 */
@Mixin(ItemEntity.class)
public abstract class ItemPickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$leaveIt(Player player, CallbackInfo ci) {
        if (!DragonPickup.picksUp(player)) {
            ci.cancel();
        }
    }
}
