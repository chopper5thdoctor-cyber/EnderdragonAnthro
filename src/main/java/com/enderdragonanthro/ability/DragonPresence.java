package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import com.enderdragonanthro.mixin.MobGoalAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;

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
    /** Walking away, and running once it is close. Creeper numbers are 1.0/1.2. */
    private static final double WALK = 1.0;
    private static final double SPRINT = 1.45;
    /** How long being hit keeps a mob angry, in ticks. */
    private static final int GRUDGE = 400;
    /** Sweeping the grudge list does not need doing every tick. */
    private static final int SWEEP = 100;

    /** Above strolling, below the goals that keep a mob alive. Creepers use 3. */
    private static final int FEAR_PRIORITY = 3;

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

    /**
     * Give a mob its fear when it loads, as a goal.
     *
     * The first version of this pushed mobs around from a tick handler --
     * looking up a spot to run to and calling moveTo directly. That is not how
     * anything in the game is afraid of anything, and it does not work like it
     * either: the mob's own strolling and looking goals own the navigation, so
     * an outside path gets overwritten a tick later and the mob wanders back.
     *
     * A creeper avoiding a cat is AvoidEntityGoal at priority 3, and this is
     * the same goal with the same shape -- it competes in the goal selector, it
     * interrupts strolling, it walks until the threat is far and sprints while
     * it is near, and it stops on its own. The only difference is the predicate:
     * a dragon rather than a cat, and only while this mob has no grudge, since
     * something that has decided to fight you should not also be running away.
     */
    public static void afraidOfDragons(Entity entity) {
        if (!(entity instanceof PathfinderMob mob) || unbothered(mob) || afraid(mob)) {
            return;
        }
        ((MobGoalAccessor) mob).enderdragonanthro$goals().addGoal(FEAR_PRIORITY,
                new Fear<>(mob, Player.class, (float) NOTICE, WALK, SPRINT,
                        living -> living instanceof Player player
                                && DragonFormManager.isDragon(player)
                                && !provoked(mob, player)));
    }

    /**
     * A marker subclass, so a mob can be asked whether it already has this.
     *
     * AvoidEntityGoal is used by half the mobs in the game for their own
     * reasons, so "does it have an AvoidEntityGoal" is not the same question as
     * "does it have ours".
     */
    private static final class Fear<T extends LivingEntity> extends AvoidEntityGoal<T> {
        private Fear(PathfinderMob mob, Class<T> avoid, float distance, double walk,
                     double sprint, java.util.function.Predicate<LivingEntity> when) {
            super(mob, avoid, distance, walk, sprint, when);
        }
    }

    private static boolean afraid(PathfinderMob mob) {
        return ((MobGoalAccessor) mob).enderdragonanthro$goals().getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof Fear);
    }

    /**
     * Housekeeping, and catching the mobs that were already here.
     *
     * ENTITY_LOAD only fires for something entering the world, so every mob
     * standing in an already-loaded chunk when the mod started -- which is all
     * of them, in a world you were already playing -- never got the goal and
     * never ran. Nearby mobs are equipped here as well, which is idempotent
     * because Fear is a marker subclass that can be looked for.
     */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % SWEEP != 0) {
            return;
        }
        long now = server.overworld().getGameTime();
        PROVOKED.values().removeIf(until -> until < now);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!DragonFormManager.isDragon(player)) {
                continue;
            }
            for (PathfinderMob mob : player.serverLevel().getEntitiesOfClass(
                    PathfinderMob.class, player.getBoundingBox().inflate(NOTICE * 2))) {
                afraidOfDragons(mob);
            }
        }
    }
}
