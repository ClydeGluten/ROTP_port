package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.TimeStopService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void jojoCancelFrozenPlayerTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (TimeStopService.isFrozen(player)) {
            if (player.damageCooldownTime > 0) player.damageCooldownTime--;
            ci.cancel();
        }
    }
}
