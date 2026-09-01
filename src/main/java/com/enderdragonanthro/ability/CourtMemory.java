package com.enderdragonanthro.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Which shades stand for whom, kept in the save they stand in.
 *
 * The court used to live only in a static map. A static map is per *process*,
 * not per world, and singleplayer runs the server inside the client — so
 * quitting to the title screen and loading a different save left the whole
 * roster in place. Summoning in the second world was told slot 0 was taken and
 * handed you Keshanne, while Vaelle was still standing in the first one. The
 * two saves were sharing a court.
 *
 * That map is now a cache of this, and this is written into the overworld's
 * data folder. On the overworld rather than per dimension, deliberately: a
 * court is one thing whose members happen to be scattered, and the whole point
 * of {@code dimension} on a seat is that a shade left in the Nether is still
 * yours.
 *
 * ## What is kept, and what is not
 *
 * Only what cannot be worked out again: who, which slot, what she was told to
 * do, and where she was last seen. Chunk tickets, half-built gates and errands
 * in progress are all deliberately dropped — none of them survive a shutdown in
 * any useful state, and a half-built gate reloaded at block 31 of 44 would be a
 * shade standing in the air finishing a frame nobody asked for.
 *
 * {@code lastPos} and {@code dimension} are the ones that earn their place. A
 * shade in an unloaded chunk cannot be looked up by UUID, so without a place to
 * load first she is not merely missing, she is unfindable — and the next summon
 * would put a second one of her in the world.
 */
public final class CourtMemory extends SavedData {
    private static final String NAME = "enderdragonanthro_court";

    /** One shade's place in the roll, as much of it as outlives a shutdown. */
    public record Seat(int slot, UUID entity, ResourceKey<Level> dimension,
                       BlockPos lastPos, String order, String wanted) {
    }

    private final Map<UUID, List<Seat>> courts = new HashMap<>();

    public static CourtMemory of(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CourtMemory::new, CourtMemory::load, DataFixTypes.LEVEL),
                NAME);
    }

    private static CourtMemory load(CompoundTag tag, HolderLookup.Provider registries) {
        CourtMemory memory = new CourtMemory();
        ListTag owners = tag.getList("Courts", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag one = owners.getCompound(i);
            if (!one.hasUUID("Owner")) {
                continue;
            }
            List<Seat> seats = new ArrayList<>();
            ListTag stored = one.getList("Seats", Tag.TAG_COMPOUND);
            for (int s = 0; s < stored.size(); s++) {
                Seat seat = readSeat(stored.getCompound(s));
                if (seat != null) {
                    seats.add(seat);
                }
            }
            if (!seats.isEmpty()) {
                memory.courts.put(one.getUUID("Owner"), seats);
            }
        }
        return memory;
    }

    /**
     * One seat, or null for one we cannot make sense of.
     *
     * A seat with no slot is not a seat, and a dimension that no longer exists —
     * a datapack removed between sessions — would otherwise hand back a
     * ResourceKey that resolves to no level and read as "she is somewhere I
     * cannot reach" forever.
     */
    private static Seat readSeat(CompoundTag tag) {
        if (!tag.contains("Slot")) {
            return null;
        }
        UUID entity = tag.hasUUID("Entity") ? tag.getUUID("Entity") : null;
        ResourceKey<Level> dimension = null;
        if (tag.contains("Dim")) {
            ResourceLocation where = ResourceLocation.tryParse(tag.getString("Dim"));
            if (where != null) {
                dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, where);
            }
        }
        BlockPos lastPos = tag.contains("At") ? BlockPos.of(tag.getLong("At")) : null;
        String order = tag.contains("Order") ? tag.getString("Order") : null;
        String wanted = tag.contains("Wanted") ? tag.getString("Wanted") : null;
        return new Seat(tag.getInt("Slot"), entity, dimension, lastPos, order, wanted);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, List<Seat>> entry : this.courts.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Owner", entry.getKey());
            ListTag seats = new ListTag();
            for (Seat seat : entry.getValue()) {
                CompoundTag stored = new CompoundTag();
                stored.putInt("Slot", seat.slot());
                if (seat.entity() != null) {
                    stored.putUUID("Entity", seat.entity());
                }
                if (seat.dimension() != null) {
                    stored.putString("Dim", seat.dimension().location().toString());
                }
                if (seat.lastPos() != null) {
                    stored.putLong("At", seat.lastPos().asLong());
                }
                if (seat.order() != null) {
                    stored.putString("Order", seat.order());
                }
                if (seat.wanted() != null) {
                    stored.putString("Wanted", seat.wanted());
                }
                seats.add(stored);
            }
            one.put("Seats", seats);
            owners.add(one);
        }
        tag.put("Courts", owners);
        return tag;
    }

    /** Everyone this save knows about, for rebuilding the roll on startup. */
    public Map<UUID, List<Seat>> everyCourt() {
        return this.courts;
    }

    /**
     * Take down what the court looks like now, if it has changed.
     *
     * Called on a pulse rather than from each place that changes the roll,
     * because there are a dozen of those and the thirteenth would be added
     * without this one — the same reason the injection audit exists. Comparing
     * before writing keeps a pulse from marking the file dirty sixty times a
     * minute for a court that has been standing still.
     */
    public void remember(UUID owner, List<Seat> seats) {
        if (seats.isEmpty()) {
            if (this.courts.remove(owner) != null) {
                setDirty();
            }
            return;
        }
        if (!Objects.equals(this.courts.get(owner), seats)) {
            this.courts.put(owner, List.copyOf(seats));
            setDirty();
        }
    }
}
