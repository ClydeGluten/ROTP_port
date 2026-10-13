package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.github.standobyte.jojo.core.AttackTimeline;
import com.github.standobyte.jojo.core.StandProgress;
import java.util.Set;

/**
 * Real survival-player checks of the server-owned Stand walk-speed modifier: the player's actual
 * {@code MOVEMENT_SPEED} attribute value is compared against a baseline sampled before the Stand acts.
 */
public final class MovementGameTests {
    private static final Identifier OTHER_TRANSIENT = Identifier.fromNamespaceAndPath("othermod", "transient_slow");
    private static final Identifier OTHER_PERMANENT = Identifier.fromNamespaceAndPath("othermod", "permanent_boost");
    private static final double EPSILON = 1e-6;

    /** Same real-ServerPlayer pattern as {@code AdvancedAbilityGameTests}: the vanilla mock player is always creative. */
    private static ServerPlayer createPlayer(GameTestHelper test) {
        var level = test.getLevel();
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.PVP, true, level.getServer());
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rotp-move-player"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setInvulnerableTime(0);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 10);
        player.setNoGravity(true);
        return player;
    }

    /** Survival player with Star Platinum summoned and another mod's modifiers already on its speed. */
    private static ServerPlayer setup(GameTestHelper test) {
        var player = createPlayer(test);
        StandService.grant(player);
        player.setAttached(StandAttachments.RESOLVE, com.github.standobyte.jojo.core.ResolveProgress.EMPTY.withLevel(4).withTraining(95));
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        test.assertFalse(player.isCreative(), "Movement tests need a real survival player");
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        speed.addTransientModifier(new AttributeModifier(OTHER_TRANSIENT, -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        speed.addPermanentModifier(new AttributeModifier(OTHER_PERMANENT, 0.01, AttributeModifier.Operation.ADD_VALUE));
        StandService.toggle(player);
        return player;
    }

    private static double speed(ServerPlayer player) { return player.getAttributeValue(Attributes.MOVEMENT_SPEED); }
    private static boolean ours(ServerPlayer player) { return player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(StandMovement.MODIFIER_ID); }

    private static void assertSpeed(GameTestHelper test, ServerPlayer player, double expected, String message) {
        test.assertTrue(Math.abs(speed(player) - expected) < EPSILON, message + " (expected " + expected + " but was " + speed(player) + ")");
    }

    private static void assertForeignPreserved(GameTestHelper test, ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        test.assertTrue(speed.hasModifier(OTHER_TRANSIENT), "Another mod's transient modifier survives");
        test.assertTrue(speed.getPermanentModifiers().stream().anyMatch(m -> m.is(OTHER_PERMANENT)), "Another mod's permanent modifier survives");
    }

    private static void assertSingleSlow(GameTestHelper test, ServerPlayer player) {
        test.assertValueEqual(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().filter(m -> m.is(StandMovement.MODIFIER_ID)).count(),
                1L, "Exactly one Stand movement modifier, never stacked");
    }

    @GameTest(maxTicks = 60) public void barrageSlowsRealSpeedSyncsAndRestores(GameTestHelper test) {
        var player = setup(test);
        double flying = player.getAbilities().getFlyingSpeed();
        double baseline = speed(player);
        test.assertFalse(ours(player), "No penalty before the Stand acts");
        test.assertTrue(StandService.startHeld(player, false), "Barrage starts");
        assertSpeed(test, player, baseline * StandMovement.BARRAGE_FACTOR, "Barrage applies the original 0.15 walk factor at once");
        test.assertFalse(player.getAttribute(Attributes.MOVEMENT_SPEED).getPermanentModifiers().stream().anyMatch(m -> m.is(StandMovement.MODIFIER_ID)),
                "The penalty is transient and never persisted");
        // Exactly what vanilla would send to the client: the modifier is in the synced attribute snapshot, so the client must not scale again.
        var packet = new ClientboundUpdateAttributesPacket(player.getId(), player.getAttributes().getSyncableAttributes());
        test.assertTrue(packet.getValues().stream().anyMatch(s -> s.attribute().equals(Attributes.MOVEMENT_SPEED)
                && s.modifiers().stream().anyMatch(m -> m.is(StandMovement.MODIFIER_ID))), "Vanilla attribute sync carries the penalty");
        for (int tick = 1; tick <= 8; tick++) test.runAtTickTime(tick, () -> StandService.refreshHeld(player));
        test.runAtTickTime(8, () -> {
            assertSpeed(test, player, baseline * StandMovement.BARRAGE_FACTOR, "Penalty holds during the rush");
            assertSingleSlow(test, player);
            assertForeignPreserved(test, player);
            test.assertTrue(player.getAbilities().getFlyingSpeed() == flying, "Flight speed is never modified");
            StandService.stopHeld(player);
            assertSpeed(test, player, baseline, "Releasing the barrage restores speed at once, even during its recovery");
            test.assertFalse(ours(player), "Penalty removed on release");
            assertForeignPreserved(test, player);
        });
        test.runAtTickTime(14, () -> {
            assertSpeed(test, player, baseline, "Barrage recovery stays unpenalized (original recovery factor is 1)");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 60) public void guardSlowsRealSpeedAndRestores(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        player.setSprinting(true);
        test.assertTrue(StandService.startHeld(player, true), "Guard starts");
        test.assertFalse(player.isSprinting(), "Guard stops an existing sprint");
        assertSpeed(test, player, baseline * StandMovement.BLOCK_FACTOR, "Guard applies the original 0.3 walk factor");
        for (int tick = 1; tick <= 6; tick++) test.runAtTickTime(tick, () -> StandService.refreshHeld(player));
        test.runAtTickTime(6, () -> {
            assertSpeed(test, player, baseline * StandMovement.BLOCK_FACTOR, "Guard penalty is stable over ticks");
            assertSingleSlow(test, player);
            StandService.stopHeld(player);
            assertSpeed(test, player, baseline, "Lowering the guard restores speed");
            assertForeignPreserved(test, player);
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void expiredLeaseRestoresWithoutExplicitRelease(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        StandService.startHeld(player, true);
        assertSpeed(test, player, baseline * StandMovement.BLOCK_FACTOR, "Guard slowed");
        // No heartbeats: the server-side lease runs out and the end-of-tick reconcile must drop the penalty.
        test.runAtTickTime(30, () -> {
            test.assertValueEqual(StandService.current(player).actionPhase(), 0, "Guard lease expired");
            assertSpeed(test, player, baseline, "Expired hold restores speed without any client message");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 200) public void heavyPenalizesWindupAndRestoresInRecovery(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        var stand = StandService.current(player);
        long[] recoverySeen = {-1};
        test.assertTrue(StandService.attack(player, true), "Heavy starts");
        assertSpeed(test, player, baseline * StandMovement.ACTION_FACTOR, "Heavy windup applies the default 0.5 walk factor");
        test.startSequence().thenWaitUntil(() -> {
            test.assertTrue(stand.actionPhase() == AttackTimeline.Phase.RECOVERY.ordinal(), "Wait for heavy recovery");
            if (recoverySeen[0] < 0) recoverySeen[0] = test.getTick();
        }).thenWaitUntil(() -> assertSpeed(test, player, baseline, "Recovery restores full speed (original recovery factor is 1)")).thenExecute(() -> {
            test.assertTrue(test.getTick() - recoverySeen[0] <= 1, "Penalty leaves within a tick of recovery starting");
            test.assertFalse(ours(player), "Modifier removed in recovery");
            assertForeignPreserved(test, player);
        }).thenWaitUntil(() -> test.assertValueEqual(stand.actionPhase(), 0, "Wait for idle"))
                .thenExecute(() -> { assertSpeed(test, player, baseline, "Idle stays unpenalized"); StandService.dismiss(player); })
                .thenSucceed();
    }

    @GameTest(maxTicks = 80) public void lightPunchDoesNotSlow(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        test.assertTrue(StandService.attack(player, false), "Light starts");
        assertSpeed(test, player, baseline, "Original light attack uses walk factor 1.0");
        test.runAtTickTime(3, () -> {
            test.assertTrue(StandService.current(player).actionPhase() != 0, "Light punch is still in progress");
            assertSpeed(test, player, baseline, "Light attack never slows the owner");
            test.assertFalse(ours(player), "No modifier for light punch");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 60) public void starFingerSlowsThenRestores(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        test.assertTrue(StandService.special(player, StarPlatinumEntity.PHASE_FINGER), "Star Finger starts");
        assertSpeed(test, player, baseline * StandMovement.ACTION_FACTOR, "Finger uses the default 0.5 factor");
        test.runAtTickTime(10, () -> assertSpeed(test, player, baseline * StandMovement.ACTION_FACTOR, "Finger still penalized mid-action"));
        test.runAtTickTime(25, () -> {
            test.assertValueEqual(StandService.current(player).actionPhase(), 0, "Finger finished");
            assertSpeed(test, player, baseline, "Finger completion restores speed");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 60) public void timeStopChargeFreezesWalkingThenRestores(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        test.assertTrue(TimeStopService.toggle(player), "Time stop charge starts");
        test.runAtTickTime(3, () -> {
            test.assertTrue(TimeStopService.isCharging(player), "Still charging");
            assertSpeed(test, player, 0, "Original held walk speed 0 while charging");
            TimeStopService.cancel(player);
        });
        test.runAtTickTime(6, () -> {
            assertSpeed(test, player, baseline, "Cancelled charge restores speed");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void dismissClearsImmediately(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        StandService.startHeld(player, false);
        assertSpeed(test, player, baseline * StandMovement.BARRAGE_FACTOR, "Penalized");
        StandService.dismiss(player);
        assertSpeed(test, player, baseline, "Dismiss restores at once");
        assertForeignPreserved(test, player);
        test.succeed();
    }

    @GameTest(maxTicks = 40) public void clearAndGrantDropPenalty(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        StandService.startHeld(player, true);
        StandService.clear(player);
        assertSpeed(test, player, baseline, "Clearing the Stand restores speed");
        test.assertFalse(StandService.startHeld(player, true), "No Stand, no guard");
        assertSpeed(test, player, baseline, "Rejected action leaves speed alone");
        test.succeed();
    }

    @GameTest(maxTicks = 40) public void deathClearsPenalty(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        StandService.startHeld(player, false);
        assertSpeed(test, player, baseline * StandMovement.BARRAGE_FACTOR, "Penalized");
        player.kill(test.getLevel());
        test.runAtTickTime(4, () -> {
            test.assertFalse(player.isAlive(), "Player died");
            test.assertFalse(ours(player), "Death removes the modifier");
            assertSpeed(test, player, baseline, "Dead player carries no Stand penalty");
            test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void disconnectClearsPenalty(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        StandService.startHeld(player, false);
        assertSpeed(test, player, baseline * StandMovement.BARRAGE_FACTOR, "Penalized");
        player.connection.disconnect(new DisconnectionDetails(Component.literal("movement test")));
        test.runAtTickTime(3, () -> {
            test.assertFalse(ours(player), "Disconnect removes the modifier");
            assertSpeed(test, player, baseline, "Disconnected player carries no Stand penalty");
            test.succeed();
        });
    }

    @GameTest(maxTicks = 60) public void dimensionChangeClearsPenalty(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        var stand = StandService.current(player);
        StandService.startHeld(player, false);
        assertSpeed(test, player, baseline * StandMovement.BARRAGE_FACTOR, "Penalized");
        var nether = test.getLevel().getServer().getLevel(Level.NETHER);
        test.assertTrue(nether != null, "Nether is available");
        var origin = player.position();
        test.assertTrue(player.teleportTo(nether, 0.5, 100, 0.5, Set.of(), 0, 0, true), "Player changes dimension");
        test.runAtTickTime(4, () -> {
            test.assertTrue(player.level() == nether, "Player is in the Nether");
            test.assertTrue(stand.isRemoved(), "Old-dimension Stand is gone");
            test.assertFalse(ours(player), "Dimension change removes the modifier");
            assertSpeed(test, player, baseline, "Speed restored after dimension change");
            assertForeignPreserved(test, player);
            player.teleportTo(test.getLevel(), origin.x, origin.y, origin.z, Set.of(), 0, 0, true);
            test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void manualControlKeepsBodyLockedWithoutPenalty(GameTestHelper test) {
        var player = setup(test);
        double baseline = speed(player);
        var stand = StandService.current(player);
        test.assertTrue(StandService.startControl(player), "Manual control starts");
        for (int tick = 1; tick <= 6; tick++) test.runAtTickTime(tick, () -> stand.control(0, 1, 0, 0, 0));
        Vec3 anchor = player.position();
        test.runAtTickTime(6, () -> {
            assertSpeed(test, player, baseline, "Locked body gets no extra speed penalty");
            test.assertTrue(player.position().distanceTo(anchor) < 0.01, "Existing body lock still holds the player");
            test.assertTrue(player.getDeltaMovement().lengthSqr() < 1e-6, "Body lock still zeroes velocity");
            StandService.dismiss(player); test.succeed();
        });
    }
}
