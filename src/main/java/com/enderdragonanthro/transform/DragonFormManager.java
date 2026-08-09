package com.enderdragonanthro.transform;

import com.enderdragonanthro.boss.DragonBossBars;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.List;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * Applies and removes the dragon form. All numbers trace back to DESIGN.md:
 * the canon dragon hitbox is 8 blocks tall, the player 1.8 — so scale is
 * 8 / 1.8. Max health becomes the canon 200. Modifiers are transient
 * (not saved to NBT); the persistent truth is the TransformAccess flag,
 * and onJoin/onRespawn re-dress the player from it.
 */
public final class DragonFormManager {
    public static final double SCALE_FACTOR = 8.0 / 1.8;

    private record Mod(ResourceLocation id, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double amount) {
    }

    private static final List<Mod> MODIFIERS = List.of(
            // base scale 1.0 -> 4.444 (8 blocks tall, ~2.67 wide; vanilla cap is 16)
            new Mod(id("dragon_scale"), Attributes.SCALE, SCALE_FACTOR - 1.0),
            // 20 -> 200 HP, canon
            new Mod(id("dragon_health"), Attributes.MAX_HEALTH, 180.0),
            // base 0.6 -> 2.5: an 8-block dragon does not hop up single blocks
            new Mod(id("dragon_step"), Attributes.STEP_HEIGHT, 1.9),
            // base 3 -> 13 blocks of safe fall
            new Mod(id("dragon_safe_fall"), Attributes.SAFE_FALL_DISTANCE, 10.0),
            // reach scaled so you can touch the ground at your own feet
            new Mod(id("dragon_block_reach"), Attributes.BLOCK_INTERACTION_RANGE, 8.0),
            new Mod(id("dragon_entity_reach"), Attributes.ENTITY_INTERACTION_RANGE, 8.0)
    );

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
    }

    public static void removeForm(ServerPlayer player) {
        ((TransformAccess) player).enderdragonanthro$setDragon(false);
        undress(player);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        DragonBossBars.remove(player);
    }

    public static void copyState(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        ((TransformAccess) newPlayer).enderdragonanthro$setDragon(isDragon(oldPlayer));
    }

    public static void onJoin(ServerPlayer player) {
        if (isDragon(player)) {
            dress(player);
            DragonBossBars.add(player);
        }
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
    }

    private static void dress(ServerPlayer player) {
        for (Mod mod : MODIFIERS) {
            AttributeInstance instance = player.getAttribute(mod.attribute());
            if (instance == null) {
                continue;
            }
            instance.removeModifier(mod.id());
            instance.addTransientModifier(new AttributeModifier(
                    mod.id(), mod.amount(), AttributeModifier.Operation.ADD_VALUE));
        }
        // Wing flight, creative-style for M1 (stamina model is a later milestone)
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
    }

    private static void undress(ServerPlayer player) {
        for (Mod mod : MODIFIERS) {
            AttributeInstance instance = player.getAttribute(mod.attribute());
            if (instance != null) {
                instance.removeModifier(mod.id());
            }
        }
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }
}
