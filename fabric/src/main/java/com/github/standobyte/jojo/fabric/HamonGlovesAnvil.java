package com.github.standobyte.jojo.fabric;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;

/** Forge GlovesItem.combineEnchantments: merge nondamageable gloves without changing vanilla item repairs. */
public final class HamonGlovesAnvil {
    public record Result(ItemStack output,int cost) { }
    public static boolean gloves(ItemStack stack) {return stack.is(HamonItems.GLOVES) || stack.is(HamonItems.BUBBLE_GLOVES);}
    public static boolean accepts(ItemStack stack,Holder<Enchantment> enchantment) {
        return enchantment.value().canEnchant(stack) || gloves(stack) && enchantment.is(Enchantments.KNOCKBACK);
    }
    public static Result combine(ItemStack left,ItemStack right,String name,Player player) {
        if(!gloves(left) || !left.is(right.getItem()))throw new IllegalArgumentException("Original glove combiner requires two matching glove items");
        var output=left.copy();var merged=new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(left));
        var incoming=EnchantmentHelper.getEnchantmentsForCrafting(right);
        boolean book=!right.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty();
        int additional=0,rename=0;
        boolean accepted=false,rejected=false;
        for(var entry:incoming.entrySet()) {
            var enchantment=entry.getKey();int current=merged.getLevel(enchantment),next=entry.getIntValue();
            next=current==next?next+1:Math.max(current,next);
            boolean allowed=player.isCreative() || accepts(left,enchantment);
            for(var existing:merged.keySet())if(!existing.equals(enchantment) && !Enchantment.areCompatible(enchantment,existing)) {allowed=false;additional++;}
            if(!allowed) {rejected=true;continue;}
            accepted=true;next=Math.min(next,enchantment.value().getMaxLevel());merged.set(enchantment,next);
            int weight=enchantment.value().getAnvilCost();if(book)weight=Math.max(1,weight/2);
            additional+=weight*next;if(left.getCount()>1)additional=40;
        }
        if(rejected && !accepted)return new Result(ItemStack.EMPTY,0);
        if(name==null || name.isBlank()) {
            if(left.has(DataComponents.CUSTOM_NAME)){rename=1;additional++;output.remove(DataComponents.CUSTOM_NAME);}
        } else if(!name.equals(left.getHoverName().getString())) {rename=1;additional++;output.set(DataComponents.CUSTOM_NAME,Component.literal(name));}
        int leftPrior=left.getOrDefault(DataComponents.REPAIR_COST,0),rightPrior=right.getOrDefault(DataComponents.REPAIR_COST,0);
        int cost=(int)Math.min(Integer.MAX_VALUE,(long)leftPrior+rightPrior+additional);
        if(additional<=0)output=ItemStack.EMPTY;
        if(rename==additional && rename>0 && cost>=40)cost=39;
        if(cost>=40 && !player.isCreative())output=ItemStack.EMPTY;
        if(!output.isEmpty()) {
            int repair=Math.max(leftPrior,rightPrior);
            if(rename!=additional || rename==0)repair=AnvilMenu.calculateIncreasedRepairCost(repair);
            output.set(DataComponents.REPAIR_COST,repair);EnchantmentHelper.setEnchantments(output,merged.toImmutable());
        }
        return new Result(output,cost);
    }
    private HamonGlovesAnvil() { }
}
