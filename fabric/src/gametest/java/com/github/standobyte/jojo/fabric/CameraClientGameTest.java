package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.client.StandView;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumRenderState;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real camera mixin, rendered Stand state, collision clipping and key-driven camera lifecycle. */
public final class CameraClientGameTest implements FabricClientGameTest {
    private static StarPlatinumRenderState renderState(Minecraft client, StarPlatinumEntity stand) {
        return (StarPlatinumRenderState) client.getEntityRenderDispatcher().extractEntity(stand, 1);
    }

    private static void assertAim(Minecraft client) {
        var camera = client.gameRenderer.mainCamera();
        var target = client.player.pick(64, 1, false).getLocation();
        double alignment = target.subtract(camera.position()).normalize().dot(new Vec3(camera.forwardVector()));
        if (alignment < 0.99999) throw new AssertionError("Shoulder crosshair missed player's block ray: " + alignment);
        if (Math.abs(client.player.getYRot()) > 0.01 || Math.abs(client.player.getXRot()) > 0.01) {
            throw new AssertionError("Camera correction rotated authoritative player aim");
        }
    }

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var id = context.computeOnClient(client -> client.player.getUUID());
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                player.setGameMode(GameType.CREATIVE);
                player.setNoGravity(true);
                player.connection.teleport(0.5, 140, 0.5, 0, 0);
                for (int x = -6; x <= 6; x++) for (int z = -7; z <= 7; z++) {
                    player.level().setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
                }
                for (int x = -3; x <= 3; x++) for (int y = 140; y <= 144; y++) {
                    player.level().setBlockAndUpdate(new BlockPos(x, y, 5), Blocks.REDSTONE_BLOCK.defaultBlockState());
                }
                StandService.grant(player);
            });
            context.waitFor(client -> client.player.getY() > 139 && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum());
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.options.bobView().set(false);
            });
            context.getInput().lookAt(0, 0);
            context.getInput().pressKey(InputConstants.KEY_G);
            context.waitFor(client -> StandView.ownStand(client) != null);
            context.getInput().holdKey(InputConstants.KEY_X);
            context.waitFor(client -> StandView.ownStand(client).actionPhase() == StarPlatinumEntity.PHASE_GUARD);
            context.waitTicks(8);
            context.runOnClient(client -> {
                var state = renderState(client, StandView.ownStand(client));
                if (state.cameraOpacity != 0.16F || state.cameraTint() >>> 24 != 41) throw new AssertionError("Own first-person Stand is opaque");
                if (client.gameRenderer.mainCamera().position().distanceTo(client.player.getEyePosition()) > 0.01) {
                    throw new AssertionError("First-person camera left the player's eye");
                }
                var other = new StarPlatinumEntity(RipplesFabric.STAR_PLATINUM, client.level);
                other.setId(1000000);
                if (renderState(client, other).cameraOpacity != 1) throw new AssertionError("Non-owned Stand faded");
            });
            context.takeScreenshot("camera-first-person-guard");

            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.runOnClient(client -> {
                var camera = client.gameRenderer.mainCamera();
                if (camera.position().x - client.player.getX() < 1) throw new AssertionError("Rear shoulder offset not applied");
                assertAim(client);
            });
            context.takeScreenshot("camera-rear-shoulder-guard");

            // A wall on the offset side must retract the camera without changing the aimed target.
            world.getServer().runOnServer(server -> {
                var level = server.getPlayerList().getPlayer(id).level();
                for (int y = 140; y <= 144; y++) for (int z = -6; z <= 2; z++) {
                    level.setBlockAndUpdate(new BlockPos(1, y, z), Blocks.STONE.defaultBlockState());
                }
            });
            context.waitFor(client -> client.level.getBlockState(new BlockPos(1, 142, -4)).is(Blocks.STONE));
            context.waitTicks(5);
            context.runOnClient(client -> {
                var camera = client.gameRenderer.mainCamera();
                if (camera.position().x >= 0.86) throw new AssertionError("Shoulder camera entered the wall: " + camera.position());
                assertAim(client);
            });
            context.takeScreenshot("camera-wall-clipped");
            world.getServer().runOnServer(server -> {
                var level = server.getPlayerList().getPlayer(id).level();
                for (int y = 140; y <= 144; y++) for (int z = -6; z <= 2; z++) {
                    level.setBlockAndUpdate(new BlockPos(1, y, z), Blocks.AIR.defaultBlockState());
                }
            });
            context.waitFor(client -> client.level.getBlockState(new BlockPos(1, 142, -4)).isAir());

            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (Math.abs(client.gameRenderer.mainCamera().position().x - client.player.getX()) > 0.01) {
                    throw new AssertionError("Rear shoulder offset leaked into front view");
                }
            });
            context.takeScreenshot("camera-front-view");
            context.getInput().releaseKey(InputConstants.KEY_X);
            context.waitFor(client -> StandView.ownStand(client).actionPhase() == 0);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            int targetId = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                var husk = net.minecraft.world.entity.EntityTypes.HUSK.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                husk.snapTo(player.position().add(0, 0, 1.5), 180, 0);
                husk.setNoAi(true); husk.setNoGravity(true);
                player.level().addFreshEntity(husk);
                return husk.getId();
            });
            context.waitFor(client -> client.level.getEntity(targetId) != null);
            context.waitTicks(5);
            context.runOnClient(client -> {
                var camera = client.gameRenderer.mainCamera();
                var target = client.level.getEntity(targetId);
                var end = camera.position().add(new Vec3(camera.forwardVector()).scale(10));
                if (target.getBoundingBox().clip(camera.position(), end).isEmpty()) throw new AssertionError("Shoulder crosshair missed nearby entity");
            });
            context.takeScreenshot("camera-rear-entity-aim");
            context.getInput().pressKey(InputConstants.KEY_R);
            world.getServer().waitFor(server -> {
                var target = (net.minecraft.world.entity.LivingEntity) server.getPlayerList().getPlayer(id).level().getEntity(targetId);
                return target.getHealth() < target.getMaxHealth();
            });
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayer(id).level().getEntity(targetId).discard());
            context.waitFor(client -> StandView.ownStand(client).actionPhase() == 0);
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_C);
            context.waitFor(client -> StandView.ownStand(client).actionPhase() == StarPlatinumEntity.PHASE_FINGER);
            context.takeScreenshot("camera-first-person-finger");
            context.waitFor(client -> StandView.ownStand(client).actionPhase() == 0);
            context.getInput().holdKey(InputConstants.KEY_H);
            context.waitFor(client -> client.getCameraEntity() instanceof StarPlatinumEntity);
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (renderState(client, StandView.ownStand(client)).cameraOpacity != 0) throw new AssertionError("Manual camera sees inside its Stand body");
            });
            context.takeScreenshot("camera-manual-body-hidden");
            context.getInput().releaseKey(InputConstants.KEY_H);
            context.waitFor(client -> client.getCameraEntity() == client.player);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.getInput().pressKey(InputConstants.KEY_G);
            context.waitFor(client -> StandView.ownStand(client) == null);
            context.waitTicks(5);
            context.runOnClient(client -> {
                var camera = client.gameRenderer.mainCamera();
                if (Math.abs(camera.position().x - client.player.getX()) > 0.01 || Math.abs(camera.yRot()) > 0.01) {
                    throw new AssertionError("Dismiss left a stale shoulder position/aim correction");
                }
            });
            context.takeScreenshot("camera-dismiss-restored");
        }
        // A new world must not inherit a camera offset from the previous owner/entity IDs.
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (StandView.ownStand(client) != null || client.getCameraEntity() != client.player) throw new AssertionError("Camera owner leaked across disconnect");
            });
        }
    }
}
