package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.HungryZombieEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity's final modern method replaces the original HungryZombieEntity alliance override. */
@Mixin(Entity.class)
abstract class HungryZombieAllianceMixin {
    @Inject(method = "isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void jojo$ownerAlliance(Entity other, CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof HungryZombieEntity zombie) {
            Boolean allied = zombie.ownerAlliance(other);
            if (allied != null) result.setReturnValue(allied);
        }
    }
}
