package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StoneMaskItem;
import com.github.standobyte.jojo.fabric.VampireItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** StoneMaskModel's original 32x32 texture, cube bounds and 0.55 deformation. */
public final class StoneMaskLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final ModelPart mask;

    public StoneMaskLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("mask", CubeListBuilder.create().texOffs(2, 2)
                .addBox(-4, -8, -4, 8, 8, 6, new CubeDeformation(0.55F)), PartPose.ZERO);
        mask = LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("mask");
    }

    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
                                 AvatarRenderState state, float yaw, float pitch) {
        if (!(state.headEquipment.getItem() instanceof StoneMaskItem) || state.isSpectator) return;
        boolean activated = StoneMaskItem.activationTicks(state.headEquipment) > 0;
        String name = state.headEquipment.is(VampireItems.AJA_STONE_MASK) ? "aja_stone_mask" : "stone_mask";
        var texture = RipplesFabric.id("textures/armor/" + name + (activated ? "_activated" : "") + ".png");
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        collector.submitModelPart(mask, pose, RenderTypes.armorCutoutNoCull(texture), light,
                OverlayTexture.NO_OVERLAY, null, -1, state.outlineColor);
        pose.popPose();
    }
}
