package com.github.standobyte.jojo.fabric;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
public final class SoapItem extends Item {
    public SoapItem(Properties properties){super(properties.component(net.minecraft.core.component.DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumable.builder().consumeSeconds(1.6F).animation(ItemUseAnimation.DRINK).sound(SoundEvents.HONEY_DRINK).build()));}
    @Override public void inventoryTick(ItemStack stack,ServerLevel level,Entity entity,EquipmentSlot slot){
        if(entity instanceof Player player){var gloves=HamonItems.find(player,s->s.is(HamonItems.BUBBLE_GLOVES)&&HamonItems.ammo(s)<=0);
            if(!gloves.isEmpty())HamonItems.reload(gloves,player,stack);}
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        if(!level.isClientSide()){
            entity.addEffect(new MobEffectInstance(MobEffects.POISON,100,0));
            entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA,300,1));
            if(!(entity instanceof Player p&&p.isCreative())){stack.shrink(1);HamonItems.give(entity,new ItemStack(Items.GLASS_BOTTLE));}
        }
        return stack;
    }
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return 32;}
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack){return ItemUseAnimation.DRINK;}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){player.startUsingItem(hand);return InteractionResult.CONSUME;}
}
