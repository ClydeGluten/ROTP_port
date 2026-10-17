package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.HamonGlovesAnvil;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge GlovesItem.canApplyAtEnchantingTable adds Knockback to the superclass eligibility. */
@Mixin(EnchantmentHelper.class)
abstract class HamonGlovesEnchantingMixin {
    @Inject(method="lambda$getAvailableEnchantmentResults$0",at=@At("HEAD"),cancellable=true)
    private static void jojo$gloveKnockback(ItemStack stack,boolean book,Holder<Enchantment> enchantment,CallbackInfoReturnable<Boolean> cir) {
        if((HamonGlovesAnvil.gloves(stack) && enchantment.is(Enchantments.KNOCKBACK)) || com.github.standobyte.jojo.fabric.equipment.EquipmentContent.allowsEnchantment(stack,enchantment))cir.setReturnValue(true);
    }
}
