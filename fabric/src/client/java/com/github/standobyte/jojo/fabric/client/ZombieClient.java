package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.core.ZombieProgress;
import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;

import java.util.List;

/** Original zombie action rows; root shares J, H and mapped mouse input with the other power modes. */
public final class ZombieClient {
    public static final String POWER_ID = "jojo:zombie";
    private static Object session;
    private static boolean mode, leftConsumed, rightConsumed, middleConsumed, leapPressed;
    private static ActionCatalog.Move queuedLeft, queuedRight, heldLeft, heldRight;
    private static ZombieActionPayload.Action sentHeld;
    private static int heartbeat;
    private static Object soundSession;
    private static final java.util.Set<Integer> DEVOUR_SOUNDS = new java.util.HashSet<>();
    public static void initialize() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (soundSession != client.level) { DEVOUR_SOUNDS.clear(); soundSession = client.level; }
            if (client.level == null || client.isPaused()) return;
            var current = new java.util.HashSet<Integer>();
            for (var entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof net.minecraft.world.entity.player.Player player)
                        || !player.getAttachedOrCreate(ZombieAttachments.VISUAL).devouring()) continue;
                current.add(player.getId());
                if (DEVOUR_SOUNDS.add(player.getId())) client.getSoundManager().play(new DevourSound(player));
            }
            DEVOUR_SOUNDS.retainAll(current);
        });
    }
    private static final class DevourSound extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final net.minecraft.world.entity.player.Player player;
        DevourSound(net.minecraft.world.entity.player.Player player) {
            super(JojoSounds.get("zombie_devour"), player.getSoundSource(), player.getRandom());
            this.player = player; volume = pitch = 1; looping = true;
            x = player.getX(); y = player.getY(); z = player.getZ();
        }
        @Override public boolean canPlaySound() { return !player.isSilent(); }
        @Override public void tick() {
            if (Minecraft.getInstance().level != player.level() || !player.isAlive() || player.isRemoved()
                    || !player.getAttachedOrCreate(ZombieAttachments.VISUAL).devouring()) { stop(); return; }
            x = player.getX(); y = player.getY(); z = player.getZ();
        }
    }
    public static ZombieProgress state() {
        var player = Minecraft.getInstance().player;
        return player == null ? ZombieProgress.EMPTY : player.getAttachedOrCreate(ZombieAttachments.PROGRESS);
    }
    public static ZombieAttachments.Hud hud() {
        var player = Minecraft.getInstance().player;
        return player == null ? ZombieAttachments.Hud.EMPTY : player.getAttachedOrCreate(ZombieAttachments.HUD);
    }
    public static List<String> attacks() { return List.of("zombie_claw_lacerate", "zombie_devour"); }
    public static List<String> abilities() { return List.of("zombie_disguise"); }
    public static String defaultMiddleAction() { return "zombie_devour"; }
    public static boolean playable() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isAlive() && !player.isSpectator() && state().zombie();
    }
    public static boolean isMode() { return mode && playable(); }
    public static void toggle() {
        session = Minecraft.getInstance().level;
        boolean desired = !mode;
        StandSelection.closeStandMode(); VampireClient.close(); HamonClient.close(); close();
        mode = desired && playable();
    }
    public static void close() { mode = false; clearHeld(); }
    public static void suspendInput() { clearHeld(); }
    public static boolean unlocked(String id) { return playable() && (attacks().contains(id) || abilities().contains(id)); }
    public static ActionCatalog.Move move(String id) {
        return switch (id) {
            case "zombie_claw_lacerate" -> new ActionCatalog.Move(id, id, "CLAW", 0, 60, 0);
            case "zombie_devour" -> new ActionCatalog.Move(id, id, "START_DEVOUR", 0, 0, 1);
            case "zombie_disguise" -> new ActionCatalog.Move(id, state().disguised() ? "zombie_disguise_on" : id, "START_DISGUISE", 0, 0, 2);
            default -> throw new IllegalArgumentException("Unknown zombie action: " + id);
        };
    }
    public static boolean available(ActionCatalog.Move move) {
        if (!unlocked(move.id())) return false;
        var client = Minecraft.getInstance(); var player = client.player; var hud = hud();
        if (TimeStopClient.isFrozen(player) || VampireCombatService.isStunned(player) || hud.clawTicks() > 0) return false;
        if (hud.held() == ZombieAttachments.Held.DISGUISE && !move.id().equals("zombie_disguise")) return false;
        if (move.id().equals("zombie_disguise")) return true;
        if (state().disguised() || !ZombieService.freeMainHand(player)) return false;
        if (move.id().equals("zombie_devour")) return client.level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL;
        return player.isCreative() || state().blood() >= 60;
    }
    public static int chargeTicks(String id) { return id.equals("zombie_disguise") && hud().held() == ZombieAttachments.Held.DISGUISE ? hud().heldTicks() : 0; }
    public static int chargeDuration(String id) { return id.equals("zombie_disguise") ? 60 : 0; }
    public static int cooldown(String id) { return 0; }
    public static int cooldownTotal(String id) { return 0; }
    public static boolean greenSelection(String id) { return id.equals("zombie_disguise") && state().disguised(); }
    public static boolean interceptClick(boolean attack) {
        if (!(attack ? leftConsumed : rightConsumed)) {
            String id = StandSelection.selectedAction(attack);
            if (!unlocked(id) || !ClientPlayNetworking.canSend(ZombieActionPayload.TYPE)) return false;
            var action = move(id);
            if (!available(action)) {
                if (!Minecraft.getInstance().player.isCreative() && state().blood() < action.cost()) ZombieHud.rejected();
                return false;
            }
            if (attack) { queuedLeft = action; leftConsumed = true; }
            else { queuedRight = action; rightConsumed = true; }
        }
        return true;
    }
    public static boolean capturesAttackHeld() { return leftConsumed || queuedLeft != null || heldLeft != null; }
    /** Original default middle action is held Devour, using the player's mapped pick-item key. */
    public static boolean interceptMiddle() {
        if (!isMode() || !StandSelection.capturesMouse() || !available(move(defaultMiddleAction()))
                || !ClientPlayNetworking.canSend(ZombieActionPayload.TYPE)) return false;
        middleConsumed = true; return true;
    }
    public static void releaseMappedKey(InputConstants.Key key) {
        var client = Minecraft.getInstance();
        if (client.options.keyAttack.matches(key)) {
            leftConsumed = false; heldLeft = null;
            if (queuedLeft != null && queuedLeft.held() != 0) queuedLeft = null;
        }
        if (client.options.keyUse.matches(key)) {
            rightConsumed = false; heldRight = null;
            if (queuedRight != null && queuedRight.held() != 0) queuedRight = null;
        }
        if (client.options.keyPickItem.matches(key)) middleConsumed = false;
        if (sentHeld != null && heldLeft == null && heldRight == null && !middleConsumed) stopSentHold();
    }
    public static void tick(Minecraft client, boolean active) {
        if (session != client.level) { close(); session = client.level; leapPressed = false; }
        if (!playable()) close();
        if (!active || !isMode() || client.gui.screen() != null || StandSelection.hotbarsDisabled()) { clearHeld(); return; }
        if (queuedLeft != null) { use(queuedLeft); heldLeft = queuedLeft.held() != 0 && leftConsumed ? queuedLeft : null; }
        if (queuedRight != null) { use(queuedRight); heldRight = queuedRight.held() != 0 && rightConsumed ? queuedRight : null; }
        if (!client.options.keyAttack.isDown()) { heldLeft = null; leftConsumed = false; }
        if (!client.options.keyUse.isDown()) { heldRight = null; rightConsumed = false; }
        if (!client.options.keyPickItem.isDown()) middleConsumed = false;
        var action = heldRight != null ? heldRight : heldLeft;
        var desired = middleConsumed ? ZombieActionPayload.Action.START_DEVOUR : action == null ? null : ZombieActionPayload.Action.valueOf(action.packet());
        if (desired != sentHeld) {
            stopSentHold(); sentHeld = desired;
            if (desired != null) { StandCamera.aimForAction(); send(desired); }
        } else if (desired != null && ++heartbeat >= 5) { send(ZombieActionPayload.Action.KEEP_HELD); heartbeat = 0; }
        queuedLeft = queuedRight = null;
    }
    public static Input leapInput(Input input) {
        var client = Minecraft.getInstance();
        boolean pressed = input.jump() && input.shift();
        boolean activate = pressed && !leapPressed && isMode() && client.gui.screen() == null
                && state().leapStrength(client.level.getDifficulty().getId()) > 0 && hud().leapCooldown() == 0
                && hud().clawTicks() == 0 && hud().held() != ZombieAttachments.Held.DISGUISE
                && (client.player.onGround() || client.player.getVehicle() != null && client.player.getVehicle().onGround())
                && !VampireCombatService.isStunned(client.player) && !TimeStopClient.isFrozen(client.player);
        leapPressed = pressed;
        if (!activate) return input;
        StandCamera.aimForAction(); send(ZombieActionPayload.Action.LEAP);
        return new Input(input.forward(), input.backward(), input.left(), input.right(), false, false, input.sprint());
    }
    private static void use(ActionCatalog.Move move) {
        if (move.held() == 0) { StandCamera.aimForAction(); send(ZombieActionPayload.Action.valueOf(move.packet())); }
    }
    private static void stopSentHold() {
        if (sentHeld != null) send(ZombieActionPayload.Action.STOP_HELD);
        sentHeld = null; heartbeat = 0;
    }
    private static void clearHeld() {
        stopSentHold(); leftConsumed = rightConsumed = middleConsumed = false;
        queuedLeft = queuedRight = heldLeft = heldRight = null;
    }
    private static void send(ZombieActionPayload.Action action) {
        if (ClientPlayNetworking.canSend(ZombieActionPayload.TYPE)) ClientPlayNetworking.send(new ZombieActionPayload(action));
    }
    private ZombieClient() { }
}
