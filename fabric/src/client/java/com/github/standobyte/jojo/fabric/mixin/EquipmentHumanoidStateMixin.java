package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.equipment.BladeHatLayer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidMobRenderer.class)
abstract class EquipmentHumanoidStateMixin {
    @Inject(method="extractHumanoidRenderState",at=@At("TAIL"))
    private static void jojo$bladeHatState(LivingEntity entity, HumanoidRenderState state, float partial, ItemModelResolver items, CallbackInfo ci) {
        BladeHatLayer.extract(entity, state);
        com.github.standobyte.jojo.fabric.client.pillarman.PillarmanLayers.extract(entity, state);
    }
}
