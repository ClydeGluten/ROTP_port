package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanClient;
import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanHud;
import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanActionCatalog;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.VampireAttachments;
import com.github.standobyte.jojo.fabric.VampireHudState;
import com.github.standobyte.jojo.fabric.VampireItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;

/** InputHandler's non-Stand mode, using the same action rows and held-action lifecycle as Stand mode. */
public final class VampireClient {
    public static final String POWER_ID = "jojo:vampirism";
    private static KeyMapping modeKey;
    private static Object session;
    private static boolean mode, leftConsumed, rightConsumed;
    private static ActionCatalog.Move queuedLeft, queuedRight, heldLeft, heldRight;
    private static VampireActionPayload.Action sentHeld;
    private static boolean leapPressed;
    private static int heartbeat;

    public static void initialize(KeyMapping.Category category) {
        VampireCombatClient.initialize();
        VampireHamonSound.initialize();
        VampireHamonParticleClient.initialize();
        VampireZombieClient.initialize();
        VampireBloodParticleClient.initialize();
        ClientPlayNetworking.registerGlobalReceiver(VampireConfig.Sync.TYPE, (packet, context) -> VampireConfig.receive(packet.data()));
        ClientPlayNetworking.registerGlobalReceiver(VampireResurrectionPayload.TYPE, (packet, context) -> {
            var client = context.client();
            if (client.level != null && client.level.getEntity(packet.entityId()) instanceof LivingEntity living) {
                living.deathTime = 0;
                if (living == client.player && client.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen) client.gui.setScreen(null);
            }
        });
        VampireClientSettings.initialize();
        modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("jojo.key.non_stand_mode", InputConstants.KEY_J, category));
    }

    public static boolean playable() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isAlive() && !player.isSpectator()
                && player.getAttachedOrCreate(VampireAttachments.PROGRESS).vampire();
    }
    public static boolean isMode() { return mode && playable(); }
    public static boolean unlocked(String id) {
        var player = Minecraft.getInstance().player;
        if (!playable()) return false;
        if (id.equals("vampirism_hamon_suicide")) return player.getAttachedOrCreate(VampireHamon.MEMORY).formerUser();
        return id.equals("vampirism_blood_drain") || id.equals("vampirism_blood_gift")
                || player.getAttachedOrCreate(VampireAttachments.PROGRESS).fullPower();
    }
    public static boolean available(ActionCatalog.Move move) {
        var client = Minecraft.getInstance();
        if (!move.unlocked()) return false;
        var player = client.player;
        var basic = player.getAttachedOrCreate(VampireAttachments.HUD);
        var combat = VampireCombatService.state(player);
        String id = move.id();
        boolean freeHand = id.equals("vampirism_claw_lacerate") || id.equals("vampirism_blood_drain")
                || id.equals("vampirism_blood_gift") || id.equals("vampirism_freeze");
        if (freeHand && !player.getMainHandItem().isEmpty() || basic.clawTicks() > 0 || combat.eyesTicks() > 0) return false;
        if (basic.held() == VampireHudState.Held.GIFT && !id.equals("vampirism_blood_gift")) return false;
        if (!player.isCreative() && player.getAttachedOrCreate(VampireAttachments.PROGRESS).blood() < cost(move)) return false;
        int maxCure = switch (id) {
            case "vampirism_freeze", "vampirism_space_ripper_stingy_eyes" -> 1;
            case "vampirism_claw_lacerate", "vampirism_hamon_suicide" -> Integer.MAX_VALUE;
            case "vampirism_blood_drain" -> 2;
            default -> 3;
        };
        if (VampireLifecycle.curingStage(player) > maxCure || cooldown(id) > 0) return false;
        if ((id.equals("vampirism_blood_drain") || id.equals("vampirism_blood_gift") || id.equals("vampirism_freeze"))
                && client.level.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (id.equals("vampirism_freeze") && (player.isOnFire() || client.level.environmentAttributes()
                .getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES))) return false;
        return !id.equals("vampirism_blood_gift") || player.getHealth() > 10;
    }
    private static float cost(ActionCatalog.Move move) { return move.id().equals("vampirism_freeze") ? .45F : move.cost(); }
    public static int cooldown(String id) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        var state = VampireCombatService.state(player);
        return switch (id) {
            case "vampirism_space_ripper_stingy_eyes" -> state.eyesCooldown();
            case "vampirism_dark_aura" -> state.auraCooldown();
            case "vampirism_zombie_summon" -> VampireZombieService.cooldown(player);
            default -> 0;
        };
    }
    public static int cooldownTotal(String id) {
        return switch (id) { case "vampirism_space_ripper_stingy_eyes" -> 50;
            case "vampirism_dark_aura" -> 300; case "vampirism_zombie_summon" -> 100; default -> 0; };
    }
    public static int chargeTicks(String id) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        var state = player.getAttachedOrCreate(VampireAttachments.HUD);
        return switch (id) {
            case "vampirism_blood_gift" -> state.held() == VampireHudState.Held.GIFT ? state.heldTicks() : 0;
            case "vampirism_space_ripper_stingy_eyes" -> VampireCombatService.state(player).eyesChargeTicks();
            case "vampirism_hamon_suicide" -> player.getAttachedOrCreate(VampireHamon.CHARGE);
            default -> 0;
        };
    }
    public static int chargeDuration(String id) {
        return switch (id) { case "vampirism_blood_gift" -> 60; case "vampirism_space_ripper_stingy_eyes" -> 20;
            case "vampirism_hamon_suicide" -> 100; default -> 0; };
    }
    /** Called before LocalPlayer's ordinary jump consumes the grounded state. */
    public static net.minecraft.world.entity.player.Input leapInput(net.minecraft.world.entity.player.Input input) {
        var client = Minecraft.getInstance();
        boolean pressed = input.jump() && input.shift();
        boolean activate = pressed && !leapPressed && isMode() && client.gui.screen() == null
                && client.player.getAttachedOrCreate(VampireAttachments.PROGRESS).bloodLevel(client.level.getDifficulty().getId()) > 0
                && VampireLifecycle.curingStage(client.player) < 3 && VampireCombatService.state(client.player).leapCooldown() == 0
                && (client.player.onGround() || client.player.getVehicle() != null && client.player.getVehicle().onGround())
                && !VampireCombatService.isStunned(client.player) && !TimeStopClient.isFrozen(client.player);
        leapPressed = pressed;
        if (!activate) return input;
        StandCamera.aimForAction(); send(VampireActionPayload.Action.LEAP);
        return new net.minecraft.world.entity.player.Input(input.forward(), input.backward(), input.left(), input.right(), false, false, input.sprint());
    }
    /** J retains the old Time Stop shortcut for players without a non-Stand power; custom separate keys still work. */
    public static boolean consumesTimeStopShortcut(KeyMapping shortcut) {
        return (playable() || HamonClient.playable() || ZombieClient.playable() || PillarmanClient.playable()) && modeKey != null && modeKey.saveString().equals(shortcut.saveString());
    }
    public static void close() { mode = false; clearHeld(); }
    static void suspendInput() { clearHeld(); }

    public static boolean interceptClick(boolean attack) {
        if (!(attack ? leftConsumed : rightConsumed)) {
            String id = StandSelection.selectedAction(attack);
            if (id.isEmpty() || !ClientPlayNetworking.canSend(VampireActionPayload.TYPE)) return false;
            var move = ActionCatalog.move(id);
            if (!available(move)) {
                var player = Minecraft.getInstance().player;
                if (!player.isCreative() && player.getAttachedOrCreate(VampireAttachments.PROGRESS).blood() < cost(move))
                    ActionHud.bloodRejected();
                return false;
            }
            if (attack) { queuedLeft = move; leftConsumed = true; }
            else { queuedRight = move; rightConsumed = true; }
        }
        return true;
    }
    public static boolean capturesAttackHeld() { return leftConsumed || queuedLeft != null || heldLeft != null; }

    public static void releaseMappedKey(InputConstants.Key key) {
        var client = Minecraft.getInstance();
        if (client == null || client.options == null) return;
        if (client.options.keyAttack.matches(key)) { leftConsumed = false; heldLeft = null; }
        if (client.options.keyUse.matches(key)) { rightConsumed = false; heldRight = null; }
        // Release edges must stop a hold even if the button was pressed again before the next client tick.
        if (sentHeld != null && heldLeft == null && heldRight == null) releaseSentHold();
    }

    public static void tick(Minecraft client, boolean active) {
        if (session != client.level) { close(); session = client.level; }
        tickSunBurnParticles(client);
        if (client.player != null && !client.isPaused() && VampireLifecycle.curingTicks(client.player) > 0)
            client.player.setYRot(client.player.getYRot() + (float)(Math.cos(client.player.tickCount * 3.25) * Math.PI * .4));
        if (!playable()) close();
        while (modeKey.consumeClick()) if (active && (playable() || HamonClient.playable() || ZombieClient.playable() || PillarmanClient.playable())) {
            if (PillarmanClient.playable()) { PillarmanClient.toggle(); continue; }
            if (ZombieClient.playable()) { ZombieClient.toggle(); continue; }
            if (HamonClient.playable()) { HamonClient.toggle(); continue; }
            HamonClient.close(); ZombieClient.close();
            boolean wanted = !mode;
            StandSelection.closeStandMode();
            close(); mode = wanted;
        }
        if (!active || !isMode() || StandSelection.hotbarsDisabled()) { clearHeld(); return; }
        if (queuedLeft != null) { use(queuedLeft); heldLeft = queuedLeft.held() != 0 && leftConsumed ? queuedLeft : null; }
        if (queuedRight != null) { use(queuedRight); heldRight = queuedRight.held() != 0 && rightConsumed ? queuedRight : null; }
        if (!client.options.keyAttack.isDown()) { heldLeft = null; leftConsumed = false; }
        if (!client.options.keyUse.isDown()) { heldRight = null; rightConsumed = false; }
        var action = heldRight != null ? heldRight : heldLeft;
        var desired = action == null ? null : VampireActionPayload.Action.valueOf(action.packet());
        if (desired != sentHeld) {
            if (desired == null) releaseSentHold(); else stopSentHold();
            sentHeld = desired;
            if (desired != null) { StandCamera.aimForAction(); send(desired); }
        } else if (desired != null && ++heartbeat >= 5) {
            send(VampireActionPayload.Action.KEEP_HELD); heartbeat = 0;
        }
        queuedLeft = queuedRight = null;
    }
    private static void use(ActionCatalog.Move action) {
        if (action.held() == 0) { StandCamera.aimForAction(); send(VampireActionPayload.Action.valueOf(action.packet())); }
    }
    private static void releaseSentHold() {
        if (sentHeld != null) send(VampireActionPayload.Action.RELEASE_HELD);
        sentHeld = null; heartbeat = 0;
    }
    private static void stopSentHold() {
        if (sentHeld != null) send(VampireActionPayload.Action.STOP_HELD);
        sentHeld = null; heartbeat = 0;
    }
    private static void clearHeld() {
        stopSentHold();
        leftConsumed = rightConsumed = false;
        queuedLeft = queuedRight = heldLeft = heldRight = null;
    }
    private static void send(VampireActionPayload.Action action) {
        if (ClientPlayNetworking.canSend(VampireActionPayload.TYPE)) ClientPlayNetworking.send(new VampireActionPayload(action));
    }

    /** VampireSunBurnEffect.applyEffectTick: local smoke/lava intensity follows the synchronized amplifier. */
    private static void tickSunBurnParticles(Minecraft client) {
        if (client.level == null || client.isPaused()) return;
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || TimeStopClient.isFrozen(entity)) continue;
            var effect = living.getEffect(VampireItems.SUN_BURN);
            if (effect == null) continue;
            int amplifier = effect.getAmplifier();
            var random = living.getRandom();
            double x = living.getX() + (random.nextDouble() - 0.5) * living.getBbWidth();
            double y = living.getY() + random.nextDouble() * living.getBbHeight();
            double z = living.getZ() + (random.nextDouble() - 0.5) * living.getBbWidth();
            int count = amplifier < 2 ? (amplifier + 1) * 2 : (Math.min(amplifier, 5) + 1) / 2;
            for (int i = 0; i < count; i++) client.level.addParticle(amplifier < 2 ? ParticleTypes.SMOKE : ParticleTypes.LARGE_SMOKE,
                    x, y, z, 0, 0, 0);
            if (amplifier >= 4) client.level.addParticle(ParticleTypes.LAVA,
                    living.getX() + (random.nextDouble() - 0.5) * living.getBbWidth(),
                    living.getY() + random.nextDouble() * living.getBbHeight(),
                    living.getZ() + (random.nextDouble() - 0.5) * living.getBbWidth(), 0, 0, 0);
        }
    }
    private VampireClient() { }
}
