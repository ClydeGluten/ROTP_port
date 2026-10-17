package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.FrozenLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class FrozenLivingRendererMixin {
    @Shadow protected abstract boolean addLayer(RenderLayer layer);
    @Inject(method = "<init>", at = @At("TAIL"))
    private void jojo$addFreeze(EntityRendererProvider.Context context, EntityModel model, float shadow, CallbackInfo ci) {
        addLayer(new FrozenLayer((RenderLayerParent) this));
    }
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void jojo$freezeState(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        FrozenLayer.extract(entity, state);
    }
}
