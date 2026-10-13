package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/** Adds the original 2% beetle Stand Arrow to desert pyramid chests, preserving vanilla loot. */
public final class StandArrowLoot {
    private static final float CHANCE = 0.02F;

    private StandArrowLoot() {}

    public static void initialize(Item beetleArrow) {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            // isBuiltin: only touch the vanilla table, never a datapack replacement.
            // Upstream's global modifier also affects replacement tables, so deliberately omit that gate.
            if (BuiltInLootTables.DESERT_PYRAMID.equals(key)) {
                builder.withPool(LootPool.lootPool()
                        .setRolls(ContextIntProviders.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(CHANCE))
                        .add(LootItem.lootTableItem(beetleArrow)));
            }
        });
    }
}
