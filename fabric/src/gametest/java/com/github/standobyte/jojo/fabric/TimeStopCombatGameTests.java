package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.core.StandProgress;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Separate batches keep regional Time Stop from pausing unrelated GameTests. */
public final class TimeStopCombatGameTests {
    @GameTest(environment = "jojo:time_stop_combat", maxTicks = 110)
    public void frozenDamageAndKnockbackFollowOriginalRules(GameTestHelper test) {
        floor(test);
        var player = caster(test, true);
        var target = cow(test, new Vec3(3.5, 1, 4.5));
        var lethalTarget = cow(test, new Vec3(5.5, 1, 4.5));
        var soundTarget = cow(test, new Vec3(1.5, 1, 4.5));
        Vec3[] frozenPosition = new Vec3[1];
        Vec3[] frozenVelocity = new Vec3[1];
        var source = standSource(player);
        test.assertTrue(TimeStopService.toggle(player), "Time Stop begins charging");

        test.startSequence()
                .thenWaitUntil(() -> test.assertTrue(TimeStopService.isActive(player), "Wait for actual Time Stop activation"))
                .thenExecute(() -> {
                    test.assertTrue(TimeStopService.isFrozen(target), "Combat target is frozen");
                    target.setHealth(100);
                    target.damageCooldownTime = 7;
                    test.assertTrue(StandDamage.hurt(test.getLevel(), target, source, 4), "First frozen Stand hit lands immediately");
                    test.assertTrue(StandDamage.hurt(test.getLevel(), target, source, 6), "Second frozen Stand hit bypasses prior hit immunity");
                    test.assertValueEqual(target.getHealth(), 90F, "Both hits change health before time resumes");
                    test.assertValueEqual(target.damageCooldownTime, 7, "Frozen Stand damage preserves the previous vanilla timer");
                    test.assertTrue(target.getLastHurtByMob() == player && target.getLastHurtByPlayer() == player,
                            "Frozen damage attributes aggression and player credit immediately");
                    test.assertTrue(target.getLastDamageSource().getEntity() == player
                                    && target.getLastDamageSource().getDirectEntity() == StandService.current(player),
                            "Frozen damage preserves owner and direct Stand source");

                    lethalTarget.setHealth(1);
                    test.assertTrue(StandDamage.hurt(test.getLevel(), lethalTarget, source, 2), "Lethal frozen hit lands");
                    test.assertTrue(lethalTarget.isDeadOrDying() && lethalTarget.getHealth() == 0,
                            "Death is resolved during stopped time");
                    test.assertTrue(lethalTarget.getKillCredit() == player, "Lethal frozen hit credits the owner immediately");

                    assertTheWorldDeferredImpact(test, player, soundTarget);
                    frozenImpulseAssertions(test, target, source);
                    // Keep live physics but zero walking speed, so resumed movement proves the stored impulse was consumed.
                    target.setPos(test.absoluteVec(new Vec3(3.5, 1, 4.5)));
                    target.setDeltaMovement(0.2, 0, 0);
                    target.setOnGround(true);
                    target.knockback(0.4, -1, 0, source, 1);
                    frozenPosition[0] = target.position();
                    frozenVelocity[0] = target.getDeltaMovement();
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    assertVector(test, target.position(), frozenPosition[0], "Frozen target cannot move before resume");
                    assertVector(test, target.getDeltaMovement(), frozenVelocity[0], "Frozen ticks preserve accumulated momentum");
                    test.assertValueEqual(target.getHealth(), 90F, "Frozen ticks do not apply hidden damage");
                    test.assertTrue(lethalTarget.isDeadOrDying(), "Frozen death remains resolved");
                    test.assertValueEqual(TimeStopService.pendingResumeActions(soundTarget), 2,
                            "The World's accepted heavy impacts remain deferred while time is stopped");
                    TimeStopService.cancel(player);
                    test.assertFalse(TimeStopService.isFrozen(target), "Manual cancellation thaws the target");
                    assertVector(test, target.getDeltaMovement(), frozenVelocity[0], "Resume neither resets nor reapplies the impulse");
                    test.assertValueEqual(target.getHealth(), 90F, "Resume does not replay health damage");
                    test.assertValueEqual(TimeStopService.pendingResumeActions(soundTarget), 0,
                            "Resume drains The World's extra impact sounds");
                })
                .thenWaitUntil(() -> test.assertTrue(target.position().distanceToSqr(frozenPosition[0]) > 0.01,
                        "Stored knockback moves the target after resume"))
                .thenExecute(() -> {
                    test.assertValueEqual(target.getHealth(), 90F, "Resumed physics does not replay Stand damage");
                    test.assertTrue(lethalTarget.isDeadOrDying(), "Resume cannot undo or repeat lethal damage");
                    test.assertValueEqual(TimeStopService.pendingResumeActions(soundTarget), 0,
                            "Resumed ticks do not requeue old heavy impact sounds");
                    finishPlayer(player);
                })
                .thenSucceed();
    }

