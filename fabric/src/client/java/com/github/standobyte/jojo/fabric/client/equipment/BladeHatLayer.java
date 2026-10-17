package com.github.standobyte.jojo.fabric.client.equipment;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.equipment.EquipmentContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class BladeHatLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private final ModelPart hat = BladeHatRenderer.model(true);
    public BladeHatLayer(RenderLayerParent<S, M> parent) { super(parent); }
    /** Call after vanilla humanoid extraction; custom head sprites must not duplicate the armor model. */
    public static void extract(net.minecraft.world.entity.LivingEntity entity, HumanoidRenderState state) {
        var helmet=entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (helmet.is(EquipmentContent.BLADE_HAT)) { state.headEquipment=helmet.copy(); state.headItem.clear(); }
    }
    public static void hideOuterHat(PlayerModel model, AvatarRenderState state) {
        if (state.headEquipment.is(EquipmentContent.BLADE_HAT)) model.hat.visible = false;
    }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yaw, float pitch) {
        if (!state.headEquipment.is(EquipmentContent.BLADE_HAT) || state instanceof AvatarRenderState avatar && avatar.isSpectator) return;
        pose.pushPose(); getParentModel().head.translateAndRotate(pose);
        var texture=RipplesFabric.id("textures/armor/blade_hat.png");
        var type=state.headEquipment.hasFoil()?RenderTypes.armorCutoutNoCullGlint(texture):RenderTypes.armorCutoutNoCull(texture);
        collector.submitModelPart(hat,pose,type,light,OverlayTexture.NO_OVERLAY,null,-1,state.outlineColor);
        pose.popPose();
    }
}
