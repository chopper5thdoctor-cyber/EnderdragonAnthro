package com.enderdragonanthro.gametest;

import com.enderdragonanthro.DragonAnatomy;
import com.enderdragonanthro.DragonRig;
import com.enderdragonanthro.ability.AbilityAction;
import com.enderdragonanthro.ability.CrystalHealing;
import com.enderdragonanthro.ability.DragonAbilities;
import com.enderdragonanthro.ability.DragonFire;
import com.enderdragonanthro.ability.DragonIntent;
import com.enderdragonanthro.ability.DragonMinions;
import com.enderdragonanthro.ability.DragonPresence;
import com.enderdragonanthro.ability.HomingCrystals;
import com.enderdragonanthro.ability.ShadeIdentity;
import com.enderdragonanthro.ability.NetherGate;
import com.enderdragonanthro.transform.DragonFormManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        // Cycled to rather than assumed. These used to be written as "two
        // cycles from Alert lands on Passive", which was true of the default at
        // the time and quietly false the moment the default moved -- a test
        // failing because a tuning decision changed teaches nobody anything.
        cycleTo(helper, dragon, DragonIntent.PASSIVE);
        double passive = dragon.getAttributeValue(Attributes.ATTACK_DAMAGE);
        cycleTo(helper, dragon, DragonIntent.ALERT);
        double alert = dragon.getAttributeValue(Attributes.ATTACK_DAMAGE);

        if (passive >= alert) {
            helper.fail("Passive hits for " + passive
                    + ", which is not less than Alert's " + alert);
        }
        helper.succeed();
    }

    /** A dragon starts on the gentlest stop; nothing breaks that you did not mean to. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void aDragonWakesUpPassive(GameTestHelper helper) {
        floor(helper, 6);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        if (DragonIntent.of(dragon).craters()) {
            helper.fail("a freshly transformed dragon is on " + DragonIntent.of(dragon)
                    + ", which takes the ground out with a bare-handed left click");
        }
        helper.succeed();
    }

    /** The crater needs a bare hand, which is what keeps mining possible. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void craterNeedsABareHand(GameTestHelper helper) {
        floor(helper, 6);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        cycleTo(helper, dragon, DragonIntent.ALERT);

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

    /**
     * A shade says her own name in her own colour.
     *
     * The four of them are about to dress differently, and the name colour is
     * what ties a line of chat to the person who said it. It was RED/BLUE/
     * GREEN/GOLD -- near the accents on the cloth and not equal to them, which
     * is the kind of wrong nobody ever reports. verify_model.py pins the two
     * palettes to each other; this pins the message actually carrying one.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void aShadeSpeaksInHerOwnColour(GameTestHelper helper) {
        floor(helper, 8);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        DragonMinions.summon(dragon);

        Component said = dragon.toldRich().stream()
                .filter(c -> c.getString().startsWith(ShadeIdentity.NAMES[0]))
                .reduce((first, last) -> last).orElse(null);
        if (said == null) {
            helper.fail("summoning said nothing that began with "
                    + ShadeIdentity.NAMES[0] + "; told " + dragon.told());
        }
        // The name is the first run of the line, styled on its own.
        Component name = said.getSiblings().isEmpty() ? said : said;
        TextColor colour = name.getStyle().getColor();
        if (colour == null || colour.getValue() != ShadeIdentity.TINT_RGB[0]) {
            helper.fail("her name is drawn in " + colour + ", not #"
                    + String.format("%06X", ShadeIdentity.TINT_RGB[0])
                    + " which is what she is painted");
        }
        helper.succeed();
    }

    /**
     * She is standing on something at every block of a gate she raises.
     *
     * REPORTED: "the ender shade looked like she had creative flight ... she
     * should look like she's actually building like a normal player." She was
     * hovering. The build switched gravity off and put her BESIDE each block,
     * which for a twelve-block frame is a shade floating in open air placing
     * obsidian into the sky — the exact silhouette of creative mode.
     *
     * The fix is an order she could really build in, so the check is on the
     * order: walk it against a flat world and measure what is under her feet at
     * the instant she arrives — before the block she came to place.
     *
     * That "before" is the whole check, and the first version of this test did
     * not have it. Letting the new block count as its own support makes a stand
     * of {@code block.above()} footing by definition, and the test passed
     * against the broken order it was written to catch. It is only a check if
     * it asks what was already there.
     *
     * One block of air is allowed: that is a jump-place, the block lands under
     * you and you land on it. Two or more is standing on nothing.
     *
     * Run over frame sizes rather than one, because the gate is cut for
     * whatever has to walk through it, and the worst case scales with the
     * height — a twelve-tall frame put her thirteen blocks up.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void aGateIsBuiltFromSomethingToStandOn(GameTestHelper helper) {
        final int hop = 1;
        for (int[] size : new int[][] {{1, 2}, {2, 3}, {3, 5}, {4, 8}, {5, 12}, {7, 21}}) {
            int inner = size[0];
            int tall = size[1];
            // The sill sits ON the ground now rather than replacing the top
            // layer of it, so solid ground starts two below foot and the sill
            // course itself is air until she lays it.
            BlockPos foot = new BlockPos(0, 2, 0);
            Set<BlockPos> solid = new HashSet<>();
            List<DragonMinions.Lay> plan =
                    DragonMinions.planFor(foot, Direction.EAST, inner, tall);

            Set<BlockPos> laid = new HashSet<>();
            for (DragonMinions.Lay lay : plan) {
                if (!laid.add(lay.block())) {
                    helper.fail("the plan lays " + lay.block() + " twice");
                }
                BlockPos probe = lay.stand().below();
                int gap = 0;
                while (probe.getY() >= foot.getY() - 1 && !solid.contains(probe)) {
                    gap++;
                    probe = probe.below();
                }
                if (gap > hop) {
                    helper.fail("building a " + inner + "x" + tall + " gate, she stands at "
                            + lay.stand() + " to place " + lay.block() + " with " + gap
                            + " blocks of nothing under her — that is the hover that"
                            + " read as creative flight");
                }
                solid.add(lay.block());
            }

            // And it is still the whole frame: perimeter, once each.
            Set<BlockPos> want = new HashSet<>();
            for (int h = -1; h <= tall; h++) {
                for (int w = -1; w <= inner; w++) {
                    if (w == -1 || w == inner || h == -1 || h == tall) {
                        want.add(foot.relative(Direction.EAST, w).above(h));
                    }
                }
            }
            if (!laid.equals(want)) {
                helper.fail("a " + inner + "x" + tall + " gate came out as " + laid.size()
                        + " blocks, not the " + want.size() + " the frame is");
            }
        }
        helper.succeed();
    }

    /**
     * Walking away from an anchor does not destroy it.
     *
     * REPORTED: "homing beacon breaks and returns to hand if too far from it."
     * It did, and it was the court's oldest bug wearing a different hat. The
     * once-a-second sweep asked player.serverLevel().getEntity(anchor) and read
     * null as "shattered" — but null is also what an entity in a closed chunk
     * answers, and an anchor is a thing you deliberately walk away from. So
     * walking out of render distance destroyed it and handed you a replacement.
     * Stepping through a portal did the same, because the lookup was against
     * whichever level you happened to be standing in.
     *
     * Two of these three assertions fail against that code:
     *
     *  - the dimension is now recorded. It was a UUID field written as null on
     *    every path, so "which world is my anchor in" had no answer at all,
     *    which is the whole reason going home across a portal could not work.
     *  - the anchor survives a save and reload, because it lives in the world's
     *    data folder now rather than in a static map.
     *
     * The third — that the sweep leaves a standing anchor alone — passes either
     * way here, because a gametest cannot close a chunk. It is asserted anyway:
     * it is the behaviour that was reported, and a future change that starts
     * handing out spare crystals should trip on something.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void anAnchorIsNotLostByWalkingAway(GameTestHelper helper) {
        floor(helper, 8);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));

        if (!HomingCrystals.place(helper.getLevel(), dragon, at)) {
            helper.fail("the anchor would not go down, so there is nothing to test");
        }
        if (HomingCrystals.anchorDimension(dragon) == null) {
            helper.fail("the anchor recorded no dimension, so nothing can find it"
                    + " once its chunk closes or you cross a portal");
        }
        if (!HomingCrystals.anchorDimension(dragon).equals(helper.getLevel().dimension())) {
            helper.fail("the anchor thinks it is in "
                    + HomingCrystals.anchorDimension(dragon).location() + ", not "
                    + helper.getLevel().dimension().location());
        }

        // Shut the world and open it again.
        HomingCrystals.save(server);
        HomingCrystals.forgetWorld();
        if (HomingCrystals.hasAnchor(dragon)) {
            helper.fail("forgetWorld left an anchor behind; it would follow you"
                    + " into the next save");
        }
        HomingCrystals.load(server);
        if (!HomingCrystals.hasAnchor(dragon)) {
            helper.fail("the anchor did not survive a reload -- logging out would"
                    + " lose the way home");
        }

        // And the sweep leaves it standing.
        for (int tick = 0; tick < 3; tick++) {
            HomingCrystals.tick(server);
        }
        if (!HomingCrystals.hasAnchor(dragon)) {
            helper.fail("the sweep struck the anchor off while it was standing"
                    + " right there");
        }
        helper.succeed();
    }

    /**
     * Closing a world empties the court, and opening one fills it from the save.
     *
     * The reported bug: play one save, summon, quit to the title screen, open a
     * different save, summon again -- and the second world hands you Keshanne,
     * because slot 0 is still spoken for by a Vaelle standing in the first one.
     * Singleplayer runs the server inside the client, so the static map that
     * holds the court outlived the world that filled it.
     *
     * Both halves are asserted here, because either alone is a different bug.
     * Only clearing would lose your court on every relog and let the next summon
     * put a second Vaelle beside the first. Only saving would fix nothing.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void aCourtBelongsToOneSave(GameTestHelper helper) {
        floor(helper, 8);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(1, 1, 1));
        DragonMinions.summon(dragon);
        if (DragonMinions.roster(dragon).size() != 1) {
            helper.fail("nobody was summoned, so there is nothing to carry across");
        }

        // Shutting down: write the roll into this world, then let go of it.
        MinecraftServer server = helper.getLevel().getServer();
        DragonMinions.save(server);
        DragonMinions.forget();
        if (!DragonMinions.roster(dragon).isEmpty()) {
            helper.fail("the court survived the world closing; open another save"
                    + " and slot 0 is still taken by a shade who is not in it");
        }

        // Opening it again: the same world, so the same court.
        DragonMinions.load(server);
        var back = DragonMinions.roster(dragon);
        if (back.size() != 1) {
            helper.fail("the court did not come back with the save: " + back.size()
                    + " row(s). Summoning now would put a second one of her in"
                    + " the world beside the first");
        }
        if (back.get(0).slot() != 0) {
            helper.fail("she came back in slot " + back.get(0).slot()
                    + " rather than the one she was summoned into");
        }
        helper.succeed();
    }

    /**
     * SHIPPED BUG: one crystal, two dragons, one beam.
     *
     * A vanilla End Crystal carries exactly ONE beam target, because the fight
     * it was written for has one dragon. Both dragons were healed -- the
     * healing is worked out per dragon -- and both wrote that single field, so
     * the last one won and only one beam was ever drawn. Reported as "end
     * crystal heals both of us but no second beam".
     *
     * Asserted on the pairing the client is actually sent, so this is what will
     * be drawn rather than a second account of it. Both dragons have to appear
     * against the same crystal; the old behaviour can only ever name one,
     * whichever happened to be processed last.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void aCrystalBeamsAtEveryDragonItFeeds(GameTestHelper helper) {
        floor(helper, 12);
        FakeDragon first = FakeDragon.transformed(helper, new BlockPos(2, 1, 2));
        FakeDragon second = FakeDragon.transformed(helper, new BlockPos(8, 1, 8));
        EndCrystal crystal = helper.spawn(EntityType.END_CRYSTAL, new BlockPos(5, 1, 5));

        CrystalHealing.tickFor(helper.getLevel().getServer(), List.of(first, second));

        List<Integer> pairs = CrystalHealing.beamPairs();
        Set<Integer> fed = new HashSet<>();
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            if (pairs.get(i) == crystal.getId()) {
                fed.add(pairs.get(i + 1));
            }
        }
        if (!fed.contains(first.getId()) || !fed.contains(second.getId())) {
            helper.fail("the crystal feeds two dragons and beams at " + fed.size()
                    + ": pairs=" + pairs + ", wanted " + first.getId()
                    + " and " + second.getId());
        }
        // ...and vanilla's own single target still has to be set, or
        // EndCrystalRenderer.shouldRender culls the crystal and takes every
        // beam with it.
        if (crystal.getBeamTarget() == null) {
            helper.fail("no vanilla beam target: the crystal will be culled"
                    + " the moment its own box leaves the view");
        }
        helper.succeed();
    }

    /**
     * ...and the beam lands on the chest rather than halfway up the body.
     *
     * Half the hitbox height is a number that refers to nothing about the
     * model, and on a body eight blocks tall it is the pelvis. The heart is
     * measured off the cube named "chest" at conversion time, so this asserts
     * the aim is above the middle and inside the body rather than a constant,
     * which would just be the same number typed twice.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void theBeamLandsOnTheHeart(GameTestHelper helper) {
        floor(helper, 12);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(2, 1, 2));
        double height = dragon.getBbHeight();
        double heart = DragonAnatomy.heartHeight(dragon);
        if (heart <= height * 0.5) {
            helper.fail(String.format(
                    "the beam aims at %.2f of %.2f blocks -- the pelvis, not the chest",
                    heart, height));
        }
        if (heart >= height) {
            helper.fail(String.format(
                    "the beam aims at %.2f on a %.2f block dragon, which is off the top of it",
                    heart, height));
        }
        helper.succeed();
    }

    /**
     * The wing sweep lands when the wings do, not when the key goes down.
     *
     * Buffet used to hurt everything around it on the press, most of a wingbeat
     * before the wings moved -- and for a while before that, before they moved
     * at all. Two assertions, because "it hits later" and "it still hits" are
     * different ways to get this wrong and only one of them is visible in play.
     *
     * The impact tick is DragonRig.BEAT_IMPACT, measured off the flap clip at
     * conversion time rather than named here: retime the animation and the test
     * follows it, which is the only way it can stay true.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void theBuffetLandsWithTheWings(GameTestHelper helper) {
        floor(helper, 12);
        FakeDragon dragon = FakeDragon.transformed(helper, new BlockPos(2, 1, 2));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 2));
        float full = zombie.getHealth();

        DragonAbilities.trigger(dragon, AbilityAction.BUFFET);
        if (zombie.getHealth() < full) {
            helper.fail("the buffet landed on the keypress, "
                    + DragonRig.BEAT_IMPACT + " ticks before the wings reach the air");
        }
        // One tick short of the stroke: still nothing.
        for (int tick = 0; tick < DragonRig.BEAT_IMPACT; tick++) {
            DragonAbilities.landBuffets(List.of(dragon));
        }
        if (zombie.getHealth() < full) {
            helper.fail("the buffet landed early, before tick " + DragonRig.BEAT_IMPACT);
        }
        DragonAbilities.landBuffets(List.of(dragon));
        if (zombie.getHealth() >= full) {
            helper.fail("the buffet never landed: the zombie is still on "
                    + zombie.getHealth() + " a full stroke later");
        }
        helper.succeed();
    }

    /**
     * ...and the cooldown pays the delay back rather than adding it on.
     *
     * A blow that arrives half a second late and is gated for the same five
     * seconds afterwards is strictly worse than the one it replaced. This is
     * the arithmetic, asserted, so a later edit to either number has to move
     * the other.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void theBuffetCooldownRepaysTheWait(GameTestHelper helper) {
        if (AbilityAction.BUFFET.cooldownTicks + DragonRig.BEAT_IMPACT != 100) {
            helper.fail("press to press is " + (AbilityAction.BUFFET.cooldownTicks
                    + DragonRig.BEAT_IMPACT) + " ticks, not the 100 it was");
        }
        helper.succeed();
    }

    /**
     * Turn the dial until it reads what the test needs, whatever it started on.
     *
     * Bounded by the number of stops, so a cycle that stopped cycling fails here
     * instead of hanging the test out to its timeout with nothing to say.
     */
    private static void cycleTo(GameTestHelper helper, FakeDragon dragon, DragonIntent want) {
        for (int turn = 0; turn < DragonIntent.values().length; turn++) {
            if (DragonIntent.of(dragon) == want) {
                return;
            }
            DragonIntent.cycle(dragon);
        }
        helper.fail("the dial never reached " + want + "; it is stuck on "
                + DragonIntent.of(dragon));
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
