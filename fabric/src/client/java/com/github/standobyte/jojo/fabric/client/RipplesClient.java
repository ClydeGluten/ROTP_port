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
        ModelLayerRegistry.registerModelLayer(StarPlatinumRenderer.LAYER, StarPlatinumModel::createBodyLayer);
        EntityRendererRegistry.register(RipplesFabric.STAR_PLATINUM, StarPlatinumRenderer::new);
        var category = KeyMapping.Category.register(RipplesFabric.id("powers"));
        var summon = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.summon", InputConstants.KEY_G, category));
        var light = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.light", InputConstants.KEY_R, category));
        var heavy = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.heavy", InputConstants.KEY_V, category));
        var barrage = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.barrage", InputConstants.KEY_Z, category));
        var guard = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.guard", InputConstants.KEY_X, category));
        var uppercut = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.uppercut", InputConstants.KEY_B, category));
        var finger = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.finger", InputConstants.KEY_C, category));
        var inhale = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.inhale", InputConstants.KEY_N, category));
        var zoom = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.zoom", InputConstants.KEY_Y, category));
        var control = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.control", InputConstants.KEY_H, category));
        var timeStop = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.time_stop", InputConstants.KEY_J, category));
        var progression = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jojo.fabric.progress", InputConstants.KEY_K, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean connected = client.player != null && ClientPlayNetworking.canSend(StandActionPayload.TYPE);
            boolean active = connected && client.gui.screen() == null && !client.isPaused() && client.isWindowActive()
                    && ClientPlayNetworking.canSend(StandActionPayload.TYPE);
            while (progression.consumeClick()) if (active) { client.gui.setScreen(new ProgressionScreen()); active = false; }
            while (summon.consumeClick()) if (active) send(StandActionPayload.Action.TOGGLE);
            while (light.consumeClick()) if (active) send(StandActionPayload.Action.LIGHT_PUNCH);
            while (heavy.consumeClick()) if (active) send(StandActionPayload.Action.HEAVY_PUNCH);
            while (timeStop.consumeClick()) if (active) send(StandActionPayload.Action.TIME_STOP);
            while (uppercut.consumeClick()) if (active) send(StandActionPayload.Action.UPPERCUT);
            while (finger.consumeClick()) if (active) send(StandActionPayload.Action.STAR_FINGER);
            while (inhale.consumeClick()) { /* Held state is sampled below. */ }
            while (zoom.consumeClick()) { /* Held state is sampled below. */ }
            while (control.consumeClick()) { /* Held state is sampled below. */ }
            boolean desiredControl = active && control.isDown();
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
            int desired = !active ? 0 : guard.isDown() ? 2 : barrage.isDown() ? 1 : inhale.isDown() ? 3 : zoom.isDown() ? 4 : 0;
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
        HudElementRegistry.addLast(RipplesFabric.id("stamina"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.gui.hud.isHidden()) return;
            var progress = client.player.getAttachedOrCreate(StandAttachments.PROGRESS);
            int x = 10;
            int y = graphics.guiHeight() - 105;
            if (!progress.hasStarPlatinum()) {
                var arrow = client.player.getAttachedOrCreate(StandAttachments.ARROW);
                if (arrow.infected()) {
                    graphics.text(client.font, Component.translatable("jojo.fabric.arrow.progress", arrow.paidLevels(), arrow.requiredLevels()), x, y - 11, 0xFFFF7777);
                    graphics.text(client.font, Component.translatable("jojo.fabric.arrow.remaining", (arrow.remainingTicks() + 19) / 20), x, y + 1, 0xFFFFFFFF);
                } else if (client.player.getMainHandItem().getItem() instanceof com.github.standobyte.jojo.fabric.StandArrowItem
                        || client.player.getOffhandItem().getItem() instanceof com.github.standobyte.jojo.fabric.StandArrowItem) {
                    graphics.text(client.font, Component.translatable(client.player.isCreative() ? "jojo.fabric.arrow.creative" : "jojo.fabric.arrow.requirement", arrow.nextCost()), x, y - 11, 0xFFFFD45C);
                    graphics.text(client.font, Component.translatable("jojo.fabric.arrow.use"), x, y + 1, 0xFFFFFFFF);
                }
                return;
            }
            int width = 120;
            if (client.player.isCreative()) {
                graphics.text(client.font, Component.translatable("jojo.fabric.stamina_unlimited"), x, y - 11, 0xFFBCEAFF);
            } else {
                float fraction = progress.maxStamina() > 0 ? Math.clamp(progress.stamina() / progress.maxStamina(), 0, 1) : 0;
                graphics.fill(x, y, x + width, y + 7, 0xC0000000);
                int filled = Math.round((width - 2) * fraction);
                if (filled > 0) graphics.fill(x + 1, y + 1, x + 1 + filled, y + 6, 0xFF8E45FF);
                graphics.text(client.font, Component.translatable("jojo.fabric.stamina", Math.round(progress.stamina()), Math.round(progress.maxStamina())), x, y - 11, 0xFFFFFFFF);
            }
            var resolve = client.player.getAttachedOrCreate(StandAttachments.RESOLVE);
            graphics.text(client.font, Component.translatable("jojo.fabric.progress.hud", resolve.level(), Math.round(resolve.resolve()), Math.round(resolve.maxResolve())), x, y - 55, 0xFFFFD45C);
            graphics.fill(x, y - 44, x + width, y - 39, 0xC0000000);
            int resolveWidth = Math.round((width - 2) * resolve.resolve() / resolve.maxResolve());
            if (resolveWidth > 0) graphics.fill(x + 1, y - 43, x + 1 + resolveWidth, y - 40, 0xFFFFD45C);
            graphics.text(client.font, Component.translatable("jojo.fabric.progress.hint", progression.getTranslatedKeyMessage()), x, y + 61, 0xFFFFD45C);
            var stand = StandView.ownStand(client);
            if (stand != null) {
                float charge = stand.finisherCharge();
                boolean ready = charge >= 0.5F && resolve.unlocked(1, client.player.isCreative());
                graphics.text(client.font, ready ? Component.translatable("jojo.fabric.finisher_ready", heavy.getTranslatedKeyMessage())
                        : Component.translatable("jojo.fabric.finisher", Math.round(charge * 100)), x, y - 24, 0xFFFFD45C);
                graphics.fill(x + 140, y - 23, x + 220, y - 17, 0xC0000000);
                int filled = Math.round(78 * charge);
                if (filled > 0) graphics.fill(x + 141, y - 22, x + 141 + filled, y - 18, 0xFFFFD45C);
                graphics.fill(x + 180, y - 23, x + 181, y - 17, 0xFFFFFFFF);
            }
            graphics.text(client.font, Component.translatable("jojo.fabric.controls", summon.getTranslatedKeyMessage(),
                    light.getTranslatedKeyMessage(), heavy.getTranslatedKeyMessage()), x, y + 10, 0xFFE1D3FF);
            graphics.text(client.font, Component.translatable("jojo.fabric.held_controls", barrage.getTranslatedKeyMessage(),
                    guard.getTranslatedKeyMessage()), x, y + 20, 0xFFE1D3FF);
            graphics.text(client.font, Component.translatable("jojo.fabric.advanced_controls", uppercut.getTranslatedKeyMessage(),
                    finger.getTranslatedKeyMessage(), inhale.getTranslatedKeyMessage()), x, y + 30, 0xFFE1D3FF);
            graphics.text(client.font, Component.translatable("jojo.fabric.view_controls", zoom.getTranslatedKeyMessage(),
                    control.getTranslatedKeyMessage()), x, y + 40, 0xFFE1D3FF);
            graphics.text(client.font, Component.translatable("jojo.fabric.time_controls", timeStop.getTranslatedKeyMessage()), x, y + 50, 0xFFE1D3FF);
            if (TimeStopClient.isActive()) {
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x187C63B7);
                graphics.text(client.font, Component.translatable("jojo.fabric.time_active", TimeStopClient.ticksRemaining() / 20F), x, y - 38, 0xFFFFD45C);
            }
        });
    }

    private static void send(StandActionPayload.Action action) {
        ClientPlayNetworking.send(new StandActionPayload(action));
    }
}
