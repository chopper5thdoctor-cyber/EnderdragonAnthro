package com.enderdragonanthro.ability;

import com.enderdragonanthro.DragonAnatomy;
import com.enderdragonanthro.network.CrystalBeamsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** One crystal, named the way an entity id has to be: with its world. */
    private record Fed(ResourceKey<Level> dimension, int crystalId) {
    }

    private static final Map<UUID, Link> LINKS = new HashMap<>();
    /** The crystals this mod pointed last tick, so it can unpoint its own. */
    private static final Set<Fed> AIMED = new HashSet<>();
    /** Who is feeding whom right now: crystal, dragon, crystal, dragon. */
    private static List<Integer> PAIRS = List.of();
    /** ...and the last one actually put on the wire, so a still scene is quiet. */
    private static List<Integer> SENT = List.of();

    /**
     * Drop everything held for a world that is no longer loaded.
     *
     * See {@link com.enderdragonanthro.ServerMemory}: these maps are static, and
     * static is per process rather than per world. Singleplayer runs the server
     * inside the client, so without this they carry into the next save you open.
     */
    public static void forgetWorld() {
        LINKS.clear();
        AIMED.clear();
        PAIRS = List.of();
        SENT = List.of();
    }

    private CrystalHealing() {
    }

    /**
     * Every dragon healed, and every beam that says so.
     *
     * The beams are worked out in a second pass on purpose. A crystal's beam
     * target is ONE field, and the first version wrote it from inside the
     * per-dragon loop -- so two dragons on one crystal wrote it twice and the
     * last one won, and worse, a dragon walking out of range called
     * setBeamTarget(null) on a crystal the other one was still drinking from.
     * Collecting first and aiming after is what makes "who is this crystal
     * feeding" a question with an answer.
     */
    public static void tick(MinecraftServer server) {
        tickFor(server, server.getPlayerList().getPlayers());
    }

    /**
     * The same, over a list somebody else chose.
     *
     * Public for the gametest and for nothing else. GameTest hands you a world
     * with an empty player list, so a FakeDragon is never in
     * getPlayerList().getPlayers() and this code could not be reached at all --
     * which is exactly why the one-beam-for-two-dragons bug had to be found by
     * standing next to somebody.
     */
    public static void tickFor(MinecraftServer server, List<ServerPlayer> dragons) {
        Map<Fed, List<ServerPlayer>> beams = new HashMap<>();
        for (ServerPlayer player : dragons) {
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
                LINKS.remove(player.getUUID());
                continue;
            }

            LINKS.put(player.getUUID(), new Link(level.dimension(), nearest.getId()));
            beams.computeIfAbsent(new Fed(level.dimension(), nearest.getId()),
                    key -> new ArrayList<>()).add(player);

            if (level.getGameTime() % HEAL_PERIOD_TICKS == 0
                    && player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0F);
            }
            // the crystal sustains the whole dragon: hunger refills too, 1/s
            if (level.getGameTime() % 20 == 0 && player.getFoodData().needsFood()) {
                player.getFoodData().eat(1, 0.4F);
            }
        }
        aim(server, beams);
    }

    /**
     * Point every crystal at what it is feeding, and say so on the wire.
     *
     * Vanilla's single target still gets set, at the first dragon in a fixed
     * order. It is doing two jobs nothing else can: EndCrystalRenderer's
     * shouldRender consults it, so a crystal with none is culled the moment its
     * own tiny box leaves the view and takes every beam with it -- and in the
     * real End fight it is the only target there is, aimed at a dragon that is
     * not a player at all.
     *
     * The rest is sent. See CrystalBeamsPayload for why the whole pairing goes
     * rather than the extras.
     */
    private static void aim(MinecraftServer server, Map<Fed, List<ServerPlayer>> beams) {
        Set<Fed> aimed = new HashSet<>();
        List<Integer> pairs = new ArrayList<>();
        // Sorted, both levels of it: the payload is compared against the last
        // one to decide whether to send at all, so an ordering that wanders
        // with the player list would resend every tick and prove nothing.
        List<Fed> order = new ArrayList<>(beams.keySet());
        order.sort(Comparator.<Fed>comparingInt(fed -> fed.crystalId())
                .thenComparing(fed -> fed.dimension().location().toString()));
        for (Fed fed : order) {
            EndCrystal crystal = crystalAt(server, fed);
            if (crystal == null) {
                continue;
            }
            List<ServerPlayer> dragons = beams.get(fed);
            dragons.sort(Comparator.comparingInt(Entity::getId));
            crystal.setBeamTarget(BlockPos.containing(DragonAnatomy.heart(dragons.get(0))));
            aimed.add(fed);
            for (ServerPlayer dragon : dragons) {
                pairs.add(crystal.getId());
                pairs.add(dragon.getId());
            }
        }

        // Anything we aimed last tick and no longer do. Only ours are cleared:
        // a crystal this mod never pointed at is the vanilla fight's business.
        for (Fed fed : AIMED) {
            if (aimed.contains(fed)) {
                continue;
            }
            EndCrystal crystal = crystalAt(server, fed);
            if (crystal != null) {
                crystal.setBeamTarget(null);
            }
        }
        AIMED.clear();
        AIMED.addAll(aimed);

        PAIRS = List.copyOf(pairs);
        // On change, and once a second regardless -- the resend is what syncs
        // somebody who joined into a scene that has not changed since.
        if (PAIRS.equals(SENT) && server.getTickCount() % 20 != 0) {
            return;
        }
        SENT = PAIRS;
        CrystalBeamsPayload payload = new CrystalBeamsPayload(SENT);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    /**
     * Every beam standing right now, flattened: crystal, dragon, crystal, dragon.
     *
     * The same list the client is sent, so a test that reads this is reading
     * what will be drawn rather than a parallel account of it.
     */
    public static List<Integer> beamPairs() {
        return PAIRS;
    }

    private static EndCrystal crystalAt(MinecraftServer server, Fed fed) {
        ServerLevel level = server.getLevel(fed.dimension());
        return level != null && level.getEntity(fed.crystalId()) instanceof EndCrystal crystal
                ? crystal : null;
    }

    /**
     * Forget this dragon's crystal.
     *
     * The beam is NOT cleared here, deliberately. It used to be, and that is
     * the bug in miniature: the crystal may still be feeding somebody else, and
     * turning the beam off because one drinker left took the other's with it.
     * aim() sweeps the crystals nobody claimed, which is the only place that
     * knows whether anybody did.
     */
    public static void unlink(ServerPlayer player) {
        LINKS.remove(player.getUUID());
    }
}
