package com.enderdragonanthro.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The canon pink boss bar, one per transformed player, shown to other
 * players within range (not to the dragon itself — you already have a
 * health bar). Range-limited to keep multiplayer bar spam down
 * (DESIGN.md complication #5).
 */
public final class DragonBossBars {
    private static final double VIEW_RANGE = 96.0;
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private DragonBossBars() {
    }

    public static void add(ServerPlayer dragon) {
        BARS.computeIfAbsent(dragon.getUUID(), u -> new ServerBossEvent(
                Component.literal(dragon.getName().getString()),
                BossEvent.BossBarColor.PINK,
                BossEvent.BossBarOverlay.PROGRESS));
    }

    public static void remove(ServerPlayer dragon) {
        ServerBossEvent bar = BARS.remove(dragon.getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    public static void tick(MinecraftServer server) {
        BARS.forEach((uuid, bar) -> {
            ServerPlayer dragon = server.getPlayerList().getPlayer(uuid);
            if (dragon == null) {
                bar.removeAllPlayers();
                return;
            }
            bar.setProgress(dragon.getHealth() / dragon.getMaxHealth());
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                boolean shouldSee = viewer != dragon
                        && viewer.level() == dragon.level()
                        && viewer.distanceTo(dragon) < VIEW_RANGE;
                if (shouldSee) {
                    bar.addPlayer(viewer);
                } else {
                    bar.removePlayer(viewer);
                }
            }
        });
    }
}
