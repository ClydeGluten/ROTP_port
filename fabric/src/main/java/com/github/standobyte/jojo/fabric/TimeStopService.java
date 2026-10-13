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
    public static final float STAMINA_UPFRONT = 225.0F;
    public static final float STAMINA_PER_TICK = 9.0F;
    public static final int MIN_CANCEL_COOLDOWN = 20;

    private static boolean initialized = false;

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
            this.ticksRemaining = CHARGE_TICKS;
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
        final int durationTicks;

        ActiveTimeStop(ServerPlayer player) {
            this.playerUuid = player.getUUID();
            this.playerId = player.getId();
            this.dimension = player.level().dimension();
            this.activationChunk = player.chunkPosition();
            this.activationBlockPos = player.blockPosition();
            this.durationTicks = ProgressionService.timeStopTicks(player);
            this.ticksRemaining = durationTicks;
            this.elapsedTicks = 0;
        }
    }

    private static final Map<UUID, ChargeState> CHARGING = new HashMap<>();
    private static final Map<UUID, ActiveTimeStop> ACTIVE_STOPS = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();

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

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();
            if (CHARGING.containsKey(uuid) || ACTIVE_STOPS.containsKey(uuid)) {
                cancel(player);
            }
            // Retain cooldown while disconnected; reconnecting cannot bypass it.
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CHARGING.clear();
            ACTIVE_STOPS.clear();
            COOLDOWNS.clear();
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
            cancel(player);
            return true;
        }

        if (isCharging(player)) {
            cancel(player);
            return true;
        }

        if (!player.isAlive() || player.isSpectator()) return false;
        if (isFrozen(player)) return false;
        if (cooldownRemaining(player) > 0) return false;

        StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (!progress.hasStarPlatinum()) return false;
        if (!ProgressionService.require(player, 4, "key.jojo.fabric.time_stop")) return false;

        StarPlatinumEntity stand = StandService.current(player);
        if (stand == null || stand.isRemoved() || !stand.allowsStaminaRegen()) return false;

        if (!player.isCreative() && progress.stamina() < STAMINA_UPFRONT) return false;

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

    public static int cooldownRemaining(ServerPlayer player) {
        if (player == null) return 0;
        return COOLDOWNS.getOrDefault(player.getUUID(), 0);
    }

    public static void cancel(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();

        if (CHARGING.containsKey(uuid)) {
            CHARGING.remove(uuid);
            COOLDOWNS.put(uuid, MIN_CANCEL_COOLDOWN);
            return;
        }

        ActiveTimeStop stop = ACTIVE_STOPS.remove(uuid);
        if (stop != null) {
            int cooldown = cooldownFor(player, stop.elapsedTicks);
            COOLDOWNS.put(uuid, cooldown);
            resumeTime(stop, player.level().getServer());
        }
    }

    public static boolean isFrozen(Entity entity) {
        if (entity == null || entity.isRemoved() || ACTIVE_STOPS.isEmpty()) return false;
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
            if (dx <= CHUNK_RANGE && dz <= CHUNK_RANGE) {
                if (level.hasChunk(entityChunk.x(), entityChunk.z())) {
                    return true;
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
            if (dx <= CHUNK_RANGE && dz <= CHUNK_RANGE) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAreaOverlapping(Level level, ChunkPos pos) {
        if (level == null || pos == null) return false;
        ResourceKey<Level> dimension = level.dimension();
        int maxOverlapDistance = CHUNK_RANGE * 2;

        for (ActiveTimeStop stop : ACTIVE_STOPS.values()) {
            if (!stop.dimension.equals(dimension)) continue;
            int dx = Math.abs(pos.x() - stop.activationChunk.x());
            int dz = Math.abs(pos.z() - stop.activationChunk.z());
            if (dx <= maxOverlapDistance && dz <= maxOverlapDistance) {
                return true;
            }
        }

        for (ChargeState charge : CHARGING.values()) {
            if (!charge.dimension.equals(dimension)) continue;
            int dx = Math.abs(pos.x() - charge.chunkPos.x());
            int dz = Math.abs(pos.z() - charge.chunkPos.z());
            if (dx <= maxOverlapDistance && dz <= maxOverlapDistance) {
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
    }

    private static void tickCharging(MinecraftServer server) {
        List<UUID> toCancel = new ArrayList<>();
        List<UUID> toComplete = new ArrayList<>();

        for (ChargeState charge : CHARGING.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(charge.playerUuid);
            if (player == null || !player.isAlive() || player.isSpectator() || !player.level().dimension().equals(charge.dimension)
                    || isFrozen(player) || !player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum()) {
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
            COOLDOWNS.put(uuid, MIN_CANCEL_COOLDOWN);
        }

        for (UUID uuid : toComplete) {
            CHARGING.remove(uuid);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                COOLDOWNS.put(uuid, MIN_CANCEL_COOLDOWN);
                continue;
            }
            StarPlatinumEntity stand = StandService.current(player);
            if (stand == null || stand.isRemoved()) {
                COOLDOWNS.put(uuid, MIN_CANCEL_COOLDOWN);
                continue;
            }
            StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
            if (isFrozen(player) || isAreaOverlapping(player.level(), player.chunkPosition()) || !progress.hasStarPlatinum()
                    || (!player.isCreative() && progress.stamina() < STAMINA_UPFRONT)) {
                COOLDOWNS.put(uuid, MIN_CANCEL_COOLDOWN);
                continue;
            }

            if (!player.isCreative()) {
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

            if (!player.isCreative()) {
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

            if (stop.ticksRemaining <= 0) {
                toEnd.add(stop.playerUuid);
                cancelledFlags.put(stop.playerUuid, false);
            }
        }

        for (UUID uuid : toEnd) {
            ActiveTimeStop stop = ACTIVE_STOPS.remove(uuid);
            if (stop == null) continue;
            boolean cancelled = cancelledFlags.getOrDefault(uuid, false);
            int cooldown = cooldownFor(server.getPlayerList().getPlayer(uuid), stop.elapsedTicks);
            COOLDOWNS.put(uuid, cooldown);
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
        StarPlatinumEntity stand = player != null ? StandService.current(player) : null;
        if (stand != null && !stand.isRemoved()) {
            stand.playOriginal("star_platinum_time_resume");
        } else if (server.getLevel(stop.dimension) != null) {
            server.getLevel(stop.dimension).playSound(null, stop.activationBlockPos.getX(), stop.activationBlockPos.getY(), stop.activationBlockPos.getZ(),
                    JojoSounds.get("star_platinum_time_resume"), SoundSource.PLAYERS, 1.0F, 1.0F);
        }

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
