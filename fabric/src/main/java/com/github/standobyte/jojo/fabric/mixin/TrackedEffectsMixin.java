package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.TrackedEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** Modern effect callbacks cover application, refresh, explicit removal and natural expiry. */
@Mixin(LivingEntity.class)
abstract class TrackedEffectsMixin {
    @Inject(method = "onEffectAdded", at = @At("TAIL"))
    private void jojo$trackAdded(MobEffectInstance effect, Entity source, CallbackInfo ci) {
        TrackedEffects.updated((LivingEntity) (Object) this, effect);
    }
    @Inject(method = "onEffectUpdated", at = @At("TAIL"))
    private void jojo$trackUpdated(MobEffectInstance effect, boolean refreshAttributes, Entity source, CallbackInfo ci) {
        TrackedEffects.updated((LivingEntity) (Object) this, effect);
    }
    @Inject(method = "onEffectsRemoved", at = @At("TAIL"))
    private void jojo$trackRemoved(Collection<MobEffectInstance> effects, CallbackInfo ci) {
        for (var effect : effects) TrackedEffects.removed((LivingEntity) (Object) this, effect);
    }
}
