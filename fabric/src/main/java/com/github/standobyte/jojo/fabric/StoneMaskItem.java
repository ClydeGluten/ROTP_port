package com.github.standobyte.jojo.fabric;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Original 101-tick activated texture followed by dropping the worn mask. */
public final class StoneMaskItem extends net.minecraft.world.item.BlockItem {
    public StoneMaskItem(net.minecraft.world.level.block.Block block, Properties properties) { super(block, properties); }
    public static int activationTicks(ItemStack stack) { return stack.getOrDefault(VampireItems.MASK_ACTIVATION, 0); }
    public static void activate(ItemStack stack) { stack.set(VampireItems.MASK_ACTIVATION, 101); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.accept(net.minecraft.network.chat.Component.translatable("jojo.fabric.mask.find").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.accept(net.minecraft.network.chat.Component.translatable("jojo.fabric.mask.use",
                net.minecraft.network.chat.Component.keybind("jojo.key.disable_hotbars")).withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.accept(net.minecraft.network.chat.Component.translatable("jojo.fabric.mask.full").withStyle(net.minecraft.ChatFormatting.YELLOW));
        tooltip.accept(net.minecraft.network.chat.Component.translatable("jojo.fabric.mask.sun").withStyle(net.minecraft.ChatFormatting.RED));
    }
    @Override public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        int remaining = activationTicks(stack);
        if (remaining <= 0) return;
        stack.set(VampireItems.MASK_ACTIVATION, --remaining);
        if (remaining == 0 && entity instanceof LivingEntity wearer && wearer.getItemBySlot(EquipmentSlot.HEAD) == stack) {
            wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            wearer.spawnAtLocation(level, stack);
        }
    }
}
