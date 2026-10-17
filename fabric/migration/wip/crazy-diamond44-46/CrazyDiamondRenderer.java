package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.core.StandStatFormulas;
import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.client.*;
import com.github.standobyte.jojo.fabric.client.model.CrazyDiamondModel;
import com.github.standobyte.jojo.fabric.stand.crazydiamond.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

/** Authored Crazy Diamond model, held objects, two-handed barrage, glow and source partial summons. */
public final class CrazyDiamondRenderer extends MobRenderer<CrazyDiamondEntity,CrazyDiamondRenderer.State,CrazyDiamondModel> {
    public static final Identifier TEXTURE=RipplesFabric.id("textures/entity/stand/crazy_diamond.png"),GLOW=RipplesFabric.id("textures/entity/stand/glow/crazy_diamond.png");
    private static BiFunction<Entity,Identifier,Identifier> textures=(entity,id)->id;
    private static Predicate<Entity> visibility=entity->true;
    private static boolean initialized;
    private final WeakHashMap<CrazyDiamondEntity,History> histories=new WeakHashMap<>();
    private static final class History {float lastAge=Float.NaN,lastLoop=-1,idleStart;boolean actionOccurred;final List<Swing> swings=new ArrayList<>();}
    public record Swing(float time,boolean left,double x,double y,double z,float zRot){}
    public static final class State extends ArmedEntityRenderState {
        public CrazyDiamondEntity.Kind kind=CrazyDiamondEntity.Kind.NONE;
        public RestorationSelection.Mode restoration=RestorationSelection.Mode.NONE;
        public int summonMode,summonVariant,actionPhase,actionDuration;
        public float actionTicks,idleTicks,cameraOpacity=1,whiteOverlay;
        public boolean leftPunch,mainArmLeft,firstPerson,summonAnimation,idleSway,glow,visible=true,entityWorked,healBarrage;
        public boolean hasArms=true,hasLegs=true,hasBody=true;
        public float[] ownerPose;
        public Identifier texture=TEXTURE,glowTexture=GLOW;
        public List<Swing> swings=List.of();
        public int tint(){return Math.round(cameraOpacity*255)<<24|0xFFFFFF;}
    }
    public static void initialize(){
        if(initialized)return;initialized=true;
        EntityRendererRegistry.register(CrazyDiamondCombatActions.STAND,CrazyDiamondRenderer::new);
        CrazyDiamondProjectileRenderer.initialize();CrazyDiamondEffects.initialize();
    }
    public static void setTextureResolver(BiFunction<Entity,Identifier,Identifier> resolver){textures=Objects.requireNonNull(resolver);}
    public static void setVisibilityPredicate(Predicate<Entity> predicate){visibility=Objects.requireNonNull(predicate);}
    public static boolean visible(Entity entity){var player=Minecraft.getInstance().player;return visibility.test(entity)&&(!entity.isInvisible()||player!=null&&!entity.isInvisibleTo(player));}
    public CrazyDiamondRenderer(EntityRendererProvider.Context context){
        super(context,new CrazyDiamondModel(),0);addLayer(new ItemInHandLayer<>(this));
        addLayer(new RenderLayer<State,CrazyDiamondModel>(this){
            private final CrazyDiamondModel swingModel=new CrazyDiamondModel();
            @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,State state,float yaw,float pitch){
                if(state.glow)collector.submitModelPart(getParentModel().root(),pose,RenderTypes.eyes(state.glowTexture),0xF000F0,OverlayTexture.NO_OVERLAY,null,state.tint(),state.outlineColor);
                if(!state.hasArms)return;
                for(var swing:state.swings){
                    float p=swing.time/4,pitchRad=state.xRot*Mth.DEG_TO_RAD;
                    var offset=new Vec3(swing.x,-swing.y,swing.z+.5-Math.abs(.5-p)).xRot(pitchRad);
                    pose.pushPose();pose.translate(offset.x,offset.y,-offset.z);
                    swingModel.setupAnim(state);CrazyDiamondAnimation.barrage(swingModel,p,pitchRad,swing.left,swing.zRot);swingModel.onlyArm(swing.left);
                    int tint=Math.round(state.cameraOpacity*.75F*255)<<24|0xFFFFFF;
                    collector.submitModelPart(swingModel.root(),pose,RenderTypes.entityTranslucentCull(state.texture),light,OverlayTexture.NO_OVERLAY,null,tint,state.outlineColor);
                    if(state.glow)collector.submitModelPart(swingModel.root(),pose,RenderTypes.eyes(state.glowTexture),0xF000F0,OverlayTexture.NO_OVERLAY,null,tint,state.outlineColor);
                    pose.popPose();
                }
            }
        });
    }
    public static boolean submitFirstPerson(PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,float partial){
        var client=Minecraft.getInstance();if(!client.options.getCameraType().isFirstPerson()||!(client.getCameraEntity() instanceof CrazyDiamondEntity stand)||!stand.manuallyControlled())return false;
        if(!(client.getEntityRenderDispatcher().getRenderer(stand) instanceof CrazyDiamondRenderer renderer))return false;
        var state=renderer.createRenderState();renderer.extractRenderState(stand,state,partial);state.firstPerson=true;state.visible=visible(stand);state.cameraOpacity=stand.renderAlpha();
        pose.pushPose();pose.rotateDegrees(Axis.XP,state.xRot);pose.rotateDegrees(Axis.YP,180+state.bodyRot);pose.translate(0,-stand.getEyeHeight(),0);renderer.submit(state,pose,collector,camera);pose.popPose();return true;
    }
    @Override public State createRenderState(){return new State();}
    @Override public Identifier getTextureLocation(State state){return state.texture;}
    @Override protected int getModelTint(State state){return state.tint();}
    @Override protected float getWhiteOverlayProgress(State state){return state.whiteOverlay;}
    @Override protected boolean shouldRenderLayers(State state){return state.visible&&state.cameraOpacity>0&&super.shouldRenderLayers(state);}
    @Override protected RenderType getRenderType(State state,boolean body,boolean translucent,boolean glow){return state.visible&&state.cameraOpacity>0?RenderTypes.entityTranslucentCull(state.texture):null;}
    @Override protected void scale(State state,PoseStack pose){pose.scale(.9375F,.9375F*1.95F/1.8F,.9375F);if(state.idleSway)pose.translate(0,Math.sin(state.idleTicks*.04F)*.04F,0);}
    @Override public void extractRenderState(CrazyDiamondEntity entity,State s,float partial){
        if(TimeStopClient.isFrozen(entity))partial=0;super.extractRenderState(entity,s,partial);ArmedEntityRenderState.extractArmedEntityRenderState(entity,s,itemModelResolver,partial);
        var client=Minecraft.getInstance();var h=histories.computeIfAbsent(entity,e->new History());var restore=RestorationSelection.state(entity);
        s.kind=entity.attackKind();s.actionPhase=entity.actionPhase();s.actionDuration=entity.actionDuration();s.actionTicks=entity.actionTicks()+partial;
        s.summonMode=entity.summonMode();s.summonVariant=entity.summonVariant();s.leftPunch=entity.leftPunch();s.mainArmLeft=entity.getMainArm()==HumanoidArm.LEFT;
        s.restoration=restore.mode();s.entityWorked=restore.entityWorked();s.healBarrage=CrazyDiamondEffects.healBarrage(entity);
        boolean action=s.kind!=CrazyDiamondEntity.Kind.NONE||s.restoration!=RestorationSelection.Mode.NONE;
        if(action)h.actionOccurred=true;s.summonAnimation=!h.actionOccurred&&s.summonMode==0&&s.ageInTicks<=20;
        if(action||s.summonAnimation)h.idleStart=s.ageInTicks;s.idleTicks=s.ageInTicks-h.idleStart;
        s.whiteOverlay=s.summonMode==0?Mth.clamp((10-s.ageInTicks)/10,0,1):0;
        s.firstPerson=false;s.texture=textures.apply(entity,TEXTURE);s.glowTexture=textures.apply(entity,GLOW);s.glow=client.getResourceManager().getResource(s.glowTexture).isPresent();s.visible=visible(entity);
        var owner=entity.level().getEntity(entity.ownerId());s.ownerPose=null;
        if(owner instanceof LivingEntity living){
            s.hasArms=StandParts.has(living,StandParts.Part.ARMS);s.hasLegs=StandParts.has(living,StandParts.Part.LEGS);s.hasBody=StandParts.has(living,StandParts.Part.MAIN_BODY);
            if(client.getEntityRenderDispatcher().getRenderer(living) instanceof LivingEntityRenderer<?,?,?> renderer&&renderer.getModel() instanceof HumanoidModel<?> model){
                ModelPart[] parts={model.head,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg};s.ownerPose=new float[18];
                for(int i=0;i<parts.length;i++){s.ownerPose[i*3]=parts[i].xRot;s.ownerPose[i*3+1]=parts[i].yRot;s.ownerPose[i*3+2]=parts[i].zRot;}
            }
        }else{s.hasArms=s.hasLegs=s.hasBody=true;}
        s.idleSway=!action&&!s.summonAnimation&&!entity.manuallyControlled()&&(owner==null||!owner.isShiftKeyDown());s.cameraOpacity=entity.renderAlpha();
        var camera=client.gameRenderer.mainCamera();if(camera.entity()==entity&&client.options.getCameraType().isFirstPerson())s.visible=false;
        if(owner==client.player&&camera.entity()==owner){
            if(client.options.getCameraType().isFirstPerson()&&!entity.isArmsOnlyMode()){
                Vec3 diff=entity.getPosition(partial).subtract(owner.getPosition(partial)).multiply(1,0,1);double dist=diff.lengthSqr();var look=Vec3.directionFromRotation(0,owner.getViewYRot(partial));
                if(dist<.25||dist<9&&look.dot(diff)>dist/2)s.summonMode=15;
            }else if(!client.options.getCameraType().isFirstPerson())s.cameraOpacity*=StandCamera.opacity(entity);
        }
        float age=s.ageInTicks,delta=Float.isNaN(h.lastAge)?0:Math.max(0,age-h.lastAge);h.lastAge=age;
        if(!client.isPaused()&&!TimeStopClient.isFrozen(entity)){h.swings.replaceAll(v->new Swing(v.time+delta*.75F,v.left,v.x,v.y,v.z,v.zRot));h.swings.removeIf(v->v.time>=3);}
        if(s.kind==CrazyDiamondEntity.Kind.BARRAGE&&s.actionPhase==4||s.healBarrage){
            float loop=age/4;if(h.lastLoop>0&&loop>h.lastLoop){
                float hits=StandStatFormulas.getBarrageHitsPerSecond(entity.visualAttackSpeed())/20F*Math.min(loop-h.lastLoop,1)*4;var random=entity.getRandom();int count=(int)hits;if(random.nextFloat()<=hits-count)count++;
                double offset=1-entity.visualPrecision()/40;boolean left=random.nextBoolean();
                for(int i=0;i<count;i++){left=!left;float t=(i/(float)count+(random.nextFloat()-.5F)*.4F/count)*2;double up=(random.nextDouble()-.5)*offset,x=random.nextDouble()*offset/2*(left?1:-1),z=random.nextDouble()*.5;
                    float rotate=offset==0?0:Mth.wrapDegrees((float)(90-Math.toDegrees(Math.atan2(up,x))))*Mth.DEG_TO_RAD;h.swings.add(new Swing(t,left,x,up,z,rotate));}
            }h.lastLoop=loop;
        }else h.lastLoop=-1;s.swings=List.copyOf(h.swings);
    }
}
