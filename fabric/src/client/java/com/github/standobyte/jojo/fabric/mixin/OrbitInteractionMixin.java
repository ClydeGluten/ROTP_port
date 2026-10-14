package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.StandCamera;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Minecraft.class, priority = 1100)
public abstract class OrbitInteractionMixin {
    /** Reuse vanilla picking/ranges/item rules without rotating the idle player's rendered body. */
    @WrapOperation(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult jojo$orbitPick(LocalPlayer player, float partialTick, Entity camera, Operation<HitResult> original) {
        if (camera != player || !StandCamera.update()) return original.call(player, partialTick, camera);
        float yaw = player.getYRot(), pitch = player.getXRot(), oldYaw = player.yRotO, oldPitch = player.xRotO;
        try {
            player.setYRot(StandCamera.viewYaw()); player.yRotO = player.getYRot();
            player.setXRot(StandCamera.viewPitch()); player.xRotO = player.getXRot();
            return original.call(player, partialTick, camera);
        } finally {
            player.setYRot(yaw); player.yRotO = oldYaw;
            player.setXRot(pitch); player.xRotO = oldPitch;
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void jojo$aimAttack(CallbackInfoReturnable<Boolean> result) { StandCamera.aimForAction(); }

    @Inject(method = "continueAttack", at = @At("HEAD"))
    private void jojo$aimBreaking(boolean down, CallbackInfo ci) { if (down) StandCamera.aimForAction(); }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void jojo$aimUse(CallbackInfo ci) { StandCamera.aimForAction(); }
}
