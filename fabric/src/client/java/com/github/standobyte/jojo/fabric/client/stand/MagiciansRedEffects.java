package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.stand.magiciansred.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import java.util.*;

/** Original four-sided FlameModelRenderer and detector/sustained-flame client sounds. */
public final class MagiciansRedEffects {
    public static final Identifier ATLAS=TextureAtlas.LOCATION_BLOCKS;
    private static final Set<Integer> DETECTORS=new HashSet<>();
    private static Object session;
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(session!=client.level){session=client.level;DETECTORS.clear();}
            if(client.level==null||client.isPaused())return;Set<Integer> active=new HashSet<>();
            for(var entity:client.level.entitiesForRendering()){
                if(entity instanceof MRDetectorEntity detector&&detector.detected()){
                    active.add(entity.getId());if(DETECTORS.add(entity.getId())&&canHear())client.getSoundManager().play(new DetectorSound(detector));
                }
                if(entity instanceof MagiciansRedEntity stand&&stand.task()==MagiciansRedEntity.Task.FLAME_BURST&&stand.actionPhase()==2&&!TimeStopClient.isFrozen(stand)&&canHear())
                    client.level.playLocalSound(stand.getX(),stand.getY(),stand.getZ(),JojoSounds.get("magicians_red_fire_ability"),stand.getSoundSource(),.5F,.3F+stand.getRandom().nextFloat()*.4F,false);
            }DETECTORS.retainAll(active);
        });
    }
    public static boolean canHear(){return StandPerception.canHearStand(Minecraft.getInstance().player);}
    private static final class DetectorSound extends AbstractTickableSoundInstance {
        private final MRDetectorEntity detector;
        DetectorSound(MRDetectorEntity detector){super(SoundEvents.FIRE_AMBIENT,detector.getSoundSource(),detector.getRandom());this.detector=detector;looping=true;delay=0;pitch=1;}
        @Override public void tick(){
            if(TimeStopClient.isFrozen(detector)){volume=0;return;}
            if(!detector.isAlive()||!detector.detected()||!canHear()){stop();return;}
            x=detector.getX();y=detector.getY();z=detector.getZ();volume=1-(float)detector.detectedDirection().length()/15*.5F;
        }
    }
    public static void flame(PoseStack pose,SubmitNodeCollector collector,float x,float y,float z,float width,float height,Direction direction,int tint,boolean body,boolean cutout){
        pose.pushPose();pose.translate(x/16,y/16,z/16);
        pose.last().normal().identity().scale(-1).rotateX(Minecraft.getInstance().gameRenderer.mainCamera().xRot()*(float)Math.PI/180);
        switch(direction){case UP->pose.rotateDegrees(Axis.XP,180);case DOWN->pose.last().normal().rotateX((float)Math.PI);case NORTH->pose.rotateDegrees(Axis.XP,-90);case SOUTH->pose.rotateDegrees(Axis.XP,90);case WEST->pose.rotateDegrees(Axis.ZP,-90);case EAST->pose.rotateDegrees(Axis.ZP,90);}
        var sprites=sprites(body);float scale=width/16*1.4F,remaining=height/16/scale;
        for(int face=0;face<4;face++){
            pose.pushPose();pose.scale(scale,scale,scale);pose.rotateDegrees(Axis.YP,face*90);pose.translate(0,0,-.4F+(int)remaining*.02F);
            strips(pose,collector,sprites,remaining,tint,cutout);pose.popPose();
        }pose.popPose();
    }
    private static TextureAtlasSprite[] sprites(boolean body){
        var atlas=Minecraft.getInstance().getAtlasManager();String path=body?"entity/stand/magicians_red_fire_":"block/mr_fire_";
        return new TextureAtlasSprite[]{atlas.get(new SpriteId(ATLAS,RipplesFabric.id(path+"0"))),atlas.get(new SpriteId(ATLAS,RipplesFabric.id(path+"1")))};
    }
    private static void strips(PoseStack pose,SubmitNodeCollector collector,TextureAtlasSprite[] sprites,float remaining,int tint,boolean cutout){
        collector.submitCustomGeometry(pose,cutout?RenderTypes.entityCutout(ATLAS):RenderTypes.entityTranslucentCull(ATLAS),(entry,vertices)->{
            float half=.5F,bottom=0,depth=0,left=remaining;for(int i=0;left>0;i++){
                var sprite=sprites[i%2];float u0=sprite.getU0(),u1=sprite.getU1();if(i/2%2==0){float swap=u0;u0=u1;u1=swap;}
                float[] xs={half,-half,-half,half},ys={-bottom,-bottom,1.4F-bottom,1.4F-bottom},us={u1,u0,u0,u1},vs={sprite.getV1(),sprite.getV1(),sprite.getV0(),sprite.getV0()};
                for(int v=0;v<4;v++)vertices.addVertex(entry.pose(),xs[v],ys[v],depth).setColor(tint).setUv(us[v],vs[v]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(entry,0,1,0);
                left-=.45F;bottom-=.45F;half*=.9F;depth+=.03F;
            }
        });
    }
    public static void detectorFlame(PoseStack pose,SubmitNodeCollector collector,Direction direction,float strength,float cameraYaw){
        if(strength<=0)return;var normal=direction.getUnitVec3();pose.pushPose();pose.translate(normal.x*.25,normal.y*.25,normal.z*.25);float scale=strength*.2F;pose.scale(scale,scale,scale);pose.rotateDegrees(Axis.YP,-cameraYaw);pose.translate(0,0,-.3F);
        strips(pose,collector,sprites(false),.5F,Math.round(strength*255)<<24|0xFFFFFF,false);pose.popPose();
    }
    private MagiciansRedEffects(){}
}
