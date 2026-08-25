package com.enderdragonanthro.gametest;

import com.enderdragonanthro.ability.DragonAbilities;
import com.enderdragonanthro.ability.DragonFire;
import com.enderdragonanthro.ability.DragonIntent;
import com.enderdragonanthro.ability.DragonMinions;
import com.enderdragonanthro.ability.DragonPresence;
import com.enderdragonanthro.ability.NetherGate;
import com.enderdragonanthro.transform.DragonFormManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The world, asserted on.
 *
 * Every bug this mod has shipped was one of two shapes: the code never ran, or
 * the code ran and the world did not change the way the commit message said it
 * did. The build catches neither. audit_injections.py catches the first. This
 * catches the second, and it is the only thing in the project that does.
 *
 * Each test here is a bug that actually shipped, written as the assertion that
 * would have caught it. That is the selection rule — not coverage for its own
 * sake, but the specific failures this project has already paid for once.
 *
 * Run with {@code ./gradlew runGametest}.
 */
public class DragonGameTests implements FabricGameTest {
    /** Ticks to let a goal notice, path and walk. Fear repaths every second. */
    private static final int SETTLE = 80;

    /**
     * SHIPPED BUG: mobs did not run.
     *
     * The fear goal was installed on every mob in the world and could not fire,
     * because AvoidEntityGoal searches through TargetingConditions.forCombat(),
     * whose test calls canBeSeenAsEnemy -- which a dragon answers no to. It was
     * on every mob, doing nothing, for several builds, and the only signal was
     * a player saying "mobs aren't running from me".
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void mobsFleeADragon(GameTestHelper helper) {
        floor(helper, 12);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 1));
        DragonPresence.afraidOfDragons(zombie);
        double before = zombie.distanceTo(dragon);

        helper.runAfterDelay(SETTLE, () -> {
            double after = zombie.distanceTo(dragon);
            if (after <= before + 1.0) {
                helper.fail(String.format(
                        "the zombie did not flee: %.1f blocks away, was %.1f",
                        after, before));
            }
            helper.succeed();
        });
    }

    /** ...and an enderman is the one thing that does not, because the court is made of them. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void endermenDoNotFlee(GameTestHelper helper) {
        var enderman = helper.spawn(EntityType.ENDERMAN, new BlockPos(1, 1, 1));
        DragonPresence.afraidOfDragons(enderman);
        boolean afraid = ((com.enderdragonanthro.mixin.MobGoalAccessor) enderman)
                .enderdragonanthro$goals().getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal().getClass().getName().contains("DragonPresence"));
        if (afraid) {
            helper.fail("an enderman was given the fear goal");
        }
        helper.succeed();
    }

    /**
     * SHIPPED BUG: the shade's gate could only be raised over water.
     *
     * The clearance check tested the sill, which is meant to be buried, and
     * water is replaceable so only a lake ever passed. This asserts the frame
     * exists on solid ground, which is where it always should have worked.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void aGateRisesOnLand(GameTestHelper helper) {
        floor(helper, 14);
        BlockPos foot = helper.absolutePos(new BlockPos(3, 1, 3));
        NetherGate.raise(helper.getLevel(), foot, Direction.EAST, 5, 10, true);

        ServerLevel level = helper.getLevel();
        if (!level.getBlockState(foot.below()).is(Blocks.OBSIDIAN)) {
            helper.fail("no sill under the frame");
        }
        if (!level.getBlockState(foot.above(10)).is(Blocks.OBSIDIAN)) {
            helper.fail("no lintel over the frame");
        }
        helper.succeed();
    }

    /**
     * SHIPPED BUG: the gate came out 5x5 because the size was read from
     * getBbHeight, which is the CURRENT pose -- and ordering a gate is
     * something you do on the wing, where a player is 0.6 tall before scale.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void aGateIsSizedForAStandingDragon(GameTestHelper helper) {
        floor(helper, 6);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        dragon.setPose(Pose.FALL_FLYING);

        int tall = NetherGate.tallFor(dragon);
        if (tall < 9) {
            helper.fail("a gliding dragon asked for a " + tall
                    + "-block gate; it is eight blocks tall standing");
        }
        helper.succeed();
    }

    /** SHIPPED FEATURE: snow goes off as steam rather than surviving dragonfire. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void dragonfireTakesSnowOff(GameTestHelper helper) {
        floor(helper, 6);
        BlockPos snow = new BlockPos(2, 1, 2);
        helper.setBlock(snow, Blocks.SNOW_BLOCK);
        DragonFire.thawAround(helper.getLevel(), helper.absolutePos(snow).above());
        helper.assertBlockNotPresent(Blocks.SNOW_BLOCK, snow);
        helper.succeed();
    }

    /**
     * The Intent dial: three stops, and the claw follows it.
     *
     * The attack bonus is an attribute, so a dial that forgets to rewrite it
     * would leave the damage on whichever stop the form was dressed at.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void intentMovesTheClaw(GameTestHelper helper) {
        floor(helper, 6);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));

        double alert = dragon.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DragonIntent.cycle(dragon);                       // Alert -> Hostile
        DragonIntent.cycle(dragon);                       // -> Passive
        double passive = dragon.getAttributeValue(Attributes.ATTACK_DAMAGE);

        if (DragonIntent.of(dragon) != DragonIntent.PASSIVE) {
            helper.fail("two cycles from Alert should land on Passive, got "
                    + DragonIntent.of(dragon));
        }
        if (passive >= alert) {
            helper.fail("Passive hits for " + passive
                    + ", which is not less than Alert's " + alert);
        }
        helper.succeed();
    }

    /** The crater needs a bare hand, which is what keeps mining possible. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void craterNeedsABareHand(GameTestHelper helper) {
        floor(helper, 6);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));

        if (!DragonAbilities.cratersNow(dragon)) {
            helper.fail("a bare-handed dragon on Alert should crater");
        }
        dragon.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Items.DIAMOND_PICKAXE));
        if (DragonAbilities.cratersNow(dragon)) {
            helper.fail("a dragon holding a pickaxe should mine, not crater");
        }
        helper.succeed();
    }

    /**
     * SHIPPED BUG worth a test of its own: the divisor.
     *
     * damage/4 + 1 is the single number the whole difficulty rests on, and it
     * lives in a ModifyVariable on actuallyHurt -- an injection that would go
     * quiet without a word if the target ever changed shape.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void damageIsQuartered(GameTestHelper helper) {
        floor(helper, 6);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        float full = dragon.getHealth();

        boolean landed = dragon.hurt(helper.getLevel().damageSources().generic(), 40.0F);
        float taken = full - dragon.getHealth();
        // 40/4 + 1 = 11
        if (Math.abs(taken - 11.0F) > 0.51F) {
            helper.fail("40 damage should land as 11, landed as " + taken
                    + " [hurt returned " + landed
                    + ", invulnerable=" + dragon.isInvulnerable()
                    + ", abilities.invulnerable=" + dragon.getAbilities().invulnerable
                    + ", invulnerableTime=" + dragon.invulnerableTime
                    + ", health=" + dragon.getHealth() + "/" + dragon.getMaxHealth() + "]");
        }
        helper.succeed();
    }

    /** A dragon does not freeze. This one shipped broken and unregistered. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void aDragonDoesNotFreeze(GameTestHelper helper) {
        floor(helper, 6);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        if (dragon.canFreeze()) {
            helper.fail("a dragon that shrugs off lava should not be freezing");
        }
        float full = dragon.getHealth();
        dragon.hurt(helper.getLevel().damageSources().freeze(), 10.0F);
        if (dragon.getHealth() < full) {
            helper.fail("cold damage got through");
        }
        helper.succeed();
    }

    /** The form survives being asked twice, which is what a relog looks like. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void theFormIsIdempotent(GameTestHelper helper) {
        floor(helper, 6);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        double once = dragon.getAttributeValue(Attributes.MAX_HEALTH);
        DragonFormManager.applyForm(dragon);
        double twice = dragon.getAttributeValue(Attributes.MAX_HEALTH);
        if (Math.abs(once - twice) > 0.01) {
            helper.fail("dressing twice stacked the modifiers: " + once + " -> " + twice);
        }
        helper.succeed();
    }

    /**
     * SHIPPED BUG: the last few seconds of a long burn came out orange.
     *
     * Two clocks for one burn. The server lit the victim for BURN_TICKS and the
     * client counted the purple mark down itself, on END_CLIENT_TICK -- which
     * runs while the world is paused, so the mark expired early and the flame
     * carried on in vanilla orange. Reported on an iron golem, where the burn is
     * long enough to see it happen.
     *
     * The mark is now held until the fire goes out rather than counted, so the
     * only thing left that can be wrong is the burn itself: this pins what the
     * breath actually sets, because everything the client draws is now measured
     * against it.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void aBurnLastsAsLongAsItSays(GameTestHelper helper) {
        floor(helper, 6);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        // Facing the victim, since the stream only catches what is in the cone.
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 1));
        dragon.moveTo(dragon.getX(), dragon.getY(), dragon.getZ(), -90.0F, 0.0F);

        DragonFire.breathe(dragon);

        if (!victim.isOnFire()) {
            helper.fail("breathing on a zombie did not set it alight");
        }
        int lit = victim.getRemainingFireTicks();
        if (lit != DragonFire.BURN_TICKS) {
            helper.fail("the breath lit it for " + lit + " ticks, but the mark sent to"
                    + " every client says " + DragonFire.BURN_TICKS);
        }
        helper.succeed();
    }

    /**
     * SHIPPED BUG: a shade you walked away from vanished off the court screen.
     *
     * The roster was built by looking each shade up in the world the OWNER was
     * standing in, so stepping through a portal deleted the retinue from the
     * screen. The shade was alive on the far side and its slot was still spoken
     * for -- which is why the next summon produced the NEXT name rather than the
     * same one again -- but with no row there was no Recall button, and Recall
     * was the one thing that would have fixed it.
     *
     * A discarded entity reproduces it exactly: prune keeps a shade it cannot
     * find (not-found is not dead), so the court still holds it and the roster
     * has to still show it.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void aShadeYouLeftBehindKeepsItsRow(GameTestHelper helper) {
        floor(helper, 8);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        DragonMinions.summon(dragon);

        if (DragonMinions.roster(dragon).size() != 1) {
            helper.fail("a summoned shade should have a row: got "
                    + DragonMinions.roster(dragon).size());
        }
        // Out of this level's reach without being dead -- what a portal does.
        for (EnderMan shade : helper.getLevel().getEntitiesOfClass(EnderMan.class,
                dragon.getBoundingBox().inflate(32.0))) {
            shade.discard();
        }

        var rows = DragonMinions.roster(dragon);
        if (rows.size() != 1) {
            helper.fail("a shade out of reach lost its row entirely; the court still"
                    + " holds its slot, so there is nothing to press Recall on");
        }
        if (rows.get(0).where().isEmpty()) {
            helper.fail("the row does not say the shade is away, so it reads as"
                    + " standing here when it is not");
        }
        helper.succeed();
    }

    /** A floor to stand on; the empty structure is air all the way down. */
    private static void floor(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }
}
