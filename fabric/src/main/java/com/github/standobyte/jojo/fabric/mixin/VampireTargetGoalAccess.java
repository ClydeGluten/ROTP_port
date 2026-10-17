package com.github.standobyte.jojo.fabric.mixin;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The original edits nearest-target player predicates without suppressing retaliation goals. */
@Mixin(NearestAttackableTargetGoal.class)
public interface VampireTargetGoalAccess {
    @Accessor("targetType") Class<?> jojo$targetType();
    @Accessor("targetConditions") TargetingConditions jojo$targetConditions();
}
