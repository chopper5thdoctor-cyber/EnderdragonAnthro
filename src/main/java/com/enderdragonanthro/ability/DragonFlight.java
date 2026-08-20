package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Wing flight, using vanilla elytra physics rather than an imitation of them.
 *
 * The glide itself is literally the elytra code path: LivingEntityGlideMixin
 * keeps the fall-flying flag set for a transformed player who wants to glide,
 * so {@code LivingEntity.travel()} takes its elytra branch untouched. Boosts
 * reproduce the firework rocket's impulse exactly.
 */
public final class DragonFlight {
    private static final Set<UUID> GLIDING = new HashSet<>();

    private DragonFlight() {
    }

    public static boolean wantsGlide(ServerPlayer player) {
        return GLIDING.contains(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        GLIDING.remove(player.getUUID());
    }

    /** Double-tap jump: start gliding, with one free boost to get moving. */
    public static void start(ServerPlayer player) {
        if (player.onGround() || player.isPassenger()) {
            return;
        }
        GLIDING.add(player.getUUID());
        player.startFallFlying();
        boost(player);
    }

    /**
     * A wingbeat. The firework rocket uses 0.1 push and a 1.5 target speed;
     * these are wings, so they beat it — 0.2 push toward a 2.6 target, and the
     * whole pull is applied rather than half of it.
     */
    public static void boost(ServerPlayer player) {
        if (!player.isFallFlying()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 delta = player.getDeltaMovement();
        double push = 0.2, target = 2.6, pull = 0.75;
        player.setDeltaMovement(delta.add(
                look.x * push + (look.x * target - delta.x) * pull,
                look.y * push + (look.y * target - delta.y) * pull,
                look.z * push + (look.z * target - delta.z) * pull));
        player.connection.send(new net.minecraft.network.protocol.game
                .ClientboundSetEntityMotionPacket(player));
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.5F, 1.0F);
    }

    /**
     * How fast you have to be going before the wall is the one that gives.
     *
     * Blocks per tick. Below this you are manoeuvring, and clipping a corner
     * while lining up a landing should not open a hole in it.
     */
    private static final double TUNNEL_SPEED = 0.45;
    /** Ticks between bores, so a held collision does not crater every tick. */
    private static final int TUNNEL_PERIOD = 2;

    /** Landing or leaving dragon form ends the glide; walls get their answer. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!GLIDING.contains(player.getUUID())) {
                continue;
            }
            if (player.onGround() || player.isPassenger() || !DragonFormManager.isDragon(player)) {
                GLIDING.remove(player.getUUID());
                player.stopFallFlying();
                continue;
            }
            tunnel(server, player);
        }
    }

    /**
     * Fly into a mountain and come out the other side.
     *
     * The damage for hitting a wall is already gone — a dragon does not bruise
     * on scenery — so without this you simply stop, which is its own kind of
     * wrong. With Explosive Intent armed the wall goes instead, using the same
     * crater the punch throws: a sphere, obsidian and end stone excepted, and
     * everything dropped rather than vaporised.
     *
     * Bored a little ahead of the eyes rather than at them, or the first bore
     * would open inside your own head and the next tick would find the hole
     * already there and stop.
     */
    private static void tunnel(MinecraftServer server, ServerPlayer player) {
        if (!player.horizontalCollision || !DragonAbilities.craterArmed(player)) {
            return;
        }
        if (server.getTickCount() % TUNNEL_PERIOD != 0) {
            return;
        }
        Vec3 delta = player.getDeltaMovement();
        // Horizontal speed only: dropping onto a roof at terminal velocity is
        // not flying into it.
        if (Math.sqrt(delta.x * delta.x + delta.z * delta.z) < TUNNEL_SPEED) {
            return;
        }
        Vec3 ahead = player.getEyePosition().add(player.getLookAngle().normalize().scale(2.5));
        DragonAbilities.crater(player, net.minecraft.core.BlockPos.containing(ahead));
    }
}
