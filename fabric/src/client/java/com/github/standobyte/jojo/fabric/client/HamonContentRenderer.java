package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Original glider, clacker and Crimson Bubble geometry with their source per-entity transforms. */
public final class HamonContentRenderer<T extends Entity> extends EntityRenderer<T,HamonContentRenderer.State>{
    public static final class State extends EntityRenderState {float yaw,pitch,height;int color=-1;Identifier texture;}
    private final String kind;private final ModelPart model;
    public HamonContentRenderer(EntityRendererProvider.Context context,String kind){super(context);this.kind=kind;model=switch(kind){case "glider"->HamonContentModels.glider();case "clackers"->HamonContentModels.clackers();default->HamonContentModels.crimson();};}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(T entity,State s,float partial){
        super.extractRenderState(entity,s,partial);s.yaw=Mth.lerp(partial,entity.yRotO,entity.getYRot());s.pitch=Mth.lerp(partial,entity.xRotO,entity.getXRot());s.height=entity.getBbHeight();s.color=-1;
        s.texture=RipplesFabric.id("textures/entity/"+(kind.equals("clackers")?"projectiles/clackers":"crimson_bubble")+".png");
        if(entity instanceof LeavesGliderEntity glider){
            var sprite=Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(glider.getLeavesBlock()).sprite().contents().name();
            s.texture=Identifier.fromNamespaceAndPath(sprite.getNamespace(),"textures/"+sprite.getPath()+".png");
            var tint=Minecraft.getInstance().getBlockColors().getTintSource(glider.getLeavesBlock(),0);
            s.color=0xFF000000|(tint==null?0xFFFFFF:tint.colorInWorld(glider.getLeavesBlock(),Minecraft.getInstance().level,entity.blockPosition()));
        }
        if(entity instanceof ClackersEntity clackers&&!clackers.isInGround())s.pitch=(float)((s.pitch+s.ageInTicks*18*entity.getDeltaMovement().length())%360);
    }
    @Override public void submit(State s,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        model.resetPose();model.yRot=s.yaw*Mth.DEG_TO_RAD;if(kind.equals("clackers"))model.xRot=s.pitch*Mth.DEG_TO_RAD;
        pose.pushPose();pose.scale(1,-1,-1);if(kind.equals("glider"))pose.translate(0,-s.height,0);
        collector.submitModelPart(model,pose,kind.equals("crimson")?RenderTypes.entityTranslucent(s.texture):RenderTypes.entityCutout(s.texture),s.lightCoords,OverlayTexture.NO_OVERLAY,null,s.color);
        pose.popPose();
    }
}
