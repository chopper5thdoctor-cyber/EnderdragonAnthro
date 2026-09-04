package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * Who each crystal is feeding, by entity id.
 *
 * A vanilla End Crystal carries exactly one beam target — an
 * {@code EntityDataAccessor<Optional<BlockPos>>} — because the fight it was
 * written for has exactly one dragon. Two dragons standing by one crystal are
 * both healed, because the healing is worked out per dragon, but both write
 * that single field and the last one wins: one beam, flicking between them.
 *
 * So the pairing is sent instead of being inferred. It is deliberately the
 * WHOLE list rather than "the extra ones": the renderer draws every beam from
 * this, and vanilla's single target is left alone to do the two jobs only it
 * can — keeping the crystal out of the frustum cull, and driving the vanilla
 * fight, where the target is a real ender dragon rather than a player and this
 * list is empty.
 *
 * ## Flattened pairs
 *
 * Sent as crystal, dragon, crystal, dragon rather than as a map, because a
 * crystal appears once per dragon it feeds and there is no key to be unique.
 * Odd-length input is a truncated packet and is dropped whole.
 */
public record CrystalBeamsPayload(List<Integer> pairs) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CrystalBeamsPayload> TYPE =
            new CustomPacketPayload.Type<>(id("crystal_beams"));

    public static final StreamCodec<FriendlyByteBuf, CrystalBeamsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.pairs().size());
                for (int entityId : payload.pairs()) {
                    buf.writeVarInt(entityId);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<Integer> pairs = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    pairs.add(buf.readVarInt());
                }
                if (pairs.size() % 2 != 0) {
                    return new CrystalBeamsPayload(List.of());
                }
                return new CrystalBeamsPayload(List.copyOf(pairs));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
