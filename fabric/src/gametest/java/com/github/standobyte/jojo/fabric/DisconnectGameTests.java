package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.core.StandProgress;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Fabric can fire DISCONNECT from Netty's channelInactive before its server-thread disconnect hook. */
public final class DisconnectGameTests {
    @GameTest(environment = "jojo:disconnect_cleanup", maxTicks = 100)
    public void offThreadDisconnectCleansStandAndTimeStopOnServer(GameTestHelper test) {
        test.forEveryBlockInStructure(pos -> {
            if (pos.getY() == 0) test.setBlock(pos, Blocks.STONE);
        });
        ServerPlayer player = caster(test);
        var server = test.getLevel().getServer();
        var stand = StandService.current(player);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(3.5, 1, 3.5));
        target.setNoGravity(true);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        Thread serverThread = Thread.currentThread();
        test.assertTrue(TimeStopService.toggle(player), "Fixture starts actual Time Stop charging");

        test.startSequence()
                .thenWaitUntil(() -> test.assertTrue(TimeStopService.isActive(player), "Wait for actual Time Stop activation"))
                .thenExecute(() -> {
                    test.assertTrue(TimeStopService.isFrozen(target), "Callback target is frozen before disconnect");
                    TimeStopService.queueOnResume(target, () -> {
                        callbackThread.set(Thread.currentThread());
                        callbacks.incrementAndGet();
                    });
                    test.assertValueEqual(TimeStopService.pendingResumeActions(target), 1, "One callback waits for thaw");

                    // Joining while the server thread is occupied makes off-thread mutation deterministic.
                    // Keep the player registered: owner-loss cleanup must not hide a failed disconnect listener.
                    AtomicReference<Throwable> workerFailure = new AtomicReference<>();
                    Thread worker = new Thread(() -> {
                        try {
                            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.connection, server);
                        } catch (Throwable failure) {
                            workerFailure.set(failure);
                        }
                    }, "rotp-disconnect-regression");
                    worker.setDaemon(true);
                    worker.start();
                    try {
                        worker.join(5_000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted while waiting for disconnect callback", interrupted);
                    }
                    test.assertFalse(worker.isAlive(), "Off-thread disconnect schedules cleanup without waiting for the server");
                    test.assertTrue(workerFailure.get() == null,
                            "Off-thread disconnect must not throw after removing the Stand map entry: " + workerFailure.get());
                    test.assertTrue(StandService.current(player) == stand && !stand.isRemoved(),
                            "Off-thread listener leaves the manifestation untouched until server execution");
                    test.assertTrue(TimeStopService.isActive(player), "Time Stop state remains server-owned before queued cleanup");
                    test.assertValueEqual(callbacks.get(), 0, "Netty-side disconnect cannot execute world callbacks");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    test.assertTrue(server.getPlayerList().getPlayer(player.getUUID()) == player,
                            "Fixture retains the owner so normal entity ticks cannot conceal orphan cleanup failure");
                    test.assertTrue(stand.isRemoved(), "Queued disconnect discards the actual manifestation");
                    test.assertTrue(test.getLevel().getEntity(stand.getId()) == null, "Discard removes the manifestation from the level");
                    test.assertTrue(StandService.current(player) == null, "Queued disconnect clears the summon lookup");
                    test.assertFalse(TimeStopService.isActive(player), "Queued disconnect cancels active Time Stop");
                    test.assertFalse(TimeStopService.isFrozen(target), "Queued disconnect thaws the target");
                    test.assertValueEqual(TimeStopService.pendingResumeActions(target), 0, "Queued disconnect drains deferred callbacks");
                    test.assertValueEqual(callbacks.get(), 1, "Disconnect runs the deferred callback exactly once");
                    test.assertTrue(callbackThread.get() == serverThread, "Deferred world callback runs on the owning server thread");

                    // A later server-side disconnect notification must remain safe and idempotent.
                    ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.connection, server);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    test.assertValueEqual(callbacks.get(), 1, "Later notifications and ticks cannot replay the callback");
                    test.assertValueEqual(TimeStopService.pendingResumeActions(target), 0, "Callback queue stays drained");
                    test.assertTrue(stand.isRemoved() && StandService.current(player) == null, "Manifestation stays dismissed");
                    StandService.clear(player);
                    player.connection.disconnect(new DisconnectionDetails(Component.literal("Disconnect regression complete")));
                })
                .thenSucceed();
    }

    private static ServerPlayer caster(GameTestHelper test) {
        var level = test.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "disconnect-test"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.CREATIVE);
        player.setInvulnerableTime(0);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        player.setNoGravity(true);
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grant(StandProgress.STAR_PLATINUM, true));
        player.setAttached(StandAttachments.RESOLVE, ResolveProgress.EMPTY.withLevel(4).withTraining(95));
        test.assertTrue(StandService.toggle(player), "Fixture summons Star Platinum");
        return player;
    }
}
