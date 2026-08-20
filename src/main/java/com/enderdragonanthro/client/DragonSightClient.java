package com.enderdragonanthro.client;

import com.enderdragonanthro.network.DragonSightPayload;
import net.minecraft.core.BlockPos;

import java.util.List;

/** The client's copy of what the sight is showing. */
public final class DragonSightClient {
    /**
     * The darker of the two colours painted on the dragon's eyes.
     *
     * The sheet has exactly two: #E079FA and this. Taking the darker one means
     * the world is seen through the same violet the eyes are, rather than
     * through a purple picked to look nice over grass.
     */
    public static final int EYE = 0xCC00FA;

    private static boolean active;
    private static List<BlockPos> gateways = List.of();
    private static List<BlockPos> end = List.of();

    private DragonSightClient() {
    }

    public static void accept(DragonSightPayload payload) {
        active = payload.active();
        gateways = payload.gateways();
        end = payload.end();
    }

    public static void clear() {
        active = false;
        gateways = List.of();
        end = List.of();
    }

    public static boolean isOpen() {
        return active;
    }

    public static List<BlockPos> gateways() {
        return gateways;
    }

    public static List<BlockPos> end() {
        return end;
    }
}
