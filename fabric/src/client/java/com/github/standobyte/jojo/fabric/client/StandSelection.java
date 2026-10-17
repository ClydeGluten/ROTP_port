package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanClient;
import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanHud;
import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanActionCatalog;

import com.github.standobyte.jojo.fabric.StandActionPayload;
import com.github.standobyte.jojo.fabric.StandAttachments;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/** Ports InputHandler's Stand mode, held row focus and temporary vanilla-input bypass. */
public final class StandSelection {
    private static KeyMapping modeKey, attackFocus, abilityFocus, disable, editor;
    private static boolean standMode, leftConsumed, rightConsumed;
    private static Object session;
    private static int held;
    private static ActionCatalog.Move registeredHeld;
    private static boolean registeredAttack;
    private static int registeredHeartbeat;
    private static int chargeButton;
    private static boolean releasedQueuedLeftCharge, releasedQueuedRightCharge;
    private static ActionCatalog.Move queuedLeft, queuedRight;
    private static ActionCatalog.Move heldLeft, heldRight;

    public static void initialize(KeyMapping.Category category) {
        modeKey = key("jojo.key.stand_mode", InputConstants.KEY_K, category);
        attackFocus = key("jojo.key.attack_hotbar", InputConstants.KEY_V, category);
        abilityFocus = key("jojo.key.ability_hotbar", InputConstants.KEY_B, category);
        disable = key("jojo.key.disable_hotbars", InputConstants.KEY_LALT, category);
        editor = key("key.jojo.fabric.layout", InputConstants.KEY_BACKSLASH, category);
    }
    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, code, category));
    }
    private static boolean playable() {
        var c = Minecraft.getInstance();
        return c.player != null && c.player.isAlive() && !c.player.isSpectator()
                && c.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand();
    }
    private static boolean acceptsInput() {
        var c = Minecraft.getInstance();
        return (playable() || nonStandPlayable()) && c.gui.screen() == null && !c.isPaused() && c.isWindowActive();
    }
    public static boolean nonStandPlayable() { return VampireClient.playable() || HamonClient.playable() || ZombieClient.playable() || PillarmanClient.playable(); }
    public static boolean isNonStandMode() { return VampireClient.isMode() || HamonClient.isMode() || ZombieClient.isMode() || PillarmanClient.isMode(); }
    public static void closeNonStandModes() { VampireClient.close(); HamonClient.close(); ZombieClient.close(); PillarmanClient.close(); }
    public static boolean isStandMode() { return standMode && playable(); }
    public static boolean isPowerMode() { return isStandMode() || isNonStandMode(); }
    public static boolean hotbarsDisabled() { return disable != null && disable.isDown(); }
    public static boolean attackFocused() { return isPowerMode() && acceptsInput() && attackFocus != null && attackFocus.isDown(); }
    public static boolean abilityFocused() { return isPowerMode() && acceptsInput() && abilityFocus != null && abilityFocus.isDown(); }
    public static boolean capturesMouse() { return isPowerMode() && !hotbarsDisabled() && acceptsInput(); }
    public static boolean capturesScroll() { return attackFocused() || abilityFocused(); }
    public static boolean editorMatches(KeyEvent event) { return editor != null && editor.matches(event); }

    /** Original onStandSummon opens the mode; dismissal deliberately leaves it open. */
    public static void onStandSummon() { if (playable()) { closeNonStandModes(); standMode = true; } }
    public static void closeStandMode() { standMode = false; clearHeld(); }

    /** Empty selection is intentional: that mouse button retains vanilla behavior. */
    public static String selectedAction(boolean attack) {
        var layout = ActionLayouts.current();
        String id = attack ? layout.selectedAttack() : layout.selectedAbility();
        if (isNonStandMode() && !id.isEmpty() && !ActionCatalog.move(id).unlocked())
            return visibleActions(attack).stream().findFirst().orElse("");
        return layout.enabledActions(attack).contains(id) && ActionCatalog.move(id).unlocked() ? id : "";
    }
    /** Original default showLockedSlots=false removes full-power attacks from a weak vampire's row. */
    public static java.util.List<String> visibleActions(boolean attack) {
        var actions = ActionLayouts.current().enabledActions(attack);
        return isNonStandMode() ? actions.stream().filter(id -> ActionCatalog.move(id).unlocked()).toList() : actions;
    }
    private static com.github.standobyte.jojo.core.ActionLayout selectVisibleSlot(
            com.github.standobyte.jojo.core.ActionLayout layout, boolean attack, int index) {
        var visible = visibleActions(attack);
        int actual = index < visible.size() ? layout.enabledActions(attack).indexOf(visible.get(index)) : -1;
        return layout.selectSlot(attack, actual, id -> ActionCatalog.move(id).unlocked());
    }

    public static void cycle(double direction) {
        if (direction == 0 || !capturesScroll()) return;
        var layout = ActionLayouts.current();
        if (isNonStandMode()) {
            if (!selectedAction(true).isEmpty()) layout = layout.select(true, selectedAction(true));
            if (!selectedAction(false).isEmpty()) layout = layout.select(false, selectedAction(false));
        }
        if (attackFocused()) layout = layout.cycleOriginal(true, direction > 0 ? -1 : 1, id -> ActionCatalog.move(id).unlocked());
        if (abilityFocused()) layout = layout.cycleOriginal(false, direction > 0 ? -1 : 1, id -> ActionCatalog.move(id).unlocked());
        ActionLayouts.set(ActionLayouts.currentId(), layout);
    }

    /** Runs before vanilla consumes number keys, so the inventory slot never changes underneath the HUD. */
    public static void consumeHotbarKeys() {
        if (!capturesScroll()) return;
        var c = Minecraft.getInstance();
        var layout = ActionLayouts.current();
        boolean changed = false;
        for (int i = 0; i < c.options.keyHotbarSlots.length; i++) {
            while (c.options.keyHotbarSlots[i].consumeClick()) {
                if (attackFocused()) layout = selectVisibleSlot(layout, true, i);
                if (abilityFocused()) layout = selectVisibleSlot(layout, false, i);
                changed = true;
            }
        }
        if (changed) ActionLayouts.set(ActionLayouts.currentId(), layout);
    }

    /** Like the original click result, cancel vanilla only when an available action consumes this click. */
    public static boolean interceptClick(boolean attack) {
        if (!capturesMouse()) return false;
        if (PillarmanClient.isMode()) return PillarmanClient.interceptClick(attack);
        if (ZombieClient.isMode()) return ZombieClient.interceptClick(attack);
        if (HamonClient.isMode()) return HamonClient.interceptClick(attack);
        if (VampireClient.isMode()) return VampireClient.interceptClick(attack);
        // Vanilla repeats item-use while held. A consumed click must not resume time immediately after charging.
        if (!attack && rightConsumed) return true;
        String id = selectedAction(attack);
        if (id.isEmpty()) return false;
        var c = Minecraft.getInstance();
        var action = ActionCatalog.displayMove(id);
        if (action.registered()) {
            boolean alongside=registeredHeld!=null&&ActionCatalog.canRunAlongsideHeld(action,registeredHeld);
            if (registeredHeld != null && !alongside && !ActionCatalog.canReplaceHeld(action,registeredHeld)) return registeredAttack == attack;
            if (!action.unlocked() || !action.available() || !ClientPlayNetworking.canSend(StandActionPayload.Registered.TYPE)) return false;
            StandCamera.aimForAction();
            sendRegistered(action, com.github.standobyte.jojo.fabric.StandActionRegistry.Phase.PRESS);
            if(!alongside){ registeredHeld = null; registeredHeartbeat = 0; }
            if (action.held() != 0) { registeredHeld = action; registeredAttack = attack; registeredHeartbeat = 0; }
            if (attack) leftConsumed = true; else rightConsumed = true;
            return true;
        }
        if (!action.unlocked() || !ClientPlayNetworking.canSend(StandActionPayload.TYPE)) return false;
        var continuing = attack ? heldLeft : heldRight;
        if (continuing != null && continuing.held() != 0) return true;
        var state = c.player.getAttachedOrCreate(StandAttachments.HUD);
        var progress = c.player.getAttachedOrCreate(StandAttachments.PROGRESS);
        // Most actions require a manifestation. Original TimeStopInstant can act on the unsummoned user.
        if (action.requiresStaminaUpfront() && !com.github.standobyte.jojo.fabric.StandConfig.staminaFree(c.player) && progress.stamina() < action.cost()) {
            ActionHud.staminaRejected(); return false;
        }
        if ((action.requiresManifestation() && StandView.ownManifestation(c) == null) || action.cooldown(state) > 0) return false;
        if (state.charge() > 0 || (state.recovery() > 0 && !action.canReplaceRecovery())) return false;
        if (attack) {
            queuedLeft = action; leftConsumed = true; releasedQueuedLeftCharge = false;
        } else {
            queuedRight = action; rightConsumed = true; releasedQueuedRightCharge = false;
        }
        return true;
    }

    /** Observe real release edges, including a release/repress between two client ticks and rebound buttons. */
    public static void releaseMappedKey(InputConstants.Key key) {
        VampireClient.releaseMappedKey(key);
        HamonClient.releaseMappedKey(key);
        ZombieClient.releaseMappedKey(key);
        PillarmanClient.releaseMappedKey(key);
        var client = Minecraft.getInstance();
        if (client == null || client.options == null) return;
        if (registeredHeld != null && (registeredAttack ? client.options.keyAttack : client.options.keyUse).matches(key)) releaseRegistered();
        if (client.options.keyAttack.matches(key)) {
            leftConsumed = false; heldLeft = null;
            releasedQueuedLeftCharge = queuedLeft != null && queuedLeft.id().equals("time_stop");
            if (chargeButton == 1) cancelCharge();
        }
        if (client.options.keyUse.matches(key)) {
            rightConsumed = false; heldRight = null;
            releasedQueuedRightCharge = queuedRight != null && queuedRight.id().equals("time_stop");
            if (chargeButton == 2) cancelCharge();
        }
    }
    public static void mouseClick(boolean attack) { interceptClick(attack); }
    public static boolean capturesAttackHeld() {
        if (PillarmanClient.isMode()) return capturesMouse() && PillarmanClient.capturesAttackHeld();
        if (ZombieClient.isMode()) return capturesMouse() && ZombieClient.capturesAttackHeld();
        if (HamonClient.isMode()) return capturesMouse() && HamonClient.capturesAttackHeld();
        if (VampireClient.isMode()) return capturesMouse() && VampireClient.capturesAttackHeld();
        return capturesMouse() && (leftConsumed || queuedLeft != null || heldLeft != null);
    }

    public static void tick(Minecraft client, boolean active) {
        if (session != client.level) {
            session = client.level;
            standMode = false;
            clearHeld();
        }
        if (!playable()) { standMode = false; clearHeld(); }
        while (modeKey.consumeClick()) if (active && playable()) { closeNonStandModes(); standMode = !standMode; }
        VampireClient.tick(client, active);
        HamonClient.tick(client, active);
        ZombieClient.tick(client, active);
        PillarmanClient.tick(client, active);
        while (editor.consumeClick()) if (active && (playable() || nonStandPlayable())) {
            client.gui.setScreen(new ActionLayoutScreen(null)); VampireClient.suspendInput(); HamonClient.suspendInput(); ZombieClient.suspendInput(); PillarmanClient.suspendInput(); active = false;
        }
        while (attackFocus.consumeClick()) { /* Original focus bindings use held state. */ }
        while (abilityFocus.consumeClick()) { /* Both held rows may receive the same selection. */ }
        while (disable.consumeClick()) { /* Alt temporarily yields both mouse buttons to vanilla. */ }
        if (!active || !capturesMouse() || isNonStandMode()) { clearHeld(); return; }
        if (registeredHeld != null) {
            if (!(registeredAttack ? client.options.keyAttack : client.options.keyUse).isDown()) releaseRegistered();
            else if (++registeredHeartbeat >= 5) { sendRegistered(registeredHeld, com.github.standobyte.jojo.fabric.StandActionRegistry.Phase.KEEP); registeredHeartbeat = 0; }
        }
        if (queuedLeft != null) {
            use(queuedLeft); heldLeft = queuedLeft.held() != 0 ? queuedLeft : null;
            if (queuedLeft.id().equals("time_stop")) chargeButton = 1;
            if (releasedQueuedLeftCharge) cancelCharge();
        }
        if (queuedRight != null) {
            use(queuedRight); heldRight = queuedRight.held() != 0 ? queuedRight : null;
            if (queuedRight.id().equals("time_stop")) chargeButton = 2;
            if (releasedQueuedRightCharge) cancelCharge();
        }
        if (chargeButton != 0) {
            if (client.player.getAttachedOrCreate(StandAttachments.HUD).activeStop() > 0) chargeButton = 0;
            else if (!(chargeButton == 1 ? client.options.keyAttack : client.options.keyUse).isDown()) cancelCharge();
        }
        if (!client.options.keyAttack.isDown() && queuedLeft == null) { heldLeft = null; leftConsumed = false; }
        if (!client.options.keyUse.isDown() && queuedRight == null) { heldRight = null; rightConsumed = false; }
        held = heldRight != null ? heldRight.held() : heldLeft != null ? heldLeft.held() : 0;
        queuedLeft = null; queuedRight = null;
        releasedQueuedLeftCharge = releasedQueuedRightCharge = false;
    }
    private static void use(ActionCatalog.Move action) {
        StandCamera.aimForAction();
        if (action.held() == 0 && ClientPlayNetworking.canSend(StandActionPayload.TYPE))
            ClientPlayNetworking.send(new StandActionPayload(action.action()));
    }
    private static void cancelCharge() {
        if (chargeButton != 0 && ClientPlayNetworking.canSend(StandActionPayload.TYPE))
            ClientPlayNetworking.send(new StandActionPayload(StandActionPayload.Action.CANCEL_TIME_CHARGE));
        chargeButton = 0;
    }
    public static void sendRegistered(ActionCatalog.Move action, com.github.standobyte.jojo.fabric.StandActionRegistry.Phase phase) {
        sendRegistered(action, phase, com.github.standobyte.jojo.fabric.StandActionRegistry.Extra.EMPTY);
    }
    public static void sendRegistered(ActionCatalog.Move action, com.github.standobyte.jojo.fabric.StandActionRegistry.Phase phase,
                                      com.github.standobyte.jojo.fabric.StandActionRegistry.Extra extra) {
        if (action.registered() && ClientPlayNetworking.canSend(StandActionPayload.Registered.TYPE))
            ClientPlayNetworking.send(new StandActionPayload.Registered(net.minecraft.resources.Identifier.parse(action.id()), phase, false, extra));
    }
    private static void releaseRegistered() {
        if (registeredHeld != null) sendRegistered(registeredHeld, com.github.standobyte.jojo.fabric.StandActionRegistry.Phase.RELEASE);
        registeredHeld = null; registeredHeartbeat = 0;
    }
    private static void clearHeld() {
        releaseRegistered();
        cancelCharge();
        leftConsumed = false; rightConsumed = false; held = 0; heldLeft = null; heldRight = null; queuedLeft = null; queuedRight = null;
        releasedQueuedLeftCharge = releasedQueuedRightCharge = false;
    }
    public static int held() { return held; }
    private StandSelection() { }
}
