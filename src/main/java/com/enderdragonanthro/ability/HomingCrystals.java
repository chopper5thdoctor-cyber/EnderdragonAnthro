package com.enderdragonanthro.ability;

import com.enderdragonanthro.network.HomingCrystalPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The homing crystal: an anchor you can come back to.
 *
 * It is an End Crystal underneath — same model, rendered orange — but it never
 * explodes and it never feeds Crystal Link. Anyone can break it, and when they
 * do the owner is locked out of placing another for a while. That is the whole
 * balance of it: a way home that an enemy can take from you.
 */
public final class HomingCrystals {
    public static final String TAG = "edanthro_homing";
    public static final String OWNER_TAG = "edanthro_home_";
    /** How long you go without an anchor after someone shatters yours. */
    private static final int BROKEN_COOLDOWN = 6000;   // 5 minutes

    private record Anchor(UUID entity, BlockPos pos, java.util.UUID dimension) {
    }

    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();
    private static final Map<UUID, Long> LOCKED_UNTIL = new HashMap<>();

    private HomingCrystals() {
    }

    public static boolean isHoming(EndCrystal crystal) {
        return crystal.getTags().contains(TAG);
    }

    public static boolean locked(ServerPlayer player) {
        return player.serverLevel().getGameTime() < LOCKED_UNTIL.getOrDefault(player.getUUID(), 0L);
    }

    public static long lockSecondsLeft(ServerPlayer player) {
        long left = LOCKED_UNTIL.getOrDefault(player.getUUID(), 0L)
                - player.serverLevel().getGameTime();
        return Math.max(0, (left + 19) / 20);
    }

    /** Set the anchor down. No bedrock required — it stands anywhere it fits. */
    public static boolean place(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        if (locked(owner)) {
            owner.displayClientMessage(Component.literal(
                    "Your anchor is still reforming (" + lockSecondsLeft(owner) + "s).")
                    .withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        // only one at a time: the old one quietly gives way
        Anchor previous = ANCHORS.get(owner.getUUID());
        if (previous != null && level.getEntity(previous.entity()) instanceof EndCrystal old) {
            old.discard();
        }

        EndCrystal crystal = new EndCrystal(level,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        crystal.setShowBottom(false);
        crystal.addTag(TAG);
        crystal.addTag(OWNER_TAG + owner.getUUID().toString().replace("-", ""));
        level.addFreshEntity(crystal);
        ANCHORS.put(owner.getUUID(), new Anchor(crystal.getUUID(), pos.immutable(), null));

        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        owner.displayClientMessage(Component.literal("Anchor set.")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Broken by anyone: it shatters instead of detonating, and the owner waits. */
    public static void shatter(ServerLevel level, EndCrystal crystal) {
        BlockPos at = crystal.blockPosition();
        crystal.discard();
        level.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.2F, 0.8F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 24, 0.4, 0.6, 0.4, 0.05);

        for (String tag : crystal.getTags()) {
            if (!tag.startsWith(OWNER_TAG)) {
                continue;
            }
            String raw = tag.substring(OWNER_TAG.length());
            UUID owner = fromCompact(raw);
            if (owner == null) {
                continue;
            }
            ANCHORS.remove(owner);
            LOCKED_UNTIL.put(owner, level.getGameTime() + BROKEN_COOLDOWN);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                player.displayClientMessage(Component.literal(
                        "Your anchor was shattered. " + (BROKEN_COOLDOWN / 20 / 60) + " minutes.")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    /** Go home. */
    public static void returnHome(ServerPlayer player) {
        Anchor anchor = ANCHORS.get(player.getUUID());
        ServerLevel level = player.serverLevel();
        if (anchor == null || !(level.getEntity(anchor.entity()) instanceof EndCrystal crystal)) {
            player.displayClientMessage(Component.literal("You have set no anchor.")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        Vec3 beside = new Vec3(crystal.getX(), crystal.getY(), crystal.getZ());
        Vec3 dest = DragonAbilities.findFooting(player, level, beside);
        if (dest == null) {
            player.displayClientMessage(Component.literal("There is no room at your anchor.")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        player.teleportTo(dest.x, dest.y, dest.z);
        player.connection.resetPosition();
        player.resetFallDistance();
        DragonFlight.clear(player);
        DragonMinions.recall(player);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        player.displayClientMessage(Component.literal("Home.").withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * Keep exactly one anchor in the dragon's keeping.
     *
     * You do not craft this — a dragon has no use for a workbench. If you hold
     * no crystal, have none standing, and are not locked out, one simply is
     * there. Break it and you wait; the replacement arrives when the lockout
     * ends. That is the only cost.
     */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        broadcast(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!DragonFormManager.isDragon(player) || locked(player)) {
                continue;
            }
            Anchor anchor = ANCHORS.get(player.getUUID());
            if (anchor != null
                    && player.serverLevel().getEntity(anchor.entity()) instanceof EndCrystal) {
                continue;                                  // one is already standing
            }
            if (player.getInventory().contains(
                    new ItemStack(com.enderdragonanthro.item.ModItems.HOMING_CRYSTAL))) {
                continue;                                  // one is already in hand
            }
            ItemStack stack = new ItemStack(com.enderdragonanthro.item.ModItems.HOMING_CRYSTAL);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.displayClientMessage(Component.literal("An anchor forms in your keeping.")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /**
     * Tell every client which crystals are anchors.
     *
     * The mark is a scoreboard tag, and scoreboard tags are server-side NBT —
     * the client never receives them. Without this the renderer cannot tell an
     * anchor from a live crystal and paints them all vanilla magenta, which is
     * exactly what it was doing.
     */
    private static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<Integer> ids = new ArrayList<>();
            for (EndCrystal crystal : player.serverLevel().getEntitiesOfClass(
                    EndCrystal.class, player.getBoundingBox().inflate(160.0),
                    HomingCrystals::isHoming)) {
                ids.add(crystal.getId());
            }
            ServerPlayNetworking.send(player, new HomingCrystalPayload(List.copyOf(ids)));
        }
    }

    private static UUID fromCompact(String raw) {
        if (raw.length() != 32) {
            return null;
        }
        try {
            return UUID.fromString(raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                    + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
