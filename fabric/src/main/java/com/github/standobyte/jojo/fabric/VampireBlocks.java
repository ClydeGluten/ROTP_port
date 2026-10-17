package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class VampireBlocks {
    public static final StoneMaskBlock STONE_MASK = mask("stone_mask");
    public static final StoneMaskBlock AJA_STONE_MASK = mask("aja_stone_mask");
    public static final java.util.Map<DyeColor, WoodenCoffinBlock> COFFINS = coffins();
    public static final BlockEntityType<StoneMaskBlockEntity> MASK_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            RipplesFabric.id("stone_mask"), FabricBlockEntityTypeBuilder.create(StoneMaskBlockEntity::new, STONE_MASK, AJA_STONE_MASK).build());
    private static StoneMaskBlock mask(String name) {
        var key = ResourceKey.create(Registries.BLOCK, RipplesFabric.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, new StoneMaskBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).setId(key).noCollision().noLootTable().isValidSpawn((state, level, pos, type) -> false)));
    }
    private static java.util.Map<DyeColor, WoodenCoffinBlock> coffins() {
        var result = new java.util.EnumMap<DyeColor, WoodenCoffinBlock>(DyeColor.class);
        for (var color : DyeColor.values()) {
            var key = ResourceKey.create(Registries.BLOCK, RipplesFabric.id("wooden_coffin_oak_" + color.getName()));
            result.put(color, Registry.register(BuiltInRegistries.BLOCK, key, new WoodenCoffinBlock(color, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).setId(key).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED))));
        }
        return java.util.Collections.unmodifiableMap(result);
    }
    public static void splashBlood(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 position, double radius) {
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(position.subtract(radius, radius, radius)),
                net.minecraft.core.BlockPos.containing(position.add(radius, radius, radius))))
            if (level.getBlockState(pos).is(STONE_MASK)) StoneMaskBlock.splash(level, pos);
    }
    public static void initialize() {
        var fire = net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance();
        COFFINS.values().forEach(block -> fire.add(block, 5, 5));
    }
    private VampireBlocks() { }
}
