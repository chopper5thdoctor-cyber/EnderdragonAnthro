package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * One order, aimed at one shade, from the command screen. The quarry is sent
 * as typed; an empty string means "use whatever I am looking at".
 */
public record ShadeOrderPayload(int slot, int order, String quarry) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShadeOrderPayload> TYPE =
            new CustomPacketPayload.Type<>(id("shade_order"));

    public static final StreamCodec<FriendlyByteBuf, ShadeOrderPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.slot());
                buf.writeVarInt(payload.order());
                buf.writeUtf(payload.quarry(), 128);
            },
            buf -> new ShadeOrderPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
