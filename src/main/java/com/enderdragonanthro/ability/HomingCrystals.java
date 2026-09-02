package com.enderdragonanthro.ability;

import com.enderdragonanthro.network.HomingCrystalPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
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

    /**
     * One anchor: which crystal, where it stands, and in which world.
     *
     * The dimension used to be a {@code UUID} that was only ever written as
     * null — a field of the right shape holding nothing, which is why going
     * home across a portal was never going to work.
     */
    private record Anchor(UUID entity, BlockPos pos, ResourceKey<Level> dimension) {
    }

    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();
    private static final Map<UUID, Long> LOCKED_UNTIL = new HashMap<>();

    /**
     * Find the crystal, loading the chunk it stands in if that is what it takes.
     *
     * {@code getEntity} answers null for an entity in an unloaded chunk exactly
     * as it does for one that has been destroyed, and an anchor is a thing you
     * deliberately walk away from — so those two answers have to be told apart
     * or walking away IS destroying it. Loading the chunk is what tells them
     * apart: if it is still not there once the chunk is open, it is gone.
     */
    private static EndCrystal resolve(ServerLevel level, Anchor anchor) {
        if (level.getEntity(anchor.entity()) instanceof EndCrystal found) {
            return found;
        }
        level.getChunk(anchor.pos());
        return level.getEntity(anchor.entity()) instanceof EndCrystal loaded ? loaded : null;
    }

    /**
     * The same, in whichever world it turns out to be in.
     *
     * The recorded dimension first, then everywhere else — an anchor placed
     * before the dimension was recorded has none, and one that somehow moved
     * should still be findable rather than reported as shattered.
     */
    private static EndCrystal resolveAnywhere(MinecraftServer server, Anchor anchor) {
        if (anchor.dimension() != null) {
            ServerLevel known = server.getLevel(anchor.dimension());
            if (known != null) {
                EndCrystal there = resolve(known, anchor);
                if (there != null) {
                    return there;
                }
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            EndCrystal found = resolve(level, anchor);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Read the anchors back out of the save that owns them. */
    public static void load(MinecraftServer server) {
        for (Map.Entry<UUID, AnchorMemory.Kept> entry
                : AnchorMemory.of(server).everyAnchor().entrySet()) {
            AnchorMemory.Kept kept = entry.getValue();
            ANCHORS.put(entry.getKey(),
                    new Anchor(kept.entity(), kept.pos(), kept.dimension()));
        }
    }

    /** Does this dragon have an anchor on the roll? Says nothing about distance. */
    public static boolean hasAnchor(ServerPlayer player) {
        return ANCHORS.containsKey(player.getUUID());
    }

    /**
     * Which world the anchor was set in, or null if we never recorded one.
     *
     * Exposed for the gametest, and worth exposing: for the whole life of this
     * class the answer was null, because the field was a UUID nobody ever
     * filled in. Null here is the bug, not a state.
     */
    public static ResourceKey<Level> anchorDimension(ServerPlayer player) {
        Anchor anchor = ANCHORS.get(player.getUUID());
        return anchor == null ? null : anchor.dimension();
    }

    /** And write them down, so logging out is not the same as losing one. */
    public static void save(MinecraftServer server) {
        AnchorMemory memory = AnchorMemory.of(server);
        for (Map.Entry<UUID, Anchor> entry : ANCHORS.entrySet()) {
            Anchor a = entry.getValue();
            memory.remember(entry.getKey(),
                    new AnchorMemory.Kept(a.entity(), a.dimension(), a.pos()));
        }
    }

    /**
     * Drop everything held for a world that is no longer loaded.
     *
     * See {@link com.enderdragonanthro.ServerMemory}: these maps are static, and
     * static is per process rather than per world. Singleplayer runs the server
     * inside the client, so without this they carry into the next save you open.
     */
    public static void forgetWorld() {
        ANCHORS.clear();
        LOCKED_UNTIL.clear();
    }

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
        // Only one at a time: the old one quietly gives way. Resolved rather
        // than looked up, because the whole point of setting a second anchor is
        // usually that you have travelled -- so the first one is nearly always
        // in a chunk that is shut, and a plain lookup left it standing forever.
        Anchor previous = ANCHORS.get(owner.getUUID());
        if (previous != null) {
            EndCrystal old = resolveAnywhere(level.getServer(), previous);
            if (old != null) {
                old.discard();
            }
        }

        EndCrystal crystal = new EndCrystal(level,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        crystal.setShowBottom(false);
        crystal.addTag(TAG);
        crystal.addTag(OWNER_TAG + owner.getUUID().toString().replace("-", ""));
        level.addFreshEntity(crystal);
        ANCHORS.put(owner.getUUID(),
                new Anchor(crystal.getUUID(), pos.immutable(), level.dimension()));
        save(level.getServer());

        // Say which crystals are anchors NOW, not on the next sweep. The sweep
        // runs once a second, so an anchor spent up to a second being drawn as
        // an ordinary end crystal before the client learned better -- the
        // quarter-second flash of magenta on placing one.
        //
        // Sending it here beats the spawn packet outright: entity tracking
        // sends that at the end of the tick, and this payload is queued during
        // it. The client stores ids, not entities, so learning about a crystal
        // it has not been told about yet is fine -- the id is already marked by
        // the time there is anything to draw.
        broadcast(level.getServer());

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
            AnchorMemory.of(level.getServer()).remember(owner, null);
            LOCKED_UNTIL.put(owner, level.getGameTime() + BROKEN_COOLDOWN);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                player.displayClientMessage(Component.literal(
                        "Your anchor was shattered. " + (BROKEN_COOLDOWN / 20 / 60) + " minutes.")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    /**
     * Go home, from anywhere, including another world.
     *
     * Distance is not a thing this asks about. The anchor's chunk is loaded to
     * find it — that is the whole reason the position is recorded — so the trip
     * works from the other side of the map as readily as from across the room.
     * It used to look the crystal up in whichever level the player happened to
     * be standing in, without loading anything, which meant "far away" and
     * "another dimension" both came back as "You have set no anchor."
     */
    public static void returnHome(ServerPlayer player) {
        Anchor anchor = ANCHORS.get(player.getUUID());
        if (anchor == null) {
            player.displayClientMessage(Component.literal("You have set no anchor.")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        MinecraftServer server = player.server;
        EndCrystal crystal = resolveAnywhere(server, anchor);
        if (crystal == null) {
            // The chunk was opened and it is not in it. That is gone, and it is
            // the only reading of "cannot find it" that this is allowed to make.
            ANCHORS.remove(player.getUUID());
            AnchorMemory.of(server).remember(player.getUUID(), null);
            player.displayClientMessage(Component.literal("Your anchor is no longer standing.")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }

        ServerLevel there = (ServerLevel) crystal.level();
        Vec3 beside = new Vec3(crystal.getX(), crystal.getY(), crystal.getZ());
        Vec3 dest = DragonAbilities.findFooting(player, there, beside);
        if (dest == null) {
            player.displayClientMessage(Component.literal("There is no room at your anchor.")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        ServerLevel here = player.serverLevel();
        here.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.7F);

        if (there != here) {
            player.changeDimension(new DimensionTransition(there, dest, Vec3.ZERO,
                    player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
            there.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL,
                    SoundSource.PLAYERS, 0.5F, 1.3F);
        } else {
            player.teleportTo(dest.x, dest.y, dest.z);
            player.connection.resetPosition();
        }
        player.resetFallDistance();
        DragonFlight.clear(player);
        DragonMinions.recall(player);
        there.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        player.displayClientMessage(Component.literal("Home.").withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * Is this anchor actually gone, as opposed to merely out of sight?
     *
     * THE BUG THIS EXISTS FOR. The sweep used to ask
     * {@code player.serverLevel().getEntity(anchor)} once a second and treat
     * null as "shattered" — so the moment you walked far enough for its chunk
     * to close, or stepped through a portal, the game decided your anchor was
     * gone and put a replacement crystal in your hand. Walking away from an
     * anchor destroyed it, which is the exact opposite of what an anchor is.
     *
     * Absent is not dead. This answers yes ONLY when the chunk is open and the
     * crystal is not in it, which is the one case that is real evidence. It
     * deliberately loads nothing: a sweep that force-loaded a chunk per player
     * per second would hold half the world open. An anchor whose chunk is shut
     * is assumed to be standing, and {@code returnHome} — which does load the
     * chunk, once, because you asked it to — is where the truth is settled.
     */
    private static boolean shattered(MinecraftServer server, Anchor anchor) {
        if (anchor.dimension() == null) {
            return false;                     // placed before we recorded one
        }
        ServerLevel level = server.getLevel(anchor.dimension());
        if (level == null || !level.isLoaded(anchor.pos())) {
            return false;                     // cannot see it; that is not evidence
        }
        return !(level.getEntity(anchor.entity()) instanceof EndCrystal);
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
            if (anchor != null && !shattered(server, anchor)) {
                continue;                                  // one is already standing
            }
            if (anchor != null) {
                ANCHORS.remove(player.getUUID());
                AnchorMemory.of(server).remember(player.getUUID(), null);
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
