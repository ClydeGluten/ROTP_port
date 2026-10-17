package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.StandConfig;
import com.github.standobyte.jojo.fabric.TimeStopService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Original highest-priority LivingHurt hook: before armor and resolve credit. Player overrides actuallyHurt. */
@Mixin({LivingEntity.class,Player.class})
abstract class FrozenDamageConfigMixin {
    @ModifyVariable(method="actuallyHurt",at=@At("HEAD"),argsOnly=true)
    private float jojo$frozenDamage(float amount){
        return TimeStopService.isFrozen((LivingEntity)(Object)this)?amount*StandConfig.server().timeStopDamageMultiplier():amount;
    }
}
