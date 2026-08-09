package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.core.particles.ParticleTypes;
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
 * Endermen adore the dragon. Hold your gaze on one for 10 seconds and it
 * emits hearts — once per gaze; breaking eye contact re-arms it. (The
 * usual stare-anger never fires in dragon form: targeting is disabled by
 * LivingEntityTargetabilityMixin, and endermen already face nearby
 * players via their vanilla look goals.)
 */
public final class EndermanAffection {
    private static final double RANGE = 48.0;
    private static final int GAZE_TICKS = 200;

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
                Gaze gaze = gazes.computeIfAbsent(enderman.getId(), i -> new Gaze());
                gaze.ticks++;
                if (gaze.ticks >= GAZE_TICKS && !gaze.fired) {
                    gaze.fired = true;
                    level.sendParticles(ParticleTypes.HEART,
                            enderman.getX(), enderman.getEyeY() + 0.6, enderman.getZ(),
                            6, 0.5, 0.5, 0.5, 0.02);
                }
            }
            // looking away resets the gaze, re-arming the hearts
            gazes.keySet().retainAll(lookedAt);
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
