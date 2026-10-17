package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.VampireLifecycle;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
abstract class VampireConsumeMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void jojo$beginCuring(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> result) {
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) VampireLifecycle.onEnchantedApple(entity);
    }
}
