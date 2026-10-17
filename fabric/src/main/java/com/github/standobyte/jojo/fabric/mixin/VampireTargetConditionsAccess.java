package com.github.standobyte.jojo.fabric.mixin;

import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TargetingConditions.class)
public interface VampireTargetConditionsAccess {
    @Accessor("selector") TargetingConditions.Selector jojo$selector();
}
