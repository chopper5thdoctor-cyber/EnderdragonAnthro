package com.enderdragonanthro.client;

import com.enderdragonanthro.network.ShadeHoldsPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * What each shade is holding, as far as anybody watching can tell.
 *
 * Broadcast rather than derived, and to viewers rather than to the owner: the
 * block in her hand is a thing OTHER people see, which is the whole reason it
 * is worth drawing at all. The owner is usually behind her.
 *
 * Cleared on disconnect with the rest of the client mirrors. Keyed by entity
 * id, and entity ids start again from scratch in the next world.
 */
public final class ShadeHoldsClient {
    private static final Map<Integer, BlockState> HELD = new HashMap<>();

    private ShadeHoldsClient() {
    }

    public static void accept(ShadeHoldsPayload payload) {
        if (payload.blockStateId() == 0) {
            HELD.remove(payload.entityId());
            return;
        }
        BlockState state = Block.stateById(payload.blockStateId());
        if (state.isAir()) {
            HELD.remove(payload.entityId());
        } else {
            HELD.put(payload.entityId(), state);
        }
    }

    public static void clear() {
        HELD.clear();
    }

    /** What this shade has in her hand, or null. */
    public static BlockState heldBy(Entity shade) {
        return HELD.get(shade.getId());
    }
}
