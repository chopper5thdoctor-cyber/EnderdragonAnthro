package com.enderdragonanthro.ability;

import com.enderdragonanthro.network.ShadeStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * Jean's court: four named elite endermen, each taking its own orders.
 *
 * They are ordinary vanilla endermen — same model, same animations — scaled to
 * 4 blocks and driven procedurally each tick rather than by custom AI goals,
 * which keeps them compatible with everything vanilla does to endermen. Each
 * is tagged with its owner and name so it survives a restart.
 */
public final class DragonMinions {
    public enum Order {
        DEFEND(ChatFormatting.AQUA, "Defend"),
        ATTACK(ChatFormatting.RED, "Attack"),
        COLLECT(ChatFormatting.GREEN, "Collect"),
        CRYSTAL(ChatFormatting.LIGHT_PURPLE, "Crystal"),
        DISMANTLE(ChatFormatting.GOLD, "Dismantle"),
        /** Not a standing order — a moment's fuss, then back to what it was doing. */
        PET(ChatFormatting.WHITE, "Pet"),
        /** Also momentary: come here, wherever "here" has got to. */
        RECALL(ChatFormatting.YELLOW, "Recall"),
        /**
         * Cut a way through, sized for what has to walk through it.
         *
         * Momentary, because it is a thing done once rather than a duty: the
         * shade raises the frame, lights it, and goes back to standing with
         * you.
         */
        PORTAL(ChatFormatting.DARK_PURPLE, "Portal");

        /** The ones that are actions rather than duties. */
        public boolean momentary() {
            return this == PET || this == RECALL || this == PORTAL;
        }

        public final ChatFormatting colour;
        public final String label;

        Order(ChatFormatting colour, String label) {
            this.colour = colour;
            this.label = label;
        }
    }

    // Names and colours live in ShadeIdentity, because the renderer needs them
    // too and this class is not somewhere the client should be dragged into.
    private static final String[] NAMES = ShadeIdentity.NAMES;
    private static final ChatFormatting[] TINTS = ShadeIdentity.TINTS;

