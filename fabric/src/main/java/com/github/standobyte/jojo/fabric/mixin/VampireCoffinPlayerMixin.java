package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.VampireLifecycle;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original PlayerUtilCap.tickCoffinSleepTimer: respawning at night does not immediately skip time. */
@Mixin(Player.class)
abstract class VampireCoffinPlayerMixin {
    @Shadow private int sleepCounter;
    @Inject(method = "tick", at = @At("TAIL"))
    private void jojo$holdCoffinRespawn(CallbackInfo ci) {
        if (VampireLifecycle.preventRespawnTimeSkip((Player) (Object) this)) sleepCounter = 0;
    }
}
