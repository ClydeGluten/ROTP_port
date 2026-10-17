package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.client.HamonPlayerAnimation;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlayerModel.class,priority=900)
abstract class HamonPlayerModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
    private void jojo$hamonAnimation(AvatarRenderState state,CallbackInfo ci){HamonPlayerAnimation.apply((PlayerModel)(Object)this,state);com.github.standobyte.jojo.fabric.client.equipment.BladeHatLayer.hideOuterHat((PlayerModel)(Object)this,state);com.github.standobyte.jojo.fabric.client.pillarman.PillarmanPresentation.apply((PlayerModel)(Object)this,state);}
}
