package com.enderdragonanthro.transform;

import com.enderdragonanthro.ability.CrystalHealing;
import com.enderdragonanthro.boss.DragonBossBars;
import com.enderdragonanthro.config.DragonConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * Applies and removes the dragon form. All numbers trace back to DESIGN.md:
 * the canon dragon hitbox is 8 blocks tall, the player 1.8 — so scale is
 * 8 / 1.8. That height is a config knob now, and everything derived from size
 * — step, reach, safe fall, jump — moves with it, so a taller dragon is
 * consistently taller rather than a big model on a small creature's stats. The
 * canon numbers that are not about size (200 HP, the damage figures) stay put.
 * Max health becomes the canon 200. Modifiers are transient
 * (not saved to NBT); the persistent truth is the TransformAccess flag,
 * and onJoin/onRespawn re-dress the player from it.
 */
public final class DragonFormManager {
    /** The canon figure, and the default. DragonConfig can move it. */
    public static final double SCALE_FACTOR = 8.0 / 1.8;

    private record Mod(ResourceLocation id, Holder<Attribute> attribute, double amount,
                       AttributeModifier.Operation operation) {
    }

    /**
     * Built per call rather than held as a constant: the size-derived numbers
     * depend on the configured height, which is not known until the config has
     * been read.
     */
    /** The player's own jump strength, which everything here is measured against. */
    private static final double VANILLA_JUMP = 0.42;

