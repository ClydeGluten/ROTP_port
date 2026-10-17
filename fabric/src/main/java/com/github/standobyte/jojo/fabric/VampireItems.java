package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class VampireItems {
    public static final DataComponentType<Integer> MASK_ACTIVATION = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            RipplesFabric.id("stone_mask_activation"), DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 101))
                    .networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Holder<MobEffect> SUN_BURN = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            RipplesFabric.id("sun_burn"), new MobEffect(MobEffectCategory.HARMFUL, 0x484D48) { });
    public static final Item STONE_MASK = mask("stone_mask", VampireBlocks.STONE_MASK);
    public static final Item AJA_STONE_MASK = mask("aja_stone_mask", VampireBlocks.AJA_STONE_MASK);
    public static final Item AJA_STONE = simple("aja_stone");
    public static final java.util.Map<net.minecraft.world.item.DyeColor, Item> COFFINS = coffins();
    private static Item simple(String name) {
        var key = ResourceKey.create(Registries.ITEM, RipplesFabric.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON).stacksTo(16)));
    }
    private static java.util.Map<net.minecraft.world.item.DyeColor, Item> coffins() {
        var result = new java.util.EnumMap<net.minecraft.world.item.DyeColor, Item>(net.minecraft.world.item.DyeColor.class);
        VampireBlocks.COFFINS.forEach((color, block) -> {
            var key = ResourceKey.create(Registries.ITEM, RipplesFabric.id("wooden_coffin_oak_" + color.getName()));
            var item = Registry.register(BuiltInRegistries.ITEM, key, new net.minecraft.world.item.BlockItem(block, new Item.Properties().setId(key).stacksTo(1)));
            item.registerBlocks(Item.BY_BLOCK, item);
            result.put(color, item);
        });
        return java.util.Collections.unmodifiableMap(result);
    }
    private static Item mask(String name, net.minecraft.world.level.block.Block block) {
        var key = ResourceKey.create(Registries.ITEM, RipplesFabric.id(name));
        var item = Registry.register(BuiltInRegistries.ITEM, key, new StoneMaskItem(block, new Item.Properties().setId(key)
                .durability(33).rarity(Rarity.RARE).equippable(EquipmentSlot.HEAD).attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ARMOR, new AttributeModifier(RipplesFabric.id("stone_mask"), 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD).build())));
        item.registerBlocks(Item.BY_BLOCK, item);
        return item;
    }
    public static void initialize() {
        AjaStoneLoot.initialize();
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.COMBAT)
                .register(output -> { output.accept(STONE_MASK); output.accept(AJA_STONE_MASK); output.accept(AJA_STONE); COFFINS.values().forEach(output::accept); });
    }
    private VampireItems() { }
}
