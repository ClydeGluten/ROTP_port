package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.core.HamonProgress;
import com.github.standobyte.jojo.core.HamonSkills;
import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/** Original non-Stand input lifecycle. J is shared with VampireClient, with no competing binding. */
public final class HamonClient {
    public static final String POWER_ID = "jojo:hamon";
    private static boolean mode, leftConsumed, rightConsumed, quickConsumed;
    private static ActionCatalog.Move queuedLeft, queuedRight, heldLeft, heldRight;
    private static String sentHeld = "";
    private static int heartbeat;
    private static Object session;
    private static KeyMapping screenKey;
    public static void initialize(KeyMapping key) {
        screenKey = key;
        ClientPlayNetworking.registerGlobalReceiver(HamonConfig.Sync.TYPE, (packet, context) -> HamonConfig.receive(packet.data()));
        HamonPresentationClient.initialize();
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && hand == net.minecraft.world.InteractionHand.MAIN_HAND && playable()
                    && entity instanceof net.minecraft.world.entity.LivingEntity teacher && HamonService.hasHamon(teacher)) {
                Minecraft.getInstance().gui.setScreen(new HamonScreen());
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
    public static HamonProgress state() {
        var p = Minecraft.getInstance().player;
        return p == null ? HamonProgress.EMPTY : HamonService.state(p);
    }
    public static boolean playable() {
        var p = Minecraft.getInstance().player;
        return p != null && p.isAlive() && !p.isSpectator() && HamonService.hasHamon(p);
    }
    public static boolean isMode() { return mode && playable(); }
    public static boolean screenKeyMatches(KeyEvent event) { return screenKey != null && screenKey.matches(event); }
    public static void toggle() { boolean wanted = !isMode(); StandSelection.closeStandMode(); VampireClient.close(); ZombieClient.close(); close(); mode = wanted && playable(); }
    public static void close() { mode = false; suspendInput(); }
    public static void suspendInput() {
        if (!sentHeld.isEmpty()) send(sentHeld, HamonActionPayload.Operation.CANCEL);
        sentHeld = ""; heartbeat = 0;
        queuedLeft = queuedRight = heldLeft = heldRight = null;
        leftConsumed = rightConsumed = quickConsumed = false;
    }
    public static boolean unlocked(String id) { return playable() && HamonActionCatalog.unlocked(state(), id); }
    public static boolean available(ActionCatalog.Move move) {
        if (!unlocked(move.id())) return false;
        var client = Minecraft.getInstance(); var player = client.player;
        var action = HamonActionCatalog.get(move.id());
        if (action == null || VampireCombatService.isStunned(player) || TimeStopClient.isFrozen(player) || cooldown(move.id()) > 0) return false;
        if ((action.freeHands() & 1) != 0 && !player.getMainHandItem().isEmpty()) return false;
        if ((action.freeHands() & 2) != 0 && !player.getOffhandItem().isEmpty()) return false;
        return player.isCreative() || state().energy() > 0 || state().breathStability() > 0 || action.energyCost() == 0;
    }
    public static int cooldown(String id) {
        var p = Minecraft.getInstance().player;
        return p == null ? 0 : HamonActionService.state(p).cooldowns().getOrDefault(id, 0);
    }
    public static int cooldownTotal(String id) { var a = HamonActionCatalog.get(id); return a == null ? 0 : a.cooldown(); }
    public static int chargeTicks(String id) {
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        var s = HamonActionService.state(p);
        return id.equals(s.held()) ? s.heldTicks() : 0;
    }
    public static int chargeDuration(String id) { var a = HamonActionCatalog.get(id); return a == null ? 0 : a.chargeTicks(); }
    public static ActionCatalog.Move displayMove(String id) {
        var client = Minecraft.getInstance();
        String selected = id;
        if (client.options.keyShift.isDown()) {
            String shift = HamonActionCatalog.shift(id);
            if (shift != null && !shift.isEmpty() && unlocked(shift)) selected = shift;
        }
        var move = ActionCatalog.move(selected);
        var runtime = HamonActionService.state(client.player);
        if (selected.equals("hamon_protection") && runtime.protection()) return new ActionCatalog.Move(selected, "hamon_protection_on", selected, 0, move.cost(), move.held());
        if (selected.equals("joseph_rebuff_overdrive") && runtime.continuous().equals(selected)) return new ActionCatalog.Move(selected, "joseph_rebuff_overdrive_cancel", selected, 0, move.cost(), move.held());
        return move;
    }
    public static boolean interceptClick(boolean attack) {
        if (attack ? leftConsumed : rightConsumed) return true;
        String id = StandSelection.selectedAction(attack);
        if (id.isEmpty() || !ClientPlayNetworking.canSend(HamonActionPayload.TYPE)) return false;
        var move = ActionCatalog.displayMove(id);
        if (!available(move)) { HamonHud.rejected(); return false; }
        if (attack) { queuedLeft = move; leftConsumed = true; }
        else { queuedRight = move; rightConsumed = true; }
        return true;
    }
    public static boolean capturesAttackHeld() { return leftConsumed || heldLeft != null || queuedLeft != null; }
    public static void releaseMappedKey(InputConstants.Key key) {
        var c = Minecraft.getInstance();
        if (c.options.keyAttack.matches(key)) { leftConsumed = false; heldLeft = null; }
        if (c.options.keyUse.matches(key)) { rightConsumed = false; heldRight = null; }
        if (c.options.keyPickItem.matches(key)) quickConsumed = false;
        if (!sentHeld.isEmpty() && heldLeft == null && heldRight == null && !quickConsumed) release();
    }
    /** Called before vanilla consumes the middle-button binding. */
    public static boolean interceptQuickBreath() {
        if (!isMode() || VampireCombatService.isStunned(Minecraft.getInstance().player) || StandSelection.hotbarsDisabled() || !ClientPlayNetworking.canSend(HamonActionPayload.TYPE)) return false;
        quickConsumed = true;
        return true;
    }
    public static void tick(Minecraft client, boolean active) {
        if (session != client.level) { close(); session = client.level; }
        if (!playable()) close();
        HamonPresentationClient.tick(client);
        teacherHints(client);
        if (!active || !isMode() || VampireCombatService.isStunned(client.player) || TimeStopClient.isFrozen(client.player) || StandSelection.hotbarsDisabled()) { suspendInput(); return; }
        if (queuedLeft != null) { use(queuedLeft); if (!leftConsumed && queuedLeft.held() != 0) tap(queuedLeft); heldLeft = leftConsumed && queuedLeft.held() != 0 ? queuedLeft : null; }
        if (queuedRight != null) { use(queuedRight); if (!rightConsumed && queuedRight.held() != 0) tap(queuedRight); heldRight = rightConsumed && queuedRight.held() != 0 ? queuedRight : null; }
        if (!client.options.keyAttack.isDown()) { heldLeft = null; leftConsumed = false; }
        if (!client.options.keyUse.isDown()) { heldRight = null; rightConsumed = false; }
        if (!client.options.keyPickItem.isDown()) quickConsumed = false;
        var action = heldRight != null ? heldRight : heldLeft;
        String desired = quickConsumed ? "hamon_breath" : action == null ? "" : action.id();
        if (!desired.equals(sentHeld)) {
            if (!sentHeld.isEmpty()) { if (desired.isEmpty()) release(); else send(sentHeld, HamonActionPayload.Operation.CANCEL); }
            sentHeld = desired; heartbeat = 0;
            if (!desired.isEmpty()) { StandCamera.aimForAction(); send(desired, HamonActionPayload.Operation.START); }
        } else if (!desired.isEmpty() && ++heartbeat >= 5) { send(desired, HamonActionPayload.Operation.KEEP); heartbeat = 0; }
        queuedLeft = queuedRight = null;
    }
    private static int hintedTarget = -1;
    private static java.util.List<Integer> knownLearners = java.util.List.of();
    private static void teacherHints(Minecraft client) {
        if (client.player == null) { knownLearners = java.util.List.of(); hintedTarget = -1; return; }
        var learners = HamonService.hud(client.player).learners();
        for (int id : learners) if (!knownLearners.contains(id)) {
            var learner = client.level.getEntity(id);
            if (learner != null) client.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "jojo.chat.message.new_hamon_learner", learner.getDisplayName(), net.minecraft.network.chat.Component.keybind("jojo.key.hamon_skills_window")), false);
        }
        knownLearners = learners;
        int target = client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit ? hit.getEntity().getId() : -1;
        if (target == hintedTarget) return;
        hintedTarget = target;
        if (!(client.level.getEntity(target) instanceof net.minecraft.world.entity.player.Player teacher)) return;
        var key = net.minecraft.network.chat.Component.keybind("jojo.key.hamon_skills_window");
        net.minecraft.network.chat.Component message = null;
        if (learners.contains(target)) message = net.minecraft.network.chat.Component.translatable("jojo.chat.message.new_hamon_learner", teacher.getDisplayName(), key);
        else if (!playable() && !VampireClient.playable() && !ZombieClient.playable() && HamonService.hasHamon(teacher)) {
            message = HamonService.hud(teacher).learners().contains(client.player.getId())
                    ? net.minecraft.network.chat.Component.translatable("jojo.chat.message.asked_hamon_teacher", teacher.getDisplayName())
                    : net.minecraft.network.chat.Component.translatable("jojo.chat.message.ask_hamon_teacher", key, teacher.getDisplayName());
        }
        if (message != null) client.gui.hud.setOverlayMessage(message, false);
    }
    private static void tap(ActionCatalog.Move move) { StandCamera.aimForAction(); send(move.id(), HamonActionPayload.Operation.START); send(move.id(), HamonActionPayload.Operation.RELEASE); }
    private static void use(ActionCatalog.Move move) { if (move.held() == 0) { StandCamera.aimForAction(); send(move.id(), HamonActionPayload.Operation.START); } }
    private static void release() { if (!sentHeld.isEmpty()) send(sentHeld, HamonActionPayload.Operation.RELEASE); sentHeld = ""; heartbeat = 0; }
    public static void send(String id, HamonActionPayload.Operation operation) {
        if (ClientPlayNetworking.canSend(HamonActionPayload.TYPE)) ClientPlayNetworking.send(new HamonActionPayload(id, operation));
    }
    public static boolean openProgression() {
        var client = Minecraft.getInstance();
        if (client.player == null) return false;
        if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity target) {
            if (playable() && HamonService.hud(client.player).learners().contains(target.getId())) {
                if (ClientPlayNetworking.canSend(HamonPayload.TYPE)) ClientPlayNetworking.send(new HamonPayload(HamonPayload.Operation.TEACH, "", target.getId(), true));
                return true;
            }
            if (!playable() && !VampireClient.playable() && !ZombieClient.playable() && HamonService.hasHamon(target)) {
                if (ClientPlayNetworking.canSend(HamonPayload.TYPE)) ClientPlayNetworking.send(new HamonPayload(HamonPayload.Operation.ASK, "", target.getId(), true));
                return true;
            }
        }
        client.gui.setScreen(playable() ? new HamonScreen() : new ProgressionScreen());
        return true;
    }
    public static boolean inputBlocked() {
        var player=Minecraft.getInstance().player;
        return player!=null&&(meditating()||VampireCombatService.isStunned(player));
    }
    public static boolean meditating() {
        var p = Minecraft.getInstance().player;
        return p != null && HamonService.hud(p).meditating();
    }
    private static boolean leapPressed;
    public static net.minecraft.world.entity.player.Input movementInput(net.minecraft.world.entity.player.Input input) {
        var p = Minecraft.getInstance().player;
        if (p == null) return input;
        var hud = HamonService.hud(p);
        boolean moving = input.forward() || input.backward() || input.left() || input.right() || input.jump();
        if (hud.meditating()) {
            if (moving && hud.meditationTicks() >= 40 && ClientPlayNetworking.canSend(HamonPayload.TYPE)) ClientPlayNetworking.send(new HamonPayload(HamonPayload.Operation.MEDITATE,"",-1,false));
            return new net.minecraft.world.entity.player.Input(false,false,false,false,false,input.shift(),false);
        }
        var action = HamonActionService.state(p);
        if (moving && action.continuous().equals("joseph_rebuff_overdrive")) send("joseph_rebuff_overdrive", HamonActionPayload.Operation.CANCEL);
        boolean leap = input.jump() && (input.shift() || action.wallClimbing());
        if (leap && !leapPressed && isMode() && (action.wallClimbing() || state().hasSkill("jump") && p.onGround())) {
            send("", HamonActionPayload.Operation.LEAP); leapPressed = true;
            return new net.minecraft.world.entity.player.Input(input.forward(),input.backward(),input.left(),input.right(),false,false,input.sprint());
        }
        leapPressed = leap;
        return input;
    }
    public static void request(HamonPayload.Operation op, String id) {
        if (ClientPlayNetworking.canSend(HamonPayload.TYPE)) ClientPlayNetworking.send(new HamonPayload(op, id, -1, true));
    }
    public static boolean introRead() { var p = Minecraft.getInstance().player; return p != null && HamonService.hud(p).introRead(); }
    public static boolean teacherNearby() { var p = Minecraft.getInstance().player; return p != null && HamonService.hud(p).teacherNearby(); }
    public static String learnReason(String id) {
        var p = Minecraft.getInstance().player;
        if (p == null) return "hamon.closed.teacher.required";
        var hud = HamonService.hud(p);
        return HamonSkills.learningProblem(state(), id, hud.teacherSkills(), hud.teacherNearby(), mixTechniques(), HamonConfig.current(true).techniqueSkillsRequirement());
    }
    public static boolean mixTechniques() { return HamonConfig.current(true).mixHamonTechniques(); }
    public static int techniqueSlots() { return HamonConfig.current(true).techniqueSkillsRequirement().size(); }
    public static int techniqueRequirement(int slot) { var requirements = HamonConfig.current(true).techniqueSkillsRequirement(); return slot < requirements.size() ? requirements.get(slot) : 60; }
    public static boolean canPickTechnique() { return techniqueSlots() > 0 && Math.min(state().strengthLevel(), state().controlLevel()) >= techniqueRequirement(0); }
    public static int statGap() { return HamonConfig.current(true).breathingHamonStatGap(); }
    public static float dailyIncrease() {
        var c = HamonConfig.current(true);
        return state().dayIncrease(HamonService.wearingMask(Minecraft.getInstance().player), c.breathingTrainingDeterioration(), c.breathingTrainingMultiplier());
    }
    public static float levelFraction(int points, int level) {
        if (level >= 60) return 1;
        int start = HamonProgress.pointsAtLevel(level), end = HamonProgress.pointsAtLevel(level + 1);
        return Math.clamp((float)(points-start)/Math.max(1,end-start),0,1);
    }
    private HamonClient() { }
}
