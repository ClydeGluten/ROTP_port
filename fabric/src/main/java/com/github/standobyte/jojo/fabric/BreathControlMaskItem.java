package com.github.standobyte.jojo.fabric;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;

/** The two original breathing-mask explanations and their original spacing. */
public final class BreathControlMaskItem extends Item {
    public BreathControlMaskItem(Properties properties) {super(properties);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> tooltip,TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.jojo.breath_control_mask.hint").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal(" ").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.jojo.breath_control_mask.hint2").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal(" "));
    }
}
