package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.pillarman.PossessionClient;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpectatorGui.class)
abstract class PossessionSpectatorGuiMixin {
    @Inject(method = {"extractHotbar", "extractAction", "onHotbarSelected", "onMouseScrolled", "onHotbarActionKeyPressed"}, at = @At("HEAD"), cancellable = true)
    private void jojo$noSpectatorMenu(CallbackInfo ci) { if (PossessionClient.suppressSpectatorMenu()) ci.cancel(); }
}
