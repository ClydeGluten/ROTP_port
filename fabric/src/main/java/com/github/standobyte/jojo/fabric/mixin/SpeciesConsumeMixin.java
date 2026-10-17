package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.SpeciesHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** Forge finish-use callback after consumption, retaining the item before its final stack is consumed. */
@Mixin(Consumable.class)
abstract class SpeciesConsumeMixin {
    @WrapMethod(method = "onConsume")
    private ItemStack jojo$speciesFood(Level level, LivingEntity user, ItemStack stack, Operation<ItemStack> original) {
        ItemStack consumed = stack.copy();
        ItemStack result = original.call(level, user, stack);
        SpeciesHooks.onFoodEaten(user, consumed);
        return result;
    }
}
