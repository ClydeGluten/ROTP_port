package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.HamonService;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class HamonProgressionMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void jojo$hamonTick(CallbackInfo ci) {HamonService.tick((LivingEntity)(Object)this);}
    @Inject(method="getDimensions",at=@At("RETURN"),cancellable=true)
    private void jojo$meditationSize(Pose pose,CallbackInfoReturnable<EntityDimensions> cir) {
        cir.setReturnValue(HamonService.meditationDimensions((LivingEntity)(Object)this,cir.getReturnValue()));
    }
    @ModifyArg(method="travelInWater",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"),index=0)
    private float jojo$breathingSwimSpeed(float acceleration) {return com.github.standobyte.jojo.fabric.PillarmanService.swimmingAcceleration((LivingEntity)(Object)this,HamonService.swimmingAcceleration((LivingEntity)(Object)this,acceleration));}
}
