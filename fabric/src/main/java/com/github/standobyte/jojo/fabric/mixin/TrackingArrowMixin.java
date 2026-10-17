package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
@Mixin(AbstractArrow.class)
abstract class TrackingArrowMixin {
 @WrapOperation(method="onHitEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setArrowCount(I)V"))
 private void jojo$embedded(LivingEntity target,int count,Operation<Void> original){original.call(target,count);TrackingHooks.projectileEmbedded((AbstractArrow)(Object)this,target,false);}
 @ModifyReturnValue(method="getPickupItem",at=@At("RETURN")) private ItemStack jojo$pickup(ItemStack result){return TrackingHooks.projectilePickup((AbstractArrow)(Object)this,result);}
}
