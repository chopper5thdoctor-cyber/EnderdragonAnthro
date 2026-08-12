package com.enderdragonanthro.item;

import com.enderdragonanthro.ability.HomingCrystals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Places the anchor. Unlike a real end crystal it needs no bedrock beneath it. */
public class HomingCrystalItem extends Item {
    public HomingCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return InteractionResult.FAIL;
        }
        if (!HomingCrystals.place(serverLevel, player, pos)) {
            return InteractionResult.FAIL;
        }
        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
