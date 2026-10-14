package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.TimeStopService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original DamageUtil.applyKnockbackStack: frozen impacts add to momentum without damping. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), cancellable = true)
    private void jojoFrozenKnockback(double strength, double x, double z, DamageSource source,
                                    float damage, boolean force, CallbackInfo ci) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (!(target.level() instanceof ServerLevel) || !TimeStopService.isFrozen(target)) return;
        ci.cancel();
        strength *= 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (strength <= 0) return;
        Vec3 direction = new Vec3(x, 0, z).normalize().scale(strength);
        Vec3 old = target.getDeltaMovement();
        target.setDeltaMovement(old.x - direction.x, Math.min(0.4, old.y + strength), old.z - direction.z);
        target.needsSync = true;
    }
}
