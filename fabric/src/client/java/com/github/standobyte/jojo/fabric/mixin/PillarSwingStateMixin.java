package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.pillarman.PillarLightClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source blade barrage explicitly restarts the arm swing every two ticks. */
@Mixin(LivingEntity.SwingState.class)
abstract class PillarSwingStateMixin {
    @Shadow private void start(InteractionHand hand, SwingAnimation animation, int duration) { throw new AssertionError(); }
    @Inject(method = "startIfAble", at = @At("HEAD"), cancellable = true)
    private void jojo$forcedBladeSwing(InteractionHand hand, SwingAnimation animation, int duration, CallbackInfoReturnable<Boolean> cir) {
        if (PillarLightClient.forcingSwing()) { start(hand, animation, duration); cir.setReturnValue(true); }
    }
}
