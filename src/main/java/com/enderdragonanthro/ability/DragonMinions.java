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
        CRYSTAL(ChatFormatting.LIGHT_PURPLE, "Crystal");

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
        court.removeIf(s -> !(level.getEntity(s.entity) instanceof EnderMan e) || !e.isAlive());
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
        EnderMan minion = level.getEntity(shade.entity) instanceof EnderMan e ? e : null;

        if (minion != null) {
            handOver(owner, level, minion, shade);
        }
        shade.order = order;
        shade.headingHome = false;
        shade.idleTicks = 0;
        if (order == Order.COLLECT) {
            Block wanted = parseBlock(quarry);
            if (wanted == null) {
                wanted = aimedBlock(owner);        // blank box: use what you look at
            }
            if (wanted != shade.wanted) {
                shade.learnedY = null;
            }
            shade.wanted = wanted;
        } else {
            shade.wanted = null;
        }
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
            case CRYSTAL -> "\"I will set a crystal on the bedrock.\"";
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

    private static Block aimedBlock(ServerPlayer owner) {
        net.minecraft.world.phys.HitResult hit = owner.pick(48.0, 0.0F, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult block) {
            BlockState state = owner.level().getBlockState(block.getBlockPos());
            if (!state.isAir()) {
                return state.getBlock();
            }
        }
        return null;
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
        court.removeIf(s -> !(level.getEntity(s.entity) instanceof EnderMan e) || !e.isAlive());
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
            if (court.removeIf(s -> !(level.getEntity(s.entity) instanceof EnderMan e) || !e.isAlive())) {
                pushState(owner);
            }
            LivingEntity avenge = grudge(owner, level);

            for (Shade shade : court) {
                if (!(level.getEntity(shade.entity) instanceof EnderMan minion)) {
                    continue;
                }
                if (minion.getTarget() == owner) {
                    minion.setTarget(null);      // never turn on the summoner
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
                    case DEFEND -> defend(owner, minion, level);
                    case COLLECT -> collect(owner, minion, level, shade);
                    case CRYSTAL -> crystal(owner, minion, level, shade);
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
            follow(owner, minion, 8.0, 1.1);
        }
    }

    /**
     * Forage far and wide, then bring back exactly one stack.
     *
     * Walking a thousand blocks would take an age, so the shade does what
     * endermen do: it teleports. It hops to fresh ground, strips surface
     * blocks of the first kind it grabs until it holds a stack, then hops
     * home and hands it over — silk-touch, so the block itself comes back.
     */
    private static void collect(ServerPlayer owner, EnderMan minion, ServerLevel level, Shade shade) {
        minion.setTarget(null);
        if (shade.cargo >= CARGO_MAX) {
            shade.headingHome = true;
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
            minion.setCarriedBlock(state);
            if (shade.cargo >= CARGO_MAX) {
                shade.headingHome = true;
                say(owner, NAMES[shade.slot] + ": \"A full stack. Returning.\"",
                        Order.COLLECT.colour, false);
            }
            return;
        }
        // nothing in reach: hop somewhere new inside the forage range. When a
        // quarry is named, alternate between the surface and a random depth so
        // buried seams actually get searched.
        if (level.getGameTime() % 40 == 0) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 64.0 + level.random.nextDouble() * FORAGE_RANGE;
            double x = owner.getX() + Math.cos(angle) * dist;
            double z = owner.getZ() + Math.sin(angle) * dist;
            int depth = shade.wanted == null ? Integer.MIN_VALUE
                    : chooseDepth(level, shade, shade.wanted);
            if (depth != Integer.MIN_VALUE) {
                int y = Math.max(level.getMinBuildHeight() + 2,
                        Math.min(level.getMaxBuildHeight() - 2, depth));
                minion.teleportTo(x, y, z);
                level.playSound(null, minion.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.HOSTILE, 0.6F, 1.0F);
            } else {
                hopToward(level, minion, x, z, 0.0);
            }
        }
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
     * Fashion an end crystal and set it on bedrock.
     *
     * The shades make these — it is their trade. A dragon has no hands for
     * crafting, so this is how the court keeps a domain stocked. Slow on
     * purpose: one crystal every CRYSTAL_WORK ticks per shade.
     */
    private static void crystal(ServerPlayer owner, EnderMan minion, ServerLevel level, Shade shade) {
        minion.setTarget(null);
        if (level.getGameTime() % CRYSTAL_WORK != 0) {
            return;
        }
        BlockPos base = minion.blockPosition();
        for (BlockPos pos : BlockPos.randomInCube(level.random, 48, base, 24)) {
            if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                continue;
            }
            if (!level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above(2)).isAir()) {
                continue;
            }
            EndCrystal crystal = new EndCrystal(level,
                    pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            crystal.setShowBottom(false);
            level.addFreshEntity(crystal);
            level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.HOSTILE, 1.0F, 1.4F);
            say(owner, NAMES[shade.slot] + " sets a crystal at "
                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ".",
                    Order.CRYSTAL.colour, false);
            return;
        }
        // no bedrock in reach - go looking for some
        hopToward(level, minion, minion.getX() + level.random.nextInt(129) - 64,
                minion.getZ() + level.random.nextInt(129) - 64, 0.0);
    }

    // ----------------------------------------------------------- movement

    private static void follow(ServerPlayer owner, EnderMan minion, double leash, double speed) {
        if (minion.distanceToSqr(owner) > leash * leash && minion.getTarget() == null) {
            if (minion.distanceToSqr(owner) > 4096.0) {
                hopToward(minion.level() instanceof ServerLevel sl ? sl : null,
                        minion, owner.getX(), owner.getZ(), 6.0);
            } else {
                minion.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), speed);
            }
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

    /** Enderman-style hop: land on the surface at (x, z), optionally offset. */
    private static void hopToward(ServerLevel level, EnderMan minion, double x, double z, double spread) {
        if (level == null) {
            return;
        }
        if (spread > 0) {
            x += (level.random.nextDouble() - 0.5) * spread;
            z += (level.random.nextDouble() - 0.5) * spread;
        }
        BlockPos column = BlockPos.containing(x, 0, z);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        minion.teleportTo(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
        level.playSound(null, surface, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.6F, 1.0F);
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
