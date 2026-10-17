package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.client.StandView;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real U input, instant relocation/clocks, and a connected victim retaining frozen damage and momentum. */
public final class TimeStopCombatClientGameTest implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(0.5, 140, 0.5);
    private static final Vec3 TARGET = new Vec3(0.5, 140, 4.5);

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

    private static KeyMapping key(ClientGameTestContext context, String action) {
        return context.computeOnClient(client -> KeyMapping.get("key.jojo.fabric." + action));
    }

    static void exercise(ClientGameTestContext context, TestServerContext server, String mode) {
        var uuid = context.computeOnClient(client -> client.player.getUUID());
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.level().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.PVP, true, minecraft);
            player.setGameMode(GameType.CREATIVE); player.setNoGravity(true);
            player.connection.teleport(START.x, START.y, START.z, 0, 0);
            for (int x = -8; x <= 8; x++) for (int z = -12; z <= 12; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
            }
        });
        context.waitFor(client -> client.player.position().distanceTo(START) < 0.01);
        for (String standId : new String[] {StandProgress.STAR_PLATINUM, StandProgress.THE_WORLD}) {
            server.runOnServer(minecraft -> {
                var player = minecraft.getPlayerList().getPlayer(uuid);
                StandService.grant(player, standId);
                player.connection.teleport(START.x, START.y, START.z, 0, 0);
            });
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).standId().equals(standId)
                    && StandView.ownStand(client) == null && client.player.position().distanceTo(START) < 0.01);
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().lookAt(0, 0);
            context.getInput().pressKey(key(context, "summon"));
            context.waitFor(client -> StandView.ownStand(client) != null);
            int targetId = server.computeOnServer(minecraft -> {
                var player = minecraft.getPlayerList().getPlayer(uuid);
                var target = EntityTypes.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
                target.snapTo(TARGET, 180, 0); target.setNoAi(true); target.setNoGravity(true);
                player.level().addFreshEntity(target);
                return target.getId();
            });
            context.waitFor(client -> client.level.getEntity(targetId) != null);
            context.getInput().pressKey(options -> options.keyTogglePerspective);
            context.waitFor(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
            context.waitTicks(5);
            var expected = server.computeOnServer(minecraft -> {
                var player = minecraft.getPlayerList().getPlayer(uuid);
                var target = player.level().getEntity(targetId);
                double separation = target.getBbWidth() + player.getBbWidth();
                return TARGET.add(0, 0, standId.equals(StandProgress.THE_WORLD) ? separation : -separation);
            });
            int impliedTicks = server.computeOnServer(minecraft -> {
                var player = minecraft.getPlayerList().getPlayer(uuid);
                return (int) Math.ceil(START.distanceTo(expected) / (player.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.1585));
            });
            long[] before = context.computeOnClient(client -> new long[] {
                    client.player.tickCount - client.level.getGameTime(),
                    StandView.ownStand(client).tickCount - client.level.getGameTime()});
            context.getInput().pressKey(key(context, "blink"));
            context.waitFor(client -> client.player.position().distanceTo(expected) < 0.01);
            context.waitTicks(2);
            context.runOnClient(client -> {
                var stand = StandView.ownStand(client);
                long playerSkip = client.player.tickCount - client.level.getGameTime() - before[0];
                long standSkip = stand.tickCount - client.level.getGameTime() - before[1];
                // Normal ticks advance both age and world time. The additional age must come from the packet.
                if (Math.abs(playerSkip - impliedTicks) > 2 || Math.abs(standSkip - impliedTicks) > 2) {
                    throw new AssertionError("Instant clocks did not synchronize: player=" + playerSkip + ", stand=" + standSkip + ", expected=" + impliedTicks);
                }
                float expectedYaw = standId.equals(StandProgress.THE_WORLD) ? 180 : 0;
                if (Math.abs(net.minecraft.util.Mth.wrapDegrees(client.gameRenderer.mainCamera().yRot() - expectedYaw)) > 0.01) {
                    throw new AssertionError("Third-person orbit ignored Blink's target-facing rotation");
                }
                if (stand.getInterpolation().hasActiveInterpolation()) {
                    throw new AssertionError("Instant Stand relocation still interpolates through travel path");
                }
            });
            // Both destinations are below the vanilla relative-movement limit. A custom snap
            // must leave its codec baseline alone: the next relative packet still starts at
            // the old server tracking position, otherwise the client applies travel twice.
            for (int tick = 0; tick < 3; tick++) {
                assertTrackedStandPosition(context, server, uuid, "near-range " + standId + " Blink");
                context.waitTicks(1);
            }
            context.takeScreenshot("time-skip-" + mode + "-" + standId.substring(5));
            if (standId.equals(StandProgress.THE_WORLD)) exerciseNearTimePunch(context, server, uuid);
            server.runOnServer(minecraft -> {
                var player = minecraft.getPlayerList().getPlayer(uuid);
                player.level().getEntity(targetId).discard();
                StandService.dismiss(player);
            });
            context.waitFor(client -> StandView.ownStand(client) == null);
        }

        // Connect a genuine client as victim; a high-blood vampire supplies the original
        // nine-second stop. This exercises the packet decoder and client countdown above
        // the human five-second maximum, as well as frozen damage and momentum.
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        server.runOnServer(minecraft -> {
            var victim = minecraft.getPlayerList().getPlayer(uuid);
            StandService.clear(victim);
            victim.setGameMode(GameType.SURVIVAL);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            victim.setHealth(100); victim.setInvulnerableTime(0); victim.setDeltaMovement(Vec3.ZERO);
            victim.connection.teleport(0.5, 140, 2.5, 180, 0);
        });
        context.waitFor(client -> client.player.getHealth() == 100 && client.player.position().distanceTo(new Vec3(0.5, 140, 2.5)) < 0.01);
        var casterId = server.computeOnServer(minecraft -> {
            var victim = minecraft.getPlayerList().getPlayer(uuid);
            var id = java.util.UUID.randomUUID();
            var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                    new com.mojang.authlib.GameProfile(id, "stop-" + id.toString().substring(0, 8)), false);
            var caster = new ServerPlayer(minecraft, victim.level(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            minecraft.getPlayerList().placeNewPlayer(connection, caster, cookie);
            caster.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            caster.setGameMode(GameType.CREATIVE); caster.setNoGravity(true);
            caster.snapTo(START, 0, 0);
            if (!VampireService.grant(caster, true)) throw new AssertionError("Could not grant vampire caster");
            var vampire = VampireService.progress(caster);
            caster.setAttached(VampireAttachments.PROGRESS, vampire.withBlood(vampire.maxBlood()));
            StandService.grant(caster, StandProgress.THE_WORLD);
            if (!StandService.toggle(caster) || !TimeStopService.toggle(caster)) throw new AssertionError("Could not start caster fixture");
            return id;
        });
        context.waitFor(client -> TimeStopClient.isFrozen(client.player) && TimeStopClient.ticksRemaining() > TimeStopService.STOP_TICKS);
        var frozenPosition = context.computeOnClient(client -> client.player.position());
        int frozenAge = context.computeOnClient(client -> client.player.tickCount);
        server.runOnServer(minecraft -> {
            var caster = minecraft.getPlayerList().getPlayer(casterId);
            if (!StandService.attack(caster, true)) throw new AssertionError("Caster could not attack frozen victim");
        });
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).getHealth() < 100);
        context.waitFor(client -> TimeStopClient.isFrozen(client.player) && client.player.getHealth() < 100, 30);
        context.waitFor(client -> client.player.getDeltaMovement().horizontalDistanceSqr() > 0.01, 20);
        var frozenImpulse = context.computeOnClient(client -> client.player.getDeltaMovement());
        context.waitTicks(5);
        context.runOnClient(client -> {
            if (!TimeStopClient.isFrozen(client.player)) throw new AssertionError("Victim thawed before frozen-momentum check");
            if (client.player.tickCount != frozenAge || client.player.position().distanceTo(frozenPosition) > 0.01) {
                throw new AssertionError("Frozen victim moved or simulated a client tick");
            }
            if (client.player.getDeltaMovement().distanceTo(frozenImpulse) > 0.001) {
                throw new AssertionError("Frozen client discarded or damped the received hit impulse");
            }
        });
        server.runOnServer(minecraft -> {
            var victim = minecraft.getPlayerList().getPlayer(uuid);
            var momentum = victim.getDeltaMovement();
            if (momentum.horizontalDistanceSqr() <= 0.01) throw new AssertionError("Frozen server lost victim momentum");
            var position = victim.position();
            victim.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(position.add(2, 0, 0), true, false));
            if (victim.position().distanceTo(position) > 0.01 || victim.getDeltaMovement().distanceTo(momentum) > 0.001) {
                throw new AssertionError("Rejected frozen movement changed position or erased retained impulse");
            }
        });
        context.waitTicks(3);
        context.runOnClient(client -> {
            if (!TimeStopClient.isFrozen(client.player) || client.player.position().distanceTo(frozenPosition) > 0.01
                    || client.player.getDeltaMovement().horizontalDistanceSqr() <= 0.01)
                throw new AssertionError("Frozen correction packet lost client momentum or unlocked movement");
        });
        context.takeScreenshot("time-stop-frozen-player-hit-" + mode);
        server.runOnServer(minecraft -> TimeStopService.cancel(minecraft.getPlayerList().getPlayer(casterId)));
        context.waitFor(client -> !TimeStopClient.isFrozen(client.player));
        context.waitFor(client -> client.player.position().subtract(frozenPosition).horizontalDistanceSqr() > 0.01, 30);
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).position().subtract(frozenPosition).horizontalDistanceSqr() > 0.01);
        context.takeScreenshot("time-stop-player-thaw-momentum-" + mode);
        server.runOnServer(minecraft -> {
            var caster = minecraft.getPlayerList().getPlayer(casterId);
            StandService.dismiss(caster);
            minecraft.getPlayerList().remove(caster);
            var victim = minecraft.getPlayerList().getPlayer(uuid);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
            victim.setHealth(20);
        });
    }

    private static void assertTrackedStandPosition(ClientGameTestContext context, TestServerContext server,
                                                   java.util.UUID uuid, String action) {
        var expected = server.computeOnServer(minecraft -> StandService.current(
                minecraft.getPlayerList().getPlayer(uuid)).position());
        context.runOnClient(client -> {
            var stand = StandView.ownStand(client);
            if (stand == null || stand.position().distanceTo(expected) > 0.02) {
                throw new AssertionError(action + " desynchronized tracked Stand: server=" + expected
                        + ", client=" + (stand == null ? "missing" : stand.position()));
            }
        });
    }

    private static void exerciseNearTimePunch(ClientGameTestContext context, TestServerContext server,
                                              java.util.UUID uuid) {
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.connection.teleport(START.x, START.y, START.z, 0, 0);
        });
        context.waitFor(client -> client.player.position().distanceTo(START) < 0.01);
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().lookAt(0, 0);
        context.waitTicks(5);
        assertTrackedStandPosition(context, server, uuid, "Time Punch fixture");
        var before = context.computeOnClient(client -> StandView.ownStand(client).position());
        context.getInput().pressKey(key(context, "time_punch"));
        context.waitFor(client -> StandView.ownStand(client).abilityKind() == 2);
        var relocated = server.computeOnServer(minecraft -> StandService.current(
                minecraft.getPlayerList().getPlayer(uuid)).position());
        double travel = before.distanceTo(relocated);
        if (travel < 0.5 || travel >= 7) throw new AssertionError("Time Punch fixture needs short relative travel: " + travel);
        // Inspect the first post-snap updates while the punch keeps its relocated position,
        // before returning to the player could conceal a broken relative-packet baseline.
        for (int tick = 0; tick < 3; tick++) {
            assertTrackedStandPosition(context, server, uuid, "near-range Time Punch");
            context.waitTicks(1);
        }
        context.waitFor(client -> StandView.ownStand(client).abilityKind() == 0);
        context.waitTicks(5);
        assertTrackedStandPosition(context, server, uuid, "Time Punch return to following");
    }
}
