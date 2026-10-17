package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.item.enchantment.Enchantments;

/** Original redstone_ore_aja_stone: no Silk Touch; Fortune chances 2/3/4/5 percent. */
public final class AjaStoneLoot {
    public static void initialize() {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (!key.identifier().equals(net.minecraft.resources.Identifier.withDefaultNamespace("blocks/redstone_ore"))) return;
            var ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
            var noSilkTouch = LootItemCondition.DIRECT_CODEC.parse(ops, com.google.gson.JsonParser.parseString("""
                    {"type":"minecraft:inverted","term":{"type":"minecraft:match_tool","predicate":{"predicates":{"minecraft:enchantments":[{"enchantments":"minecraft:silk_touch","levels":{"min":1}}]}}}}
                    """)).getOrThrow();
            builder.withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).when(Holder.direct(noSilkTouch))
                    .when(BonusLevelTableCondition.bonusLevelFlatChance(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), .02F, .03F, .04F, .05F))
                    .add(LootItem.lootTableItem(VampireItems.AJA_STONE)));
        });
    }
    private AjaStoneLoot() { }
}
