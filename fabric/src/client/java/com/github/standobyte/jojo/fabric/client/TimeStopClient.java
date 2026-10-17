package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.StarPlatinumEntity;
import com.github.standobyte.jojo.fabric.TimeStopPayload;
import com.github.standobyte.jojo.fabric.TimeStopService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Independent regional stops; one distant caster resuming cannot clear another caster's freeze. */
public final class TimeStopClient {
    private static final class Stop {
        final BlockPos origin;
        final Identifier dimension;
        final UUID caster;
        final int casterId;
        int ticks;
        Stop(TimeStopPayload payload) {
            origin = payload.origin(); dimension = payload.dimension(); caster = payload.casterUuid();
            casterId = payload.casterId(); ticks = Math.clamp(payload.ticksRemaining(), 0, TimeStopService.MAX_STOP_TICKS);
        }
        boolean contains(Entity entity) {
            return dimension.equals(entity.level().dimension().identifier()) && contains(entity.blockPosition());
        }
        boolean contains(BlockPos pos) {
            return com.github.standobyte.jojo.fabric.StandConfig.current(true).timeStopChunkRange()==0 || Math.abs((pos.getX() >> 4) - (origin.getX() >> 4)) <= com.github.standobyte.jojo.fabric.StandConfig.current(true).timeStopChunkRange()
                    && Math.abs((pos.getZ() >> 4) - (origin.getZ() >> 4)) <= com.github.standobyte.jojo.fabric.StandConfig.current(true).timeStopChunkRange();
        }
        boolean immune(Entity entity) {
            return entity instanceof net.minecraft.world.entity.monster.Enderman && com.github.standobyte.jojo.fabric.StandConfig.current(true).endermenBeyondTimeSpace()
                    || entity.getUUID().equals(caster) || entity.getId() == casterId
                    || entity instanceof StarPlatinumEntity stand && stand.ownerId() == casterId;
        }
    }
    private static final Map<UUID, Stop> STOPS = new HashMap<>();
    private static boolean initialized;

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(TimeStopPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (payload.state() == TimeStopPayload.State.RESUME || payload.ticksRemaining() <= 0) STOPS.remove(payload.casterUuid());
            else STOPS.put(payload.casterUuid(), new Stop(payload));
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> reset()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) { reset(); return; }
            Identifier dimension = client.level.dimension().identifier();
            STOPS.values().removeIf(stop -> !stop.dimension.equals(dimension));
            if (!client.isPaused()) for (Stop stop : STOPS.values()) stop.ticks = Math.max(0, stop.ticks - 1);
            // Only an authoritative resume ends a stop. Client/server tick rates can diverge.
        });
    }
    public static boolean isActive() {
        Entity player = Minecraft.getInstance().player;
        return player != null && STOPS.values().stream().anyMatch(stop -> stop.contains(player));
    }
    public static int ticksRemaining() {
        Entity player = Minecraft.getInstance().player;
        return player == null ? 0 : STOPS.values().stream().filter(stop -> stop.contains(player)).mapToInt(stop -> stop.ticks).max().orElse(0);
    }
    public static boolean isFrozen(Entity entity) {
        if (entity instanceof com.github.standobyte.jojo.fabric.SpaceRipperStingyEyesEntity beam && beam.bound()) return isFrozen(beam.boundOwner());
        return entity != null && !entity.isRemoved()
                && (!(entity instanceof com.github.standobyte.jojo.fabric.KnifeEntity knife) || knife.getTimeStopFlightTicks() == 0) && STOPS.values().stream().anyMatch(stop -> stop.contains(entity) && !stop.immune(entity));
    }
    public static boolean isTimeStopped(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return level != null && STOPS.values().stream().anyMatch(stop -> stop.dimension.equals(level.dimension().identifier()) && stop.contains(pos));
    }
    public static void reset() { STOPS.clear(); }
    private TimeStopClient() { }
}
