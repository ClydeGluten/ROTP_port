package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
public final class HamonProtectionLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    private final PlayerModel model=new PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(.2F),false),64,64).bakeRoot(),false);
    public HamonProtectionLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent){super(parent);}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch){
        var data=HamonPlayerAnimation.pose(state);if(data==null||!data.protection()||state.isInvisible)return;
        pose.pushPose();
        collector.submitModel(model,state,pose,RenderTypes.energySwirl(RipplesFabric.id("textures/entity/biped/hamon_protection.png"),data.age()*.01F,data.age()*.002F),0xF000F0,OverlayTexture.NO_OVERLAY,0x19262626);
        pose.popPose();
    }
    public static void submitHand(PoseStack pose,SubmitNodeCollector collector,ModelPart arm){
        var p=net.minecraft.client.Minecraft.getInstance().player;if(p==null||!HamonActionService.state(p).protection())return;
        collector.submitModelPart(arm,pose,RenderTypes.energySwirl(RipplesFabric.id("textures/entity/biped/hamon_protection.png"),p.tickCount*.01F,p.tickCount*.01F),0xF000F0,OverlayTexture.NO_OVERLAY,null,0x40262626);
    }
}
