package com.enderdragonanthro.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * The court roster, sent to the owner so the command screen has something to
 * show. {@code open} is set when the packet is the answer to the command key,
 * which is what actually pops the screen; otherwise it is a quiet refresh for
 * a screen that is already up.
 */
public record ShadeStatePayload(boolean open, List<Entry> shades) implements CustomPacketPayload {
    /** One shade. {@code quarry} is a block id, or empty for "whatever it finds". */
    public record Entry(int slot, String name, int order, String quarry, int cargo) {
    }

    public static final CustomPacketPayload.Type<ShadeStatePayload> TYPE =
            new CustomPacketPayload.Type<>(id("shade_state"));

    public static final StreamCodec<FriendlyByteBuf, ShadeStatePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.open());
                buf.writeVarInt(payload.shades().size());
                for (Entry entry : payload.shades()) {
                    buf.writeVarInt(entry.slot());
                    buf.writeUtf(entry.name(), 64);
                    buf.writeVarInt(entry.order());
                    buf.writeUtf(entry.quarry(), 128);
                    buf.writeVarInt(entry.cargo());
                }
            },
            buf -> {
                boolean open = buf.readBoolean();
                int count = buf.readVarInt();
                List<Entry> shades = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    shades.add(new Entry(buf.readVarInt(), buf.readUtf(64),
                            buf.readVarInt(), buf.readUtf(128), buf.readVarInt()));
                }
                return new ShadeStatePayload(open, List.copyOf(shades));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
