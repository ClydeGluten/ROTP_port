package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.HamonMapPurchasePayload;
import com.github.standobyte.jojo.fabric.JojoSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** TrEntitySpecialEffectPacket, MenacingParticleEmitter and OnomatopoeiaParticle's original trade effect. */
public final class HamonMapClient {
    private static final List<Emitter> EMITTERS=new ArrayList<>();
    public static void initialize(){
        ParticleProviderRegistry.getInstance().register(HamonMapPurchasePayload.MENACING,sprites->
                (type,level,x,y,z,dx,dy,dz,random)->new Glyph(level,x,y,z,dx,dy,dz,sprites.get(random)));
        ClientPlayNetworking.registerGlobalReceiver(HamonMapPurchasePayload.TYPE,(payload,context)->{
            var client=context.client();if(client.level==null||client.player==null)return;
            var merchant=client.level.getEntity(payload.merchantId());if(merchant==null)return;
            if(client.player.getId()==payload.buyerId())client.getSoundManager().play(new SimpleSoundInstance(
                    JojoSounds.get("map_bought_hamon_temple").location(),SoundSource.RECORDS,1,1,client.level.getRandom(),
                    false,0,SoundInstance.Attenuation.NONE,0,0,0,true));
            var emitter=new Emitter(merchant,client.player.position());emitter.tick(client);EMITTERS.add(emitter);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.level==null){EMITTERS.clear();return;}
            if(client.isPaused())return;
            EMITTERS.removeIf(emitter->emitter.entity.level()!=client.level||emitter.tick(client));
        });
    }
    private static final class Emitter {
        final Entity entity;final Vec3 first,second;int life;
        Emitter(Entity entity,Vec3 observer){
            this.entity=entity;var direction=entity.position().subtract(observer);
            float rotation=(float)(Math.PI/2+Mth.atan2(direction.x,direction.z));
            first=new Vec3(0,0,entity.getBbWidth()).yRot(rotation);second=new Vec3(0,0,-entity.getBbWidth()).yRot(rotation);
        }
        boolean tick(Minecraft client){
            if(life%40==0)for(var offset:List.of(first,second)){
                var position=entity.position().add(offset);
                var particle=client.particleEngine.createParticle(HamonMapPurchasePayload.MENACING,position.x,position.y,position.z,0,.01,0);
                if(particle!=null)particle.setLifetime(200);
            }
            return ++life>=200;
        }
    }
    private static final class Glyph extends SingleQuadParticle {
        private double offsetX,offsetY,offsetZ;
        Glyph(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,TextureAtlasSprite sprite){
            super(level,x,y,z,dx,dy,dz,sprite);quadSize=.12F+random.nextFloat()*.06F;hasPhysics=false;xd=dx;yd=dy;zd=dz;lifetime=400;
        }
        @Override public void tick(){
            xo=x;yo=y;zo=z;
            if(age++>=lifetime)remove();
            else{
                double dx=xd-offsetX,dy=yd-offsetY,dz=zd-offsetZ;
                offsetX=(random.nextDouble()-.5)*.02;offsetY=(random.nextDouble()-.5)*.01;offsetZ=(random.nextDouble()-.5)*.02;
                move(dx+offsetX,dy+offsetY,dz+offsetZ);
            }
            alpha=Mth.clamp((float)lifetime/age*3-3,0,1);
        }
        @Override protected Layer getLayer(){return Layer.TRANSLUCENT;}
    }
    private HamonMapClient(){}
}
