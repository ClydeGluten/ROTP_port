package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.HamonActionService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Source PlayerArmBarrageSwing and ArmsBarrageAnimation with extracted per-frame arm snapshots. */
public final class HamonBarrageLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    private record Swing(float age,float initial,boolean left,Vec3 offset){}
    private record Frame(float tick,boolean left,Vec3 offset){}
    private static final class Holder {float previous=-1;final List<Swing> swings=new ArrayList<>();}
    private static final Map<Avatar,Holder> HOLDERS=new WeakHashMap<>();
    private static final Map<AvatarRenderState,List<Frame>> FRAMES=new WeakHashMap<>();
    private final PlayerModel[] regular=new PlayerModel[32],slim=new PlayerModel[32];
    public HamonBarrageLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent){super(parent);}
    public static void extract(Avatar entity,AvatarRenderState state,float partial){
        var action=HamonActionService.state(entity);boolean active=action.held().equals("jonathan_overdrive_barrage")||action.continuous().equals("jonathan_syo_barrage")&&action.continuousTicks()<70;
        var holder=HOLDERS.computeIfAbsent(entity,e->new Holder());float age=entity.tickCount+(TimeStopClient.isFrozen(entity)?0:partial);
        holder.swings.removeIf(swing->swing.initial()+(age-swing.age())*.75F>=3);
        float loop=age/4;
        if(active&&holder.previous>0&&loop>holder.previous&&!Minecraft.getInstance().isPaused()){
            float hits=3*Math.min(loop-holder.previous,1)*4;int count=(int)hits;
            var random=entity.getRandom();if(random.nextFloat()<=hits-count)count++;
            boolean left=entity.getMainArm()==HumanoidArm.LEFT;if(random.nextBoolean())left=!left;
            for(int i=0;i<count;i++){
                float initial=(i/(float)count+(random.nextFloat()-.5F)*.4F/count)*2;
                left=!left;double up=(random.nextDouble()-.5)*.625,side=random.nextDouble()*.625/2*(left?1:-1),front=random.nextDouble()*.5;
                holder.swings.add(new Swing(age,initial,left,new Vec3(side,up,front)));
            }
        }
        holder.previous=active?Math.max(loop,holder.previous):-1;
        FRAMES.put(state,holder.swings.stream().map(swing->new Frame(swing.initial()+(age-swing.age())*.75F,swing.left(),swing.offset())).toList());
    }
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch){
        var frames=FRAMES.get(state);if(frames==null||frames.isEmpty()||state.skin==null||state.isInvisible)return;
        boolean isSlim=state.skin.model()==PlayerModelType.SLIM;var models=isSlim?slim:regular;
        for(int i=0;i<Math.min(frames.size(),models.length);i++){
            var frame=frames.get(i);var model=models[i];
            if(model==null)models[i]=model=new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,isSlim),64,64).bakeRoot(),isSlim);
            model.root().getAllParts().forEach(ModelPart::resetPose);VampireBendGeometry.reset(model);
            float ratio=frame.tick()/4,animationTick=2+2*(frame.left()?ratio:1-ratio);
            HamonPlayerAnimation.applyClip(model,"punch_barrage",animationTick);
            var arm=frame.left()?model.leftArm:model.rightArm;var sleeve=frame.left()?model.leftSleeve:model.rightSleeve;sleeve.visible=false;
            var rotation=new org.joml.Quaternionf().rotationX(pitch*(float)Math.PI/180).mul(new org.joml.Quaternionf().rotationZYX(arm.zRot,arm.yRot,arm.xRot)).getEulerAnglesZYX(new org.joml.Vector3f());
            arm.xRot=rotation.x;arm.yRot=rotation.y;arm.zRot=rotation.z;
            double extension=.5F-Math.abs(.5F-ratio);var offset=new Vec3(frame.offset().x,-frame.offset().y,frame.offset().z+extension).xRot(pitch*(float)Math.PI/180);
            pose.pushPose();pose.translate(offset.x,offset.y,-offset.z);
            collector.submitModelPart(arm,pose,RenderTypes.entityCutout(state.skin.body().texturePath()),light,OverlayTexture.NO_OVERLAY,null,0xBFFFFFFF);
            pose.popPose();
        }
    }
}
