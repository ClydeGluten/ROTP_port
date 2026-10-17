package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.client.model.SilverChariotModel;
import com.github.standobyte.jojo.fabric.stand.StandAfterimageEntity;
import com.github.standobyte.jojo.fabric.stand.silverchariot.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Source armor, glow, tapered atlas flames, launched rapier and delayed-origin rendering. */
public final class SilverChariotLayers extends RenderLayer<SilverChariotRenderer.State,SilverChariotModel> {
    public static final Identifier ARMOR=RipplesFabric.id("textures/entity/stand/silver_chariot_armor.png"),
        BODY_GLOW=RipplesFabric.id("textures/entity/stand/glow/silver_chariot.png"),ARMOR_GLOW=RipplesFabric.id("textures/entity/stand/glow/silver_chariot_armor.png");
    private static final Identifier ATLAS=TextureAtlas.LOCATION_BLOCKS;
    private final SilverChariotModel armor=new SilverChariotModel(true),swingBody=new SilverChariotModel(false);
    public SilverChariotLayers(SilverChariotRenderer renderer){super(renderer);}
    private static RenderType type(Identifier texture,float alpha){return alpha<1?RenderTypes.entityTranslucentCull(texture):RenderTypes.entityCutout(texture);}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,SilverChariotRenderer.State state,float yaw,float pitch){
        if(state.isInvisible||state.cameraOpacity<=0)return;
        getParentModel().setupAnim(state);
        if(state.bodyGlow)collector.submitModelPart(getParentModel().root(),pose,RenderTypes.eyes(BODY_GLOW),0xF000F0,OverlayTexture.NO_OVERLAY,null,state.tint(),state.outlineColor);
        if(state.armor){
            armor.setupAnim(state);collector.submitModelPart(armor.root(),pose,type(ARMOR,state.cameraOpacity),light,OverlayTexture.NO_OVERLAY,null,state.tint(),state.outlineColor);
            if(state.armorGlow)collector.submitModelPart(armor.root(),pose,RenderTypes.eyes(ARMOR_GLOW),0xF000F0,OverlayTexture.NO_OVERLAY,null,state.tint(),state.outlineColor);
        }
        if(state.rapierFire&&state.rapier)rapierFire(pose,collector,getParentModel(),state.tint());
        if(!getParentModel().part("leftArm").visible||!getParentModel().part("rightArm").visible)return;
        for(var swing:state.swings){
            float fraction=swing.time()/2,pitchRad=state.xRot*Mth.DEG_TO_RAD;
            var offset=new Vec3(swing.x(),-swing.y(),swing.z()+.5-Math.abs(.5-fraction)).xRot(pitchRad);
            pose.pushPose();pose.translate(offset.x,offset.y,-offset.z);
            int tint=Math.round(state.cameraOpacity*.75F*255)<<24|0xFFFFFF;
            swingBody.setupAnim(state);SilverChariotAnimation.barrage(swingBody,fraction,pitchRad,0);swingBody.onlyArm(state.mainArmLeft);
            collector.submitModelPart(swingBody.root(),pose,RenderTypes.entityTranslucentCull(SilverChariotRenderer.TEXTURE),light,OverlayTexture.NO_OVERLAY,null,tint,state.outlineColor);
            if(state.bodyGlow)collector.submitModelPart(swingBody.root(),pose,RenderTypes.eyes(BODY_GLOW),0xF000F0,OverlayTexture.NO_OVERLAY,null,tint,state.outlineColor);
            // Source armor.isLayerModel suppresses additional barrage arms; body/glow/flame retain them.
            if(state.rapierFire&&state.rapier)rapierFire(pose,collector,swingBody,tint);
            pose.popPose();
        }
    }
    private static void rapierFire(PoseStack pose,SubmitNodeCollector collector,SilverChariotModel model,int tint){
        if(!model.part("rightArm").visible)return;
        pose.pushPose();var part=model.root();part.translateAndRotate(pose);
        for(String name:new String[]{"root","body","upperPart","rightArm","rightForeArm","rapier"}){
            part=part.getChild(name);part.translateAndRotate(pose);
        }
        for(int i=0;i<8;i++)flame(pose,collector,0,0,-3-i*1.5F,1,3,tint);
        pose.popPose();
    }
    /** FlameModelRenderer: eight firelets, each four faces, alternating atlas sprites and shrinking strips. */
    private static void flame(PoseStack pose,SubmitNodeCollector collector,float x,float y,float z,float width,float height,int tint){
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.rotateDegrees(Axis.XP,-90);
        var atlas=Minecraft.getInstance().getAtlasManager();
        var sprites=new TextureAtlasSprite[]{atlas.get(new SpriteId(ATLAS,Identifier.withDefaultNamespace("block/fire_0"))),atlas.get(new SpriteId(ATLAS,Identifier.withDefaultNamespace("block/fire_1")))};
        float scale=width/16*1.4F,remaining=height/16/scale;
        for(int face=0;face<4;face++){
            pose.pushPose();pose.scale(scale,scale,scale);pose.rotateDegrees(Axis.YP,face*90);pose.translate(0,0,-.4F+(int)remaining*.02F);
            collector.submitCustomGeometry(pose,RenderTypes.entityTranslucentCull(ATLAS),(entry,vertices)->{
                float half=.5F,bottom=0,depth=0,left=remaining;
                for(int i=0;left>0;i++){
                    var sprite=sprites[i%2];float u0=sprite.getU0(),u1=sprite.getU1();
                    if(i/2%2==0){float swap=u0;u0=u1;u1=swap;}
                    float[] xs={half,-half,-half,half},ys={-bottom,-bottom,1.4F-bottom,1.4F-bottom},us={u1,u0,u0,u1},vs={sprite.getV1(),sprite.getV1(),sprite.getV0(),sprite.getV0()};
                    for(int v=0;v<4;v++)vertices.addVertex(entry.pose(),xs[v],ys[v],depth).setColor(tint).setUv(us[v],vs[v]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(entry,0,1,0);
                    left-=.45F;bottom-=.45F;half*=.9F;depth+=.03F;
                }
            });pose.popPose();
        }
        pose.popPose();
    }
    public static final class RapierRenderer extends EntityRenderer<SilverChariotRapierEntity,RapierRenderer.State>{
        public static final class State extends EntityRenderState{float yaw,pitch;boolean burning,visible;}
        private final ModelPart rapier;
        public RapierRenderer(EntityRendererProvider.Context context){
            super(context);var mesh=new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("rapier",CubeListBuilder.create().texOffs(32,72).addBox(-.5F,-1.5F,-.5F,1,1,15,new CubeDeformation(-.3F)),PartPose.offset(-.5F,0,0));
            rapier=LayerDefinition.create(mesh,128,128).bakeRoot().getChild("rapier");
        }
        @Override public State createRenderState(){return new State();}
        @Override public void extractRenderState(SilverChariotRapierEntity entity,State state,float partial){
            super.extractRenderState(entity,state,partial);state.yaw=Mth.rotLerp(partial,entity.yRotO,entity.getYRot());state.pitch=Mth.lerp(partial,entity.xRotO,entity.getXRot());state.burning=entity.isOnFire();
            var client=Minecraft.getInstance();state.visible=!entity.isInvisible()||!entity.isInvisibleTo(client.player);
            if(entity.getOwner() instanceof SilverChariotEntity stand&&client.player!=null&&stand.ownerId()==client.player.getId())state.outlineColor=0xFF000000|entity.getTeamColor();
        }
        @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
            if(!state.visible)return;
            pose.pushPose();pose.scale(1,-1,-1);rapier.yRot=state.yaw*Mth.DEG_TO_RAD;rapier.xRot=state.pitch*Mth.DEG_TO_RAD;
            collector.submitModelPart(rapier,pose,RenderTypes.entityCutout(SilverChariotRenderer.TEXTURE),state.lightCoords,OverlayTexture.NO_OVERLAY,null,-1,state.outlineColor);
            if(state.burning){pose.pushPose();rapier.translateAndRotate(pose);for(int i=0;i<8;i++)flame(pose,collector,0,-1,3+i*1.5F,1,3,-1);pose.popPose();}
            pose.popPose();super.submit(state,pose,collector,camera);
        }
    }
    public static final class FlameRenderer extends EntityRenderer<SilverChariotFlameSwingEntity,FlameRenderer.State>{
        public static final class State extends EntityRenderState{Vec3 start=Vec3.ZERO;ClientLevelReference level;boolean visible;}
        private record ClientLevelReference(net.minecraft.client.multiplayer.ClientLevel value) { }
        public FlameRenderer(EntityRendererProvider.Context context){super(context);}
        @Override public State createRenderState(){return new State();}
        @Override public void extractRenderState(SilverChariotFlameSwingEntity entity,State state,float partial){
            super.extractRenderState(entity,state,partial);state.start=entity.getStartingPos();state.level=new ClientLevelReference((net.minecraft.client.multiplayer.ClientLevel)entity.level());
            state.visible=!entity.isInvisible()||!entity.isInvisibleTo(Minecraft.getInstance().player);
        }
        @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
            if(!state.visible)return;Vec3 position=new Vec3(state.x,state.y,state.z),distance=state.start.subtract(position),step=distance.normalize().scale(.4);
            for(int i=(int)(distance.length()/.4);i>0;i--){state.level.value.addParticle(SilverChariotActions.FLAME_PARTICLE,true,true,position.x,position.y,position.z,0,0,0);position=position.add(step);}
        }
    }
}
