package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StandActionPayload;
import com.github.standobyte.jojo.fabric.StandAttachments;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumModel;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RipplesClient implements ClientModInitializer {
    private int heldInput;
    private int heartbeatTicks;
    private boolean controlling;
    @Override public void onInitializeClient() {
        TimeStopClient.initialize();
        TimeSkipClient.initialize();
        ModelLayerRegistry.registerModelLayer(StarPlatinumRenderer.LAYER, StarPlatinumModel::createBodyLayer);
        EntityRendererRegistry.register(RipplesFabric.STAR_PLATINUM, StarPlatinumRenderer::new);
        ModelLayerRegistry.registerModelLayer(TheWorldRenderer.LAYER, com.github.standobyte.jojo.fabric.client.model.TheWorldModel::createBodyLayer);
        EntityRendererRegistry.register(RipplesFabric.THE_WORLD, TheWorldRenderer::new);
        EntityRendererRegistry.register(RipplesFabric.KNIFE, KnifeRenderer::new);
        var category = KeyMapping.Category.register(RipplesFabric.id("powers"));
        StandSelection.initialize(category);
        var summon = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.summon", InputConstants.KEY_M, category));
        var light = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.light", InputConstants.KEY_R, category));
        var heavy = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.heavy", InputConstants.UNKNOWN.getValue(), category));
        var barrage = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.barrage", InputConstants.KEY_Z, category));
        var guard = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.guard", InputConstants.KEY_X, category));
        var uppercut = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.uppercut", InputConstants.UNKNOWN.getValue(), category));
        var finger = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.finger", InputConstants.KEY_C, category));
        var inhale = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.inhale", InputConstants.KEY_N, category));
        var zoom = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.zoom", InputConstants.KEY_Y, category));
        var control = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.control", InputConstants.KEY_O, category));
        var timeStop = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.time_stop", InputConstants.KEY_J, category));
        var blink = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.blink", InputConstants.KEY_U, category));
        var timePunch = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.time_punch", InputConstants.KEY_I, category));
        var progression = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.progress", InputConstants.KEY_H, category));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(OriginalControlsMigration::apply);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            StandCamera.update();
            boolean connected = client.player != null && ClientPlayNetworking.canSend(StandActionPayload.TYPE);
            boolean active = connected && client.gui.screen() == null && !client.isPaused() && client.isWindowActive()
                    && ClientPlayNetworking.canSend(StandActionPayload.TYPE);
            while (progression.consumeClick()) if (active) { client.gui.setScreen(new ProgressionScreen()); active = false; }
            StandSelection.tick(client, active);
            active &= client.gui.screen() == null;
            while (summon.consumeClick()) if (active) {
                if (StandView.ownStand(client) == null) StandSelection.onStandSummon();
                send(StandActionPayload.Action.TOGGLE);
            }
            while (light.consumeClick()) if (active) send(StandActionPayload.Action.LIGHT_PUNCH);
            while (heavy.consumeClick()) if (active) send(StandActionPayload.Action.HEAVY_PUNCH);
            while (timeStop.consumeClick()) if (active) send(StandActionPayload.Action.TIME_STOP);
            while (blink.consumeClick()) if (active) send(StandActionPayload.Action.valueOf("BLINK"));
            while (timePunch.consumeClick()) if (active) send(StandActionPayload.Action.valueOf("TIME_PUNCH"));
            while (uppercut.consumeClick()) if (active) send(StandActionPayload.Action.UPPERCUT);
            while (finger.consumeClick()) if (active) send(StandActionPayload.Action.STAR_FINGER);
            while (inhale.consumeClick()) { /* Held state is sampled below. */ }
            while (zoom.consumeClick()) { /* Held state is sampled below. */ }
            while (control.consumeClick()) { /* Held state is sampled below. */ }
            boolean desiredControl = active && (control.isDown() || StandSelection.held() == 5);
            if (desiredControl != controlling) {
                if (connected) send(desiredControl ? StandActionPayload.Action.START_CONTROL : StandActionPayload.Action.STOP_CONTROL);
                controlling = desiredControl;
            }
            if (controlling) {
                var o = client.options;
                ClientPlayNetworking.send(new com.github.standobyte.jojo.fabric.StandControlPayload(
                        (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0),
                        (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0),
                        (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0), client.player.getYRot(), client.player.getXRot()));
            }
            StandView.tick(client, active);
            while (barrage.consumeClick()) { /* Held state is sampled below. */ }
            while (guard.consumeClick()) { /* Held state is sampled below. */ }
            int desired = !active ? 0 : guard.isDown() ? 2 : barrage.isDown() ? 1 : inhale.isDown() ? 3 : zoom.isDown() ? 4 : StandSelection.held() < 5 ? StandSelection.held() : 0;
            if (desired != heldInput) {
                if (connected && heldInput != 0) send(StandActionPayload.Action.STOP_HELD);
                heldInput = desired;
                heartbeatTicks = 0;
                if (desired != 0) send(switch (desired) {
                    case 2 -> StandActionPayload.Action.START_GUARD;
                    case 3 -> StandActionPayload.Action.START_INHALE;
                    case 4 -> StandActionPayload.Action.START_ZOOM;
                    default -> StandActionPayload.Action.START_BARRAGE;
                });
            } else if (desired != 0 && ++heartbeatTicks >= 5) {
                send(StandActionPayload.Action.KEEP_HELD);
                heartbeatTicks = 0;
            }
        });
        HudElementRegistry.addLast(RipplesFabric.id("stamina"), (graphics, delta) ->
                ActionHud.render(graphics, delta.getGameTimeDeltaPartialTick(false), StandSelection.isStandMode(),
                        StandSelection.attackFocused(), StandSelection.abilityFocused(), StandSelection.hotbarsDisabled()));
    }

    private static void send(StandActionPayload.Action action) {
        if (action != StandActionPayload.Action.TOGGLE && action != StandActionPayload.Action.STOP_HELD
                && action != StandActionPayload.Action.STOP_CONTROL) StandCamera.aimForAction();
        ClientPlayNetworking.send(new StandActionPayload(action));
    }
}
