package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.StandSelection;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class StandSelectionInputMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void jojo$selectNumberedAction(CallbackInfo ci) {
        StandSelection.consumeHotbarKeys();
    }
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void jojo$selectedAttack(CallbackInfoReturnable<Boolean> cir) {
        if (StandSelection.interceptClick(true)) cir.setReturnValue(false);
    }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void jojo$selectedAttackHeld(boolean down, CallbackInfo ci) {
        if (StandSelection.capturesAttackHeld()) ci.cancel();
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void jojo$selectedUse(CallbackInfo ci) {
        if (StandSelection.interceptClick(false)) ci.cancel();
    }
}
