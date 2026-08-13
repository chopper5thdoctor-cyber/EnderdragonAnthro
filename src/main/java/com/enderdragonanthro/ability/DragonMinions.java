package com.enderdragonanthro.ability;

import com.enderdragonanthro.network.ShadeStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.ChunkPos;
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
        RECALL(ChatFormatting.YELLOW, "Recall");

        /** The ones that are actions rather than duties. */
        public boolean momentary() {
            return this == PET || this == RECALL;
        }

        public final ChatFormatting colour;
        public final String label;

        Order(ChatFormatting colour, String label) {
            this.colour = colour;
            this.label = label;
        }
    }

    /** The four of them. Jean's court, in order of summoning. */
    private static final String[] NAMES = {"Vael", "Kesh", "Nyra", "Orrin"};
    private static final ChatFormatting[] TINTS = {
            ChatFormatting.DARK_PURPLE, ChatFormatting.BLUE,
            ChatFormatting.DARK_AQUA, ChatFormatting.GOLD};

    public static final int MAX_PER_PLAYER = 4;
    private static final double SCALE = 4.0 / 2.9;      // enderman is 2.9 blocks tall
    private static final double FORAGE_RANGE = 1000.0;  // how far afield they will roam
    private static final int WORK_RADIUS = 16;          // mining reach around themselves
    private static final int CARGO_MAX = 64;            // one stack, then home
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
    /** Bedrock this far above the world floor is fair game; below it is not. */
    private static final int BEDROCK_FLOOR = 5;
    /** A Collect order that has got nowhere in this long comes home anyway. */
    private static final int COLLECT_TIMEOUT = 3600;
    /** Fruitless hops around one patch before trying somewhere else entirely. */
    private static final int DRY_HOPS = 12;
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
        BlockState cargoType;
        /** Depth the quarry was last actually found at, so searching improves. */
        Integer learnedY;
        /** What the owner asked for. Null means "whatever is worth taking". */
        Block wanted;
        int cargo;
        boolean headingHome;
        /** Ticks an Attack order has spent with nothing to hunt. */
        int idleTicks;
        /** Last place we actually saw it, so an unloaded shade can be fetched. */
        BlockPos lastPos;
        /** The patch it is working, and how many fruitless hops it has had. */
        BlockPos site;
        int dryHops;
        /** Ticks spent away on a Collect order without finishing. */
        int awayTicks;
        /** The chunk we are currently holding open for it. */
        ChunkPos ticket;
        /** The crystal this shade raised, and the block it stands on. */
        UUID crystal;
        BlockPos bedrock;
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

        Vec3 look = owner.getLookAngle();
        Vec3 at = owner.position().add(look.x * 5.0, 0.0, look.z * 5.0);
        EnderMan minion = EntityType.ENDERMAN.create(level);
        if (minion == null) {
            return;
        }
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
                if (s.entity.equals(stray.getUUID())) {
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
            if (shade.order == Order.COLLECT) {
                continue;                       // away on business; let it work
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

    /** Pick who you are talking to: whoever you are looking at, else the next one. */
    public static void select(ServerPlayer owner) {
        List<Shade> court = living(owner);
        if (court.isEmpty()) {
            say(owner, "No shade answers yet.", ChatFormatting.DARK_GRAY, true);
            return;
        }
        ServerLevel level = owner.serverLevel();
        LivingEntity aimed = lookedAt(owner, level);
        for (Shade s : court) {
            if (aimed != null && aimed.getUUID().equals(s.entity)) {
                SELECTED.put(owner.getUUID(), s.slot);
                say(owner, NAMES[s.slot] + " is listening.", TINTS[s.slot], true);
                return;
            }
        }
        int current = SELECTED.getOrDefault(owner.getUUID(), -1);
        Shade next = court.get(0);
        for (Shade s : court) {
            if (s.slot > current) {
                next = s;
                break;
            }
        }
        SELECTED.put(owner.getUUID(), next.slot);
        say(owner, NAMES[next.slot] + " is listening.", TINTS[next.slot], true);
    }

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
            if (!(level.getEntity(s.entity) instanceof EnderMan e) || !e.isAlive()) {
                continue;
            }
            entries.add(new ShadeStatePayload.Entry(s.slot, NAMES[s.slot], s.order.ordinal(),
                    s.wanted == null ? "" : BuiltInRegistries.BLOCK.getKey(s.wanted).toString(),
                    s.cargo));
        }
        entries.sort(Comparator.comparingInt(ShadeStatePayload.Entry::slot));
        ServerPlayNetworking.send(owner, new ShadeStatePayload(open, List.copyOf(entries)));
    }

    /**
     * One shade, one order, straight from the screen.
     *
     * Changing a shade's mind never costs you its cargo: whatever it is
     * carrying is handed over first, wherever in the world it had got to.
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
            EnderMan target = resolve(level, shade);
            if (target == null) {
                say(owner, NAMES[slot] + " is too far to hear you.",
                        ChatFormatting.DARK_GRAY, true);
                return;
            }
            if (order == Order.RECALL) {
                Vec3 look = owner.getLookAngle();
                if (!blink(level, target, owner.getX() - look.x * 3.0, owner.getY(),
                        owner.getZ() - look.z * 3.0, 16)) {
                    target.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                }
                // Recall has to end the errand, not just interrupt it. A shade
                // still under Collect arrived, was told nothing had changed, and
                // hopped straight back out to its patch inside two seconds — so
                // it looked as though it had never come. It hands over what it
                // dug up and stays.
                deliver(owner, target, shade);
                standDown(owner, target, shade, "\"At your side.\"");
            } else {
                target.getLookControl().setLookAt(owner, 60.0F, 60.0F);
                EndermanAffection.adore(level, target);
                say(owner, NAMES[slot] + " leans into it.", Order.PET.colour, false);
            }
            pushState(owner);
            return;
        }

        EnderMan minion = level.getEntity(shade.entity) instanceof EnderMan e ? e : null;
        if (minion != null) {
            handOver(owner, level, minion, shade);
        }
        shade.order = order;
        shade.headingHome = false;
        shade.idleTicks = 0;
        shade.awayTicks = 0;
        shade.dryHops = 0;
        shade.site = null;
        if (order == Order.COLLECT) {
            Block wanted = parseBlock(quarry);     // named from the picker, or nothing
            if (wanted != shade.wanted) {
                shade.learnedY = null;
            }
            shade.wanted = wanted;
        }
        // A quarry is a standing preference, not part of the order: switching
        // to Defend and back should not make the shade forget what it digs for.
        SELECTED.put(owner.getUUID(), slot);
        if (minion != null) {
            rename(minion, shade);
            minion.setTarget(null);
        }
        say(owner, NAMES[slot] + ": " + line(shade), order.colour, false);
        pushState(owner);
    }

    /** Name the quarry for the shade currently being addressed. */
    public static boolean setQuarry(ServerPlayer owner, Block block) {
        Shade shade = selected(owner);
        if (shade == null) {
            return false;
        }
        applyOrder(owner, shade.slot, Order.COLLECT.ordinal(),
                BuiltInRegistries.BLOCK.getKey(block).toString());
        return true;
    }

    private static String line(Shade shade) {
        return switch (shade.order) {
            case ATTACK -> "\"I hunt what you look upon.\"";
            case DEFEND -> "\"I stand with you.\"";
            case COLLECT -> shade.wanted == null
                    ? "\"I will bring you a stack of whatever I find.\""
                    : "\"I will bring you a stack of "
                      + shade.wanted.getName().getString() + ".\"";
            case CRYSTAL -> "\"I will raise a crystal and keep it.\"";
            case DISMANTLE -> "\"I will pull the bedrock down.\"";
            case PET, RECALL -> "\"I stand with you.\"";     // never a standing order
        };
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

            for (Shade shade : court) {
                if (!(level.getEntity(shade.entity) instanceof EnderMan minion)) {
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
                // stops what it is doing; the ones away on business keep their
                // cargo and their errand.
                if (avenge != null && minion.distanceToSqr(owner) < GRUDGE_RANGE * GRUDGE_RANGE) {
                    minion.setTarget(avenge);
                    if (minion.distanceToSqr(avenge) > 9.0) {
                        minion.getNavigation().moveTo(avenge, 1.3);
                    }
                    continue;
                }
                switch (shade.order) {
                    case ATTACK -> attack(owner, minion, level, shade);
                    case COLLECT -> collect(owner, minion, level, shade);
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
        shade.headingHome = false;
        shade.idleTicks = 0;
        shade.awayTicks = 0;
        shade.dryHops = 0;
        shade.site = null;
        minion.setTarget(null);
        rename(minion, shade);
        say(owner, NAMES[shade.slot] + ": " + line, Order.DEFEND.colour, false);
        pushState(owner);
    }

    private static void defend(ServerPlayer owner, EnderMan minion, ServerLevel level) {
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
     * Forage far and wide, then bring back exactly one stack.
     *
     * Walking a thousand blocks would take an age, so the shade does what
     * endermen do: it teleports. It settles on one patch and works it rather
     * than bouncing to a fresh random point across the world every two seconds
     * — that used to force a chunk to generate each time, and gave it no chance
     * to find a seam before it moved on.
     *
     * Every hop goes through {@link #blink}, so it can only ever land somewhere
     * it can stand. And the whole errand is on a clock: a shade that has got
     * nowhere in {@value #COLLECT_TIMEOUT} ticks comes home with whatever it
     * has, rather than staying out forever and holding its slot.
     */
    private static void collect(ServerPlayer owner, EnderMan minion, ServerLevel level, Shade shade) {
        minion.setTarget(null);
        if (shade.cargo >= CARGO_MAX) {
            shade.headingHome = true;
        }
        if (!shade.headingHome && ++shade.awayTicks > COLLECT_TIMEOUT) {
            shade.headingHome = true;
            say(owner, NAMES[shade.slot] + ": \"The seam is dry. Returning.\"",
                    Order.COLLECT.colour, false);
        }
        if (shade.headingHome) {
            if (minion.distanceToSqr(owner) < 64.0) {
                deliver(owner, minion, shade);
                standDown(owner, minion, shade, "\"It is yours. I stand with you.\"");
            } else if (level.getGameTime() % 20 == 0) {
                hopToward(level, minion, owner.getX(), owner.getZ(), 24.0);
            }
            return;
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        BlockPos found = findHarvest(level, minion.blockPosition(), shade);
        if (found != null) {
            BlockState state = level.getBlockState(found);
            level.removeBlock(found, false);
            shade.cargoType = state;
            shade.learnedY = found.getY();       // remember where the seam was
            shade.cargo++;
            shade.dryHops = 0;                   // this patch is paying out
            minion.setCarriedBlock(state);
            if (shade.cargo >= CARGO_MAX) {
                shade.headingHome = true;
                say(owner, NAMES[shade.slot] + ": \"A full stack. Returning.\"",
                        Order.COLLECT.colour, false);
            }
            return;
        }
        if (level.getGameTime() % 40 != 0) {
            return;
        }
        // Nothing in reach. Pick a patch if we have not got one, otherwise
        // shuffle around inside it; give up on a patch that keeps coming up
        // empty and choose somewhere else entirely.
        if (shade.site == null || ++shade.dryHops > DRY_HOPS) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 64.0 + level.random.nextDouble() * FORAGE_RANGE;
            shade.site = BlockPos.containing(owner.getX() + Math.cos(angle) * dist,
                    owner.getY(), owner.getZ() + Math.sin(angle) * dist);
            shade.dryHops = 0;
        }
        int depth = shade.wanted == null ? Integer.MIN_VALUE
                : chooseDepth(level, shade, shade.wanted);
        double x = shade.site.getX() + level.random.nextInt(65) - 32;
        double z = shade.site.getZ() + level.random.nextInt(65) - 32;
        if (depth != Integer.MIN_VALUE) {
            int y = Math.max(level.getMinBuildHeight() + 2,
                    Math.min(level.getMaxBuildHeight() - 5, depth));
            if (blink(level, minion, x, y, z, 24)) {
                return;
            }
            // no pocket anywhere near that depth: work the surface instead
        }
        hopToward(level, minion, x, z, 0.0);
    }

    private static void deliver(ServerPlayer owner, EnderMan minion, Shade shade) {
        if (shade.cargoType != null && shade.cargo > 0) {
            ItemStack stack = new ItemStack(shade.cargoType.getBlock(), shade.cargo);
            if (!stack.isEmpty()) {
                minion.spawnAtLocation(stack);
            }
            say(owner, NAMES[shade.slot] + " sets down " + shade.cargo + " "
                    + shade.cargoType.getBlock().getName().getString() + ".",
                    Order.COLLECT.colour, false);
        }
        shade.cargoType = null;
        shade.cargo = 0;
        shade.headingHome = false;
        minion.setCarriedBlock(null);
    }

    /**
     * Interrupting a shade never costs you what it dug up: it comes back,
     * sets the load down, and only then takes the new order.
     */
    private static void handOver(ServerPlayer owner, ServerLevel level, EnderMan minion, Shade shade) {
        if (shade.cargo <= 0 || shade.cargoType == null) {
            shade.cargoType = null;
            shade.cargo = 0;
            minion.setCarriedBlock(null);
            return;
        }
        if (minion.distanceToSqr(owner) > 64.0) {
            hopToward(level, minion, owner.getX(), owner.getZ(), 4.0);
        }
        deliver(owner, minion, shade);
    }

    /**
     * Find something worth taking.
     *
     * With no quarry named it only strips surface blocks, so it will not
     * swiss-cheese the landscape. Once you name one it will dig for it — an
     * enderman can stand anywhere, so buried ore is fair game.
     */
    private static BlockPos findHarvest(ServerLevel level, BlockPos base, Shade shade) {
        Block quarry = shade.wanted != null ? shade.wanted
                : (shade.cargoType != null ? shade.cargoType.getBlock() : null);
        int samples = quarry != null ? 160 : 24;
        for (BlockPos pos : BlockPos.randomInCube(level.random, samples, base, WORK_RADIUS)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity()
                    || state.getDestroySpeed(level, pos) < 0
                    || !state.getFluidState().isEmpty()
                    || state.is(Blocks.BEDROCK)
                    || state.getBlock().asItem() == Items.AIR) {
                continue;
            }
            if (quarry != null) {
                if (!state.is(quarry)) {
                    continue;
                }
            } else if (!level.getBlockState(pos.above()).isAir()) {
                continue;                       // unnamed: surface only, no tunnelling
            }
            if (shade.cargoType != null && !state.is(shade.cargoType.getBlock())) {
                continue;                       // one kind per trip, so it stacks
            }
            return pos.immutable();
        }
        return null;
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
            follow(owner, minion, HEEL, 1.1);      // its crystal still stands; guard it
            return;
        }
        shade.crystal = null;
        if (level.getGameTime() % CRYSTAL_WORK != 0) {
            follow(owner, minion, HEEL, 1.1);
            return;
        }
        BlockPos spot = crystalSpot(level, minion.blockPosition());
        if (spot == null) {
            follow(owner, minion, HEEL, 1.1);
            return;
        }
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
                Order.CRYSTAL.colour, false);
    }

    /** Somewhere near the shade with solid footing and headroom for a crystal. */
    private static BlockPos crystalSpot(ServerLevel level, BlockPos base) {
        for (BlockPos pos : BlockPos.randomInCube(level.random, 64, base, 6)) {
            BlockState here = level.getBlockState(pos);
            if (here.isAir() || here.is(Blocks.BEDROCK) || here.getDestroySpeed(level, pos) < 0) {
                continue;                          // want ordinary ground to build on
            }
            if (!level.getBlockState(pos.above()).isAir()
                    || !level.getBlockState(pos.above(2)).isAir()
                    || !level.getBlockState(pos.above(3)).isAir()) {
                continue;
            }
            return pos.immutable();
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
        for (EndCrystal sitting : level.getEntitiesOfClass(EndCrystal.class,
                new AABB(target.above()).inflate(0.9))) {
            forget(owner, sitting.getUUID(), null);
            sitting.discard();
        }
        level.removeBlock(target, false);
        forget(owner, null, target);
        level.playSound(null, target, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
        say(owner, NAMES[shade.slot] + " pulls down the bedrock at "
                + target.getX() + ", " + target.getY() + ", " + target.getZ() + ".",
                Order.DISMANTLE.colour, false);
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
        } else if (gap > leash * leash) {
            minion.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), speed);
        }
    }

    /**
     * Where a given block actually lives.
     *
     * Blind-searching every depth wastes a shade's time, so the common ores
     * carry their real generation band and it aims there. Anything unlisted
     * returns null and is treated as a surface material. A shade also
     * remembers the depth it last struck the quarry at and favours that,
     * so it gets better at a seam the longer it works it.
     */
    private static int[] oreBand(Block block) {
        String key = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (key.contains("diamond_ore")) return new int[]{-63, 16, -59};
        if (key.contains("redstone_ore")) return new int[]{-63, 15, -59};
        if (key.contains("lapis_ore")) return new int[]{-64, 64, 0};
        if (key.contains("gold_ore") && !key.contains("nether")) return new int[]{-64, 32, -16};
        if (key.contains("iron_ore")) return new int[]{-24, 56, 16};
        if (key.contains("copper_ore")) return new int[]{-16, 112, 48};
        if (key.contains("coal_ore")) return new int[]{0, 192, 96};
        if (key.contains("emerald_ore")) return new int[]{-16, 256, 200};
        if (key.equals("ancient_debris")) return new int[]{8, 22, 15};
        if (key.contains("amethyst")) return new int[]{-64, 30, -20};
        if (key.equals("deepslate") || key.contains("deepslate_")) return new int[]{-64, 8, -30};
        if (key.equals("granite") || key.equals("diorite") || key.equals("andesite")
                || key.equals("tuff") || key.equals("calcite")) return new int[]{-64, 80, 0};
        if (key.equals("gravel") || key.equals("clay")) return new int[]{-20, 70, 50};
        return null;
    }

    /** Pick a depth to search, favouring what the shade has already learned. */
    private static int chooseDepth(ServerLevel level, Shade shade, Block quarry) {
        if (shade.learnedY != null && level.random.nextFloat() < 0.7F) {
            return shade.learnedY + level.random.nextInt(17) - 8;
        }
        int[] band = oreBand(quarry);
        if (band == null) {
            return Integer.MIN_VALUE;                 // surface material
        }
        // triangular pull toward the peak, so most stops land in the rich part
        int a = level.random.nextInt(band[1] - band[0] + 1) + band[0];
        int b = band[2] + level.random.nextInt(17) - 8;
        return Math.max(band[0], Math.min(band[1], (a + b * 3) / 4));
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
