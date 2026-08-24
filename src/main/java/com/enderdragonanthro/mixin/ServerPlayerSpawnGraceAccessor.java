package com.enderdragonanthro.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The sixty ticks a freshly created player cannot be hurt for.
 *
 * ServerPlayer.hurt returns false outright while spawnInvulnerableTime is
 * positive, and the only thing that decrements it is doTick(). A player made by
 * hand for a test is never ticked, so it stays at 60 forever and every hurt()
 * in every test quietly answers false -- which reads exactly like the damage
 * code being broken, and cost a debugging round to tell apart from one.
 *
 * The field is private and there is no setter. An accessor rather than
 * reflection because Loom remaps it: a hardcoded field name would work in dev
 * and mean nothing anywhere else.
 *
 * Only FakeDragon uses this. Nothing in the mod proper has any business
 * reaching into a player's spawn grace.
 */
@Mixin(ServerPlayer.class)
public interface ServerPlayerSpawnGraceAccessor {
    @Accessor("spawnInvulnerableTime")
    void enderdragonanthro$setSpawnGrace(int ticks);
}
