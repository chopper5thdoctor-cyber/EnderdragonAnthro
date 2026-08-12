package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side ability execution. Damage numbers are the canon dragon's:
 * head/charge 6/10/15 and wing 3/5/7 by difficulty.
 */
public final class DragonAbilities {
    private static final Map<UUID, Map<AbilityAction, Long>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_CHARGES = new HashMap<>();
    private static final Set<UUID> CRATER_ARMED = new HashSet<>();

    /** How far the breath will look for ground before giving up. */
    private static final double BREATH_REACH = 100.0;
    private static final double TELEPORT_REACH = 64.0;

    private DragonAbilities() {
    }

    public static boolean craterArmed(ServerPlayer player) {
        return CRATER_ARMED.contains(player.getUUID());
    }

    public static void trigger(ServerPlayer player, AbilityAction action) {
        if (!DragonFormManager.isDragon(player)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Map<AbilityAction, Long> cds = COOLDOWNS.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
        long readyAt = cds.getOrDefault(action, 0L);
        if (now < readyAt) {
            player.displayClientMessage(Component.translatable(
                    "message.enderdragonanthro.cooldown", (readyAt - now + 19) / 20), true);
            return;
        }
        cds.put(action, now + action.cooldownTicks);

        switch (action) {
            case BREATH -> breath(player);
            case FIREBALL -> fireball(player);
            case BUFFET -> wingBuffet(player);
            case CHARGE -> beginCharge(player);
            case CRATER -> toggleCrater(player);
            case TELEPORT -> teleport(player);
            case GLIDE -> DragonFlight.start(player);
            case BOOST -> DragonFlight.boost(player);
            case SUMMON -> DragonMinions.summon(player);
            case COMMAND -> DragonMinions.cycleOrder(player);
            case TRANSFORM -> {
            }
        }
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (DragonFormManager.isDragon(player) && player.isSprinting()) {
                chargeContactDamage(player);
            }
        }
        Iterator<Map.Entry<UUID, Integer>> it = ACTIVE_CHARGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || entry.getValue() <= 0 || !DragonFormManager.isDragon(player)) {
                it.remove();
                continue;
            }
            int left = entry.getValue();
            entry.setValue(left - 1);
            if (left > 4) {
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(look.x * 1.8, look.y * 0.9 + 0.1, look.z * 1.8);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
            chargeContactDamage(player);
        }
    }

    /**
     * Exhale at the ground you are looking at.
     *
     * A giant's mouth sits ~7 blocks up, so a shallow look angle used to strand
     * the cloud in mid-air. The ray is now traced against terrain first; if it
     * reaches {@link #BREATH_REACH} without touching anything, the endpoint is
     * dropped straight down onto whatever is beneath it. Either way the breath
     * lands on ground rather than hanging in the sky.
     */
    private static void breath(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 mouth = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 far = mouth.add(look.scale(BREATH_REACH));

        BlockHitResult hit = level.clip(new ClipContext(mouth, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 landing;
        if (hit.getType() == HitResult.Type.BLOCK) {
            landing = hit.getLocation();
        } else {
            // nothing along the ray: fall from the far end to the ground below it
            BlockHitResult down = level.clip(new ClipContext(far,
                    far.subtract(0.0, level.getHeight(), 0.0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            landing = down.getType() == HitResult.Type.BLOCK ? down.getLocation()
                    : new Vec3(far.x, level.getMinBuildHeight() + 1, far.z);
        }

        // a short trail of clouds up to the landing point, then the pool itself
        double span = mouth.distanceTo(landing);
        int steps = (int) Math.min(6, Math.max(1, span / 6.0));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = mouth.add(landing.subtract(mouth).scale((double) i / steps));
            spawnCloud(level, player, at, i == steps ? 4.0F : 2.0F);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void spawnCloud(ServerLevel level, ServerPlayer owner, Vec3 at, float radius) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setOwner(owner);
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);
        cloud.setRadius(radius);
        cloud.setDuration(120);
        cloud.setRadiusOnUse(-0.5F);
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
        level.addFreshEntity(cloud);
    }

    private static void fireball(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        DragonFireball ball = new DragonFireball(level, player, look);
        ball.setPos(player.getEyePosition().add(look.scale(2.0)));
        level.addFreshEntity(ball);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT,
                SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void wingBuffet(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(6.0, 3.0, 6.0);
        float damage = byDifficulty(level.getDifficulty(), 3.0F, 5.0F, 7.0F);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !DragonMinions.isOwnedBy(player, e))) {
            target.hurt(level.damageSources().playerAttack(player), damage);
            Vec3 away = target.position().subtract(player.position()).normalize();
            target.setDeltaMovement(away.x * 2.0, 0.8, away.z * 2.0);
            target.hurtMarked = true;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP,
                SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void beginCharge(ServerPlayer player) {
        ACTIVE_CHARGES.put(player.getUUID(), 12);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5F, 1.4F);
    }

    private static void chargeContactDamage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float damage = byDifficulty(level.getDifficulty(), 6.0F, 10.0F, 15.0F);
        AABB path = player.getBoundingBox().expandTowards(player.getDeltaMovement()).inflate(1.0);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, path,
                e -> e != player && e.isAlive() && !DragonMinions.isOwnedBy(player, e))) {
            if (target.hurt(level.damageSources().playerAttack(player), damage)) {
                Vec3 away = target.position().subtract(player.position()).normalize();
                target.setDeltaMovement(away.x * 3.0, 1.0, away.z * 3.0);
                target.hurtMarked = true;
            }
        }
    }

    private static void toggleCrater(ServerPlayer player) {
        boolean on = !CRATER_ARMED.remove(player.getUUID());
        if (on) {
            CRATER_ARMED.add(player.getUUID());
        }
        player.displayClientMessage(Component.literal(
                on ? "Crater punch ARMED" : "Crater punch off")
                .withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
    }

    /**
     * Blink to where you are looking. If the dragon does not fit there, search
     * outward for somewhere it does — the same idea as chorus fruit, but the
     * fit test uses the real (very large) hitbox rather than a single block.
     */
    private static void teleport(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getLookAngle().scale(TELEPORT_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 aim = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.5))
                : far;

        Vec3 dest = fits(player, level, aim) ? aim : nearestFit(player, level, aim);
        if (dest == null) {
            player.displayClientMessage(Component.literal("Nowhere there will hold you.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.teleportTo(dest.x, dest.y, dest.z);
        player.connection.resetPosition();
        DragonFlight.clear(player);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /** Does the player's full bounding box sit clear of terrain at {@code at}? */
    private static boolean fits(ServerPlayer player, ServerLevel level, Vec3 at) {
        AABB box = player.getBoundingBox().move(at.subtract(player.position()));
        return level.noCollision(player, box) && level.getWorldBorder().isWithinBounds(box);
    }

    /** Spiral outward from the aim point looking for room, then settle onto ground. */
    private static Vec3 nearestFit(ServerPlayer player, ServerLevel level, Vec3 aim) {
        double height = player.getBbHeight();
        for (int radius = 1; radius <= 8; radius++) {
            for (int i = 0; i < 12; i++) {
                double angle = (Math.PI * 2 / 12) * i;
                double x = aim.x + Math.cos(angle) * radius * 2.0;
                double z = aim.z + Math.sin(angle) * radius * 2.0;
                for (int dy = 0; dy <= 6; dy++) {
                    for (int sign : new int[]{1, -1}) {
                        Vec3 candidate = new Vec3(x, aim.y + dy * sign, z);
                        if (candidate.y < level.getMinBuildHeight()
                                || candidate.y + height > level.getMaxBuildHeight()) {
                            continue;
                        }
                        if (fits(player, level, candidate)) {
                            return settle(player, level, candidate);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Drop the candidate onto the first ground below it, so you never hover. */
    private static Vec3 settle(ServerPlayer player, ServerLevel level, Vec3 at) {
        Vec3 cur = at;
        for (int i = 0; i < 64; i++) {
            Vec3 below = cur.subtract(0.0, 1.0, 0.0);
            if (below.y < level.getMinBuildHeight() || !fits(player, level, below)) {
                return cur;
            }
            cur = below;
        }
        return cur;
    }

    /** The 5x5x5 crater, used by the left-click hook when the toggle is armed. */
    public static void crater(ServerPlayer player, BlockPos centre) {
        ServerLevel level = player.serverLevel();
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 2, 2))) {
            var state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
                continue;                       // bedrock, barriers and the like stay put
            }
            level.destroyBlock(pos.immutable(), true, player);
            broken++;
        }
        if (broken > 0) {
            level.playSound(null, centre, SoundEvents.ENDER_DRAGON_FLAP,
                    SoundSource.PLAYERS, 1.2F, 0.5F);
            level.sendParticles(ParticleTypes.EXPLOSION, centre.getX() + 0.5,
                    centre.getY() + 0.5, centre.getZ() + 0.5, 6, 1.5, 1.5, 1.5, 0.0);
        }
    }

    private static float byDifficulty(Difficulty difficulty, float easy, float normal, float hard) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0F;
            case EASY -> easy;
            case NORMAL -> normal;
            case HARD -> hard;
        };
    }
}
