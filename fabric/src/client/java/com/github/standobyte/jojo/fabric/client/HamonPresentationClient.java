package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.*;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Tracking presentation for the original particle-only entities and held Hamon energy sound. */
public final class HamonPresentationClient {
    private static final Map<LivingEntity,EnergySound> SOUNDS=new WeakHashMap<>();
    private static final Map<Entity,Integer> SPARK_DELAY=new WeakHashMap<>();
    private static final Set<LivingEntity> WATER_WALKING=Collections.newSetFromMap(new WeakHashMap<>());
    public static void initialize(){
        HamonParticleClient.initialize();
        EntityRendererRegistry.register(HamonEntities.BUBBLE,HamonProjectileRenderer::new);
        EntityRendererRegistry.register(HamonEntities.CUTTER,HamonProjectileRenderer::new);
        EntityRendererRegistry.register(HamonEntities.BUBBLE_CUTTER,HamonProjectileRenderer::new);
        EntityRendererRegistry.register(HamonEntities.BARRIER,HamonProjectileRenderer::new);
        EntityRendererRegistry.register(HamonEntities.TURQUOISE,HamonProjectileRenderer::new);
        EntityRendererRegistry.register(HamonEntities.ZOOM,HamonProjectileRenderer::new);
        EntityRendererRegistry.register(HamonEntities.SNAKE,HamonProjectileRenderer::new);
        EntityRendererRegistry.register(HamonEntities.SHIELD,HamonShieldRenderer::new);
        EntityRendererRegistry.register(HamonEntities.AFTERIMAGE,HamonAfterimageRenderer::new);
        EntityRendererRegistry.register(HamonEntities.SENDO,EmptyRenderer::new);
        EntityRendererRegistry.register(HamonEntities.BLOCK_CHARGE,EmptyRenderer::new);
    }
    public static void tick(Minecraft client){
        HamonGliderPresentation.tick(client);
        HamonWallAnimation.tick(client);
        if(client.level==null||client.isPaused())return;
        for(var entity:client.level.entitiesForRendering()){
            if(TimeStopClient.isFrozen(entity))continue;
            var charge=entity.getAttached(HamonCharges.CHARGE);
            if(charge!=null&&charge.remaining()>=0){
                float ratio=charge.initial()==Integer.MAX_VALUE?1:Math.clamp((float)charge.remaining()/Math.max(1,charge.initial()),.25F,1);
                if(!charge.projectile()||client.level.getRandom().nextFloat()<ratio)client.level.addParticle(VampireHamonParticles.SPARK,entity.getRandomX(.5),entity.getRandomY(),entity.getRandomZ(.5),0,.015,0);
                sparkSound(entity,charge.projectile()?ratio:1);
            }
            if(entity instanceof HamonBlockChargeEntity){client.level.addParticle(VampireHamonParticles.SPARK,entity.getX()+client.level.getRandom().nextDouble(),entity.getY()+client.level.getRandom().nextDouble(),entity.getZ()+client.level.getRandom().nextDouble(),0,.015,0);sparkSound(entity,1);}
            if(entity instanceof LivingEntity living&&HamonService.hasHamon(living)){
                var action=HamonActionService.state(living);
                boolean breath=action.held().equals("hamon_breath");
                if(breath&&!SOUNDS.containsKey(living)){var sound=new EnergySound(living);SOUNDS.put(living,sound);client.getSoundManager().play(sound);}
                if(!breath)SOUNDS.remove(living);
                boolean walking=HamonMovement.isWalkingOnLiquid(living);
                if(walking){
                    if(WATER_WALKING.add(living)){
                        for(int i=0;i<11;i++)client.level.addParticle(VampireHamonParticles.SPARK,living.getX()+living.getRandom().nextGaussian()*.05,living.getY()+living.getRandom().nextGaussian()*.05,living.getZ()+living.getRandom().nextGaussian()*.05,living.getRandom().nextGaussian()*.25,living.getRandom().nextGaussian()*.25,living.getRandom().nextGaussian()*.25);
                        client.level.playLocalSound(living.getX(),living.getY(),living.getZ(),JojoSounds.get("hamon_spark"),living.getSoundSource(),.1F,1+(living.getRandom().nextFloat()-.5F)*.15F,false);
                    }else{
                        sparkSound(living,1);
                        client.level.addParticle(VampireHamonParticles.SPARK,living.getRandomX(.5),living.getY(living.getRandom().nextDouble()*.1),living.getRandomZ(.5),living.getRandom().nextGaussian()*.25,living.getRandom().nextGaussian()*.25,living.getRandom().nextGaussian()*.25);
                    }
                }else WATER_WALKING.remove(living);
                var progress=HamonService.state(living);
                float energy=progress.energy();
                if(action.held().equals("hamon_sunlight_yellow_overdrive"))energy=(energy+action.spentEnergy())*2;
                float particles=energy/Math.max(1,progress.maxBreathStability())*progress.damageMultiplier();
                var random=client.level.getRandom();int count=(int)particles+(random.nextFloat()<particles%1?1:0);
                var aura=aura(living,action,progress);
                for(int i=0;i<count;i++)client.level.addParticle(aura,living.getX()+(random.nextDouble()-.5)*(living.getBbWidth()+.5),living.getY()+random.nextDouble()*living.getBbHeight()*.5,living.getZ()+(random.nextDouble()-.5)*(living.getBbWidth()+.5),0,0,0);
                if(breath||!action.held().isEmpty()||!action.continuous().isEmpty()||action.protection()){
                    for(int side:new int[]{-1,1}){
                        Vec3 hand=living.getEyePosition().add(new Vec3(side*.35,-.5,.3).yRot(-living.getYRot()*(float)Math.PI/180));
                        client.level.addParticle(spark(aura),hand.x,hand.y,hand.z,0,.015,0);
                        if(breath&&living==client.player&&random.nextFloat()<particles/5)client.level.addParticle(aura,hand.x,hand.y,hand.z,0,.004,0);
                    }
                }
            }
            if(entity instanceof HamonSendoEntity sendo){
                float radius=sendo.radius();var center=sendo.getBoundingBox().getCenter();
                if(radius>0&&sendo.tickCount%4==0&&sendo.tickCount<=sendo.waves()*4){
                    double rotation=Math.toRadians(sendo.getYRot()+90),minimum=sendo.axis()==net.minecraft.core.Direction.Axis.Y?rotation-sendo.arc()/2:-Math.PI,maximum=sendo.axis()==net.minecraft.core.Direction.Axis.Y?rotation+sendo.arc()/2:Math.PI;
                    for(int side:new int[]{-1,1}){
                        var offset=switch(sendo.axis()){case X->new Vec3(side*.55,0,0);case Y->new Vec3(0,side*.55,0);case Z->new Vec3(0,0,side*.55);};var origin=center.add(offset);
                        for(double angle=minimum;angle<maximum;angle+=Math.PI*.2/radius){
                            Vec3 v=switch(sendo.axis()){case X->new Vec3(0,Math.sin(angle),Math.cos(angle));case Y->new Vec3(Math.cos(angle),0,Math.sin(angle));case Z->new Vec3(Math.sin(angle),Math.cos(angle),0);};v=v.scale(radius/15);
                            client.level.addParticle(switch(sendo.axis()){case X->HamonParticles.SENDO_X;case Y->HamonParticles.SENDO_Y;case Z->HamonParticles.SENDO_Z;},origin.x,origin.y,origin.z,v.x,v.y,v.z);
                        }
                    }
                }
                var box=sendo.getBoundingBox();var camera=client.gameRenderer.mainCamera().position();
                sparkSound(sendo,new Vec3(Math.clamp(camera.x,box.minX,box.maxX),Math.clamp(camera.y,box.minY,box.maxY),Math.clamp(camera.z,box.minZ,box.maxZ)),1);
            }
            if(entity instanceof HamonProjectileEntity p&&p.kind()==HamonProjectileEntity.Kind.TURQUOISE){
                float radius=p.radius();var center=p.getBoundingBox().getCenter();
                for(int i=0;i<Math.max(8,(int)(radius*16));i++){
                    double a=client.level.getRandom().nextDouble()*Math.PI*2,b=client.level.getRandom().nextDouble()*Math.PI;
                    client.level.addParticle(HamonParticles.SPARK_BLUE,center.x+Math.cos(a)*Math.sin(b)*radius,center.y+Math.cos(b)*radius,center.z+Math.sin(a)*Math.sin(b)*radius,0,0,0);
                }
            }
        }
    }
    static void sparkSound(Entity entity,float volume){
        sparkSound(entity,entity.position(),volume);
    }
    static void sparkSound(Entity entity,Vec3 position,float volume){
        var client=Minecraft.getInstance();
        if(client.player==null||client.gameRenderer.mainCamera().position().distanceToSqr(position)>256||entity.tickCount<SPARK_DELAY.getOrDefault(entity,0))return;
        client.level.playLocalSound(position.x,position.y,position.z,JojoSounds.get("hamon_spark_short"),net.minecraft.sounds.SoundSource.AMBIENT,volume,1,false);
        SPARK_DELAY.put(entity,entity.tickCount+3+client.level.getRandom().nextInt(3));
    }
    private static net.minecraft.core.particles.SimpleParticleType spark(net.minecraft.core.particles.SimpleParticleType aura){
        if(aura==HamonParticles.AURA_BLUE)return HamonParticles.SPARK_BLUE;if(aura==HamonParticles.AURA_YELLOW)return HamonParticles.SPARK_YELLOW;if(aura==HamonParticles.AURA_RED)return HamonParticles.SPARK_RED;if(aura==HamonParticles.AURA_SILVER)return HamonParticles.SPARK_SILVER;return VampireHamonParticles.SPARK;
    }
    private static net.minecraft.core.particles.SimpleParticleType aura(LivingEntity user,HamonActionState action,com.github.standobyte.jojo.core.HamonProgress progress){
        String used=action.lastUsed();
        if(action.held().equals("hamon_sunlight_yellow_overdrive")||action.held().equals("jonathan_syo_barrage")||action.continuous().equals("jonathan_syo_barrage"))return HamonParticles.AURA_YELLOW;
        if(action.held().equals("jonathan_scarlet_overdrive"))return HamonParticles.AURA_RED;
        if(progress.energy()>0)switch(used){case "hamon_turquoise_blue_overdrive":return HamonParticles.AURA_BLUE;case "hamon_sunlight_yellow_overdrive":return HamonParticles.AURA_YELLOW;case "jonathan_scarlet_overdrive":return HamonParticles.AURA_RED;case "jonathan_metal_silver_overdrive","jonathan_metal_silver_overdrive_weapon":return HamonParticles.AURA_SILVER;}
        if(progress.hasSkill("metal_silver_overdrive")&&user.getMainHandItem().has(net.minecraft.core.component.DataComponents.WEAPON))return HamonParticles.AURA_SILVER;
        if(progress.hasSkill("turquoise_blue_overdrive")&&user.isUnderWater())return HamonParticles.AURA_BLUE;
        return HamonParticles.AURA;
    }
    private static final class EmptyRenderer<T extends Entity> extends EntityRenderer<T,EntityRenderState>{
        EmptyRenderer(EntityRendererProvider.Context context){super(context);}
        @Override public EntityRenderState createRenderState(){return new EntityRenderState();}
    }
    private static final class EnergySound extends AbstractTickableSoundInstance {
        private final LivingEntity entity;private boolean stoppedBreath;
        EnergySound(LivingEntity entity){super(JojoSounds.get("hamon_concentration"),entity.getSoundSource(),entity.getRandom());this.entity=entity;volume=0;pitch=1;x=entity.getX();y=entity.getY();z=entity.getZ();}
        @Override public void tick(){
            if(!entity.isAlive()){stop();return;}
            x=entity.getX();y=entity.getY();z=entity.getZ();
            stoppedBreath|=!HamonActionService.state(entity).held().equals("hamon_breath");
            if(stoppedBreath){volume=Math.max(0,volume-.1F);if(volume==0)stop();}
            else{var s=HamonService.state(entity);volume=s.energy()/Math.max(1,s.maxBreathStability());}
        }
    }
    private HamonPresentationClient(){}
}
