package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.client.StandView;
import com.github.standobyte.jojo.fabric.client.StandCamera;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumRenderState;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real mouse orbit, camera-relative movement, vanilla picking and server-authoritative Stand aim. */
public final class CameraClientGameTest implements FabricClientGameTest {
    private static StarPlatinumRenderState renderState(Minecraft client, StarPlatinumEntity stand) {
        return (StarPlatinumRenderState) client.getEntityRenderDispatcher().extractEntity(stand, 1);
    }

    /** Follow remappable controls, independently of their original-mod defaults. */
    private static KeyMapping key(ClientGameTestContext context, String action) {
        return context.computeOnClient(client -> {
            var mapping = KeyMapping.get("key.jojo.fabric." + action);
            if (mapping == null || mapping.isUnbound()) throw new AssertionError("Missing test control: " + action);
            return mapping;
        });
    }

    private record Angles(float yaw, float pitch) {
        static Angles camera(Minecraft client) {
            var camera = client.gameRenderer.mainCamera();
            return new Angles(camera.yRot(), camera.xRot());
        }
        void assertSame(Minecraft client, String reason) {
            var camera = client.gameRenderer.mainCamera();
            if (angleDistance(yaw, camera.yRot()) > 0.01 || Math.abs(pitch - camera.xRot()) > 0.01) {
                throw new AssertionError(reason + ": " + this + " -> " + camera.yRot() + ", " + camera.xRot());
            }
        }
        Vec3 forward() { return Vec3.directionFromRotation(pitch, yaw); }
    }

