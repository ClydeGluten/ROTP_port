package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.client.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;

/** Original mode/row controls, Shift variants, textured editor persistence and both Stand renderers. */
public final class ActionHudClientGameTest implements FabricClientGameTest {
    private static KeyMapping key(ClientGameTestContext context, String name) {
        return context.computeOnClient(client -> {
            var mapping = KeyMapping.get(name);
            if (mapping == null || mapping.isUnbound()) throw new AssertionError("Missing control " + name);
            return mapping;
        });
    }
    private static void mode(ClientGameTestContext context, boolean wanted) {
        if (context.computeOnClient(client -> StandSelection.isStandMode()) != wanted)
            context.getInput().pressKey(key(context, "jojo.key.stand_mode"));
        context.waitFor(client -> StandSelection.isStandMode() == wanted);
    }
    private static void slot(ClientGameTestContext context, boolean attack, int number) {
        var focus = key(context, attack ? "jojo.key.attack_hotbar" : "jojo.key.ability_hotbar");
        context.getInput().holdKey(focus);
        context.getInput().pressKey(options -> options.keyHotbarSlots[number - 1]);
        context.getInput().releaseKey(focus);
    }
    private static void editorSlot(ClientGameTestContext context, boolean attack, int index, int mouseButton) {
        var xy = context.computeOnClient(client -> {
            var screen = client.gui.screen();
            return new double[] {((screen.width - 230) / 2 + 20 + index * 18 + 9) * client.getWindow().getGuiScale(),
                    ((screen.height - 180) / 2 + (attack ? 10 : 36) + 9) * client.getWindow().getGuiScale()};
        });
        context.getInput().setCursorPos(xy[0], xy[1]);
        context.waitTicks(1);
        context.getInput().pressMouse(mouseButton);
    }
    private static void done(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            screen.setFocused(screen.children().stream().filter(child -> child instanceof AbstractWidget widget
                    && widget.getMessage().getString().equals("Done")).findFirst().orElseThrow());
        });
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitFor(client -> client.gui.screen() == null);
    }
    private static void idle(ClientGameTestContext context) {
        context.waitFor(client -> StandView.ownStand(client) != null && StandView.ownStand(client).actionPhase() == 0
                && client.player.getAttachedOrCreate(StandAttachments.HUD).recovery() == 0);
        context.waitTicks(3);
    }

    @Override public void runTest(ClientGameTestContext context) {
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var id = context.computeOnClient(c -> c.player.getUUID());
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayer(id);
                p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true);
                p.connection.teleport(0, 140, 0, 0, 0);
                p.getFoodData().setFoodLevel(17);
                StandService.grant(p);
                ProgressionService.setLevel(p, 3);
                p.setAttached(StandAttachments.PROGRESS, p.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(2000));
            });
            context.waitFor(c -> StandAttachments.resolve(c.player).level() == 3 && c.player.getY() > 139);
            context.runOnClient(c -> {
                c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                ActionLayouts.set(StandProgress.STAR_PLATINUM, ActionCatalog.defaults(false));
                ActionLayouts.set(StandProgress.THE_WORLD, ActionCatalog.defaults(true));
            });
            context.getInput().lookAt(0, 0);
            context.getInput().pressKey(key(context, "key.jojo.fabric.summon"));
            context.waitFor(c -> StandView.ownStand(c) != null && StandSelection.isStandMode());
            mode(context, false);
            context.runOnClient(c -> {
                if (StandSelection.capturesMouse()) throw new AssertionError("K did not restore vanilla mouse input");
            });
            mode(context, true);
            int hotbar = context.computeOnClient(c -> c.player.getInventory().getSelectedSlot());
            context.getInput().holdKey(key(context, "jojo.key.attack_hotbar"));
            context.getInput().scroll(-1);
            context.runOnClient(c -> {
                if (!StandSelection.selectedAction(true).equals("barrage")) throw new AssertionError("V-wheel did not select barrage");
                if (c.player.getInventory().getSelectedSlot() != hotbar) throw new AssertionError("V-wheel changed vanilla hotbar");
            });
            context.getInput().releaseKey(key(context, "jojo.key.attack_hotbar"));
            slot(context, true, 3);
            slot(context, false, 3);
            context.runOnClient(c -> {
                if (!StandSelection.selectedAction(true).equals("finger") || !StandSelection.selectedAction(false).equals("inhale"))
                    throw new AssertionError("V/B number selection picked wrong row");
                if (c.player.getInventory().getSelectedSlot() != hotbar) throw new AssertionError("Focused numbers changed vanilla hotbar");
            });

            // Deselecting a row and holding Alt must each restore actual vanilla use behavior.
            var note = new BlockPos(0, 141, 2);
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayer(id).level()
                    .setBlockAndUpdate(note, Blocks.NOTE_BLOCK.defaultBlockState()));
            context.waitFor(c -> c.level.getBlockState(note).is(Blocks.NOTE_BLOCK));
            slot(context, false, 9);
            context.runOnClient(c -> {
                if (!StandSelection.selectedAction(false).isEmpty()) throw new AssertionError("Unused number did not deselect ability row");
            });
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            world.getServer().waitFor(server -> server.getPlayerList().getPlayer(id).level().getBlockState(note).getValue(NoteBlock.NOTE) == 1);
            slot(context, false, 1);
            context.getInput().holdAlt();
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            world.getServer().waitFor(server -> server.getPlayerList().getPlayer(id).level().getBlockState(note).getValue(NoteBlock.NOTE) == 2);
            context.getInput().releaseAlt();
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayer(id).level().setBlockAndUpdate(note, Blocks.AIR.defaultBlockState()));

            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitFor(c -> c.player.getAttachedOrCreate(StandAttachments.HUD).finger() > 0);
            context.takeScreenshot("hud-star-finger-cooldown");
            idle(context);
            slot(context, true, 1);
            context.getInput().holdShift();
            context.runOnClient(c -> {
                if (!ActionCatalog.displayMove("light").id().equals("heavy")) throw new AssertionError("Shift did not replace punch icon/action");
            });
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            world.getServer().waitFor(server -> {
                var stand = StandService.current(server.getPlayerList().getPlayer(id));
                return stand.actionPhase() != 0 && stand.heavyAttack() && stand.abilityKind() == 0;
            });
            context.getInput().releaseShift();
            idle(context);

            // Capture original folded/expanded overlay at two usable GUI scales.
            context.runOnClient(c -> { c.options.guiScale().set(2); c.resizeGui(); });
            context.waitTicks(60);
            context.takeScreenshot("hud-original-folded-scale2");
            context.getInput().holdKey(key(context, "jojo.key.attack_hotbar"));
            context.getInput().holdKey(key(context, "jojo.key.ability_hotbar"));
            context.waitTicks(5);
            context.takeScreenshot("hud-original-expanded-scale2");
            context.runOnClient(c -> { c.options.guiScale().set(3); c.resizeGui(); });
            context.waitTicks(5);
            context.takeScreenshot("hud-original-expanded-scale3");
            context.getInput().releaseKey(key(context, "jojo.key.attack_hotbar"));
            context.getInput().releaseKey(key(context, "jojo.key.ability_hotbar"));

            // Opening the original editor must release a held barrage on the server.
            slot(context, true, 2);
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(id)).actionPhase() == 4);
            context.getInput().pressKey(key(context, "key.jojo.fabric.layout"));
            context.waitFor(c -> c.gui.screen() instanceof ActionLayoutScreen);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            world.getServer().waitFor(server -> StandService.current(server.getPlayerList().getPlayer(id)).actionPhase() != 4);
            context.takeScreenshot("hud-layout-editor-original");
            var beforeEditor = context.computeOnClient(c -> ActionLayouts.current());
            editorSlot(context, true, 1, InputConstants.MOUSE_BUTTON_LEFT);
            context.getInput().pressKey(InputConstants.KEY_1); // picked barrage moves into slot 1
            editorSlot(context, true, 2, InputConstants.MOUSE_BUTTON_RIGHT); // disable finger
            editorSlot(context, false, 1, InputConstants.MOUSE_BUTTON_LEFT); // pick zoom
            editorSlot(context, true, 1, InputConstants.MOUSE_BUTTON_LEFT); // transfer before light
            editorSlot(context, false, 0, InputConstants.MOUSE_BUTTON_LEFT); // pick guard
            context.getInput().setCursorPos(8, 8);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT); // outside cancels pick only
            context.runOnClient(c -> {
                if (!ActionLayouts.current().equals(beforeEditor)) throw new AssertionError("Editor changed live layout before save");
            });
            done(context);
            context.runOnClient(c -> {
                var layout = ActionLayouts.current();
                if (!layout.attacks().equals(java.util.List.of("barrage", "zoom", "light", "finger"))
                        || !layout.abilities().equals(java.util.List.of("guard", "inhale", "time_stop"))
                        || !layout.disabled().equals(java.util.List.of("finger")) || !layout.selectedAttack().equals("zoom")) {
                    throw new AssertionError("Original editor did not save reorder/disable/cross-row/cancel: " + layout);
                }
                ActionLayouts.load();
                if (!ActionLayouts.current().equals(layout)) throw new AssertionError("Layout did not survive disk reload");
                if (!ActionLayouts.get(StandProgress.THE_WORLD).equals(ActionCatalog.defaults(true))) throw new AssertionError("Layout leaked between Stands");
            });
            context.getInput().pressKey(key(context, "key.jojo.fabric.layout"));
            context.waitFor(c -> c.gui.screen() instanceof ActionLayoutScreen);
            editorSlot(context, true, 3, InputConstants.MOUSE_BUTTON_RIGHT); // re-enable finger
            context.getInput().pressKey(InputConstants.KEY_ESCAPE); // original Escape saves edits
            context.waitFor(c -> c.gui.screen() == null);
            context.runOnClient(c -> {
                if (ActionLayouts.current().disabled().contains("finger")) throw new AssertionError("Escape did not save enabled action");
                var file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("jojo/action-layouts.json");
                try { java.nio.file.Files.writeString(file, "{broken"); }
                catch (java.io.IOException error) { throw new AssertionError("Could not create corrupt-settings fixture", error); }
                ActionLayouts.load();
                if (!ActionLayouts.current().equals(ActionCatalog.defaults(false))) throw new AssertionError("Corrupt layout did not fall back");
                if (!java.nio.file.Files.exists(file.resolveSibling("action-layouts.invalid.json"))) throw new AssertionError("Corrupt settings backup missing");
                ActionLayouts.set(StandProgress.STAR_PLATINUM, ActionCatalog.defaults(false));
            });
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayer(id);
                StandService.grant(p, StandProgress.THE_WORLD);
                ProgressionService.setLevel(p, 2);
                p.setAttached(StandAttachments.PROGRESS, p.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(2100));
                StandService.toggle(p);
            });
            context.waitFor(c -> c.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld()
                    && StandView.ownStand(c) != null && StandView.ownStand(c).isTheWorld() && StandAttachments.resolve(c.player).level() == 2);
            context.runOnClient(c -> {
                if (!(c.getEntityRenderDispatcher().getRenderer(StandView.ownStand(c)) instanceof TheWorldRenderer)) throw new AssertionError("Wrong Stand renderer");
                c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                ActionLayouts.set(StandProgress.THE_WORLD, ActionCatalog.defaults(true).select(false, "time_stop"));
            });
            context.waitTicks(5);
            context.takeScreenshot("hud-the-world-front");
            mode(context, true);
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitFor(c -> c.player.getAttachedOrCreate(StandAttachments.HUD).charge() > 0);
            // Both input edges in one client task: polling only the final held state misses this release.
            context.runOnClient(c -> {
                var useKey = InputConstants.getKey(c.options.keyUse.saveString());
                KeyMapping.set(useKey, false);
                KeyMapping.set(useKey, true);
            });
            context.waitFor(c -> c.player.getAttachedOrCreate(StandAttachments.HUD).charge() == 0);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                if (TimeStopService.isActive(player) || ProgressionService.state(player).trainingPoints() != 0) {
                    throw new AssertionError("Releasing mouse before charge completed activated Time Stop");
                }
            });
            context.waitTicks(20);
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitFor(c -> c.player.getAttachedOrCreate(StandAttachments.HUD).charge() > 0);
            context.takeScreenshot("hud-the-world-time-charge");
            world.getServer().waitFor(server -> ProgressionService.state(server.getPlayerList().getPlayer(id)).trainingPoints() > 0, 100);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitFor(c -> c.player.getAttachedOrCreate(StandAttachments.HUD).timeStop() > 0);
            context.takeScreenshot("hud-the-world-time-cooldown");
            context.getInput().pressKey(key(context, "key.jojo.fabric.progress"));
            context.waitFor(c -> c.gui.screen() instanceof ProgressionScreen);
            context.getInput().pressKey(InputConstants.KEY_END);
            context.takeScreenshot("hud-the-world-progression");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            world.getServer().runOnServer(server -> StandService.dismiss(server.getPlayerList().getPlayer(id)));
            context.waitFor(c -> StandView.ownStand(c) == null);
            context.waitTicks(3);
            save = world.getWorldSave();
            context.runOnClient(c -> {
                c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                ActionLayouts.set(StandProgress.STAR_PLATINUM, ActionCatalog.defaults(false));
                ActionLayouts.set(StandProgress.THE_WORLD, ActionCatalog.defaults(true));
            });
        }
        try (var reopened = save.open()) {
            context.waitFor(c -> c.player != null && c.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld());
            context.runOnClient(c -> {
                if (StandAttachments.resolve(c.player).level() != 2 || StandAttachments.resolve(c.player).trainingPoints() != 0.5F)
                    throw new AssertionError("Reload lost The World progression");
                if (c.player.getAttachedOrCreate(StandAttachments.RESOLVE).level() != 3)
                    throw new AssertionError("The World overwrote Star Platinum learning");
            });
        }
        var properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1"); properties.setProperty("online-mode", "false");
        try (var server = context.worldBuilder().createServer(properties); var connection = server.connect()) {
            connection.waitForChunksRender();
            var id = context.computeOnClient(c -> c.player.getUUID());
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayer(id);
                player.setGameMode(GameType.CREATIVE); player.setNoGravity(true);
                player.connection.teleport(0, 140, 0, 0, 0);
                StandService.grant(player, StandProgress.THE_WORLD);
                StandService.toggle(player);
                // Server-side fixture supplies charge; the tested Shift input must choose and consume it.
                try {
                    var stand = StandService.current(player);
                    var charge = StarPlatinumEntity.class.getDeclaredField("finisher");
                    var decay = StarPlatinumEntity.class.getDeclaredField("finisherDecay");
                    charge.setAccessible(true); decay.setAccessible(true);
                    charge.setFloat(stand, 0.75F); decay.setInt(stand, 1000);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Could not create finisher-charge fixture", error);
                }
            });
            context.waitFor(c -> StandView.ownStand(c) != null && StandView.ownStand(c).isTheWorld());
            context.getInput().lookAt(0, 0);
            mode(context, true);
            context.runOnClient(c -> ActionLayouts.set(StandProgress.THE_WORLD, ActionCatalog.defaults(true)));
            context.waitFor(c -> StandView.ownStand(c).finisherCharge() >= 0.5F);
            context.getInput().holdShift();
            context.runOnClient(c -> {
                if (!ActionCatalog.displayMove("light").id().equals("kick")) throw new AssertionError("Shift did not display charged Kick variant");
            });
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            server.waitFor(mc -> StandService.current(mc.getPlayerList().getPlayer(id)).abilityKind() == 1);
            context.getInput().releaseShift();
            context.waitFor(c -> StandView.ownStand(c).finisherCharge() < 0.5F);
            context.takeScreenshot("hud-the-world-dedicated-kick");
            server.waitFor(mc -> StandService.current(mc.getPlayerList().getPlayer(id)).actionPhase() == 0);
            idle(context);
            slot(context, false, 2);
            context.getInput().holdShift();
            context.runOnClient(c -> {
                if (!ActionCatalog.displayMove("time_stop").id().equals("blink")) throw new AssertionError("Shift did not replace Time Stop with Time Skip");
            });
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitFor(c -> c.player.getZ() > 1);
            context.getInput().releaseShift();
            idle(context);
            slot(context, true, 3);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitFor(c -> StandView.ownStand(c) != null && StandView.ownStand(c).abilityKind() == 2);
            context.runOnClient(c -> {
                if (c.player.getAttachedOrCreate(StandAttachments.HUD).timePunch() != 0)
                    throw new AssertionError("Creative Time Punch should have no cooldown");
            });
            context.takeScreenshot("hud-the-world-dedicated-time-punch");
            server.runOnServer(mc -> StandService.dismiss(mc.getPlayerList().getPlayer(id)));
            context.waitFor(c -> StandView.ownStand(c) == null);
        }

    }
}
