package com.enderdragonanthro.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The chunk holds themselves, so a test can count them.
 *
 * Exists for one assertion and is worth the mixin. A shade holding a chunk is
 * invisible from every angle the game offers: the chunk stays loaded either
 * way while anything else is looking at it, so "her hold was quietly removed by
 * somebody standing next to her" reads as nothing at all until she is a
 * thousand blocks away and Recall says she cannot be found. Counting the
 * tickets is the only place that bug is visible before it costs somebody a
 * shade.
 */
@Mixin(DistanceManager.class)
public interface ChunkTicketAccessor {
    @Accessor("tickets")
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> enderdragonanthro$tickets();
}
