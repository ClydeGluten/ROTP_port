package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.equipment.BladeHatLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CustomModelArmorItem applies to players, humanoid mobs and armor stands. */
@Mixin(LivingEntityRenderer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
abstract class EquipmentArmorLayerMixin {
    @Shadow protected abstract boolean addLayer(RenderLayer layer);
    @Inject(method="<init>",at=@At("TAIL"))
    private void jojo$bladeHatLayer(EntityRendererProvider.Context context, EntityModel model, float shadow, CallbackInfo ci) {
        if (model instanceof HumanoidModel) {
            addLayer(new BladeHatLayer((LivingEntityRenderer)(Object)this));
            addLayer(new com.github.standobyte.jojo.fabric.client.pillarman.PillarmanLayers((LivingEntityRenderer)(Object)this));
        }
    }
}
