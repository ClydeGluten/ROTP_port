package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.PillarmanBaseActions;
import com.github.standobyte.jojo.fabric.PillarmanService;
import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.TimeStopService;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

/** Original HideInEntity camera possession. Root installs lifecycle, camera and teleport guards. */
public final class PossessionService {
    public static final int HOLD_TICKS = 20;
    public static final int RESOLVE_TICKS = 20;
    private static final Identifier SPEED = RipplesFabric.id("pillar_possession_hold");
    private static final Map<ServerPlayer, Hold> HOLDS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final java.util.Set<ServerPlayer> CAMERA_TRANSITIONS = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final class Hold {
        final UUID host; final Identifier dimension; int ticks, heartbeat;
        Hold(ServerPlayer player, Entity host) { this.host = host.getUUID(); dimension = player.level().dimension().identifier(); }
    }
    private static final class Session { Entity resolved; int unresolvedTicks; }
    /** Original IPlayerPossess hosts may be nonliving; only the authoritative host can claim a player. */
    public interface ExternalHost {
        boolean isEntrapped(ServerPlayer player);
        default Identifier possessionContext() { return PossessionState.ANGELO; }
        default void grunt(ServerPlayer player) { }
    }
    public static boolean beginExternal(ServerPlayer player, Entity target) {
        if (!player.isAlive() || target == player || target.level() != player.level() || !target.isAlive()
                || !(target instanceof ExternalHost external) || !external.isEntrapped(player)
                || !external.possessionContext().equals(PossessionState.ANGELO)) return false;
        var old = state(player);
        if (old != null && !old.asAlive()) return old.host().equals(target.getUUID());
        if (old != null) stop(player);
        stopHeld(player);
        player.setAttached(PossessionState.TYPE, new PossessionState(target.getUUID(), player.level().dimension().identifier(), player.gameMode(), false, external.possessionContext()));
        var session = new Session(); session.resolved = target; SESSIONS.put(player, session);
        player.setGameMode(GameType.SPECTATOR); setHostCamera(player, target); sync(player); return true;
    }
    public static boolean requestExit(ServerPlayer player) {
        var state = state(player); if (state == null || !state.asAlive()) return false;
        stop(player); return true;
    }
    public static void requestGrunt(ServerPlayer player) {
        var state = state(player); var target = host(player);
        if (state != null && !state.asAlive() && target instanceof ExternalHost external && external.isEntrapped(player)) external.grunt(player);
    }
    public static void requestRespawn(ServerPlayer player) {
        var state = state(player); var target = host(player);
        if (state == null || state.asAlive() || !(target instanceof ExternalHost external) || !external.isEntrapped(player)) return;
        player.removeEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
        restore(player); killTrapped(player, "rock_respawn");
    }
    public static void stopPillar(ServerPlayer player) { var state = state(player); if (state == null || state.asAlive()) stop(player); }
    public static void onDeath(ServerPlayer player) { restore(player); }
    public static void onDisconnect(ServerPlayer player) {
        var state = state(player);
        if (state == null || state.asAlive()) stop(player);
        else { stopHeld(player); SESSIONS.remove(player); }
    }
    public static void initialize() { PossessionState.initialize(); PossessionPayload.initialize(); }
    public static PossessionState state(ServerPlayer player) { return player.getAttached(PossessionState.TYPE); }
    public static boolean isPossessing(ServerPlayer player) { return state(player) != null; }
    public static boolean isHolding(ServerPlayer player) { return HOLDS.containsKey(player); }
    public static int heldTicks(ServerPlayer player) { var hold = HOLDS.get(player); return hold == null ? -1 : hold.ticks; }
    public static Entity host(ServerPlayer player) {
        var state = state(player);
        return state != null && state.dimension().equals(player.level().dimension().identifier()) ? player.level().getEntity(state.host()) : null;
    }
    /** Source Action range: eye-to-host-box surface minus user half-width, 8 blocks; unseen targets halve range. */
    public static boolean validTarget(ServerPlayer player, Entity entity) {
        if (!(entity instanceof LivingEntity target) || entity == player || entity.level() != player.level() || !entity.isAlive() || entity.isRemoved()) return false;
        var bounds = target.getBoundingBox(); Vec3 eye = player.getEyePosition();
        double distance = 0;
        if (!bounds.contains(eye)) {
            double ratio = player.getBbHeight() == 0 ? 0 : player.getEyeHeight() / player.getBbHeight();
            Vec3 center = new Vec3((bounds.minX + bounds.maxX) * .5, bounds.minY + ratio * (bounds.maxY - bounds.minY), (bounds.minZ + bounds.maxZ) * .5);
            distance = bounds.clip(eye, center).map(point -> eye.distanceTo(point) - player.getBbWidth() * .5).orElse(-1D);
        }
        return distance * distance <= (player.hasLineOfSight(target) ? 64 : 16);
    }
    public static boolean startHeld(ServerPlayer player, Entity target) {
        if (isPossessing(player) || isHolding(player) || !PillarmanBaseActions.canStart(player, 2, false, false) || !validTarget(player, target)) return false;
        PillarmanBaseActions.stopHeld(player);
        HOLDS.put(player, new Hold(player, target));
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) { speed.removeModifier(SPEED); speed.addTransientModifier(new AttributeModifier(SPEED, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
        sync(player); return true;
    }
    public static void keepHeld(ServerPlayer player) { var hold = HOLDS.get(player); if (hold != null) hold.heartbeat = 0; }
    /** Release before completion cancels; the hold-to-fire action fires automatically on tick20. */
    public static void stopHeld(ServerPlayer player) {
        boolean changed = HOLDS.remove(player) != null;
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null) speed.removeModifier(SPEED);
        if (changed) sync(player);
    }
    public static void tick(ServerPlayer player) {
        var state = state(player);
        if (state != null) {
            if (!player.isAlive() || player.isRemoved() || state.asAlive() && !PillarmanService.hasPillarman(player)
                    || !state.dimension().equals(player.level().dimension().identifier()) || state.host().equals(player.getUUID())) { stop(player); return; }
            var session = SESSIONS.computeIfAbsent(player, ignored -> new Session());
            Entity target = host(player);
            boolean valid = state.asAlive() ? target instanceof LivingEntity
                    : target instanceof ExternalHost external && external.possessionContext().equals(state.context()) && external.isEntrapped(player);
            if (!valid || !target.isAlive() || target.isRemoved()) {
                // A saved UUID may load after its player. A previously resolved host is never given this grace period.
                if (session.resolved != null || target != null || ++session.unresolvedTicks >= RESOLVE_TICKS) stop(player);
                return;
            }
            boolean resolvedNow = session.resolved != target;
            session.resolved = target; session.unresolvedTicks = 0;
            if (!player.isSpectator()) player.setGameMode(GameType.SPECTATOR);
            if (player.getCamera() != target) setHostCamera(player, target);
            if (resolvedNow) sync(player);
            return;
        }
        var hold = HOLDS.get(player); if (hold == null) return;
        if (TimeStopService.isFrozen(player)) return;
        Entity target = player.level().getEntity(hold.host);
        if (!hold.dimension.equals(player.level().dimension().identifier()) || ++hold.heartbeat > 15
                || !PillarmanBaseActions.canContinue(player, 2, false, false) || !validTarget(player, target)) { stopHeld(player); return; }
        if (++hold.ticks < HOLD_TICKS) { sync(player); return; }
        // Clear the hold before changing game mode, but persist recovery state before any camera transition.
        stopHeld(player);
        player.setAttached(PossessionState.TYPE, new PossessionState(target.getUUID(), player.level().dimension().identifier(), player.gameMode()));
        var session = new Session(); session.resolved = target; SESSIONS.put(player, session);
        PillarmanBaseActions.clear(player);
        player.setGameMode(GameType.SPECTATOR); setHostCamera(player, target); sync(player);
    }
    /** Idempotent exit for dismount, death, disconnect (before player save), power clear and respawn. */
    public static void stop(ServerPlayer player) {
        var state = state(player); restore(player);
        if (state != null && !state.asAlive()) killTrapped(player, "rock_broken");
    }
    private static void killTrapped(ServerPlayer player, String type) {
        if (!player.isAlive()) return;
        player.damageCooldownTime = 0;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, RipplesFabric.id(type));
        player.hurtServer(player.level(), new net.minecraft.world.damagesource.DamageSource(player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(key)), Float.MAX_VALUE);
    }
    private static void restore(ServerPlayer player) {
        stopHeld(player);
        var state = state(player); SESSIONS.remove(player);
        if (state == null) return;
        // Clear first so root camera/game-mode mixins allow the restoration and cannot recurse.
        player.removeAttached(PossessionState.TYPE);
        player.setGameMode(state.previousMode()); player.setCamera(player); sync(player);
    }
    /** Join after attachment load. Missing host resolves for at most20 server ticks, then restores prior mode. */
    public static void onJoin(ServerPlayer player) { SESSIONS.remove(player); tick(player); sync(player); }
    /** Call at HEAD of ServerPlayer.setCamera; true means cancel vanilla change. */
    public static boolean cancelCameraChange(ServerPlayer player, Entity requested) {
        if (!isPossessing(player)) return false;
        if (requested == player || requested == null) { if (!state(player).asAlive()) return true; stop(player); return false; }
        return requested != host(player);
    }
    /** Source rejects arbitrary teleports while possessed. Forced dimension/lifecycle exits call stop first. */
    public static boolean cancelTeleport(ServerPlayer player) { return isPossessing(player) && !CAMERA_TRANSITIONS.contains(player); }
    private static void setHostCamera(ServerPlayer player, Entity host) {
        // 26.3 setCamera invokes the dimension-aware teleport overload internally. Only this authoritative transition may pass.
        CAMERA_TRANSITIONS.add(player);
        try { player.setCamera(host); } finally { CAMERA_TRANSITIONS.remove(player); }
    }
    /** Source defers administrator game-mode changes until exit, keeping spectator camera meanwhile. */
    public static boolean cancelGameModeChange(ServerPlayer player, GameType requested) {
        var state = state(player); if (state == null || !player.isSpectator() && requested == GameType.SPECTATOR) return false;
        player.setAttached(PossessionState.TYPE, state.previousMode(requested)); sync(player); return true;
    }
    public static GameType actualGameMode(ServerPlayer player) { var state = state(player); return state == null ? player.gameMode() : state.previousMode(); }
    public static PossessionPayload snapshot(ServerPlayer player) {
        var state = state(player); var hold = HOLDS.get(player); Entity target = state == null ? (hold == null ? null : player.level().getEntity(hold.host)) : host(player);
        return new PossessionPayload(player.getId(), player.getUUID(), target == null ? -1 : target.getId(),
                state != null ? state.host() : hold != null ? hold.host : PossessionPayload.NO_TARGET, player.level().dimension().identifier(),
                hold == null ? -1 : hold.ticks, state != null, state == null ? -1 : state.previousMode().getId(), state == null || state.asAlive(), state == null ? PossessionState.CONTEXT : state.context());
    }
    public static void syncTo(ServerPlayer owner, ServerPlayer recipient) {
        if (ServerPlayNetworking.canSend(recipient, PossessionPayload.TYPE)) ServerPlayNetworking.send(recipient, snapshot(owner));
    }
    public static void sync(ServerPlayer player) {
        syncTo(player, player); for (var recipient : PlayerLookup.tracking(player)) if (recipient != player) syncTo(player, recipient);
    }
    /** Stop players through lifecycle hooks first; this only releases transient session references on server shutdown. */
    public static void clearAllRuntime() { HOLDS.clear(); SESSIONS.clear(); CAMERA_TRANSITIONS.clear(); }
    private PossessionService() { }
}
