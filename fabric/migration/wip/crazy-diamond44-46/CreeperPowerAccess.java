package com.github.standobyte.jojo.fabric.mixin.restoration;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Original CommonReflection.getCreeperPoweredParameter. */
@Mixin(Creeper.class)
public interface CreeperPowerAccess {
    @Accessor("DATA_IS_POWERED") static EntityDataAccessor<Boolean> jojo$restorationPoweredData() {
        throw new AssertionError("Mixin accessor was not transformed");
    }
}
