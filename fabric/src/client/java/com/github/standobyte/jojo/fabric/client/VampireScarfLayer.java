package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.VampireHamon;
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

/** Original SatiporojaScarfArmorModel: three cubes, 32x32 texture, body-driven head-slot rotation. */
public final class VampireScarfLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final ModelPart scarf;
    public VampireScarfLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("scarf", CubeListBuilder.create()
                .texOffs(0, 7).addBox(-4.5F, -1.2F, -2.5F, 9, 1, 5)
                .texOffs(0, 0).addBox(-4.5F, 0, -2.6F, 9, 2, 5, new CubeDeformation(.2F))
                .texOffs(0, 13).addBox(-4.1F, -.5F, -3.5F, 3, 11, 1, new CubeDeformation(-.3F)),
                PartPose.offsetAndRotation(0, .5F, 0, .0873F, 0, 0));
        scarf = LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("scarf");
    }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        if (!state.headEquipment.is(VampireHamon.SCARF) || state.isSpectator) return;
        var body = getParentModel().body;
        scarf.xRot = body.xRot + .0873F; scarf.yRot = body.yRot; scarf.zRot = body.zRot;
        collector.submitModelPart(scarf, pose, RenderTypes.armorCutoutNoCullGlint(RipplesFabric.id("textures/armor/satiporoja_scarf.png")),
                light, OverlayTexture.NO_OVERLAY, null, -1, state.outlineColor);
    }
}
