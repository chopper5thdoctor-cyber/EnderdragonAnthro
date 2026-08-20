package com.enderdragonanthro.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * Dragon Sight: whether it is open, and what it has found.
 *
 * The tint, the brightness and the health readouts are all things the client
 * can work out for itself once it knows the sight is open. The portals are not
 * — they are blocks, most of them outside the render distance and some in
 * chunks this client will never load, so the search has to happen on the
 * server and the answers have to be sent.
 */
public record DragonSightPayload(boolean active, List<BlockPos> gateways, List<BlockPos> end)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DragonSightPayload> TYPE =
            new CustomPacketPayload.Type<>(id("dragon_sight"));

    public static final StreamCodec<FriendlyByteBuf, DragonSightPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.active());
                write(buf, payload.gateways());
                write(buf, payload.end());
            },
            buf -> new DragonSightPayload(buf.readBoolean(), read(buf), read(buf)));

    private static void write(FriendlyByteBuf buf, List<BlockPos> positions) {
        buf.writeVarInt(positions.size());
        for (BlockPos pos : positions) {
            buf.writeBlockPos(pos);
        }
    }

    private static List<BlockPos> read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buf.readBlockPos());
        }
        return List.copyOf(positions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
