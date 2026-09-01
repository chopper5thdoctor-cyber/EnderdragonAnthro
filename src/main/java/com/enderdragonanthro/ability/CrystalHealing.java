package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Canon crystal link: within 32 blocks of an End crystal, the crystal
 * beams at the dragon and heals 1 HP every half-second (2 HP/s). Popping
 * a crystal that is actively healing you costs you 10 HP — exactly like
 * the real dragon (DESIGN.md 1.3).
 */
public final class CrystalHealing {
    private static final double RANGE = 32.0;
    private static final int HEAL_PERIOD_TICKS = 10;

    private record Link(ResourceKey<Level> dimension, int crystalId) {
    }

    private static final Map<UUID, Link> LINKS = new HashMap<>();

    /**
     * Drop everything held for a world that is no longer loaded.
     *
     * See {@link com.enderdragonanthro.ServerMemory}: these maps are static, and
     * static is per process rather than per world. Singleplayer runs the server
     * inside the client, so without this they carry into the next save you open.
     */
    public static void forgetWorld() {
        LINKS.clear();
    }

    private CrystalHealing() {
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!DragonFormManager.isDragon(player)) {
                unlink(player);
                continue;
            }
            ServerLevel level = player.serverLevel();
            Link link = LINKS.get(player.getUUID());

            EndCrystal previous = null;
            if (link != null) {
                if (!link.dimension().equals(level.dimension())) {
                    // dimension change: the old crystal is fine, just unreachable
                    LINKS.remove(player.getUUID());
                } else {
                    Entity entity = level.getEntity(link.crystalId());
                    if (entity instanceof EndCrystal crystal && crystal.isAlive()) {
                        previous = crystal;
                    } else {
                        // the crystal that was healing us was destroyed: canon 10 damage.
                        // 36 fed through the dragon's own (x/4 + 1) resilience lands as 10.
                        LINKS.remove(player.getUUID());
                        player.hurt(player.damageSources().magic(), 36.0F);
                    }
                }
            }

            EndCrystal nearest = null;
            double best = Double.MAX_VALUE;
            for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class,
                    player.getBoundingBox().inflate(RANGE))) {
                if (HomingCrystals.isHoming(crystal)) {
                    continue;              // an anchor is a waypoint, not a battery
                }
                double dist = crystal.distanceToSqr(player);
                if (dist < best) {
                    best = dist;
                    nearest = crystal;
                }
            }

            if (nearest == null) {
                if (previous != null) {
                    previous.setBeamTarget(null);
                }
                LINKS.remove(player.getUUID());
                continue;
            }

            if (previous != null && previous != nearest) {
                previous.setBeamTarget(null);
            }
            LINKS.put(player.getUUID(), new Link(level.dimension(), nearest.getId()));
            nearest.setBeamTarget(BlockPos.containing(
                    player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ()));

            if (level.getGameTime() % HEAL_PERIOD_TICKS == 0
                    && player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0F);
            }
            // the crystal sustains the whole dragon: hunger refills too, 1/s
            if (level.getGameTime() % 20 == 0 && player.getFoodData().needsFood()) {
                player.getFoodData().eat(1, 0.4F);
            }
        }
    }

    public static void unlink(ServerPlayer player) {
        Link link = LINKS.remove(player.getUUID());
        if (link == null) {
            return;
        }
        ServerLevel level = player.getServer() != null
                ? player.getServer().getLevel(link.dimension()) : null;
        if (level != null && level.getEntity(link.crystalId()) instanceof EndCrystal crystal) {
            crystal.setBeamTarget(null);
        }
    }
}
