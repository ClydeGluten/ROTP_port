package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TimeStopService {
    public static final int CHUNK_RANGE = 12;
    public static final int CHARGE_TICKS = 40;
    public static final int STOP_TICKS = 100;
    /** Original high-lifeform maximum; STOP_TICKS remains the human cost baseline. */
    public static final int MAX_STOP_TICKS = 180;
    public static final float STAMINA_UPFRONT = 225.0F;
    public static final float STAMINA_PER_TICK = 9.0F;
    public static final int MIN_CANCEL_COOLDOWN = 20;

    private static boolean initialized = false;
    // Entity-local callbacks mirror EntityUtilCap; health and velocity are never queued or replayed.
    private static final Map<Entity, List<Runnable>> RESUME_ACTIONS = new java.util.IdentityHashMap<>();

    public static void queueOnResume(Entity entity, Runnable action) {
        if (entity == null || entity.isRemoved()) return;
        if (isFrozen(entity)) RESUME_ACTIONS.computeIfAbsent(entity, ignored -> new ArrayList<>()).add(action);
        else action.run();
    }

    static int pendingResumeActions(Entity entity) {
        return RESUME_ACTIONS.getOrDefault(entity, List.of()).size();
    }

    private static void releaseThawedActions() {
        List<Runnable> ready = new ArrayList<>();
        RESUME_ACTIONS.entrySet().removeIf(entry -> {
            Entity entity = entry.getKey();
            if (entity.isRemoved() || !(entity.level() instanceof ServerLevel level)
                    || level.getEntity(entity.getUUID()) != entity) return true;
            if (isFrozen(entity)) return false;
            ready.addAll(entry.getValue());
            return true;
        });
        ready.forEach(Runnable::run);
    }

    private static final class ChargeState {
        final UUID playerUuid;
        final ResourceKey<Level> dimension;
        final ChunkPos chunkPos;
        final BlockPos blockPos;
        int ticksRemaining;

        ChargeState(ServerPlayer player) {
            this.playerUuid = player.getUUID();
            this.dimension = player.level().dimension();
            this.chunkPos = player.chunkPosition();
            this.blockPos = player.blockPosition();
            this.ticksRemaining = player.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld() ? 30 : CHARGE_TICKS;
        }
    }

    private static final class ActiveTimeStop {
        final UUID playerUuid;
        final int playerId;
        final ResourceKey<Level> dimension;
        final ChunkPos activationChunk;
        final BlockPos activationBlockPos;
        int ticksRemaining;
        int elapsedTicks;
        boolean manuallyShortened;
        final int durationTicks;
        final String standName;

        ActiveTimeStop(ServerPlayer player) {
            this.playerUuid = player.getUUID();
            this.playerId = player.getId();
            this.dimension = player.level().dimension();
            this.activationChunk = player.chunkPosition();
            this.activationBlockPos = player.blockPosition();
            this.standName = player.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld() ? "the_world" : "star_platinum";
            this.durationTicks = ProgressionService.timeStopTicks(player);
            this.ticksRemaining = durationTicks;
            this.elapsedTicks = 0;
        }
    }

    private static final Map<UUID, ChargeState> CHARGING = new HashMap<>();
    private static final Map<UUID, ActiveTimeStop> ACTIVE_STOPS = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN_DURATIONS = new HashMap<>();

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        PayloadTypeRegistry.clientboundPlay().register(TimeStopPayload.TYPE, TimeStopPayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(TimeStopService::serverTick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayer(handler.player));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, oldLevel, newLevel) -> {
            cancel(player);
            syncPlayer(player);
        });

        // Fabric may fire disconnect from Netty's channelInactive; never mutate world state there.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();
            if (CHARGING.containsKey(uuid) || ACTIVE_STOPS.containsKey(uuid)) {
                cancel(player);
            }
            // Retain cooldown while disconnected; reconnecting cannot bypass it.
        }));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            RESUME_ACTIONS.clear();
            CHARGING.clear();
            ACTIVE_STOPS.clear();
            COOLDOWNS.clear();
            COOLDOWN_DURATIONS.clear();
        });
    }

    private static void syncPlayer(ServerPlayer player) {
        for (ActiveTimeStop stop : ACTIVE_STOPS.values()) {
            if (stop.dimension.equals(player.level().dimension())) ServerPlayNetworking.send(player,
                    new TimeStopPayload(stop.activationBlockPos, stop.dimension.identifier(), stop.playerId,
                            stop.playerUuid, TimeStopPayload.State.SYNC, stop.ticksRemaining));
        }
    }

    public static boolean toggle(ServerPlayer player) {
        if (player == null) return false;
        UUID uuid = player.getUUID();

        if (isActive(player)) {
            ActiveTimeStop stop = ACTIVE_STOPS.get(uuid);
            if (!stop.manuallyShortened && stop.ticksRemaining > 11) {
                stop.manuallyShortened = true;
                stop.ticksRemaining = 11;
                broadcastPayload(player.level().getServer(), stop.dimension, new TimeStopPayload(stop.activationBlockPos,
                        stop.dimension.identifier(), stop.playerId, stop.playerUuid, TimeStopPayload.State.SYNC, 11));
            } else cancel(player);
            return true;
        }

        if (isCharging(player)) {
            cancel(player);
            return true;
        }

        if (!player.isAlive() || player.isSpectator()) return false;
        if (isFrozen(player)) return false;
        if (cooldownRemaining(player) > 0) return false;
        if (AbilityCooldowns.remaining(player, 11) > 0) return false;

        StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (!progress.canStopTime()) return false;
        if (!ProgressionService.require(player, progress.timeStopUnlock(), "key.jojo.fabric.time_stop")) return false;

        StarPlatinumEntity stand = StandService.current(player);
        if (stand == null || stand.isRemoved() || !stand.allowsStaminaRegen()) return false;

        if (!StandConfig.staminaFree(player) && progress.stamina() < STAMINA_UPFRONT) return false;

        if (isAreaOverlapping(player.level(), player.chunkPosition())) return false;

        CHARGING.put(uuid, new ChargeState(player));
        return true;
    }

    public static boolean isCharging(ServerPlayer player) {
        return player != null && CHARGING.containsKey(player.getUUID());
    }

    public static int chargeTicksRemaining(ServerPlayer player) {
        if (player == null) return 0;
        ChargeState state = CHARGING.get(player.getUUID());
        return state != null ? state.ticksRemaining : 0;
    }

    public static boolean isActive(ServerPlayer player) {
        return player != null && ACTIVE_STOPS.containsKey(player.getUUID());
    }

    public static int ticksRemaining(ServerPlayer player) {
        if (player == null) return 0;
        ActiveTimeStop stop = ACTIVE_STOPS.get(player.getUUID());
        return stop != null ? stop.ticksRemaining : 0;
    }

    private static void setCooldown(UUID uuid, int ticks) {
        COOLDOWNS.put(uuid, ticks);
        COOLDOWN_DURATIONS.put(uuid, ticks);
    }

    public static int cooldownDuration(ServerPlayer player) {
        return cooldownRemaining(player) > 0 ? COOLDOWN_DURATIONS.getOrDefault(player.getUUID(), 0) : 0;
    }

    public static int cooldownRemaining(ServerPlayer player) {
        if (player == null) return 0;
        return COOLDOWNS.getOrDefault(player.getUUID(), 0);
    }

    public static void setCooldown(ServerPlayer player, int ticks) {
        if (player == null || ticks <= 0) return;
        UUID uuid = player.getUUID();
        int current = COOLDOWNS.getOrDefault(uuid, 0);
        if (ticks > current) {
            setCooldown(uuid, ticks);
        }
    }

    /** Releasing an original HUD hold cancels only charging, never an already active Time Stop. */
    public static void cancelCharge(ServerPlayer player) {
        if (player != null && CHARGING.containsKey(player.getUUID())) cancel(player);
    }

    public static void cancel(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();

        if (CHARGING.containsKey(uuid)) {
            CHARGING.remove(uuid);
            setCooldown(uuid, MIN_CANCEL_COOLDOWN);
            AbilityCooldowns.start(player, 11, MIN_CANCEL_COOLDOWN / 6);
            return;
        }

        ActiveTimeStop stop = ACTIVE_STOPS.remove(uuid);
        if (stop != null) {
            int cooldown = cooldownFor(player, stop.elapsedTicks);
            setCooldown(uuid, cooldown);
            AbilityCooldowns.start(player, 11, cooldown / 6);
            resumeTime(stop, player.level().getServer());
        }
    }

    public static int durationTicks(ServerPlayer player) {
        ActiveTimeStop stop = ACTIVE_STOPS.get(player.getUUID());
        return stop == null ? 0 : stop.durationTicks;
    }

    public static boolean isFrozen(Entity entity) {
        if (entity instanceof com.github.standobyte.jojo.fabric.SpaceRipperStingyEyesEntity beam && beam.bound()) return isFrozen(beam.boundOwner());
        if (entity instanceof com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity part && part.bound() && part.livingOwner() != null) return isFrozen(part.livingOwner());
        if (entity == null || entity.isRemoved() || ACTIVE_STOPS.isEmpty()) return false;
        if(entity instanceof net.minecraft.world.entity.monster.Enderman&&StandConfig.server().endermenBeyondTimeSpace())return false;
        Level level = entity.level();
        ResourceKey<Level> dimension = level.dimension();
        ChunkPos entityChunk = entity.chunkPosition();

        for (ActiveTimeStop stop : ACTIVE_STOPS.values()) {
            if (!stop.dimension.equals(dimension)) continue;

            if (entity instanceof ServerPlayer player && player.getUUID().equals(stop.playerUuid)) {
                continue;
            }

            if (entity instanceof StarPlatinumEntity stand) {
                ServerPlayer owner = stand.owner();
                if (owner != null && owner.getUUID().equals(stop.playerUuid)) {
                    continue;
                }
                if (stand.ownerId() == stop.playerId) {
                    continue;
                }
            }

            int dx = Math.abs(entityChunk.x() - stop.activationChunk.x());
            int dz = Math.abs(entityChunk.z() - stop.activationChunk.z());
            if (StandConfig.server().timeStopChunkRange()==0 || dx <= StandConfig.server().timeStopChunkRange() && dz <= StandConfig.server().timeStopChunkRange()) {
                if (level.hasChunk(entityChunk.x(), entityChunk.z())) {
                    return !(entity instanceof KnifeEntity knife) || knife.getTimeStopFlightTicks() == 0;
                }
            }
        }
        return false;
    }

    public static boolean isTimeStopped(Level level, ChunkPos pos) {
        if (level == null || pos == null || ACTIVE_STOPS.isEmpty()) return false;
        ResourceKey<Level> dimension = level.dimension();
        for (ActiveTimeStop stop : ACTIVE_STOPS.values()) {
            if (!stop.dimension.equals(dimension)) continue;
            int dx = Math.abs(pos.x() - stop.activationChunk.x());
            int dz = Math.abs(pos.z() - stop.activationChunk.z());
            if (StandConfig.server().timeStopChunkRange()==0 || dx <= StandConfig.server().timeStopChunkRange() && dz <= StandConfig.server().timeStopChunkRange()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAreaOverlapping(Level level, ChunkPos pos) {
        if (level == null || pos == null) return false;
        ResourceKey<Level> dimension = level.dimension();
        long maxOverlapDistance = (long)StandConfig.server().timeStopChunkRange() * 2;

        for (ActiveTimeStop stop : ACTIVE_STOPS.values()) {
            if (!stop.dimension.equals(dimension)) continue;
            int dx = Math.abs(pos.x() - stop.activationChunk.x());
            int dz = Math.abs(pos.z() - stop.activationChunk.z());
            if (maxOverlapDistance==0 || dx <= maxOverlapDistance && dz <= maxOverlapDistance) {
                return true;
            }
        }

        for (ChargeState charge : CHARGING.values()) {
            if (!charge.dimension.equals(dimension)) continue;
            int dx = Math.abs(pos.x() - charge.chunkPos.x());
            int dz = Math.abs(pos.z() - charge.chunkPos.z());
            if (maxOverlapDistance==0 || dx <= maxOverlapDistance && dz <= maxOverlapDistance) {
                return true;
            }
        }

        return false;
    }

    public static void serverTick(MinecraftServer server) {
        COOLDOWNS.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) return true;
            entry.setValue(remaining);
            return false;
        });

        tickActive(server);
        tickCharging(server);
        releaseThawedActions();
    }

    private static void tickCharging(MinecraftServer server) {
        List<UUID> toCancel = new ArrayList<>();
        List<UUID> toComplete = new ArrayList<>();

        for (ChargeState charge : CHARGING.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(charge.playerUuid);
            if (player == null || !player.isAlive() || player.isSpectator() || !player.level().dimension().equals(charge.dimension)
                    || isFrozen(player) || !player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()) {
                toCancel.add(charge.playerUuid);
                continue;
            }
            StarPlatinumEntity stand = StandService.current(player);
            if (stand == null || stand.isRemoved() || !stand.allowsStaminaRegen()) {
                toCancel.add(charge.playerUuid);
                continue;
            }

            charge.ticksRemaining--;
            if (charge.ticksRemaining <= 0) {
                toComplete.add(charge.playerUuid);
            }
        }

        for (UUID uuid : toCancel) {
            CHARGING.remove(uuid);
            setCooldown(uuid, MIN_CANCEL_COOLDOWN);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                AbilityCooldowns.start(player, 11, MIN_CANCEL_COOLDOWN / 6);
            }
        }

        for (UUID uuid : toComplete) {
            CHARGING.remove(uuid);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                setCooldown(uuid, MIN_CANCEL_COOLDOWN);
                continue;
            }
            StarPlatinumEntity stand = StandService.current(player);
            if (stand == null || stand.isRemoved()) {
                setCooldown(uuid, MIN_CANCEL_COOLDOWN);
                continue;
            }
            StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
            if (isFrozen(player) || isAreaOverlapping(player.level(), player.chunkPosition()) || !progress.hasStand()
                    || (!StandConfig.staminaFree(player) && progress.stamina() < STAMINA_UPFRONT)) {
                setCooldown(uuid, MIN_CANCEL_COOLDOWN);
                continue;
            }

            if (!StandConfig.staminaFree(player)) {
                player.setAttached(StandAttachments.PROGRESS, progress.withStamina(progress.stamina() - STAMINA_UPFRONT));
            }

            ActiveTimeStop stop = new ActiveTimeStop(player);
            ACTIVE_STOPS.put(uuid, stop);
            stand.playOriginal("star_platinum_time_stop");

            TimeStopPayload payload = new TimeStopPayload(
                    stop.activationBlockPos,
                    stop.dimension.identifier(),
                    stop.playerId,
                    stop.playerUuid,
                    TimeStopPayload.State.START,
                    stop.ticksRemaining
            );
            broadcastPayload(server, stop.dimension, payload);
        }
    }

    private static void tickActive(MinecraftServer server) {
        List<UUID> toEnd = new ArrayList<>();
        Map<UUID, Boolean> cancelledFlags = new HashMap<>();

        for (ActiveTimeStop stop : ACTIVE_STOPS.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(stop.playerUuid);
            if (player == null || !player.isAlive() || player.isSpectator() || !player.level().dimension().equals(stop.dimension)) {
                toEnd.add(stop.playerUuid);
                cancelledFlags.put(stop.playerUuid, true);
                continue;
            }
            StarPlatinumEntity stand = StandService.current(player);
            if (stand == null || stand.isRemoved()) {
                toEnd.add(stop.playerUuid);
                cancelledFlags.put(stop.playerUuid, true);
                continue;
            }

            if (!StandConfig.staminaFree(player)) {
                StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
                float cost = STAMINA_PER_TICK * STOP_TICKS / stop.durationTicks;
                if (progress.stamina() < cost) {
                    toEnd.add(stop.playerUuid);
                    cancelledFlags.put(stop.playerUuid, true);
                    continue;
                }
                player.setAttached(StandAttachments.PROGRESS, progress.withStamina(progress.stamina() - cost));
            }

            stop.elapsedTicks++;
            stop.ticksRemaining--;
            // TimeStopInstance.tickSounds starts the original resume cue ten ticks before thaw.
            if (stop.ticksRemaining == 10) stand.playOriginal("star_platinum_time_resume");

            if (stop.ticksRemaining <= 0) {
                toEnd.add(stop.playerUuid);
                cancelledFlags.put(stop.playerUuid, false);
            }
        }

        for (UUID uuid : toEnd) {
            ActiveTimeStop stop = ACTIVE_STOPS.remove(uuid);
            if (stop == null) continue;
            boolean cancelled = cancelledFlags.getOrDefault(uuid, false);
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            int cooldown = cooldownFor(p, stop.elapsedTicks);
            setCooldown(uuid, cooldown);
            if (p != null) {
                AbilityCooldowns.start(p, 11, cooldown / 6);
            }
            resumeTime(stop, server);
        }
    }

    private static int cooldownFor(ServerPlayer player, int elapsed) {
        if (player != null && player.isCreative()) return 0;
        return elapsed * (player != null && ProgressionService.state(player).isBurst() ? 1 : 3);
    }

    private static void resumeTime(ActiveTimeStop stop, MinecraftServer server) {
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(stop.playerUuid);
        if (player != null) ProgressionService.trainTimeStop(player, stop.elapsedTicks);
        // Tick cancellation preserves momentum. Keep any new impact impulse for the resumed tick.

        TimeStopPayload payload = new TimeStopPayload(
                stop.activationBlockPos,
                stop.dimension.identifier(),
                stop.playerId,
                stop.playerUuid,
                TimeStopPayload.State.RESUME,
                0
        );
        broadcastPayload(server, stop.dimension, payload);
        releaseThawedActions();
    }

    private static void broadcastPayload(MinecraftServer server, ResourceKey<Level> dimension, TimeStopPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(dimension)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private TimeStopService() {}
}
