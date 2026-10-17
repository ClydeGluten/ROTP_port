package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.stand.HierophantGreenRenderer;
import com.github.standobyte.jojo.fabric.client.stand.SilverChariotRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** ControllerStand.renderStandHands: submit the controlled Stand's own model in the hand render pass. */
@Mixin(GameRenderer.class)
abstract class StandFirstPersonHandsMixin {
    @WrapOperation(method="renderItemInHand",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"))
    private void jojo$standHands(FirstPersonHandsAndItemsRenderer renderer,float partial,PoseStack pose,SubmitNodeCollector collector,
            PlayerRenderState playerState,FirstPersonHandsAndItemsRenderState hands,Operation<Void> original,
            CameraRenderState camera,PlayerRenderState player,GpuTextureView target){
        if(!SilverChariotRenderer.submitFirstPerson(pose,collector,camera,partial)
                &&!HierophantGreenRenderer.submitFirstPerson(pose,collector,camera,partial)
                &&!com.github.standobyte.jojo.fabric.client.stand.MagiciansRedRenderer.submitFirstPerson(pose,collector,camera,partial))
            original.call(renderer,partial,pose,collector,playerState,hands);
    }
}
