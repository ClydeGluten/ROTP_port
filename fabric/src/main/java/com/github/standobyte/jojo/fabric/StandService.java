package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** All mutation runs on the server thread. Manifestations never survive their player session. */
public final class StandService {
    private static final Map<UUID, StarPlatinumEntity> SUMMONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_TOGGLE = new HashMap<>();

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive()) { dismiss(player); continue; }
                // Reconcile every tick so idle, recovery, dimension changes and expired leases drop the penalty promptly.
                StandMovement.update(player);
                if (TimeStopService.isFrozen(player)) continue;
                StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
                if (!progress.hasStarPlatinum()) continue;
                StarPlatinumEntity stand = current(player);
                StandProgress next = progress.regenerate(stand != null, (stand == null || stand.allowsStaminaRegen()) && !TimeStopService.isCharging(player) && !TimeStopService.isActive(player),
                        player.getFoodData().getFoodLevel(), player.isCreative());
                if (next != progress) player.setAttached(StandAttachments.PROGRESS, next);
            }
            SUMMONS.values().removeIf(StarPlatinumEntity::isRemoved);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            dismiss(handler.player);
            LAST_TOGGLE.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SUMMONS.clear(); LAST_TOGGLE.clear(); AbilityCooldowns.clear(); });
    }

    public static StarPlatinumEntity current(ServerPlayer player) {
        StarPlatinumEntity stand = SUMMONS.get(player.getUUID());
        if (stand != null && (stand.isRemoved() || stand.level() != player.level() || stand.owner() != player)) {
            stand.discard();
            SUMMONS.remove(player.getUUID());
            return null;
        }
        return stand;
    }

    public static boolean toggle(ServerPlayer player) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        if (!player.isAlive() || player.isSpectator() || !player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum()) return false;
        StarPlatinumEntity existing = current(player);
        if (existing != null && existing.actionPhase() == StarPlatinumEntity.PHASE_GUARD_BROKEN) return false;
        long tick = player.level().getGameTime();
        Long previous = LAST_TOGGLE.get(player.getUUID());
        if (previous != null && tick - previous < 5) return false;
        LAST_TOGGLE.put(player.getUUID(), tick);
        if (current(player) != null) { dismiss(player); return true; }
        StarPlatinumEntity stand = new StarPlatinumEntity(RipplesFabric.STAR_PLATINUM, player.level());
        stand.setOwner(player);
        if (!player.level().addFreshEntity(stand)) return false;
        SUMMONS.put(player.getUUID(), stand);
        stand.playOriginal("star_platinum_summon");
        return true;
    }

    public static void dismiss(ServerPlayer player) {
        TimeStopService.cancel(player);
        StandMovement.clear(player);
        StarPlatinumEntity stand = SUMMONS.remove(player.getUUID());
        if (stand != null && !stand.isRemoved()) {
            stand.playOriginal("star_platinum_unsummon");
            stand.discard();
        }
    }

    public static boolean attack(ServerPlayer player, boolean heavy) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StarPlatinumEntity stand = current(player);
        return stand != null && movementSynced(player, stand.beginAttack(heavy));
    }

    public static boolean startHeld(ServerPlayer player, boolean guard) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StarPlatinumEntity stand = current(player);
        return stand != null && movementSynced(player, stand.beginHeld(guard));
    }

    public static boolean special(ServerPlayer player, int phase) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StarPlatinumEntity stand = current(player);
        return stand != null && movementSynced(player, stand.beginSpecial(phase));
    }

    public static boolean uppercut(ServerPlayer player) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StarPlatinumEntity stand = current(player);
        return stand != null && movementSynced(player, stand.beginUppercut());
    }

    public static boolean startControl(ServerPlayer player) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StarPlatinumEntity stand = current(player);
        return stand != null && movementSynced(player, stand.startControl());
    }

    public static void stopControl(ServerPlayer player) {
        StarPlatinumEntity stand = current(player);
        if (stand != null) { stand.stopControl(); StandMovement.update(player); }
    }

    public static void stopHeld(ServerPlayer player) {
        StarPlatinumEntity stand = current(player);
        if (stand != null) { stand.stopHeld(); StandMovement.update(player); }
    }

    public static void refreshHeld(ServerPlayer player) {
        StarPlatinumEntity stand = current(player);
        if (stand != null) stand.refreshHeld();
    }

    /** Applies a started action's walk-speed change at once instead of waiting for the end-of-tick reconcile. */
    private static boolean movementSynced(ServerPlayer player, boolean started) {
        if (started) StandMovement.update(player);
        return started;
    }

    public static float guardDamage(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float damage) {
        StarPlatinumEntity stand = current(player);
        return stand == null ? damage : stand.protectOwner(source, damage);
    }

    public static void grant(ServerPlayer player) {
        StandArrowService.cancel(player);
        dismiss(player);
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(player.isCreative()));
        ProgressionService.onGrant(player);
    }

    public static void clear(ServerPlayer player) {
        StandArrowService.cancel(player);
        dismiss(player);
        ProgressionService.onClear(player);
        StandProgress old = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress("", 0, 0, old.hadStand()));
    }

    private StandService() { }
}
