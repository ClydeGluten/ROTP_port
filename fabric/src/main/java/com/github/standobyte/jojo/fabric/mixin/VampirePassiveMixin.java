package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.VampireService;
import com.github.standobyte.jojo.fabric.VampireCombatService;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge PotionRemoveEvent adaptation: current hidden passives survive milk and effect clearing. */
@Mixin(LivingEntity.class)
abstract class VampirePassiveMixin {
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void jojo$vampireHungerImmunity(net.minecraft.world.effect.MobEffectInstance effect, CallbackInfoReturnable<Boolean> result) {
        var self = (LivingEntity) (Object) this;
        // Original GameplayEventHandler/IApplicableEffect applies to every effect source,
        // including commands and other powers, rather than only the vampire attack path.
        if ((effect.is(VampireCombatService.STUN) || effect.is(com.github.standobyte.jojo.fabric.HamonEffects.SHOCK))
                && (self instanceof net.minecraft.world.entity.player.Player player && player.isCreative()
                || self instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi())) {
            result.setReturnValue(false);
            return;
        }
        if(effect.is(com.github.standobyte.jojo.fabric.HamonEffects.IMMOBILIZE) && self instanceof net.minecraft.world.entity.player.Player player && player.isCreative()) {result.setReturnValue(false);return;}
        if(effect.is(com.github.standobyte.jojo.fabric.VampireHamon.SPREAD) && !com.github.standobyte.jojo.fabric.VampireHamon.affected(self)) {result.setReturnValue(false);return;}
        if (effect.is(VampireCombatService.FREEZE) && VampireCombatService.immuneToCold(self)
                && !(self instanceof net.minecraft.world.entity.player.Player player && VampireService.progress(player).vampire())) {
            result.setReturnValue(false);
            return;
        }
        if ((Object) this instanceof net.minecraft.world.entity.player.Player player && (VampireService.progress(player).vampire() || com.github.standobyte.jojo.fabric.PillarmanService.hasPillarman(player))) {
            if (effect.is(net.minecraft.world.effect.MobEffects.HUNGER)) result.setReturnValue(false);
            else if (effect.is(net.minecraft.world.effect.MobEffects.REGENERATION)) result.setReturnValue(true);
        }
    }
    @Inject(method="onEffectsRemoved",at=@At("TAIL"))
    private void jojo$restoreStunAi(java.util.Collection<net.minecraft.world.effect.MobEffectInstance> removed,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(removed.stream().anyMatch(effect->effect.is(VampireCombatService.STUN)||effect.is(com.github.standobyte.jojo.fabric.HamonEffects.SHOCK)))VampireCombatService.restoreStunAi((LivingEntity)(Object)this);
    }
    @Inject(method = "removeEffect", at = @At("HEAD"), cancellable = true)
    private void jojo$protectPassive(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> result) {
        if (com.github.standobyte.jojo.fabric.SpeciesHooks.protectsPassive((LivingEntity) (Object) this, effect)) result.setReturnValue(false);
    }
    @Inject(method = "removeAllEffects", at = @At("HEAD"), cancellable = true)
    private void jojo$keepPassiveEffects(CallbackInfoReturnable<Boolean> result) {
        var self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || self.getActiveEffects().stream().noneMatch(effect -> com.github.standobyte.jojo.fabric.SpeciesHooks.protectsPassive(self, effect.getEffect()))) return;
        boolean removed = false;
        for (var effect : java.util.List.copyOf(self.getActiveEffects())) {
            if (!com.github.standobyte.jojo.fabric.SpeciesHooks.protectsPassive(self, effect.getEffect())) removed |= self.removeEffect(effect.getEffect());
        }
        result.setReturnValue(removed);
    }
}
