package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.pillarman.PillarLightActions;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Original FULL_INVISIBILITY cancels the entire living render, including worn/held layers. */
@Mixin(EntityRenderer.class)
abstract class PillarInvisibleRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void jojo$fullInvisibility(Entity entity, Frustum frustum, double x, double y, double z, float partialTick,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && PillarLightActions.hidden(living)) cir.setReturnValue(false);
    }
}
