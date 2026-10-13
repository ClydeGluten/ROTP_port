package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void jojoZeroFrozenInput(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && TimeStopClient.isFrozen(client.player)) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
        }
    }
}
