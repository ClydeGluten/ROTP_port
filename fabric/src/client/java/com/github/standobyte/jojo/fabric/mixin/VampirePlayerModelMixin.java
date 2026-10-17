package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.VampireClawAnimation;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class VampirePlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void jojo$clawPose(AvatarRenderState state, CallbackInfo ci) {
        VampireClawAnimation.apply((PlayerModel) (Object) this, state);
        com.github.standobyte.jojo.fabric.client.ZombieLayer.applyAnimation((PlayerModel) (Object) this, state);
    }
}
