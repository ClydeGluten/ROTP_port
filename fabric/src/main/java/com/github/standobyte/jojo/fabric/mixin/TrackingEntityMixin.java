package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.world.entity.Entity.class)
abstract class TrackingEntityMixin {
 @Inject(method="baseTick",at=@At("TAIL")) private void jojo$actualHolder(CallbackInfo ci){
  var entity=(net.minecraft.world.entity.Entity)(Object)this;
  if(!entity.level().isClientSide()&&(entity instanceof net.minecraft.world.entity.item.ItemEntity||entity instanceof net.minecraft.world.entity.decoration.ItemFrame||entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow||entity instanceof net.minecraft.world.Container))TrackingHooks.entityChanged(entity);
 }
}
