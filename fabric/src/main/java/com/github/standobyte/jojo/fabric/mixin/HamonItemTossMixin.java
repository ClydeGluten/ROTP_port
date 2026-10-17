package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.HamonCharges;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayer.class)
abstract class HamonItemTossMixin {
    @Inject(method="drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("RETURN"))
    private void jojo$infuseToss(ItemStack stack,boolean record,Prediction prediction,CallbackInfoReturnable<ItemEntity> cir){if(cir.getReturnValue()!=null)HamonCharges.chargeDrop((ServerPlayer)(Object)this,cir.getReturnValue());}
}
