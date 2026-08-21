package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What it is like to be near a dragon.
 *
 * The old rule was one line — nothing can see a transformed player as an enemy
 * — and it produced a world of livestock. Nothing attacked, nothing fled,
 * nothing reacted at all, which reads less like being terrifying and more like
 * being furniture.
 *
 * Two rules instead. Anything that can see you runs, and anything you actually
 * hit stops running and comes back at you. The second is what makes the first
 * mean something: fleeing from a thing that could never hurt you is just an
 * animation, and fighting a thing that never provoked you is just vanilla.
 *
 * Four exceptions, and they are the artist's:
 *
 * <ul>
 *   <li>pigs, cows and sheep, which are too dumb to know what they are looking
 *       at — and a field that empties the moment you walk into it would make
 *       the overworld unusable;
 *   <li>endermen, which are too friendly, and which the court is made of.
 * </ul>
 *
 * Being hit still provokes an exempt animal. A cow that does not know to run
 * from a dragon still knows it has been hit by one.
 */
public final class DragonPresence {
    /** How far a mob notices you from. Vanilla's own avoid range is 16. */
    private static final double NOTICE = 20.0;
    /** How far it runs before it stops to think. */
    private static final int FLEE_DISTANCE = 20;
    private static final int FLEE_HEIGHT = 8;
    /** Panic is faster than a walk and slower than a sprint. */
    private static final double FLEE_SPEED = 1.45;
    /** How long being hit keeps a mob angry, in ticks. */
    private static final int GRUDGE = 400;
    /** Only look for something to run to this often; pathing is not cheap. */
    private static final int REPATH = 20;

    private static final Map<UUID, Long> PROVOKED = new HashMap<>();

    private DragonPresence() {
    }

    /**
     * Whether this mob has a reason to be angry at you.
     *
     * Read from the damage hook rather than from a scoreboard tag, because the
     * mixin that grants the attack runs on both sides and tags do not.
     */
    public static boolean provoked(LivingEntity mob, Player dragon) {
        Long until = PROVOKED.get(mob.getUUID());
        return until != null && mob.level().getGameTime() < until;
    }

    /** You hit it. It has noticed. */
    public static void provoke(LivingEntity mob, ServerPlayer dragon) {
        PROVOKED.put(mob.getUUID(), mob.level().getGameTime() + GRUDGE);
        if (mob instanceof Mob angry) {
            // Set directly: the goals cannot acquire a target they are not
            // allowed to see, so the target is given to them and the attack
            // permission comes from the canAttack mixin.
            angry.setTarget(dragon);
            angry.setLastHurtByMob(dragon);
        }
    }

    /**
     * The ones that do not run.
     *
     * Deliberately by class rather than by tag, because this is a character
     * judgement rather than a category: sheep are exempt for being sheep.
     */
    private static boolean unbothered(Mob mob) {
        return mob instanceof Pig || mob instanceof Cow || mob instanceof Sheep
                || mob instanceof EnderMan;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % REPATH != 0) {
            return;
        }
        PROVOKED.values().removeIf(until -> until < server.overworld().getGameTime() - GRUDGE * 4);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!DragonFormManager.isDragon(player)) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            // PathfinderMob rather than Mob: getPosAway wants something that
            // walks, and anything that does not (a ghast, a shulker) has no
            // ground path to run down anyway.
            for (net.minecraft.world.entity.PathfinderMob mob : level.getEntitiesOfClass(
                    net.minecraft.world.entity.PathfinderMob.class,
                    player.getBoundingBox().inflate(NOTICE))) {
                if (unbothered(mob) || !mob.isAlive()
                        || DragonMinions.isOwnedBy(player, mob)
                        || provoked(mob, player)) {
                    continue;                       // angry things do not flee
                }
                if (!mob.getSensing().hasLineOfSight(player)) {
                    continue;                       // it has to see you to be afraid
                }
                Vec3 away = DefaultRandomPos.getPosAway(mob, FLEE_DISTANCE, FLEE_HEIGHT,
                        player.position());
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
                }
            }
        }
    }
}
