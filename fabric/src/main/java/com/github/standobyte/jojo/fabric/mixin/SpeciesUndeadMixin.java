package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.SpeciesHooks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class SpeciesUndeadMixin {
    @Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
    private void jojo$undeadPotions(CallbackInfoReturnable<Boolean> result) {
        if (SpeciesHooks.undeadPlayer((LivingEntity) (Object) this)) result.setReturnValue(true);
    }
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void jojo$undeadEffects(net.minecraft.world.effect.MobEffectInstance effect, CallbackInfoReturnable<Boolean> result) {
        var self = (LivingEntity) (Object) this;
        if (SpeciesHooks.undeadPlayer(self) && (effect.is(net.minecraft.world.effect.MobEffects.POISON)
                || !SpeciesHooks.vampire(self) && effect.is(net.minecraft.world.effect.MobEffects.REGENERATION))) result.setReturnValue(false);
    }
}
