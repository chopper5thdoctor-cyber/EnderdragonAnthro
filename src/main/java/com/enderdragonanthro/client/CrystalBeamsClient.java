package com.enderdragonanthro.client;

import com.enderdragonanthro.network.CrystalBeamsPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's copy of which dragons each crystal is feeding.
 *
 * Kept flat and replaced wholesale: the server sends the entire pairing every
 * time it changes, so there is no incremental state to fall out of step. A
 * crystal missing from the map is not being drawn by us at all, which is the
 * ordinary case and the vanilla one.
 *
 * Cleared on disconnect like every other client mirror — see
 * {@code EnderdragonAnthroClient}. The map is keyed by ENTITY ID, and entity
 * ids are handed out again from scratch in the next world; a stale one would
 * name whatever happened to get that number.
 */
public final class CrystalBeamsClient {
    private static Map<Integer, List<Integer>> fed = Map.of();

    private CrystalBeamsClient() {
    }

    public static void accept(CrystalBeamsPayload payload) {
        Map<Integer, List<Integer>> next = new HashMap<>();
        List<Integer> pairs = payload.pairs();
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            next.computeIfAbsent(pairs.get(i), key -> new ArrayList<>()).add(pairs.get(i + 1));
        }
        fed = next;
    }

    public static void clear() {
        fed = Map.of();
    }

    /** The dragons this crystal is feeding, by entity id; empty if it feeds none. */
    public static List<Integer> targets(int crystalId) {
        return fed.getOrDefault(crystalId, List.of());
    }
}
