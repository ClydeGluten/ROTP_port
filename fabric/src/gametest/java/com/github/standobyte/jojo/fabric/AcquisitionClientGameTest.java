package com.github.standobyte.jojo.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** Real use-item packets, item rendering, infection persistence and acquisition on a dedicated server. */
public final class AcquisitionClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        int paidBeforeClose;
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var id = context.computeOnClient(client -> client.player.getUUID());
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                player.setGameMode(GameType.SURVIVAL);
                player.setInvulnerableTime(0);
                player.getFoodData().setFoodLevel(17);
                player.giveExperienceLevels(30);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StandItems.STAND_ARROW_BEETLE));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(StandItems.STAND_ARROW));
            });
            context.waitFor(client -> client.player.getMainHandItem().is(StandItems.STAND_ARROW_BEETLE) && client.player.experienceLevel == 30);
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().lookAt(0, -15);
            context.waitTicks(5);
            context.takeScreenshot("arrow-original-items-and-cost");
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.ARROW).infected());
            context.waitTicks(24);
            context.takeScreenshot("arrow-virus-progress");
            paidBeforeClose = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayer(id).getAttachedOrCreate(StandAttachments.ARROW).paidLevels());
            if (paidBeforeClose < 1) throw new AssertionError("Real use-item packet did not start XP payment");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player.getMainHandItem().getDamageValue() != 1 || player.getOffhandItem().getDamageValue() != 0) throw new AssertionError("Self-piercing consumed the wrong arrow");
                if (player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum()) throw new AssertionError("Survival acquired instantly");
            });
            save = world.getWorldSave();
        }
        try (var reopened = save.open()) {
            context.waitFor(client -> client.player != null && client.player.getAttachedOrCreate(StandAttachments.ARROW).infected());
            var id = context.computeOnClient(client -> client.player.getUUID());
            context.runOnClient(client -> {
                var arrow = client.player.getAttachedOrCreate(StandAttachments.ARROW);
                if (arrow.paidLevels() < paidBeforeClose || arrow.requiredLevels() != 30) throw new AssertionError("Reload lost acquisition progress");
            });
            context.takeScreenshot("arrow-infection-restored");
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), 500);
            reopened.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player.experienceLevel != 0 || player.getAttachedOrCreate(StandAttachments.ARROW).acquired() != 1) throw new AssertionError("Reloaded acquisition charged the wrong XP or granted twice");
            });
            context.waitFor(client -> !client.player.hasEffect(StandItems.STAND_VIRUS));
            context.getInput().pressKey(InputConstants.KEY_G);
            reopened.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(id)) != null);
            context.waitTicks(10);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.takeScreenshot("arrow-survival-awakening");
        }
        try (var reopened = save.open()) {
            context.waitFor(client -> client.player != null && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum());
            context.runOnClient(client -> {
                var arrow = client.player.getAttachedOrCreate(StandAttachments.ARROW);
                if (arrow.acquired() != 1 || arrow.nextCost() != 35 || arrow.infected()) throw new AssertionError("Successful arrow history did not persist");
            });
        }

        var properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("online-mode", "false");
        try (var server = context.worldBuilder().createServer(properties)) {
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                var id = context.computeOnClient(client -> client.player.getUUID());
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(id);
                    player.setGameMode(GameType.SURVIVAL);
                    player.getFoodData().setFoodLevel(17);
                    player.giveExperienceLevels(30);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StandItems.STAND_ARROW));
                });
                context.waitFor(client -> client.player.getMainHandItem().is(StandItems.STAND_ARROW));
                context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
                context.getInput().lookAt(0, -15);
                context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.ARROW).paidLevels() >= 2);
                context.takeScreenshot("arrow-dedicated-virus");
            }
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.ARROW).infected());
                context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), 500);
                var id = context.computeOnClient(client -> client.player.getUUID());
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(id);
                    if (player.experienceLevel != 0 || player.getAttachedOrCreate(StandAttachments.ARROW).acquired() != 1) throw new AssertionError("Dedicated server charged the wrong XP or granted twice");
                });
                // Ownership, arrow history and vanilla XP travel in separate packets.
                context.waitFor(client -> client.player.experienceLevel == 0 && client.player.getAttachedOrCreate(StandAttachments.ARROW).acquired() == 1);
                context.runOnClient(client -> {
                    if (client.player.experienceLevel != 0 || client.player.getAttachedOrCreate(StandAttachments.ARROW).acquired() != 1) throw new AssertionError("Dedicated acquisition failed across reconnect");
                });
                context.takeScreenshot("arrow-dedicated-awakened");
            }
        }
    }
}