    private static List<Mod> modifiers() {
        double size = DragonConfig.sizeRatio();      // 1.0 at the canon eight blocks
        return List.of(
            // base scale 1.0 -> 4.444 (8 blocks tall, ~2.67 wide; vanilla cap is 16)
            new Mod(id("dragon_scale"), Attributes.SCALE, DragonConfig.scaleFactor() - 1.0,
                    AttributeModifier.Operation.ADD_VALUE),
            // 20 -> 200 HP, canon
            new Mod(id("dragon_health"), Attributes.MAX_HEALTH, 180.0,
                    AttributeModifier.Operation.ADD_VALUE),
            // base 0.6 -> 2.5: an 8-block dragon does not hop up single blocks
            new Mod(id("dragon_step"), Attributes.STEP_HEIGHT, 1.9 * size,
                    AttributeModifier.Operation.ADD_VALUE),
            // base 3 -> 13 blocks of safe fall
            new Mod(id("dragon_safe_fall"), Attributes.SAFE_FALL_DISTANCE, 10.0 * size,
                    AttributeModifier.Operation.ADD_VALUE),
            // reach scaled so you can touch the ground at your own feet
            new Mod(id("dragon_block_reach"), Attributes.BLOCK_INTERACTION_RANGE, 8.0 * size,
                    AttributeModifier.Operation.ADD_VALUE),
            new Mod(id("dragon_entity_reach"), Attributes.ENTITY_INTERACTION_RANGE, 8.0 * size,
                    AttributeModifier.Operation.ADD_VALUE),
            // Stride proportional to height: speed/size holds at 1, the same
            // ratio the first-person bob keeps. At the canon eight blocks that
            // is 4.44x rather than the 3x this used to be capped at -- which was
            // a deliberate trim, so expect ground travel to outrun elytra
            // cruising and terrain to read faster than it used to.
            new Mod(id("dragon_speed"), Attributes.MOVEMENT_SPEED,
                    DragonConfig.scaleFactor() - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            // Jump HEIGHT proportional, which is not the same as jump strength
            // proportional: height goes as the square of the launch speed, so
            // multiplying strength by the size ratio would give a jump 4.44x too
            // energetic and about twenty blocks tall. The square root is what
            // makes a creature 4.44x your size clear 4.44x your hop -- roughly
            // 5.5 blocks at the canon eight, well inside the 13-block safe fall.
            new Mod(id("dragon_jump"), Attributes.JUMP_STRENGTH,
                    VANILLA_JUMP * (Math.sqrt(DragonConfig.scaleFactor()) - 1.0),
                    AttributeModifier.Operation.ADD_VALUE),
            // canon head-hit is 10 on Normal: bare fist 1 -> 10
            new Mod(id("dragon_attack"), Attributes.ATTACK_DAMAGE, 9.0,
                    AttributeModifier.Operation.ADD_VALUE),
            // dragon hits launch people
            new Mod(id("dragon_attack_knockback"), Attributes.ATTACK_KNOCKBACK, 1.5,
                    AttributeModifier.Operation.ADD_VALUE));
    }

    private DragonFormManager() {
    }

    public static boolean isDragon(Player player) {
        return ((TransformAccess) player).enderdragonanthro$isDragon();
    }

    public static void toggle(ServerPlayer player) {
        if (isDragon(player)) {
            removeForm(player);
        } else {
            applyForm(player);
        }
    }

    public static void applyForm(ServerPlayer player) {
        ((TransformAccess) player).enderdragonanthro$setDragon(true);
        dress(player);
        player.setHealth(player.getMaxHealth());
        DragonBossBars.add(player);
        transformBurst(player);
    }

    public static void removeForm(ServerPlayer player) {
        ((TransformAccess) player).enderdragonanthro$setDragon(false);
        undress(player);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        DragonBossBars.remove(player);
        CrystalHealing.unlink(player);
        transformBurst(player);
    }

    public static void copyState(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        ((TransformAccess) newPlayer).enderdragonanthro$setDragon(isDragon(oldPlayer));
    }

    public static void onJoin(ServerPlayer player) {
        if (isDragon(player)) {
            dress(player);
            resync(player);
            DragonBossBars.add(player);
        }
    }

    /**
     * Push the attributes to the client again, after dressing on login.
     *
     * The client is sent its attributes as part of being placed in the world,
     * and JOIN fires after that packet has gone. So a modifier added here is
     * real on the server and unknown to the client until something else
     * happens to dirty it — which for MAX_HEALTH meant a dragon logging back in
     * saw ten hearts and had a hundred. The scale modifier hid it: that one is
     * read straight off the attribute by the HUD, so the hearts went purple on
     * a bar that was still vanilla length.
     *
     * Health is set after the resync rather than before, because clamping
     * against a max the client has not heard about is how the bar ends up
     * disagreeing with the number behind it.
     */
    private static void resync(ServerPlayer player) {
        player.connection.send(new net.minecraft.network.protocol.game
                .ClientboundUpdateAttributesPacket(player.getId(),
                        player.getAttributes().getSyncableAttributes()));
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    public static void onRespawn(ServerPlayer player) {
        if (isDragon(player)) {
            dress(player);
            player.setHealth(player.getMaxHealth());
            DragonBossBars.add(player);
        }
    }

    public static void onLeave(ServerPlayer player) {
        DragonBossBars.remove(player);
        CrystalHealing.unlink(player);
    }

    /**
     * How long the end takes to close behind a transformation.
     *
     * Not a balance cost -- Y costs nothing and should stay free -- but the
     * burst is six hundred particles, and a key you can hold down would post
     * that to every client in view distance as fast as the server ticks.
     */
    private static final int BURST_COOLDOWN = 40;
    /** Ambient drift, in ticks between puffs. Endermen do this constantly. */
    private static final int AMBIENT_EVERY = 3;
    private static final Map<UUID, Long> LAST_BURST = new HashMap<>();

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isDragon(player)) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (level.getGameTime() % AMBIENT_EVERY != 0) {
                continue;
            }
            // The same drift an enderman carries, sized to a body eight blocks
            // tall instead of three so it wraps the whole dragon.
            level.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                    12, player.getBbWidth() * 0.5, player.getBbHeight() * 0.4,
                    player.getBbWidth() * 0.5, 0.35);
        }
        LAST_BURST.keySet().removeIf(u -> server.getPlayerList().getPlayer(u) == null);
    }

    /** The end tearing open around a transformation, both directions. */
    private static void transformBurst(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = LAST_BURST.get(player.getUUID());
        if (last != null && now - last < BURST_COOLDOWN) {
            return;
        }
        LAST_BURST.put(player.getUUID(), now);

        double h = player.getBbHeight();
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + h * 0.5, player.getZ(),
                500, 1.2, h * 0.6, 1.2, 1.4);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + h * 0.5, player.getZ(),
                140, 0.8, h * 0.5, 0.8, 0.7);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                player.getX(), player.getY() + h * 0.35, player.getZ(),
                60, 0.9, h * 0.3, 0.9, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.4F, 0.5F);
    }

    private static void dress(ServerPlayer player) {
        for (Mod mod : modifiers()) {
            AttributeInstance instance = player.getAttribute(mod.attribute());
            if (instance == null) {
                continue;
            }
            instance.removeModifier(mod.id());
            instance.addTransientModifier(new AttributeModifier(
                    mod.id(), mod.amount(), mod.operation()));
        }
        // Flight is elytra-style gliding (DragonFlight), not creative flight.
        player.onUpdateAbilities();
    }

    private static void undress(ServerPlayer player) {
        for (Mod mod : modifiers()) {
            AttributeInstance instance = player.getAttribute(mod.attribute());
            if (instance != null) {
                instance.removeModifier(mod.id());
            }
        }
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        player.stopFallFlying();
    }
}
