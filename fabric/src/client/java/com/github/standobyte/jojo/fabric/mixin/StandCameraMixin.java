package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.StandView;
import com.github.standobyte.jojo.fabric.client.StandCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class StandCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void jojo$cameraLifecycle(float partialTick, CallbackInfo ci) {
        StandCamera.update();
    }

    // Set the independent angles before vanilla mirrors front view and clips the camera against walls.
    @Inject(method = "alignWithEntity", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;detached:Z",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void jojo$orbit(float partialTick, CallbackInfo ci) {
        if (StandCamera.update()) setRotation(StandCamera.yaw(), StandCamera.pitch());
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void jojo$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() / StandView.zoom());
    }
}
