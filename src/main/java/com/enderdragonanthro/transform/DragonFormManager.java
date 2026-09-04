package com.enderdragonanthro.transform;

import com.enderdragonanthro.ability.CrystalHealing;
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

    /** The one modifier DragonIntent rewrites, so both ends agree on its name. */
    private static final ResourceLocation ATTACK_ID = id("dragon_attack");

    /**
     * What the form is worth, as totals rather than as bonuses.
     *
     * Public because the court is balanced against them: a shade is never
     * allowed to be stronger than the dragon she is defending, and the only
     * way to keep that true through a rebalance is for her ceiling to be
     * derived from these rather than typed next to them. See
     * DragonMinions#COURT_CEILING.
     */
    public static final double HEALTH_BONUS = 180.0;
    /** Where the claw starts; DragonIntent rewrites it the moment the dial moves. */
    public static final double ATTACK_BONUS = 34.0;
    /** Vanilla player base, which the bonus above is added to. */
    public static final double DRAGON_HEALTH = 20.0 + HEALTH_BONUS;

    /**
     * The hardest this form can hit, whatever the dial currently says.
     *
     * A CEILING rather than the live value, and the distinction is the whole
     * point of it. The claw is DragonIntent: 10 on Passive, 35 on Alert and
     * Hostile. Passive is a dragon choosing to hold back, not a dragon who has
     * become weak -- so the court is measured against what she can do, and four
     * shades do not get feebler because their dragon put the dial down.
     *
     * Read off the enum rather than repeated here, so a stop added or retuned
     * moves the bar with it.
     */
    public static double dragonAttack() {
        double best = 0.0;
        for (com.enderdragonanthro.ability.DragonIntent stop
                : com.enderdragonanthro.ability.DragonIntent.values()) {
            best = Math.max(best, stop.attackBonus());
        }
        return 1.0 + best;
    }

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
            new Mod(id("dragon_health"), Attributes.MAX_HEALTH, HEALTH_BONUS,
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
            /*
             * Boss weight, benchmarked against the Warden.
             *
             * This was 9 — bare fist 1 to 10, the canon dragon's head-hit on
             * Normal — and it is the number that kept the form from reading as
             * a boss at all. The Warden hits for 30 and is the yardstick
             * anybody reaches for; ten is a stone sword. Through netherite it
             * landed for about two.
             *
             * 34 puts a bare claw at 35, above the Warden, and a weapon still
             * stacks on top of it.
             */
            new Mod(ATTACK_ID, Attributes.ATTACK_DAMAGE, ATTACK_BONUS,
                    AttributeModifier.Operation.ADD_VALUE),
            // dragon hits launch people
            new Mod(id("dragon_attack_knockback"), Attributes.ATTACK_KNOCKBACK, 1.5,
                    AttributeModifier.Operation.ADD_VALUE),
            /*
             * And is not launched itself.
             *
             * The Warden has this at 1.0 and it is half of why it reads as a
             * boss: a thing that staggers backwards every time a sword lands is
             * being handled, not fought. Eight tonnes of dragon should not skid
             * when a human swings at it.
             */
            new Mod(id("dragon_knockback_resist"), Attributes.KNOCKBACK_RESISTANCE, 1.0,
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
        transformBurst(player);
    }

    public static void removeForm(ServerPlayer player) {
        ((TransformAccess) player).enderdragonanthro$setDragon(false);
        undress(player);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
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
        }
    }

    public static void onLeave(ServerPlayer player) {
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

    /**
     * Drop everything held for a world that is no longer loaded.
     *
     * See {@link com.enderdragonanthro.ServerMemory}: these maps are static, and
     * static is per process rather than per world. Singleplayer runs the server
     * inside the client, so without this they carry into the next save you open.
     */
    public static void forgetWorld() {
        LAST_BURST.clear();
    }

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

    /**
     * Permanent modifiers, not transient ones, and that word means "saved".
     *
     * AttributeInstance.save() writes only permanentModifiers; a transient one
     * exists until the player file is written and then does not. That is the
     * whole of the relog bug. Attributes are read back before Health is —
     * LivingEntity.readAdditionalSaveData does them in that order — so a dragon
     * who logged out at a hundred came back with a maximum of twenty, had its
     * saved Health clamped down to twenty by setHealth, and only then got its
     * modifiers put back on by JOIN. Ten hearts full, ninety empty, and a slow
     * climb back. The resync packet was never the missing piece; the health had
     * already been thrown away by the time anything on the client mattered.
     *
     * Health is carried across the swap because removing a max-health modifier
     * and adding it back is a moment where the maximum is briefly twenty.
     */
    private static void dress(ServerPlayer player) {
        float health = player.getHealth();
        for (Mod mod : modifiers()) {
            AttributeInstance instance = player.getAttribute(mod.attribute());
            if (instance == null) {
                continue;
            }
            instance.removeModifier(mod.id());
            instance.addPermanentModifier(new AttributeModifier(
                    mod.id(), mod.amount(), mod.operation()));
        }
        // The claw is not a constant: DragonIntent decides how much of it is
        // out, and the list above only carries the value it starts at. Applied
        // after, so the stop the player was on survives a re-dress.
        com.enderdragonanthro.ability.DragonIntent.refresh(player);
        com.enderdragonanthro.ability.DragonIntent.tell(player);
        // Flight is elytra-style gliding (DragonFlight), not creative flight.
        player.onUpdateAbilities();
        player.setHealth(Math.min(health, player.getMaxHealth()));
    }

    /**
     * Rewrite the claw alone, without disturbing the other nine modifiers.
     *
     * Damage comes off ATTACK_DAMAGE and the game reads that attribute rather
     * than asking us, so a dial that changes how hard you hit has to change the
     * attribute. Permanent rather than transient for the reason every modifier
     * here is: AttributeInstance.save writes only the permanent ones, so a
     * transient claw would be twenty again after a relog.
     */
    public static void setAttackBonus(ServerPlayer player, double bonus) {
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        instance.removeModifier(ATTACK_ID);
        instance.addPermanentModifier(new AttributeModifier(
                ATTACK_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
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
