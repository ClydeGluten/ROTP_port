package com.github.standobyte.jojo.fabric.stand;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Exact vanilla 1.16.5 Material assignments, extracted from Mojang's official server registry.
 * Modern-only blocks stay UNKNOWN until explicitly classified; no sound/tag approximation. */
public final class SourceEraMaterials {
    public enum Material { UNKNOWN, AIR, STONE, GRASS, DIRT, WOOD, PLANT, WATER, LAVA, SAND, LEAVES, SPONGE, GLASS, METAL, WOOL, DECORATION, PISTON, WEB, REPLACEABLE_PLANT, REPLACEABLE_WATER_PLANT, EXPLOSIVE, FIRE, TOP_SNOW, ICE, SNOW, CACTUS, CLAY, VEGETABLE, PORTAL, CAKE, EGG, BUILDABLE_GLASS, HEAVY_METAL, BARRIER, CLOTH_DECORATION, ICE_SOLID, STRUCTURAL_AIR, SHULKER_SHELL, WATER_PLANT, BAMBOO_SAPLING, BAMBOO, BUBBLE_COLUMN, NETHER_WOOD, REPLACEABLE_FIREPROOF_PLANT }
    private static final Map<Block, Material> MATERIALS = new IdentityHashMap<>();
    static {
        names(Material.AIR,
                "air", "void_air", "cave_air");
        names(Material.STONE,
                "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite",
                "polished_andesite", "cobblestone", "bedrock", "gold_ore", "iron_ore", "coal_ore",
                "nether_gold_ore", "lapis_ore", "dispenser", "sandstone", "chiseled_sandstone", "cut_sandstone",
                "bricks", "mossy_cobblestone", "obsidian", "spawner", "diamond_ore", "furnace",
                "cobblestone_stairs", "stone_pressure_plate", "redstone_ore", "netherrack", "basalt", "polished_basalt",
                "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "brick_stairs", "stone_brick_stairs",
                "nether_bricks", "nether_brick_fence", "nether_brick_stairs", "enchanting_table", "end_portal_frame", "end_stone",
                "sandstone_stairs", "emerald_ore", "ender_chest", "cobblestone_wall", "mossy_cobblestone_wall", "nether_quartz_ore",
                "quartz_block", "chiseled_quartz_block", "quartz_pillar", "quartz_stairs", "dropper", "white_terracotta",
                "orange_terracotta", "magenta_terracotta", "light_blue_terracotta", "yellow_terracotta", "lime_terracotta", "pink_terracotta",
                "gray_terracotta", "light_gray_terracotta", "cyan_terracotta", "purple_terracotta", "blue_terracotta", "brown_terracotta",
                "green_terracotta", "red_terracotta", "black_terracotta", "prismarine", "prismarine_bricks", "dark_prismarine",
                "prismarine_stairs", "prismarine_brick_stairs", "dark_prismarine_stairs", "prismarine_slab", "prismarine_brick_slab", "dark_prismarine_slab",
                "terracotta", "coal_block", "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone", "red_sandstone_stairs",
                "stone_slab", "smooth_stone_slab", "sandstone_slab", "cut_sandstone_slab", "petrified_oak_slab", "cobblestone_slab",
                "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab", "red_sandstone_slab", "cut_red_sandstone_slab",
                "purpur_slab", "smooth_stone", "smooth_sandstone", "smooth_quartz", "smooth_red_sandstone", "purpur_block",
                "purpur_pillar", "purpur_stairs", "end_stone_bricks", "magma_block", "red_nether_bricks", "bone_block",
                "observer", "white_glazed_terracotta", "orange_glazed_terracotta", "magenta_glazed_terracotta", "light_blue_glazed_terracotta", "yellow_glazed_terracotta",
                "lime_glazed_terracotta", "pink_glazed_terracotta", "gray_glazed_terracotta", "light_gray_glazed_terracotta", "cyan_glazed_terracotta", "purple_glazed_terracotta",
                "blue_glazed_terracotta", "brown_glazed_terracotta", "green_glazed_terracotta", "red_glazed_terracotta", "black_glazed_terracotta", "white_concrete",
                "orange_concrete", "magenta_concrete", "light_blue_concrete", "yellow_concrete", "lime_concrete", "pink_concrete",
                "gray_concrete", "light_gray_concrete", "cyan_concrete", "purple_concrete", "blue_concrete", "brown_concrete",
                "green_concrete", "red_concrete", "black_concrete", "dead_tube_coral_block", "dead_brain_coral_block", "dead_bubble_coral_block",
                "dead_fire_coral_block", "dead_horn_coral_block", "tube_coral_block", "brain_coral_block", "bubble_coral_block", "fire_coral_block",
                "horn_coral_block", "dead_tube_coral", "dead_brain_coral", "dead_bubble_coral", "dead_fire_coral", "dead_horn_coral",
                "dead_tube_coral_fan", "dead_brain_coral_fan", "dead_bubble_coral_fan", "dead_fire_coral_fan", "dead_horn_coral_fan", "dead_tube_coral_wall_fan",
                "dead_brain_coral_wall_fan", "dead_bubble_coral_wall_fan", "dead_fire_coral_wall_fan", "dead_horn_coral_wall_fan", "polished_granite_stairs", "smooth_red_sandstone_stairs",
                "mossy_stone_brick_stairs", "polished_diorite_stairs", "mossy_cobblestone_stairs", "end_stone_brick_stairs", "stone_stairs", "smooth_sandstone_stairs",
                "smooth_quartz_stairs", "granite_stairs", "andesite_stairs", "red_nether_brick_stairs", "polished_andesite_stairs", "diorite_stairs",
                "polished_granite_slab", "smooth_red_sandstone_slab", "mossy_stone_brick_slab", "polished_diorite_slab", "mossy_cobblestone_slab", "end_stone_brick_slab",
                "smooth_sandstone_slab", "smooth_quartz_slab", "granite_slab", "andesite_slab", "red_nether_brick_slab", "polished_andesite_slab",
                "diorite_slab", "brick_wall", "prismarine_wall", "red_sandstone_wall", "mossy_stone_brick_wall", "granite_wall",
                "stone_brick_wall", "nether_brick_wall", "andesite_wall", "red_nether_brick_wall", "sandstone_wall", "end_stone_brick_wall",
                "diorite_wall", "smoker", "blast_furnace", "stonecutter", "warped_nylium", "crimson_nylium",
                "crying_obsidian", "respawn_anchor", "blackstone", "blackstone_stairs", "blackstone_wall", "blackstone_slab",
                "polished_blackstone", "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone_brick_slab", "polished_blackstone_brick_stairs",
                "polished_blackstone_brick_wall", "gilded_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_pressure_plate", "polished_blackstone_wall",
                "chiseled_nether_bricks", "cracked_nether_bricks", "quartz_bricks");
        names(Material.GRASS,
                "grass_block", "mycelium", "hay_block", "nether_wart_block", "dried_kelp_block", "warped_wart_block",
                "shroomlight", "target");
        names(Material.DIRT,
                "dirt", "coarse_dirt", "podzol", "farmland", "soul_soil", "grass_path");
        names(Material.WOOD,
                "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
                "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
                "stripped_spruce_log", "stripped_birch_log", "stripped_jungle_log", "stripped_acacia_log", "stripped_dark_oak_log", "stripped_oak_log",
                "oak_wood", "spruce_wood", "birch_wood", "jungle_wood", "acacia_wood", "dark_oak_wood",
                "stripped_oak_wood", "stripped_spruce_wood", "stripped_birch_wood", "stripped_jungle_wood", "stripped_acacia_wood", "stripped_dark_oak_wood",
                "note_block", "bookshelf", "oak_stairs", "chest", "crafting_table", "oak_sign",
                "spruce_sign", "birch_sign", "acacia_sign", "jungle_sign", "dark_oak_sign", "oak_door",
                "oak_wall_sign", "spruce_wall_sign", "birch_wall_sign", "acacia_wall_sign", "jungle_wall_sign", "dark_oak_wall_sign",
                "oak_pressure_plate", "spruce_pressure_plate", "birch_pressure_plate", "jungle_pressure_plate", "acacia_pressure_plate", "dark_oak_pressure_plate",
                "jukebox", "oak_fence", "oak_trapdoor", "spruce_trapdoor", "birch_trapdoor", "jungle_trapdoor",
                "acacia_trapdoor", "dark_oak_trapdoor", "brown_mushroom_block", "red_mushroom_block", "mushroom_stem", "oak_fence_gate",
                "spruce_stairs", "birch_stairs", "jungle_stairs", "trapped_chest", "daylight_detector", "acacia_stairs",
                "dark_oak_stairs", "white_banner", "orange_banner", "magenta_banner", "light_blue_banner", "yellow_banner",
                "lime_banner", "pink_banner", "gray_banner", "light_gray_banner", "cyan_banner", "purple_banner",
                "blue_banner", "brown_banner", "green_banner", "red_banner", "black_banner", "white_wall_banner",
                "orange_wall_banner", "magenta_wall_banner", "light_blue_wall_banner", "yellow_wall_banner", "lime_wall_banner", "pink_wall_banner",
                "gray_wall_banner", "light_gray_wall_banner", "cyan_wall_banner", "purple_wall_banner", "blue_wall_banner", "brown_wall_banner",
                "green_wall_banner", "red_wall_banner", "black_wall_banner", "oak_slab", "spruce_slab", "birch_slab",
                "jungle_slab", "acacia_slab", "dark_oak_slab", "spruce_fence_gate", "birch_fence_gate", "jungle_fence_gate",
                "acacia_fence_gate", "dark_oak_fence_gate", "spruce_fence", "birch_fence", "jungle_fence", "acacia_fence",
                "dark_oak_fence", "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door",
                "loom", "barrel", "cartography_table", "fletching_table", "lectern", "smithing_table",
                "campfire", "soul_campfire", "composter", "bee_nest", "beehive");
        names(Material.PLANT,
                "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling",
                "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "wither_rose",
                "lily_of_the_valley", "brown_mushroom", "red_mushroom", "wheat", "sugar_cane", "attached_pumpkin_stem",
                "attached_melon_stem", "pumpkin_stem", "melon_stem", "lily_pad", "nether_wart", "cocoa",
                "carrots", "potatoes", "chorus_plant", "chorus_flower", "beetroots", "sweet_berry_bush",
                "warped_fungus", "crimson_fungus", "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant");
        names(Material.WATER,
                "water");
        names(Material.LAVA,
                "lava");
        names(Material.SAND,
                "sand", "red_sand", "gravel", "soul_sand", "white_concrete_powder", "orange_concrete_powder",
                "magenta_concrete_powder", "light_blue_concrete_powder", "yellow_concrete_powder", "lime_concrete_powder", "pink_concrete_powder", "gray_concrete_powder",
                "light_gray_concrete_powder", "cyan_concrete_powder", "purple_concrete_powder", "blue_concrete_powder", "brown_concrete_powder", "green_concrete_powder",
                "red_concrete_powder", "black_concrete_powder");
        names(Material.LEAVES,
                "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves");
        names(Material.SPONGE,
                "sponge", "wet_sponge");
        names(Material.GLASS,
                "glass", "glowstone", "white_stained_glass", "orange_stained_glass", "magenta_stained_glass", "light_blue_stained_glass",
                "yellow_stained_glass", "lime_stained_glass", "pink_stained_glass", "gray_stained_glass", "light_gray_stained_glass", "cyan_stained_glass",
                "purple_stained_glass", "blue_stained_glass", "brown_stained_glass", "green_stained_glass", "red_stained_glass", "black_stained_glass",
                "glass_pane", "beacon", "white_stained_glass_pane", "orange_stained_glass_pane", "magenta_stained_glass_pane", "light_blue_stained_glass_pane",
                "yellow_stained_glass_pane", "lime_stained_glass_pane", "pink_stained_glass_pane", "gray_stained_glass_pane", "light_gray_stained_glass_pane", "cyan_stained_glass_pane",
                "purple_stained_glass_pane", "blue_stained_glass_pane", "brown_stained_glass_pane", "green_stained_glass_pane", "red_stained_glass_pane", "black_stained_glass_pane",
                "sea_lantern", "conduit");
        names(Material.METAL,
                "lapis_block", "gold_block", "iron_block", "diamond_block", "iron_door", "iron_bars",
                "chain", "brewing_stand", "cauldron", "emerald_block", "command_block", "light_weighted_pressure_plate",
                "heavy_weighted_pressure_plate", "redstone_block", "hopper", "iron_trapdoor", "repeating_command_block", "chain_command_block",
                "bell", "lantern", "soul_lantern", "structure_block", "jigsaw", "netherite_block",
                "ancient_debris");
        names(Material.WOOL,
                "white_bed", "orange_bed", "magenta_bed", "light_blue_bed", "yellow_bed", "lime_bed",
                "pink_bed", "gray_bed", "light_gray_bed", "cyan_bed", "purple_bed", "blue_bed",
                "brown_bed", "green_bed", "red_bed", "black_bed", "white_wool", "orange_wool",
                "magenta_wool", "light_blue_wool", "yellow_wool", "lime_wool", "pink_wool", "gray_wool",
                "light_gray_wool", "cyan_wool", "purple_wool", "blue_wool", "brown_wool", "green_wool",
                "red_wool", "black_wool");
        names(Material.DECORATION,
                "powered_rail", "detector_rail", "torch", "wall_torch", "redstone_wire", "ladder",
                "rail", "lever", "redstone_torch", "redstone_wall_torch", "stone_button", "soul_torch",
                "soul_wall_torch", "repeater", "tripwire_hook", "tripwire", "flower_pot", "potted_oak_sapling",
                "potted_spruce_sapling", "potted_birch_sapling", "potted_jungle_sapling", "potted_acacia_sapling", "potted_dark_oak_sapling", "potted_fern",
                "potted_dandelion", "potted_poppy", "potted_blue_orchid", "potted_allium", "potted_azure_bluet", "potted_red_tulip",
                "potted_orange_tulip", "potted_white_tulip", "potted_pink_tulip", "potted_oxeye_daisy", "potted_cornflower", "potted_lily_of_the_valley",
                "potted_wither_rose", "potted_red_mushroom", "potted_brown_mushroom", "potted_dead_bush", "potted_cactus", "oak_button",
                "spruce_button", "birch_button", "jungle_button", "acacia_button", "dark_oak_button", "skeleton_skull",
                "skeleton_wall_skull", "wither_skeleton_skull", "wither_skeleton_wall_skull", "zombie_head", "zombie_wall_head", "player_head",
                "player_wall_head", "creeper_head", "creeper_wall_head", "dragon_head", "dragon_wall_head", "comparator",
                "activator_rail", "end_rod", "potted_bamboo", "scaffolding", "crimson_button", "warped_button",
                "potted_crimson_fungus", "potted_warped_fungus", "potted_crimson_roots", "potted_warped_roots", "polished_blackstone_button");
        names(Material.PISTON,
                "sticky_piston", "piston", "piston_head", "moving_piston");
        names(Material.WEB,
                "cobweb");
        names(Material.REPLACEABLE_PLANT,
                "grass", "fern", "dead_bush", "vine", "sunflower", "lilac",
                "rose_bush", "peony", "tall_grass", "large_fern");
        names(Material.REPLACEABLE_WATER_PLANT,
                "seagrass", "tall_seagrass");
        names(Material.EXPLOSIVE,
                "tnt");
        names(Material.FIRE,
                "fire", "soul_fire");
        names(Material.TOP_SNOW,
                "snow");
        names(Material.ICE,
                "ice", "frosted_ice");
        names(Material.SNOW,
                "snow_block");
        names(Material.CACTUS,
                "cactus");
        names(Material.CLAY,
                "clay", "infested_stone", "infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks", "infested_cracked_stone_bricks",
                "infested_chiseled_stone_bricks", "slime_block", "honey_block", "honeycomb_block");
        names(Material.VEGETABLE,
                "pumpkin", "carved_pumpkin", "jack_o_lantern", "melon");
        names(Material.PORTAL,
                "nether_portal", "end_portal", "end_gateway");
        names(Material.CAKE,
                "cake");
        names(Material.EGG,
                "dragon_egg", "turtle_egg");
        names(Material.BUILDABLE_GLASS,
                "redstone_lamp");
        names(Material.HEAVY_METAL,
                "anvil", "chipped_anvil", "damaged_anvil", "grindstone", "lodestone");
        names(Material.BARRIER,
                "barrier");
        names(Material.CLOTH_DECORATION,
                "white_carpet", "orange_carpet", "magenta_carpet", "light_blue_carpet", "yellow_carpet", "lime_carpet",
                "pink_carpet", "gray_carpet", "light_gray_carpet", "cyan_carpet", "purple_carpet", "blue_carpet",
                "brown_carpet", "green_carpet", "red_carpet", "black_carpet");
        names(Material.ICE_SOLID,
                "packed_ice", "blue_ice");
        names(Material.STRUCTURAL_AIR,
                "structure_void");
        names(Material.SHULKER_SHELL,
                "shulker_box", "white_shulker_box", "orange_shulker_box", "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box",
                "lime_shulker_box", "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box",
                "blue_shulker_box", "brown_shulker_box", "green_shulker_box", "red_shulker_box", "black_shulker_box");
        names(Material.WATER_PLANT,
                "kelp", "kelp_plant", "tube_coral", "brain_coral", "bubble_coral", "fire_coral",
                "horn_coral", "tube_coral_fan", "brain_coral_fan", "bubble_coral_fan", "fire_coral_fan", "horn_coral_fan",
                "tube_coral_wall_fan", "brain_coral_wall_fan", "bubble_coral_wall_fan", "fire_coral_wall_fan", "horn_coral_wall_fan", "sea_pickle");
        names(Material.BAMBOO_SAPLING,
                "bamboo_sapling");
        names(Material.BAMBOO,
                "bamboo");
        names(Material.BUBBLE_COLUMN,
                "bubble_column");
        names(Material.NETHER_WOOD,
                "warped_stem", "stripped_warped_stem", "warped_hyphae", "stripped_warped_hyphae", "crimson_stem", "stripped_crimson_stem",
                "crimson_hyphae", "stripped_crimson_hyphae", "crimson_planks", "warped_planks", "crimson_slab", "warped_slab",
                "crimson_pressure_plate", "warped_pressure_plate", "crimson_fence", "warped_fence", "crimson_trapdoor", "warped_trapdoor",
                "crimson_fence_gate", "warped_fence_gate", "crimson_stairs", "warped_stairs", "crimson_door", "warped_door",
                "crimson_sign", "warped_sign", "crimson_wall_sign", "warped_wall_sign");
        names(Material.REPLACEABLE_FIREPROOF_PLANT,
                "warped_roots", "nether_sprouts", "crimson_roots");
        // Renamed blocks and the old water-cauldron state have identical source identities.
        names(Material.DIRT, "dirt_path");
        names(Material.REPLACEABLE_PLANT, "short_grass");
        names(Material.METAL, "water_cauldron");
    }
    public static Material material(BlockState state) { return MATERIALS.getOrDefault(state.getBlock(), Material.UNKNOWN); }
    public static void register(Block block, Material material) { MATERIALS.put(block, material); }
    public static boolean stone(BlockState state) { return material(state) == Material.STONE; }
    public static boolean glass(BlockState state) { return material(state) == Material.GLASS; }
    public static boolean hard(BlockState state) {
        return switch (material(state)) {
            case BUILDABLE_GLASS, ICE_SOLID, WOOD, NETHER_WOOD, GLASS, ICE, STONE, METAL, HEAVY_METAL -> true;
            case CLAY -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("infested");
            default -> false;
        };
    }
    private static void names(Material material, String... names) {
        for (var name : names) { var id = Identifier.withDefaultNamespace(name); if (BuiltInRegistries.BLOCK.containsKey(id)) register(BuiltInRegistries.BLOCK.getValue(id), material); }
    }
    private SourceEraMaterials() { }
}
