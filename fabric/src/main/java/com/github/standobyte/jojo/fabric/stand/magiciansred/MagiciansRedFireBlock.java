package com.github.standobyte.jojo.fabric.stand.magiciansred;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

/** Shared original fire block used by Chariot's fiery sweep and Magician's Red projectiles. */
public final class MagiciansRedFireBlock extends FireBlock {
    public static final MagiciansRedFireBlock BLOCK = Registry.register(BuiltInRegistries.BLOCK, RipplesFabric.id("magicians_red_fire"),
            new MagiciansRedFireBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FIRE).sound(SoundType.WOOL).lightLevel(state -> 15)
                    .setId(ResourceKey.create(Registries.BLOCK, RipplesFabric.id("magicians_red_fire")))));
    private static boolean initialized;
    public MagiciansRedFireBlock(BlockBehaviour.Properties properties) { super(properties); }
    public static void initialize() {
        if (initialized) return; initialized = true;
        var source = FlammableBlockRegistry.getDefaultInstance(); var target = FlammableBlockRegistry.getInstance(BLOCK);
        for (var block : BuiltInRegistries.BLOCK) {
            var entry = source.get(block); if (entry != null) target.add(block, entry.getIgniteOdds(), entry.getBurnOdds());
        }
    }
    @Override public BlockState getStateForPlacement(BlockGetter level, BlockPos pos) { return super.getStateForPlacement(level, pos); }
    /** FireBlock's modern private age helper delegates here through the shared root-owned mixin. */
    public BlockState stateWithAge(LevelReader level, BlockPos pos, int age) { return getStateForPlacement(level, pos).setValue(AGE, Math.clamp(age, 0, MAX_AGE)); }
}
