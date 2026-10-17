package com.github.standobyte.jojo.fabric.mixin;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractHorse.class)
public interface HamonHorseAccess { @Accessor("owner") void jojo$setHamonOwner(EntityReference<LivingEntity> owner); }
