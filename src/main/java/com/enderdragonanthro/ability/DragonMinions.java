package com.enderdragonanthro.ability;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * Summoned endermen that serve the dragon.
 *
 * These are ordinary vanilla endermen — same model, same animations — scaled
 * to 4 blocks and driven procedurally each tick rather than by custom AI
 * goals, which keeps them compatible with everything vanilla does to endermen.
 */
public final class DragonMinions {
    public enum Order {
        ATTACK(ChatFormatting.RED, "Attack"),
        DEFEND(ChatFormatting.AQUA, "Defend"),
        COLLECT(ChatFormatting.GREEN, "Collect");

        final ChatFormatting colour;
        final String label;

        Order(ChatFormatting colour, String label) {
            this.colour = colour;
            this.label = label;
        }
    }

    private static final int MAX_PER_PLAYER = 3;
    private static final double SCALE = 4.0 / 2.9;          // enderman is 2.9 blocks tall
    private static final double FOLLOW_RANGE = 24.0;
    private static final double COLLECT_RANGE = 12.0;
    private static final ResourceLocation SCALE_ID = id("minion_scale");

    private static final Map<UUID, List<UUID>> OWNED = new HashMap<>();
    private static final Map<UUID, Order> ORDERS = new HashMap<>();

    private DragonMinions() {
    }

    public static void summon(ServerPlayer owner) {
        List<UUID> mine = OWNED.computeIfAbsent(owner.getUUID(), u -> new ArrayList<>());
        ServerLevel level = owner.serverLevel();
        mine.removeIf(u -> !(level.getEntity(u) instanceof EnderMan e) || !e.isAlive());
        if (mine.size() >= MAX_PER_PLAYER) {
            owner.displayClientMessage(Component.literal("Your three already answer.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }

        Vec3 look = owner.getLookAngle();
        Vec3 at = owner.position().add(look.x * 4.0, 0.0, look.z * 4.0);
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
        level.addFreshEntity(minion);
        mine.add(minion.getUUID());
        ORDERS.putIfAbsent(owner.getUUID(), Order.DEFEND);
        name(minion, ORDERS.get(owner.getUUID()));

        level.playSound(null, minion.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 1.0F, 0.6F);
        say(owner, "A shade rises. " + mine.size() + " of " + MAX_PER_PLAYER + ".");
    }

    /** Cycles the standing order for every minion this player owns. */
    public static void cycleOrder(ServerPlayer owner) {
        Order[] all = Order.values();
        Order next = all[(ORDERS.getOrDefault(owner.getUUID(), Order.DEFEND).ordinal() + 1) % all.length];
        ORDERS.put(owner.getUUID(), next);
        ServerLevel level = owner.serverLevel();
        for (UUID u : OWNED.getOrDefault(owner.getUUID(), List.of())) {
            if (level.getEntity(u) instanceof EnderMan e) {
                name(e, next);
            }
        }
        say(owner, switch (next) {
            case ATTACK -> "They hunt what you look upon.";
            case DEFEND -> "They stand with you.";
            case COLLECT -> "They gather what they find.";
        });
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            List<UUID> mine = OWNED.get(owner.getUUID());
            if (mine == null || mine.isEmpty()) {
                continue;
            }
            ServerLevel level = owner.serverLevel();
            Order order = ORDERS.getOrDefault(owner.getUUID(), Order.DEFEND);
            mine.removeIf(u -> !(level.getEntity(u) instanceof EnderMan e) || !e.isAlive());

            for (UUID u : mine) {
                if (!(level.getEntity(u) instanceof EnderMan minion)) {
                    continue;
                }
                // never turn on the summoner, whatever else happens
                if (minion.getTarget() == owner) {
                    minion.setTarget(null);
                }
                switch (order) {
                    case ATTACK -> attack(owner, minion, level);
                    case DEFEND -> defend(owner, minion, level);
                    case COLLECT -> collect(owner, minion, level);
                }
                if (minion.distanceToSqr(owner) > FOLLOW_RANGE * FOLLOW_RANGE
                        && minion.getTarget() == null) {
                    minion.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), 1.15);
                }
            }
        }
    }

    private static void attack(ServerPlayer owner, EnderMan minion, ServerLevel level) {
        LivingEntity aim = lookedAt(owner, level);
        if (aim != null && aim != owner) {
            minion.setTarget(aim);
            return;
        }
        if (minion.getTarget() == null || !minion.getTarget().isAlive()) {
            minion.setTarget(nearestFoe(owner, minion, level));
        }
    }

    private static void defend(ServerPlayer owner, EnderMan minion, ServerLevel level) {
        LivingEntity threat = minion.getTarget();
        if (threat == null || !threat.isAlive()) {
            // anything already hostile to the owner
            for (Monster m : level.getEntitiesOfClass(Monster.class,
                    owner.getBoundingBox().inflate(16.0),
                    e -> e != minion && e.isAlive() && e.getTarget() == owner)) {
                minion.setTarget(m);
                return;
            }
            minion.setTarget(null);
            if (minion.distanceToSqr(owner) > 64.0) {
                minion.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), 1.1);
            }
        }
    }

    /** Endermen already carry blocks; this points that instinct at a job. */
    private static void collect(ServerPlayer owner, EnderMan minion, ServerLevel level) {
        minion.setTarget(null);
        BlockState carried = minion.getCarriedBlock();
        if (carried != null) {
            if (minion.distanceToSqr(owner) < 16.0) {
                minion.spawnAtLocation(new ItemStack(carried.getBlock()));
                minion.setCarriedBlock(null);
            } else {
                minion.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), 1.2);
            }
            return;
        }
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        BlockPos base = minion.blockPosition();
        for (BlockPos pos : BlockPos.randomInCube(level.random, 8, base, (int) COLLECT_RANGE)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0
                    || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                    || state.is(Blocks.BEDROCK)) {
                continue;
            }
            if (!level.getBlockState(pos.above()).isAir()) {
                continue;                              // only surface blocks, no tunnelling
            }
            level.removeBlock(pos, false);
            minion.setCarriedBlock(state);
            return;
        }
    }

    private static LivingEntity lookedAt(ServerPlayer owner, ServerLevel level) {
        Vec3 eye = owner.getEyePosition();
        Vec3 end = eye.add(owner.getLookAngle().scale(48.0));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(2.0),
                x -> x != owner && x.isAlive() && !(x instanceof EnderMan))) {
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
                e -> e != minion && e.isAlive() && !isMinion(owner, e))) {
            double d = m.distanceToSqr(minion);
            if (d < bestDist) {
                best = m;
                bestDist = d;
            }
        }
        return best;
    }

    private static boolean isMinion(ServerPlayer owner, LivingEntity e) {
        return isOwnedBy(owner, e);
    }

    private static void name(EnderMan minion, Order order) {
        minion.setCustomName(Component.literal("Shade · " + order.label)
                .withStyle(order.colour));
    }

    private static void say(ServerPlayer owner, String line) {
        owner.displayClientMessage(Component.literal(line)
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    public static void dismissAll(Player owner) {
        OWNED.remove(owner.getUUID());
        ORDERS.remove(owner.getUUID());
    }

    public static boolean isOwnedBy(Player owner, LivingEntity e) {
        return OWNED.getOrDefault(owner.getUUID(), List.of()).contains(e.getUUID());
    }
}
