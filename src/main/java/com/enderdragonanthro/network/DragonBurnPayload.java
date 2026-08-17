package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * "This one is burning with dragonfire, for this long."
 *
 * Vanilla records that an entity is on fire and nothing at all about what lit
 * it, so purple flames need saying out loud. Sent to everyone who can see the
 * victim rather than only to the victim, because the flames on a burning mob
 * are drawn by whoever is looking at it.
 */
public record DragonBurnPayload(int entityId, int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DragonBurnPayload> TYPE =
            new CustomPacketPayload.Type<>(id("dragon_burn"));

    public static final StreamCodec<FriendlyByteBuf, DragonBurnPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(payload.ticks());
            },
            buf -> new DragonBurnPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
