package com.github.standobyte.jojo.fabric;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
public final class HamonOilItem extends Item {
    public HamonOilItem(Properties properties){super(properties);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        ItemStack weapon=player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);
        if(!HamonItems.isWeapon(weapon))return InteractionResult.FAIL;
        if(!level.isClientSide()){
            HamonItems.value(weapon,"HamonOiled",HamonItems.MAX_OIL_USES);
            if(!player.isCreative()){player.getItemInHand(hand).shrink(1);HamonItems.give(player,new ItemStack(Items.GLASS_BOTTLE));}
            level.playSound(null,player.getX(),player.getEyeY(),player.getZ(),SoundEvents.BOTTLE_EMPTY,player.getSoundSource(),1,1);
        }
        return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip,TooltipFlag flag){
        tooltip.accept(net.minecraft.network.chat.Component.translatable("item.jojo.oil.hint").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
