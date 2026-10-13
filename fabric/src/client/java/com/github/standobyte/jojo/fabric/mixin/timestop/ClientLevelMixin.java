package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Shadow
    private void tickPassenger(Entity vehicle, Entity passenger) {
        throw new AssertionError();
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void jojoCancelClientTickNonPassenger(Entity entity, CallbackInfo ci) {
        if (TimeStopClient.isFrozen(entity)) {
            for (Entity passenger : entity.getPassengers()) {
                tickPassenger(entity, passenger);
            }
            ci.cancel();
        }
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void jojoCancelClientTickPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        if (TimeStopClient.isFrozen(passenger)) {
            ci.cancel();
        }
    }
}
