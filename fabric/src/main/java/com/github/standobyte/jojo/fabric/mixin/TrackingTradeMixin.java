package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.*;
@Mixin(net.minecraft.world.inventory.MerchantResultSlot.class)
abstract class TrackingTradeMixin {
 @Shadow @Final private Merchant merchant;
 @WrapOperation(method="onTake",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/trading/MerchantOffer;take(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
 private boolean jojo$traded(MerchantOffer offer,ItemStack first,ItemStack second,Operation<Boolean> original){
  if(merchant instanceof net.minecraft.world.entity.Entity entity&&offer.satisfiedBy(first,second)){TrackingHooks.traded(entity,first);TrackingHooks.traded(entity,second);}
  return original.call(offer,first,second);
 }
}
