package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.HamonInheritance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ServerPlayer overrides die without calling LivingEntity.handleKillingBlow. */
@Mixin(ServerPlayer.class)
abstract class HamonPlayerInheritanceMixin {
    @Inject(method="die",at=@At("HEAD"))
    private void jojo$inheritBeforePlayerDrops(DamageSource source,CallbackInfo ci) {HamonInheritance.onDeath((ServerPlayer)(Object)this);}
}
