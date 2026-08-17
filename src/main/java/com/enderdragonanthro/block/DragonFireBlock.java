package com.enderdragonanthro.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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

    public DragonFireBlock(Properties properties) {
        super(properties, FIRE_DAMAGE);
    }

    @Override
    public MapCodec<? extends BaseFireBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSurviveOnBlock(level.getBlockState(pos.below()));
    }

    /**
     * Anything solid enough to stand on holds it.
     *
     * Soul fire is pickier — it wants soul sand or soul soil, which is what
     * makes it a Nether feature rather than a fire. Dragonfire is something
     * breathed onto whatever happened to be there, so the only question is
     * whether there is a floor.
     */
    private static boolean canSurviveOnBlock(BlockState below) {
        return !below.isAir();
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return true;
    }
}
