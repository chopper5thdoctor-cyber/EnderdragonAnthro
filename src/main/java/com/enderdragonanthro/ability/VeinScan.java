package com.enderdragonanthro.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finding a seam, the way a miner would.
 *
 * A shade sent to Collect no longer wanders the world hoping to bump into
 * something — it searches outward from where it was standing and takes a vein.
 * Scattering a stack's worth of blocks out of a hundred-block radius would
 * leave the landscape looking chewed; a vein comes out in one piece.
 */
public final class VeinScan {
    /** How far out a shade will look, horizontally. Vertically it takes the lot. */
    public static final int RADIUS = 100;
    /** One stack, and no more, however rich the seam turns out to be. */
    public static final int HAUL = 64;
    /**
     * Enough candidates to pick a good vein from without holding the whole
     * world in a list — a hundred-block radius of stone would be millions.
     */
    private static final int CANDIDATE_CAP = 4096;

    private VeinScan() {
    }

    /** The chunk columns within reach, nearest first, so it digs close by. */
    public static List<ChunkPos> route(ChunkPos centre) {
        int reach = (RADIUS >> 4) + 1;
        List<ChunkPos> out = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                out.add(new ChunkPos(centre.x + dx, centre.z + dz));
            }
        }
        out.sort(Comparator.comparingInt(c ->
                (c.x - centre.x) * (c.x - centre.x) + (c.z - centre.z) * (c.z - centre.z)));
        return out;
    }

    /**
     * Every block of {@code quarry} in one chunk column, top to bottom.
     *
     * Sections whose palette does not mention the block are skipped outright,
     * which is what makes a full-height search of a hundred-block radius
     * affordable at all: most of the world is stone and air, and those sections
     * are rejected without a single block lookup.
     */
    public static void scanChunk(ServerLevel level, ChunkPos at, Block quarry, List<BlockPos> out) {
        if (out.size() >= CANDIDATE_CAP) {
            return;
        }
        LevelChunk chunk = level.getChunk(at.x, at.z);
        LevelChunkSection[] sections = chunk.getSections();
        int minSection = level.getMinSection();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(quarry))) {
                continue;
            }
            int baseY = (minSection + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!section.getBlockState(x, y, z).is(quarry)) {
                            continue;
                        }
                        out.add(new BlockPos(at.getMinBlockX() + x, baseY + y,
                                at.getMinBlockZ() + z));
                        if (out.size() >= CANDIDATE_CAP) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Take up to {@code budget} blocks, whole veins at a time.
     *
     * Starts at whatever is nearest and floods outward through touching blocks
     * of the same kind — diagonals included, since ore blobs are not
     * rectilinear. If a vein runs out before the budget does it moves to the
     * next nearest and starts again, so a stack of coal can come from three
     * seams rather than one impossible one.
     */
    public static List<BlockPos> veins(List<BlockPos> candidates, BlockPos from, int budget) {
        Set<BlockPos> left = new HashSet<>(candidates);
        List<BlockPos> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(p -> p.distSqr(from)));

        List<BlockPos> taken = new ArrayList<>();
        for (BlockPos seed : sorted) {
            if (taken.size() >= budget) {
                break;
            }
            if (!left.remove(seed)) {
                continue;                       // already part of a vein we took
            }
            Deque<BlockPos> edge = new ArrayDeque<>();
            edge.add(seed);
            taken.add(seed);
            while (!edge.isEmpty() && taken.size() < budget) {
                BlockPos here = edge.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0 || taken.size() >= budget) {
                                continue;
                            }
                            BlockPos next = here.offset(dx, dy, dz);
                            if (left.remove(next)) {
                                edge.add(next);
                                taken.add(next);
                            }
                        }
                    }
                }
            }
        }
        return taken;
    }
}
