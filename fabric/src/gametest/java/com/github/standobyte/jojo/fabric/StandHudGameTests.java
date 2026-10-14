package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Shared identity changes must not leak learning or authoritative HUD state. */
public final class StandHudGameTests {
    @GameTest public void progressionBelongsToEachStand(GameTestHelper test) {
        var player = ProgressionGameTests.player(test);
        ProgressionService.setLevel(player, 3); ProgressionService.setTraining(player, 12.5F);
        StandService.grant(player, StandProgress.THE_WORLD);
        test.assertValueEqual(ProgressionService.state(player).level(), 0, "The World starts with its own Resolve");
        test.assertValueEqual(ProgressionService.state(player).trainingPoints(), 0F, "Training does not leak from Star Platinum");
        ProgressionService.setLevel(player, 2); ProgressionService.trainTimeStop(player, 5);
        test.assertValueEqual(ProgressionService.state(player).trainingPoints(), 0.5F, "The World trains at 0.1 per stopped tick");
        StandService.grant(player, StandProgress.STAR_PLATINUM);
        test.assertValueEqual(ProgressionService.state(player).level(), 3, "Original Stand level restored");
        test.assertValueEqual(ProgressionService.state(player).trainingPoints(), 12.5F, "Original Stand learning restored");
        StandService.grant(player, StandProgress.THE_WORLD);
        test.assertValueEqual(ProgressionService.state(player).trainingPoints(), 0.5F, "The World's own learning restored");
        test.succeed();
    }

    @GameTest(maxTicks = 20) public void hudPublishesServerCooldownsAndClearsWithOwnership(GameTestHelper test) {
        var player = ProgressionGameTests.player(test);
        AbilityCooldowns.start(player, StarPlatinumEntity.PHASE_FINGER, 80);
        test.assertValueEqual(StandHudState.capture(player).finger(), 80, "Snapshot captures authoritative cooldown");
        test.assertValueEqual(StandHudState.capture(player).fingerTotal(), 80, "Original HUD receives full cooldown duration");
        test.runAtTickTime(4, () -> {
            int ticks = player.getAttachedOrCreate(StandAttachments.HUD).finger();
            test.assertTrue(ticks > 70 && ticks < 80, "Owner attachment receives ticking cooldown");
            test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.HUD).fingerTotal(), 80, "Cooldown denominator stays fixed as remaining ticks decrease");
            StandService.clear(player);
        });
        test.runAtTickTime(7, () -> {
            test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.HUD), StandHudState.EMPTY, "Clearing ownership removes stale HUD state");
            test.succeed();
        });
    }
}
