package com.enderdragonanthro.client;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One wingbeat per Boost, counted client-side.
 *
 * Nothing about a wingbeat needs the server's opinion: the press is already
 * sent as an ability, and the beat is cosmetic. Keeping it here means it starts
 * on the frame the key goes down rather than after a round trip, which for
 * something this short is the difference between a flap and a stutter.
 *
 * Keyed by player, so a beat is per dragon rather than global — several
 * transformed players in view each keep their own.
 */
public final class DragonWings {
    /** Ticks a single beat takes. Roughly the canon dragon's own cadence. */
    private static final int BEAT_TICKS = 12;

    private static final Map<UUID, Long> STARTED = new HashMap<>();
    private static long ticks;

    private DragonWings() {
    }

    public static void tick() {
        ticks++;
        if (ticks % 200 == 0) {
            STARTED.values().removeIf(at -> at + BEAT_TICKS < ticks);
        }
    }

    /**
     * Start a beat, unless one is already in flight.
     *
     * Restarting was the bug: held, Boost re-fires far faster than a beat
     * lasts, and every re-fire snapped the wings back to phase zero, so they
     * juddered instead of beating. A wingbeat is not interruptible — the wing
     * finishes its stroke and the next one starts after.
     */
    public static void beat(Player player) {
        Long at = STARTED.get(player.getUUID());
        if (at != null && ticks - at < BEAT_TICKS) {
            return;
        }
        STARTED.put(player.getUUID(), ticks);
    }

    /**
     * Where in the beat this player is, 0..1, or -1 when the wings are at rest.
     *
     * partialTick is folded in so the beat is smooth at any framerate rather
     * than stepping twelve times.
     */
    public static float phase(Player player, float partialTick) {
        Long at = STARTED.get(player.getUUID());
        if (at == null) {
            return -1.0F;
        }
        float elapsed = (ticks - at) + partialTick;
        if (elapsed < 0.0F || elapsed >= BEAT_TICKS) {
            return -1.0F;
        }
        return elapsed / BEAT_TICKS;
    }

    public static void clear() {
        STARTED.clear();
    }
}
