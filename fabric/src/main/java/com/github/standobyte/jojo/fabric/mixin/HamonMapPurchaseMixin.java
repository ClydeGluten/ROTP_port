package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.HamonTempleTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
abstract class HamonMapPurchaseMixin {
    @Shadow @Final private Merchant merchant;
    @Inject(method="onTake",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V"))
    private void jojo$mapPurchased(Player player,ItemStack stack,CallbackInfo ci){HamonTempleTrades.onPurchase(player,merchant,stack);}
}
