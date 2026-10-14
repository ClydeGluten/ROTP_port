package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.world.level.GameType;

/** Exercises real key input, C2S packets, entity/attachment sync, rendering and a saved-world reopen. */
public final class StandClientGameTest implements FabricClientGameTest {
    /** Locate the original gradient edge; an empty red warning flash must not count as stamina. */
    private static int staminaPixels(ClientGameTestContext context, java.nio.file.Path screenshot) {
        try {
            var image = javax.imageio.ImageIO.read(screenshot.toFile());
            int[] gui = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScale(), client.getWindow().getGuiScaledHeight()});
            int scale = gui[0];
            int widest = 0;
            for (int y = 5 * scale; y < Math.min(image.getHeight(), 11 * scale); y++) {
                int filled = 0;
                for (int x = 18 * scale; x < Math.min(image.getWidth(), 218 * scale); x++) {
                    int rgb = image.getRGB(x, y), g = rgb >> 8 & 255, b = rgb & 255;
                    // Even at the red pulse's 75% peak, the yellow/green fill remains above blue.
                    if (g > 10 && g - b > 15) filled = x - 18 * scale + 1;
                }
                widest = Math.max(widest, filled);
            }
            return Math.round(widest / (float) scale);
        } catch (java.io.IOException error) { throw new AssertionError("Cannot read HUD screenshot", error); }
    }

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> {
            net.minecraft.client.KeyMapping.get("key.jojo.fabric.heavy").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F6));
            net.minecraft.client.KeyMapping.get("key.jojo.fabric.uppercut").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F7));
            net.minecraft.client.KeyMapping.resetMapping();
        });
        TestWorldSave save;
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var playerId = context.computeOnClient(client -> client.player.getUUID());
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.setGameMode(GameType.CREATIVE);
                StandService.grant(player);
            });
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum());
            context.getInput().pressKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.summon"));
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)) != null);
            int entityId = world.getServer().computeOnServer(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).getId());
            context.waitFor(client -> client.level.getEntity(entityId) instanceof StarPlatinumEntity);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(10);
            if (staminaPixels(context, context.takeScreenshot("star-platinum-summoned")) != 0) {
                throw new AssertionError("Creative unlimited stamina still shows a finite bar");
            }
            double walkSpeed = context.computeOnClient(client -> client.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
            // Exercise the actual HUD pixels and attachment packets in Survival. Creative bypasses costs.
            context.getInput().holdKey(InputConstants.KEY_X);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() == StarPlatinumEntity.PHASE_GUARD);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.setGameMode(GameType.SURVIVAL);
                player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
            });
            context.waitFor(client -> !client.player.isCreative() && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() == 2000);
            context.waitFor(client -> Math.abs(client.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - walkSpeed * 0.3) < 0.000001);
            context.getInput().holdKey(InputConstants.KEY_W);
            context.getInput().holdKey(InputConstants.KEY_LCONTROL);
            context.waitTicks(6);
            context.runOnClient(client -> {
                if (client.player.isSprinting()) throw new AssertionError("Sprint key bypassed Stand guard restriction");
            });
            context.getInput().releaseKey(InputConstants.KEY_LCONTROL);
            context.getInput().releaseKey(InputConstants.KEY_W);
            int fullBar = staminaPixels(context, context.takeScreenshot("stamina-survival-full"));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(1000));
            });
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() == 1000);
            context.waitTicks(2);
            int halfBar = staminaPixels(context, context.takeScreenshot("stamina-survival-half"));
            if (fullBar < 100 || Math.abs(halfBar * 2 - fullBar) > 2) {
                throw new AssertionError("HUD did not render half stamina: full=" + fullBar + ", half=" + halfBar);
            }
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(0));
            });
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() == 0);
            context.waitTicks(2);
            if (staminaPixels(context, context.takeScreenshot("stamina-survival-empty")) != 0) throw new AssertionError("Empty stamina still renders a filled bar");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(1000));
            });
            context.getInput().releaseKey(InputConstants.KEY_X);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() == 0);
            context.getInput().holdKey(InputConstants.KEY_Z);
            context.waitTicks(40);
            float drained = context.computeOnClient(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina());
            context.runOnClient(client -> {
                if (Math.abs(client.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - walkSpeed * 0.15) > 0.000001) throw new AssertionError("Barrage speed did not synchronize to the client");
            });
            if (drained >= 950 || drained <= 0) throw new AssertionError("Survival barrage did not synchronize stamina drain: " + drained);
            int drainedBar = staminaPixels(context, context.takeScreenshot("stamina-survival-draining"));
            if (drainedBar >= halfBar) throw new AssertionError("Barrage did not visibly shrink stamina");
            context.getInput().releaseKey(InputConstants.KEY_Z);
            context.waitTicks(50);
            float restored = context.computeOnClient(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina());
            context.runOnClient(client -> {
                if (Math.abs(client.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - walkSpeed) > 0.000001) throw new AssertionError("Release left the client slowed");
            });
            if (restored <= drained) throw new AssertionError("Resting stamina did not regenerate and synchronize");
            if (staminaPixels(context, context.takeScreenshot("stamina-survival-regenerating")) <= drainedBar) throw new AssertionError("Regeneration did not visibly grow stamina");
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayer(playerId).setGameMode(GameType.CREATIVE));
            context.waitFor(client -> client.player.isCreative() && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() == 2000);
            if (staminaPixels(context, context.takeScreenshot("stamina-creative-unlimited")) != 0) throw new AssertionError("Mode switch left a stale finite stamina bar");
            for (int i = 0; i < 5; i++) {
                float expectedCharge = (i + 1) * 0.1F - 0.001F;
                context.getInput().pressKey(InputConstants.KEY_R);
                world.getServer().waitFor(server -> {
                    var stand = StandService.current(server.getPlayerList().getPlayer(playerId));
                    return stand.actionPhase() == 3 && stand.finisherCharge() >= expectedCharge;
                });
            }
            context.waitFor(client -> ((StarPlatinumEntity) client.level.getEntity(entityId)).finisherCharge() >= 0.5F);
            context.takeScreenshot("star-platinum-finisher-ready");
            context.getInput().pressKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.heavy"));
            world.getServer().waitFor(server -> {
                var stand = StandService.current(server.getPlayerList().getPlayer(playerId));
                return stand.heavyAttack() && stand.actionPhase() != 0;
            });
            world.getServer().runOnServer(server -> {
                if (StandService.current(server.getPlayerList().getPlayer(playerId)).abilityKind() != 1) throw new AssertionError("Heavy key did not automatically select charged uppercut");
            });
            context.waitTicks(6);
            context.takeScreenshot("star-platinum-heavy-punch");
            context.waitTicks(30);
            context.getInput().holdKey(InputConstants.KEY_Z);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() == StarPlatinumEntity.PHASE_BARRAGE);
            context.waitTicks(20); // Longer than the server lease: this requires real input heartbeats.
            world.getServer().runOnServer(server -> {
                if (StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() != StarPlatinumEntity.PHASE_BARRAGE) {
                    throw new AssertionError("Held barrage expired despite client heartbeats");
                }
            });
            context.waitFor(client -> ((StarPlatinumEntity) client.level.getEntity(entityId)).actionPhase() == StarPlatinumEntity.PHASE_BARRAGE);
            context.takeScreenshot("star-platinum-barrage");
            context.getInput().releaseKey(InputConstants.KEY_Z);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() == 0);
            context.waitTicks(6);
            context.getInput().holdKey(InputConstants.KEY_X);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() == StarPlatinumEntity.PHASE_GUARD);
            context.waitFor(client -> ((StarPlatinumEntity) client.level.getEntity(entityId)).actionPhase() == StarPlatinumEntity.PHASE_GUARD);
            context.takeScreenshot("star-platinum-guard");
            context.getInput().releaseKey(InputConstants.KEY_X);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() == 0);
            context.getInput().pressKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.uppercut"));
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).abilityKind() == 1);
            context.waitTicks(7);
            context.takeScreenshot("star-platinum-uppercut");
            context.waitTicks(30);
            context.getInput().pressKey(InputConstants.KEY_C);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() == StarPlatinumEntity.PHASE_FINGER);
            context.waitTicks(12);
            context.takeScreenshot("star-platinum-star-finger");
            context.waitTicks(12);
            context.getInput().holdKey(InputConstants.KEY_N);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() == StarPlatinumEntity.PHASE_INHALE);
            context.waitTicks(20);
            context.takeScreenshot("star-platinum-inhale");
            context.getInput().releaseKey(InputConstants.KEY_N);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).actionPhase() == 0);
            context.getInput().holdKey(InputConstants.KEY_Y);
            context.waitTicks(20);
            context.runOnClient(client -> {
                if (com.github.standobyte.jojo.fabric.client.StandView.zoom() <= 5) throw new AssertionError("Zoom did not narrow the camera FOV");
            });
            context.takeScreenshot("star-platinum-zoom");
            context.getInput().releaseKey(InputConstants.KEY_Y);
            context.waitTicks(15);
            context.runOnClient(client -> {
                if (com.github.standobyte.jojo.fabric.client.StandView.zoom() != 1) throw new AssertionError("Zoom did not restore on release");
                client.options.setCameraType(CameraType.FIRST_PERSON);
            });
            context.getInput().holdKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.control"));
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)).manuallyControlled());
            context.waitFor(client -> client.getCameraEntity() instanceof StarPlatinumEntity);
            var ownerPosition = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayer(playerId).position());
            context.getInput().holdKey(InputConstants.KEY_W);
            context.waitTicks(25);
            context.getInput().releaseKey(InputConstants.KEY_W);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player.position().distanceTo(ownerPosition) > 0.1) throw new AssertionError("Manual control moved the player body");
                if (StandService.current(player).position().distanceTo(ownerPosition) < 3) throw new AssertionError("Manual control failed to move the Stand");
            });
            context.takeScreenshot("star-platinum-manual-control");
            context.getInput().releaseKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.control"));
            world.getServer().waitFor(server -> !StandService.current(server.getPlayerList().getPlayer(playerId)).manuallyControlled());
            context.waitFor(client -> client.getCameraEntity() == client.player);
            int frozenTargetId = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var cow = net.minecraft.world.entity.EntityTypes.COW.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                cow.snapTo(player.position().add(2, 0, 2), 0, 0);
                cow.setNoAi(true); cow.setNoGravity(true);
                player.level().addFreshEntity(cow);
                return cow.getId();
            });
            context.getInput().pressKey(InputConstants.KEY_J);
            world.getServer().waitFor(server -> TimeStopService.isCharging(server.getPlayerList().getPlayer(playerId)));
            context.waitTicks(10);
            context.takeScreenshot("star-platinum-time-charge");
            world.getServer().waitFor(server -> TimeStopService.isActive(server.getPlayerList().getPlayer(playerId)));
            context.waitFor(client -> com.github.standobyte.jojo.fabric.client.TimeStopClient.isActive());
            int frozenTick = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayer(playerId).level().getEntity(frozenTargetId).tickCount);
            context.waitTicks(10);
            world.getServer().runOnServer(server -> {
                if (server.getPlayerList().getPlayer(playerId).level().getEntity(frozenTargetId).tickCount != frozenTick) throw new AssertionError("Time stop did not freeze mob ticks");
            });
            context.takeScreenshot("star-platinum-time-stop");
            context.getInput().pressKey(InputConstants.KEY_J);
            world.getServer().waitFor(server -> !TimeStopService.isActive(server.getPlayerList().getPlayer(playerId)));
            context.waitFor(client -> !com.github.standobyte.jojo.fabric.client.TimeStopClient.isActive());
            context.waitTicks(5);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player.level().getEntity(frozenTargetId).tickCount <= frozenTick) throw new AssertionError("Frozen mob did not resume");
                if (TimeStopService.cooldownRemaining(player) != 0) throw new AssertionError("Original Creative Time Stop should have no cooldown");
            });
            context.getInput().pressKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.summon"));
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(playerId)) == null);
            context.waitFor(client -> client.level.getEntity(entityId) == null);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, 2000, 0.25F, true));
            });
            save = world.getWorldSave();
        }
        try (var reopened = save.open()) {
            context.waitFor(client -> client.player != null && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum());
            context.runOnClient(client -> {
                var progress = client.player.getAttachedOrCreate(StandAttachments.PROGRESS);
                if (progress.development() != 0.25F || !progress.hadStand()) {
                    throw new AssertionError("Stand progression did not survive save and reload: " + progress);
                }
            });
            var playerId = context.computeOnClient(client -> client.player.getUUID());
            reopened.getServer().runOnServer(server -> {
                if (StandService.current(server.getPlayerList().getPlayer(playerId)) != null) {
                    throw new AssertionError("Transient summon survived world reload");
                }
            });
            context.takeScreenshot("star-platinum-reloaded");
        }

        // A real client connection to a dedicated test server catches integrated-server assumptions.
        var properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("online-mode", "false");
        try (var server = context.worldBuilder().createServer(properties)) {
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                var playerId = context.computeOnClient(client -> client.player.getUUID());
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(playerId);
                    player.setGameMode(GameType.CREATIVE);
                    StandService.grant(player);
                });
                context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum());
                context.getInput().pressKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.summon"));
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)) != null);
                int entityId = server.computeOnServer(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)).getId());
                context.waitFor(client -> client.level.getEntity(entityId) instanceof StarPlatinumEntity);
                context.getInput().holdKey(InputConstants.KEY_X);
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)).actionPhase() == StarPlatinumEntity.PHASE_GUARD);
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(playerId);
                    player.setGameMode(GameType.SURVIVAL);
                    player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(1000));
                });
                context.waitFor(client -> !client.player.isCreative() && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() == 1000);
                // The insecure-chat notification overlaps the top-left bar at GUI scale 3.
                context.runOnClient(client -> client.gui.toastManager().clear());
                context.waitTicks(2);
                int halfBar = staminaPixels(context, context.takeScreenshot("stamina-dedicated-half"));
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(playerId);
                    player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(500));
                });
                context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() == 500);
                context.waitTicks(2);
                int quarterBar = staminaPixels(context, context.takeScreenshot("stamina-dedicated-quarter"));
                if (quarterBar == 0 || Math.abs(quarterBar * 2 - halfBar) > 2) throw new AssertionError("Dedicated-server stamina packets did not resize the HUD");
                context.getInput().releaseKey(InputConstants.KEY_X);
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)).actionPhase() == 0);
                server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayer(playerId).setGameMode(GameType.CREATIVE));
                context.waitFor(client -> client.player.isCreative());
                context.getInput().pressKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.heavy"));
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)).actionPhase() != 0);
                context.waitTicks(5);
                context.takeScreenshot("star-platinum-dedicated-server");
                context.waitTicks(30);
                context.getInput().holdKey(InputConstants.KEY_Z);
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)).actionPhase() == StarPlatinumEntity.PHASE_BARRAGE);
                context.waitTicks(20);
                context.takeScreenshot("star-platinum-dedicated-barrage");
                context.getInput().releaseKey(InputConstants.KEY_Z);
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)).actionPhase() == 0);
                context.waitTicks(6);
                context.getInput().holdKey(InputConstants.KEY_X);
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)).actionPhase() == StarPlatinumEntity.PHASE_GUARD);
                // The dedicated server keeps ticking while the local pause menu is open.
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)).actionPhase() == 0);
                context.getInput().releaseKey(InputConstants.KEY_X);
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                context.getInput().holdKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.control"));
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(playerId)).manuallyControlled());
                context.waitFor(client -> client.getCameraEntity() instanceof StarPlatinumEntity);
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                server.waitFor(minecraft -> !StandService.current(minecraft.getPlayerList().getPlayer(playerId)).manuallyControlled());
                context.waitFor(client -> client.getCameraEntity() == client.player);
                context.getInput().releaseKey(net.minecraft.client.KeyMapping.get("key.jojo.fabric.control"));
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                // Exercise the frozen-player client through actual S2C messages, including independent regions.
                var frozenCaster = java.util.UUID.randomUUID();
                var distantCaster = java.util.UUID.randomUUID();
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(playerId);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new TimeStopPayload(player.blockPosition(),
                            player.level().dimension().identifier(), -100, frozenCaster, TimeStopPayload.State.START, 100));
                });
                context.waitFor(client -> com.github.standobyte.jojo.fabric.client.TimeStopClient.isFrozen(client.player));
                int frozenPlayerTick = context.computeOnClient(client -> client.player.tickCount);
                var frozenPlayerPosition = context.computeOnClient(client -> client.player.position());
                context.getInput().holdKey(InputConstants.KEY_W);
                context.waitTicks(8);
                context.getInput().releaseKey(InputConstants.KEY_W);
                context.runOnClient(client -> {
                    if (client.player.tickCount != frozenPlayerTick || client.player.position().distanceTo(frozenPlayerPosition) > 0.01) {
                        throw new AssertionError("Frozen local player continued to move or tick");
                    }
                });
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(playerId);
                    var distant = player.blockPosition().offset(1024, 0, 0);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new TimeStopPayload(distant,
                            player.level().dimension().identifier(), -101, distantCaster, TimeStopPayload.State.START, 100));
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new TimeStopPayload(distant,
                            player.level().dimension().identifier(), -101, distantCaster, TimeStopPayload.State.RESUME, 0));
                });
                context.waitTicks(3);
                context.runOnClient(client -> {
                    if (!com.github.standobyte.jojo.fabric.client.TimeStopClient.isFrozen(client.player)) throw new AssertionError("Distant resume cleared a different active region");
                });
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(playerId);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new TimeStopPayload(player.blockPosition(),
                            player.level().dimension().identifier(), -100, frozenCaster, TimeStopPayload.State.RESUME, 0));
                });
                context.waitFor(client -> !com.github.standobyte.jojo.fabric.client.TimeStopClient.isActive());
                context.getInput().pressKey(InputConstants.KEY_J);
                server.waitFor(minecraft -> TimeStopService.isActive(minecraft.getPlayerList().getPlayer(playerId)));
                context.waitFor(client -> com.github.standobyte.jojo.fabric.client.TimeStopClient.isActive());
                context.takeScreenshot("star-platinum-dedicated-time-stop");
                // Disconnect during active time stop must restore the server world.
            }
            // Closing the client socket and processing its server disconnect are asynchronous.
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayerCount() == 0);
            server.runOnServer(minecraft -> {
                for (var level : minecraft.getAllLevels()) {
                    for (var entity : level.getAllEntities()) {
                        if (entity instanceof StarPlatinumEntity && !entity.isRemoved()) {
                            throw new AssertionError("Disconnect left an orphan manifestation: id=" + entity.getId()
                                    + ", owner=" + ((StarPlatinumEntity) entity).owner() + ", ownerId=" + ((StarPlatinumEntity) entity).ownerId());
                        }
                    }
                }
            });
        }
    }
}
