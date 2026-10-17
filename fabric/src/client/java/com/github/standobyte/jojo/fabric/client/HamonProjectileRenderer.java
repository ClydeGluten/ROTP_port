package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Literal source projectile cubes/UVs, cutter rotation, bubble size and stretched owner skin. */
public final class HamonProjectileRenderer extends EntityRenderer<HamonProjectileEntity,HamonProjectileRenderer.State> {
    public static final class State extends EntityRenderState {
        HamonProjectileEntity.Kind kind;float yaw,pitch,size,length;int color,entityId;boolean left,slim;Identifier skin;
    }
    private final EnumMap<HamonProjectileEntity.Kind,ModelPart> models=new EnumMap<>(HamonProjectileEntity.Kind.class);
    private final ModelPart[] arms=new ModelPart[4];
    public HamonProjectileRenderer(EntityRendererProvider.Context context){
        super(context);
        for(var kind:List.of(HamonProjectileEntity.Kind.BUBBLE,HamonProjectileEntity.Kind.CUTTER,HamonProjectileEntity.Kind.BUBBLE_CUTTER,HamonProjectileEntity.Kind.BARRIER,HamonProjectileEntity.Kind.SNAKE))models.put(kind,model(kind));
        for(int i=0;i<4;i++)arms[i]=arm((i&1)!=0,(i&2)!=0);
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(HamonProjectileEntity entity,State state,float partial){
        super.extractRenderState(entity,state,partial);state.kind=entity.kind();state.yaw=Mth.lerp(partial,entity.yRotO,entity.getYRot());state.pitch=Mth.lerp(partial,entity.xRotO,entity.getXRot());
        state.size=entity.size(partial);state.color=0xFF000000|entity.color();state.entityId=entity.getId();state.length=1;state.skin=null;
        var owner=entity.owner();
        if(owner!=null){state.left=owner.getMainArm()==HumanoidArm.LEFT;state.length=(float)entity.position().distanceTo(owner.getEyePosition().add(0,-.47,0));
            state.yaw=owner.getYRot();state.pitch=owner.getXRot();
            if(owner instanceof net.minecraft.client.player.AbstractClientPlayer player){state.skin=player.getSkin().body().texturePath();state.slim=player.getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM;}}
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.kind==HamonProjectileEntity.Kind.TURQUOISE)return; // Original is exclusively particles.
        pose.pushPose();pose.scale(1,-1,-1);
        if(state.kind==HamonProjectileEntity.Kind.ZOOM){
            if(state.skin!=null){pose.rotateDegrees(Axis.YP,state.yaw);pose.rotateDegrees(Axis.XP,state.pitch);pose.scale(1,1,Math.max(.1F,state.length/.75F));
                collector.submitModelPart(arms[(state.left?1:0)|(state.slim?2:0)],pose,RenderTypes.entityTranslucent(state.skin),state.lightCoords,OverlayTexture.NO_OVERLAY,null);}
            pose.popPose();return;
        }
        var model=models.get(state.kind);model.resetPose();
        String texture=switch(state.kind){case BUBBLE->"hamon_bubble";case CUTTER->"hamon_cutter";case BUBBLE_CUTTER->"hamon_bubble_cutter";case BARRIER->"hamon_bubble_barrier";default->"satiporoja_scarf";};
        model.yRot=(state.yaw+(state.kind==HamonProjectileEntity.Kind.CUTTER||state.kind==HamonProjectileEntity.Kind.BUBBLE_CUTTER?state.ageInTicks*60:0))*Mth.DEG_TO_RAD;
        if(state.kind==HamonProjectileEntity.Kind.BUBBLE){model.xRot=state.pitch*Mth.DEG_TO_RAD;float scale=1+(new Random(state.entityId).nextFloat()-.5F)*.4F;pose.scale(scale,scale,scale);}
        if(state.kind==HamonProjectileEntity.Kind.BARRIER)pose.scale(state.size,state.size,state.size);
        Identifier id=RipplesFabric.id("textures/entity/"+(state.kind==HamonProjectileEntity.Kind.SNAKE?"":"projectiles/")+texture+".png");
        var type=state.kind==HamonProjectileEntity.Kind.BARRIER?RenderTypes.entityTranslucent(id):RenderTypes.entityCutout(id);
        if(state.kind==HamonProjectileEntity.Kind.SNAKE){model.xRot=(float)Math.PI/2;for(int i=0;i<Math.max(1,(int)Math.ceil(state.length/.7125F));i++){collector.submitModelPart(model,pose,type,state.lightCoords,OverlayTexture.NO_OVERLAY,null);pose.translate(0,.7125F,0);}}
        else collector.submitModelPart(model,pose,type,state.lightCoords,OverlayTexture.NO_OVERLAY,null,state.kind==HamonProjectileEntity.Kind.CUTTER?state.color:-1);
        pose.popPose();
    }
    private static ModelPart model(HamonProjectileEntity.Kind kind){
        MeshDefinition mesh=new MeshDefinition();CubeListBuilder b=CubeListBuilder.create();int size=32;PartPose pivot=PartPose.ZERO;
        switch(kind){
            case BUBBLE->{size=16;b.texOffs(0,0).addBox(-1,-2,-1,2,2,2).texOffs(6,0).addBox(-1,-1.5F,-.5F,2,1,1,new CubeDeformation(.2F)).texOffs(0,4).addBox(-.5F,-2,-.5F,1,2,1,new CubeDeformation(.2F)).texOffs(6,2).addBox(-.5F,-1.5F,-1,1,1,2,new CubeDeformation(.2F));}
            case CUTTER,BUBBLE_CUTTER->{b.texOffs(0,0).addBox(-2,-1,-3,4,1,6,new CubeDeformation(-.4F)).texOffs(0,7).addBox(-3,-1,-2,6,1,4,new CubeDeformation(-.395F));if(kind==HamonProjectileEntity.Kind.BUBBLE_CUTTER)b.texOffs(0,12).addBox(-1,-1,-1,2,1,2,new CubeDeformation(.2F));}
            case BARRIER->{size=256;pivot=PartPose.offset(0,-15,0);b.texOffs(0,0).addBox(-12,-12,-12,24,24,24).texOffs(96,0).addBox(-9,-9,-15,18,18,30).texOffs(0,48).addBox(-6,-6,-16.5F,12,12,33).texOffs(90,48).addBox(-15,-9,-9,30,18,18).texOffs(90,84).addBox(-16.5F,-6,-6,33,12,12).texOffs(0,93).addBox(-9,-15,-9,18,30,18).texOffs(72,108).addBox(-6,-16.5F,-6,12,33,12);}
            case SNAKE->b.texOffs(0,10).addBox(-.5F,-3,-1,1,3,12,new CubeDeformation(-.3F));
            default->{}
        }
        mesh.getRoot().addOrReplaceChild("projectile",b,pivot);return LayerDefinition.create(mesh,size,size).bakeRoot().getChild("projectile");
    }
    private static ModelPart arm(boolean left,boolean slim){
        MeshDefinition mesh=new MeshDefinition();
        var b=CubeListBuilder.create().texOffs(left?32:40,left?48:16).addBox(-2,-10,-2,slim?3:4,12,4)
                .texOffs(left?48:40,left?48:32).addBox(-2,-10,-2,slim?3:4,12,4,new CubeDeformation(.25F));
        mesh.getRoot().addOrReplaceChild("arm",b,PartPose.offsetAndRotation(0,-2,0,-1.5708F,0,0));
        return LayerDefinition.create(mesh,64,64).bakeRoot().getChild("arm");
    }
}
