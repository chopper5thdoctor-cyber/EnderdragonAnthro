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
    /** How far the stream reaches. */
    private static final double REACH = 9.0;
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
    /** Per shot. Small, because the burn is the weapon and this is the light. */
    private static final float DAMAGE = 2.0F;
    /**
     * Hunger per shot. At the ability's five-tick cooldown that is four shots a
     * second, so 0.6 drains roughly half a haunch a second held — enough that
     * a flamethrower is something you spend rather than something you lean on.
     */
    private static final float EXHAUSTION = 0.6F;

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
                    2, spread, spread, spread, 0.01);
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
            BlockPos at = hit.getBlockPos().relative(hit.getDirection());
            if (level.getBlockState(at).canBeReplaced()
                    && !level.getBlockState(at.below()).isAir()) {
                level.setBlockAndUpdate(at, ModBlocks.DRAGON_FIRE.defaultBlockState());
            }
        }

        if (!player.isCreative()) {
            player.causeFoodExhaustion(EXHAUSTION);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.7F, 0.6F);
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
