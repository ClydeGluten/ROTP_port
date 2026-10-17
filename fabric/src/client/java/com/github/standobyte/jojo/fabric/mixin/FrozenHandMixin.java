package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.FrozenLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class FrozenHandMixin {
    @Inject(method = "renderHand", at = @At("TAIL"))
    private void jojo$frozenHand(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
        com.github.standobyte.jojo.fabric.client.ZombieLayer.submitHand(pose, collector, arm, light);
        var model = (net.minecraft.client.model.player.PlayerModel)((AvatarRenderer)(Object)this).getModel();
        com.github.standobyte.jojo.fabric.client.pillarman.PillarmanPresentation.submitHand(pose, collector, arm, arm == model.leftArm ? model.leftSleeve : model.rightSleeve, light);
        com.github.standobyte.jojo.fabric.client.pillarman.PillarWindClient.submitHand(pose, collector, arm, arm == model.leftArm ? model.leftSleeve : model.rightSleeve, light);
        FrozenLayer.submitHand(pose, collector, light, arm);
        com.github.standobyte.jojo.fabric.client.HamonProtectionLayer.submitHand(pose, collector, arm);
    }
}
