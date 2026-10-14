package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.StandTimeSkipPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.Entity;
import java.util.IdentityHashMap;
import java.util.Map;

/** Instant relocation must not interpolate through intervening space. No entity simulation is skipped forward. */
public final class TimeSkipClient {
    private static final Map<Entity, Integer> NO_LERP = new IdentityHashMap<>();
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(StandTimeSkipPayload.TYPE, (p, context) -> {
            var level = context.client().level;
            if (level == null || !level.dimension().identifier().equals(p.dimension()) || p.ticks() < 0 || p.ticks() > 100) return;
            Entity entity = level.getEntity(p.entityId());
            if (entity == null || !entity.getUUID().equals(p.uuid())) return;
            entity.getInterpolation().cancel();
            entity.snapTo(p.position(), p.yaw(), p.pitch());
            // Leave the vanilla tracking baseline intact: its next relative packet still starts at the old position.
            entity.setOldPosAndRot();
            entity.tickCount += p.ticks();
            if (entity == context.client().player) StandCamera.adoptServerFacing(p.yaw(), p.pitch());
            NO_LERP.put(entity, 3);
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.level == null) { NO_LERP.clear(); return; }
            if (client.isPaused()) return;
            NO_LERP.entrySet().removeIf(entry -> {
                Entity entity = entry.getKey();
                if (entity.isRemoved() || entity.level() != client.level) return true;
                var interpolation = entity.getInterpolation();
                if (interpolation.hasActiveInterpolation()) {
                    var target = interpolation.target();
                    entity.snapTo(target.position(), target.yRot(), target.xRot());
                    interpolation.cancel();
                    entity.setOldPosAndRot();
                }
                int remaining = entry.getValue() - 1;
                entry.setValue(remaining);
                return remaining <= 0;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> NO_LERP.clear()));
    }
    private TimeSkipClient() { }
}
