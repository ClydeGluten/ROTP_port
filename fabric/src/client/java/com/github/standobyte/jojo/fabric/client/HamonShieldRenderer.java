package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
public final class HamonShieldRenderer extends EntityRenderer<HamonProjectileShieldEntity,HamonShieldRenderer.State>{
    public static final class State extends EntityRenderState {float yaw,pitch,width,height;}
    public HamonShieldRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(HamonProjectileShieldEntity entity,State s,float partial){super.extractRenderState(entity,s,partial);s.yaw=entity.getYRot();s.pitch=entity.getXRot();s.width=entity.width();s.height=entity.height();}
    @Override public void submit(State s,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(0,s.height/2,0);pose.rotateDegrees(Axis.YP,-s.yaw);pose.rotateDegrees(Axis.XP,s.pitch);
        collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(RipplesFabric.id("textures/entity/projectile_shield.png")),(p,v)->{
            float x=s.width/2,y=s.height/2;
            vertex(p,v,x,-y,1,1);vertex(p,v,-x,-y,0,1);vertex(p,v,-x,y,0,0);vertex(p,v,x,y,1,0);
            vertex(p,v,-x,-y,1,1);vertex(p,v,x,-y,0,1);vertex(p,v,x,y,0,0);vertex(p,v,-x,y,1,0);
        });pose.popPose();
    }
    private static void vertex(PoseStack.Pose p,VertexConsumer v,float x,float y,float u,float t){v.addVertex(p,x,y,0).setColor(128,128,128,255).setUv(u,t).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(p,0,0,1);}
}
