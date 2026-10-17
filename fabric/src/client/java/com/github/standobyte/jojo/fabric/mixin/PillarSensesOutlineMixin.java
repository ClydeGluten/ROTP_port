package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanPresentation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class PillarSensesOutlineMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void jojo$sourceOutline(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        PillarmanPresentation.applyOutline(entity, state);
    }
}
