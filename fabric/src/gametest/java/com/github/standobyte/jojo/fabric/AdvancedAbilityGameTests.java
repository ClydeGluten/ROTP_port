package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class AdvancedAbilityGameTests {
    /** Vanilla's deprecated mock always reports creative mode; use a real server player for stamina tests. */
    private static net.minecraft.server.level.ServerPlayer createPlayer(GameTestHelper test) {
        var level = test.getLevel();
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.PVP, true, level.getServer());
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rotp-test-player"), false);
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setInvulnerableTime(0);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        player.setNoGravity(true);
        return player;
    }


    private static net.minecraft.server.level.ServerPlayer setup(GameTestHelper test) {
        var player = createPlayer(test);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 10);
        StandService.grant(player);
        player.setAttached(StandAttachments.RESOLVE, com.github.standobyte.jojo.core.ResolveProgress.EMPTY.withLevel(4).withTraining(95));
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        StandService.toggle(player);
        return player;
    }

    @GameTest(maxTicks = 160) public void heavyBelowHalfChargeRemainsNormal(GameTestHelper test) {
        checkAutomaticFinisher(test, 4, false);
    }

    @GameTest(maxTicks = 180) public void heavyAtHalfChargeSelectsUppercut(GameTestHelper test) {
        checkAutomaticFinisher(test, 5, true);
    }

    @GameTest(maxTicks = 280) public void chargedHeavyPreservesUnusedFinisher(GameTestHelper test) {
        checkAutomaticFinisher(test, 10, true);
    }

    private static void checkAutomaticFinisher(GameTestHelper test, int punches, boolean uppercut) {
        var player = setup(test);
        var stand = StandService.current(player);
        var sequence = test.startSequence();
        for (int i = 0; i < punches; i++) {
            sequence.thenExecute(() -> test.assertTrue(StandService.attack(player, false), "Light punch starts"))
                    .thenWaitUntil(() -> test.assertValueEqual(stand.actionPhase(), 3, "Light punch reaches cancellable recovery"));
        }
        var target = new net.minecraft.world.entity.LivingEntity[1];
        float expectedCharge = Math.max(0, punches * 0.1F - 0.51F);
        sequence.thenExecute(() -> {
            test.assertTrue(Math.abs(stand.finisherCharge() - punches * 0.1F) < 0.001F, "Missed punches build original charge");
            target[0] = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(1.5, 1, 3));
            target[0].setNoGravity(true);
            test.assertTrue(StandService.attack(player, true), "Heavy cancels light recovery");
            test.assertValueEqual(stand.abilityKind(), uppercut ? 1 : 0, "Half-charge threshold selects original uppercut variation");
            test.assertTrue(Math.abs(stand.finisherCharge() - expectedCharge) < 0.001F, "Heavy consumes 0.51, preserving unused charge");
        }).thenWaitUntil(() -> test.assertValueEqual(stand.actionPhase(), 3, "Heavy reaches recovery"))
                .thenExecute(() -> {
                    test.assertTrue(target[0].getHealth() < target[0].getMaxHealth(), "Selected heavy damages target");
                    test.assertTrue(Math.abs(stand.finisherCharge() - expectedCharge) < 0.001F, "Strike does not erase remaining charge");
                    if (uppercut) test.assertTrue(target[0].getDeltaMovement().y > 0.5, "Automatic uppercut launches target");
                    StandService.dismiss(player);
                }).thenSucceed();
    }

    @GameTest(maxTicks = 40) public void starFingerExtendsBeyondPunchAndHitsOnce(GameTestHelper test) {
        var player = setup(test);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 4.5));
        target.setNoGravity(true);
        test.assertTrue(StandService.special(player, StarPlatinumEntity.PHASE_FINGER), "Star Finger starts");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 1625F, "Original 375 stamina cost");
        test.assertFalse(StandService.special(player, StarPlatinumEntity.PHASE_FINGER), "Spam cannot replace the active finger");
        test.assertFalse(StandService.attack(player, true), "Finger excludes simultaneous melee");
        test.runAtTickTime(5, () -> test.assertValueEqual(target.getHealth(), target.getMaxHealth(), "Finger cannot reach its maximum distance instantly"));
        test.runAtTickTime(22, () -> {
            test.assertValueEqual(target.getHealth(), target.getMaxHealth() - 4.5F, "Extended finger hits once for upstream damage");
            test.assertFalse(StandService.special(player, StarPlatinumEntity.PHASE_FINGER), "Finger cooldown survives completed animation");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void starFingerHonorsWall(GameTestHelper test) {
        var player = setup(test);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 4.5)); target.setNoGravity(true);
        test.setBlock(new BlockPos(1, 2, 3), Blocks.STONE);
        StandService.special(player, StarPlatinumEntity.PHASE_FINGER);
        test.runAtTickTime(22, () -> {
            test.assertValueEqual(target.getHealth(), target.getMaxHealth(), "Finger cannot damage through stone");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest public void specialRequiresPowerAndStamina(GameTestHelper test) {
        var player = setup(test);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, 374, 0, true));
        test.assertFalse(StandService.special(player, StarPlatinumEntity.PHASE_FINGER), "Insufficient finger stamina rejects intent");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 374F, "Rejected special preserves stamina");
        StandService.clear(player);
        test.assertFalse(StandService.special(player, StarPlatinumEntity.PHASE_INHALE), "No Stand cannot inhale");
        test.assertFalse(StandService.startControl(player), "No Stand cannot enter manual control"); test.succeed();
    }

    @GameTest(maxTicks = 40) public void fingerCooldownSurvivesResummon(GameTestHelper test) {
        var player = setup(test);
        StandService.special(player, StarPlatinumEntity.PHASE_FINGER);
        StandService.dismiss(player);
        test.runAtTickTime(6, () -> {
            test.assertTrue(StandService.toggle(player), "Re-summon after normal toggle debounce");
            test.assertFalse(StandService.special(player, StarPlatinumEntity.PHASE_FINGER), "Re-summon cannot bypass player-owned cooldown");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void uppercutLaunchesTarget(GameTestHelper test) {
        var player = setup(test);
        var target = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(1.5, 1, 3)); target.setNoGravity(true);
        test.assertTrue(StandService.uppercut(player), "Uppercut starts");
        test.assertValueEqual(StandService.current(player).abilityKind(), 1, "Uppercut pose is synchronized separately from heavy");
        test.runAtTickTime(16, () -> {
            test.assertTrue(target.getHealth() < target.getMaxHealth(), "Uppercut damages a reachable target");
            test.assertTrue(target.getY() > test.absoluteVec(new Vec3(0, 1, 0)).y + 0.2, "Uppercut launches target upwards");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void inhalePullsFrontAndExpiresWithoutHeartbeat(GameTestHelper test) {
        var player = setup(test);
        var front = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 5.5)); front.setNoGravity(true);
        var rear = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 0)); rear.setNoGravity(true);
        double frontZ = front.getZ(), rearZ = rear.getZ();
        test.assertTrue(StandService.special(player, StarPlatinumEntity.PHASE_INHALE), "Inhale starts");
        test.runAtTickTime(7, () -> {
            test.assertTrue(front.getZ() < frontZ, "Inhale pulls entities in front");
            test.assertTrue(Math.abs(rear.getZ() - rearZ) < 0.01, "Inhale leaves rear entities alone");
            test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() <= 1990, "Inhale drains stamina without action regeneration");
        });
        test.runAtTickTime(15, () -> {
            test.assertValueEqual(StandService.current(player).actionPhase(), 0, "Missing input lease stops inhale");
            test.assertTrue(AbilityCooldowns.remaining(player, StarPlatinumEntity.PHASE_INHALE) > 0, "Inhale applies duration-based cooldown");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void inhaleDoesNotPullThroughWall(GameTestHelper test) {
        var player = setup(test);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 5.5)); target.setNoGravity(true);
        double z = target.getZ();
        test.setBlock(new BlockPos(1, 1, 3), Blocks.STONE); test.setBlock(new BlockPos(1, 2, 3), Blocks.STONE);
        StandService.special(player, StarPlatinumEntity.PHASE_INHALE);
        test.runAtTickTime(7, () -> {
            test.assertTrue(Math.abs(target.getZ() - z) < 0.01, "Inhale respects solid walls");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest(maxTicks = 240, padding = 16) public void manualControlClampsDistanceAndExpires(GameTestHelper test) {
        var player = setup(test); var stand = StandService.current(player); Vec3 body = player.position();
        // This test crosses a chunk border depending on the randomized test origin. Keep both chunks
        // ticking explicitly; the gameplay intentionally refuses to load missing chunks itself.
        var forced = new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int z = ((int) Math.floor(body.z)) >> 4; z <= ((int) Math.floor(body.z + 10)) >> 4; z++) {
            int x = ((int) Math.floor(body.x)) >> 4;
            if (test.getLevel().setChunkForced(x, z, true)) forced.add(new net.minecraft.world.level.ChunkPos(x, z));
        }
        test.assertTrue(StandService.startControl(player), "Manual control starts");
        boolean[] release = {false}; int startClock = stand.tickCount; int[] releaseClock = {0};
        test.onEachTick(() -> { if (!release[0]) stand.control(0, 1, 0, 0, 0); });
        test.startSequence().thenWaitUntil(() -> test.assertTrue(stand.tickCount - startClock >= 55, "Wait for actual entity ticks after chunk loading"))
                .thenExecute(() -> {
                    test.assertTrue(stand.position().distanceTo(body) > 9.9, "Control reaches the maximum range");
                    test.assertTrue(stand.position().distanceTo(body) <= 10.01, "Maximum range is enforced by server");
                    test.assertTrue(player.position().distanceTo(body) < 0.01, "Owner body stays anchored");
                    release[0] = true; releaseClock[0] = stand.tickCount;
                }).thenWaitUntil(() -> test.assertTrue(stand.tickCount - releaseClock[0] >= 16, "Wait for stale input expiry"))
                .thenExecute(() -> {
                    test.assertFalse(stand.manuallyControlled(), "Stale manual input expires");
                    test.assertTrue(stand.position().distanceTo(body) < 2, "Manifestation returns to its owner");
                    StandService.dismiss(player);
                    for (var chunk : forced) test.getLevel().setChunkForced(chunk.x(), chunk.z(), false);
                }).thenSucceed();
    }

    @GameTest(maxTicks = 40) public void malformedControlCannotRenewLease(GameTestHelper test) {
        var player = setup(test); var stand = StandService.current(player);
        StandService.startControl(player);
        for (int tick = 1; tick <= 14; tick++) test.runAtTickTime(tick, () -> {
            stand.control(Float.NaN, 1, 0, 0, 0); stand.control(0, 1000, 0, 0, 0);
        });
        test.runAtTickTime(16, () -> {
            test.assertFalse(stand.manuallyControlled(), "Invalid input cannot keep control alive");
            test.assertTrue(Double.isFinite(stand.getX()), "Malformed input cannot corrupt entity position");
            StandService.dismiss(player); test.succeed();
        });
    }
    @GameTest(maxTicks = 45) public void manualPunchUsesManifestationOrigin(GameTestHelper test) {
        var player = setup(test); var stand = StandService.current(player);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 6.5)); target.setNoGravity(true);
        StandService.startControl(player);
        for (int tick = 1; tick <= 10; tick++) test.runAtTickTime(tick, () -> stand.control(0, 1, 0, 0, 25));
        test.runAtTickTime(11, () -> {
            stand.control(0, 0, 0, 0, 25);
            test.assertTrue(StandService.attack(player, false), "Manual Stand accepts melee intent");
        });
        test.runAtTickTime(16, () -> {
            test.assertValueEqual(target.getHealth(), target.getMaxHealth() - 4, "Manual punch originates from Stand, beyond owner melee reach");
            StandService.dismiss(player); test.succeed();
        });
    }

}
