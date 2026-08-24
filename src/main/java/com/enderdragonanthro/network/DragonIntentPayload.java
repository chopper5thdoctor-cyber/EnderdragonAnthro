package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * Which stop the dial is on, so the HUD can name it.
 *
 * The dial lives on the server, because the claw it sets is an attribute and
 * the crater it permits is a server decision. The chip that names it lives on
 * the client. One byte closes the gap.
 *
 * Downward only. Cycling is already an AbilityAction going up, and a second way
 * to ask would be a second way for the two sides to disagree.
 */
public record DragonIntentPayload(int ordinal) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DragonIntentPayload> TYPE =
            new CustomPacketPayload.Type<>(id("dragon_intent"));

    public static final StreamCodec<FriendlyByteBuf, DragonIntentPayload> CODEC =
            StreamCodec.of((buf, payload) -> buf.writeVarInt(payload.ordinal()),
                    buf -> new DragonIntentPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
