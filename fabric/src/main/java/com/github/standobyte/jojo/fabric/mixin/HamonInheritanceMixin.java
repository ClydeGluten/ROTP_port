package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.HamonInheritance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after death cancellation has resolved, before held equipment enters vanilla death drops. */
@Mixin(LivingEntity.class)
abstract class HamonInheritanceMixin {
    @Inject(method="handleKillingBlow",at=@At("HEAD"))
    private void jojo$inheritBeforeDrops(CallbackInfo ci) {HamonInheritance.onDeath((LivingEntity)(Object)this);}
}
