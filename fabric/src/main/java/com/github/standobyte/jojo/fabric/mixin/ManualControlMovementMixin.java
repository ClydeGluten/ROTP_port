package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.StandService;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ManualControlMovementMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void jojo$anchorBody(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // Packet handlers first run on the network thread; let vanilla reschedule there.
        if (!player.level().getServer().isSameThread()) return;
        var stand = StandService.current(player);
        if ((stand != null && stand.manuallyControlled()) || com.github.standobyte.jojo.fabric.TimeStopService.isCharging(player)) ci.cancel();
    }
}
