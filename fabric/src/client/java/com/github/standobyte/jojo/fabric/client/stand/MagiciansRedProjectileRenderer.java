package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.stand.magiciansred.*;
import com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotActions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Source procedural seven-flame ankhs, repeating bind ribbon, generated fireball item, and six-axis detector. */
public final class MagiciansRedProjectileRenderer {
    public static void initialize(){
        EntityRendererRegistry.register(MagiciansRedActions.FLAME,Flame::new);EntityRendererRegistry.register(MagiciansRedActions.FIREBALL_ENTITY,Fireball::new);
        EntityRendererRegistry.register(MagiciansRedActions.CROSSFIRE_ENTITY,Ankh::new);EntityRendererRegistry.register(MagiciansRedActions.CROSSFIRE_SPECIAL_ENTITY,Ankh::new);
        EntityRendererRegistry.register(MagiciansRedActions.BIND_ENTITY,Bind::new);EntityRendererRegistry.register(MagiciansRedActions.DETECTOR_ENTITY,Detector::new);
    }
    public static class State extends EntityRenderState {
        boolean visible;float yaw,pitch,scale=1,alpha=1;Vec3 start=Vec3.ZERO,detection;List<Vec3> origins=List.of();net.minecraft.client.multiplayer.ClientLevel level;
        final ItemStackRenderState item=new ItemStackRenderState();
    }
    private abstract static class Base<T extends Entity> extends EntityRenderer<T,State> {
        Base(EntityRendererProvider.Context context){super(context);}
        @Override public State createRenderState(){return new State();}
        @Override public void extractRenderState(T entity,State state,float partial){
            if(TimeStopClient.isFrozen(entity))partial=0;super.extractRenderState(entity,state,partial);state.visible=!entity.isInvisible()||!entity.isInvisibleTo(Minecraft.getInstance().player);
            state.yaw=Mth.rotLerp(partial,entity.yRotO,entity.getYRot());state.pitch=Mth.lerp(partial,entity.xRotO,entity.getXRot());
        }
    }
    private static final class Flame extends Base<MRFlameEntity> {
        Flame(EntityRendererProvider.Context context){super(context);}
        @Override public void extractRenderState(MRFlameEntity entity,State state,float partial){super.extractRenderState(entity,state,partial);state.start=entity.startingPosition();state.level=(net.minecraft.client.multiplayer.ClientLevel)entity.level();}
        @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
            if(!state.visible)return;var at=new Vec3(state.x,state.y,state.z);var delta=state.start.subtract(at);var step=delta.normalize().scale(.4);
            for(int i=(int)(delta.length()/.4);i>0;i--){state.level.addParticle(SilverChariotActions.FLAME_PARTICLE,true,true,at.x,at.y,at.z,0,0,0);at=at.add(step);}super.submit(state,pose,collector,camera);
        }
    }
    private static final class Fireball extends Base<MRFireballEntity> {
        private final ItemModelResolver resolver;private ItemStack icon;
        Fireball(EntityRendererProvider.Context context){super(context);resolver=context.getItemModelResolver();}
        private ItemStack icon(){if(icon==null){icon=new ItemStack(Items.PAPER);icon.set(DataComponents.ITEM_MODEL,RipplesFabric.id("item_icon_mr_fireball"));}return icon;}
        @Override public void extractRenderState(MRFireballEntity entity,State state,float partial){super.extractRenderState(entity,state,partial);resolver.updateForNonLiving(state.item,icon(),ItemDisplayContext.GROUND,entity);}
        @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
            if(!state.visible)return;pose.pushPose();pose.rotate(camera.orientation);pose.rotateDegrees(Axis.YP,180);state.item.submit(pose,collector,0xF000F0,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();super.submit(state,pose,collector,camera);
        }
    }
    private static final class Ankh<T extends MRCrossfireEntity> extends Base<T> {
        Ankh(EntityRendererProvider.Context context){super(context);}
        @Override public void extractRenderState(T entity,State state,float partial){super.extractRenderState(entity,state,partial);state.scale=entity.scale()*(entity.small()?.5F:1);}
        @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
            if(!state.visible)return;pose.pushPose();pose.scale(state.scale,-state.scale,-state.scale);pose.rotateDegrees(Axis.YP,state.yaw);pose.rotateDegrees(Axis.XP,state.pitch);
            fire(pose,collector,0,-1,3,10,Direction.UP);fire(pose,collector,0,-14.5F,3,8,Direction.WEST);fire(pose,collector,0,-14.5F,3,8,Direction.EAST);
            fire(pose,collector,4,-16,2,8,Direction.UP);fire(pose,collector,-5,-17,2,8,Direction.WEST);fire(pose,collector,-4,-26,2,8,Direction.DOWN);fire(pose,collector,5,-25,2,8,Direction.EAST);
            pose.popPose();super.submit(state,pose,collector,camera);
        }
        private static void fire(PoseStack pose,SubmitNodeCollector collector,float x,float y,float width,float height,Direction direction){MagiciansRedEffects.flame(pose,collector,x,y,0,width,height,direction,-1,false,true);}
    }
    private static final class Bind extends Base<MRRedBindEntity> {
        Bind(EntityRendererProvider.Context context){super(context);}
        @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(MRRedBindEntity entity,float partial){return entity.getBoundingBox().expandTowards(entity.origin(partial).subtract(entity.position()));}
        @Override public void extractRenderState(MRRedBindEntity entity,State state,float partial){
            if(TimeStopClient.isFrozen(entity))partial=0;super.extractRenderState(entity,state,partial);
            state.alpha=entity.getOwner() instanceof MagiciansRedEntity stand?stand.renderAlpha():1;
            if(entity.kick()&&entity.getOwner() instanceof MagiciansRedEntity stand)state.origins=List.of(MagiciansRedAnimation.kickRibbonOrigin(stand,false,partial),MagiciansRedAnimation.kickRibbonOrigin(stand,true,partial));
            else state.origins=List.of(entity.origin(partial));
        }
        @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
            if(!state.visible)return;int tint=Math.round(state.alpha*255)<<24|0xFFFFFF;var position=new Vec3(state.x,state.y,state.z);
            for(var origin:state.origins){var extent=position.subtract(origin);float yaw=(float)Math.toDegrees(Math.atan2(-extent.x,extent.z)),pitch=(float)-Math.toDegrees(Math.atan2(extent.y,extent.horizontalDistance()));
                pose.pushPose();pose.scale(1,-1,-1);pose.rotateDegrees(Axis.YP,yaw);pose.rotateDegrees(Axis.XP,pitch);float length=(float)extent.length();
                while(length>=3/16F){MagiciansRedEffects.flame(pose,collector,0,1,0,2,3,Direction.NORTH,tint,false,false);length-=3/16F;pose.translate(0,0,3/16F);}
                MagiciansRedEffects.flame(pose,collector,0,1,0,2,3,Direction.NORTH,tint,false,false);pose.popPose();
            }super.submit(state,pose,collector,camera);
        }
    }
    private static final class Detector extends Base<MRDetectorEntity> {
        private static final Identifier TEXTURE=RipplesFabric.id("textures/entity/mr_detector.png");
        private final ModelPart model;
        Detector(EntityRendererProvider.Context context){
            super(context);var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("detector",CubeListBuilder.create()
                .texOffs(0,9).addBox(-4,-.5F,-.5F,8,1,1,new CubeDeformation(-.2F))
                .texOffs(0,11).addBox(-.5F,-4,-.5F,1,8,1,new CubeDeformation(-.2F))
                .texOffs(0,0).addBox(-.5F,-.5F,-4,1,1,8,new CubeDeformation(-.2F)),PartPose.ZERO);
            model=LayerDefinition.create(mesh,32,32).bakeRoot();
        }
        @Override public void extractRenderState(MRDetectorEntity entity,State state,float partial){super.extractRenderState(entity,state,partial);state.detection=entity.detected()?entity.detectedDirection():null;}
        @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
            if(!state.visible)return;pose.pushPose();pose.scale(1,-1,-1);pose.translate(0,Math.sin(state.ageInTicks*.04F)*.04F,0);pose.scale(1,-1,-1);
            collector.submitModelPart(model,pose,RenderTypes.entityCutout(TEXTURE),state.lightCoords,OverlayTexture.NO_OVERLAY,null,-1,state.outlineColor);
            if(state.detection!=null)for(var direction:Direction.values()){
                double distance=switch(direction.getAxis()){case X->state.detection.x;case Y->state.detection.y;case Z->state.detection.z;};
                boolean positive=direction.getAxisDirection()==Direction.AxisDirection.POSITIVE;
                float strength=positive&&distance>=-1||!positive&&distance<=1?(15-(float)Math.abs(distance))/15:-1;
                MagiciansRedEffects.detectorFlame(pose,collector,direction,strength,camera.yRot);
            }pose.popPose();super.submit(state,pose,collector,camera);
        }
    }
    private MagiciansRedProjectileRenderer(){}
}
