package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.HamonGlovesAnvil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
abstract class HamonGlovesAnvilMixin extends ItemCombinerMenu {
    @Shadow @Final private DataSlot cost;
    @Shadow private String itemName;
    @Shadow private int repairItemCountCost;
    @Shadow private boolean onlyRenaming;
    protected HamonGlovesAnvilMixin(MenuType<?> type,int id,Inventory inventory,ContainerLevelAccess access,ItemCombinerMenuSlotDefinition slots) {super(type,id,inventory,access,slots);}
    @Inject(method="createResult",at=@At("HEAD"),cancellable=true)
    private void jojo$combineMatchingGloves(CallbackInfo ci) {
        var left=inputSlots.getItem(0);var right=inputSlots.getItem(1);
        if(right.isEmpty() || !HamonGlovesAnvil.gloves(left) || !left.is(right.getItem()))return;
        var result=HamonGlovesAnvil.combine(left,right,itemName,player);
        cost.set(result.cost());repairItemCountCost=0;
        // Forge's original special merge consumed the second glove even for a rename-only merge.
        onlyRenaming=false;resultSlots.setItem(0,result.output());broadcastChanges();ci.cancel();
    }
    @WrapOperation(method="createResult",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean jojo$knockbackBook(Enchantment enchantment,ItemStack stack,Operation<Boolean> original,@Local(ordinal=0) Holder<Enchantment> holder) {
        return original.call(enchantment,stack) || HamonGlovesAnvil.gloves(stack) && holder.is(Enchantments.KNOCKBACK) || com.github.standobyte.jojo.fabric.equipment.EquipmentContent.allowsEnchantment(stack,holder);
    }
}
