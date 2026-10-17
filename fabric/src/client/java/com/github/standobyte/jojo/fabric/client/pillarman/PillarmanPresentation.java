package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.pillarman.*;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Local presentation only: source senses outlines, held absorption sound and first-person aura particles. */
public final class PillarmanPresentation {
    private static Object world;private static int clock;private static boolean initialized;
    private static final Map<Entity,Integer> OUTLINES=new WeakHashMap<>();
    private static final Map<LivingEntity,AbsorptionSound> SOUNDS=new WeakHashMap<>();
    private static final List<Aura> AURA=new ArrayList<>();
    public static void initialize(){if(initialized)return;initialized=true;ClientTickEvents.END_CLIENT_TICK.register(PillarmanPresentation::tick);}
    public static void extract(Avatar entity,AvatarRenderState state,float partial){PillarmanPlayerAnimation.extract(entity,state,partial);}
    public static void apply(PlayerModel model,AvatarRenderState state){PillarmanPlayerAnimation.apply(model,state);PillarmanLayers.hideOuter(model,state);}
    public static boolean glowing(Entity entity){return entity.level()==Minecraft.getInstance().level&&OUTLINES.getOrDefault(entity,0)>clock;}
    public static void applyOutline(Entity entity,EntityRenderState state){if(glowing(entity))state.outlineColor=0xFF000000|PillarmanHud.COLOR;}
    public static void tick(Minecraft client){
        if(world!=client.level){world=client.level;clock=0;OUTLINES.clear();AURA.clear();SOUNDS.clear();PillarmanLayers.clear();PillarmanPlayerAnimation.clear();}
        if(client.level==null||client.player==null||client.isPaused())return;clock++;
        OUTLINES.entrySet().removeIf(e->e.getKey().isRemoved()||e.getKey().level()!=client.level||e.getValue()<=clock);
        var player=client.player;
        if(PillarmanService.hasPillarman(player)&&PillarmanBaseActions.sensesScanDue(player)&&!TimeStopClient.isFrozen(player)){
            double radius=PillarmanBaseActions.sensesRadius(player);Vec3 center=player.getBoundingBox().getCenter();
            // MCUtil.entitiesAround uses the cube around entity center, not a spherical distance filter.
            var box=new AABB(center.subtract(radius,radius,radius),center.add(radius,radius,radius));
            for(var entity:client.level.getEntitiesOfClass(LivingEntity.class,box,e->e!=player))OUTLINES.put(entity,clock+80);
        }
        SOUNDS.keySet().removeIf(e->e.isRemoved()||e.level()!=client.level||!absorbing(e));
        for(var entity:client.level.entitiesForRendering())if(entity instanceof LivingEntity living&&absorbing(living)&&!SOUNDS.containsKey(living)){
            var sound=new AbsorptionSound(living);SOUNDS.put(living,sound);client.getSoundManager().play(sound);
        }
        if(!TimeStopClient.isFrozen(player)){
            AURA.forEach(Aura::tick);AURA.removeIf(a->a.age>=a.life);
            if(client.getCameraEntity()==player){
                int count=PillarWindClient.firstPersonAuraCount(player);if(count>0)addAura(player,"green",count);
                count=PillarLightClient.firstPersonAuraCount(player);if(count>0)addAura(player,"rainbow",count);
                var heat=PillarHeatActions.state(player).phase();
                if(heat==PillarHeatActions.Phase.GIANT)addAura(player,"red",1);else if(heat==PillarHeatActions.Phase.DETONATION)addAura(player,"red",2);
            }
        }
    }
    private static boolean absorbing(LivingEntity user){
        if(!user.isAlive()||!PillarmanService.hasPillarman(user)||user.getAttachedOrCreate(PillarmanAttachments.ACTIONS).held()!=PillarmanBaseActions.Held.ABSORPTION.ordinal()||!user.getMainHandItem().isEmpty())return false;
        var start=user.getEyePosition();var end=start.add(user.getLookAngle().scale(2));
        var wall=user.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,user));
        double max=wall.getType()==HitResult.Type.MISS?4:start.distanceToSqr(wall.getLocation());
        for(var target:user.level().getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(1),e->e!=user&&e.isAlive()&&!e.isSpectator())){
            var box=target.getBoundingBox();var hit=box.clip(start,end);if(box.contains(start)||hit.isPresent()&&start.distanceToSqr(hit.get())<=max)return true;
        }return false;
    }
    private static final class AbsorptionSound extends AbstractTickableSoundInstance {
        private final LivingEntity user;
        AbsorptionSound(LivingEntity user){super(JojoSounds.get("pillar_man_absorption"),user.getSoundSource(),user.getRandom());this.user=user;volume=1.25F;pitch=.8F;looping=true;x=user.getX();y=user.getY();z=user.getZ();}
        @Override public boolean canPlaySound(){return !user.isSilent();}
        @Override public void tick(){if(user.level()!=Minecraft.getInstance().level||!absorbing(user)){stop();return;}x=user.getX();y=user.getY();z=user.getZ();volume=TimeStopClient.isFrozen(user)?0:1.25F;}
    }
    public static void submitHand(PoseStack pose,SubmitNodeCollector collector,ModelPart arm,int light){
        var client=Minecraft.getInstance();var player=client.player;
        if(player==null||player.isInvisible()||player.isSpectator()||!arm.visible||PillarLightActions.hidden(player))return;
        if(player.getAttachedOrCreate(PillarmanAttachments.PROGRESS).stoneForm())collector.submitModelPart(arm,pose,RenderTypes.entityTranslucent(PillarmanLayers.STONE),light,OverlayTexture.NO_OVERLAY,null);
        // Source first-person blades are an empty callback; only the active aura pseudo-particles render here.
        boolean left=arm.x>0;boolean main=(player.getMainArm()==HumanoidArm.LEFT)==left;
        if(!(main?player.getMainHandItem():player.getOffhandItem()).isEmpty())return;
        float partial=TimeStopClient.isFrozen(player)?0:client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for(var aura:AURA)if(aura.left==left){
            float age=aura.age+partial,ratio=age/aura.life;
            float alpha=.05F+(ratio<=.5F?ratio*2:(1-ratio)*2)*.3F;
            float size=aura.size*Math.clamp(ratio*32,0,1);
            double x=aura.oldX+(aura.x-aura.oldX)*partial,y=aura.oldY+(aura.y-aura.oldY)*partial,z=aura.oldZ+(aura.z-aura.oldZ)*partial;
            int frames=aura.color.equals("rainbow")?22:11,frame=1+(aura.age+aura.offset)%aura.life*(frames-1)/aura.life;
            var texture=RipplesFabric.id("textures/particle/hamon/"+aura.color+"/hamon_charge_"+aura.color+"_"+frame+".png");
            var rotation=new org.joml.Quaternionf().rotationY((left?-1:1)*(float)Math.toRadians(57.5)).rotateX((left?1:-1)*(float)Math.toRadians(62.5));
            var vertices=new org.joml.Vector3f[4];float[][] corners={{-1,-1},{-1,1},{1,1},{1,-1}};
            for(int n=0;n<4;n++)vertices[n]=new org.joml.Vector3f(corners[n][0],corners[n][1],0).rotate(rotation).mul(size).add((float)x,(float)y,(float)z);
            int color=(Math.clamp((int)(alpha*255),0,255)<<24)|0xFFFFFF;
            collector.submitCustomGeometry(pose,RenderTypes.eyes(texture),(entry,buffer)->{
                float[][] uv={{1,1},{1,0},{0,0},{0,1}};
                for(int n=0;n<4;n++)buffer.addVertex(entry,vertices[n]).setColor(color).setUv(uv[n][0],uv[n][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(entry,0,0,1);
            });
        }
    }
    public static void submitHand(PoseStack pose,SubmitNodeCollector collector,ModelPart arm,ModelPart outer,int light){
        submitHand(pose,collector,arm,light);var player=Minecraft.getInstance().player;
        if(player!=null&&!player.isInvisible()&&!player.isSpectator()&&!PillarLightActions.hidden(player)&&outer.visible
                &&player.getAttachedOrCreate(PillarmanAttachments.PROGRESS).stoneForm()){
            float old=outer.xRot;outer.xRot=0;collector.submitModelPart(outer,pose,RenderTypes.entityTranslucent(PillarmanLayers.STONE),light,OverlayTexture.NO_OVERLAY,null);outer.xRot=old;
        }
    }
    private static void addAura(LivingEntity player,String color,int count){for(boolean left:new boolean[]{false,true})for(int n=0;n<count;n++)AURA.add(new Aura(player.getRandom(),left,color));}
    private static final class Aura {
        final boolean left;final String color;final int life,offset;final float size;int age;
        double x,y,z,oldX,oldY,oldZ,dx,dy,dz;
        Aura(net.minecraft.util.RandomSource random,boolean left,String color){
            this.left=left;this.color=color;x=(random.nextDouble()*.5-.625)*(left?-1:1);y=random.nextDouble();z=random.nextDouble()*.5-.25;oldX=x;oldY=y;oldZ=z;
            life=25+random.nextInt(10);offset=random.nextInt(life);size=.1F*(random.nextFloat()*.5F+.5F)*2*.75F*(1.2F+.6F*random.nextFloat());
            var velocity=new Vec3((random.nextDouble()*2-1)*.4,(random.nextDouble()*2-1)*.4,(random.nextDouble()*2-1)*.4).normalize().scale((random.nextDouble()+random.nextDouble()+1)*.15*.4);
            dx=velocity.x*.05;dy=(velocity.y+.1)*.1;dz=velocity.z*.05;
        }
        void tick(){oldX=x;oldY=y;oldZ=z;age++;dy+=.0005;var move=new Vec3(dx,dy,dz).xRot((left?-1:1)*(float)Math.toRadians(62.5)).yRot((left?-1:1)*(float)Math.toRadians(57.5));x+=move.x;y+=move.y;z+=move.z;if(y==oldY){dx*=1.1;dz*=1.1;}dx*=.96;dy*=.96;dz*=.96;}
    }
    private PillarmanPresentation(){}
}
