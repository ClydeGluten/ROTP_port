package com.github.standobyte.jojo.fabric;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
public final class BubbleGlovesItem extends Item {
    public BubbleGlovesItem(Properties properties){super(properties);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(!player.isShiftKeyDown())return InteractionResult.FAIL;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        return HamonItems.reload(player.getItemInHand(hand),player,null)?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    @Override public boolean isBarVisible(ItemStack stack){return HamonItems.ammo(stack)<HamonItems.MAX_AMMO;}
    @Override public int getBarWidth(ItemStack stack){return Math.round(13F*HamonItems.ammo(stack)/HamonItems.MAX_AMMO);}
    @Override public int getBarColor(ItemStack stack){return net.minecraft.util.Mth.hsvToRgb(HamonItems.ammo(stack)/(float)HamonItems.MAX_AMMO/3,1,1);}
}
