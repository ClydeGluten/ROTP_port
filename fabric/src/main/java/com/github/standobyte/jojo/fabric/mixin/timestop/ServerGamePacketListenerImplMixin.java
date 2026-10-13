package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.TimeStopService;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Shadow
    public abstract void teleport(double x, double y, double z, float yRot, float xRot);

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenPlayerMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            ci.cancel();
        }
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenVehicleMovement(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenPlayerInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenPlayerAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePunch", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenPlayerPunch(ServerboundPunchPacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenPlayerInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenPlayerUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenPlayerUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePaddleBoat", at = @At("HEAD"), cancellable = true)
    private void jojoLockFrozenPlayerPaddleBoat(ServerboundPaddleBoatPacket packet, CallbackInfo ci) {
        if (player != null && player.level().getServer().isSameThread() && TimeStopService.isFrozen(player)) {
            ci.cancel();
        }
    }
}
