package com.enderdragonanthro.client;

import com.enderdragonanthro.network.EndermanHappyPayload;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/** How long each enderman keeps its ^^ face, counted in client ticks. */
public final class EndermanHappyClient {
    private static final Map<Integer, Long> UNTIL = new HashMap<>();
    private static long ticks;

    private EndermanHappyClient() {
    }

    public static void tick() {
        ticks++;
        if (ticks % 100 == 0) {
            UNTIL.values().removeIf(until -> until < ticks);
        }
    }

    public static void accept(EndermanHappyPayload payload) {
        UNTIL.put(payload.entityId(), ticks + payload.ticks());
    }

    public static void clear() {
        UNTIL.clear();
    }

    public static boolean isHappy(Entity enderman) {
        Long until = UNTIL.get(enderman.getId());
        return until != null && until > ticks;
    }
}
