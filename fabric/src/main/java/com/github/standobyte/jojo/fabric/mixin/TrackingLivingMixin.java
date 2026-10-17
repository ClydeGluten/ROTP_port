package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.world.entity.LivingEntity.class)
abstract class TrackingLivingMixin {
 @Inject(method="tick",at=@At("TAIL")) private void jojo$tracking(CallbackInfo ci){
  var entity=(net.minecraft.world.entity.LivingEntity)(Object)this;
  if(com.github.standobyte.jojo.fabric.TimeStopService.isFrozen(entity))return;
  if(!entity.level().isClientSide()&&!(entity instanceof net.minecraft.server.level.ServerPlayer)){
   TrackingHooks.entityChanged(entity);com.github.standobyte.jojo.fabric.StandEffects.tick(entity);
  }
  TrackingHooks.livingTick(entity);
 }
}
