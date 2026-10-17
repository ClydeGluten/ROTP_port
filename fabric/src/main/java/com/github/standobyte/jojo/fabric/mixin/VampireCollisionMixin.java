package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.VampireCollisionImpact;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class VampireCollisionMixin {
    @Inject(method = "collide", at = @At("RETURN"))
    private void jojo$vampireImpact(Vec3 movement, CallbackInfoReturnable<Vec3> result) {
        VampireCollisionImpact.collide((Entity) (Object) this, movement, result.getReturnValue());
        com.github.standobyte.jojo.fabric.stand.StandHeavyImpact.collide((Entity)(Object)this,movement,result.getReturnValue());
    }
}
