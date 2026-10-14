package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.StandSelection;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class StandSelectionScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void jojo$cycle(long window, double x, double y, CallbackInfo ci) {
        if (y != 0 && StandSelection.capturesScroll()) { StandSelection.cycle(y); ci.cancel(); }
    }
}
