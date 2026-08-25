package com.enderdragonanthro.client;

import com.enderdragonanthro.network.DragonBurnPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Which entities are burning with ours, so their flames draw purple.
 *
 * The thing this gets wrong if you are not careful is that it is a second clock
 * for something that already has one, and the two do not run at the same speed.
 *
 * It used to count the burn down itself: the server said "300 ticks" and this
 * counted 300 client ticks from the packet. Both numbers were 300 and the burn
 * still finished orange, because ClientTickEvents.END_CLIENT_TICK fires from
 * Minecraft.tick() whether or not the world is running. Open the pause menu for
 * five seconds and this clock spends a hundred ticks while the entity's
 * remainingFireTicks spends none. The same thing happens more slowly any time
 * the integrated server falls behind. The mark ran out first, the flame carried
 * on, and the last few seconds of every long burn came out vanilla orange —
 * which is exactly what it looked like on an iron golem.
 *
 * So the countdown is gone. The fire is its own clock: the mark is held for as
 * long as the entity is seen burning, and isOnFire() is synced from the server,
 * so purple ends when the flame ends by construction rather than by arithmetic.
 *
 * That has to be a renewal and not just a longer deadline, because there is no
 * number long enough. Standing in dragonfire tops an entity back up to 160 ticks
 * every tick it is inside — measured, not assumed — so a burn lasts as long as
 * the flames do, which is minutes. Any fixed horizon is a cap, and a burn that
 * outlives it comes out orange at the far end. That is what "it still goes
 * orange if it lasts long enough" was.
 *
 * The remaining deadline only covers an entity that has stopped answering, and
 * it is counted on END_WORLD_TICK — which Fabric fires from
 * ClientLevel.tickEntities, the same call the pause menu stops. Two clocks that
 * stop together can be compared.
 */
public final class DragonBurnClient {
    /** Entity id -> the tick after which the mark is stale even if still lit. */
    private static final Map<Integer, Long> MARKED = new HashMap<>();
    private static long ticks;

    /**
     * How long a mark outlives the last sighting of the fire it is about.
     *
     * Not the length of the burn. The burn has no length here any more — the
     * sweep below pushes this deadline forward every time it sees the entity
     * still alight, so a mark lasts exactly as long as the flame does however
     * long that turns out to be. Standing in dragonfire tops an entity back up
     * to 160 ticks EVERY tick it is inside, so "however long" is genuinely
     * unbounded, and any fixed horizon here would be a cap that a long enough
     * burn walks straight through and out the orange side.
     *
     * What it is instead: how long to keep believing a mark for an entity that
     * has stopped answering — one that went out of range, or unloaded, or blinked
     * between two entity-data updates. Two seconds is longer than any of those
     * and far shorter than a burn.
     */
    private static final int GRACE = 40;

    /** How often to check the marks against the entities they are about. */
    private static final int SWEEP = 10;

    private DragonBurnClient() {
    }

    /**
     * Called on the world tick, not the client tick.
     *
     * The distinction is half the fix: this one stops when the world does. The
     * other half is that it renews rather than only expiring.
     */
    public static void tick(ClientLevel level) {
        ticks++;
        if (ticks % SWEEP != 0) {
            return;
        }
        // An entity that went out off-screen never reaches isBurning, so marks
        // have to be checked against the world rather than only when something
        // asks to draw one. Otherwise the id lingers and the next entity to
        // inherit it catches fire looking like ours.
        MARKED.entrySet().removeIf(mark -> {
            Entity entity = level.getEntity(mark.getKey());
            if (entity != null && entity.isOnFire()) {
                mark.setValue(ticks + GRACE);   // still alight: still ours
                return false;
            }
            return mark.getValue() < ticks;
        });
    }

    public static void accept(DragonBurnPayload payload) {
        MARKED.put(payload.entityId(), ticks + payload.ticks() + GRACE);
    }

    public static void clear() {
        MARKED.clear();
        ticks = 0;
    }

    /**
     * Whether this one is burning with ours.
     *
     * Falls false the moment the entity stops burning at all, so a burn cut
     * short by water or a death draws nothing of ours.
     *
     * It reports and does not decide. Dropping the mark here on a single false
     * reading was tempting and wrong: this runs per frame off synced entity
     * data, so one late update between two packets would retire a mark
     * permanently and turn the rest of a live burn orange — the exact failure
     * the rewrite was for. Retiring is the sweep's job, because the sweep is
     * the one with a grace window.
     */
    public static boolean isBurning(Entity entity) {
        if (!entity.isOnFire()) {
            return false;
        }
        Long until = MARKED.get(entity.getId());
        return until != null && until > ticks;
    }
}
