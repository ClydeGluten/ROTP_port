package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.world.entity.animal.equine.AbstractHorse.class)
abstract class TrackingHorseMixin {
 @Shadow protected net.minecraft.world.SimpleContainer inventory;
 @Inject(method="createInventory",at=@At("TAIL")) private void jojo$horseInventory(CallbackInfo ci){TrackingHooks.inventoryChanged((net.minecraft.world.entity.LivingEntity)(Object)this,inventory);}
}
