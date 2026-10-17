package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.world.level.block.entity.BlockEntity.class)
abstract class TrackingBlockEntityMixin {
 @Inject(method="setChanged()V",at=@At("TAIL")) private void jojo$actualContainer(CallbackInfo ci){
  var block=(net.minecraft.world.level.block.entity.BlockEntity)(Object)this;
  if(block.getLevel() instanceof net.minecraft.server.level.ServerLevel level&&block instanceof net.minecraft.world.Container container)TrackingHooks.containerChanged(level,block.getBlockPos(),container);
 }
}
