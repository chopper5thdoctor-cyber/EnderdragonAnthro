package com.enderdragonanthro.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every doorway this world has shown us, kept.
 *
 * The sight used to answer the portal question by scanning blocks, and blocks
 * only exist in loaded chunks — so a gate stopped being a fact the moment you
 * flew far enough for its chunk to close, which in the Nether is about fifty
 * metres. That is precisely backwards for what the mark is for: you need the
 * bearing home most when home is far away and behind you.
 *
 * So the scan is now a way of *learning* portals rather than of listing them.
 * Anything seen once is written down, and the mark points at what is written
 * down. A gate is a wall of obsidian that somebody deliberately lit; forgetting
 * it because you walked away is the wrong model of the world.
 *
 * Kept per dimension, in that dimension's own save folder, because the
 * coordinates mean different places on either side of a portal and mixing them
 * would point you into a wall.
 */
public final class GateMemory extends SavedData {
    private static final String NAME = "enderdragonanthro_gates";
    /**
     * Portal blocks this close to a remembered one are the same portal.
     *
     * Wider than it sounds it needs to be, because a gate cut for a dragon is
     * ten blocks of interior from sill to lintel — a radius of eight would file
     * the top and the bottom of one doorway as two.
     */
    private static final int SAME_GATE = 12;
    /** More than this and the oldest is dropped; nobody needs 257 doors. */
    private static final int CAP = 256;

    private final List<BlockPos> gates = new ArrayList<>();

    public static GateMemory of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GateMemory::new, GateMemory::load, DataFixTypes.LEVEL),
                NAME);
    }

    private static GateMemory load(CompoundTag tag, HolderLookup.Provider registries) {
        GateMemory memory = new GateMemory();
        for (long packed : tag.getLongArray("Gates")) {
            memory.gates.add(BlockPos.of(packed));
        }
        return memory;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] packed = new long[this.gates.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = this.gates.get(i).asLong();
        }
        tag.putLongArray("Gates", packed);
        return tag;
    }

    /**
     * Write down a doorway, unless we already knew about it.
     *
     * A nether portal is up to twenty-one blocks wide and as many tall, and the
     * scan reports every one of its blocks, so the dedup radius has to be
     * generous enough that one gate makes one memory rather than a constellation
     * of them.
     */
    public void remember(BlockPos pos) {
        BlockPos at = pos.immutable();
        for (BlockPos known : this.gates) {
            if (known.distSqr(at) < (double) SAME_GATE * SAME_GATE) {
                return;
            }
        }
        this.gates.add(at);
        while (this.gates.size() > CAP) {
            this.gates.remove(0);
        }
        setDirty();
    }

    /**
     * Drop the ones that have been broken, but only where we can actually see.
     *
     * The distinction matters more than it looks: a remembered gate whose chunk
     * is not loaded is not evidence of anything, and treating "cannot see it"
     * as "it is gone" would re-create exactly the fifty-metre amnesia this class
     * exists to fix. Only a loaded chunk with no portal in it is proof.
     */
    public void prune(ServerLevel level) {
        boolean changed = this.gates.removeIf(pos -> level.isLoaded(pos)
                && !level.getBlockState(pos).is(Blocks.NETHER_PORTAL));
        if (changed) {
            setDirty();
        }
    }

    /** The nearest few, closest first — the sight only draws one, but sends a margin. */
    public List<BlockPos> nearest(BlockPos from, int limit) {
        return this.gates.stream()
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(from)))
                .limit(limit)
                .toList();
    }
}
