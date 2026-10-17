package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.core.StandStatFormulas;
import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.client.ActionCatalog;
import com.github.standobyte.jojo.fabric.client.StandCamera;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.client.model.HierophantGreenModel;
import com.github.standobyte.jojo.fabric.stand.hierophant.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/** Original segmented HG, legacy animation and fullbright StandGlowLayer on modern render states. */
public final class HierophantGreenRenderer extends MobRenderer<HierophantGreenEntity, HierophantGreenRenderer.State, HierophantGreenModel> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(RipplesFabric.id("hierophant_green"),"main");
    private static final Identifier TEXTURE=RipplesFabric.id("textures/entity/stand/hierophant_green.png");
    private static final Identifier GLOW=RipplesFabric.id("textures/entity/stand/glow/hierophant_green.png");
    private static BiFunction<Entity,Identifier,Identifier> textureResolver=(entity,texture)->texture;
    private static Predicate<Entity> visibility=entity->true;
    private static boolean initialized;
    private final WeakHashMap<HierophantGreenEntity,Clock> clocks=new WeakHashMap<>();
    private static final class Clock { float idleStamp, summonStamp; boolean arms; }
    public static final class State extends ArmedEntityRenderState {
        public HierophantGreenEntity.Task task=HierophantGreenEntity.Task.NONE;
        public int phase,parts=7;
        public float phaseProgress,idleTime,summonTime,opacity=1,whiteOverlay,sway;
        public boolean armsOnly,leftArm=true,rightArm=true,summoning,visible=true;
        public Identifier texture=TEXTURE,glow=GLOW;
        public int tint(){return Math.round(Math.clamp(opacity,0,1)*255)<<24|0xFFFFFF;}
    }
    public static void initialize() {
        if(initialized)return; initialized=true;
        ModelLayerRegistry.registerModelLayer(LAYER,HierophantGreenModel::createBodyLayer);
        EntityRendererRegistry.register(HierophantGreenActions.TYPE,HierophantGreenRenderer::new);
        HierophantProjectileRenderer.initialize();
        ActionCatalog.registerHeldReplacement(HierophantGreenActions.STAND_ID,(next,active)->{
            var task=HierophantGreenActions.task(Identifier.parse(active.id()));
            return task==HierophantGreenEntity.Task.BLOCK||task==HierophantGreenEntity.Task.GRAPPLE||task==HierophantGreenEntity.Task.GRAPPLE_ENTITY;
        });
    }
    /** Shared skin/audience plans install source policy without replacing these dedicated renderers. */
    public static void setTextureResolver(BiFunction<Entity,Identifier,Identifier> resolver){textureResolver=java.util.Objects.requireNonNull(resolver);}
    public static void setVisibilityPredicate(Predicate<Entity> predicate){visibility=java.util.Objects.requireNonNull(predicate);}
    public static Identifier texture(Entity entity,Identifier base){return textureResolver.apply(entity,base);}
    public static boolean visible(Entity entity){var player=Minecraft.getInstance().player;return visibility.test(entity)&&(!entity.isInvisible()||player!=null&&!entity.isInvisibleTo(player));}
    public static boolean submitFirstPerson(PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,float partial){
        var client=Minecraft.getInstance();
        if(!(client.getCameraEntity() instanceof HierophantGreenEntity stand)||!stand.manuallyControlled()||!client.options.getCameraType().isFirstPerson())return false;
        if(!(client.getEntityRenderDispatcher().getRenderer(stand) instanceof HierophantGreenRenderer renderer))return false;
        var state=renderer.createRenderState();renderer.extractRenderState(stand,state,partial);
        state.visible=visible(stand);state.armsOnly=true;state.leftArm=state.rightArm=true;
        pose.pushPose();
        pose.rotateDegrees(Axis.XP,state.xRot);pose.rotateDegrees(Axis.YP,180+state.bodyRot);
        pose.translate(0,-stand.getEyeHeight(),0);
        renderer.submit(state,pose,collector,camera);
        pose.popPose();return true;
    }
    public HierophantGreenRenderer(EntityRendererProvider.Context context) {
        super(context,new HierophantGreenModel(context.bakeLayer(LAYER)),0);
        addLayer(new ItemInHandLayer<>(this));
        addLayer(new RenderLayer<State,HierophantGreenModel>(this){
            @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,State state,float yRot,float xRot){
                if(state.visible&&state.opacity>0)collector.submitModel(getParentModel(),state,pose,RenderTypes.entityTranslucent(state.glow),0xF000F0,OverlayTexture.NO_OVERLAY,state.tint());
            }
        });
    }
    @Override public State createRenderState(){return new State();}
    @Override public Identifier getTextureLocation(State state){return state.texture;}
    @Override protected int getModelTint(State state){return state.tint();}
    @Override protected float getWhiteOverlayProgress(State state){return state.whiteOverlay;}
    @Override protected boolean shouldRenderLayers(State state){return state.visible&&state.opacity>0&&super.shouldRenderLayers(state);}
    @Override protected RenderType getRenderType(State state,boolean body,boolean translucent,boolean glowing){
        return state.visible&&state.opacity>0?RenderTypes.entityTranslucent(state.texture):null;
    }
    @Override protected void scale(State state,PoseStack pose){pose.scale(.9375F,.9375F*1.9F/1.8F,.9375F);pose.translate(0,state.sway,0);}
    @Override public void extractRenderState(HierophantGreenEntity entity,State state,float partial){
        if(TimeStopClient.isFrozen(entity))partial=0;
        super.extractRenderState(entity,state,partial); ArmedEntityRenderState.extractArmedEntityRenderState(entity,state,itemModelResolver,partial);
        state.texture=texture(entity,TEXTURE);state.glow=texture(entity,GLOW);state.visible=visible(entity);
        state.task=entity.task();state.phase=entity.taskPhase();state.phaseProgress=entity.actionDuration()>0?(entity.phaseElapsed()+partial)/entity.actionDuration():0;
        state.armsOnly=entity.isArmsOnlyMode();
        var user=entity.user();state.parts=user==null?7:StandParts.mask(user);
        boolean mainLeft=user!=null&&user.getMainArm()==HumanoidArm.LEFT;
        state.leftArm=mainLeft?entity.showMainArm():entity.showOffArm();state.rightArm=mainLeft?entity.showOffArm():entity.showMainArm();
        var clock=clocks.computeIfAbsent(entity,e->new Clock());
        if(clock.arms&&!state.armsOnly)clock.summonStamp=state.ageInTicks;
        clock.arms=state.armsOnly;
        state.summonTime=state.ageInTicks-clock.summonStamp;
        state.summoning=!state.armsOnly&&state.task==HierophantGreenEntity.Task.NONE&&state.summonTime<=20&&!entity.wasSummonedAsArms();
        boolean actionPose=state.task==HierophantGreenEntity.Task.BLOCK||state.task==HierophantGreenEntity.Task.SPLASH||state.task==HierophantGreenEntity.Task.CONCENTRATED
                ||state.task==HierophantGreenEntity.Task.GRAPPLE||state.task==HierophantGreenEntity.Task.GRAPPLE_ENTITY;
        if(actionPose||state.summoning)clock.idleStamp=state.ageInTicks;
        state.idleTime=state.ageInTicks-clock.idleStamp;
        state.whiteOverlay=state.armsOnly?0:Math.clamp((10-state.summonTime)/10,0,1);
        state.opacity=alpha(entity);
        var client=Minecraft.getInstance();var camera=client.gameRenderer.mainCamera();
        if(camera.entity()==entity&&client.options.getCameraType().isFirstPerson())state.visible=false;
        if(user==client.player&&camera.entity()==user){
            if(client.options.getCameraType().isFirstPerson()&&!state.armsOnly){
                Vec3 difference=entity.getPosition(partial).subtract(user.getPosition(partial)).multiply(1,0,1);
                double distance=difference.lengthSqr(); Vec3 look=Vec3.directionFromRotation(0,user.getViewYRot(partial));
                if(distance<.25||distance<9&&look.dot(difference)>distance/2){state.armsOnly=true;state.leftArm=state.rightArm=true;}
            }else if(!client.options.getCameraType().isFirstPerson())state.opacity*=StandCamera.opacity(entity);
        }
        state.sway=!actionPose&&!state.summoning&&entity.followingUser()&&user!=null&&!user.isShiftKeyDown()?(float)Math.sin(state.idleTime*.04F)*.04F:0;
    }
    public static float alpha(HierophantGreenEntity entity){
        float alpha=1;
        if(entity.summonLockTicks()>0)alpha=(8-entity.summonLockTicks())/8F;
        else if(entity.gradualSummonTicks()>0){
            var user=entity.user();float development=user==null?0:user.getAttachedOrCreate(StandAttachments.PROGRESS).development();
            int total=StandStatFormulas.getSummonLockTicks(entity.definition().stats().speed(development)*entity.efficiency());
            if(total>0)alpha=(float)(total-entity.gradualSummonTicks())/total;
        }
        if(entity.unsummonTicks()>=0)alpha*=Math.max(0,1-(float)entity.unsummonTicks()/entity.getUnsummonDuration());
        if(entity.user()!=null)alpha*=StandStatFormulas.rangeStrengthFactor(50,100,entity.distanceTo(entity.user()));
        return Math.clamp(alpha,0,1);
    }
}
