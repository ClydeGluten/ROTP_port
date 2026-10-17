package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public final class StoneMaskLootGameTests {
    @GameTest public void jungleLootAddsOriginalMaskChanceAndKeepsVanillaLoot(GameTestHelper test) {
        var registries = test.getLevel().getServer().reloadableRegistries();
        var jungle = registries.getLootTable(BuiltInLootTables.JUNGLE_TEMPLE);
        var desert = registries.getLootTable(BuiltInLootTables.DESERT_PYRAMID);
        var params = new LootParams.Builder(test.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        int masks = 0, otherItems = 0;
        for (long seed = 1; seed <= 5000; seed++) {
            var loot = jungle.getRandomItems(params, seed);
            int count = loot.stream().filter(stack -> stack.is(VampireItems.STONE_MASK)).mapToInt(ItemStack::getCount).sum();
            test.assertTrue(count <= 1, "One independent mask roll per jungle chest");
            masks += count;
            otherItems += loot.stream().filter(stack -> !stack.is(VampireItems.STONE_MASK)).mapToInt(ItemStack::getCount).sum();
            if (seed <= 100) test.assertFalse(desert.getRandomItems(params, seed).stream().anyMatch(stack -> stack.is(VampireItems.STONE_MASK)),
                    "Unrelated loot tables do not acquire the mask");
        }
        test.assertTrue(masks >= 330 && masks <= 470, "Original eight-percent frequency across fixed seeds: " + masks);
        test.assertTrue(otherItems > 5000, "Original vanilla chest contents remain");
        test.succeed();
    }
}
