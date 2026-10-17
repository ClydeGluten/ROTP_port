package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Original ModdedProjectileEntity knockback factor, before vanilla sends player velocity. */
@Mixin(LivingEntity.class)
abstract class OwnedBodyPartKnockbackMixin {
    @WrapMethod(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V")
    private void jojo$projectileKnockback(double strength, double x, double z, DamageSource source,
                                         float damage, boolean force, Operation<Void> original) {
        if (source.getDirectEntity() instanceof OwnedBodyPartEntity part) strength *= part.knockbackMultiplier();
        if (source.getDirectEntity() instanceof com.github.standobyte.jojo.fabric.pillarman.PillarSandstormEntity sandstorm) strength *= sandstorm.knockbackMultiplier();
        if (strength > 0) original.call(strength, x, z, source, damage, force);
    }
}
