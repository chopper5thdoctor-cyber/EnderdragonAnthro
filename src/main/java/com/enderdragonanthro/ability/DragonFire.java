package com.enderdragonanthro.ability;

import com.enderdragonanthro.block.ModBlocks;
import com.enderdragonanthro.network.DragonBurnPayload;
import com.enderdragonanthro.particle.ModParticles;
import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Held fire, out of the mouth.
 *
 * Not the Breath ability, which lobs a pool of acid at a place and leaves it
 * there. This is a stream: it goes where you are looking, for as long as you
 * hold it, and costs you to keep holding it.
 */
public final class DragonFire {
    /** How far the stream reaches — as far as you can pick a target, not arm's length. */
    private static final double REACH = 24.0;
    /**
     * How tight the cone is, as the cosine of its half-angle. 0.94 is about
     * twenty degrees — wide enough to be a stream rather than a laser, tight
     * enough that you cannot torch a room by looking down the middle of it.
     */
    private static final double CONE = 0.94;
    /**
     * How long the burn lasts after the stream stops, in ticks.
     *
     * Fifteen seconds, which is what lava leaves on you once you climb out.
     * Dragonfire has no business going out sooner than the ground does.
     */
    private static final int BURN_TICKS = 300;
    /**
     * Per shot. Small, because the burn is the weapon and this is the light.
     *
     * Doubled from 2 when the kit was scaled to boss weight. It is still the
     * smallest number in it, and deliberately: at four shots a second the
     * stream already lands 16 a second before the fifteen-second burn starts,
     * and it costs hunger the whole time it is held.
     */
    private static final float DAMAGE = 4.0F;
    /**
     * Hunger per shot. At the ability's five-tick cooldown that is four shots a
     * second, so 0.6 drains roughly half a haunch a second held — enough that
     * a flamethrower is something you spend rather than something you lean on.
     */
    private static final float EXHAUSTION = 0.6F;
    /** Half-width of the patch left where the stream lands: 2 gives a 5x5. */
    private static final int SPREAD = 2;

    private DragonFire() {
    }

