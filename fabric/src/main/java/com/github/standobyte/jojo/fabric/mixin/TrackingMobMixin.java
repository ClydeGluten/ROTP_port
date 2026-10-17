package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.world.entity.Mob.class)
abstract class TrackingMobMixin {
 @Shadow protected abstract void pickUpItem(net.minecraft.server.level.ServerLevel level,net.minecraft.world.entity.item.ItemEntity item);
 @Inject(method="aiStep",at=@At("TAIL")) private void jojo$markedPickup(CallbackInfo ci){
  var mob=(net.minecraft.world.entity.Mob)(Object)this;
  if(mob.level() instanceof net.minecraft.server.level.ServerLevel level)for(var item:TrackingHooks.markedPickupCandidates(mob))pickUpItem(level,item);
 }
}
