package com.github.standobyte.jojo.fabric.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractArrow.class)
public interface KnifeArrowAccess {
    @Accessor("baseDamage") double jojo$getBaseDamage();
}
