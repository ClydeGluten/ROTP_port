package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.core.StandStatFormulas;
import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.client.*;
import com.github.standobyte.jojo.fabric.client.model.SilverChariotModel;
import com.github.standobyte.jojo.fabric.stand.StandAfterimageEntity;
import com.github.standobyte.jojo.fabric.stand.silverchariot.*;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import java.util.*;

/** Dedicated source Chariot body, independent armor/glow, rapier fire, and one-handed barrage. */
public final class SilverChariotRenderer extends MobRenderer<SilverChariotEntity,SilverChariotRenderer.State,SilverChariotModel> {
    public static final Identifier TEXTURE=RipplesFabric.id("textures/entity/stand/silver_chariot.png");
    private final Map<SilverChariotEntity,VisualHistory> history=new WeakHashMap<>();
    private static boolean initialized;
    public static void initialize() {
        if(initialized)return; initialized=true;
        EntityRendererRegistry.register(SilverChariotActions.STAND,SilverChariotRenderer::new);
        EntityRendererRegistry.register(SilverChariotActions.RAPIER,SilverChariotLayers.RapierRenderer::new);
        EntityRendererRegistry.register(SilverChariotActions.FLAME,SilverChariotLayers.FlameRenderer::new);
        ParticleProviderRegistry.getInstance().register(SilverChariotActions.FLAME_PARTICLE,sprites ->
            (type,level,x,y,z,dx,dy,dz,random)->new OneTickFlame(level,x,y,z,sprites));
        ActionCatalog.registerReplacement(SilverChariotActions.ID,(client,action)-> {
            if(!(StandView.ownManifestation(client) instanceof SilverChariotEntity stand))return action;
            if(action.equals(SilverChariotActions.ATTACK)&&!stand.hasRapier())return SilverChariotActions.UNARMED;
            if(action.equals(SilverChariotActions.DASH)&&client.player!=null&&StandParts.has(client.player,StandParts.Part.ARMS)
                &&(!StandParts.has(client.player,StandParts.Part.MAIN_BODY)||StandAttachments.resolve(client.player).unlocked(1,client.player.isCreative())
                &&(stand.finisherCharge()>=.5F||stand.attackKind()==SilverChariotEntity.Kind.SWEEP)))return SilverChariotActions.SWEEP;
            return action;
        });
        ActionCatalog.registerHeldReplacement(SilverChariotActions.ID,(next,active)->
            (next.id().equals(SilverChariotActions.DASH.toString())||next.id().equals(SilverChariotActions.SWEEP.toString()))&&active.id().equals(SilverChariotActions.BARRAGE.toString()));
    }
    public static final class State extends LivingEntityRenderState {
        public boolean rapier,armor,rapierFire,leftPunch,mainArmLeft,bodyGlow,armorGlow,firstPerson,summonAnimation,idleSway;
        public boolean hasArms=true,hasLegs=true,hasBody=true;
        public int summonMode,summonVariant,actionPhase,actionDuration;
        public float actionTicks,idleTicks,cameraOpacity=1;
        public SilverChariotEntity.Kind kind=SilverChariotEntity.Kind.NONE;
        public List<Swing> swings=List.of();
        public int tint() {return Math.round(cameraOpacity*255)<<24|0xFFFFFF;}
    }
    public record Swing(float time,double x,double y,double z) { }
    private static final class VisualHistory {
        float lastAge=Float.NaN,lastLoop=-1,idleStart;
        final List<Swing> swings=new ArrayList<>();
    }
    public SilverChariotRenderer(EntityRendererProvider.Context context) {
        super(context,new SilverChariotModel(false),0);
        addLayer(new SilverChariotLayers(this));
    }
    /** Active ControllerStand.renderStandHands route: normal posed model, restricted arms, source camera transforms. */
    public static boolean submitFirstPerson(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector collector,
            net.minecraft.client.renderer.state.level.CameraRenderState camera,float partial) {
        var client=Minecraft.getInstance();
        if(!client.options.getCameraType().isFirstPerson()||!(StandView.ownManifestation(client) instanceof SilverChariotEntity stand)
                ||!stand.manuallyControlled()||client.gameRenderer.mainCamera().entity()!=stand)return false;
        if(!(client.getEntityRenderDispatcher().getRenderer(stand) instanceof SilverChariotRenderer renderer))return false;
        var state=renderer.createRenderState();renderer.extractRenderState(stand,state,partial);state.firstPerson=true;state.cameraOpacity=stand.renderAlpha();
        pose.pushPose();pose.rotateDegrees(com.mojang.math.Axis.XP,state.xRot);pose.rotateDegrees(com.mojang.math.Axis.YP,180+state.bodyRot);
        pose.translate(0,-stand.getEyeHeight(),0);renderer.submit(state,pose,collector,camera);pose.popPose();return true;
    }
    @Override public State createRenderState(){return new State();}
    @Override public Identifier getTextureLocation(State state){return TEXTURE;}
    @Override protected int getModelTint(State state){return state.tint();}
    @Override protected RenderType getRenderType(State state,boolean visible,boolean translucent,boolean glowing){
        if(state.cameraOpacity<=0)return null;
        if(state.cameraOpacity<1&&(visible||translucent))return RenderTypes.entityTranslucentCull(TEXTURE);
        return super.getRenderType(state,visible,translucent,glowing);
    }
    @Override protected boolean shouldRenderLayers(State state){return state.cameraOpacity>0&&super.shouldRenderLayers(state);}
    @Override public void submit(State state,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector collector,
            net.minecraft.client.renderer.state.level.CameraRenderState camera){
        pose.pushPose();if(state.idleSway)pose.translate(0,Math.sin(state.idleTicks*.04F)*.04F,0);
        super.submit(state,pose,collector,camera);pose.popPose();
    }
    @Override public void extractRenderState(SilverChariotEntity entity,State state,float partial){
        if(TimeStopClient.isFrozen(entity))partial=0;
        super.extractRenderState(entity,state,partial);
        state.ageInTicks=entity.visualAge()+partial;state.summonAnimation=entity.summonAnimation();
        state.rapier=entity.hasRapier();state.armor=entity.hasArmor();state.rapierFire=entity.isRapierOnFire();
        state.kind=entity.attackKind();state.actionPhase=entity.actionPhase();state.actionDuration=entity.actionDuration();state.actionTicks=entity.actionTicks()+partial;
        state.summonMode=entity.summonMode();state.summonVariant=entity.summonVariant();state.leftPunch=entity.leftPunch();
        state.mainArmLeft=entity.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT;
        state.firstPerson=false;
        var owner=entity.level().getEntity(entity.ownerId());
        state.idleSway=state.kind==SilverChariotEntity.Kind.NONE&&!state.summonAnimation&&!entity.manuallyControlled()&&(owner==null||!owner.isShiftKeyDown());
        if(owner instanceof net.minecraft.world.entity.LivingEntity living){state.hasArms=StandParts.has(living,StandParts.Part.ARMS);state.hasLegs=StandParts.has(living,StandParts.Part.LEGS);state.hasBody=StandParts.has(living,StandParts.Part.MAIN_BODY);}
        else {state.hasArms=true;state.hasLegs=true;state.hasBody=true;}
        state.cameraOpacity=StandCamera.opacity(entity)*entity.renderAlpha();
        var resources=Minecraft.getInstance().getResourceManager();
        state.bodyGlow=resources.getResource(SilverChariotLayers.BODY_GLOW).isPresent();state.armorGlow=resources.getResource(SilverChariotLayers.ARMOR_GLOW).isPresent();
        var h=history.computeIfAbsent(entity,key->new VisualHistory());
        float age=state.ageInTicks,delta=Float.isNaN(h.lastAge)?0:Math.max(0,age-h.lastAge);h.lastAge=age;
        if(!Minecraft.getInstance().isPaused()&&!TimeStopClient.isFrozen(entity)){
            h.swings.replaceAll(s->new Swing(s.time+delta*.75F,s.x,s.y,s.z));h.swings.removeIf(s->s.time>=1.5F);
        }
        if(state.kind!=SilverChariotEntity.Kind.NONE||state.summonAnimation&&age<=20)h.idleStart=age;
        state.idleTicks=age-h.idleStart;
        if(state.kind==SilverChariotEntity.Kind.BARRAGE&&state.actionPhase==4){
            float loop=age/2;
            if(h.lastLoop>0&&loop>h.lastLoop){
                float hits=StandStatFormulas.getBarrageHitsPerSecond(entity.visualAttackSpeed())/20F*Math.min(loop-h.lastLoop,1)*2;
                var random=entity.getRandom();int count=(int)hits;if(random.nextFloat()<=hits-count)count++;
                double offset=1-entity.visualPrecision()/40;
                for(int i=0;i<count;i++){
                    float f=(i/(float)count+(random.nextFloat()-.5F)*.4F/count);
                    h.swings.add(new Swing(f,(state.mainArmLeft?1:-1)*random.nextDouble()*offset/2,(random.nextDouble()-.5)*offset,random.nextDouble()*.5));
                }
            }
            h.lastLoop=loop;
        }else h.lastLoop=-1;
        state.swings=List.copyOf(h.swings);
    }
    private static final class OneTickFlame extends SingleQuadParticle {
        OneTickFlame(ClientLevel level,double x,double y,double z,SpriteSet sprites){super(level,x,y,z,0,0,0,sprites.get(level.getRandom()));lifetime=1;hasPhysics=false;}
        @Override protected int getLightCoords(float partial){return 0xF000F0;}
        @Override protected Layer getLayer(){return Layer.OPAQUE;}
    }
}
