package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import com.enderdragonanthro.mixin.MobGoalAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
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
 * One exception: endermen, which are too friendly, and which the court is made
 * of. Everything else in the world runs.
 *
 * Being hit still provokes an enderman. One that does not know to run from a
 * dragon still knows it has been hit by one.
 *
 * Bats are afraid too, and are handled in BatFearMixin rather than here,
 * because a bat is the one common mob whose movement does not go through the
 * goal selector at all.
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

    /**
     * Drop everything held for a world that is no longer loaded.
     *
     * See {@link com.enderdragonanthro.ServerMemory}: these maps are static, and
     * static is per process rather than per world. Singleplayer runs the server
     * inside the client, so without this they carry into the next save you open.
     */
    public static void forgetWorld() {
        PROVOKED.clear();
    }

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
     * Livestock were exempt at first, on the theory that a field emptying the
     * moment you walked into it would make the overworld unusable. Played, it
     * was the other way round: the cows standing placidly in the middle of a
     * stampede were what broke it, because they were the one thing on screen
     * saying you were not frightening. Everything runs now.
     *
     * Endermen stay, and they are the whole of the list — the court is made of
     * them, and a shade that flees the dragon she serves is not a shade.
     */
    private static boolean unbothered(Mob mob) {
        return mob instanceof EnderMan;
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
        ((MobGoalAccessor) mob).enderdragonanthro$goals().addGoal(FEAR_PRIORITY, new Fear(mob));
    }

    /**
     * AvoidEntityGoal's shape, without its targeting conditions.
     *
     * The vanilla goal cannot be used here, and the reason is the blanket rule
     * again. AvoidEntityGoal looks for what to avoid through
     * {@code TargetingConditions.forCombat()}, and that test calls both
     * canBeSeenAsEnemy and canAttack on the candidate — so a dragon, which
     * answers no to being anyone's enemy, is invisible to it. The goal was
     * installed on every mob and could never find anything to run from.
     *
     * Everything else is copied from it deliberately: MOVE flag so it competes
     * for the navigation, a path computed away from the threat, walk while far
     * and sprint while near, and it ends when the path does. Only the search is
     * ours, and it just looks for the nearest dragon in range with line of
     * sight.
     */
    private static final class Fear extends Goal {
        private final PathfinderMob mob;
        private Player threat;
        private Path path;

        private Fear(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.threat = dreadedBy(this.mob);
            if (this.threat == null) {
                return false;
            }
            Vec3 away = DefaultRandomPos.getPosAway(this.mob, 16, 7, this.threat.position());
            if (away == null || this.threat.distanceToSqr(away.x, away.y, away.z)
                    < this.threat.distanceToSqr(this.mob)) {
                return false;                 // no point running somewhere worse
            }
            this.path = this.mob.getNavigation().createPath(away.x, away.y, away.z, 0);
            return this.path != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.mob.getNavigation().isDone();
        }

        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.path, WALK);
        }

        @Override
        public void stop() {
            this.threat = null;
        }

        @Override
        public void tick() {
            // Sprinting only once it is close, exactly as the vanilla goal does.
            this.mob.getNavigation().setSpeedModifier(
                    this.threat != null && this.mob.distanceToSqr(this.threat) < 49.0
                            ? SPRINT : WALK);
        }
    }

    /**
     * The dragon this creature can see and has no quarrel with, if any.
     *
     * Ours rather than TargetingConditions', and that is the point. Every
     * vanilla way of asking "what is near me that matters" routes through
     * TargetingConditions.forCombat, whose test calls canBeSeenAsEnemy and
     * canAttack -- and a dragon answers no to being anybody's enemy, by the
     * blanket rule that makes the world stop attacking you. So a dragon is
     * invisible to every vanilla search, including the avoid goal's, which is
     * why the fear was installed on every mob in the world and never once fired.
     *
     * Line of sight is required because this is fear at the sight of you, not
     * dread through a wall.
     */
    public static Player dreadedBy(Mob mob) {
        Player best = null;
        double closest = NOTICE * NOTICE;
        for (Player player : mob.level().players()) {
            if (!DragonFormManager.isDragon(player) || player.isSpectator()
                    || provoked(mob, player)) {
                continue;
            }
            double gap = player.distanceToSqr(mob);
            if (gap < closest && mob.getSensing().hasLineOfSight(player)) {
                closest = gap;
                best = player;
            }
        }
        return best;
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
