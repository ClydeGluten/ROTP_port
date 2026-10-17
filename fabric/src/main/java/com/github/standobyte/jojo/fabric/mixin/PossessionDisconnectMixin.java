package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.pillarman.PossessionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restore live possession before logout; retain original trapped-rock state across rejoin. */
@Mixin(PlayerList.class)
abstract class PossessionDisconnectMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void jojo$exitBeforeSave(ServerPlayer player, CallbackInfo ci) { PossessionService.onDisconnect(player); }
}
