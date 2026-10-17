package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import com.github.standobyte.jojo.fabric.stand.hierophant.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Literal HGEmeraldModel and repeating HGStringModel, with source owner alpha/fullbright string overlay. */
public final class HierophantProjectileRenderer extends EntityRenderer<Entity,HierophantProjectileRenderer.State> {
    private static final Identifier EMERALD=RipplesFabric.id("textures/entity/projectiles/hg_emerald.png");
    private static final Identifier STRING=RipplesFabric.id("textures/entity/projectiles/hg_string.png");
    private static final Identifier GLOW=RipplesFabric.id("textures/entity/projectiles/hg_string_glow.png");
    private final ModelPart emerald=emerald(),string=string();
    public static final class State extends EntityRenderState {
        Vec3 extent=Vec3.ZERO;
        boolean emerald,visible;
        float yaw,pitch,alpha=1;
        Identifier texture=EMERALD,glow=GLOW;
        int tint(){return Math.round(alpha*255)<<24|0xFFFFFF;}
    }
    public HierophantProjectileRenderer(EntityRendererProvider.Context context){super(context);}
    public static void initialize(){
        EntityRendererRegistry.register(HierophantGreenActions.EMERALD,HierophantProjectileRenderer::new);
        EntityRendererRegistry.register(HierophantGreenActions.STRING,HierophantProjectileRenderer::new);
        EntityRendererRegistry.register(HierophantGreenActions.GRAPPLING_STRING,HierophantProjectileRenderer::new);
        EntityRendererRegistry.register(HierophantGreenActions.BARRIER_ENTITY,HierophantProjectileRenderer::new);
    }
    @Override public State createRenderState(){return new State();}
    @Override protected AABB getBoundingBoxForCulling(Entity entity,float partial){return entity instanceof OwnedBodyPartEntity body?body.segmentBounds():super.getBoundingBoxForCulling(entity,partial);}
    @Override public void extractRenderState(Entity entity,State state,float partial){
        if(TimeStopClient.isFrozen(entity))partial=0;
        super.extractRenderState(entity,state,partial);state.emerald=entity instanceof HGEmeraldEntity;
        state.visible=HierophantGreenRenderer.visible(entity);state.alpha=1;
        state.texture=HierophantGreenRenderer.texture(entity,state.emerald?EMERALD:STRING);state.glow=HierophantGreenRenderer.texture(entity,GLOW);
        state.yaw=Mth.lerp(partial,entity.yRotO,entity.getYRot());state.pitch=Mth.lerp(partial,entity.xRotO,entity.getXRot());
        if(entity instanceof OwnedBodyPartEntity body){
            state.extent=entity.getPosition(partial).subtract(body.origin(partial));
            if(body.livingOwner()!=null)state.lightCoords=entityRenderDispatcher.getPackedLightCoords(body.livingOwner(),partial);
            if(body.livingOwner() instanceof HierophantGreenEntity stand)state.alpha=HierophantGreenRenderer.alpha(stand);
        }
        if(outline(entity))state.outlineColor=0xFFFF0000;
    }
    /** Root's shared Minecraft glow predicate uses this so the outline post-process is scheduled. */
    public static boolean outline(Entity entity){
        return entity instanceof HGBarrierEntity barrier&&barrier.wasRipped()&&!TimeStopClient.isFrozen(entity)
                &&barrier.livingOwner() instanceof HierophantGreenEntity stand&&stand.user()==Minecraft.getInstance().player;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(!state.visible||state.alpha<=0)return;
        pose.pushPose();pose.scale(1,-1,-1);
        if(state.emerald){
            emerald.yRot=state.yaw*Mth.DEG_TO_RAD;emerald.xRot=state.pitch*Mth.DEG_TO_RAD;
            collector.submitModelPart(emerald,pose,RenderTypes.entityCutout(state.texture),state.lightCoords,OverlayTexture.NO_OVERLAY,null,state.tint(),state.outlineColor);
        }else{
            float yaw=(float)Math.toDegrees(Math.atan2(-state.extent.x,state.extent.z));
            float pitch=(float)Math.toDegrees(-Math.atan2(state.extent.y,state.extent.horizontalDistance()));
            pose.rotateDegrees(Axis.YP,yaw);pose.rotateDegrees(Axis.XP,pitch);
            int pieces=(int)Math.floor(state.extent.length()*2)+1;
            for(int i=0;i<pieces;i++){
                collector.submitModelPart(string,pose,RenderTypes.entityTranslucent(state.texture),state.lightCoords,OverlayTexture.NO_OVERLAY,null,state.tint(),state.outlineColor);
                collector.submitModelPart(string,pose,RenderTypes.entityTranslucent(state.glow),0xF000F0,OverlayTexture.NO_OVERLAY,null,state.tint(),state.outlineColor);
                pose.translate(0,0,.5);
            }
        }
        pose.popPose();
    }
    private static ModelPart string(){
        var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("string",CubeListBuilder.create().texOffs(0,0).addBox(-.5F,-.5F,-.5F,1,1,8),PartPose.ZERO);
        return LayerDefinition.create(mesh,32,32).bakeRoot().getChild("string");
    }
    private static ModelPart emerald(){
        var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("emerald",CubeListBuilder.create()
                .texOffs(0,0).addBox(-2.5F,-4,-2.5F,5,4,5)
                .texOffs(0,9).addBox(-2,-3.5F,-3.5F,4,3,7)
                .texOffs(0,19).addBox(-1.5F,-3,-4.5F,3,2,9),PartPose.ZERO);
        return LayerDefinition.create(mesh,32,32).bakeRoot().getChild("emerald");
    }
}
