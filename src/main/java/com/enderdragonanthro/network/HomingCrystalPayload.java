package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * Which End Crystals in the level are anchors, by entity id.
 *
 * The server marks an anchor with a scoreboard tag, and scoreboard tags live
 * in server-side NBT — they are never sent to the client. So the renderer had
 * no way to tell an anchor from a live crystal and drew every one of them
 * vanilla magenta. This is that missing half.
 */
public record HomingCrystalPayload(List<Integer> ids) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HomingCrystalPayload> TYPE =
            new CustomPacketPayload.Type<>(id("homing_crystals"));

    public static final StreamCodec<FriendlyByteBuf, HomingCrystalPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.ids().size());
                for (int entityId : payload.ids()) {
                    buf.writeVarInt(entityId);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<Integer> ids = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ids.add(buf.readVarInt());
                }
                return new HomingCrystalPayload(List.copyOf(ids));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
