package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.StandSelection;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class StandSelectionReleaseMixin {
    @Inject(method = "set", at = @At("HEAD"))
    private static void jojo$releaseAction(InputConstants.Key key, boolean down, CallbackInfo ci) {
        if (!down) StandSelection.releaseMappedKey(key);
    }
}
