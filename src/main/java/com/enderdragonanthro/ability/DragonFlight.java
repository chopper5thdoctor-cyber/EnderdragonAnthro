package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
     * How far past your own body the tunnel is cut.
     *
     * The real dragon has no margin — it clears exactly its own bounding box.
     * A little more here, because the box is the player's and the wings are
     * drawn well outside it, so a bore that matched it exactly would fit the
     * body through a hole the wings were still buried in.
     */
    private static final double TUNNEL_MARGIN = 1.0;

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
     * This is EnderDragon.checkWalls, which is the whole of how the real one
     * tunnels: every tick it walks the blocks inside its own bounding box and
     * clears them. No collision test, no speed threshold, no aiming — it does
     * not ram anything, it simply occupies space that had blocks in it and the
     * blocks lose. That is why the dragon's flight looks unbothered by terrain
     * rather than like something smashing through it.
     *
     * Its two rules are kept exactly. BlockTags.DRAGON_TRANSPARENT is skipped
     * without being broken — vanilla's list of things not worth noticing.
     * BlockTags.DRAGON_IMMUNE survives outright, which is what keeps obsidian,
     * bedrock, barriers and the End's own furniture standing. And it obeys
     * mobGriefing, because a rule that turns off every other block-eating mob
     * has no business making an exception for this one.
     *
     * Nothing drops. The dragon does not mine, and a tunnel through a mountain
     * that carpeted itself in falling stone would be its own kind of problem.
     */
    private static void tunnel(MinecraftServer server, ServerPlayer player) {
        if (!DragonAbilities.craterArmed(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(TUNNEL_MARGIN);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(BlockTags.DRAGON_TRANSPARENT)
                    || state.is(BlockTags.DRAGON_IMMUNE)) {
                continue;
            }
            level.removeBlock(pos, false);
        }
    }
}
