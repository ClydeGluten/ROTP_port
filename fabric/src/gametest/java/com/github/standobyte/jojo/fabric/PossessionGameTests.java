package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.pillarman.PossessionService;
import com.github.standobyte.jojo.fabric.pillarman.PossessionState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Real player/host/camera/save lifecycle cases; root registers and runs the consolidated build lane. */
public final class PossessionGameTests {
    private static ServerPlayer player(GameTestHelper test) {
        var player = VampireGameTests.player(test, "pillar-possess", 1.5);
        PillarmanService.grant(player); PillarmanService.setStage(player, 2); return player;
    }
    private static void finishHold(ServerPlayer player) {
        for (int tick = 0; tick < PossessionService.HOLD_TICKS; tick++) { PossessionService.keepHeld(player); PossessionService.tick(player); }
    }
    private static void close(ServerPlayer player) {
        PossessionService.stop(player); PillarmanService.clear(player); player.level().getServer().getPlayerList().remove(player);
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void twentyTickHoldUsesRealHostAndRestoresMode(GameTestHelper test) {
        var player = player(test); player.setGameMode(GameType.ADVENTURE);
        var host = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3));
        try {
            float energy = PillarmanService.state(player).energy();
            test.assertTrue(PossessionService.startHeld(player, host), "Stage2 can start original possession hold");
            test.assertValueEqual(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), 0D, "Source hold prevents walking");
            for (int i = 0; i < 19; i++) { PossessionService.keepHeld(player); PossessionService.tick(player); }
            test.assertFalse(player.isSpectator(), "Hold cannot fire before tick20");
            PossessionService.keepHeld(player); PossessionService.tick(player);
            test.assertTrue(player.isSpectator() && player.getCamera() == host, "Tick20 enters actual host spectator camera");
            test.assertValueEqual(PillarmanService.state(player).energy(), energy, "Source possession has no energy cost");
            test.assertFalse(PillarmanBaseActions.heavyPunch(player), "Spectator possession blocks powers");
            test.assertTrue(PossessionService.cancelTeleport(player), "Source arbitrary teleport is blocked");
            test.assertFalse(PossessionService.startHeld(player, host), "Repeated START cannot overwrite prior mode");
            test.assertFalse(PossessionService.cancelCameraChange(player, player), "Self-camera dismount permits vanilla restoration");
            test.assertValueEqual(player.gameMode(), GameType.ADVENTURE, "Dismount restores exact original mode");
            test.assertTrue(player.getCamera() == player && !PossessionService.isPossessing(player), "Exit clears camera and state");
            PossessionService.stop(player);
            test.assertValueEqual(player.gameMode(), GameType.ADVENTURE, "Repeated cleanup is idempotent");
            test.succeed();
        } finally { host.discard(); close(player); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void targetGatesAndStaleOrReleasedHoldCannotPossess(GameTestHelper test) {
        var player = player(test);
        var host = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3));
        try {
            test.assertFalse(PossessionService.startHeld(player, player), "Self cannot be possessed");
            PillarmanService.setStage(player, 1);
            test.assertFalse(PossessionService.startHeld(player, host), "Stage1 is locked");
            PillarmanService.setStage(player, 2); PillarmanService.setStoneForm(player, true);
            test.assertFalse(PossessionService.startHeld(player, host), "Stone form blocks HideInEntity");
            PillarmanService.setStoneForm(player, false);
            host.setPos(player.position().add(0, 0, 12));
            test.assertFalse(PossessionService.startHeld(player, host), "Forged out-of-source-range target rejected");
            host.setPos(player.position().add(0, 0, 2));
            test.assertTrue(PossessionService.startHeld(player, host), "Nearby target starts");
            for (int i = 0; i < 16; i++) PossessionService.tick(player);
            test.assertFalse(PossessionService.isHolding(player), "Missing heartbeat clears stale hold");
            test.assertFalse(player.isSpectator(), "Stale input never changes mode");
            test.assertTrue(PossessionService.startHeld(player, host), "New valid input can restart");
            PossessionService.stopHeld(player); finishHold(player);
            test.assertFalse(player.isSpectator(), "Release before20 cancels action");
            test.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0, "Canceled hold restores movement");
            test.succeed();
        } finally { host.discard(); close(player); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void savedPossessionResolvesHostAndFailsSafeWithoutHost(GameTestHelper test) {
        var player = player(test); player.setGameMode(GameType.CREATIVE);
        var host = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3));
        try {
            test.assertTrue(PossessionService.startHeld(player, host), "Creative owner starts possession"); finishHold(player);
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess());
            player.saveWithoutId(output); var saved = output.buildResult();
            PossessionService.stop(player);
            player.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), saved));
            PossessionService.onJoin(player);
            test.assertTrue(PossessionService.host(player) == host && player.getCamera() == host, "Real player serialization resolves original host UUID");
            test.assertValueEqual(PossessionService.actualGameMode(player), GameType.CREATIVE, "Previous mode survives attachment load");
            test.assertTrue(PossessionService.cancelGameModeChange(player, GameType.ADVENTURE), "Admin mode change waits for exit per source");
            PossessionService.stop(player);
            test.assertValueEqual(player.gameMode(), GameType.ADVENTURE, "Exit applies requested original admin mode semantics");
            host.discard();
            player.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), saved));
            PossessionService.onJoin(player);
            for (int i = 0; i < PossessionService.RESOLVE_TICKS; i++) PossessionService.tick(player);
            test.assertFalse(PossessionService.isPossessing(player), "Unresolved persisted host expires with bounded retry");
            test.assertValueEqual(player.gameMode(), GameType.CREATIVE, "Invalid resume recovers saved non-spectator mode");
            test.assertTrue(player.getCamera() == player, "Invalid resume restores owner camera");
            test.succeed();
        } finally { host.discard(); close(player); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void deathRemovalAndForcedDimensionExitRestorePlayer(GameTestHelper test) {
        var player = player(test);
        var host = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3));
        try {
            test.assertTrue(PossessionService.startHeld(player, host), "Possession starts"); finishHold(player);
            host.setHealth(0); PossessionService.tick(player);
            test.assertTrue(player.gameMode() == GameType.SURVIVAL && player.getCamera() == player, "Host death restores owner immediately");
            host.setHealth(100); host.setPos(player.position().add(0, 0, 2));
            test.assertTrue(PossessionService.startHeld(player, host), "Alive host can be entered again"); finishHold(player);
            player.setHealth(0); PossessionService.tick(player);
            test.assertFalse(PossessionService.isPossessing(player), "Player death clears persisted possession before clone");
            test.assertValueEqual(player.gameMode(), GameType.SURVIVAL, "Player death restores previous mode");
            player.setHealth(player.getMaxHealth());
            var original = player.level(); var position = player.position();
            // Forced lifecycle transfer clears possession first; arbitrary in-session teleport is blocked by the root hook.
            test.assertTrue(PossessionService.startHeld(player, host), "Restored player can enter"); finishHold(player);
            PossessionService.stop(player);
            var nether = original.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            test.assertTrue(nether != null, "Dimension fixture exists");
            player.teleportTo(nether, 0, 100, 0, java.util.Set.of(), 0, 0, true); PossessionService.tick(player);
            test.assertFalse(player.isSpectator() || PossessionService.isPossessing(player), "Forced transfer leaves no stranded camera/mode");
            player.teleportTo(original, position.x, position.y, position.z, java.util.Set.of(), 0, 0, true);
            test.assertTrue(player.getAttached(PossessionState.TYPE) == null, "Recovery marker is cleared after exit");
            test.succeed();
        } finally { host.discard(); close(player); }
    }
}
