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
     * The speed a wingbeat pulls toward, unarmed.
     *
     * The firework rocket uses 0.1 push toward 1.5 and applies half the pull;
     * these are wings, so they beat it — 0.2 push toward 2.6, all of the pull.
     * Sustained, that settles around 2.7 a tick, which is 54 blocks a second.
     */
    private static final double TARGET = 2.6;

    /**
     * ...and armed.
     *
     * A boost is a target rather than a force: it pulls your velocity toward a
     * speed and then stops mattering, which is why even this beat, much harder
     * than a firework's, settles below terminal velocity. Falling has no target
     * — 0.08 a tick, forever, balanced against drag at 3.92 — so anything that
     * merely converges cannot beat a stone dropped beside it.
     *
     * Raising the target is the only way past that, and 4.5 is chosen to clear
     * free fall even when the beat is lazy: 4.49 a tick beating every five
     * ticks, and still 4.28 at every ten, against falling's 3.92. Ninety blocks
     * a second, and the first speed in this mod that outruns gravity.
     *
     * The risk is not bolted on, it is what ninety blocks a second already
     * costs. Terrain arrives faster than the server sends it. And the tunnel
     * has exceptions — bedrock, obsidian, crying obsidian, end stone, iron
     * bars, barriers, the End's portal furniture and command blocks are all
     * BlockTags.DRAGON_IMMUNE — so the one thing that will not move is the one
     * thing you meet at full speed.
     */
    private static final double ARMED_TARGET = 4.5;

    /** A wingbeat, harder with Explosive Intent armed. */
    public static void boost(ServerPlayer player) {
        if (!player.isFallFlying()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 delta = player.getDeltaMovement();
        double push = 0.2, pull = 0.75;
        double target = DragonIntent.tunnels(player) ? ARMED_TARGET : TARGET;
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

    /**
     * How fast counts as flying into something rather than being near it.
     *
     * Vanilla's own number, not a chosen one. LivingEntity.travel bills a
     * gliding player for hitting a wall as {@code (speedLost * 10 - 3)}, so a
     * collision costs nothing at all until the speed it took off you passes
     * 0.3 a tick — and a wall you hit takes all of it. Below that, vanilla says
     * you brushed the wall; above it, vanilla says you flew into it.
     *
     * Borrowing the threshold rather than picking one means the rule is exactly
     * "if it would have hurt, the wall loses instead", which cannot drift out
     * of step with the damage it stands in for.
     *
     * Horizontal, as vanilla measures it. Coming straight down is a different
     * question with a different answer, which DIVE_SPEED below handles.
     */
    private static final double TUNNEL_SPEED = 3.0 / 10.0;

    /**
     * How fast down counts as a dive rather than a glide.
     *
     * You cannot outfly falling. Point straight down and the elytra's lift term
     * is {@code g * (-1 + cos²(pitch) * 0.75)}, whose cosine is zero at ninety
     * degrees — so a vertical dive is exactly free fall, 3.92 a tick, and no
     * more. There is no speed at which flying down beats dropping.
     *
     * What there is instead is the angle. Sink rate at terminal, by pitch:
     * level 0.98, thirty degrees 1.71, forty-five 2.45, sixty 3.18, straight
     * down 3.92. A level glide is already falling at nearly one a tick, which
     * is why "descending fast" cannot be the test — it would bore the ground
     * out from under an ordinary approach.
     *
     * So the cut is the forty-five degree terminal. Steeper than that and you
     * are diving; shallower and you are landing. It is a real number off the
     * same curve rather than a chosen one, and it sits clear of both ends.
     */
    private static final double DIVE_SPEED = 2.45;

    /** Ticks of travel cleared in front of you, so the move lands in air. */
    private static final double LOOKAHEAD = 2.0;
    /** Extra width per block/tick of speed, so the hole outgrows the hitbox. */
    private static final double WIDEN = 0.5;
    /**
     * How far the rounded bore reaches past the body it carries.
     *
     * Three, not one. At one the round bore was smaller than the box it
     * replaced exactly where that hurt: 80% of its volume at full speed, with
     * the missing quarter all in the corners of the swept region — which is
     * the space you move into when you turn. Slow flight never noticed,
     * because with a short sweep the rounding dominates and the round shape is
     * the bigger of the two; fast flight noticed immediately.
     */
    private static final double ROUNDING = 3.0;

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
        if (!DragonIntent.tunnels(player)) {
            return;
        }
        Vec3 delta = player.getDeltaMovement();
        // Flying into it, or dropping onto it. Either counts; drifting past it
        // on the way to a landing does not.
        if (delta.horizontalDistance() < TUNNEL_SPEED && -delta.y < DIVE_SPEED) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        // Cleared AHEAD of the move, and wider than the body.
        //
        // The real dragon never has this problem, and destruction is not why:
        // EnderDragon sets noPhysics, so it does not collide with blocks at
        // all. checkWalls is cleanup behind a thing that was already passing
        // through. It also runs that check on its head and neck hitboxes,
        // which lead the body by most of a neck, so what little it does clear
        // is cleared before the body reaches it.
        //
        // A player collides for real, so the only way to get the same result
        // is to be finished before the move happens. At 4.5 a tick a box that
        // hugs the body is four blocks behind the problem -- which is exactly
        // why spamming space stopped working, and why the margin is scaled by
        // speed rather than fixed.
        double speed = delta.length();
        AABB swept = player.getBoundingBox()
                .inflate(TUNNEL_MARGIN + speed * WIDEN)
                .expandTowards(delta.scale(LOOKAHEAD));
        // A capsule along the path, NOT one ellipsoid over the swept box.
        //
        // That was the mistake, and it made collisions worse rather than
        // better. Sweeping moves the box's far face forward, so its centre
        // ends up half the sweep ahead of you -- and an ellipsoid centred
        // there tapers to a point at its back end, which is exactly where your
        // body is. At speed the bore was pinched to nothing around the player
        // and only opened up several blocks ahead.
        //
        // A radius carried along the segment you are about to travel has no
        // back end to be caught in. Full width at your own position, full
        // width the whole way forward, and round instead of square.
        Vec3 from = player.getBoundingBox().getCenter();
        Vec3 to = from.add(delta.scale(LOOKAHEAD));
        AABB body = player.getBoundingBox();
        double rx = body.getXsize() / 2.0 + TUNNEL_MARGIN + speed * WIDEN + ROUNDING;
        double ry = body.getYsize() / 2.0 + TUNNEL_MARGIN + speed * WIDEN + ROUNDING;
        double rz = body.getZsize() / 2.0 + TUNNEL_MARGIN + speed * WIDEN + ROUNDING;
        AABB box = new AABB(from, to).inflate(Math.max(rx, Math.max(ry, rz)));
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            // Distance to the path, measured in units of the radius on each
            // axis, so the round cross-section is as tall as the dragon and as
            // wide as its wings rather than a compromise between them.
            double px = (pos.getX() + 0.5 - from.x) / rx;
            double py = (pos.getY() + 0.5 - from.y) / ry;
            double pz = (pos.getZ() + 0.5 - from.z) / rz;
            double ax = (to.x - from.x) / rx;
            double ay = (to.y - from.y) / ry;
            double az = (to.z - from.z) / rz;
            double len = ax * ax + ay * ay + az * az;
            double t = len <= 1.0e-6 ? 0.0
                    : Math.max(0.0, Math.min(1.0, (px * ax + py * ay + pz * az) / len));
            double dx = px - ax * t;
            double dy = py - ay * t;
            double dz = pz - az * t;
            if (dx * dx + dy * dy + dz * dz > 1.0) {
                continue;                                  // outside the capsule
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(BlockTags.DRAGON_TRANSPARENT)
                    || state.is(BlockTags.DRAGON_IMMUNE)) {
                continue;
            }
            level.removeBlock(pos, false);
        }
    }
}
