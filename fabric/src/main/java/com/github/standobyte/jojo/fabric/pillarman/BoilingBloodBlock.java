package com.github.standobyte.jojo.fabric.pillarman;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

/** Public constructor for the original lava-like liquid block; vanilla owns collision and buckets. */
public final class BoilingBloodBlock extends LiquidBlock {
    public BoilingBloodBlock(FlowingFluid fluid, Properties properties) { super(fluid, properties); }
}
