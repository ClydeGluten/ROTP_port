package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/** Original jungle_temple_stone_mask global modifier: one independent eight-percent bonus roll. */
public final class StoneMaskLoot {
    public static void initialize(Item mask) {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            // Like the original global modifier, also applies to a replacement of this table.
            if (BuiltInLootTables.JUNGLE_TEMPLE.equals(key)) {
                builder.withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.08F))
                        .add(LootItem.lootTableItem(mask)));
            }
        });
    }

    private StoneMaskLoot() { }
}
