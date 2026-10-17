package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.VampireBlood;
import com.github.standobyte.jojo.fabric.VampireHamon;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class VampireBleedingMixin {
    @Inject(method="canBeAffected", at=@At("HEAD"), cancellable=true)
    private void jojo$bleedingEligibility(MobEffectInstance effect, CallbackInfoReturnable<Boolean> result) {
        var self=(LivingEntity)(Object)this;
        if (effect.is(VampireBlood.BLEEDING) && (!VampireBlood.canBleed(self) || VampireBlood.limitAmplifier(self,effect.getAmplifier())<0)) result.setReturnValue(false);
        if (effect.is(VampireHamon.SPREAD) && !VampireHamon.affected(self)) result.setReturnValue(false);
    }
    @ModifyVariable(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at=@At("HEAD"), argsOnly=true)
    private MobEffectInstance jojo$boundBloodLoss(MobEffectInstance effect) {
        if (!effect.is(VampireBlood.BLEEDING)) return effect;
        int level=VampireBlood.limitAmplifier((LivingEntity)(Object)this,effect.getAmplifier());
        return level>=0 && level!=effect.getAmplifier() ? new MobEffectInstance(effect.getEffect(),effect.getDuration(),level,effect.isAmbient(),effect.isVisible(),effect.showIcon()) : effect;
    }
}