    private static void assertTheWorldDeferredImpact(GameTestHelper test, ServerPlayer player, LivingEntity target) {
        var stand = StandService.current(player);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
        test.assertTrue(stand.isTheWorld() && TimeStopService.isFrozen(target), "The World attacks a genuinely frozen target");
        float health = target.getHealth();
        test.assertTrue(stand.beginAttack(false), "Frozen light punch begins");
        finishAttack(test, stand);
        test.assertTrue(target.getHealth() < health, "Frozen light punch actually lands");
        test.assertValueEqual(TimeStopService.pendingResumeActions(target), 0, "Ordinary light impacts are not replayed on resume");

        health = target.getHealth();
        test.assertTrue(stand.beginUppercut(), "Frozen kick begins");
        finishAttack(test, stand);
        test.assertTrue(target.getHealth() < health, "Frozen kick actually lands");
        test.assertValueEqual(TimeStopService.pendingResumeActions(target), 0, "The World's kick does not queue the heavy-punch resume sound");

        for (int hit = 1; hit <= 2; hit++) {
            health = target.getHealth();
            test.assertTrue(stand.beginAttack(true), "Frozen heavy punch begins");
            finishAttack(test, stand);
            test.assertTrue(target.getHealth() < health, "Frozen heavy punch actually lands");
            test.assertValueEqual(TimeStopService.pendingResumeActions(target), hit,
                    "Each accepted normal heavy punch queues exactly one additional resume sound");
        }

        // Creative attacks bypass permanent invulnerability, so reject this hit using a survival owner.
        player.setGameMode(GameType.SURVIVAL);
        target.setPermanentlyInvulnerable(true);
        health = target.getHealth();
        test.assertTrue(stand.beginAttack(true), "Heavy punch at an invulnerable target still starts its attack task");
        finishAttack(test, stand);
        test.assertValueEqual(target.getHealth(), health, "Invulnerable target rejects the heavy hit");
        test.assertValueEqual(TimeStopService.pendingResumeActions(target), 2, "Rejected heavy hit adds no resume sound");
        target.setPermanentlyInvulnerable(false);
        player.setGameMode(GameType.CREATIVE);
    }

    private static void finishAttack(GameTestHelper test, StarPlatinumEntity stand) {
        for (int tick = 0; tick < 100 && stand.actionPhase() != 0; tick++) stand.tick();
        test.assertValueEqual(stand.actionPhase(), 0, "Actual Stand attack completes within 100 entity ticks");
    }

