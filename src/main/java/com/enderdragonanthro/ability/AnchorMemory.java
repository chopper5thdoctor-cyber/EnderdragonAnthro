package com.enderdragonanthro.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Where each dragon's way home is, kept in the save it stands in.
 *
 * The same reasoning as {@link CourtMemory}, and the same shape. An anchor is
 * meant to outlive a session — that is the entire point of one — and the map
 * that held it was static, which is per process rather than per world. It is
 * cleared on shutdown now, so it has to be written down or logging out would
 * lose the way home.
 *
 * What is kept is what cannot be worked out again: which crystal, which world,
 * and where it stands. That last one is not decoration — a crystal in an
 * unloaded chunk cannot be looked up by UUID, so without a place to load first
 * it is not merely missing, it is unfindable.
 *
 * The lockout is deliberately NOT kept. It is stamped with {@code getGameTime()},
 * which is per world, so carrying it across would either expire instantly or
 * lock you out for the age of the older save.
 */
public final class AnchorMemory extends SavedData {
    private static final String NAME = "enderdragonanthro_anchors";

    /** One dragon's anchor, as much of it as outlives a shutdown. */
    public record Kept(UUID entity, ResourceKey<Level> dimension, BlockPos pos) {
    }

    private final Map<UUID, Kept> anchors = new HashMap<>();

    public static AnchorMemory of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AnchorMemory::new, AnchorMemory::load, DataFixTypes.LEVEL),
                NAME);
    }

    private static AnchorMemory load(CompoundTag tag, HolderLookup.Provider registries) {
        AnchorMemory memory = new AnchorMemory();
        ListTag stored = tag.getList("Anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag one = stored.getCompound(i);
            if (!one.hasUUID("Owner") || !one.hasUUID("Entity") || !one.contains("At")) {
                continue;
            }
            ResourceKey<Level> dimension = null;
            if (one.contains("Dim")) {
                ResourceLocation where = ResourceLocation.tryParse(one.getString("Dim"));
                if (where != null) {
                    dimension = ResourceKey.create(Registries.DIMENSION, where);
                }
            }
            memory.anchors.put(one.getUUID("Owner"), new Kept(one.getUUID("Entity"),
                    dimension, BlockPos.of(one.getLong("At"))));
        }
        return memory;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag stored = new ListTag();
        for (Map.Entry<UUID, Kept> entry : this.anchors.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Owner", entry.getKey());
            one.putUUID("Entity", entry.getValue().entity());
            if (entry.getValue().dimension() != null) {
                one.putString("Dim", entry.getValue().dimension().location().toString());
            }
            one.putLong("At", entry.getValue().pos().asLong());
            stored.add(one);
        }
        tag.put("Anchors", stored);
        return tag;
    }

    public Map<UUID, Kept> everyAnchor() {
        return this.anchors;
    }

    /** Write one down, or rub it out, only when it has actually changed. */
    public void remember(UUID owner, Kept anchor) {
        if (anchor == null) {
            if (this.anchors.remove(owner) != null) {
                setDirty();
            }
            return;
        }
        if (!Objects.equals(this.anchors.get(owner), anchor)) {
            this.anchors.put(owner, anchor);
            setDirty();
        }
    }
}
