package com.enderdragonanthro.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mob.goalSelector is protected, and fear has to be a goal like any other.
 */
@Mixin(Mob.class)
public interface MobGoalAccessor {
    @Accessor("goalSelector")
    GoalSelector enderdragonanthro$goals();
}