    private static void frozenImpulseAssertions(GameTestHelper test, LivingEntity target, DamageSource source) {
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        target.setOnGround(true);
        target.setDeltaMovement(0.2, 0.05, 0.1);
        target.knockback(0.4, -1, 0, source, 1);
        target.knockback(0.4, -1, 0, source, 1);
        assertVector(test, target.getDeltaMovement(), new Vec3(1, 0.4, 0.1),
                "Same-direction frozen hits add to existing horizontal momentum without damping");

        target.setDeltaMovement(0.2, 0.05, 0.1);
        target.knockback(0.4, -1, 0, source, 1);
        target.knockback(0.4, 1, 0, source, 1);
        assertVector(test, target.getDeltaMovement(), new Vec3(0.2, 0.4, 0.1),
                "Opposing frozen hits cancel horizontally while preserving the original momentum");

        target.setOnGround(false);
        target.setDeltaMovement(0.1, -0.2, 0.3);
        target.knockback(0.2, 0, -1, source, 1);
        assertVector(test, target.getDeltaMovement(), new Vec3(0.1, 0, 0.5), "Frozen airborne targets gain vertical knockback");
        target.knockback(0.5, 0, -1, source, 1);
        assertVector(test, target.getDeltaMovement(), new Vec3(0.1, 0.4, 1), "Frozen vertical knockback caps at 0.4");

        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.5);
        target.setDeltaMovement(0.2, 0.05, 0.1);
        target.knockback(0.4, -1, 0, source, 1);
        assertVector(test, target.getDeltaMovement(), new Vec3(0.4, 0.25, 0.1), "Resistance scales each frozen impulse once");

        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        Vec3 unchanged = new Vec3(0.2, 0.05, 0.1);
        target.setDeltaMovement(unchanged);
        target.knockback(0.4, -1, 0, source, 1);
        assertVector(test, target.getDeltaMovement(), unchanged, "Full resistance preserves momentum");

        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        target.knockback(0, -1, 0, source, 1);
        assertVector(test, target.getDeltaMovement(), unchanged, "Zero-strength Resolve barrage preserves momentum");
        target.setDeltaMovement(Vec3.ZERO);
        for (int hit = 0; hit < 3; hit++) target.knockback(0.048, 0, -1, source, 1);
        assertVector(test, target.getDeltaMovement(), new Vec3(0, 0.144, 0.144), "Reduced barrage impulses accumulate during Time Stop");
    }

    @GameTest
    public void ordinaryKnockbackKeepsVanillaDamping(GameTestHelper test) {
        var target = cow(test, new Vec3(3.5, 1, 4.5));
        test.assertFalse(TimeStopService.isFrozen(target), "Control target is outside stopped time");
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        target.setOnGround(true);
        target.setDeltaMovement(0.2, 0.05, 0.1);
        target.knockback(0.4, -1, 0, target.damageSources().generic(), 1);
        assertVector(test, target.getDeltaMovement(), new Vec3(0.5, 0.4, 0.05), "Ordinary grounded knockback still halves prior horizontal velocity");
        target.setOnGround(false);
        target.setDeltaMovement(0.2, 0.05, 0.1);
        target.knockback(0.4, -1, 0, target.damageSources().generic(), 1);
        assertVector(test, target.getDeltaMovement(), new Vec3(0.5, 0.05, 0.05), "Ordinary airborne knockback preserves vertical velocity");
        test.succeed();
    }

    private enum EndReason { MANUAL, NATURAL, EXHAUSTION, DEATH, STAND_REMOVED, DIMENSION, DISCONNECT }

    @GameTest(environment = "jojo:time_stop_callbacks", maxTicks = 600)
    public void resumeCallbacksFollowTargetLifecycleExactlyOnce(GameTestHelper test) {
        floor(test);
        var target = cow(test, new Vec3(3.5, 1, 4.5));
        var sequence = test.startSequence();
        ServerPlayer[] current = new ServerPlayer[1];

        for (EndReason reason : EndReason.values()) {
            List<Integer> callbacks = new ArrayList<>();
            int[] removedCallbacks = {0};
            LivingEntity[] removedTarget = new LivingEntity[1];
            sequence.thenExecute(() -> {
                        current[0] = caster(test);
                        test.assertTrue(TimeStopService.toggle(current[0]), reason + ": charge begins");
                    })
                    .thenWaitUntil(() -> test.assertTrue(TimeStopService.isActive(current[0]), reason + ": wait for activation"))
                    .thenExecute(() -> {
                        test.assertTrue(TimeStopService.isFrozen(target), reason + ": target is frozen");
                        TimeStopService.queueOnResume(target, () -> callbacks.add(1));
                        TimeStopService.queueOnResume(target, () -> callbacks.add(2));
                        test.assertValueEqual(TimeStopService.pendingResumeActions(target), 2, reason + ": both effects remain queued");
                        test.assertTrue(callbacks.isEmpty(), reason + ": effects cannot run during frozen time");
                        int[] immediate = {0};
                        TimeStopService.queueOnResume(current[0], () -> immediate[0]++);
                        test.assertValueEqual(immediate[0], 1, reason + ": immune caster runs callbacks immediately");

                        removedTarget[0] = cow(test, new Vec3(5.5, 1, 4.5));
                        TimeStopService.queueOnResume(removedTarget[0], () -> removedCallbacks[0]++);
                        test.assertValueEqual(TimeStopService.pendingResumeActions(removedTarget[0]), 1, reason + ": removable target holds an effect");
                        removedTarget[0].discard();
                    })
                    .thenIdle(3)
                    .thenExecute(() -> {
                        test.assertTrue(callbacks.isEmpty(), reason + ": queued effects stay deferred across server ticks");
                        test.assertValueEqual(removedCallbacks[0], 0, reason + ": removed targets cannot run effects");
                        endStop(test, current[0], reason);
                        if (reason == EndReason.MANUAL) {
                            test.assertValueEqual(callbacks, List.of(1, 2), "Manual cancellation releases effects synchronously");
                        }
                    })
                    .thenWaitUntil(() -> test.assertFalse(TimeStopService.isActive(current[0]), reason + ": wait for stop termination"))
                    .thenIdle(2)
                    .thenExecute(() -> {
                        test.assertFalse(TimeStopService.isFrozen(target), reason + ": target thawed");
                        test.assertValueEqual(callbacks, List.of(1, 2), reason + ": deferred effects run once in insertion order");
                        test.assertValueEqual(TimeStopService.pendingResumeActions(target), 0, reason + ": target queue is drained");
                        test.assertValueEqual(removedCallbacks[0], 0, reason + ": removed-target effect is discarded");
                        test.assertValueEqual(TimeStopService.pendingResumeActions(removedTarget[0]), 0, reason + ": removed-target queue is cleaned up");
                        TimeStopService.cancel(current[0]);
                        TimeStopService.queueOnResume(target, () -> callbacks.add(3));
                        test.assertValueEqual(callbacks, List.of(1, 2, 3), reason + ": already-thawed target runs a new effect immediately");
                    })
                    .thenIdle(3)
                    .thenExecute(() -> {
                        test.assertValueEqual(callbacks, List.of(1, 2, 3), reason + ": repeated cancellation and later ticks cannot replay effects");
                        finishPlayer(current[0]);
                    });
        }
        sequence.thenSucceed();
    }

    private static void endStop(GameTestHelper test, ServerPlayer player, EndReason reason) {
        switch (reason) {
            case MANUAL -> {
                TimeStopService.cancel(player);
                test.assertFalse(TimeStopService.isActive(player), "Manual cancellation finishes synchronously");
            }
            case NATURAL -> { /* Let the actual server tick expire the full duration. */ }
            case EXHAUSTION -> {
                player.setGameMode(GameType.SURVIVAL);
                player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(0));
            }
            case DEATH -> player.kill(player.level());
            case STAND_REMOVED -> StandService.current(player).discard();
            case DIMENSION -> {
                var destination = test.getLevel().getServer().getLevel(Level.NETHER);
                test.assertTrue(destination != null, "Nether exists for the dimension-change lifecycle");
                test.assertTrue(player.teleportTo(destination, 0.5, 100, 0.5, Set.of(), 0, 0, true), "Caster changes dimension");
            }
            case DISCONNECT -> player.connection.disconnect(new DisconnectionDetails(Component.literal("Time Stop callback test")));
        }
    }

    private static ServerPlayer caster(GameTestHelper test) {
        return caster(test, false);
    }

    private static ServerPlayer caster(GameTestHelper test, boolean theWorld) {
        var level = test.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "time-stop-combat"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.CREATIVE);
        player.setInvulnerableTime(0);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        player.setNoGravity(true);
        player.setAttached(StandAttachments.PROGRESS,
                StandProgress.grant(theWorld ? StandProgress.THE_WORLD : StandProgress.STAR_PLATINUM, true));
        player.setAttached(theWorld ? StandAttachments.THE_WORLD_RESOLVE : StandAttachments.RESOLVE,
                ResolveProgress.EMPTY.withLevel(4).withTraining(95));
        test.assertTrue(StandService.toggle(player), "Caster summons its Stand");
        return player;
    }

    private static LivingEntity cow(GameTestHelper test, Vec3 position) {
        var cow = test.spawn(EntityTypes.COW, position);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        cow.setHealth(100);
        return cow;
    }

    private static DamageSource standSource(ServerPlayer player) {
        return new DamageSource(player.damageSources().playerAttack(player).typeHolder(), StandService.current(player), player);
    }

    private static void finishPlayer(ServerPlayer player) {
        StandService.clear(player);
        if (player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            player.connection.disconnect(new DisconnectionDetails(Component.literal("Time Stop combat test complete")));
        }
    }

    private static void floor(GameTestHelper test) {
        test.forEveryBlockInStructure(pos -> {
            if (pos.getY() == 0) test.setBlock(pos, Blocks.STONE);
        });
    }

    private static void assertVector(GameTestHelper test, Vec3 actual, Vec3 expected, String message) {
        test.assertTrue(actual.distanceToSqr(expected) < 1.0E-12, message + "; expected=" + expected + ", actual=" + actual);
    }
}
