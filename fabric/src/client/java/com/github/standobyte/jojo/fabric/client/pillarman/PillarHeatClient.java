package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.pillarman.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Original repeating vein model and per-tick blood/aura emissions. Player poses are shared by Pillar presentation. */
public final class PillarHeatClient {
    public static void initialize() {
        EntityRendererRegistry.register(PillarHeatActions.VEINS, context -> new PillarBodyPartRenderer<>(context,
                RipplesFabric.id("textures/entity/projectiles/pillarman_veins.png"), true));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.isPaused()) return;
            for (var entity : client.level.entitiesForRendering()) {
                if (TimeStopClient.isFrozen(entity)) continue;
                if (entity instanceof PillarVeinEntity vein) {
                    var random = vein.getRandom();
                    Vec3 offset = new Vec3(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5)
                            .normalize().scale(random.nextDouble() * 2).add(vein.getBoundingBox().getCenter());
                    client.level.addParticle(VampireBloodParticles.BLOOD, offset.x, offset.y, offset.z, 0, -1, 0);
                }
                if (!(entity instanceof LivingEntity living)) continue;
                var phase = PillarHeatActions.state(living).phase();
                int intensity = phase == PillarHeatActions.Phase.GIANT ? 6 : phase == PillarHeatActions.Phase.DETONATION ? 12 : 0;
                for (int i = 0; i < intensity; i++) aura(living, HamonParticles.AURA_RED);
                if (phase == PillarHeatActions.Phase.DETONATION) aura(living, PillarWorldContent.BOILING_BLOOD_POP);
            }
        });
    }
    private static void aura(LivingEntity entity, net.minecraft.core.particles.SimpleParticleType type) {
        var random = entity.getRandom();
        entity.level().addParticle(type, entity.getX() + (random.nextDouble() - .5) * (entity.getBbWidth() + .5),
                entity.getY() + random.nextDouble() * entity.getBbHeight() * .5,
                entity.getZ() + (random.nextDouble() - .5) * (entity.getBbWidth() + .5), 0, 0, 0);
    }
    private PillarHeatClient() { }
}
