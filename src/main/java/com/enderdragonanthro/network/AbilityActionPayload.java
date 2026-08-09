package com.enderdragonanthro.network;

import com.enderdragonanthro.ability.AbilityAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static com.enderdragonanthro.EnderdragonAnthro.id;

public record AbilityActionPayload(AbilityAction action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AbilityActionPayload> TYPE =
            new CustomPacketPayload.Type<>(id("ability_action"));

    public static final StreamCodec<FriendlyByteBuf, AbilityActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeEnum(payload.action()),
            buf -> new AbilityActionPayload(buf.readEnum(AbilityAction.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
