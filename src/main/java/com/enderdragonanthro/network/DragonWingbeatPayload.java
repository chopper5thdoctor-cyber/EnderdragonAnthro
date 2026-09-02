package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * "That dragon just beat its wings."
 *
 * The beat is a client-side animation started by a keypress, which is exactly
 * right for the player who pressed the key and no use at all to anyone else:
 * nothing about a wingbeat is entity state, so nothing syncs it on its own. A
 * second player saw a dragon boost past with its wings held perfectly still.
 *
 * Sent to viewers rather than to the flier, for the same reason
 * {@link DragonBurnPayload} is: the animation is drawn by whoever is looking.
 */
public record DragonWingbeatPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DragonWingbeatPayload> TYPE =
            new CustomPacketPayload.Type<>(id("dragon_wingbeat"));

    public static final StreamCodec<FriendlyByteBuf, DragonWingbeatPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.entityId()),
                    buf -> new DragonWingbeatPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
