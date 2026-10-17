package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.core.StandDefinitions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** All mutation runs on the server thread. Manifestations never survive their player session. */
public final class StandService {
    private static final Map<UUID, StandManifestation> SUMMONS = new HashMap<>();
    private static final Map<String, java.util.function.Function<net.minecraft.server.level.ServerLevel, ? extends StandManifestation>> FACTORIES = new HashMap<>();
    private static final Map<UUID, Long> LAST_TOGGLE = new HashMap<>();

    public static void initialize() {
        registerFactory(StandProgress.STAR_PLATINUM, level -> new StarPlatinumEntity(RipplesFabric.STAR_PLATINUM, level));
        registerFactory(StandProgress.THE_WORLD, level -> new StarPlatinumEntity(RipplesFabric.THE_WORLD, level));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive()) { dismiss(player); continue; }
                // Reconcile every tick so idle, recovery, dimension changes and expired leases drop the penalty promptly.
                StandMovement.update(player);
                AbilityCooldowns.tick(player);
                StandParts.tick(player);
                StandEffects.tick(player);
                if (TimeStopService.isFrozen(player)) continue;
                StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
                if (!progress.hasStand()) continue;
                progress = progress.withMultipliers(SpeciesHooks.staminaMaximum(player), SpeciesHooks.staminaRegeneration(player));
                StandManifestation stand = currentManifestation(player);
                StandProgress next = progress.regenerate(stand != null, (stand == null || stand.allowsStaminaRegen()) && !TimeStopService.isCharging(player) && !TimeStopService.isActive(player),
                        player.getFoodData().getFoodLevel(), StandConfig.staminaFree(player));
                if (!next.equals(player.getAttachedOrCreate(StandAttachments.PROGRESS))) player.setAttached(StandAttachments.PROGRESS, next);
            }
            SUMMONS.values().removeIf(stand -> stand.entity().isRemoved());
        });
        // Fabric may fire disconnect from Netty's channelInactive; never mutate world state there.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            dismiss(handler.player);
            LAST_TOGGLE.remove(handler.player.getUUID());
            if (server.isPublished()) StandEffects.cleanup(handler.player, StandEffects.Removal.LOGOUT);
            StandMovement.releaseAllActions(handler.player);
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SUMMONS.clear(); LAST_TOGGLE.clear(); StandEffects.clearRuntime(); StandMovement.clearRuntime(); });
    }

    public static StandDefinitions.Definition definition(String id) { return StandDefinitions.definition(id); }

    public static void registerFactory(String id, java.util.function.Function<net.minecraft.server.level.ServerLevel, ? extends StandManifestation> factory) {
        if (definition(id).kind() != StandDefinitions.Kind.ENTITY) throw new IllegalArgumentException("Non-manifesting Stand: " + id);
        if (FACTORIES.putIfAbsent(id, java.util.Objects.requireNonNull(factory)) != null)
            throw new IllegalStateException("Duplicate Stand factory: " + id);
    }

    /** Compatibility view for old consumers while their concrete action paths migrate. */
    public static StarPlatinumEntity current(ServerPlayer player) {
        return currentManifestation(player) instanceof StarPlatinumEntity stand ? stand : null;
    }

    public static StandManifestation currentManifestation(ServerPlayer player) {
        StandManifestation stand = SUMMONS.get(player.getUUID());
        if (stand != null && (stand.entity().isRemoved() || stand.entity().level() != player.level() || stand.owner() != player)) {
            if (!stand.entity().level().getServer().isSameThread()) throw new IllegalStateException("Cross-server Stand mutation from " + Thread.currentThread().getName());
            stand.entity().discard();
            SUMMONS.remove(player.getUUID());
            return null;
        }
        return stand;
    }

    public static boolean toggle(ServerPlayer player) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        if (!player.isAlive() || player.isSpectator() || !player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()) return false;
        StandManifestation existing = currentManifestation(player);
        if (existing != null && existing.actionPhase() == StarPlatinumEntity.PHASE_GUARD_BROKEN) return false;
        long tick = player.level().getGameTime();
        Long previous = LAST_TOGGLE.get(player.getUUID());
        if (previous != null && tick - previous < 5) return false;
        LAST_TOGGLE.put(player.getUUID(), tick);
        if (existing != null) { dismiss(player); return true; }
        var definition = definition(player.getAttachedOrCreate(StandAttachments.PROGRESS).standId());
        var factory = FACTORIES.get(definition.id());
        if (!definition.playable() || definition.kind() != StandDefinitions.Kind.ENTITY || factory == null) return false;
        StandManifestation stand = factory.apply(player.level());
        if (!stand.definition().id().equals(definition.id())) throw new IllegalStateException("Stand factory returned wrong type for " + definition.id());
        stand.setOwner(player);
        if (!player.level().addFreshEntity(stand.entity())) return false;
        SUMMONS.put(player.getUUID(), stand);
        stand.playOriginal(stand.definition().path() + "_summon");
        return true;
    }

    /** StandEntityAction.onClick exposes the provisional entity to conditions without spawning it. */
    public static StandActionRegistry.Preparation prepareSummon(ServerPlayer player) {
        var current=currentManifestation(player);
        if(current!=null)return StandActionRegistry.Preparation.existing(current);
        var progress=player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if(!progress.hasStand())return StandActionRegistry.Preparation.existing(null);
        var definition=definition(progress.standId());var factory=FACTORIES.get(definition.id());
        if(!definition.playable()||factory==null)return StandActionRegistry.Preparation.existing(null);
        var stand=factory.apply(player.level());
        if(!stand.definition().id().equals(definition.id()))throw new IllegalStateException("Wrong provisional Stand type");
        stand.setOwner(player);SUMMONS.put(player.getUUID(),stand);
        return new StandActionRegistry.Preparation(){
            private boolean finished;
            public StandManifestation manifestation(){return stand;}
            public boolean finish(boolean success){
                if(finished)throw new IllegalStateException("Stand preparation completed twice");
                finished=true;
                if(success&&!stand.entity().isRemoved()&&player.level().addFreshEntity(stand.entity())){
                    stand.playOriginal(stand.definition().path()+"_summon");return true;
                }
                SUMMONS.remove(player.getUUID(),stand);stand.entity().discard();return false;
            }
        };
    }

    public static void dismiss(ServerPlayer player) {
        StandActionRegistry.cancel(player);
        TimeStopService.cancel(player);
        StandMovement.clear(player);
        StandManifestation stand = SUMMONS.remove(player.getUUID());
        if (stand != null && !stand.entity().isRemoved()) {
            stand.playOriginal(stand.definition().path() + "_unsummon");
            if (!stand.entity().level().getServer().isSameThread()) throw new IllegalStateException("Cross-server Stand mutation from " + Thread.currentThread().getName());
            stand.entity().discard();
        }
    }

    public static boolean attack(ServerPlayer player, boolean heavy) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StandManifestation stand = currentManifestation(player);
        return stand != null && movementSynced(player, stand.beginAttack(heavy));
    }

    public static boolean startHeld(ServerPlayer player, boolean guard) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StandManifestation stand = currentManifestation(player);
        return stand != null && movementSynced(player, stand.beginHeld(guard));
    }

    public static boolean special(ServerPlayer player, int phase) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StandManifestation stand = currentManifestation(player);
        return stand != null && movementSynced(player, stand.beginSpecial(phase));
    }

    public static boolean uppercut(ServerPlayer player) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StandManifestation stand = currentManifestation(player);
        return stand != null && movementSynced(player, stand.beginUppercut());
    }

    public static boolean startControl(ServerPlayer player) {
        if (TimeStopService.isFrozen(player) || TimeStopService.isCharging(player)) return false;
        StandManifestation stand = currentManifestation(player);
        return stand != null && movementSynced(player, stand.startControl());
    }

    public static void stopControl(ServerPlayer player) {
        StandManifestation stand = currentManifestation(player);
        if (stand != null) { stand.stopControl(); StandMovement.update(player); }
    }

    public static void stopHeld(ServerPlayer player) {
        StandManifestation stand = currentManifestation(player);
        if (stand != null) { stand.stopHeld(); StandMovement.update(player); }
    }

    public static void refreshHeld(ServerPlayer player) {
        StandManifestation stand = currentManifestation(player);
        if (stand != null) stand.refreshHeld();
    }

    /** Applies a started action's walk-speed change at once instead of waiting for the end-of-tick reconcile. */
    private static boolean movementSynced(ServerPlayer player, boolean started) {
        if (started) StandMovement.update(player);
        return started;
    }

    public static float guardDamage(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float damage) {
        StandManifestation stand = currentManifestation(player);
        return stand == null ? damage : stand.protectOwner(source, damage);
    }

    public static void grant(ServerPlayer player) { grant(player, StandProgress.STAR_PLATINUM); }

    public static void grant(ServerPlayer player, String standId) {
        StandProgress next = StandProgress.grant(standId, player.isCreative());
        StandEffects.cleanup(player, StandEffects.Removal.STAND_CHANGED);
        StandParts.reset(player);
        StandArrowService.cancel(player);
        dismiss(player);
        ProgressionService.onClear(player);
        player.setAttached(StandAttachments.PROGRESS, next);
        ProgressionService.onGrant(player);
    }

    public static void clear(ServerPlayer player) {
        StandEffects.cleanup(player, StandEffects.Removal.STAND_CHANGED);
        StandParts.reset(player);
        StandArrowService.cancel(player);
        dismiss(player);
        ProgressionService.onClear(player);
        StandProgress old = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress("", 0, 0, old.hadStand()));
    }

    private StandService() { }
}
