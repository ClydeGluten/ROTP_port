package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.mixin.TimeSkipMobAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Regression coverage for original instant Time Stop placement, accounting and independent cooldowns. */
public final class TimeSkipGameTests {
    private static ServerPlayer player(GameTestHelper test, String stand, int resolve, float stamina, float training) {
        var level = test.getLevel();
        var uuid = java.util.UUID.randomUUID();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(uuid, "skip-" + uuid.toString().substring(0, 8)), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        player.setNoGravity(true);
        StandService.grant(player, stand);
        player.setAttached(StandAttachments.resolveType(player), ResolveProgress.EMPTY.withLevel(resolve).withTraining(training));
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(stand, stamina, resolve / 4F, true));
        for (int x = 0; x < 5; x++) for (int z = 0; z < 8; z++) test.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        return player;
    }

    @GameTest public void bothStandUnlocksAndUnsummonedUse(GameTestHelper test) {
        var player = player(test, StandProgress.STAR_PLATINUM, 3, 2000, 15);
        test.assertFalse(TheWorldActions.blink(player), "Star Platinum instant Time Stop requires Resolve 4");
        player.setAttached(StandAttachments.RESOLVE, ResolveProgress.EMPTY.withLevel(4).withTraining(15));
        test.assertTrue(TheWorldActions.blink(player), "Star Platinum instant Time Stop works without summoning");
        test.assertTrue(StandService.current(player) == null, "Instant Time Stop does not summon a Stand");
        test.succeed();
    }

    @GameTest public void starPlatinumStopsInFrontOfEntity(GameTestHelper test) {
        var player = player(test, StandProgress.STAR_PLATINUM, 4, 2000, 15);
        var target = test.spawn(EntityTypes.HUSK, new BlockPos(1, 1, 4));
        target.setNoAi(true); target.setNoGravity(true); target.setYRot(180);
        var expected = target.position().subtract(new Vec3(0, 0, target.getBbWidth() + player.getBbWidth()));
        test.assertTrue(TheWorldActions.blink(player), "Star Platinum blinks toward a living target");
        test.assertTrue(player.position().distanceTo(expected) < 0.01, "Star Platinum stops on the approaching side");
        test.assertTrue(player.getLookAngle().dot(target.position().subtract(player.position()).normalize()) > 0.99,
                "Instant Time Stop turns toward the target");
        test.succeed();
    }

    @GameTest public void theWorldAppearsBehindTargetFacing(GameTestHelper test) {
        var player = player(test, StandProgress.THE_WORLD, 2, 2000, 15);
        var target = test.spawn(EntityTypes.HUSK, new BlockPos(1, 1, 4));
        target.setNoAi(true); target.setNoGravity(true); target.setYRot(180);
        var expected = target.position().subtract(Vec3.directionFromRotation(0, 180)
                .scale(target.getBbWidth() + player.getBbWidth()));
        test.assertTrue(TheWorldActions.blink(player), "The World blinks toward a living target");
        test.assertTrue(player.position().distanceTo(expected) < 0.01, "The World lands behind target facing, across the target");
        test.assertTrue(player.getLookAngle().dot(target.position().subtract(player.position()).normalize()) > 0.99,
                "The World turns back toward target after crossing it");
        test.succeed();
    }

    @GameTest public void blockTopAndCeilingCollision(GameTestHelper test) {
        var player = player(test, StandProgress.THE_WORLD, 2, 2000, 15);
        test.setBlock(new BlockPos(1, 2, 4), Blocks.OBSIDIAN);
        test.assertTrue(TheWorldActions.blink(player), "Aimed block permits safe travel up and across");
        test.assertTrue(player.position().distanceTo(test.absoluteVec(new Vec3(1.5, 3, 4.5))) < 0.01,
                "An empty block above the hit becomes the landing position");
        test.assertTrue(player.level().noCollision(player, player.getBoundingBox()), "Block-top landing fits full player body");
        AbilityCooldowns.start(player, TheWorldActions.COOLDOWN_BLINK, 0);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        // The aimed block is covered; original placement falls back to its hit face.
        test.setBlock(new BlockPos(1, 3, 4), Blocks.OBSIDIAN);
        test.assertTrue(TheWorldActions.blink(player), "Covered block falls back to hit-face placement");
        test.assertTrue(player.getZ() < test.absoluteVec(new Vec3(1.5, 1, 4)).z,
                "Full-body sweep never tunnels through the covered block");
        test.assertTrue(player.level().noCollision(player, player.getBoundingBox()), "Fallback does not enter wall or ceiling");
        test.succeed();
    }

    @GameTest public void missLandsOnGroundAndDoesNotTrain(GameTestHelper test) {
        var player = player(test, StandProgress.THE_WORLD, 2, 2000, 15);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 4, 1.5)), 0, 0);
        int age = player.tickCount;
        test.assertTrue(TheWorldActions.blink(player), "A missed ray still blinks");
        test.assertTrue(Math.abs(player.getY() - test.absoluteVec(new Vec3(0, 1, 0)).y) < 0.01,
                "A missed ray scans downward to ground");
        test.assertValueEqual(player.tickCount, age + 20, "Travel consumes at most the learned implied tick budget");
        test.assertTrue(Math.abs(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() - 1100) < 0.01,
                "Twenty implied ticks cost 180 + 20 * (720 / 20)");
        test.assertValueEqual(ProgressionService.state(player).trainingPoints(), 15F, "Instant travel does not train Time Stop");
        test.succeed();
    }

    @GameTest public void exhaustedStaminaKeepsOriginalFiveTickMinimum(GameTestHelper test) {
        var player = player(test, StandProgress.THE_WORLD, 2, 0, 15);
        int age = player.tickCount;
        var before = player.position();
        test.assertTrue(TheWorldActions.blink(player), "Original instant Time Stop remains usable when stamina is exhausted");
        test.assertValueEqual(player.tickCount, age + 5, "Exhausted stamina caps instant travel at minimum five ticks");
        test.assertTrue(player.position().distanceTo(before) > 1 && player.position().distanceTo(before) < 1.1,
                "Minimum instant travel uses movement speed times five ticks");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 0F, "Stamina never becomes negative");
        test.assertValueEqual(TheWorldActions.blinkCooldown(player), 2, "Cooldown truncates 15 / 6 to two ticks");
        test.succeed();
    }

    @GameTest public void fullTimeStopCooldownDoesNotBlockInstantVariation(GameTestHelper test) {
        var player = player(test, StandProgress.THE_WORLD, 2, 2000, 15);
        TimeStopService.setCooldown(player, 100);
        test.assertTrue(TheWorldActions.blink(player), "Expired instant cooldown permits travel during full Time Stop cooldown");
        test.assertValueEqual(TimeStopService.cooldownRemaining(player), 100, "Blink preserves an existing full Time Stop cooldown");
        test.assertValueEqual(TheWorldActions.blinkCooldown(player), 10, "Instant variation has its own one-sixth cooldown");
        test.assertFalse(TheWorldActions.blink(player), "The instant action's own cooldown still blocks repetition");
        test.succeed();
    }

    @GameTest public void lostAggroClearsRunningTargetGoals(GameTestHelper test) {
        var player = player(test, StandProgress.THE_WORLD, 2, 2000, 15);
        var behind = test.spawn(EntityTypes.HUSK, new BlockPos(3, 1, 1));
        var watching = test.spawn(EntityTypes.HUSK, new BlockPos(3, 1, 2));
        behind.setNoAi(true); behind.setNoGravity(true); behind.setYRot(180); behind.setTarget(player);
        watching.setNoAi(true); watching.setNoGravity(true); watching.setYRot(0); watching.setTarget(player);
        var stopped = new java.util.concurrent.atomic.AtomicBoolean();
        var goals = ((TimeSkipMobAccessor) behind).jojo$targetSelector();
        Goal marker = new Goal() {
            @Override public boolean canUse() { return true; }
            @Override public void stop() { stopped.set(true); }
        };
        goals.addGoal(0, marker);
        goals.getAvailableGoals().stream().filter(goal -> goal.getGoal() == marker).forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::start);
        test.assertTrue(TheWorldActions.blink(player), "Blink executes with nearby attackers");
        test.assertTrue(behind.getTarget() == null && stopped.get(), "Attacker looking away from landing loses target and running target goal");
        test.assertTrue(watching.getTarget() == player, "Attacker facing landing retains target");
        test.succeed();
    }

    @GameTest public void tickSkipAdvancesOnlyEntityClocks(GameTestHelper test) {
        var player = player(test, StandProgress.THE_WORLD, 2, 2000, 15);
        test.assertTrue(StandService.toggle(player), "Fixture summons The World");
        var stand = StandService.current(player);
        int playerAge = player.tickCount, standAge = stand.tickCount;
        long worldTime = player.level().getGameTime();
        player.setRemainingFireTicks(80);
        AbilityCooldowns.start(player, TheWorldActions.COOLDOWN_BLINK, 20);
        var position = player.position();
        TheWorldActions.skipTicks(player, stand, 17);
        test.assertValueEqual(player.tickCount, playerAge + 17, "Player clock advances implied ticks");
        test.assertValueEqual(stand.tickCount, standAge + 17, "Stand clock advances implied ticks");
        test.assertValueEqual(player.getRemainingFireTicks(), 80, "Skip does not simulate entity ticking/fire");
        test.assertValueEqual(player.level().getGameTime(), worldTime, "Skip does not advance world time");
        test.assertValueEqual(TheWorldActions.blinkCooldown(player), 20, "Skip does not advance server cooldown time");
        test.assertValueEqual(player.position(), position, "Clock skipping does not move player");
        StandService.dismiss(player);
        test.succeed();
    }
}
