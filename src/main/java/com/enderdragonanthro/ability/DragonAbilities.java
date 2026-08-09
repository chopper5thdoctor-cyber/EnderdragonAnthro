package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side ability execution. Damage numbers are the canon dragon's
 * (DESIGN.md 1.3): head/charge 6/10/15 and wing 3/5/7 by difficulty;
 * breath and fireball reuse vanilla dragon-breath effect clouds.
 */
public final class DragonAbilities {
    /** UUID -> (action -> game time when usable again) */
    private static final Map<UUID, Map<AbilityAction, Long>> COOLDOWNS = new HashMap<>();
    /** UUID -> remaining ticks of an active charge dash */
    private static final Map<UUID, Integer> ACTIVE_CHARGES = new HashMap<>();

    private DragonAbilities() {
    }

    public static void trigger(ServerPlayer player, AbilityAction action) {
        if (!DragonFormManager.isDragon(player)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Map<AbilityAction, Long> cds = COOLDOWNS.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
        long readyAt = cds.getOrDefault(action, 0L);
        if (now < readyAt) {
            long secondsLeft = (readyAt - now + 19) / 20;
            player.displayClientMessage(
                    Component.translatable("message.enderdragonanthro.cooldown", secondsLeft), true);
            return;
        }
        cds.put(action, now + action.cooldownTicks);

        switch (action) {
            case BREATH -> breath(player);
            case FIREBALL -> fireball(player);
            case BUFFET -> wingBuffet(player);
            case CHARGE -> beginCharge(player);
            case TRANSFORM -> {
            }
        }
    }

    public static void tick(MinecraftServer server) {
        // Automatic charge: a sprinting dragon is a charging dragon — anything
        // it runs (or fly-sprints) into takes canon charge damage, no key needed.
        // The knockback throws victims clear, which naturally prevents re-hits.
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
            int ticksLeft = entry.getValue();
            entry.setValue(ticksLeft - 1);
            // Sustained propulsion: a single impulse gets eaten by ground
            // friction before the client feels it, so push every tick for the
            // first phase of the dash (steerable — reads the live look vector).
            // The motion packet is sent directly: the entity-tracker relay
            // (hurtMarked) can drop self-motion for players depending on tick
            // ordering, which made the dash silently do nothing.
            if (ticksLeft > 4) {
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(look.x * 1.8, look.y * 0.9 + 0.1, look.z * 1.8);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
            chargeContactDamage(player);
        }
    }

    /**
     * Exhale along the look ray: a trail of lingering harming clouds with
     * dragon-breath particles, the same recipe the perched dragon uses.
     */
    private static void breath(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 mouth = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (int i = 2; i <= 10; i += 2) {
            Vec3 at = mouth.add(look.scale(i));
            AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
            cloud.setOwner(player);
            cloud.setParticle(ParticleTypes.DRAGON_BREATH);
            cloud.setRadius(2.0F);
            cloud.setDuration(100);
            cloud.setRadiusOnUse(-0.5F);
            cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
            level.addFreshEntity(cloud);
        }
        level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void fireball(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        DragonFireball ball = new DragonFireball(level, player, look);
        ball.setPos(player.getEyePosition().add(look.scale(2.0)));
        level.addFreshEntity(ball);
        level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_SHOOT,
                net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    /** Canon wing hit: modest damage, absurd knockback. */
    private static void wingBuffet(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(6.0, 3.0, 6.0);
        float damage = byDifficulty(level.getDifficulty(), 3.0F, 5.0F, 7.0F);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive())) {
            target.hurt(level.damageSources().playerAttack(player), damage);
            Vec3 away = target.position().subtract(player.position()).normalize();
            target.setDeltaMovement(away.x * 2.0, 0.8, away.z * 2.0);
            target.hurtMarked = true;
        }
        level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP,
                net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void beginCharge(ServerPlayer player) {
        ACTIVE_CHARGES.put(player.getUUID(), 12);
        player.serverLevel().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 1.4F);
    }

    private static void chargeContactDamage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float damage = byDifficulty(level.getDifficulty(), 6.0F, 10.0F, 15.0F);
        AABB path = player.getBoundingBox().expandTowards(player.getDeltaMovement()).inflate(1.0);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, path,
                e -> e != player && e.isAlive())) {
            if (target.hurt(level.damageSources().playerAttack(player), damage)) {
                Vec3 away = target.position().subtract(player.position()).normalize();
                target.setDeltaMovement(away.x * 3.0, 1.0, away.z * 3.0);
                target.hurtMarked = true;
            }
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