    private static float angleDistance(float a, float b) {
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(a - b));
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
                for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                    player.level().setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
                }
                StandService.grant(player);
            });
            context.waitFor(client -> client.player.getY() > 139
                    && client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum());
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.options.bobView().set(false);
                client.options.sensitivity().set(0.5);
                client.options.invertMouseX().set(false);
                client.options.invertMouseY().set(false);
                client.options.smoothCamera = false;
            });
            context.getInput().lookAt(0, 0);
            context.getInput().pressKey(key(context, "summon"));
            context.waitFor(client -> StandView.ownStand(client) != null);
            context.getInput().holdKey(key(context, "guard"));
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
            context.getInput().releaseKey(key(context, "guard"));
            context.waitFor(client -> StandView.ownStand(client).actionPhase() == 0);
            context.waitTicks(8);

            // Actual F5 and MouseHandler input, not direct calls to StandCamera.turn().
            context.getInput().pressKey(options -> options.keyTogglePerspective);
            context.waitFor(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
            context.waitTicks(5);
            float idleBodyYaw = context.computeOnClient(client -> client.player.getYRot());
            // Vanilla deliberately ignores the first callback after grabbing the pointer.
            context.getInput().moveCursor(0, 0);
            context.getInput().moveCursor(300, 0);
            context.waitTicks(5);
            var orbit = context.computeOnClient(Angles::camera);
            context.runOnClient(client -> {
                if (angleDistance(orbit.yaw(), 45) > 0.05 || Math.abs(orbit.pitch()) > 0.01) {
                    throw new AssertionError("Real mouse deltas did not reach the orbit: " + orbit);
                }
                if (angleDistance(client.player.getYRot(), idleBodyYaw) > 0.01) throw new AssertionError("Mouse orbit rotated stationary body");
                var offset = client.gameRenderer.mainCamera().position().subtract(client.player.getEyePosition());
                if (offset.normalize().dot(orbit.forward()) > -0.999) throw new AssertionError("Unexpected lateral shoulder offset");
            });
            context.takeScreenshot("camera-mouse-orbit-independent-body");
            context.getInput().moveCursor(0, 120);
            context.waitTicks(3);
            context.runOnClient(client -> {
                new Angles(orbit.yaw(), 18).assertSame(client, "Vertical mouse input did not orbit camera");
                if (Math.abs(client.player.getXRot()) > 0.01) throw new AssertionError("Vertical orbit changed body pitch");
            });
            context.getInput().moveCursor(0, -120);
            context.waitTicks(3);
            context.runOnClient(client -> orbit.assertSame(client, "Returning vertical mouse input drifted orbit"));

            // Old shoulder convergence changed angles as near targets entered/left the player's ray.
            var stationaryCamera = context.computeOnClient(client -> client.gameRenderer.mainCamera().position());
            var nearBlock = context.computeOnClient(client -> BlockPos.containing(client.player.getEyePosition()
                    .add(orbit.forward().scale(3))));
            int nearTarget = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                var husk = EntityTypes.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
                husk.snapTo(player.position().add(0, 0, 1.5), 180, 0);
                husk.setNoAi(true); husk.setNoGravity(true);
                player.level().addFreshEntity(husk);
                return husk.getId();
            });
            context.waitFor(client -> client.level.getEntity(nearTarget) != null);
            for (int tick = 0; tick < 5; tick++) {
                context.waitTicks(1);
                context.runOnClient(client -> {
                    orbit.assertSame(client, "Nearby entity changed orbit angles");
                    if (client.gameRenderer.mainCamera().position().distanceTo(stationaryCamera) > 0.01) {
                        throw new AssertionError("Nearby entity made stationary camera jump");
                    }
                });
            }
            world.getServer().runOnServer(server -> {
                var level = server.getPlayerList().getPlayer(id).level();
                level.getEntity(nearTarget).discard();
                level.setBlockAndUpdate(nearBlock, Blocks.REDSTONE_BLOCK.defaultBlockState());
            });
            context.waitFor(client -> client.level.getEntity(nearTarget) == null
                    && client.level.getBlockState(nearBlock).is(Blocks.REDSTONE_BLOCK));
            context.waitTicks(5);
            context.runOnClient(client -> orbit.assertSame(client, "Near block changed orbit angles"));
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayer(id).level()
                    .setBlockAndUpdate(nearBlock, Blocks.AIR.defaultBlockState()));
            context.waitFor(client -> client.level.getBlockState(nearBlock).isAir());
            context.runOnClient(client -> orbit.assertSame(client, "Removing target changed orbit angles"));

            // Eight real WASD directions must travel relative to the orbit and face travel direction.
            int[][] directions = {{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};
            for (int[] input : directions) {
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayer(id);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.connection.teleport(0.5, 140, 0.5, 0, 0);
                });
                context.waitFor(client -> client.player.position().distanceTo(new Vec3(0.5, 140, 0.5)) < 0.03);
                context.waitTicks(3);
                Vec3 before = context.computeOnClient(client -> client.player.position());
                if (input[0] > 0) context.getInput().holdKey(options -> options.keyLeft);
                if (input[0] < 0) context.getInput().holdKey(options -> options.keyRight);
                if (input[1] > 0) context.getInput().holdKey(options -> options.keyUp);
                if (input[1] < 0) context.getInput().holdKey(options -> options.keyDown);
                context.waitTicks(8);
                context.runOnClient(client -> {
                    var delta = client.player.position().subtract(before).multiply(1, 0, 1);
                    double yaw = Math.toRadians(orbit.yaw());
                    var expected = new Vec3(input[0] * Math.cos(yaw) - input[1] * Math.sin(yaw), 0,
                            input[0] * Math.sin(yaw) + input[1] * Math.cos(yaw)).normalize();
                    if (delta.length() < 0.1 || delta.normalize().dot(expected) < 0.95) {
                        throw new AssertionError("Orbit-relative movement " + input[0] + "," + input[1]
                                + " travelled " + delta + ", expected " + expected);
                    }
                    if (Vec3.directionFromRotation(0, client.player.getYRot()).dot(expected) < 0.99) {
                        throw new AssertionError("Body does not face movement direction");
                    }
                    orbit.assertSame(client, "Moving body dragged camera");
                });
                context.getInput().releaseKey(options -> options.keyLeft);
                context.getInput().releaseKey(options -> options.keyRight);
                context.getInput().releaseKey(options -> options.keyUp);
                context.getInput().releaseKey(options -> options.keyDown);
                context.waitTicks(7);
            }
            context.takeScreenshot("camera-eight-direction-movement");

            // Retain vanilla camera collision behind the orbit.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.teleport(0.5, 140, 0.5, 0, 0);
                for (int x = -5; x <= 5; x++) for (int y = 140; y <= 144; y++) {
                    player.level().setBlockAndUpdate(new BlockPos(x, y, -2), Blocks.STONE.defaultBlockState());
                }
            });
            context.waitFor(client -> client.level.getBlockState(new BlockPos(2, 142, -2)).is(Blocks.STONE)
                    && client.player.position().distanceTo(new Vec3(0.5, 140, 0.5)) < 0.03);
            context.waitTicks(5);
            context.runOnClient(client -> {
                var camera = client.gameRenderer.mainCamera();
                if (camera.position().z < -0.95 || camera.position().distanceTo(client.player.getEyePosition()) >= 3) {
                    throw new AssertionError("Orbit camera did not retract before wall: " + camera.position());
                }
                orbit.assertSame(client, "Wall clipping changed orbit angles");
            });
            context.takeScreenshot("camera-wall-clipped");
            world.getServer().runOnServer(server -> {
                var level = server.getPlayerList().getPlayer(id).level();
                for (int x = -5; x <= 5; x++) for (int y = 140; y <= 144; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, -2), Blocks.AIR.defaultBlockState());
                }
            });
            context.waitFor(client -> client.level.getBlockState(new BlockPos(2, 142, -2)).isAir());
            context.waitTicks(8);

            // A Stand key must send orbit-facing rotation before the server receives its attack packet.
            int targetId = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayer(id);
                var husk = EntityTypes.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
                husk.snapTo(player.position().add(orbit.forward().scale(1.8)), 180, 0);
                husk.setNoAi(true); husk.setNoGravity(true);
                player.level().addFreshEntity(husk);
                return husk.getId();
            });
            context.waitFor(client -> client.level.getEntity(targetId) != null);
            context.getInput().pressKey(key(context, "light"));
            world.getServer().waitFor(server -> {
                var player = server.getPlayerList().getPlayer(id);
                var target = (LivingEntity) player.level().getEntity(targetId);
                return target.getHealth() < target.getMaxHealth() && angleDistance(player.getYRot(), orbit.yaw()) < 0.1;
            });
            context.runOnClient(client -> orbit.assertSame(client, "Stand attack snapped camera"));
            context.takeScreenshot("camera-orbit-server-stand-hit");
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayer(id).level().getEntity(targetId).discard());
            context.waitFor(client -> StandView.ownStand(client).actionPhase() == 0);
            context.waitTicks(8);

            // F5 front/rear shares one orbit; first person adopts the direction visible in front view.
            context.getInput().pressKey(options -> options.keyTogglePerspective);
            context.waitFor(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT);
            context.waitTicks(5);
            context.runOnClient(client -> new Angles(orbit.yaw() + 180, -orbit.pitch())
                    .assertSame(client, "Front F5 inherited body rotation instead of orbit"));
            context.takeScreenshot("camera-front-view");
            var frontBlock = context.computeOnClient(client -> BlockPos.containing(client.player.getEyePosition()
                    .add(new Angles(orbit.yaw() + 180, -orbit.pitch()).forward().scale(3))));
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayer(id).level()
                    .setBlockAndUpdate(frontBlock, Blocks.REDSTONE_BLOCK.defaultBlockState()));
            context.waitFor(client -> client.level.getBlockState(frontBlock).is(Blocks.REDSTONE_BLOCK));
            context.waitTicks(3);
            context.runOnClient(client -> {
                if (!(client.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(frontBlock)) {
                    throw new AssertionError("Front-view picking did not mirror the orbit direction");
                }
                if (angleDistance(client.player.getYRot(), orbit.yaw()) > 0.01) {
                    throw new AssertionError("Front-view picking rotated the stationary body");
                }
            });
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayer(id).level()
                    .setBlockAndUpdate(frontBlock, Blocks.AIR.defaultBlockState()));
            context.waitFor(client -> client.level.getBlockState(frontBlock).isAir());
            // Execute the same camera lifecycle before any tick can repair previous-frame angles.
            // Otherwise a five-tick check would miss the first frame interpolating from stale body aim.
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                StandCamera.update();
                if (angleDistance(client.player.getViewYRot(0.5F), orbit.yaw() + 180) > 0.01
                        || Math.abs(client.player.getViewXRot(0.5F) + orbit.pitch()) > 0.01) {
                    throw new AssertionError("First F5 frame interpolates from stale body aim");
                }
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (angleDistance(client.player.getYRot(), orbit.yaw() + 180) > 0.01) {
                    throw new AssertionError("F5 first person returned to stale body direction");
                }
            });
            context.getInput().pressKey(key(context, "finger"));
            context.waitFor(client -> StandView.ownStand(client).actionPhase() == StarPlatinumEntity.PHASE_FINGER);
            context.takeScreenshot("camera-first-person-finger");
            context.waitFor(client -> StandView.ownStand(client).actionPhase() == 0);
            context.getInput().holdKey(key(context, "control"));
            context.waitFor(client -> client.getCameraEntity() instanceof StarPlatinumEntity);
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (renderState(client, StandView.ownStand(client)).cameraOpacity != 0) throw new AssertionError("Manual camera sees inside Stand body");
            });
            context.takeScreenshot("camera-manual-body-hidden");
            context.getInput().releaseKey(key(context, "control"));
            context.waitFor(client -> client.getCameraEntity() == client.player);
            context.getInput().pressKey(options -> options.keyTogglePerspective);
            context.waitFor(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
            context.waitTicks(5);
            var resumedOrbit = context.computeOnClient(Angles::camera);
            context.getInput().pressKey(key(context, "summon"));
            context.waitFor(client -> StandView.ownStand(client) == null);
            context.waitTicks(5);
            context.runOnClient(client -> resumedOrbit.assertSame(client, "Dismissing Stand reset orbit"));
            context.takeScreenshot("camera-dismiss-keeps-orbit");

            // Vanilla interaction must pick along the orbit while body facing remains independent.
            context.getInput().moveCursor(600, 0);
            context.waitTicks(5);
            var useOrbit = context.computeOnClient(Angles::camera);
            var block = context.computeOnClient(client -> BlockPos.containing(client.player.getEyePosition()
                    .add(useOrbit.forward().scale(3))));
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayer(id).level()
                    .setBlockAndUpdate(block, Blocks.NOTE_BLOCK.defaultBlockState()));
            context.waitFor(client -> client.level.getBlockState(block).is(Blocks.NOTE_BLOCK));
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (angleDistance(client.player.getYRot(), useOrbit.yaw()) < 45) throw new AssertionError("Pick setup did not keep body independent");
                if (!(client.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(block)) {
                    throw new AssertionError("Vanilla crosshair did not pick orbit block: " + client.hitResult);
                }
            });
            // Original controls allow vanilla clicks while Alt is held, even if Stand mode is active.
            context.getInput().holdAlt();
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            world.getServer().waitFor(server -> server.getPlayerList().getPlayer(id).level()
                    .getBlockState(block).getValue(NoteBlock.NOTE) == 1);
            context.runOnClient(client -> useOrbit.assertSame(client, "Vanilla use snapped camera"));
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            world.getServer().waitFor(server -> server.getPlayerList().getPlayer(id).level().getBlockState(block).isAir());
            context.getInput().releaseAlt();
            context.runOnClient(client -> useOrbit.assertSame(client, "Vanilla attack snapped camera"));
            context.takeScreenshot("camera-vanilla-orbit-interactions");
        }
        // A new player identity must not inherit old orbit angles or the manual Stand camera.
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            // Finish the perspective transition before lookAt directly writes fixture angles.
            // Real MouseHandler input performs this transition before applying its deltas.
            context.waitTicks(1);
            context.getInput().lookAt(0, 0);
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (StandView.ownStand(client) != null || client.getCameraEntity() != client.player) throw new AssertionError("Camera owner leaked across disconnect");
            });
            context.getInput().pressKey(options -> options.keyTogglePerspective);
            context.waitFor(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
            context.waitTicks(5);
            context.runOnClient(client -> new Angles(0, 0).assertSame(client, "Previous world orbit leaked into F5"));
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }
}
