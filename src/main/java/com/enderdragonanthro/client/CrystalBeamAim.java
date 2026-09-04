package com.enderdragonanthro.client;

import com.enderdragonanthro.DragonAnatomy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who a crystal's beam is really pointing at.
 *
 * The beam target on the wire is a BlockPos, so the player it stands for has
 * to be found again on this side by looking at who is standing there. That
 * lookup fails while you move quickly, and the failure is visible: the beam
 * drops back to vanilla's blocky aim for a frame or two, which sits a couple
 * of blocks higher than the point we aim at, so it reads as the beam snapping
 * up to your head and back.
 *
 * The gap is not slop in the search, it is time. The server picks the block
 * from where the player was when it last ticked, and the client draws the
 * player where it thinks they are now; at a sprinting dragon's speed those are
 * further apart than the block itself is wide. So the claim is loosened, and
 * once a crystal has been matched to a player it keeps them while they are
 * anywhere near the aim rather than re-earning the match every frame.
 *
 * Deliberately not in the mixin package: anything under there is loaded as a
 * mixin and throws on first reference.
 */
public final class CrystalBeamAim {
    /** How near the aimed block a player must be to be claimed by a beam. */
    private static final double CLAIM = 3.0;
    /** ...and how far they may drift before the claim is given up again. */
    private static final double KEEP = 12.0;

    private static final Map<Integer, UUID> HELD = new HashMap<>();

    private CrystalBeamAim() {
    }

    public static void clear() {
        HELD.clear();
    }

    /**
     * The exact point the blocky aim is standing in for, or null if nothing is
     * there and the block itself is genuinely the target.
     */
    public static Vec3 of(EndCrystal crystal, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        BlockPos target = crystal.getBeamTarget();
        if (level == null || target == null) {
            HELD.remove(crystal.getId());
            return null;
        }

        Vec3 centre = Vec3.atCenterOf(target);
        Player best = null;
        double nearest = CLAIM * CLAIM;
        for (Player player : level.players()) {
            double gap = middle(player).distanceToSqr(centre);
            if (gap < nearest) {
                nearest = gap;
                best = player;
            }
        }

        if (best == null) {
            best = held(level, crystal, centre);
        }
        if (best == null) {
            HELD.remove(crystal.getId());
            return null;
        }
        HELD.put(crystal.getId(), best.getUUID());
        return DragonAnatomy.heart(best, partialTick);
    }

    /** The player this crystal was last matched to, if they are still about. */
    private static Player held(ClientLevel level, EndCrystal crystal, Vec3 centre) {
        UUID uuid = HELD.get(crystal.getId());
        if (uuid == null) {
            return null;
        }
        Player player = level.getPlayerByUUID(uuid);
        if (player == null || middle(player).distanceToSqr(centre) > KEEP * KEEP) {
            return null;
        }
        return player;
    }

    /**
     * The point the server aimed at, so the search looks where the answer is.
     *
     * The feet would be four blocks out on a dragon eight blocks tall, which is
     * further than the CLAIM radius — the match would simply never be made and
     * the beam would fall back to vanilla's blocky aim for good.
     */
    private static Vec3 middle(Player player) {
        return DragonAnatomy.heart(player);
    }
}
