package com.enderdragonanthro.ability;

import com.enderdragonanthro.network.DragonSightPayload;
import com.enderdragonanthro.transform.DragonFormManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The eyes open.
 *
 * Four things at once, and only one of them belongs on this side. The tint,
 * the brightness and the health readouts are all the client's business once it
 * knows the sight is open. The portals are not: they are blocks, and most of
 * the ones worth sensing are outside the render distance or in chunks the
 * client will never be sent, so the search happens here.
 *
 * Searching for a block across a hundred blocks of world sounds ruinous — that
 * is sixteen million lookups — but it is not, because a chunk section carries
 * a palette of every state in it and can be asked whether it holds a thing
 * without unpacking anything. Almost every section answers no in a pointer
 * comparison, and only the handful that say yes get walked.
 */
public final class DragonSight {
    /** Chunks out from the player the sense reaches. */
    private static final int RADIUS_CHUNKS = 8;
    /** Ticks between sweeps. A portal is not going anywhere. */
    private static final int SCAN_PERIOD = 40;
    /** Enough to see, without the count itself becoming the cost. */
    private static final int MAX_MARKS = 64;
    /**
     * How long a top-up of night vision lasts, and when it is renewed.
     *
     * Both numbers matter. Vanilla fades night vision out below 200 ticks
     * remaining -- GameRenderer.getNightVisionScale pulses it with a sine --
     * so anything granted for less than that flickers the whole time the sight
     * is open. And renewing it every tick would post an effect packet every
     * tick, because MobEffectInstance.update marks itself dirty whenever the
     * new duration beats the old.
     */
    private static final int VISION_TICKS = 600;
    private static final int VISION_RENEW_BELOW = 300;

    /** Bumped by hand when the sight changes; shown when it opens. */
    private static final String BUILD = "sight-11";

    private static final Set<UUID> ACTIVE = new HashSet<>();

    private DragonSight() {
    }

    public static boolean isOpen(ServerPlayer player) {
        return ACTIVE.contains(player.getUUID());
    }

