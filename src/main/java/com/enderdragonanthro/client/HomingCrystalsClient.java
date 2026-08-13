package com.enderdragonanthro.client;

import com.enderdragonanthro.network.HomingCrystalPayload;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Set;

/** The client's copy of "which crystals are anchors", refreshed once a second. */
public final class HomingCrystalsClient {
    private static Set<Integer> ids = Set.of();

    private HomingCrystalsClient() {
    }

    public static void accept(HomingCrystalPayload payload) {
        ids = new HashSet<>(payload.ids());
    }

    public static void clear() {
        ids = Set.of();
    }

    public static boolean isHoming(Entity crystal) {
        return ids.contains(crystal.getId());
    }
}
