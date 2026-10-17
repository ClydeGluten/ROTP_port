package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
/** The original BreathControlMaskModel's two 32x32 cubes. */
public final class HamonMaskLayer extends RenderLayer<AvatarRenderState,PlayerModel>{
    private final ModelPart mask;
    public HamonMaskLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent){super(parent);var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("mask",CubeListBuilder.create().texOffs(0,0).addBox(-4,-3,-4,8,3,3,new CubeDeformation(.4F)).texOffs(22,0).addBox(-1,-2,-5,2,2,1,new CubeDeformation(.6F)),PartPose.ZERO);mask=LayerDefinition.create(mesh,32,32).bakeRoot().getChild("mask");}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch){
        if(!state.headEquipment.is(HamonItems.BREATH_CONTROL_MASK)||state.isSpectator)return;
        pose.pushPose();getParentModel().head.translateAndRotate(pose);
        collector.submitModelPart(mask,pose,RenderTypes.armorCutoutNoCull(RipplesFabric.id("textures/armor/breath_control_mask.png")),light,OverlayTexture.NO_OVERLAY,null,-1,state.outlineColor);pose.popPose();
    }
}
