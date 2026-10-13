package com.github.standobyte.jojo.fabric;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class StandItems {
    public static final Holder<MobEffect> STAND_VIRUS = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            RipplesFabric.id("stand_virus"), new MobEffect(MobEffectCategory.HARMFUL, 0xC10019) { });
    public static final Holder<MobEffect> RESOLVE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            RipplesFabric.id("resolve"), new MobEffect(MobEffectCategory.BENEFICIAL, 0xEBC440) { });
    public static final Item STAND_ARROW = register("stand_arrow", 25, Rarity.UNCOMMON);
    public static final Item STAND_ARROW_BEETLE = register("stand_arrow_beetle", 250, Rarity.RARE);

    private static Item register(String name, int durability, Rarity rarity) {
        var key = ResourceKey.create(Registries.ITEM, RipplesFabric.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new StandArrowItem(new Item.Properties().setId(key).durability(durability).rarity(rarity)));
    }
    public static void initialize() {
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.COMBAT)
                .register(output -> { output.accept(STAND_ARROW); output.accept(STAND_ARROW_BEETLE); });
    }
    private StandItems() { }
}
