package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.client.ActionCatalog;
import com.github.standobyte.jojo.fabric.client.ActionLayouts;
import com.github.standobyte.jojo.fabric.client.KnifeRenderer;
import com.github.standobyte.jojo.fabric.client.StandSelection;
import com.github.standobyte.jojo.fabric.client.StandView;
import com.github.standobyte.jojo.fabric.client.StuckKnifeLayer;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Actual knife models/use packets, The World's second volley, and networked stopped-time flight and impacts. */
public final class KnifeClientGameTest implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(0.5, 140, 0.5);
    private static final Vec3 PROBE_START = new Vec3(-3, 142, 1.5);

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            exercise(context, world.getServer(), "singleplayer");
        }
        var properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("online-mode", "false");
        try (var server = context.worldBuilder().createServer(properties)) {
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                exercise(context, server, "dedicated");
            }
        }
    }

    private static void exercise(ClientGameTestContext context, TestServerContext server, String mode) {
        UUID uuid = context.computeOnClient(client -> client.player.getUUID());
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.getFoodData().setFoodLevel(20);
            player.connection.teleport(START.x, START.y, START.z, 0, 0);
            for (int x = -12; x <= 12; x++) for (int z = -8; z <= 32; z++)
                player.level().setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
            StandService.grant(player, StandProgress.THE_WORLD);
            player.setAttached(StandAttachments.THE_WORLD_RESOLVE, ResolveProgress.EMPTY.withLevel(4).withTraining(95));
            player.setAttached(StandAttachments.PROGRESS, StandProgress.grant(StandProgress.THE_WORLD, true));
            int[] counts = {1, 2, 4, 8};
            for (int slot = 0; slot < counts.length; slot++)
                player.getInventory().setItem(slot, new ItemStack(StandItems.KNIFE, counts[slot]));
        });
        context.waitFor(client -> client.player.position().distanceTo(START) < 0.01
                && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld()
                && client.player.getInventory().getItem(3).is(StandItems.KNIFE));
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            ActionLayouts.set(StandProgress.THE_WORLD, ActionCatalog.defaults(true).select(false, "guard"));
        });
        context.getInput().lookAt(0, 0);
        verifyBundleModels(context, mode);
        context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).getInventory().getSelectedSlot() == 0);
        equip(context, server, uuid, 16);
        context.getInput().pressKey(key(context, "key.jojo.fabric.summon"));
        context.waitFor(client -> StandView.ownStand(client) != null && StandSelection.isStandMode());

        // Original InputHandler gives a successful selected ability priority over item use.
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(uuid)).actionPhase()
                == StarPlatinumEntity.PHASE_GUARD);
        context.waitTicks(3);
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            if (!knives(player).isEmpty() || player.getMainHandItem().getCount() != 16)
                throw new AssertionError("Selected Stand guard failed to take priority over knife use");
        });
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(uuid)).actionPhase() == 0);
        altThrow(context);
        server.waitFor(minecraft -> knives(minecraft.getPlayerList().getPlayer(uuid)).size() == 16);
        context.waitFor(client -> client.player.getMainHandItem().isEmpty());
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            if (!player.getMainHandItem().isEmpty()) throw new AssertionError("The World did not consume two eight-knife volleys");
            for (var knife : knives(player)) {
                if (knife.getOwner() != player) throw new AssertionError("The World's extra knife lost its player owner");
            }
        });
        context.runOnClient(client -> {
            if (!StandSelection.isStandMode()) throw new AssertionError("Alt bypass permanently disabled Stand mode");
        });

        waitCooldown(context, server, uuid);
        clearKnives(server, uuid);
        equip(context, server, uuid, 8);
        context.getInput().holdShift();
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).isShiftKeyDown());
        altThrow(context);
        server.waitFor(minecraft -> knives(minecraft.getPlayerList().getPlayer(uuid)).size() == 1);
        context.waitFor(client -> client.player.getMainHandItem().getCount() == 7);
        context.getInput().releaseShift();
        server.waitFor(minecraft -> !minecraft.getPlayerList().getPlayer(uuid).isShiftKeyDown());
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            if (knives(player).size() != 1 || player.getMainHandItem().getCount() != 7)
                throw new AssertionError("Shift throw triggered The World's extra volley or consumed multiple knives");
        });

        waitCooldown(context, server, uuid);
        clearKnives(server, uuid);
        equip(context, server, uuid, 16);
        int targetId = server.computeOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(2000));
            var target = EntityTypes.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
            target.snapTo(new Vec3(0.5, 140, 4.5), 180, 0);
            target.setNoAi(true); target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            target.setHealth(200);
            player.level().addFreshEntity(target);
            return target.getId();
        });
        context.waitFor(client -> client.level.getEntity(targetId) instanceof LivingEntity target && target.getHealth() == 200);
        context.getInput().pressKey(key(context, "key.jojo.fabric.time_stop"));
        server.waitFor(minecraft -> TimeStopService.isActive(minecraft.getPlayerList().getPlayer(uuid)));
        context.waitFor(client -> TimeStopClient.isActive());
        altThrow(context);
        server.waitFor(minecraft -> knives(minecraft.getPlayerList().getPlayer(uuid)).size() == 16);
        server.waitFor(minecraft -> knives(minecraft.getPlayerList().getPlayer(uuid)).stream()
                .allMatch(knife -> knife.getTimeStopFlightTicks() == 0));
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            var target = (LivingEntity) player.level().getEntity(targetId);
            if (!TimeStopService.isFrozen(target) || target.getHealth() != 200)
                throw new AssertionError("Knife entity impacts dealt damage before time resumed");
            if (knives(player).stream().noneMatch(knife -> knife.tickCount < 5
                    && knife.position().distanceTo(target.getBoundingBox().getCenter()) < 2))
                throw new AssertionError("Knife fixture did not collide with the frozen target during its flight window");
        });

        // A separate unobstructed projectile makes the five-tick budget and final packet position deterministic.
        int probeId = server.computeOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            var probe = new KnifeEntity(player.level(), player);
            probe.snapTo(PROBE_START, 0, 0);
            probe.setNoGravity(true);
            probe.setDeltaMovement(0, 0, 1);
            probe.setTimeStopFlightTicks(5);
            player.level().addFreshEntity(probe);
            return probe.getId();
        });
        server.waitFor(minecraft -> ((KnifeEntity) minecraft.getPlayerList().getPlayer(uuid).level()
                .getEntity(probeId)).getTimeStopFlightTicks() == 0);
        Vec3 frozenEnd = server.computeOnServer(minecraft -> {
            var probe = (KnifeEntity) minecraft.getPlayerList().getPlayer(uuid).level().getEntity(probeId);
            if (probe.tickCount != 5 || probe.position().distanceTo(PROBE_START) < 4.8
                    || probe.position().distanceTo(PROBE_START) > 5.01)
                throw new AssertionError("Stopped-time knife did not fly exactly five ordinary projectile ticks: age="
                        + probe.tickCount + ", travel=" + probe.position().distanceTo(PROBE_START));
            return probe.position();
        });
        context.waitFor(client -> client.level.getEntity(probeId) instanceof KnifeEntity probe
                && probe.getTimeStopFlightTicks() == 0 && probe.position().distanceTo(frozenEnd) < 0.03, 30);
        int frozenClientAge = context.computeOnClient(client -> client.level.getEntity(probeId).tickCount);
        context.waitTicks(4);
        context.runOnClient(client -> {
            var probe = (KnifeEntity) client.level.getEntity(probeId);
            if (!TimeStopClient.isActive() || probe.tickCount != frozenClientAge || probe.position().distanceTo(frozenEnd) > 0.03)
                throw new AssertionError("Frozen knife kept simulating or disagreed with its authoritative endpoint");
            if (!(client.getEntityRenderDispatcher().getRenderer(probe) instanceof KnifeRenderer renderer))
                throw new AssertionError("Knife uses a fallback entity renderer");
            for (float partialTick : new float[] {0F, 0.5F, 1F}) {
                var state = renderer.createRenderState(probe, partialTick);
                var renderedPosition = new Vec3(state.x, state.y, state.z);
                if (renderedPosition.distanceTo(probe.position()) > 0.00001 || renderedPosition.distanceTo(frozenEnd) > 0.03)
                    throw new AssertionError("Frozen knife rendered behind its final flight position at partial "
                            + partialTick + ": rendered=" + renderedPosition + ", actual=" + probe.position());
                if (Math.abs(net.minecraft.util.Mth.wrapDegrees(state.yaw - probe.getYRot())) > 0.001
                        || Math.abs(state.pitch - probe.getXRot()) > 0.001)
                    throw new AssertionError("Frozen knife rendered a stale flight rotation at partial " + partialTick);
            }
            var texture = RipplesFabric.id("textures/entity/projectiles/knife.png");
            if (!probe.getKnifeTexture().equals(texture) || client.getResourceManager().getResource(texture).isEmpty())
                throw new AssertionError("Projectile did not resolve its original knife texture");
            if (((LivingEntity) client.level.getEntity(targetId)).getHealth() != 200)
                throw new AssertionError("Client received knife damage before thaw");
        });
        server.runOnServer(minecraft -> {
            var probe = (KnifeEntity) minecraft.getPlayerList().getPlayer(uuid).level().getEntity(probeId);
            if (probe.tickCount != 5 || probe.position().distanceTo(frozenEnd) > 0.00001)
                throw new AssertionError("Frozen server knife continued moving after its five-tick budget");
        });
        context.getInput().lookAt(25, -5);
        context.takeScreenshot("knives-original-projectile-frozen-" + mode);
        context.getInput().pressKey(key(context, "key.jojo.fabric.time_stop"));
        context.waitFor(client -> !TimeStopClient.isActive());
        server.waitFor(minecraft -> ((LivingEntity) minecraft.getPlayerList().getPlayer(uuid).level().getEntity(targetId)).getHealth() < 200);
        context.waitFor(client -> ((LivingEntity) client.level.getEntity(targetId)).getHealth() < 200);
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).level().getEntity(probeId).position().distanceTo(frozenEnd) > 0.1);
        context.waitFor(client -> client.level.getEntity(probeId) != null
                && client.level.getEntity(probeId).position().distanceTo(frozenEnd) > 0.1);
        context.takeScreenshot("knives-resumed-flight-and-hit-" + mode);
        clearKnives(server, uuid);
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.level().getEntity(targetId).discard();
            StandService.dismiss(player);
        });
        verifyStuckKnives(context, server, uuid, mode);
    }

    private static void verifyStuckKnives(ClientGameTestContext context, TestServerContext server, UUID uuid, String mode) {
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            for (int knife = 0; knife < 12; knife++) StuckKnives.add(player);
        });
        context.waitFor(client -> StuckKnives.count(client.player) == 12 && StandView.ownStand(client) == null);
        context.runOnClient(client -> {
            var renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
            if (!(renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?>))
                throw new AssertionError("Stuck knives fixture did not use the actual player renderer");
            var state = renderer.createRenderState(client.player, 1F);
            if (((FabricRenderState) state).getDataOrDefault(StuckKnifeLayer.COUNT, 0) != 12)
                throw new AssertionError("Avatar render state lost the synchronized stuck-knife count");
            try {
                var field = net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredField("layers");
                field.setAccessible(true);
                var layers = (List<?>) field.get(renderer);
                if (layers.stream().filter(StuckKnifeLayer.class::isInstance).count() != 1)
                    throw new AssertionError("Player renderer must contain exactly one original stuck-knife layer");
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Could not inspect the actual player's render layers", failure);
            }
            if (client.getResourceManager().getResource(RipplesFabric.id("textures/entity/projectiles/knife.png")).isEmpty())
                throw new AssertionError("Stuck-knife layer texture is missing");
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        context.getInput().lookAt(0, 0);
        context.getInput().pressKey(options -> options.keyTogglePerspective);
        context.waitFor(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
        context.getInput().pressKey(options -> options.keyTogglePerspective);
        context.waitFor(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT);
        int previousFov = context.computeOnClient(client -> client.options.fov().get());
        context.runOnClient(client -> client.options.fov().set(45));
        double orbitPixels = context.computeOnClient(client -> {
            double sensitivity = client.options.sensitivity().get() * 0.6 + 0.2;
            double direction = client.options.invertMouseX().get() ? -1 : 1;
            return direction * 45 / (sensitivity * sensitivity * sensitivity * 8 * 0.15);
        });
        context.getInput().moveCursor(0, 0);
        context.getInput().moveCursor(orbitPixels, 0);
        context.waitTicks(3);
        context.takeScreenshot("knives-original-player-stuck-" + mode);
        context.getInput().pressKey(options -> options.keyTogglePerspective);
        context.waitFor(client -> client.options.getCameraType() == CameraType.FIRST_PERSON);
        context.runOnClient(client -> client.options.fov().set(previousFov));
        server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayer(uuid).setAttached(StuckKnives.COUNT, 0));
        context.waitFor(client -> StuckKnives.count(client.player) == 0);
        context.runOnClient(client -> {
            var renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
            var state = renderer.createRenderState(client.player, 1F);
            if (((FabricRenderState) state).getDataOrDefault(StuckKnifeLayer.COUNT, 0) != 0)
                throw new AssertionError("Cleared knife attachment left stale player render-state data");
        });
    }

    private static void verifyBundleModels(ClientGameTestContext context, String mode) {
        int[] counts = {1, 2, 4, 8};
        for (int slot = 0; slot < counts.length; slot++) {
            int selectedSlot = slot, count = counts[slot];
            context.getInput().pressKey(options -> options.keyHotbarSlots[selectedSlot]);
            context.waitFor(client -> client.player.getMainHandItem().is(StandItems.KNIFE)
                    && client.player.getMainHandItem().getCount() == count);
            context.runOnClient(client -> {
                var state = new ItemStackRenderState();
                client.getItemModelResolver().updateForLiving(state, client.player.getMainHandItem(),
                        ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, client.player);
                var particle = state.pickParticleMaterial(RandomSource.create(0));
                var expected = RipplesFabric.id("item/" + (count == 1 ? "knife" : "knives" + count));
                if (state.isEmpty() || particle == null || !particle.sprite().contents().name().equals(expected))
                    throw new AssertionError("Knife count " + count + " resolved the wrong original model/texture: " + particle);
            });
            if (mode.equals("singleplayer")) {
                context.waitTicks(4);
                context.takeScreenshot("knives-original-held-" + count);
            }
        }
        if (mode.equals("singleplayer")) {
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitFor(client -> client.gui.screen() != null);
            context.takeScreenshot("knives-original-inventory-bundles");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null);
        }
    }

    private static void equip(ClientGameTestContext context, TestServerContext server, UUID uuid, int count) {
        server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayer(uuid)
                .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StandItems.KNIFE, count)));
        context.waitFor(client -> client.player.getMainHandItem().is(StandItems.KNIFE)
                && client.player.getMainHandItem().getCount() == count);
    }

    private static void altThrow(ClientGameTestContext context) {
        var disable = key(context, "jojo.key.disable_hotbars");
        context.getInput().holdKey(disable);
        context.waitFor(client -> StandSelection.hotbarsDisabled());
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.getInput().releaseKey(disable);
    }

    private static void waitCooldown(ClientGameTestContext context, TestServerContext server, UUID uuid) {
        server.waitFor(minecraft -> !minecraft.getPlayerList().getPlayer(uuid).getCooldowns()
                .isOnCooldown(new ItemStack(StandItems.KNIFE)));
        context.waitFor(client -> !client.player.getCooldowns().isOnCooldown(new ItemStack(StandItems.KNIFE)));
    }

    private static List<KnifeEntity> knives(ServerPlayer player) {
        List<KnifeEntity> result = new ArrayList<>();
        for (var entity : player.level().getAllEntities()) {
            if (entity instanceof KnifeEntity knife && !knife.isRemoved() && knife.getOwner() == player) result.add(knife);
        }
        return result;
    }

    private static void clearKnives(TestServerContext server, UUID uuid) {
        server.runOnServer(minecraft -> knives(minecraft.getPlayerList().getPlayer(uuid)).forEach(KnifeEntity::discard));
    }

    private static KeyMapping key(ClientGameTestContext context, String name) {
        return context.computeOnClient(client -> KeyMapping.get(name));
    }
}
