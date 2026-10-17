package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/** Exercises the real modern anvil override, including its otherwise different right-slot behavior. */
public final class HamonGlovesGameTests {
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void nondamageableGlovesMergeAndConsumeTheSacrifice(GameTestHelper test) {
        var player=VampireGameTests.player(test,"hamon-gloves",1.5);
        player.experienceLevel=50;
        var knockback=test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK);
        var left=new ItemStack(HamonItems.BUBBLE_GLOVES);var right=new ItemStack(HamonItems.BUBBLE_GLOVES);
        HamonItems.value(left,"Ammo",123);left.enchant(knockback,1);right.enchant(knockback,1);
        var menu=new AnvilMenu(1,player.getInventory());
        menu.getSlot(0).set(left);menu.getSlot(1).set(right);menu.createResult();
        var result=menu.getSlot(2).getItem();
        test.assertTrue(!result.isEmpty()&&result.getEnchantments().getLevel(knockback)==2,"Matching nondamageable gloves merge Knockback I into II");
        test.assertTrue(menu.getCost()==4&&result.getOrDefault(DataComponents.REPAIR_COST,0)==1,"Original rarity cost and prior-work growth are retained");
        test.assertTrue(HamonItems.ammo(result)==123,"Combining keeps the left glove's soap charge");
        menu.getSlot(2).onTake(player,result);
        test.assertTrue(menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty(),"Taking a merged glove consumes both inputs");
        test.assertTrue(player.experienceLevel==46,"Merge spends the displayed levels");
        var rename=new AnvilMenu(2,player.getInventory());
        rename.getSlot(0).set(new ItemStack(HamonItems.GLOVES));rename.getSlot(1).set(new ItemStack(HamonItems.GLOVES));rename.setItemName("Caesar");
        test.assertTrue(rename.getCost()==1&&!rename.getSlot(2).getItem().isEmpty(),"Matching gloves can be renamed without enchantments");
        rename.getSlot(2).onTake(player,rename.getSlot(2).getItem());
        test.assertTrue(rename.getSlot(1).getItem().isEmpty(),"Source rename-only glove merge still consumes the second glove");
        player.level().getServer().getPlayerList().remove(player);test.succeed();
    }
}