    public static final int MAX_PER_PLAYER = 4;
    private static final double SCALE = 4.0 / 2.9;      // enderman is 2.9 blocks tall
    private static final int CRYSTAL_WORK = 400;        // 20s of work per crystal
    /** Two hits inside this window counts as "you keep getting damaged". */
    private static final int GRUDGE_WINDOW = 100;
    private static final int RETALIATE_AFTER = 2;
    /** How long the court stays angry at whoever drew blood. */
    private static final int GRUDGE_TICKS = 300;
    /** A shade this close to you drops what it is doing to answer a grudge. */
    private static final double GRUDGE_RANGE = 48.0;
    /** An Attack order with nothing left to kill falls back to Defend. */
    private static final int IDLE_REVERT = 200;
    /** How close a shade keeps to the dragon when it has nothing else to do. */
    private static final double HEEL = 6.0;
    /**
     * How far the gap may open before a shade breaks into a run.
     *
     * Just past HEEL, so ordinary station-keeping is a walk and only falling
     * behind is a run. A retinue that sprints everywhere looks panicked; one
     * that never sprints is left over the horizon, because the dragon's own
     * speed scales with its size and a walking enderman cannot match it.
     */
    private static final double STRIDE_OUT = 8.0;
    /**
     * What a run multiplies the caller's walk by.
     *
     * A multiplier rather than a second constant, so each call site keeps the
     * pace it asked for -- 1.1 idling at your heel, 1.3 going to avenge you --
     * and running is the same shade in a hurry rather than every shade at one
     * flat speed.
     */
    private static final double RUN = 1.55;
    /**
     * How close a shade will stand. Inside this it stops dead and watches you.
     *
     * A guard holds a post; it does not stand on the person it is guarding.
     * Two and a half blocks clears the dragon's own bulk, so they no longer walk
     * into you and shove — which they did because follow() used to path them to
     * your exact feet and a path is only finished when it arrives.
     */
    private static final double GUARD_RING = 2.5;
    /** Bedrock this far above the world floor is fair game; below it is not. */
    private static final int BEDROCK_FLOOR = 5;
    /**
     * How long a shade is away on a Collect order — the errand's cooldown,
     * wearing the shape of a journey rather than a number on a bar.
     */
    private static final int TRIP_TICKS = 600;
    /** Chunk columns searched per tick, so the sweep never lands as one hitch. */
    private static final int CHUNKS_PER_TICK = 3;
    /**
     * How often the court screen is refreshed while a shade is away.
     *
     * The haul ticks up every TRIP_TICKS/HAUL = 9.375 ticks, so four refreshes a
     * second shows every increment without ever showing the same number twice in
     * a row for long. It costs a few dozen bytes and only while someone is out.
     */
    private static final int STATE_PULSE = 5;
    /** Past this it has genuinely lost you, and walking will not close it. */
    private static final double FETCH_DISTANCE = 48.0;
    /**
     * Keeps a shade's chunk loaded wherever it has got to.
     *
     * A shade sent foraging walks straight out of the player's view distance,
     * its chunk unloads, and then it is neither working nor findable — which is
     * how a Collect order used to end with a minion simply gone. Radius 2 puts
     * the chunk at level 31, which is entity-ticking, so the shade keeps mining
     * and Recall can still reach it from a thousand blocks out.
     */
    private static final TicketType<ChunkPos> SHADE_TICKET =
            TicketType.create("edanthro_shade", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_RADIUS = 2;
    private static final ResourceLocation SCALE_ID = id("minion_scale");
    private static final String OWNER_TAG = "edanthro_owner_";
    private static final String SLOT_TAG = "edanthro_slot_";

    private static final class Shade {
        UUID entity;
        int slot;
        Order order = Order.DEFEND;
        /** What the owner asked for. Null means "whatever is worth taking". */
        Block wanted;
        /** Ticks an Attack order has spent with nothing to hunt. */
        int idleTicks;
        /** Last place we actually saw it, so an unloaded shade can be fetched. */
        BlockPos lastPos;
        /** Set while it is away digging; the entity does not exist meanwhile. */
        Dig dig;
        /** The chunk we are currently holding open for it. */
        ChunkPos ticket;
        /** The crystal this shade raised, and the block it stands on. */
        UUID crystal;
        BlockPos bedrock;
        /** Where it has gone off to build. Null when it is not on a job. */
        BlockPos work;
    }

    /**
     * A shade's errand, while it is away.
     *
     * It has left the world entirely for the duration — no entity to lose, no
     * chunk to keep open, nothing to walk into a wall. The search runs a few
     * chunk columns per tick so a hundred-block sweep never lands as one hitch,
     * and the trip home is the cooldown.
     */
    private static final class Dig {
        Block quarry;
        BlockPos origin;
        ResourceKey<Level> dimension;
        List<ChunkPos> route;
        int cursor;
        final List<BlockPos> found = new ArrayList<>();
        long returnAt;
    }

    /** Who the court is angry at, and until when. */
    private record Grudge(UUID target, long until) {
    }

    /** Running tally of who has been hitting the owner. */
    private static final class Strike {
        UUID attacker;
        long last;
        int hits;
    }

    private static final Map<UUID, List<Shade>> COURT = new HashMap<>();
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();
    private static final Map<UUID, Grudge> GRUDGES = new HashMap<>();
    private static final Map<UUID, Strike> STRIKES = new HashMap<>();

    private DragonMinions() {
    }

    // ---------------------------------------------------------------- summon

    public static void summon(ServerPlayer owner) {
        List<Shade> court = COURT.computeIfAbsent(owner.getUUID(), u -> new ArrayList<>());
        ServerLevel level = owner.serverLevel();
        prune(level, court);
        adoptStrays(owner, level, court);
        if (court.size() >= MAX_PER_PLAYER) {
            say(owner, "All four already answer you.", ChatFormatting.DARK_PURPLE, true);
            return;
        }
        int slot = freeSlot(court);

        EnderMan minion = spawnShade(owner, level, slot);
        if (minion == null) {
            return;
        }

        Shade shade = new Shade();
        shade.entity = minion.getUUID();
        shade.slot = slot;
        court.add(shade);
        rename(minion, shade);
        SELECTED.putIfAbsent(owner.getUUID(), slot);

        level.playSound(null, minion.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 1.0F, 0.6F);
        say(owner, NAMES[slot] + " rises and waits on you.", TINTS[slot], false);
        pushState(owner);
    }

    /**
     * Drop a shade only when we have actually seen it die.
     *
     * {@code getEntity} answers null for an unloaded entity exactly as it does
     * for a dead one, and an evasive jump lands a thousand blocks away — so
     * the old check quietly struck the whole court off the roll the moment you
     * left, freed their slots, and let you summon a second Orrin standing next
     * to the first. Absent is not dead.
     */
    private static void prune(ServerLevel level, List<Shade> court) {
        court.removeIf(s -> {
            if (s.entity == null) {
                return false;                  // away on an errand, not dead
            }
            if (level.getEntity(s.entity) instanceof EnderMan e && !e.isAlive()) {
                releaseChunk(level, s);
                return true;
            }
            return false;
        });
    }

    /**
     * Re-adopt shades the court has lost track of, and put down duplicates.
     *
     * Every shade carries its owner and its slot as tags, so one that has come
     * adrift can be recognised and slotted back in. If its slot is already held
     * by somebody else, it is a duplicate of exactly the kind the bug above
     * produced, and it is dismissed.
     */
    private static void adoptStrays(ServerPlayer owner, ServerLevel level, List<Shade> court) {
        String ownerTag = OWNER_TAG + owner.getUUID().toString().replace("-", "");
        for (EnderMan stray : level.getEntitiesOfClass(EnderMan.class,
                owner.getBoundingBox().inflate(128.0),
                e -> e.isAlive() && e.getTags().contains(ownerTag))) {
            boolean known = false;
            for (Shade s : court) {
                // A shade away on an errand has no entity at all, so this has
                // to be null-safe or summoning one while another digs throws.
                if (stray.getUUID().equals(s.entity)) {
                    known = true;
                    break;
                }
            }
            if (known) {
                continue;
            }
            int slot = slotOf(stray);
            if (slot < 0) {
                continue;
            }
            boolean taken = false;
            for (Shade s : court) {
                if (s.slot == slot) {
                    taken = true;
                    break;
                }
            }
            if (taken) {
                stray.discard();          // a second of somebody who already stands here
                say(owner, "A second " + NAMES[slot] + " is dismissed.",
                        ChatFormatting.DARK_GRAY, true);
                continue;
            }
            Shade shade = new Shade();
            shade.entity = stray.getUUID();
            shade.slot = slot;
            court.add(shade);
            rename(stray, shade);
            say(owner, NAMES[slot] + " rejoins you.", TINTS[slot], true);
        }
    }

    private static int slotOf(EnderMan minion) {
        for (String tag : minion.getTags()) {
            if (tag.startsWith(SLOT_TAG)) {
                try {
                    int slot = Integer.parseInt(tag.substring(SLOT_TAG.length()));
                    if (slot >= 0 && slot < MAX_PER_PLAYER) {
                        return slot;
                    }
                } catch (NumberFormatException ignored) {
                    // a tag we did not write; not ours to interpret
                }
            }
        }
        return -1;
    }

    /** Hold the chunk the shade is standing in, releasing whichever it left. */
    private static void holdChunk(ServerLevel level, Shade shade, EnderMan minion) {
        ChunkPos now = new ChunkPos(minion.blockPosition());
        if (now.equals(shade.ticket)) {
            return;
        }
        releaseChunk(level, shade);
        level.getChunkSource().addRegionTicket(SHADE_TICKET, now, TICKET_RADIUS, now);
        shade.ticket = now;
    }

    private static void releaseChunk(ServerLevel level, Shade shade) {
        if (shade.ticket != null) {
            level.getChunkSource().removeRegionTicket(
                    SHADE_TICKET, shade.ticket, TICKET_RADIUS, shade.ticket);
            shade.ticket = null;
        }
    }

    /** Let the world close up again when the owner logs out. */
    public static void onLeave(ServerPlayer owner) {
        List<Shade> court = COURT.get(owner.getUUID());
        if (court == null) {
            return;
        }
        ServerLevel level = owner.serverLevel();
        for (Shade shade : court) {
            releaseChunk(level, shade);
        }
    }

    /** Is this one of somebody's shades? Used to keep them from blinking away. */
    public static boolean isShade(EnderMan minion) {
        for (String tag : minion.getTags()) {
            if (tag.startsWith(OWNER_TAG)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bring the court along when the dragon jumps.
     *
     * Evade, warp and going home all cover ground no enderman is going to walk,
     * so the retinue is carried rather than left behind. A shade whose chunk
     * has already unloaded is fetched by loading the chunk we last saw it in.
     */
    public static void recall(ServerPlayer owner) {
        List<Shade> court = COURT.get(owner.getUUID());
        if (court == null || court.isEmpty()) {
            return;
        }
        ServerLevel level = owner.serverLevel();
        for (Shade shade : court) {
            if (shade.dig != null) {
                continue;                       // away digging; it comes back on its own
            }
            EnderMan minion = resolve(level, shade);
            if (minion == null) {
                continue;
            }
            Vec3 look = owner.getLookAngle();
            if (!blink(level, minion, owner.getX() - look.x * 3.0,
                    owner.getY(), owner.getZ() - look.z * 3.0, 16)) {
                minion.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            }
        }
    }

    /** The shade's entity, loading the chunk it was last seen in if need be. */
    private static EnderMan resolve(ServerLevel level, Shade shade) {
        if (shade.entity == null) {
            return null;                          // away on an errand; there is no entity
        }
        if (level.getEntity(shade.entity) instanceof EnderMan found) {
            return found;
        }
        if (shade.lastPos != null) {
            level.getChunk(shade.lastPos);
            if (level.getEntity(shade.entity) instanceof EnderMan loaded) {
                return loaded;
            }
        }
        return null;
    }

    /** Raise one, scaled and tagged, a few paces in front of the dragon. */
    private static EnderMan spawnShade(ServerPlayer owner, ServerLevel level, int slot) {
        EnderMan minion = EntityType.ENDERMAN.create(level);
        if (minion == null) {
            return null;
        }
        Vec3 look = owner.getLookAngle();
        Vec3 at = owner.position().add(look.x * 5.0, 0.0, look.z * 5.0);
        minion.moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
        AttributeInstance scale = minion.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.addPermanentModifier(new AttributeModifier(
                    SCALE_ID, SCALE - 1.0, AttributeModifier.Operation.ADD_VALUE));
        }
        minion.setPersistenceRequired();
        minion.setCustomNameVisible(true);
        minion.addTag(OWNER_TAG + owner.getUUID().toString().replace("-", ""));
        minion.addTag(SLOT_TAG + slot);
        level.addFreshEntity(minion);
        burst(level, minion);
        return minion;
    }

    private static int freeSlot(List<Shade> court) {
        boolean[] used = new boolean[MAX_PER_PLAYER];
        for (Shade s : court) {
            if (s.slot >= 0 && s.slot < MAX_PER_PLAYER) {
                used[s.slot] = true;
            }
        }
        for (int i = 0; i < MAX_PER_PLAYER; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------- commands


    /**
     * Open the court screen.
     *
     * Orders used to cycle on a key, which meant reaching Collect cost you
     * whatever the shade was already carrying and there was no way to name a
     * quarry without looking at one. Now the key just asks the server for the
     * roster and the client puts a screen up with every order one click away.
     */
    public static void openCommandUi(ServerPlayer owner) {
        sendState(owner, true);
    }

    /** Quiet refresh for a screen that is already up. */
    public static void pushState(ServerPlayer owner) {
        sendState(owner, false);
    }

    /**
     * Reads a snapshot rather than {@link #living}, because this is also
     * called from inside the tick loop — pruning the court there would be
     * pulling the rug out from under the iteration.
     */
    private static void sendState(ServerPlayer owner, boolean open) {
        List<ShadeStatePayload.Entry> entries = new ArrayList<>();
        ServerLevel level = owner.serverLevel();
        for (Shade s : List.copyOf(COURT.getOrDefault(owner.getUUID(), List.of()))) {
            boolean away = s.dig != null;
            if (!away && (s.entity == null
                    || !(level.getEntity(s.entity) instanceof EnderMan e) || !e.isAlive())) {
                continue;
            }
            entries.add(new ShadeStatePayload.Entry(s.slot, NAMES[s.slot], s.order.ordinal(),
                    s.wanted == null ? "" : BuiltInRegistries.BLOCK.getKey(s.wanted).toString(),
                    away ? (int) Math.max(1, (s.dig.returnAt - level.getGameTime() + 19) / 20) : 0,
                    away ? carrying(s.dig, level.getGameTime()) : 0));
        }
        entries.sort(Comparator.comparingInt(ShadeStatePayload.Entry::slot));
        ServerPlayNetworking.send(owner, new ShadeStatePayload(open, List.copyOf(entries)));
    }

    /**
     * One shade, one order, straight from the screen.
     *
     * Collect despatches rather than sets a duty: the shade leaves at once and
     * the order reverts to Defend when it walks back in.
     */
    public static void applyOrder(ServerPlayer owner, int slot, int ordinal, String quarry) {
        Shade shade = null;
        for (Shade s : living(owner)) {
            if (s.slot == slot) {
                shade = s;
                break;
            }
        }
        if (shade == null) {
            pushState(owner);
            return;
        }
        Order[] all = Order.values();
        Order order = all[Math.floorMod(ordinal, all.length)];
        ServerLevel level = owner.serverLevel();

        // Pet and Recall are things you do to a shade, not jobs you give it:
        // they happen and the standing order carries on untouched.
        if (order.momentary()) {
            if (shade.dig != null) {
                if (order == Order.RECALL) {
                    // Cut the errand short and take what has accrued so far.
                    // returnHome raises the shade again, so it walks back in as
                    // an entity exactly as a finished trip does.
                    int hand = carrying(shade.dig, level.getGameTime());
                    List<BlockPos> haul =
                            VeinScan.veins(shade.dig.found, shade.dig.origin, hand);
                    say(owner, NAMES[slot] + ": "
                            + ShadeVoice.duty(Order.RECALL, level.random, ""),
                            TINTS[slot], false);
                    returnHome(owner, level, shade, haul);
                    return;
                }
                long left = (shade.dig.returnAt - level.getGameTime() + 19) / 20;
                say(owner, NAMES[slot] + " is away digging — back in "
                        + Math.max(1, left) + "s.", ChatFormatting.DARK_GRAY, true);
                return;
            }
            EnderMan target = resolve(level, shade);
            if (target == null) {
                say(owner, NAMES[slot] + " cannot be reached — not in this world.",
                        ChatFormatting.DARK_GRAY, true);
                return;
            }
            if (order == Order.RECALL) {
                burst(level, target);          // where it left
                Vec3 look = owner.getLookAngle();
                if (!blink(level, target, owner.getX() - look.x * 3.0, owner.getY(),
                        owner.getZ() - look.z * 3.0, 16)) {
                    target.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                }
                // Recall has to end the errand, not just interrupt it: a shade
                // that arrives and is told nothing has changed goes straight
                // back out, which looks exactly like never having come.
                burst(level, target);          // and where it arrived
                int moved = (int) Math.sqrt(target.distanceToSqr(owner));
                standDown(owner, target, shade,
                        ShadeVoice.duty(Order.RECALL, level.random, "")
                                + " (" + moved + "m away)");
            } else if (order == Order.PORTAL) {
                BlockPos cut = raiseGate(owner, level, target);
                if (cut == null) {
                    say(owner, NAMES[slot] + " has no room to raise a gate here.",
                            ChatFormatting.DARK_GRAY, true);
                } else {
                    say(owner, NAMES[slot] + ": \"A way through, majesty.\" ("
                            + cut.getX() + ", " + cut.getY() + ", " + cut.getZ() + ")",
                            TINTS[slot], false);
                }
            } else {
                target.getLookControl().setLookAt(owner, 60.0F, 60.0F);
                EndermanAffection.adore(level, target);
                say(owner, NAMES[slot] + ": "
                        + ShadeVoice.duty(Order.PET, level.random, ""),
                        TINTS[slot], false);
            }
            pushState(owner);
            return;
        }

        // A shade that is away is away. Its entity does not exist to be given a
        // new duty, and setting the field anyway loses the order silently:
        // returnHome puts it back on Defend the moment it walks in. Say what is
        // actually happening and how long it has left.
        if (shade.dig != null) {
            long left = (shade.dig.returnAt - level.getGameTime() + 19) / 20;
            say(owner, NAMES[slot] + " is away digging — back in "
                    + Math.max(1, left) + "s.", ChatFormatting.DARK_GRAY, true);
            return;
        }

        EnderMan minion = resolve(level, shade);
        shade.order = order;
        shade.idleTicks = 0;
        if (order == Order.COLLECT) {
            Block wanted = parseBlock(quarry);
            if (wanted == null) {
                wanted = shade.wanted;             // keep what it was already after
            }
            if (wanted == null) {
                say(owner, NAMES[slot] + ": \"Name what you want and I will find it.\"",
                        ChatFormatting.DARK_GRAY, true);
                return;                            // a vein needs something to follow
            }
            shade.wanted = wanted;
            if (minion != null) {
                despatch(owner, minion, level, shade, wanted);
                pushState(owner);
                return;
            }
        }
        // A quarry is a standing preference, not part of the order: switching
        // to Defend and back should not make the shade forget what it digs for.
        SELECTED.put(owner.getUUID(), slot);
        if (minion != null) {
            rename(minion, shade);
            minion.setTarget(null);
        }
        say(owner, NAMES[slot] + ": " + line(shade, level.random), TINTS[slot], false);
        pushState(owner);
    }

    /** Name the quarry for the shade currently being addressed. */
    public static boolean setQuarry(ServerPlayer owner, Block block) {
        Shade shade = lookedAtShade(owner);
        if (shade == null) {
            return false;
        }
        applyOrder(owner, shade.slot, Order.COLLECT.ordinal(),
                BuiltInRegistries.BLOCK.getKey(block).toString());
        return true;
    }

    /**
     * What a shade says on taking an order. Ten answers per duty, picked at
     * random -- four of them replying with the same sentence was what made the
     * court read as spawned mobs rather than as anyone's retinue.
     */
    private static String line(Shade shade, RandomSource random) {
        String quarry = shade.wanted == null ? "whatever I can find"
                                             : shade.wanted.getName().getString();
        return ShadeVoice.duty(shade.order, random, quarry);
    }

    private static Block parseBlock(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(raw.trim().toLowerCase(java.util.Locale.ROOT));
        if (key == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
        return block == Blocks.AIR ? null : block;
    }


    /**
     * Who a typed command speaks to.
     *
     * There used to be a key for this: N cycled a "listening" shade that only
     * /shade collect ever read. The court screen addresses each shade by name on
     * its own row, so the key was a second, worse way to say the same thing --
     * and one whose state you could not see. Look at a shade to name it, or say
     * nothing and the first one answers.
     */
    private static Shade lookedAtShade(ServerPlayer owner) {
        List<Shade> court = living(owner);
        if (court.isEmpty()) {
            return null;
        }
        LivingEntity aimed = lookedAt(owner, owner.serverLevel());
        if (aimed != null) {
            for (Shade s : court) {
                if (aimed.getUUID().equals(s.entity)) {
                    return s;
                }
            }
        }
        return court.get(0);
    }

    private static Shade selected(ServerPlayer owner) {
        int slot = SELECTED.getOrDefault(owner.getUUID(), -1);
        for (Shade s : living(owner)) {
            if (s.slot == slot) {
                return s;
            }
        }
        List<Shade> court = living(owner);
        return court.isEmpty() ? null : court.get(0);
    }

    private static List<Shade> living(ServerPlayer owner) {
        List<Shade> court = COURT.computeIfAbsent(owner.getUUID(), u -> new ArrayList<>());
        ServerLevel level = owner.serverLevel();
        prune(level, court);
        return court;
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            List<Shade> court = COURT.get(owner.getUUID());
            if (court == null || court.isEmpty()) {
                continue;
            }
            ServerLevel level = owner.serverLevel();
            int before = court.size();
            prune(level, court);
            if (court.size() != before) {
                pushState(owner);
            }
            LivingEntity avenge = grudge(owner, level);
            LivingEntity watched = lookedAt(owner, level);

            // The court screen only knows what it was last told, and until now it
            // was only ever told when something happened. A haul that climbs a
            // block every half second is not something happening, so while anyone
            // is away, keep it fed.
            if (level.getGameTime() % STATE_PULSE == 0) {
                for (Shade away : court) {
                    if (away.dig != null) {
                        pushState(owner);
                        break;
                    }
                }
            }

            // A copy: an errand that ends can drop its shade from the court, and
            // that happens from inside this loop.
            for (Shade shade : List.copyOf(court)) {
                if (shade.dig != null) {
                    digTick(owner, level, shade);
                    continue;                  // away; there is no entity to drive
                }
                if (shade.entity == null
                        || !(level.getEntity(shade.entity) instanceof EnderMan minion)) {
                    continue;
                }
                if (minion.getTarget() == owner) {
                    minion.setTarget(null);      // never turn on the summoner
                }
                shade.lastPos = minion.blockPosition();
                holdChunk(level, shade, minion);

                // Walled into stone, or fallen through the floor. Dig it out
                // rather than let it suffocate: EndermanTeleportMixin took away
                // the escape blink a wild enderman would have used, so this is
                // the only way back for one that has got itself stuck.
                if (stranded(level, minion)) {
                    if (!blink(level, minion, minion.getX(), minion.getY() + 4, minion.getZ(), 48)
                            && !blink(level, minion, owner.getX(), owner.getY(), owner.getZ(), 24)) {
                        minion.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                    }
                    say(owner, NAMES[shade.slot] + " claws free of the rock.",
                            ChatFormatting.DARK_GRAY, true);
                    continue;
                }

                // Meet the dragon's eye. Looking at a shade is also how you
                // remind it where it is supposed to be.
                if (watched == minion) {
                    minion.getLookControl().setLookAt(owner, 60.0F, 60.0F);
                    if (shade.order != Order.COLLECT) {
                        follow(owner, minion, HEEL, 1.25);
                    }
                }
                // Someone is beating on Jean. Any shade close enough to see it
                // stops what it is doing; the ones away digging are not here to
                // be asked.
                if (avenge != null && minion.distanceToSqr(owner) < GRUDGE_RANGE * GRUDGE_RANGE) {
                    minion.setTarget(avenge);
                    if (minion.distanceToSqr(avenge) > 9.0) {
                        minion.getNavigation().moveTo(avenge, 1.3);
                    }
                    continue;
                }
                switch (shade.order) {
                    case ATTACK -> attack(owner, minion, level, shade);
                    case CRYSTAL -> crystal(owner, minion, level, shade);
                    case DISMANTLE -> dismantle(owner, minion, level, shade);
                    // Pet and Recall are never standing orders; if one somehow
                    // lands here, standing with you is the right thing to do.
                    default -> defend(owner, minion, level);
                }
            }
        }
    }


    /**
     * A gate the size of what has to walk through it.
     *
     * Vanilla's portal is a two-by-three hole, which is a doorway for a person
     * and a wall for anything else. A dragon is 2.67 across and 8 tall, so it
     * cannot use its own world's portals at all — the frame is smaller than the
     * body. The interior here is measured off the actual hitbox and then given
     * a block of clearance on each side, so it fits whatever DragonConfig says
     * the dragon is rather than whatever it happened to be when this was
     * written.
     *
     * Well inside vanilla's limits: PortalShape.MAX_WIDTH and MAX_HEIGHT are 21
     * each, and this asks for roughly 5 by 10.
     *
     * The frame is raised on the axis across your line of sight, so it faces
     * you, and lit with our own fire — BaseFireBlock.onPlace runs the portal
     * check for any fire, so the ignition is vanilla's and the shape is ours.
     */
    private static BlockPos raiseGate(ServerPlayer owner, ServerLevel level, EnderMan shade) {
        int inner = NetherGate.innerFor(owner);
        int tall = NetherGate.tallFor(owner);

        // Across the look direction, so you walk into its face rather than its
        // edge. The wider horizontal component of the look decides the axis.
        Vec3 look = owner.getLookAngle();
        boolean alongX = Math.abs(look.x) < Math.abs(look.z);
        net.minecraft.core.Direction across = alongX
                ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.SOUTH;

        BlockPos foot = owner.blockPosition()
                .relative(net.minecraft.core.Direction.fromYRot(owner.getYRot()), 4)
                .offset(-(alongX ? inner / 2 : 0), 0, -(alongX ? 0 : inner / 2));
        foot = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                foot);

        // Everything the frame and the hole will occupy has to be free first,
        // or a half-buried gate is worse than none.
        for (int w = -1; w <= inner; w++) {
            for (int h = -1; h <= tall; h++) {
                BlockPos at = foot.relative(across, w).above(h);
                if (!level.getBlockState(at).canBeReplaced()
                        && !level.getBlockState(at).is(Blocks.OBSIDIAN)) {
                    return null;
                }
            }
        }

        burst(level, shade);
        // carve = false: a shade refuses rather than burying a gate in
        // somebody's hillside, and the clearance check above is that refusal.
        NetherGate.raise(level, foot, across, inner, tall, false);
        level.playSound(null, foot, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.8F, 1.4F);
        return foot;
    }

    /**
     * Take note of whoever is hurting the owner.
     *
     * One hit is an accident; a second inside {@link #GRUDGE_WINDOW} is a
     * fight, and then the whole court knows the name.
     */
    public static void retaliate(Player owner, Entity attacker) {
        if (!(owner instanceof ServerPlayer sp) || !(attacker instanceof LivingEntity foe)
                || foe == owner || !foe.isAlive() || isOwnedBy(owner, foe)) {
            return;
        }
        List<Shade> court = COURT.get(owner.getUUID());
        if (court == null || court.isEmpty()) {
            return;
        }
        long now = sp.serverLevel().getGameTime();
        Strike strike = STRIKES.computeIfAbsent(owner.getUUID(), u -> new Strike());
        if (!foe.getUUID().equals(strike.attacker) || now - strike.last > GRUDGE_WINDOW) {
            strike.attacker = foe.getUUID();
            strike.hits = 0;
        }
        strike.last = now;
        strike.hits++;
        if (strike.hits < RETALIATE_AFTER) {
            return;
        }
        Grudge previous = GRUDGES.put(owner.getUUID(), new Grudge(foe.getUUID(), now + GRUDGE_TICKS));
        if (previous == null || !previous.target().equals(foe.getUUID())) {
            say(sp, "The court turns on " + foe.getName().getString() + ".",
                    ChatFormatting.DARK_RED, true);
        }
    }

    private static LivingEntity grudge(ServerPlayer owner, ServerLevel level) {
        Grudge held = GRUDGES.get(owner.getUUID());
        if (held == null) {
            return null;
        }
        if (level.getGameTime() > held.until()
                || !(level.getEntity(held.target()) instanceof LivingEntity foe)
                || !foe.isAlive() || foe == owner) {
            GRUDGES.remove(owner.getUUID());
            return null;
        }
        return foe;
    }

    private static void attack(ServerPlayer owner, EnderMan minion, ServerLevel level, Shade shade) {
        LivingEntity aim = lookedAt(owner, level);
        if (aim != null && aim != owner && !isOwnedBy(owner, aim)) {
            minion.setTarget(aim);
            shade.idleTicks = 0;
            return;
        }
        if (minion.getTarget() == null || !minion.getTarget().isAlive()) {
            minion.setTarget(nearestFoe(owner, minion, level));
        }
        if (minion.getTarget() != null) {
            shade.idleTicks = 0;
        } else if (++shade.idleTicks > IDLE_REVERT) {
            // nothing left to hunt: fall back to standing with you
            standDown(owner, minion, shade, "\"Nothing stirs. I stand with you.\"");
            return;
        }
        follow(owner, minion, 24.0, 1.15);
    }

    /** Back to the default duty, which is you. */
    private static void standDown(ServerPlayer owner, EnderMan minion, Shade shade, String line) {
        shade.order = Order.DEFEND;
        shade.wanted = null;
        shade.idleTicks = 0;
        minion.setTarget(null);
        rename(minion, shade);
        say(owner, NAMES[shade.slot] + ": " + line, Order.DEFEND.colour, false);
        pushState(owner);
    }

    /**
     * The end tearing open around a shade.
     *
     * Endermen trail portal particles constantly, so a departure or an arrival
     * has to be an order of magnitude more than the ambient drift to read as an
     * event rather than as the same idle shimmer. Scaled to the shade's own
     * height so a four-block minion is wrapped in it rather than standing in a
     * puff around its ankles.
     */
    private static void burst(ServerLevel level, double x, double y, double z, double height) {
        level.sendParticles(ParticleTypes.PORTAL, x, y + height * 0.5, z,
                180, 0.6, height * 0.45, 0.6, 1.1);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + height * 0.5, z,
                60, 0.4, height * 0.35, 0.4, 0.5);
    }

    private static void burst(ServerLevel level, EnderMan minion) {
        burst(level, minion.getX(), minion.getY(), minion.getZ(),
                minion.getBbHeight());
    }

    private static void defend(ServerPlayer owner, EnderMan minion, ServerLevel level) {
        minion.setCarriedBlock(null);              // off duty, empty-handed
        LivingEntity threat = minion.getTarget();
        if (threat == null || !threat.isAlive()) {
            for (Monster m : level.getEntitiesOfClass(Monster.class,
                    owner.getBoundingBox().inflate(16.0),
                    e -> e != minion && e.isAlive() && e.getTarget() == owner)) {
                minion.setTarget(m);
                return;
            }
            minion.setTarget(null);
            follow(owner, minion, HEEL, 1.15);
        }
    }

    /**
     * Send a shade off to fetch a seam.
     *
     * It leaves the world outright rather than walking there. Everything that
     * went wrong with the old Collect came from a real entity out in real
     * terrain: it hopped into ungenerated chunks, got walled into stone,
     * suffocated where its escape blink had been taken away, and could not be
     * recalled because it was no longer anywhere. An errand with no entity has
     * none of those failure modes.
     *
     * The trip is the cooldown. The search itself finishes in a few seconds;
     * the shade stays away for {@value #TRIP_TICKS} ticks regardless, because a
     * retinue that returns instantly is a vending machine.
     */
    private static void despatch(ServerPlayer owner, EnderMan minion, ServerLevel level,
                                 Shade shade, Block quarry) {
        Dig dig = new Dig();
        dig.quarry = quarry;
        dig.origin = owner.blockPosition();
        dig.dimension = level.dimension();
        dig.route = VeinScan.route(new ChunkPos(dig.origin));
        dig.returnAt = level.getGameTime() + TRIP_TICKS;
        shade.dig = dig;

        burst(level, minion);
        level.playSound(null, minion.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 1.0F, 0.7F);
        releaseChunk(level, shade);
        minion.discard();
        shade.entity = null;

        say(owner, NAMES[shade.slot] + ": "
                + ShadeVoice.duty(Order.COLLECT, level.random, quarry.getName().getString()),
                TINTS[shade.slot], false);
    }

    /**
     * Work the errand: a few chunk columns a tick, then come back.
     *
     * The blocks are only taken from the world at the moment of return, so a
     * seam the dragon mines out in the meantime simply is not there any more.
     */
    private static void digTick(ServerPlayer owner, ServerLevel level, Shade shade) {
        Dig dig = shade.dig;
        if (!level.dimension().equals(dig.dimension)) {
            returnHome(owner, level, shade, List.of());   // followed you somewhere else
            return;
        }
        for (int i = 0; i < CHUNKS_PER_TICK && dig.cursor < dig.route.size(); i++) {
            ChunkPos at = dig.route.get(dig.cursor++);
            if (dig.origin.distSqr(new BlockPos(at.getMiddleBlockX(), dig.origin.getY(),
                    at.getMiddleBlockZ())) > (VeinScan.RADIUS + 12) * (VeinScan.RADIUS + 12)) {
                continue;                                  // corner of the square, out of reach
            }
            VeinScan.scanChunk(level, at, dig.quarry, dig.found);
        }
        if (level.getGameTime() < dig.returnAt) {
            return;                                        // still gathering
        }
        List<BlockPos> haul = VeinScan.veins(dig.found, dig.origin,
                carrying(dig, level.getGameTime()));
        returnHome(owner, level, shade, haul);
    }

    /**
     * How much the shade has gathered by now: nothing at the start, the full
     * haul by the end, one block at a time in between.
     *
     * Straight-line accrual is what makes an early Recall a real decision
     * rather than an exploit. The rate is constant, so recalling at ten seconds
     * for a third of the load and sending it straight back out earns exactly
     * what waiting would have -- what you actually buy is the shade standing
     * next to you again in the meantime, and what you pay is having to give the
     * order twice.
     *
     * The search finishes inside three seconds, long before the meter means
     * anything, so an early Recall is limited by the meter rather than by how
     * far the sweep has got.
     */
    private static int carrying(Dig dig, long now) {
        long elapsed = TRIP_TICKS - (dig.returnAt - now);
        int meter = elapsed <= 0 ? 0
                  : elapsed >= TRIP_TICKS ? VeinScan.HAUL
                  : (int) (elapsed * VeinScan.HAUL / TRIP_TICKS);
        // Bounded by what the sweep actually turned up. The clock is a pace, not
        // a promise: a quarry with ten blocks of it inside a hundred stops the
        // count at ten and holds there, and one with none never leaves zero.
        // Nothing is conjured — every block counted here is a real position
        // found in the world, and returnHome checks each is still there before
        // taking it.
        return Math.min(meter, Math.min(dig.found.size(), VeinScan.HAUL));
    }

    /** The shade steps back out of nowhere, hands over the haul, and stands down. */
    private static void returnHome(ServerPlayer owner, ServerLevel level, Shade shade,
                                   List<BlockPos> haul) {
        Block quarry = shade.dig.quarry;
        // Cut short, rather than having searched and found nothing. The two look
        // identical from here -- both come home with an empty haul -- and saying
        // "none within reach" to someone who recalled after two seconds blames
        // the world for the player's own impatience.
        boolean early = level.getGameTime() < shade.dig.returnAt;
        shade.dig = null;
        shade.order = Order.DEFEND;

        int taken = 0;
        for (BlockPos pos : haul) {
            if (level.getBlockState(pos).is(quarry) && level.removeBlock(pos, false)) {
                taken++;                                   // only what is still there
            }
        }
        EnderMan minion = spawnShade(owner, level, shade.slot);
        if (minion == null) {
            // Nothing came back and nothing can: a shade with neither an entity
            // nor an errand is invisible to every loop here but still holds its
            // slot, so the court would silently shrink by one for good. Give the
            // slot back instead.
            COURT.getOrDefault(owner.getUUID(), new ArrayList<>()).remove(shade);
            say(owner, NAMES[shade.slot] + " does not return.",
                    ChatFormatting.DARK_GRAY, false);
            pushState(owner);
            return;
        }
        shade.entity = minion.getUUID();
        rename(minion, shade);
        if (taken > 0) {
            hand(owner, quarry, taken);
            say(owner, NAMES[shade.slot] + ": "
                    + ShadeVoice.haul(level.random, taken, quarry.getName().getString()),
                    TINTS[shade.slot], false);
        } else if (early) {
            say(owner, NAMES[shade.slot] + ": " + ShadeVoice.early(level.random),
                    TINTS[shade.slot], false);
        } else {
            say(owner, NAMES[shade.slot] + ": "
                    + ShadeVoice.barren(level.random, quarry.getName().getString()),
                    TINTS[shade.slot], false);
        }
        pushState(owner);
    }

    /** Into your hands, or at your feet if there is no room. */
    private static void hand(ServerPlayer owner, Block block, int count) {
        while (count > 0) {
            ItemStack stack = new ItemStack(block, Math.min(count, block.asItem().getDefaultMaxStackSize()));
            count -= stack.getCount();
            if (!owner.getInventory().add(stack)) {
                owner.drop(stack, false);
            }
        }
    }

    /**
     * Raise a crystal, and keep exactly one.
     *
     * It used to hunt for bedrock already in the world and place on that, which
     * meant it did nothing at all anywhere the bedrock is at the bottom of the
     * world — that is to say, everywhere. A shade brings its own: one block of
     * bedrock set down, one crystal on top of it, and then it guards the thing.
     * Nothing further is raised until that crystal is destroyed.
     */
    private static void crystal(ServerPlayer owner, EnderMan minion, ServerLevel level, Shade shade) {
        minion.setTarget(null);
        if (shade.crystal != null
                && level.getEntity(shade.crystal) instanceof EndCrystal standing
                && standing.isAlive()) {
            minion.setCarriedBlock(null);
            shade.work = null;
            follow(owner, minion, HEEL, 1.1);      // its crystal still stands; guard it
            return;
        }
        shade.crystal = null;

        // Pick somewhere to build, well clear of the dragon, and set off for it
        // carrying the stone. An enderman holding a block already reads as one
        // about to put it down -- vanilla renders it in both hands -- so the
        // errand tells its own story on the way out.
        if (shade.work == null) {
            if (level.getGameTime() % CRYSTAL_WORK != 0) {
                minion.setCarriedBlock(null);
                follow(owner, minion, HEEL, 1.1);
                return;
            }
            shade.work = worksite(level, owner);
            if (shade.work == null) {
                follow(owner, minion, HEEL, 1.1);
                return;
            }
            minion.setCarriedBlock(Blocks.BEDROCK.defaultBlockState());
            say(owner, NAMES[shade.slot] + ": "
                    + ShadeVoice.duty(Order.CRYSTAL, level.random, ""),
                    TINTS[shade.slot], false);
        }
        BlockPos spot = shade.work;
        if (!minion.blockPosition().closerThan(spot, 3.0)) {
            if (minion.blockPosition().distSqr(spot) > FETCH_DISTANCE * FETCH_DISTANCE) {
                hopToward(level, minion, spot.getX(), spot.getZ(), 0.0);
            } else {
                minion.getNavigation().moveTo(spot.getX() + 0.5, spot.getY() + 1,
                        spot.getZ() + 0.5, 1.15);
            }
            return;                                // still walking it over
        }
        if (!buildable(level, spot)) {
            shade.work = null;                     // somebody built there while it walked
            minion.setCarriedBlock(null);
            return;
        }
        // Arrived. Face the work and swing at it, then set the stone down.
        minion.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5);
        minion.swing(InteractionHand.MAIN_HAND);
        minion.setCarriedBlock(null);
        shade.work = null;
        level.setBlockAndUpdate(spot, Blocks.BEDROCK.defaultBlockState());
        EndCrystal crystal = new EndCrystal(level,
                spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5);
        crystal.setShowBottom(false);
        level.addFreshEntity(crystal);
        shade.crystal = crystal.getUUID();
        shade.bedrock = spot;
        level.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 1.0F, 1.4F);
        say(owner, NAMES[shade.slot] + " raises a crystal at "
                + spot.getX() + ", " + (spot.getY() + 1) + ", " + spot.getZ() + ".",
                TINTS[shade.slot], false);
    }

    /**
     * Somewhere to build, chosen well away from the dragon.
     *
     * A crystal wants clear sky and a wide berth, and watching a shade set one
     * down inside your own footprint reads as clutter rather than as work. Ten
     * to sixteen blocks out is far enough to be an errand and near enough to
     * watch.
     */
    private static BlockPos worksite(ServerLevel level, ServerPlayer owner) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double out = 10.0 + level.random.nextDouble() * 6.0;
            int x = (int) Math.floor(owner.getX() + Math.cos(angle) * out);
            int z = (int) Math.floor(owner.getZ() + Math.sin(angle) * out);
            // The heightmap answers minBuildHeight for a chunk it does not hold,
            // which is how things used to end up inside the bedrock. Load first.
            level.getChunk(x >> 4, z >> 4);
            BlockPos ground = new BlockPos(x,
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (buildable(level, ground)) {
                return ground;
            }
        }
        return null;
    }

