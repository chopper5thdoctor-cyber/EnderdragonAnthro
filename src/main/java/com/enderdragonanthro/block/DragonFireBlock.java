package com.enderdragonanthro.block;

import com.mojang.serialization.MapCodec;
import com.enderdragonanthro.ability.DragonFire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A third fire, after the orange one and the blue one.
 *
 * Modelled on soul fire rather than on fire: fire is the one that spreads,
 * carrying a dozen state properties for which neighbours are alight and a whole
 * scheduled-tick machine to consume the world. Soul fire just burns where it
 * is put. Dragonfire is a thing a dragon leaves behind, not a wildfire, so it
 * is the quiet one of the two — it stays where it lands and goes out when the
 * block under it does.
 *
 * It burns hotter than either: 3 a tick against fire's 1 and soul fire's 2.
 * Neither of those was breathed by anything.
 */
public class DragonFireBlock extends BaseFireBlock {
    public static final MapCodec<DragonFireBlock> CODEC = simpleCodec(DragonFireBlock::new);

    /**
     * Per tick, standing in it. Fire does 1 and soul fire 2; this does 3,
     * because it is not a hazard the world grew, it is a dragon's.
     */
    private static final float FIRE_DAMAGE = 3.0F;

    /** How long standing in it keeps the burn marked as ours. */
    private static final int MARK_TICKS = 300;
    /** Chance per random tick that a flammable neighbour catches. */
    private static final float CATCH_CHANCE = 0.6F;
    /** ...and that the block it caught from is consumed outright. */
    private static final float CONSUME_CHANCE = 0.35F;

    public DragonFireBlock(Properties properties) {
        super(properties, FIRE_DAMAGE);
    }

    @Override
    public MapCodec<? extends BaseFireBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return anchored(level, pos);
    }

    /**
     * Any solid face will hold it, not just a floor.
     *
     * This is why the tree would not light. The ability was already willing to
     * put fire against a trunk — but canSurvive still demanded a block
     * underneath, so the moment the placement fired a neighbour update the fire
     * failed its own survival check and popped, in the same tick, invisibly.
     *
     * Flint and steel worked because vanilla fire has that same floor rule and
     * was being placed on the GROUND beside the tree, where there is a floor;
     * it then spread upward on its own. Ours was being asked to start halfway
     * up a trunk, which vanilla fire never is.
     *
     * Soul fire is pickier still — soul sand or soul soil only, which is what
     * makes it a Nether feature rather than a fire. Dragonfire is breathed onto
     * whatever happened to be there.
     */
    private static boolean anchored(LevelReader level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockPos side = pos.relative(face);
            if (level.getBlockState(side).isFaceSturdy(level, side, face.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return true;
    }

    /**
     * Spread to anything that will burn, and eat it.
     *
     * Random tick rather than a scheduled one, which is the cheap way to get
     * fire that creeps rather than fire that explodes across a forest in a
     * second. Only blocks the game already marks flammable are touched, so
     * stone and dirt are safe and a tree is not.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        for (Direction face : Direction.values()) {
            BlockPos next = pos.relative(face);
            BlockState neighbour = level.getBlockState(next);
            if (!neighbour.ignitedByLava() && !neighbour.is(BlockTags.LOGS)
                    && !neighbour.is(BlockTags.LEAVES)) {
                continue;
            }
            if (random.nextFloat() < CONSUME_CHANCE) {
                level.setBlockAndUpdate(next, state);
            } else if (random.nextFloat() < CATCH_CHANCE) {
                BlockPos open = next.relative(face);
                if (level.getBlockState(open).canBeReplaced()) {
                    level.setBlockAndUpdate(open, state);
                }
            }
        }
    }

    /**
     * Standing in it counts as being lit by it.
     *
     * BaseFireBlock already does the damage; this only says whose fire it was,
     * so the flames and the screen go purple for as long as the burn lasts
     * rather than only while your feet are in the block.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level instanceof ServerLevel server && entity instanceof LivingEntity victim
                && !entity.fireImmune()) {
            // A fixed span, not getRemainingFireTicks(). BaseFireBlock adds a
            // tick at a time, so reading it back here marked the burn as ours
            // for one tick and the screen went orange again immediately.
            DragonFire.mark(server, victim, MARK_TICKS);
        }
    }
}
