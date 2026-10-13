package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public LocalPlayer player;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenClientStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (player != null && TimeStopClient.isFrozen(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenClientContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (player != null && TimeStopClient.isFrozen(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenClientUseItem(CallbackInfo ci) {
        if (player != null && TimeStopClient.isFrozen(player)) {
            ci.cancel();
        }
    }
}
