package com.enderdragonanthro.ability;

import com.enderdragonanthro.network.DragonPickupPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Whether the dragon stoops.
 *
 * A dragon eight blocks tall walking across a battlefield hoovers up every
 * rotten flesh and arrow shaft it steps over, and there is no way to stop it
 * short of not walking there. Vanilla has never needed a switch for this
 * because vanilla players are not four and a half times the size of the mess
 * they leave, and because a player who does not want an item can walk around
 * it — which a dragon cannot, its hitbox being most of the room.
 *
 * So: a switch, in the inventory, where the rest of what you carry lives.
 *
 * Held by absence. The set names the players who have turned it OFF, so the
 * default is vanilla behaviour and a player nobody has heard of picks things
 * up like everyone else.
 */
public final class DragonPickup {
    private static final Set<UUID> STOOPS_NOT = new HashSet<>();

    /**
     * Drop everything held for a world that is no longer loaded.
     *
     * See {@link com.enderdragonanthro.ServerMemory}: these maps are static, and
     * static is per process rather than per world. Singleplayer runs the server
     * inside the client, so without this they carry into the next save you open.
     */
    public static void forgetWorld() {
        STOOPS_NOT.clear();
    }

    private DragonPickup() {
    }

    /** Asked by the mixin on every item that touches a player. */
    public static boolean picksUp(Player player) {
        return !STOOPS_NOT.contains(player.getUUID());
    }

    /** The button was clicked. Store it and say so, so the button can redraw. */
    public static void set(ServerPlayer player, boolean on) {
        if (on) {
            STOOPS_NOT.remove(player.getUUID());
        } else {
            STOOPS_NOT.add(player.getUUID());
        }
        tell(player);
    }

    /** Send the current state, for a client that has just arrived. */
    public static void tell(ServerPlayer player) {
        ServerPlayNetworking.send(player, new DragonPickupPayload(picksUp(player)));
    }
}