    /** Ordinary ground with three blocks of sky over it. */
    private static boolean buildable(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (here.isAir() || here.is(Blocks.BEDROCK) || here.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        return level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.above(2)).isAir()
                && level.getBlockState(pos.above(3)).isAir();
    }

    /** Somewhere near the shade with solid footing and headroom for a crystal. */
    private static BlockPos crystalSpot(ServerLevel level, BlockPos base) {
        for (BlockPos pos : BlockPos.randomInCube(level.random, 64, base, 6)) {
            if (buildable(level, pos)) {
                return pos.immutable();
            }
        }
        return null;
    }

    /**
     * Take the bedrock back out.
     *
     * Any shade can pull up any bedrock, including a crystal another shade
     * raised — it used to sample points at random inside its own work radius,
     * which meant it almost never found anything and never crossed the room to
     * reach it. The court's own platforms are remembered, so those are targeted
     * directly; anything else is found by sweeping nearby.
     *
     * Only above {@link #BEDROCK_FLOOR}: the world's own floor is left alone,
     * because pulling that up drops everything standing on it into the void.
     */
    private static void dismantle(ServerPlayer owner, EnderMan minion, ServerLevel level, Shade shade) {
        minion.setTarget(null);
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        BlockPos target = dismantleTarget(owner, level, minion);
        if (target == null) {
            follow(owner, minion, HEEL, 1.1);      // nothing left to pull up
            return;
        }
        if (!minion.blockPosition().closerThan(target, 6.0)) {
            if (minion.blockPosition().distSqr(target) > FETCH_DISTANCE * FETCH_DISTANCE) {
                hopToward(level, minion, target.getX(), target.getZ(), 0.0);
            } else {
                minion.getNavigation().moveTo(target.getX() + 0.5,
                        target.getY() + 1, target.getZ() + 0.5, 1.2);
            }
            return;
        }
        minion.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 1,
                target.getZ() + 0.5);
        minion.swing(InteractionHand.MAIN_HAND);
        for (EndCrystal sitting : level.getEntitiesOfClass(EndCrystal.class,
                new AABB(target.above()).inflate(0.9))) {
            forget(owner, sitting.getUUID(), null);
            sitting.discard();
        }
        level.removeBlock(target, false);
        minion.setCarriedBlock(Blocks.BEDROCK.defaultBlockState());   // carries it off
        forget(owner, null, target);
        level.playSound(null, target, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
        say(owner, NAMES[shade.slot] + " pulls down the bedrock at "
                + target.getX() + ", " + target.getY() + ", " + target.getZ() + ".",
                TINTS[shade.slot], false);
    }

