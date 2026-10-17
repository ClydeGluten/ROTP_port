package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import java.util.*;
/** Original glider flight fade, charge sparks and health-loss foliage effects. */
final class HamonGliderPresentation {
    private static final Map<LeavesGliderEntity,Float> HEALTH=new WeakHashMap<>();
    private static final Set<LeavesGliderEntity> FLYING=Collections.newSetFromMap(new WeakHashMap<>());
    static void tick(Minecraft client) {
        if(client.level==null){HEALTH.clear();FLYING.clear();return;}
        for(var iterator=HEALTH.entrySet().iterator();iterator.hasNext();){var entry=iterator.next();if(entry.getKey().isRemoved()){if(entry.getKey().level()==client.level){leaves(entry.getKey(),200);leafSound(entry.getKey(),true);}iterator.remove();FLYING.remove(entry.getKey());}}
        for(var entity:client.level.entitiesForRendering())if(entity instanceof LeavesGliderEntity glider){
            if(TimeStopClient.isFrozen(glider))continue;
            float previous=HEALTH.getOrDefault(glider,glider.getHealth());HEALTH.put(glider,glider.getHealth());
            if(glider.getHealth()<previous){leaves(glider,Math.max(1,(int)((previous-glider.getHealth())*100)));leafSound(glider,false);}
            if(glider.isFlying()){if(FLYING.add(glider))client.getSoundManager().play(new Flight(glider));}else FLYING.remove(glider);
            float energy=glider.getEnergy()/LeavesGliderEntity.MAX_ENERGY;
            boolean charging=false;
            for(int i=0;i<glider.getPassengers().size();i++)if(glider.isCharging(i)){
                charging=true;var rider=glider.getPassengers().get(i);
                for(int side:new int[]{-1,1}){var hand=rider.getEyePosition().add(new net.minecraft.world.phys.Vec3(side*.35,-.45,.25).yRot(-rider.getYRot()*(float)Math.PI/180));
                    client.level.addParticle(VampireHamonParticles.SPARK,hand.x,hand.y,hand.z,0,.08,0);}
            }
            if(energy>0&&(charging||client.level.getRandom().nextFloat()<energy*.2F)){
                HamonPresentationClient.sparkSound(glider,energy);
                client.level.addParticle(VampireHamonParticles.SPARK,glider.getRandomX(.5),glider.getY(1),glider.getRandomZ(.5),0,.015,0);
            }
        }
    }
    private static void leaves(LeavesGliderEntity glider,int count){for(int i=0;i<count;i++)glider.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK,glider.getLeavesBlock()),glider.getRandomX(.5),glider.getY(1),glider.getRandomZ(.5),0,0,0);}
    private static void leafSound(LeavesGliderEntity glider,boolean broken){var sound=glider.getLeavesBlock().getSoundType();Minecraft.getInstance().level.playLocalSound(glider.getX(),glider.getY(1),glider.getZ(),broken?sound.getBreakSound():sound.getHitSound(),glider.getSoundSource(),(sound.getVolume()+1)/(broken?2:8),sound.getPitch()*.8F,false);}
    private static final class Flight extends AbstractTickableSoundInstance {
        private final LeavesGliderEntity glider;private int time;
        Flight(LeavesGliderEntity glider){super(JojoSounds.get("glider_flight"),SoundSource.AMBIENT,glider.getRandom());this.glider=glider;looping=true;delay=0;volume=.05F;}
        @Override public void tick(){time++;if(!glider.isAlive()||time>20&&!glider.isFlying()){stop();return;}x=glider.getX();y=glider.getY();z=glider.getZ();volume=Math.clamp((float)glider.getDeltaMovement().lengthSqr()*1.5F,0,1);volume*=Math.clamp((time-20)/20F,0,1);pitch=volume>.8F?1+volume-.8F:1;var client=Minecraft.getInstance();if(client.player!=null&&client.player.getVehicle()!=glider)volume*=Math.max(0,1-client.player.distanceTo(glider)/16F);}
    }
    private HamonGliderPresentation(){}
}
