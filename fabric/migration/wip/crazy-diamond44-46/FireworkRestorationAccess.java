package com.github.standobyte.jojo.fabric.mixin.restoration;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRestorationAccess {
    @Accessor("lifetime") void jojo$restorationLifetime(int ticks);
    @Accessor("DATA_ID_FIREWORKS_ITEM") static EntityDataAccessor<ItemStack> jojo$restorationFireworkItem() { throw new AssertionError(); }
}
