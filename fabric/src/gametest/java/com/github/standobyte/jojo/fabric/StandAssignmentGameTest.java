package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class StandAssignmentGameTest {
    private static ServerPlayer player(GameTestHelper test, int levels) {
        var level = test.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "rotp-assign-test"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerableTime(0);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        player.setNoGravity(true);
        player.getFoodData().setFoodLevel(17);
        player.giveExperienceLevels(levels);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StandItems.STAND_ARROW));
        return player;
    }

    private static InteractionResult use(ServerPlayer player) {
        return player.getMainHandItem().getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
    }

    @GameTest(environment = "jojo:assignment_1")
    public void successfulArrowSequenceAllocatesBothAndAdvancesGeneration(GameTestHelper test) {
        var server = test.getLevel().getServer();
        StandAssignments.setCycle(server, StandAssignments.WorldCycle.INITIAL);

        // Player 1 prefers Star Platinum (provisional 1)
        var player1 = player(test, 0);
        player1.setGameMode(GameType.CREATIVE);
        StandAssignments.setProvisional(player1, 1);
        test.assertTrue(use(player1) == InteractionResult.SUCCESS, "Creative arrow use succeeds");
        test.assertValueEqual(player1.getAttachedOrCreate(StandAttachments.PROGRESS).standId(), StandProgress.STAR_PLATINUM, "Player 1 receives Star Platinum");

        var cycle1 = StandAssignments.getCycle(server);
        test.assertValueEqual(cycle1.generation(), 1, "Still generation 1");
        test.assertValueEqual(cycle1.used(), List.of(StandProgress.STAR_PLATINUM), "Star Platinum is reserved in cycle");

        // Player 2 also prefers Star Platinum (provisional 1), but SP is reserved, so falls back to The World
        var player2 = player(test, 0);
        player2.setGameMode(GameType.CREATIVE);
        StandAssignments.setProvisional(player2, 1);
        test.assertTrue(use(player2) == InteractionResult.SUCCESS, "Creative arrow use succeeds for Player 2");
        test.assertValueEqual(player2.getAttachedOrCreate(StandAttachments.PROGRESS).standId(), StandProgress.THE_WORLD, "Player 2 receives The World without duplicate");

        var cycle2 = StandAssignments.getCycle(server);
        test.assertValueEqual(cycle2.generation(), 1, "Still generation 1 after second unique Stand");
        test.assertValueEqual(cycle2.used(), List.of(StandProgress.STAR_PLATINUM, StandProgress.THE_WORLD), "Both Stands reserved in generation 1");

        // Player 3 prefers Star Platinum (provisional 1). Generation 1 is full, so generation 2 begins!
        var player3 = player(test, 0);
        player3.setGameMode(GameType.CREATIVE);
        StandAssignments.setProvisional(player3, 1);
        test.assertTrue(use(player3) == InteractionResult.SUCCESS, "Creative arrow use succeeds for Player 3");
        test.assertValueEqual(player3.getAttachedOrCreate(StandAttachments.PROGRESS).standId(), StandProgress.STAR_PLATINUM, "Player 3 receives Star Platinum in generation 2");

        var cycle3 = StandAssignments.getCycle(server);
        test.assertValueEqual(cycle3.generation(), 2, "Generation advances to 2");
        test.assertValueEqual(cycle3.used(), List.of(StandProgress.STAR_PLATINUM), "Generation 2 has Star Platinum reserved");
        test.succeed();
    }

    @GameTest(maxTicks = 450, environment = "jojo:assignment_2")
    public void grantTheWorldAvailableSurvivalArrow(GameTestHelper test) {
        var server = test.getLevel().getServer();
        // Pre-reserve Star Platinum so the remaining available Stand in generation 1 is The World
        StandAssignments.setCycle(server, new StandAssignments.WorldCycle(1, List.of(StandProgress.STAR_PLATINUM)));

        var player = player(test, 30);
        test.assertTrue(use(player) == InteractionResult.SUCCESS, "Survival arrow use starts");
        test.startSequence().thenWaitUntil(() -> test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld(), "Survival arrow awakens The World"))
                .thenExecute(() -> {
                    test.assertTrue(player.isAlive(), "Player survived virus to awaken The World");
                    var cycle = StandAssignments.getCycle(server);
                    test.assertValueEqual(cycle.used(), List.of(StandProgress.STAR_PLATINUM, StandProgress.THE_WORLD), "Both Stands reserved in generation 1");
                }).thenSucceed();
    }

    @GameTest(maxTicks = 70, environment = "jojo:assignment_3")
    public void deadInfectionConsumesNothing(GameTestHelper test) {
        var server = test.getLevel().getServer();
        StandAssignments.setCycle(server, StandAssignments.WorldCycle.INITIAL);

        var player = player(test, 0);
        use(player);
        test.startSequence().thenWaitUntil(() -> test.assertFalse(player.isAlive(), "Unpaid virus kills player"))
                .thenExecute(() -> {
                    test.assertFalse(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand(), "Dead player receives no Stand");
                    var cycle = StandAssignments.getCycle(server);
                    test.assertValueEqual(cycle.generation(), 1, "Generation unchanged");
                    test.assertTrue(cycle.used().isEmpty(), "Dead infection consumed no reservation from cycle");
                }).thenSucceed();
    }

    @GameTest(environment = "jojo:assignment_4")
    public void noAllocationPriorCompletion(GameTestHelper test) {
        var server = test.getLevel().getServer();
        StandAssignments.setCycle(server, StandAssignments.WorldCycle.INITIAL);

        var player = player(test, 30);
        use(player);
        test.assertTrue(player.getAttachedOrCreate(StandAttachments.ARROW).infected(), "Virus active");
        test.assertFalse(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand(), "No Stand prior to completion");
        test.assertTrue(StandAssignments.getCycle(server).used().isEmpty(), "No cycle reservation prior to completion");

        StandArrowService.cancel(player);
        test.assertFalse(player.getAttachedOrCreate(StandAttachments.ARROW).infected(), "Infection cancelled");
        test.assertTrue(StandAssignments.getCycle(server).used().isEmpty(), "Cancelled infection consumes nothing");
        test.succeed();
    }

    @GameTest(environment = "jojo:assignment_5")
    public void persistenceCodecsAndWorldGlobalAcrossDimensions(GameTestHelper test) {
        var server = test.getLevel().getServer();
        var uuid = UUID.randomUUID();

        // 1. WorldCycle codec round-trip
        var originalCycle = new StandAssignments.WorldCycle(3, List.of(StandProgress.STAR_PLATINUM, StandProgress.THE_WORLD));
        var tag = StandAssignments.CYCLE_CODEC.encodeStart(NbtOps.INSTANCE, originalCycle).getOrThrow();
        var decodedCycle = StandAssignments.CYCLE_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        test.assertValueEqual(decodedCycle.generation(), 3, "Generation round-trip");
        test.assertValueEqual(decodedCycle.used(), originalCycle.used(), "Used stands round-trip");


        // 2. Provisional integer codec round-trip
        var intTag = Codec.INT.encodeStart(NbtOps.INSTANCE, 2).getOrThrow();
        var decodedInt = Codec.INT.parse(NbtOps.INSTANCE, intTag).getOrThrow();
        test.assertValueEqual(decodedInt, 2, "Provisional integer round-trip");

        // 3. Global Overworld state across dimensions
        StandAssignments.setCycle(server, originalCycle);
        test.assertValueEqual(server.overworld().getAttachedOrCreate(StandAssignments.CYCLE), originalCycle, "Cycle stored on Overworld");
        test.assertValueEqual(StandAssignments.getCycle(server), originalCycle, "StandAssignments reads from Overworld globally");
        test.succeed();
    }

    @GameTest(environment = "jojo:assignment_6")
    public void reloginAndDeathDoNotDuplicateReservation(GameTestHelper test) {
        var server = test.getLevel().getServer();
        StandAssignments.setCycle(server, StandAssignments.WorldCycle.INITIAL);

        var player = player(test, 0);
        // Existing owner entering from an old save with Star Platinum
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grant(StandProgress.STAR_PLATINUM, false));

        player.removeAttached(StandAssignments.ACCOUNTED);
        // Initial join seeds Star Platinum into generation 1
        StandAssignments.onPlayerJoin(player);
        var cycle1 = StandAssignments.getCycle(server);
        test.assertValueEqual(cycle1.used(), List.of(StandProgress.STAR_PLATINUM), "Initial join seeds Star Platinum");
        test.assertTrue(player.getAttachedOrCreate(StandAssignments.ACCOUNTED), "Player UUID recorded in seeded owners");

        // Relogin simulation (onPlayerJoin called again for same UUID)
        StandAssignments.onPlayerJoin(player);
        var cycle2 = StandAssignments.getCycle(server);
        test.assertValueEqual(cycle2.used(), List.of(StandProgress.STAR_PLATINUM), "Relogin does not duplicate reservation");
        test.assertValueEqual(cycle2.generation(), 1, "Generation unchanged on relogin");

        // Death / respawn simulation (seedOwner called again for same UUID)
        StandAssignments.seedOwner(player);
        var cycle3 = StandAssignments.getCycle(server);
        test.assertValueEqual(cycle3.used(), List.of(StandProgress.STAR_PLATINUM), "Respawn does not duplicate reservation");
        test.assertValueEqual(cycle3.generation(), 1, "Generation unchanged on respawn");
        test.succeed();
    }
}
