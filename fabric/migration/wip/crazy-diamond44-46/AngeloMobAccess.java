package com.github.standobyte.jojo.fabric.mixin.restoration;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mob.class)
public interface AngeloMobAccess {
    @Invoker("getAmbientSound") SoundEvent jojo$ambientSound();
    @Invoker("playHurtSound") void jojo$playHurtSound(DamageSource source);
}
