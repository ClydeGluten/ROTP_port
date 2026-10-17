package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.JojoSounds;
import com.github.standobyte.jojo.fabric.VampireHamonParticles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** HamonSparkParticle and HamonUtil's original damage-scaled tracking emitters and sounds. */
public final class VampireHamonParticleClient {
    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(VampireHamonParticles.SPARK, sprites ->
                (type, level, x, y, z, dx, dy, dz, random) -> new Spark(level, x, y, z, dx, dy, dz, sprites.get(random)));
        ClientPlayNetworking.registerGlobalReceiver(VampireHamonParticles.Emitter.TYPE, (packet, context) -> {
            var client = context.client();
            if (client.level == null || !(packet.intensity() > 0) || !Float.isFinite(packet.intensity())) return;
            var entity = client.level.getEntity(packet.entityId());
            if (entity == null) return;
            float intensity = Math.min(packet.intensity(), 4);
            int count = (int) (intensity * 9.5F);
            for (int i = count; i >= 0; i--) {
                client.particleEngine.createTrackingEmitter(entity, VampireHamonParticles.SPARK, Math.max(1, count - i));
                if (i % 2 == 0 && i < 4) client.getSoundManager().play(new SparksSound(entity,
                        Math.min(intensity * 2, 1), 1 + (client.level.getRandom().nextFloat() - .5F) * .15F));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(VampireHamonParticles.ShortSpark.TYPE,(packet,context)->{
            var level=context.client().level;
            if(level==null)return;
            var entity=level.getEntity(packet.entityId());
            if(entity==null)return;
            var position=packet.position();var random=level.getRandom();
            HamonPresentationClient.sparkSound(entity,position,packet.volume());
            for(int i=0;i<packet.count();i++)level.addParticle(VampireHamonParticles.SPARK,
                    position.x+random.nextGaussian()*.05,position.y+random.nextGaussian()*.05,position.z+random.nextGaussian()*.05,
                    random.nextGaussian()*.25,random.nextGaussian()*.25,random.nextGaussian()*.25);
        });
    }

    /** Modern CritParticle's constructor is private; retain its motion with Hamon's white/fullbright override. */
    static final class Spark extends SingleQuadParticle {
        Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
            super(level, x, y, z, 0, 0, 0, sprite);
            friction = .7F; gravity = .5F;
            xd = xd * .1F + dx * .4; yd = yd * .1F + dy * .4; zd = zd * .1F + dz * .4;
            random.nextFloat(); // Preserve CritParticle's color random draw before the lifetime draw.
            quadSize *= .75F;
            lifetime = Math.max((int) (6 / (random.nextFloat() * .8 + .6)), 1);
            hasPhysics = false;
            tick();
        }
        @Override public void tick() { super.tick(); setColor(1, 1, 1); }
        @Override public float getQuadSize(float partialTick) { return quadSize * Math.clamp((age + partialTick) / lifetime * 32, 0, 1); }
        @Override protected int getLightCoords(float partialTick) { return 0xF000F0; }
        @Override protected Layer getLayer() { return Layer.OPAQUE; }
    }

    static final class SparksSound extends AbstractTickableSoundInstance {
        private final Entity entity;
        SparksSound(Entity entity, float volume, float pitch) {
            super(JojoSounds.get("hamon_sparks_long"), SoundSource.AMBIENT, entity.level().getRandom());
            this.entity = entity; this.volume = volume; this.pitch = pitch;
            x = entity.getX(); y = entity.getY(); z = entity.getZ();
        }
        @Override public void tick() {
            if (entity.isAlive()) { x = entity.getX(); y = entity.getY(); z = entity.getZ(); }
        }
    }
    private VampireHamonParticleClient() { }
}
