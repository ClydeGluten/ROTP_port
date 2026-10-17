package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.VampireBloodParticles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Retains BloodParticle/BloodFromEntityParticle's sprites, sticking, gravity and water/rain decay. */
public final class VampireBloodParticleClient {
    private static SpriteSet sprites;
    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(VampireBloodParticles.BLOOD, spriteSet -> {
            sprites = spriteSet;
            return (type, level, x, y, z, dx, dy, dz, random) -> new BloodParticle(level, x, y, z, dx, dy, dz, null, false);
        });
        ClientPlayNetworking.registerGlobalReceiver(VampireBloodParticles.Burst.TYPE, (packet, context) -> {
            var client = context.client();
            if (client.level == null || sprites == null) return;
            if (client.gameRenderer.mainCamera().position().distanceToSqr(packet.source()) >= 1024
                    || client.options.particles().get() == ParticleStatus.MINIMAL) return;
            var excluded = client.level.getEntity(packet.entityId());
            Vec3 aimed = packet.destination() == null ? null : packet.destination().subtract(packet.source()).normalize().scale(packet.speed());
            for (int i = 0; i < packet.count(); i++) {
                if (client.options.particles().get() == ParticleStatus.DECREASED && client.level.getRandom().nextInt(3) == 0) continue;
                Vec3 velocity = aimed;
                if (velocity == null) {
                    double pitch = (client.level.getRandom().nextFloat() - 0.5) * Math.PI;
                    double yaw = client.level.getRandom().nextFloat() * Math.PI * 2;
                    velocity = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).scale(packet.speed());
                }
                var position = packet.source();
                client.particleEngine.add(new BloodParticle(client.level, position.x, position.y, position.z,
                        velocity.x, velocity.y, velocity.z, excluded, true));
            }
        });
    }
    private static final class BloodParticle extends SingleQuadParticle {
        private int waterDownTicks;
        private Entity attached;
        private Vec3 offset;
        private final Entity excluded;
        private final boolean fromEntity;
        BloodParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, Entity excluded, boolean fromEntity) {
            super(level, x, y, z, sprites.get(level.getRandom()));
            this.excluded = excluded;
            this.fromEntity = fromEntity;
            xd = dx + (random.nextDouble() * 2 - 1) * 0.1;
            yd = dy + (random.nextDouble() * 2 - 1) * 0.1;
            zd = dz + (random.nextDouble() * 2 - 1) * 0.1;
            lifetime = 60;
            scale(0.5F);
            gravity = 1;
        }
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
        @Override public void tick() {
            xo = x; yo = y; zo = z;
            float ageRatio = (float) (age + waterDownTicks) / lifetime;
            setAlpha(Math.min((1 - ageRatio) * 4, 1));
            if (age++ + waterDownTicks >= lifetime) { remove(); return; }
            if (attached == null) {
                var box = fromEntity ? getBoundingBox() : getBoundingBox().move(xd * 4, yd * 4, zd * 4);
                var entities = level.getEntities(excluded, box, entity -> !BuiltInRegistries.ENTITY_TYPE
                        .getKey(entity.getType()).toString().equals("jojo:cd_blood_cutter"));
                if (!entities.isEmpty()) {
                    attached = entities.getFirst();
                    offset = attached.position().subtract(x, y, z);
                }
            }
            if (attached != null) {
                if (!attached.isAlive()) attached = null;
                else {
                    var pos = attached.position().subtract(offset);
                    setPos(pos.x, pos.y, pos.z);
                }
            } else {
                if (age >= 5 && !(xd == 0 && yd == 0 && zd == 0)) yd -= 0.04 * gravity;
                double previousX = xd, previousY = yd, previousZ = zd;
                move(xd, yd, zd);
                double small = 1.0E-5;
                if (Math.abs(previousX) >= small && Math.abs(xd) < small
                        || Math.abs(previousY) >= small && Math.abs(yd) < small
                        || Math.abs(previousZ) >= small && Math.abs(zd) < small) xd = yd = zd = 0;
            }
            var pos = BlockPos.containing(getBoundingBox().getCenter());
            if (!level.getFluidState(pos).isEmpty()) waterDownTicks = lifetime;
            else if (level.isRainingAt(pos)) waterDownTicks += lifetime / 10;
        }
    }
    private VampireBloodParticleClient() { }
}
