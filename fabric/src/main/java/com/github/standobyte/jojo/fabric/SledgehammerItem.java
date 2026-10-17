package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Original iron pickaxe-based sledgehammer: +9 tier damage, -3.3 speed, two mining wear and one hit wear. */
public final class SledgehammerItem extends Item {
    public static final Item SLEDGEHAMMER = register();
    private static Item register() {
        var key = ResourceKey.create(Registries.ITEM, RipplesFabric.id("sledgehammer"));
        return Registry.register(BuiltInRegistries.ITEM, key, new SledgehammerItem(new Item.Properties().setId(key)
                .pickaxe(ToolMaterial.IRON, 9, -3.3F).component(DataComponents.WEAPON, new Weapon(1))));
    }
    private SledgehammerItem(Properties properties) { super(properties); }
    @Override public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos position, LivingEntity miner) {
        if (!level.isClientSide() && state.getDestroySpeed(level, position) != 0) stack.hurtAndBreak(2, miner, EquipmentSlot.MAINHAND);
        return true;
    }
    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(SLEDGEHAMMER));
    }
}
