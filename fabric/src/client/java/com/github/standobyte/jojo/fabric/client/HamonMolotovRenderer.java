package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.MolotovEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
/** Original item sprite and its narrowing, alternating flame quads; rain/water extinguish the visual. */
final class HamonMolotovRenderer extends ThrownItemRenderer<MolotovEntity> {
    private static final Identifier ATLAS=Identifier.withDefaultNamespace("textures/atlas/blocks.png");
    static final class State extends ThrownItemRenderState {boolean burning;}
    HamonMolotovRenderer(EntityRendererProvider.Context context){super(context,1F,true);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(MolotovEntity entity,ThrownItemRenderState state,float partial){super.extractRenderState(entity,state,partial);((State)state).burning=!entity.isInWaterOrRain();}
    @Override public void submit(ThrownItemRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.last().normal().identity().rotateX((float)Math.PI/2).rotateY(camera.yRot*(float)Math.PI/180);super.submit(state,pose,collector,camera);pose.popPose();
        if(!((State)state).burning)return;
        float width=state.boundingBoxWidth,height=state.boundingBoxHeight/Math.max(.001F,width);
        pose.pushPose();pose.scale(width,width,width);pose.rotateDegrees(Axis.YP,-camera.yRot);pose.translate(0,.5,-.3+height*.02);
        var atlas=Minecraft.getInstance().getAtlasManager();
        var sprites=new net.minecraft.client.renderer.texture.TextureAtlasSprite[]{atlas.get(new SpriteId(ATLAS,Identifier.withDefaultNamespace("block/fire_0"))),atlas.get(new SpriteId(ATLAS,Identifier.withDefaultNamespace("block/fire_1")))};
        collector.submitCustomGeometry(pose,RenderTypes.entityCutout(ATLAS),(entry,vertices)->{
            float half=.5F,bottom=0,z=0,remaining=height;
            for(int i=0;remaining>0;i++){
                var sprite=sprites[i%2];float u0=sprite.getU0(),u1=sprite.getU1(),v0=sprite.getV0(),v1=sprite.getV1();
                if(i/2%2==0){float swap=u1;u1=u0;u0=swap;}
                float[] xs={half,-half,-half,half},ys={-bottom,-bottom,1.4F-bottom,1.4F-bottom},us={u1,u0,u0,u1},vs={v1,v1,v0,v0};
                for(int j=0;j<4;j++)vertices.addVertex(entry.pose(),xs[j],ys[j],z).setColor(-1).setUv(us[j],vs[j]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(240).setNormal(entry,0,1,0);
                remaining-=.45F;bottom-=.45F;half*=.9F;z+=.03F;
            }
        });pose.popPose();
    }
}
