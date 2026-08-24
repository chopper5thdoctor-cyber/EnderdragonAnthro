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
 * So the countdown is gone. The fire is its own clock: the mark is held until
 * the entity stops burning, and isOnFire() is synced from the server, so purple
 * now ends when the flame does by construction rather than by arithmetic.
 *
 * What remains of the deadline is a backstop for entities that go out where
 * nobody is looking, and it is counted on END_WORLD_TICK — which Fabric fires
 * from ClientLevel.tickEntities, the same call the pause menu stops. Two clocks
 * that stop together can still be compared.
 */
public final class DragonBurnClient {
    /** Entity id -> the tick after which the mark is stale even if still lit. */
    private static final Map<Integer, Long> MARKED = new HashMap<>();
    private static long ticks;

    /**
     * How long past the burn a mark is still believed.
     *
     * Generous on purpose. This is not the length of the burn any more — the
     * flame decides that — it is only how long a mark for an entity nobody can
     * see is worth keeping before assuming it went out unobserved. Cheap to be
     * wrong slowly, expensive to be wrong quickly.
     */
    private static final int SLACK = 200;

    /** How often to check the marks against the entities they are about. */
    private static final int SWEEP = 20;

    private DragonBurnClient() {
    }

    /**
     * Called on the world tick, not the client tick.
     *
     * The distinction is the whole fix: this one stops when the world does.
     */
    public static void tick(ClientLevel level) {
        ticks++;
        if (ticks % SWEEP != 0) {
            return;
        }
        // An entity that went out off-screen never reaches isBurning, so the
        // mark has to be checked against the world rather than only at the
        // moment something asks to draw it. Otherwise the id lingers and the
        // next entity to inherit it catches fire looking like ours.
        MARKED.entrySet().removeIf(mark -> {
            Entity entity = level.getEntity(mark.getKey());
            return entity == null || !entity.isOnFire() || mark.getValue() < ticks;
        });
    }

    public static void accept(DragonBurnPayload payload) {
        MARKED.put(payload.entityId(), ticks + payload.ticks() + SLACK);
    }

    public static void clear() {
        MARKED.clear();
        ticks = 0;
    }

    /**
     * Whether this one is burning with ours.
     *
     * Falls false the moment the entity stops burning at all — and drops the
     * mark with it, so a burn cut short by water or a death does not leave it
     * behind on whatever reuses the id.
     */
    public static boolean isBurning(Entity entity) {
        if (!entity.isOnFire()) {
            MARKED.remove(entity.getId());
            return false;
        }
        Long until = MARKED.get(entity.getId());
        return until != null && until > ticks;
    }
}
