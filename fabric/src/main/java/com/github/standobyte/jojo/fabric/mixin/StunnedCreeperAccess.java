package com.github.standobyte.jojo.fabric.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Original StunEffect resets the fuse itself, not merely its current swelling direction. */
@Mixin(Creeper.class)
public interface StunnedCreeperAccess {
    @Accessor("swell") void jojo$setSwell(int ticks);
}