    /** The court's own platforms first, then whatever bedrock is lying about. */
    private static BlockPos dismantleTarget(ServerPlayer owner, ServerLevel level, EnderMan minion) {
        int floor = level.getMinBuildHeight() + BEDROCK_FLOOR;
        BlockPos base = minion.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (Shade other : COURT.getOrDefault(owner.getUUID(), List.of())) {
            BlockPos known = other.bedrock;
            if (known == null || known.getY() <= floor
                    || !level.getBlockState(known).is(Blocks.BEDROCK)) {
                continue;
            }
            double dist = known.distSqr(base);
            if (dist < bestDist) {
                best = known;
                bestDist = dist;
            }
        }
        if (best != null) {
            return best;
        }
        // Nothing of ours in the ledger: sweep, nearest first. Bounded on
        // purpose - this runs twice a second and the ledger covers the
        // common case.
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-8, -8, -8), base.offset(8, 8, 8))) {
            if (pos.getY() <= floor || !level.getBlockState(pos).is(Blocks.BEDROCK)) {
                continue;
            }
            double dist = pos.distSqr(base);
            if (dist < bestDist) {
                best = pos.immutable();
                bestDist = dist;
            }
        }
        return best;
    }

    /** Strike a dismantled crystal or platform off whichever shade raised it. */
    private static void forget(ServerPlayer owner, UUID crystal, BlockPos bedrock) {
        for (Shade s : COURT.getOrDefault(owner.getUUID(), List.of())) {
            if (crystal != null && crystal.equals(s.crystal)) {
                s.crystal = null;
            }
            if (bedrock != null && bedrock.equals(s.bedrock)) {
                s.bedrock = null;
            }
        }
    }

    // ----------------------------------------------------------- movement

    /**
     * Keep station on the dragon.
     *
     * A shade walks after you now rather than blinking about — EndermanTeleportMixin
     * refuses their idle teleports, because a retinue that reappears somewhere
     * else every few seconds is not a retinue. The one exception is losing you
     * entirely: past FETCH_DISTANCE it hops, since no enderman is going to walk
     * a thousand blocks and an evasive jump makes that gap routine.
     */
    private static void follow(ServerPlayer owner, EnderMan minion, double leash, double speed) {
        if (minion.getTarget() != null) {
            return;
        }
        double gap = minion.distanceToSqr(owner);
        if (gap > FETCH_DISTANCE * FETCH_DISTANCE) {
            hopToward(minion.level() instanceof ServerLevel sl ? sl : null,
                    minion, owner.getX(), owner.getZ(), 6.0);
            return;
        }
        // Inside the ring it holds station. Navigation is stopped rather than
        // merely left alone, because a path issued a moment ago is still being
        // walked and would carry it the rest of the way in.
        if (gap < GUARD_RING * GUARD_RING) {
            minion.getNavigation().stop();
            minion.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            return;
        }
        if (gap > leash * leash) {
            // Walk to the ring, not to the dragon. Pathing to the owner's own
            // feet is what made them crowd in and shove: the path only ends when
            // they arrive, and arriving means standing where you are standing.
            Vec3 out = new Vec3(minion.getX() - owner.getX(), 0.0, minion.getZ() - owner.getZ());
            out = out.lengthSqr() < 1.0e-4 ? new Vec3(GUARD_RING, 0.0, 0.0)
                                           : out.normalize().scale(GUARD_RING);
            // A walk is for a shade that is already with you and a dragon who
            // is not going anywhere. Either of those failing is a run: the gap
            // has opened past STRIDE_OUT, or you have started sprinting and
            // walking after you would only end in a teleport at FETCH_DISTANCE.
            boolean hurry = gap > STRIDE_OUT * STRIDE_OUT || owner.isSprinting();
            minion.getNavigation().moveTo(owner.getX() + out.x, owner.getY(),
                    owner.getZ() + out.z, hurry ? speed * RUN : speed);
        }
    }

    /**
     * Enderman-style hop: land on the surface at (x, z), optionally offset.
     *
     * The chunk is forced in FIRST. {@code Level.getHeight} answers
     * {@code minBuildHeight} for a chunk it does not have — and a forage hop is
     * always into terrain nobody has visited — so asking before loading put
     * shades at Y=-64, inside the bedrock. That is the same bug the evasive
     * jump had; this is the copy of it that was left behind.
     */
    private static void hopToward(ServerLevel level, EnderMan minion, double x, double z, double spread) {
        if (level == null) {
            return;
        }
        if (spread > 0) {
            x += (level.random.nextDouble() - 0.5) * spread;
            z += (level.random.nextDouble() - 0.5) * spread;
        }
        BlockPos column = BlockPos.containing(x, 0, z);
        level.getChunk(column);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        blink(level, minion, surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5, 32);
    }

    /**
     * Teleport, but never into somewhere it cannot stand.
     *
     * A shade used to be dropped at a raw depth with nothing checked, which
     * walled it into solid stone. It could not blink out either — the whole
     * point of EndermanTeleportMixin is that shades hold their ground — so it
     * suffocated out there, which is where the court kept going.
     */
    private static boolean blink(ServerLevel level, EnderMan minion,
                                 double x, double y, double z, int span) {
        Vec3 spot = standingRoom(level, x, y, z, span);
        if (spot == null) {
            return false;
        }
        minion.teleportTo(spot.x, spot.y, spot.z);
        level.playSound(null, minion.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.6F, 1.0F);
        return true;
    }

    /** The nearest spot in this column a four-block shade actually fits. */
    private static Vec3 standingRoom(ServerLevel level, double x, double y, double z, int span) {
        BlockPos wanted = BlockPos.containing(x, y, z);
        level.getChunk(wanted);                    // generate before asking
        int floor = level.getMinBuildHeight() + 1;
        int ceiling = level.getMaxBuildHeight() - 5;
        for (int step = 0; step <= span; step++) {
            for (int dir = 0; dir < (step == 0 ? 1 : 2); dir++) {
                int cy = wanted.getY() + (dir == 0 ? step : -step);
                if (cy < floor || cy > ceiling) {
                    continue;
                }
                BlockPos at = new BlockPos(wanted.getX(), cy, wanted.getZ());
                if (fitsShade(level, at)) {
                    return new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                }
            }
        }
        return null;
    }

    /** Four blocks of headroom on solid ground — a shade is not a normal enderman. */
    private static boolean fitsShade(ServerLevel level, BlockPos at) {
        for (int dy = 0; dy < 4; dy++) {
            BlockPos head = at.above(dy);
            if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                return false;
            }
        }
        BlockPos under = at.below();
        return !level.getBlockState(under).getCollisionShape(level, under).isEmpty();
    }

    /** Walled in, or fallen out of the world. */
    private static boolean stranded(ServerLevel level, EnderMan minion) {
        BlockPos at = minion.blockPosition();
        if (at.getY() < level.getMinBuildHeight() + 1) {
            return true;
        }
        BlockPos head = at.above();
        return level.getBlockState(head).isSuffocating(level, head);
    }

    // -------------------------------------------------------------- helpers

    private static LivingEntity lookedAt(ServerPlayer owner, ServerLevel level) {
        Vec3 eye = owner.getEyePosition();
        Vec3 end = eye.add(owner.getLookAngle().scale(48.0));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(2.0), x -> x != owner && x.isAlive())) {
            double d = e.distanceToSqr(owner);
            if (d < bestDist && e.getBoundingBox().inflate(1.5).clip(eye, end).isPresent()) {
                best = e;
                bestDist = d;
            }
        }
        return best;
    }

    private static LivingEntity nearestFoe(ServerPlayer owner, EnderMan minion, ServerLevel level) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Monster m : level.getEntitiesOfClass(Monster.class,
                minion.getBoundingBox().inflate(20.0),
                e -> e != minion && e.isAlive() && !isOwnedBy(owner, e))) {
            double d = m.distanceToSqr(minion);
            if (d < bestDist) {
                best = m;
                bestDist = d;
            }
        }
        return best;
    }

    private static void rename(EnderMan minion, Shade shade) {
        minion.setCustomName(Component.literal(NAMES[shade.slot])
                .withStyle(TINTS[shade.slot], ChatFormatting.BOLD)
                .append(Component.literal("  ·  " + shade.order.label)
                        .withStyle(shade.order.colour)));
        minion.setCustomNameVisible(true);
    }

    private static void say(ServerPlayer owner, String line, ChatFormatting colour, boolean actionBar) {
        owner.displayClientMessage(Component.literal(line).withStyle(colour), actionBar);
    }

    public static boolean isOwnedBy(Player owner, LivingEntity e) {
        List<Shade> court = COURT.get(owner.getUUID());
        if (court == null) {
            return false;
        }
        for (Shade s : court) {
            if (s.entity.equals(e.getUUID())) {
                return true;
            }
        }
        return false;
    }
}
