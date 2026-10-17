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
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void jojo$hamonDetector(net.minecraft.world.entity.Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (com.github.standobyte.jojo.fabric.client.pillarman.PillarmanPresentation.glowing(entity)
                || com.github.standobyte.jojo.fabric.client.stand.HierophantProjectileRenderer.outline(entity)) { cir.setReturnValue(true); return; }
        var player = Minecraft.getInstance().player;
        if (player != null && com.github.standobyte.jojo.fabric.HamonActionService.state(player).detected().contains(entity.getId())) cir.setReturnValue(true);
    }
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void jojo$selectNumberedAction(CallbackInfo ci) {
        StandSelection.consumeHotbarKeys();
    }
    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void jojo$hamonBreath(CallbackInfo ci) {
        if (com.github.standobyte.jojo.fabric.client.HamonClient.interceptQuickBreath()
                || com.github.standobyte.jojo.fabric.client.ZombieClient.interceptMiddle()
                || com.github.standobyte.jojo.fabric.client.pillarman.PillarmanClient.interceptMiddle()) ci.cancel();
    }
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void jojo$selectedAttack(CallbackInfoReturnable<Boolean> cir) {
        if (com.github.standobyte.jojo.fabric.client.HamonClient.inputBlocked() || StandSelection.interceptClick(true)) cir.setReturnValue(false);
    }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void jojo$selectedAttackHeld(boolean down, CallbackInfo ci) {
        if (com.github.standobyte.jojo.fabric.client.HamonClient.inputBlocked() || StandSelection.capturesAttackHeld()) ci.cancel();
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void jojo$selectedUse(CallbackInfo ci) {
        if (com.github.standobyte.jojo.fabric.client.HamonClient.inputBlocked() || StandSelection.interceptClick(false)) ci.cancel();
    }
}
