package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.StandMovement;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Original action slowdown also disables sprinting, including double-tap activation. */
@Mixin(LocalPlayer.class)
public abstract class StandSprintMixin {
    @Inject(method = "isSprintingPossible", at = @At("HEAD"), cancellable = true)
    private void jojo$restrictSprint(boolean flying, CallbackInfoReturnable<Boolean> result) {
        var speed = ((LocalPlayer) (Object) this).getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(StandMovement.MODIFIER_ID)) result.setReturnValue(false);
    }
}
