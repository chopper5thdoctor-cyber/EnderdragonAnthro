package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * "Do I sweep things up off the floor?"
 *
 * Registered in both directions and deliberately the same record either way.
 * Going up it is a request — the button was clicked, set it to this. Coming
 * down it is the answer — this is what it is now, draw the button that way.
 * A second record would say nothing the boolean does not.
 */
public record DragonPickupPayload(boolean on) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DragonPickupPayload> TYPE =
            new CustomPacketPayload.Type<>(id("dragon_pickup"));

    public static final StreamCodec<FriendlyByteBuf, DragonPickupPayload> CODEC =
            StreamCodec.of((buf, payload) -> buf.writeBoolean(payload.on()),
                    buf -> new DragonPickupPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
