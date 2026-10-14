package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.client.ActionCatalog;
import com.github.standobyte.jojo.fabric.client.ActionLayouts;
import com.github.standobyte.jojo.fabric.client.StandSelection;
import com.github.standobyte.jojo.fabric.client.StandView;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Real Shift/mouse input and authoritative cooldown snapshots for the original time-action variants. */
public final class TimeActionsClientGameTest implements FabricClientGameTest {
    private static void enableMode(ClientGameTestContext context) {
        if (!context.computeOnClient(client -> StandSelection.isStandMode())) {
            context.getInput().pressKey(KeyMapping.get("jojo.key.stand_mode"));
        }
        context.waitFor(client -> StandSelection.isStandMode());
    }

    private static void assertCooldown(ActionCatalog.Move action, StandHudState state, int remaining, int total) {
        if (action.cooldown(state) != remaining || action.cooldownTotal(state) != total) {
            throw new AssertionError(action.id() + " inherited another time action's cooldown: " + state);
        }
    }

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var id = context.computeOnClient(client -> client.player.getUUID());
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                player.setGameMode(GameType.SURVIVAL);
                player.setNoGravity(true);
                player.getFoodData().setFoodLevel(17);
                player.connection.teleport(0.5, 140, 0.5, 0, 0);
                for (int x = -8; x <= 8; x++) for (int z = -8; z <= 16; z++) {
                    player.level().setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
                }
                StandService.grant(player, StandProgress.STAR_PLATINUM);
                ProgressionService.setLevel(player, 3);
            });
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum()
                    && StandAttachments.resolve(client.player).level() == 3 && client.player.getY() > 139);
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                ActionLayouts.set(StandProgress.STAR_PLATINUM, ActionCatalog.defaults(false).select(false, "time_stop"));
                if (ActionCatalog.move("blink").unlocked()) throw new AssertionError("Star Platinum Blink unlocked before Resolve 4");
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                ProgressionService.setLevel(player, 4);
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(0));
                TimeStopService.setCooldown(player, 300);
                AbilityCooldowns.start(player, 11, 0);
            });
            context.waitFor(client -> StandAttachments.resolve(client.player).level() == 4
                    && client.player.getAttachedOrCreate(StandAttachments.HUD).timeStop() > 100);
            context.getInput().lookAt(0, 0);
            enableMode(context);
            context.getInput().holdShift();
            context.runOnClient(client -> {
                var action = ActionCatalog.displayMove("time_stop");
                var state = client.player.getAttachedOrCreate(StandAttachments.HUD);
                if (!action.id().equals("blink") || !action.icon().equals("star_platinum_ts_blink")
                        || action.action() != StandActionPayload.Action.BLINK || !action.unlocked()) {
                    throw new AssertionError("Star Platinum Shift variant did not become its original Blink");
                }
                if (StandView.ownStand(client) != null) throw new AssertionError("Unsummoned Blink fixture unexpectedly summoned a Stand");
                assertCooldown(action, state, 0, 0);
            });
            // A real mouse click must bypass full-stop cooldown, missing manifestation and insufficient upfront stamina.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(0));
            });
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() < 180);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            world.getServer().waitFor(server -> server.getPlayerList().getPlayer(id).getZ() > 0.75);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                if (TimeStopService.cooldownRemaining(player) <= 0 || StandService.current(player) != null) {
                    throw new AssertionError("Blink required a completed full-stop cooldown or a manifestation");
                }
            });
            context.takeScreenshot("time-actions-star-platinum-blink");
            context.getInput().releaseShift();

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                player.connection.teleport(0.5, 140, 0.5, 0, 0);
                StandService.grant(player, StandProgress.THE_WORLD);
                ProgressionService.setLevel(player, 3);
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(1000));
                StandService.toggle(player);
                TimeStopService.setCooldown(player, 300);
                AbilityCooldowns.start(player, 11, 80);
                AbilityCooldowns.start(player, 12, 40);
            });
            context.waitFor(client -> StandView.ownStand(client) != null && StandView.ownStand(client).isTheWorld()
                    && client.player.getAttachedOrCreate(StandAttachments.HUD).timePunch() > 0
                    && client.player.getAttachedOrCreate(StandAttachments.HUD).timeStop() > 100);
            context.runOnClient(client -> ActionLayouts.set(StandProgress.THE_WORLD,
                    ActionCatalog.defaults(true).select(true, "time_punch").select(false, "time_stop")));
            enableMode(context);
            context.getInput().holdShift();
            context.runOnClient(client -> {
                var state = client.player.getAttachedOrCreate(StandAttachments.HUD);
                var blink = ActionCatalog.displayMove("time_stop");
                var punch = ActionCatalog.displayMove("time_punch");
                if (!blink.icon().equals("the_world_ts_blink") || blink.level() != 2) throw new AssertionError("Wrong The World Blink variant");
                if (!punch.icon().equals("the_world_ts_punch_back") || punch.action() != StandActionPayload.Action.TIME_PUNCH) {
                    throw new AssertionError("Shift did not display/send the original Time Breaker backshot");
                }
                if (!punch.name().equals(ActionCatalog.move("time_punch").name())) throw new AssertionError("Backshot changed the original action name");
                assertCooldown(blink, state, state.blink(), state.blinkTotal());
                assertCooldown(punch, state, state.timePunch(), state.timePunchTotal());
                if (state.blinkTotal() != 80 || state.timePunchTotal() != 40 || state.timeStopTotal() != 300) {
                    throw new AssertionError("Cooldown fixture did not synchronize independent durations: " + state);
                }
            });
            context.takeScreenshot("time-actions-independent-cooldowns-backshot");
            context.getInput().releaseShift();

            // Begin a heavy attack, then replace its recovery with depleted-stamina Time Breaker via real mouse input.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                AbilityCooldowns.start(player, 12, 0);
                if (!StandService.current(player).beginAttack(true)) throw new AssertionError("Could not start recovery fixture");
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(0));
            });
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.HUD).recovery() > 0
                    && client.player.getAttachedOrCreate(StandAttachments.HUD).timePunch() == 0
                    && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() < 140);
            context.getInput().holdShift();
            world.getServer().waitFor(server -> server.getPlayerList().getPlayer(id).isShiftKeyDown());
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            world.getServer().waitFor(server -> {
                var stand = StandService.current(server.getPlayerList().getPlayer(id));
                return stand != null && stand.abilityKind() == 2;
            });
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.HUD).timePunch() > 0);
            context.takeScreenshot("time-actions-recovery-backshot");
            context.getInput().releaseShift();
            world.getServer().runOnServer(server -> StandService.dismiss(server.getPlayerList().getPlayer(id)));
            context.waitFor(client -> StandView.ownStand(client) == null);
            context.runOnClient(client -> {
                ActionLayouts.set(StandProgress.STAR_PLATINUM, ActionCatalog.defaults(false));
                ActionLayouts.set(StandProgress.THE_WORLD, ActionCatalog.defaults(true));
            });
        }
    }
}
