package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.HamonCharges;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Projectile.class)
abstract class HamonProjectileMixin {
    @Inject(method="onHit",at=@At("HEAD"))private void jojo$chargedImpact(HitResult hit,CallbackInfo ci){HamonCharges.impact((Projectile)(Object)this,hit);}
}
