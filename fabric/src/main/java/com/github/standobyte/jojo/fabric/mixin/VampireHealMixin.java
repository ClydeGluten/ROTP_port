package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.VampireService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Forge LivingHealEvent adaptation: all actual vampire healing spends original difficulty-scaled blood. */
@Mixin(LivingEntity.class)
abstract class VampireHealMixin {
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float jojo$payVampireHealing(float amount) {
        var self=(LivingEntity)(Object)this;
        if(self.hasEffect(com.github.standobyte.jojo.fabric.VampireItems.SUN_BURN))return 0;
        return com.github.standobyte.jojo.fabric.SpeciesHooks.payHealing(self,com.github.standobyte.jojo.fabric.VampireHamon.reduceHealing(self,amount));
    }
}
