package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.pillarman.PossessionPayload;
import com.github.standobyte.jojo.fabric.pillarman.PossessionService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Camera and original possession-animation state; no client-authorized mode change or host movement. */
public final class PossessionClient {
    private static final Map<UUID, PossessionPayload> STATES = new HashMap<>();
    private static ClientLevel world;
    private static Entity controlledCamera;
    private static int unresolvedTicks;
    private static boolean exitRequested, initialized;
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(PossessionPayload.TYPE, (payload, context) -> context.client().execute(() -> accept(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(PossessionClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(client));
    }
    public static void accept(Minecraft client, PossessionPayload payload) {
        if (client.level == null || !payload.dimension().equals(client.level.dimension().identifier())) return;
        if (world != client.level) reset(client);
        world = client.level;
        Entity owner = client.level.getEntity(payload.playerId());
        if (owner != null && !owner.getUUID().equals(payload.playerUuid())) return;
        if (!payload.active() && payload.heldTicks() < 0) STATES.remove(payload.playerUuid());
        else STATES.put(payload.playerUuid(), payload);
        if (client.player != null && client.player.getUUID().equals(payload.playerUuid())) {
            unresolvedTicks = 0; exitRequested = false;
            if (!payload.active()) restoreCamera(client);
        }
    }
    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null || world != client.level) { reset(client); world = client.level; return; }
        var state = STATES.get(client.player.getUUID());
        if (state == null || !state.active()) { restoreCamera(client); return; }
        var host = client.level.getEntity(state.targetId());
        // UUID check prevents camera hijack when an old network ID is recycled after unload.
        if (host != null && host.isAlive() && !host.isRemoved() && host.getUUID().equals(state.targetUuid())) {
            unresolvedTicks = 0; controlledCamera = host;
            if (client.getCameraEntity() != host) client.setCameraEntity(host);
        } else if (!client.isPaused() && ++unresolvedTicks >= PossessionService.RESOLVE_TICKS) {
            restoreCamera(client);
            if (!exitRequested && state.asAlive()) { exitRequested = true; send(PossessionPayload.Action.EXIT, -1); }
        }
    }
    public static boolean isPossessing() {
        var player = Minecraft.getInstance().player;
        var state = player == null ? null : STATES.get(player.getUUID()); return state != null && state.active();
    }
    /** Root uses for original third/first-person possession charge animation, including tracked other players. */
    public static int heldTicks(Entity player) { var state = STATES.get(player.getUUID()); return state == null ? -1 : state.heldTicks(); }
    public static boolean suppressSpectatorMenu() { return isPossessing(); }
    /** Original heldWalkSpeed(0); root input applies this alongside its existing action movement scale. */
    public static float movementMultiplier() {
        var player = Minecraft.getInstance().player; return player != null && heldTicks(player) >= 0 ? 0 : 1;
    }
    public static void start(Entity target) { if (target != null) send(PossessionPayload.Action.START, target.getId()); }
    public static void keepHeld() { send(PossessionPayload.Action.KEEP_HELD, -1); }
    public static void release() { send(PossessionPayload.Action.RELEASE, -1); }
    public static boolean trueSpectator(net.minecraft.world.entity.player.Player player) {
        var state=STATES.get(player.getUUID());return state!=null&&state.active()?state.previousMode()==net.minecraft.world.level.GameType.SPECTATOR.getId():player.isSpectator();
    }
    public static boolean canExitAlive() { var player=Minecraft.getInstance().player;var state=player==null?null:STATES.get(player.getUUID());return state!=null&&state.active()&&state.asAlive(); }
    public static net.minecraft.world.level.GameType actualGameMode() {
        var client=Minecraft.getInstance();var state=client.player==null?null:STATES.get(client.player.getUUID());
        return state!=null&&state.active()?net.minecraft.world.level.GameType.byId(state.previousMode()):client.gameMode==null?net.minecraft.world.level.GameType.SURVIVAL:client.gameMode.getPlayerMode();
    }
    public static void exit() { if(canExitAlive())send(PossessionPayload.Action.EXIT, -1); }
    public static void requestRespawn() { send(PossessionPayload.Action.RESPAWN,-1); }
    public static void requestGrunt() { send(PossessionPayload.Action.GRUNT,-1); }
    private static void send(PossessionPayload.Action action, int targetId) {
        if (ClientPlayNetworking.canSend(PossessionPayload.Intent.TYPE)) ClientPlayNetworking.send(new PossessionPayload.Intent(action, targetId));
    }
    private static void restoreCamera(Minecraft client) {
        if (controlledCamera != null && client.player != null && client.getCameraEntity() == controlledCamera) client.setCameraEntity(client.player);
        controlledCamera = null;
    }
    public static void reset(Minecraft client) {
        restoreCamera(client); STATES.clear(); world = null; unresolvedTicks = 0; exitRequested = false;
    }
    private PossessionClient() { }
}
