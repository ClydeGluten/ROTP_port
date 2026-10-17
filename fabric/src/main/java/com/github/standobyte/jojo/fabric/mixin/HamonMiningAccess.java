package com.github.standobyte.jojo.fabric.mixin;

import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Original tickExercises reads the actual server block-destruction and delayed-destruction state. */
@Mixin(ServerPlayerGameMode.class)
public interface HamonMiningAccess {
    @Accessor("isDestroyingBlock") boolean jojo$hamonDestroying();
    @Accessor("hasDelayedDestroy") boolean jojo$hamonDelayedDestroy();
}
