package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.StandAttachments;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.platform.InputConstants;

/** Original O remote control shares vanilla's newer Friends key only while controlling an owned Stand. */
@Mixin(Minecraft.class)
public abstract class StandRemoteKeyMixin {
    @Inject(method = "handleGlobalKeyPress", at = @At("HEAD"), cancellable = true)
    private void jojo$remoteBeforeFriends(InputConstants.Key pressed, boolean alternate, CallbackInfoReturnable<Boolean> cir) {
        var client = (Minecraft) (Object) this;
        var remote = KeyMapping.get("key.jojo.fabric.control");
        if (client.player != null && client.gui.screen() == null
                && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()
                && remote != null && remote.matches(pressed) && remote.same(client.options.keyFriends)) {
            // Return false so KeyboardHandler continues dispatching the ordinary held key to the Stand mapping.
            cir.setReturnValue(false);
        }
    }
}