    public static void close(ServerPlayer player) {
        if (ACTIVE.remove(player.getUUID())) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            send(player, List.of(), List.of());
        }
    }

    public static void toggle(ServerPlayer player) {
        if (!DragonFormManager.isDragon(player)) {
            return;
        }
        if (ACTIVE.remove(player.getUUID())) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            send(player, List.of(), List.of());
            player.displayClientMessage(Component.literal("The sight closes.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        } else {
            ACTIVE.add(player.getUUID());
            sweep(player);
            // The build stamp is here because three separate fixes have now
            // been reported broken while the jar under test predated them, and
            // neither of us could tell that apart from a real bug. Bumped by
            // hand whenever something in the sight changes, so one keypress
            // says which build is actually loaded.
            player.displayClientMessage(Component.literal("The sight opens. [" + BUILD + "]")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    public static void tick(MinecraftServer server) {
        long time = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ACTIVE.contains(player.getUUID())) {
                continue;
            }
            if (!DragonFormManager.isDragon(player)) {
                close(player);                       // human eyes do not do this
                continue;
            }
            // Kept topped up rather than granted once, so it cannot expire mid
            // flight and cannot outlive the sight if the player logs out.
            // Ambient and hidden: this is the dragon's own sight, not a potion.
            MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
            if (vision == null || vision.getDuration() < VISION_RENEW_BELOW) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                        VISION_TICKS, 0, true, false, false));
            }
            if (time % SCAN_PERIOD == 0) {
                sweep(player);
            }
        }
    }

    /** Everything worth knowing about, sent as it stands. */
    private static void sweep(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> gateways = new ArrayList<>();
        List<BlockPos> end = new ArrayList<>();
        ChunkPos origin = player.chunkPosition();

        for (int cx = origin.x - RADIUS_CHUNKS; cx <= origin.x + RADIUS_CHUNKS; cx++) {
            for (int cz = origin.z - RADIUS_CHUNKS; cz <= origin.z + RADIUS_CHUNKS; cz++) {
                // Loaded chunks only. Forcing a load per sweep would generate
                // terrain in a ring around the player every two seconds.
                ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                scan(chunk, gateways, end);
                if (gateways.size() + end.size() >= MAX_MARKS) {
                    stronghold(player, end);
        send(player, gateways, end);
                    return;
                }
            }
        }
        send(player, gateways, end);
    }

    /**
     * One chunk, cheaply.
     *
     * hasOnlyAir skips the sky and maybeHas asks the section's palette rather
     * than its contents — a section with no portal in it costs one call, not
     * four thousand.
     */
    private static void scan(ChunkAccess chunk, List<BlockPos> gateways, List<BlockPos> end) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(DragonSight::marked)) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        List<BlockPos> into = target(state, gateways, end);
                        if (into == null) {
                            continue;
                        }
                        BlockPos at = new BlockPos(
                                chunk.getPos().getMinBlockX() + x, baseY + y,
                                chunk.getPos().getMinBlockZ() + z);
                        // One mark per portal, not one per block: a nether
                        // portal is a wall of them and a frame is twelve.
                        if (into.stream().noneMatch(p -> p.distSqr(at) < 8.0 * 8.0)) {
                            into.add(at);
                        }
                    }
                }
            }
        }
    }

    /**
     * Which list a block belongs in, or null for the overwhelming majority.
     *
     * A nether portal only counts when it is lit, because an unlit one is four
     * obsidian blocks and not a way anywhere. An End portal counts either way:
     * a frame with no eyes in it is still the only door to the End for miles,
     * and knowing where the stronghold is IS the sense.
     */
    private static List<BlockPos> target(BlockState state, List<BlockPos> gateways,
                                         List<BlockPos> end) {
        if (state.is(Blocks.NETHER_PORTAL)) {
            return gateways;
        }
        if (state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)
                || state.is(Blocks.END_GATEWAY)) {
            return end;
        }
        return null;
    }

    private static boolean marked(BlockState state) {
        return state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_GATEWAY);
    }

    /**
     * The stronghold, from world generation rather than from loaded blocks.
     *
     * The block scan can only see chunks that exist, and a stronghold is
     * typically a thousand blocks off and unloaded, so the sense had nothing to
     * point at and drew nothing. This is the query an eye of ender runs:
     * StructureTags.EYE_OF_ENDER_LOCATED against the chunk generator, which
     * answers from the seed whether or not anything has been generated yet.
     *
     * Overworld only, and skipped when the sight is closed, because it is not
     * cheap -- the generator may search a long way. Once found it is cached for
     * the session, since a stronghold does not move.
     */
    private static void stronghold(ServerPlayer player, List<BlockPos> end) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        BlockPos known = STRONGHOLDS.get(level.dimension().location());
        if (known != null) {
            end.add(known);
            return;
        }
        com.mojang.datafixers.util.Pair<BlockPos,
                net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>>
                hit = level.getChunkSource().getGenerator().findNearestMapStructure(
                level, level.registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .getTag(net.minecraft.tags.StructureTags.EYE_OF_ENDER_LOCATED)
                        .orElseThrow(),
                player.blockPosition(), 100, false);
        BlockPos found = hit == null ? null : hit.getFirst();
        if (found != null) {
            STRONGHOLDS.put(level.dimension().location(), found);
            end.add(found);
        }
    }

    private static final java.util.Map<net.minecraft.resources.ResourceLocation, BlockPos>
            STRONGHOLDS = new java.util.HashMap<>();

    private static void send(ServerPlayer player, List<BlockPos> gateways, List<BlockPos> end) {
        ServerPlayNetworking.send(player, new DragonSightPayload(
                ACTIVE.contains(player.getUUID()), List.copyOf(gateways), List.copyOf(end)));
    }
}
