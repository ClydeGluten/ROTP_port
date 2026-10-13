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

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void jojo$shoulder(float partialTick, CallbackInfo ci) {
        var pose = StandCamera.shoulder((Camera) (Object) this, partialTick);
        if (pose != null) {
            setPosition(pose.position());
            setRotation(pose.yaw(), pose.pitch());
        }
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void jojo$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() / StandView.zoom());
    }
}
