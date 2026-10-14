package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.StandView;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class StandInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void jojo$anchorInput(CallbackInfo ci) {
        if (StandView.bodyLocked()) { keyPresses = Input.EMPTY; moveVector = Vec2.ZERO; }
        else moveVector = com.github.standobyte.jojo.fabric.client.StandCamera.movement(moveVector);
    }
}
