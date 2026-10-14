package com.github.standobyte.jojo.fabric.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Preserve vanilla's previous damage amount while applying an independently timed Stand hit. */
@Mixin(LivingEntity.class)
public interface StandDamageAccess {
    @Accessor("lastHurt") float jojo$getLastHurt();
    @Accessor("lastHurt") void jojo$setLastHurt(float damage);
}
