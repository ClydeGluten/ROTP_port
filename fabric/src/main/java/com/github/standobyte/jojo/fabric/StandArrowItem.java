package com.github.standobyte.jojo.fabric;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Self-piercing acquisition slice; projectile and enchantment behavior is deferred. */
public final class StandArrowItem extends Item {
    public StandArrowItem(Properties properties) { super(properties); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (!StandArrowService.pierce(serverPlayer)) return InteractionResult.FAIL;
        player.getItemInHand(hand).hurtAndBreak(1, player, hand);
        return InteractionResult.SUCCESS;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                          Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("jojo.fabric.arrow.use").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("jojo.fabric.arrow.cost").withStyle(ChatFormatting.YELLOW));
        tooltip.accept(Component.translatable("jojo.fabric.arrow.danger").withStyle(ChatFormatting.RED));
        tooltip.accept(Component.translatable("jojo.fabric.arrow.pool").withStyle(ChatFormatting.GRAY));
    }
}
