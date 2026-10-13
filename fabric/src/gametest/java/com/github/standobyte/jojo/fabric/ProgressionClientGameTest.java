package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.fabric.client.ProgressionScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Real keyboard actions, synchronized progression UI, saved training and dedicated reconnect. */
public final class ProgressionClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var id = context.computeOnClient(client -> client.player.getUUID());
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
                player.connection.teleport(0, 140, 0, 0, 0);
                player.getFoodData().setFoodLevel(17);
                StandService.grant(player);
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(2000));
            });
            context.waitFor(client -> client.player.getY() > 139 && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum());
            context.getInput().pressKey(InputConstants.KEY_G);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(id)) != null);
            context.getInput().pressKey(InputConstants.KEY_C);
            context.waitTicks(5);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                if (StandService.current(player).actionPhase() != 0 || AbilityCooldowns.remaining(player, StarPlatinumEntity.PHASE_FINGER) != 0) {
                    throw new AssertionError("Actual locked key started an ability");
                }
            });
            context.takeScreenshot("progression-locked-feedback");
            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitFor(client -> client.gui.screen() instanceof ProgressionScreen);
            context.takeScreenshot("progression-new-survival");
            context.getInput().pressKey(InputConstants.KEY_END);
            context.takeScreenshot("progression-locked-moves-and-training");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null);
            context.getInput().lookAt(0, 0);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                ProgressionService.update(player, new ResolveProgress(0, 2499, 400, 1, -1, java.util.List.of(), true, 0, 0, 0));
                var husk = EntityTypes.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
                husk.snapTo(player.position().add(0, 0, 1.5), 180, 0);
                husk.setNoAi(true); husk.setNoGravity(true);
                player.level().addFreshEntity(husk);
            });
            context.waitTicks(5);
            context.getInput().pressKey(InputConstants.KEY_R);
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.RESOLVE).level() == 1);
            context.waitFor(client -> client.player.hasEffect(StandItems.RESOLVE));
            context.takeScreenshot("progression-combat-level-up");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player.getAttachedOrCreate(StandAttachments.PROGRESS).development() != 0.25F) throw new AssertionError("Combat level-up did not develop stats");
                ProgressionService.setLevel(player, 4);
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(2250));
            });
            context.waitTicks(6);
            context.getInput().pressKey(InputConstants.KEY_G);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(id)) != null);
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.RESOLVE).trainingPoints() > 0, 120);
            world.getServer().runOnServer(server -> {
                var state = ProgressionService.state(server.getPlayerList().getPlayer(id));
                if (state.trainingPoints() != 1.25F || state.timeStopTicks() != 6) throw new AssertionError("Actual Time Stop key awarded wrong training");
            });
            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitFor(client -> client.gui.screen() instanceof ProgressionScreen);
            context.getInput().pressKey(InputConstants.KEY_END);
            context.takeScreenshot("progression-trained-time-stop");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            save = world.getWorldSave();
        }
        try (var reopened = save.open()) {
            context.waitFor(client -> client.player != null && client.player.getAttachedOrCreate(StandAttachments.RESOLVE).level() == 4);
            context.runOnClient(client -> {
                var state = client.player.getAttachedOrCreate(StandAttachments.RESOLVE);
                if (state.trainingPoints() != 1.25F || state.timeStopTicks() != 6) throw new AssertionError("Reload lost fractional training");
            });
            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitFor(client -> client.gui.screen() instanceof ProgressionScreen);
            context.takeScreenshot("progression-restored");
        }
        var properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1"); properties.setProperty("online-mode", "false");
        try (var server = context.worldBuilder().createServer(properties)) {
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                var id = context.computeOnClient(client -> client.player.getUUID());
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(id);
                    player.setGameMode(GameType.SURVIVAL); StandService.grant(player);
                    ProgressionService.setLevel(player, 3); ProgressionService.setTraining(player, 12.5F);
                });
                context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.RESOLVE).level() == 3);
                context.getInput().pressKey(InputConstants.KEY_K);
                context.waitFor(client -> client.gui.screen() instanceof ProgressionScreen);
                context.takeScreenshot("progression-dedicated-unlocks");
            }
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.RESOLVE).level() == 3);
                context.runOnClient(client -> {
                    var state = client.player.getAttachedOrCreate(StandAttachments.RESOLVE);
                    if (state.trainingPoints() != 12.5F || state.unlocked(4, false)) throw new AssertionError("Reconnect changed training or unlocked Time Stop early");
                });
                context.getInput().pressKey(InputConstants.KEY_J);
                context.waitTicks(5);
                var id = context.computeOnClient(client -> client.player.getUUID());
                server.runOnServer(minecraft -> {
                    if (TimeStopService.isCharging(minecraft.getPlayerList().getPlayer(id))) throw new AssertionError("Dedicated server accepted locked Time Stop");
                });
            }
        }
    }
}
