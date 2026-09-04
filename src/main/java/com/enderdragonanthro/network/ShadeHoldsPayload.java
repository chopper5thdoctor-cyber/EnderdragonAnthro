package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * "This shade is holding this block." Zero for an empty hand.
 *
 * A vanilla enderman already has a carried block, and this is deliberately not
 * it. That one is drawn by CarriedBlockLayer in front of the chest with both
 * hands, and it is what a shade hauling a haul home is using; a shade BUILDING
 * has one block in one hand and is about to put it down. Reusing the carried
 * block would also have started the CARRY clip, which lifts both arms and is
 * the wrong pose for somebody laying a course of obsidian.
 *
 * The state id is Block.getId, which is the same numbering vanilla puts on the
 * wire everywhere else.
 */
public record ShadeHoldsPayload(int entityId, int blockStateId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShadeHoldsPayload> TYPE =
            new CustomPacketPayload.Type<>(id("shade_holds"));

    public static final StreamCodec<FriendlyByteBuf, ShadeHoldsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(payload.blockStateId());
            },
            buf -> new ShadeHoldsPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
