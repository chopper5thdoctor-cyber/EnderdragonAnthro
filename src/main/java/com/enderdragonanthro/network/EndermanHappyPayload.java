package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/** "This enderman is pleased, for this many ticks." Drives the ^^ face. */
public record EndermanHappyPayload(int entityId, int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EndermanHappyPayload> TYPE =
            new CustomPacketPayload.Type<>(id("enderman_happy"));

    public static final StreamCodec<FriendlyByteBuf, EndermanHappyPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(payload.ticks());
            },
            buf -> new EndermanHappyPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
