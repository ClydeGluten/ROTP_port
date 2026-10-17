package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.client.*;
import com.github.standobyte.jojo.fabric.client.model.MagiciansRedModel;
import com.github.standobyte.jojo.fabric.stand.magiciansred.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Exact MR body with independently animated original fire sprites and optional source glow. */
public final class MagiciansRedRenderer extends MobRenderer<MagiciansRedEntity,MagiciansRedRenderer.State,MagiciansRedModel> {
    public static final Identifier TEXTURE=RipplesFabric.id("textures/entity/stand/magicians_red.png"),GLOW=RipplesFabric.id("textures/entity/stand/glow/magicians_red.png");
    private final Map<MagiciansRedEntity,Float> idleStarts=new WeakHashMap<>();
    private static boolean initialized;
    public static void initialize(){
        if(initialized)return;initialized=true;EntityRendererRegistry.register(MagiciansRedActions.STAND,MagiciansRedRenderer::new);
        MagiciansRedProjectileRenderer.initialize();MagiciansRedEffects.initialize();
        ActionCatalog.registerReplacement(MagiciansRedActions.ID,(client,action)->{
            if(!action.equals(MagiciansRedActions.HEAVY)||client.player==null||!StandParts.has(client.player,StandParts.Part.LEGS))return action;
            if(!StandParts.has(client.player,StandParts.Part.ARMS))return MagiciansRedActions.KICK;
            return StandView.ownManifestation(client) instanceof MagiciansRedEntity stand&&(stand.task()==MagiciansRedEntity.Task.KICK||stand.finisherCharge()>=.5F)&&StandActionTraining.unlocked(client.player,MagiciansRedActions.KICK)?MagiciansRedActions.KICK:action;
        });
        ActionCatalog.registerHeldReplacement(MagiciansRedActions.ID,(next,active)->active.id().equals(MagiciansRedActions.FLAME_BURST.toString())||active.id().equals(MagiciansRedActions.BIND.toString())||active.id().equals(MagiciansRedActions.BLOCK.toString()));
        ActionCatalog.registerAlongsideHeld(MagiciansRedActions.ID,(next,active)->next.id().equals(MagiciansRedActions.DETECTOR.toString())&&(active.id().equals(MagiciansRedActions.FLAME_BURST.toString())||active.id().equals(MagiciansRedActions.BIND.toString())||active.id().equals(MagiciansRedActions.BLOCK.toString())));
    }
    public static final class State extends LivingEntityRenderState {
        public MagiciansRedEntity.Task task=MagiciansRedEntity.Task.NONE;
        public int actionPhase,actionDuration,summonMode,summonVariant;
        public float actionTicks,idleTicks,opacity=1;
        public boolean summonAnimation,leftPunch,mainArmLeft,firstPerson,bodyFlame,glow,idleSway,hasArms=true,hasLegs=true,hasBody=true;
        public int tint(){return Math.round(opacity*255)<<24|0xFFFFFF;}
    }
    public MagiciansRedRenderer(EntityRendererProvider.Context context){super(context,new MagiciansRedModel(),0);addLayer(new Flames(this));}
    @Override public State createRenderState(){return new State();}
    @Override public Identifier getTextureLocation(State state){return TEXTURE;}
    @Override protected int getModelTint(State state){return state.tint();}
    @Override protected RenderType getRenderType(State state,boolean visible,boolean translucent,boolean glowing){if(state.opacity<=0)return null;if(state.opacity<1&&(visible||translucent))return RenderTypes.entityTranslucentCull(TEXTURE);return super.getRenderType(state,visible,translucent,glowing);}
    @Override protected boolean shouldRenderLayers(State state){return state.opacity>0&&super.shouldRenderLayers(state);}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){pose.pushPose();if(state.idleSway)pose.translate(0,Math.sin(state.idleTicks*.04F)*.04F,0);super.submit(state,pose,collector,camera);pose.popPose();}
    @Override public void extractRenderState(MagiciansRedEntity entity,State state,float partial){
        if(TimeStopClient.isFrozen(entity))partial=0;super.extractRenderState(entity,state,partial);state.isInvisible=entity.isInvisibleTo(Minecraft.getInstance().player);
        state.ageInTicks=entity.visualAge()+partial;state.task=entity.task();state.actionPhase=entity.actionPhase();state.actionDuration=entity.actionDuration();state.actionTicks=entity.actionTicks()+partial;
        state.summonMode=entity.summonMode();state.summonVariant=entity.summonVariant();state.summonAnimation=entity.summonAnimation();state.leftPunch=entity.leftPunch();state.mainArmLeft=entity.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT;state.firstPerson=false;
        state.bodyFlame=!entity.isInWaterOrRain();state.opacity=StandCamera.opacity(entity)*entity.renderAlpha();state.glow=Minecraft.getInstance().getResourceManager().getResource(GLOW).isPresent();
        var user=entity.user();state.hasArms=user==null||StandParts.has(user,StandParts.Part.ARMS);state.hasLegs=user==null||StandParts.has(user,StandParts.Part.LEGS);state.hasBody=user==null||StandParts.has(user,StandParts.Part.MAIN_BODY);
        if(state.task!=MagiciansRedEntity.Task.NONE||state.summonAnimation&&state.ageInTicks<=20)idleStarts.put(entity,state.ageInTicks);state.idleTicks=state.ageInTicks-idleStarts.computeIfAbsent(entity,e->state.ageInTicks);
        state.idleSway=state.task==MagiciansRedEntity.Task.NONE&&!state.summonAnimation&&!entity.manuallyControlled()&&(user==null||!user.isShiftKeyDown());
    }
    public static boolean submitFirstPerson(PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,float partial){
        var client=Minecraft.getInstance();if(!client.options.getCameraType().isFirstPerson()||!(StandView.ownManifestation(client) instanceof MagiciansRedEntity stand)||!stand.manuallyControlled()||client.gameRenderer.mainCamera().entity()!=stand)return false;
        if(!(client.getEntityRenderDispatcher().getRenderer(stand) instanceof MagiciansRedRenderer renderer))return false;var state=renderer.createRenderState();renderer.extractRenderState(stand,state,partial);state.firstPerson=true;state.opacity=stand.renderAlpha();
        pose.pushPose();pose.rotateDegrees(Axis.XP,state.xRot);
        if(MagiciansRedAnimation.flamePose(state.task)){renderer.model.setupAnim(state);pose.translate(0,-stand.getBbHeight()*.25,.25);pose.rotateDegrees(Axis.XP,renderer.model.part("head").xRot*180/(float)Math.PI-state.xRot);pose.translate(0,stand.getBbHeight()*.25,-.25);}
        pose.rotateDegrees(Axis.YP,180+state.bodyRot);pose.translate(0,-stand.getEyeHeight(),0);renderer.submit(state,pose,collector,camera);pose.popPose();return true;
    }
    private static final class Flames extends RenderLayer<State,MagiciansRedModel> {
        Flames(MagiciansRedRenderer renderer){super(renderer);}
        @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,State state,float yaw,float pitch){
            if(state.isInvisible||state.opacity<=0)return;var model=getParentModel();model.setupAnim(state);
            if(state.glow)collector.submitModelPart(model.root(),pose,RenderTypes.eyes(GLOW),0xF000F0,OverlayTexture.NO_OVERLAY,null,state.tint(),state.outlineColor);
            if(!state.bodyFlame)return;
            for(String side:List.of("left","right")){
                if(model.part(side+"Arm").visible)limbFire(model,pose,collector,state.tint(),new String[]{"root","body","upperPart",side+"Arm",side+"ForeArm"},3,4,3);
                if(model.part(side+"Leg").visible){limbFire(model,pose,collector,state.tint(),new String[]{"root","body",side+"Leg"},5,4,4);limbFire(model,pose,collector,state.tint(),new String[]{"root","body",side+"Leg",side+"LowerLeg"},4,4,4);}
            }
        }
        private static void limbFire(MagiciansRedModel model,PoseStack pose,SubmitNodeCollector collector,int tint,String[] path,float y,float width,float height){
            pose.pushPose();var part=model.root();part.translateAndRotate(pose);for(String name:path){part=part.getChild(name);part.translateAndRotate(pose);}MagiciansRedEffects.flame(pose,collector,0,y,0,width,height,Direction.UP,tint,true,false);pose.popPose();
        }
    }
}
