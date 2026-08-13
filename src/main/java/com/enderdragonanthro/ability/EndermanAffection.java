package com.enderdragonanthro.ability;

import com.enderdragonanthro.network.EndermanHappyPayload;
import com.enderdragonanthro.particle.ModParticles;
import com.enderdragonanthro.transform.DragonFormManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Endermen adore the dragon.
 *
 * Hold your gaze on one for three seconds and it looks back, wears a ^^ for a
 * moment and gives off purple hearts — once per gaze; looking away re-arms it.
 * They meet your eyes the whole time you are watching, which is the opposite
 * of what an enderman does to anyone else.
 *
 * (The usual stare-anger never fires in dragon form: targeting is disabled by
 * LivingEntityTargetabilityMixin.)
 */
public final class EndermanAffection {
    private static final double RANGE = 48.0;
    /** Three seconds, as asked — long enough to be deliberate, short enough to find. */
    private static final int GAZE_TICKS = 60;
    public static final int HAPPY_TICKS = 60;

    private static final class Gaze {
        int ticks;
        boolean fired;
    }

    private static final Map<UUID, Map<Integer, Gaze>> GAZES = new HashMap<>();

    private EndermanAffection() {
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!DragonFormManager.isDragon(player)) {
                GAZES.remove(player.getUUID());
                continue;
            }
            ServerLevel level = player.serverLevel();
            Map<Integer, Gaze> gazes = GAZES.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
            Set<Integer> lookedAt = new HashSet<>();

            for (EnderMan enderman : level.getEntitiesOfClass(EnderMan.class,
                    player.getBoundingBox().inflate(RANGE))) {
                if (!isGazingAt(player, enderman)) {
                    continue;
                }
                lookedAt.add(enderman.getId());
                // meet the dragon's eyes for as long as it is watching
                enderman.getLookControl().setLookAt(player, 60.0F, 60.0F);

                Gaze gaze = gazes.computeIfAbsent(enderman.getId(), i -> new Gaze());
                gaze.ticks++;
                if (gaze.ticks >= GAZE_TICKS && !gaze.fired) {
                    gaze.fired = true;
                    delight(level, enderman);
                }
            }
            // looking away resets the gaze, re-arming the hearts
            gazes.keySet().retainAll(lookedAt);
        }
    }

    /** Hearts, and the ^^, for everyone who can see it. */
    public static void delight(ServerLevel level, EnderMan enderman) {
        level.sendParticles(ModParticles.PURPLE_HEART,
                enderman.getX(), enderman.getEyeY() + 0.6, enderman.getZ(),
                7, 0.5, 0.5, 0.5, 0.02);
        announce(level, enderman, HAPPY_TICKS);
    }

    /** A bigger show, for a deliberate fuss rather than a passing glance. */
    public static void adore(ServerLevel level, EnderMan enderman) {
        level.sendParticles(ModParticles.PURPLE_HEART,
                enderman.getX(), enderman.getEyeY() + 0.4, enderman.getZ(),
                24, 0.7, 0.9, 0.7, 0.05);
        announce(level, enderman, HAPPY_TICKS * 2);
    }

    /**
     * The ^^ is a client-side overlay, and nothing about an entity's mood is
     * synced on its own, so it has to be said out loud.
     */
    private static void announce(ServerLevel level, EnderMan enderman, int ticks) {
        EndermanHappyPayload payload = new EndermanHappyPayload(enderman.getId(), ticks);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(enderman) < 128.0 * 128.0) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
    }

    /** The enderman stare check, loosened slightly for a 7-block eye height. */
    private static boolean isGazingAt(ServerPlayer player, EnderMan enderman) {
        Vec3 view = player.getViewVector(1.0F).normalize();
        Vec3 toEnderman = new Vec3(
                enderman.getX() - player.getX(),
                enderman.getEyeY() - player.getEyeY(),
                enderman.getZ() - player.getZ());
        double distance = toEnderman.length();
        double dot = view.dot(toEnderman.normalize());
        return dot > 1.0 - 0.06 / Math.max(distance, 1.0) && player.hasLineOfSight(enderman);
    }
}
