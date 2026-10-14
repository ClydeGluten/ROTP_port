package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @ModifyVariable(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("HEAD"), argsOnly = true)
    private float jojoFreezeRenderInterpolation(float partialTick, T entity, float originalPartialTick) {
        if (TimeStopClient.isFrozen(entity)) {
            // Knives finish their grace flight before freezing. Their old coordinates still
            // describe the preceding flight tick; render the authoritative final endpoint.
            return entity instanceof com.github.standobyte.jojo.fabric.KnifeEntity ? 1.0F : 0.0F;
        }
        return partialTick;
    }
}
