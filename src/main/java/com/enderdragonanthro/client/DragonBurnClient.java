package com.enderdragonanthro.client;

import com.enderdragonanthro.network.DragonBurnPayload;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/** How long each entity keeps burning purple, in client ticks. */
public final class DragonBurnClient {
    private static final Map<Integer, Long> UNTIL = new HashMap<>();
    private static long ticks;

    private DragonBurnClient() {
    }

    public static void tick() {
        ticks++;
        if (ticks % 100 == 0) {
            UNTIL.values().removeIf(until -> until < ticks);
        }
    }

    public static void accept(DragonBurnPayload payload) {
        UNTIL.put(payload.entityId(), ticks + payload.ticks());
    }

    public static void clear() {
        UNTIL.clear();
    }

    /**
     * Whether this one is burning with ours.
     *
     * Falls false the moment the entity stops burning at all, so a burn cut
     * short by water or a death does not leave the mark behind on whatever
     * reuses the id.
     */
    public static boolean isBurning(Entity entity) {
        if (!entity.isOnFire()) {
            return false;
        }
        Long until = UNTIL.get(entity.getId());
        return until != null && until > ticks;
    }
}
