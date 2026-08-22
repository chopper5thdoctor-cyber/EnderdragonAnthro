package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gates big enough for what walks through them, on both sides.
 *
 * A shade can raise one on request, which solves the near half of the problem.
 * The far half is vanilla's: PortalForcer builds the destination portal at the
 * only size it knows, a two-by-three hole, so a dragon that squeezes into a
 * wide gate arrives at a narrow one and cannot get back out. Widening what you
 * arrive in is the other half, and it is done on arrival rather than by
 * rewriting the generator, because that way it also fixes the portals that were
 * already there and too small.
 */
public final class NetherGate {
    /** How far around the arrival point to look for the portal we came out of. */
    private static final int FIND = 6;
    /** Cap on the flood fill that measures an existing portal. */
    private static final int SCAN_LIMIT = 512;

    private NetherGate() {
    }

    /**
     * Interior width a body of this size needs, with a block of clearance.
     *
     * Measured from the STANDING pose rather than from getBbWidth/getBbHeight,
     * and that is the whole of the "portal is 7x7, too small for me" bug. Those
     * two accessors report the hitbox of the pose the player is in right now,
     * and a player who is gliding is in FALL_FLYING — which is 0.6 by 0.6 before
     * scale, so a dragon eight blocks tall measured 2.67 and asked for a five by
     * five hole. Ordering a gate is something you do on the wing, so the pose at
     * the moment of asking is the one pose the size must not come from.
     */
    public static int innerFor(ServerPlayer player) {
        return size(player.getDimensions(Pose.STANDING).width(), 2, PortalShape.MAX_WIDTH);
    }

    /** ...and interior height. */
    public static int tallFor(ServerPlayer player) {
        return size(player.getDimensions(Pose.STANDING).height(), 3, PortalShape.MAX_HEIGHT);
    }

    /**
     * A body, plus a block of air on each side of it, within what will light.
     *
     * The floor is vanilla's minimum portal — two by three interior — because a
     * frame smaller than that is not a portal at all, and the ceiling is its
     * maximum, because a frame larger than that will not ignite.
     */
    private static int size(float body, int least, int most) {
        return Math.max(least, Math.min((int) Math.ceil(body) + 2, most - 2));
    }

    /**
     * Make the portal you have just stepped out of fit you.
     *
     * Called after a dimension change. Everything about this is deliberately
     * forgiving: if there is no portal nearby, if it is already big enough, or
     * if you are not a dragon, it does nothing at all.
     */
    public static void widenArrival(ServerPlayer player) {
        if (!DragonFormManager.isDragon(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos seed = findPortal(level, player.blockPosition());
        if (seed == null) {
            return;
        }
        // The door you just came out of is the door back, and it is worth
        // remembering whether or not it needs widening.
        GateMemory.of(level).remember(seed);

        List<BlockPos> interior = flood(level, seed);
        if (interior.isEmpty()) {
            return;
        }
        Direction.Axis axis = level.getBlockState(seed).getValue(NetherPortalBlock.AXIS);
        Direction across = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : interior) {
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
        }
        int haveWide = axis == Direction.Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
        int haveTall = maxY - minY + 1;
        int wantWide = innerFor(player);
        int wantTall = tallFor(player);
        if (haveWide >= wantWide && haveTall >= wantTall) {
            return;                       // already walked through something big enough
        }

        // Grown from the middle of what is there and from its floor, so the new
        // gate stands where the old one did rather than sinking into the ground.
        int growth = Math.max(0, wantWide - haveWide);
        BlockPos foot = new BlockPos(minX, minY, minZ).relative(across, -(growth / 2));
        raise(level, foot, across, Math.max(haveWide, wantWide), Math.max(haveTall, wantTall),
                true);
        level.playSound(null, foot, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.6F, 0.8F);
    }

    /**
     * Cut the frame and light it.
     *
     * {@code carve} is the difference between the two sides. In the overworld a
     * shade refuses rather than burying a gate in somebody's hillside, so the
     * caller checks the ground first. On arrival there is no refusing — you are
     * already standing there, and the Nether is solid rock — so the frame is cut
     * out of whatever is in the way.
     *
     * The ignition is vanilla's either way: BaseFireBlock.onPlace runs the
     * portal check for any fire, so only the shape here is ours.
     */
    public static void raise(ServerLevel level, BlockPos foot, Direction across,
                             int inner, int tall, boolean carve) {
        for (int w = -1; w <= inner; w++) {
            for (int h = -1; h <= tall; h++) {
                BlockPos at = foot.relative(across, w).above(h);
                boolean edge = w == -1 || w == inner || h == -1 || h == tall;
                if (!carve && !edge && !level.getBlockState(at).canBeReplaced()) {
                    continue;
                }
                level.setBlockAndUpdate(at, edge
                        ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
            }
        }
        BlockPos heart = foot.relative(across, inner / 2);
        level.setBlockAndUpdate(heart, Blocks.FIRE.defaultBlockState());
        // Written down at the moment it is cut rather than waiting for a sweep
        // to notice it, so the mark for the gate you just made is there before
        // you have flown out of range of it.
        GateMemory.of(level).remember(heart);
    }

    private static BlockPos findPortal(ServerLevel level, BlockPos around) {
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-FIND, -FIND, -FIND),
                around.offset(FIND, FIND, FIND))) {
            if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                return pos.immutable();
            }
        }
        return null;
    }

    /** Every portal block joined to this one — which is the interior, exactly. */
    private static List<BlockPos> flood(ServerLevel level, BlockPos seed) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        seen.add(seed);
        while (!queue.isEmpty() && found.size() < SCAN_LIMIT) {
            BlockPos at = queue.poll();
            BlockState state = level.getBlockState(at);
            if (!state.is(Blocks.NETHER_PORTAL)) {
                continue;
            }
            found.add(at);
            for (Direction face : Direction.values()) {
                BlockPos next = at.relative(face);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return found;
    }
}
