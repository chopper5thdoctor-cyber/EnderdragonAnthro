package com.enderdragonanthro;

import com.enderdragonanthro.ability.CrystalHealing;
import com.enderdragonanthro.ability.DragonAbilities;
import com.enderdragonanthro.ability.DragonFlight;
import com.enderdragonanthro.ability.DragonIntent;
import com.enderdragonanthro.ability.DragonMinions;
import com.enderdragonanthro.ability.DragonPickup;
import com.enderdragonanthro.ability.DragonPresence;
import com.enderdragonanthro.ability.DragonSight;
import com.enderdragonanthro.ability.EndermanAffection;
import com.enderdragonanthro.ability.HomingCrystals;
import com.enderdragonanthro.boss.DragonBossBars;
import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.server.MinecraftServer;

/**
 * Everything the mod keeps in memory, and the one place it is let go of.
 *
 * ## The bug this exists for
 *
 * Almost every piece of live state in this mod is a {@code static Map<UUID, …>}
 * hanging off the class that uses it. That is the right shape for it — it is
 * per player, it is rebuilt from the world, and it has no business in a save
 * file. What it is *not* is per world.
 *
 * A dedicated server hides that, because the process and the world start and
 * stop together. Singleplayer does not: the integrated server is started and
 * stopped inside a client that keeps running, so a static map filled while you
 * played one save is still full when you open the next one. The court was the
 * loud case — Vaelle stood in one world, so summoning in a second world was
 * told slot 0 was taken and handed over Keshanne — but every map here had the
 * same fault, and the quiet ones are worse:
 *
 * - Cooldowns and grudges are stamped with {@code getGameTime()}, and game time
 *   is per world. A cooldown taken into a younger save reads as thousands of
 *   ticks in the future and the ability is simply dead until the new world
 *   catches up.
 * - {@code EndermanAffection} keys gazes by entity *id*, which are handed out
 *   again from 1 in every world. A stale one names a different creature.
 * - {@code DragonBossBars} holds {@code ServerBossEvent}s pointing at the
 *   players of a server that has stopped.
 *
 * ## Both ends, deliberately
 *
 * Cleared on stop, which is the fix, and again on start, which is the belt: a
 * crash never runs shutdown, and "what survives a crash" is precisely the state
 * nobody exercises.
 *
 * ## Adding to it
 *
 * A new static map is a new leak, and one that will not show up until somebody
 * plays two saves in one sitting. {@code verify_model.py} greps for the shape
 * and fails if a class holding one is not named here, so forgetting to add the
 * line is a build failure rather than a bug report six months later.
 */
public final class ServerMemory {
    private ServerMemory() {
    }

    /**
     * Take down the one piece that is meant to outlive the session.
     *
     * On STOPPING rather than STOPPED, and this is not a detail: STOPPED fires
     * after the levels have been saved and closed, so a SavedData marked dirty
     * there is marked dirty on a world that will never be written again. The
     * court would be taken down perfectly and thrown away.
     */
    public static void leavingWorld(MinecraftServer server) {
        DragonMinions.save(server);
    }

    /** Let go of everything belonging to a world. */
    public static void forgetWorld(MinecraftServer server) {
        DragonMinions.forget();
        DragonFormManager.forgetWorld();
        DragonBossBars.forgetWorld();
        DragonSight.forgetWorld();
        DragonAbilities.forgetWorld();
        DragonFlight.forgetWorld();
        DragonIntent.forgetWorld();
        DragonPickup.forgetWorld();
        DragonPresence.forgetWorld();
        CrystalHealing.forgetWorld();
        HomingCrystals.forgetWorld();
        EndermanAffection.forgetWorld();
    }

    /** Start clean, then read back the one thing that is kept. */
    public static void enterWorld(MinecraftServer server) {
        forgetWorld(server);
        DragonMinions.load(server);
    }
}