    public static void breathe(ServerPlayer player) {
        if (!DragonFormManager.isDragon(player)) {
            return;
        }
        // Nothing to burn with. Vanilla lets you sprint to zero and then stops
        // you; the same rule reads better here than a silent no-op.
        if (player.getFoodData().getFoodLevel() <= 0 && !player.isCreative()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 mouth = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        for (double d = 1.0; d <= REACH; d += 1.0) {
            Vec3 at = mouth.add(look.scale(d));
            // The stream widens as it goes, the way a jet of anything does.
            double spread = 0.06 * d;
            level.sendParticles(ModParticles.DRAGON_FLAME, at.x, at.y, at.z,
                    8, spread, spread, spread, 0.01);
            // Smoke trails the flame rather than riding with it: thinner, and
            // only past the first few blocks, so the mouth stays bright and the
            // far end of the stream is what fouls the air.
            if (d > 4.0) {
                level.sendParticles(ModParticles.DRAGON_SMOKE, at.x, at.y, at.z,
                        3, spread, spread, spread, 0.04);
            }
        }

        Vec3 far = mouth.add(look.scale(REACH));
        AABB box = new AABB(mouth, far).inflate(1.5);
        // The court does not burn. They stand in front of you by design, so a
        // stream that cooked them would make Defend a liability.
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player && !DragonMinions.isOwnedBy(player, e));
        for (LivingEntity entity : caught) {
            Vec3 toward = entity.getBoundingBox().getCenter().subtract(mouth);
            if (toward.lengthSqr() > REACH * REACH || toward.normalize().dot(look) < CONE) {
                continue;
            }
            entity.igniteForTicks(BURN_TICKS);
            mark(level, entity, BURN_TICKS);
            entity.hurt(level.damageSources().onFire(), DAMAGE);
        }

        // What it leaves behind. The ray is traced separately from the cone so
        // fire lands where you are actually pointing rather than at the first
        // thing wide enough to be caught by it.
        BlockHitResult hit = level.clip(new ClipContext(mouth, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            // A five-by-five patch, not one block. A single block was why
            // breathing on a tree did nothing visible: the spot beside a trunk
            // has air under it, so the one candidate failed and that was that.
            // Twenty-five candidates find a floor even when the middle does not.
            BlockPos centre = hit.getBlockPos().relative(hit.getDirection());
            boolean steamed = false;
            for (int dx = -SPREAD; dx <= SPREAD; dx++) {
                for (int dz = -SPREAD; dz <= SPREAD; dz++) {
                    BlockPos column = centre.offset(dx, 0, dz);
                    // Before the flame, not instead of it. A single snow layer
                    // has no collision shape, so the ray goes straight through
                    // one and lands on the ground underneath -- and snow is
                    // replaceable, so fire would quietly overwrite it and the
                    // snowfield would just vanish a block at a time with nothing
                    // to show for it. Both the spot and the one below are asked,
                    // which is the same pair light() tries and for the same
                    // reason.
                    steamed |= thaw(level, column) | thaw(level, column.below());
                    light(level, column);
                }
            }
            if (steamed) {
                // One hiss for the patch. Twenty-five of them at once is not
                // louder, it is a buzz.
                level.playSound(null, centre, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 0.7F, 1.4F);
            }
        }

        if (!player.isCreative()) {
            player.causeFoodExhaustion(EXHAUSTION);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.7F, 0.6F);
    }

    /**
     * Snow does not survive being breathed on.
     *
     * Dragonfire sets stone alight and leaves a burn that outlasts lava's; a
     * snowdrift standing in the middle of that was the one thing on screen
     * arguing it was not hot. It goes off as steam rather than melting to water,
     * because water is what a slow thaw leaves and this is not one.
     *
     * All three snows, since they are the same substance wearing different
     * blocks: the layer you walk over, the block you build with, and the powder
     * that hides a pit.
     *
     * @return whether there was anything there to lose
     */
    private static boolean thaw(ServerLevel level, BlockPos at) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
        if (!state.is(net.minecraft.world.level.block.Blocks.SNOW)
                && !state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                && !state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
            return false;
        }
        level.removeBlock(at, false);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                STEAM, 0.25, 0.25, 0.25, 0.02);
        return true;
    }

    /** Puffs per block of snow lost. Enough to read as a cloud, not a fog bank. */
    private static final int STEAM = 6;

    /**
     * The same heat, standing still.
     *
     * A patch of dragonfire left burning in a snowfield should clear the ground
     * around it rather than sitting in a hole the breath happened to make, so
     * the block asks this of its neighbours as it ticks.
     */
    public static boolean thawAround(ServerLevel level, BlockPos pos) {
        boolean any = false;
        for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
            any |= thaw(level, pos.relative(face));
        }
        return any;
    }

    /**
     * One block of it, if there is anywhere to put it.
     *
     * Where fire will hold is the block's business, not the ability's. Asking
     * it directly is not tidiness: this had its own idea of a valid spot,
     * stricter than the block's, so the ability would decline to place flames
     * the block would happily have kept — in a canopy, every time.
     *
     * The spot itself and one below, so a patch laid across uneven ground
     * follows the ground rather than stopping at the first step.
     */
    private static void light(ServerLevel level, BlockPos at) {
        net.minecraft.world.level.block.state.BlockState flame =
                ModBlocks.DRAGON_FIRE.defaultBlockState();
        for (BlockPos pos : new BlockPos[] {at, at.below()}) {
            if (level.getBlockState(pos).canBeReplaced() && flame.canSurvive(level, pos)) {
                // shaped(), not the default: a flame with no floor lies flat
                // against whatever is burning beside it rather than standing up
                // in the middle of the air.
                level.setBlockAndUpdate(pos, ModBlocks.DRAGON_FIRE.shaped(level, pos));
                return;
            }
        }
    }

    /**
     * Tell everyone watching that this burn is ours.
     *
     * To viewers rather than to the victim: the flames on a burning entity are
     * drawn by whoever is looking at it, and the victim may well be a mob that
     * is not looking at anything.
     */
    public static void mark(ServerLevel level, LivingEntity victim, int ticks) {
        DragonBurnPayload payload = new DragonBurnPayload(victim.getId(), ticks);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(victim) < 128.0 * 128.0) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, payload);
            }
        }
    }
}
