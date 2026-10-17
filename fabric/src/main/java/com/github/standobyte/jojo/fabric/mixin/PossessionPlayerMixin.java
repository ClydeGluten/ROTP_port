package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.pillarman.PossessionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Original possession keeps the host camera and defers game mode changes until exit. */
@Mixin(ServerPlayer.class)
abstract class PossessionPlayerMixin {
    @Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
    private void jojo$hostCamera(Entity target, CallbackInfo ci) {
        if (PossessionService.cancelCameraChange((ServerPlayer)(Object)this, target)) ci.cancel();
    }
    @Inject(method = "setGameMode", at = @At("HEAD"), cancellable = true)
    private void jojo$returnMode(GameType mode, CallbackInfoReturnable<Boolean> cir) {
        if (PossessionService.cancelGameModeChange((ServerPlayer)(Object)this, mode)) cir.setReturnValue(false);
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at = @At("HEAD"), cancellable = true)
    private void jojo$hostLocation(CallbackInfoReturnable<Boolean> cir) {
        if (PossessionService.cancelTeleport((ServerPlayer)(Object)this)) cir.setReturnValue(false);
    }
}
