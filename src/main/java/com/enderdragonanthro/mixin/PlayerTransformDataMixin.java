package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.TransformAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerTransformDataMixin implements TransformAccess {
    @Unique
    private static final String NBT_KEY = "enderdragonanthro:dragon";

    @Unique
    private boolean enderdragonanthro$dragon = false;

    @Override
    public boolean enderdragonanthro$isDragon() {
        return this.enderdragonanthro$dragon;
    }

    @Override
    public void enderdragonanthro$setDragon(boolean dragon) {
        this.enderdragonanthro$dragon = dragon;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void enderdragonanthro$save(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(NBT_KEY, this.enderdragonanthro$dragon);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void enderdragonanthro$load(CompoundTag tag, CallbackInfo ci) {
        this.enderdragonanthro$dragon = tag.getBoolean(NBT_KEY);
    }
}
