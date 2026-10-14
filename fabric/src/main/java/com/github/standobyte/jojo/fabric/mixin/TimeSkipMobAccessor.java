package com.github.standobyte.jojo.fabric.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Original instant Time Stop clears the targeting task as well as its current victim. */
@Mixin(Mob.class)
public interface TimeSkipMobAccessor {
    @Accessor("targetSelector") GoalSelector jojo$targetSelector();
}
