package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.ProgressionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player overrides this method; its corresponding accepted-hit hook lives in PlayerGuardMixin. */
@Mixin(LivingEntity.class)
abstract class ResolveDamageMixin {
    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private void jojo$earnResolve(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        ProgressionService.onAttack((LivingEntity) (Object) this, source, amount);
    }
}
