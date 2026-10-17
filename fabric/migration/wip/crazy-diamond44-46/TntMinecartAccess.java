package com.github.standobyte.jojo.fabric.mixin.restoration;

import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Original CommonReflection.setFuse, adapted to the pinned minecart field. */
@Mixin(MinecartTNT.class)
public interface TntMinecartAccess {
    @Accessor("fuse") void jojo$setRestorationFuse(int fuse);
}
