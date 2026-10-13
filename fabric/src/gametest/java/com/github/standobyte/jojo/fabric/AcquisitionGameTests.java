package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public final class AcquisitionGameTests {
    private static ServerPlayer player(GameTestHelper test, int levels) {
        var level = test.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rotp-arrow-test"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerableTime(0);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        player.setNoGravity(true);
        player.getFoodData().setFoodLevel(17); // Isolate virus damage and its own healing from natural regeneration.
        player.giveExperienceLevels(levels);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StandItems.STAND_ARROW));
        return player;
    }

    private static InteractionResult use(ServerPlayer player) {
        return player.getMainHandItem().getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
    }

    @GameTest(maxTicks = 450) public void survivalPaysThirtyLevelsAwakensAndHeals(GameTestHelper test) {
        var player = player(test, 30);
        test.assertTrue(use(player) == InteractionResult.SUCCESS, "Arrow accepts Survival use");
        test.assertValueEqual(player.getMainHandItem().getDamageValue(), 1, "Piercing costs one of 25 durability");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.ARROW).remainingTicks(), 620, "Original 31-second maximum virus duration");
        test.assertFalse(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Survival acquisition is not instant");
        test.startSequence().thenWaitUntil(() -> test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Wait for paid awakening"))
                .thenExecute(() -> {
                    test.assertValueEqual(player.experienceLevel, 0, "Exactly thirty XP levels paid");
                    test.assertTrue(player.isAlive(), "Paying player survives the virus");
                    test.assertFalse(player.hasEffect(StandItems.STAND_VIRUS), "Virus removed on success");
                    test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.ARROW).acquired(), 1, "Arrow history increments once");
                    test.assertTrue(StandService.toggle(player), "New Stand can be summoned without grant command");
                    StandService.dismiss(player);
                }).thenWaitUntil(() -> test.assertTrue(player.getHealth() == player.getMaxHealth(), "Original recovery healing reaches full health"))
                .thenExecute(() -> test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.ARROW).nextCost(), 35, "Next arrow costs five more levels"))
                .thenSucceed();
    }

    @GameTest public void duplicatePiercingDoesNotSpendAgain(GameTestHelper test) {
        var player = player(test, 30);
        use(player);
        float health = player.getHealth();
        test.assertTrue(use(player) == InteractionResult.FAIL, "Active virus rejects repeated use");
        test.assertValueEqual(player.getMainHandItem().getDamageValue(), 1, "Rejected use preserves durability");
        test.assertValueEqual(player.getHealth(), health, "Rejected use preserves health");
        StandService.grant(player);
        test.assertFalse(player.getAttachedOrCreate(StandAttachments.ARROW).infected(), "Operator replacement cancels pending acquisition");
        test.assertTrue(use(player) == InteractionResult.FAIL, "Existing Stand rejects arrow");
        test.assertValueEqual(player.getMainHandItem().getDamageValue(), 1, "Already-owned rejection preserves durability");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.ARROW).acquired(), 0, "Operator grant does not count as arrow acquisition");
        test.succeed();
    }

    @GameTest(maxTicks = 650) public void survivingFullVirusAwakensWithoutAllXp(GameTestHelper test) {
        var player = player(test, 0);
        player.getAbilities().invulnerable = true; // Isolate the original survival-through-expiration route from lethal damage.
        use(player);
        test.runAtTickTime(600, () -> test.assertFalse(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Unpaid virus cannot finish early"));
        test.startSequence().thenWaitUntil(() -> test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Surviving the full virus grants a Stand"))
                .thenExecute(() -> {
                    test.assertTrue(test.getTick() >= 619, "Full original duration elapsed");
                    test.assertValueEqual(player.experienceLevel, 0, "No nonexistent XP is charged");
                    test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.ARROW).acquired(), 1, "Survival route records exactly one awakening");
                    test.assertFalse(player.hasEffect(StandItems.STAND_VIRUS), "Expired virus is removed");
                }).thenSucceed();
    }

    @GameTest(maxTicks = 70) public void noXpVirusCanKillAndNeverGrantsOnDeath(GameTestHelper test) {
        var player = player(test, 0);
        use(player);
        test.startSequence().thenWaitUntil(() -> test.assertFalse(player.isAlive(), "Unpaid virus kills player"))
                .thenExecute(() -> {
                    test.assertFalse(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Death does not grant a Stand");
                    test.assertFalse(player.getAttachedOrCreate(StandAttachments.ARROW).infected(), "Death clears pending virus");
                    test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.ARROW).acquired(), 0, "Failed attempt does not increase cost");
                }).thenSucceed();
    }

    @GameTest public void creativeAwakensImmediatelyAndPreservesArrow(GameTestHelper test) {
        var player = player(test, 0);
        player.setGameMode(GameType.CREATIVE);
        test.assertTrue(use(player) == InteractionResult.SUCCESS, "Creative use succeeds");
        test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Creative grants immediately");
        test.assertValueEqual(player.getMainHandItem().getDamageValue(), 0, "Creative does not damage arrow");
        test.assertValueEqual(player.experienceLevel, 0, "Creative does not need XP");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.ARROW).acquired(), 1, "Creative acquisition is recorded");
        test.assertFalse(player.hasEffect(StandItems.STAND_VIRUS), "Creative does not receive virus");
        test.succeed();
    }

    @GameTest(maxTicks = 60) public void respawnKeepsHistoryAndDropsInterruptedVirus(GameTestHelper test) {
        var player = player(test, 35);
        player.setAttached(StandAttachments.ARROW, new ArrowProgress(1, 0, 0, 0, false));
        use(player);
        player.kill(test.getLevel());
        test.runAtTickTime(2, () -> {
            var replacement = test.getLevel().getServer().getPlayerList().respawn(player, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            var arrow = replacement.getAttachedOrCreate(StandAttachments.ARROW);
            test.assertValueEqual(arrow.acquired(), 1, "Actual respawn retains prior arrow acquisitions");
            test.assertValueEqual(arrow.nextCost(), 35, "Death cannot reset the escalating XP cost");
            test.assertFalse(arrow.infected() || arrow.healing(), "Interrupted attempt and recovery do not survive death");
            test.assertFalse(replacement.hasEffect(StandItems.STAND_VIRUS), "Respawn has no virus effect");
            test.assertFalse(replacement.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Death/respawn cannot grant a pending Stand");
            test.succeed();
        });
    }

    @GameTest public void historyEscalatesCostAndSurvivesClear(GameTestHelper test) {
        var player = player(test, 40);
        player.setGameMode(GameType.CREATIVE); use(player);
        StandService.clear(player);
        player.setGameMode(GameType.SURVIVAL);
        use(player);
        var state = player.getAttachedOrCreate(StandAttachments.ARROW);
        test.assertValueEqual(state.requiredLevels(), 35, "Second awakening costs 35");
        test.assertValueEqual(state.remainingTicks(), 720, "Second virus maximum is 36 seconds");
        StandService.clear(player);
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.ARROW).acquired(), 1, "Clear keeps prior arrow history");
        test.assertFalse(player.hasEffect(StandItems.STAND_VIRUS), "Clear cancels infection");
        test.succeed();
    }

    @GameTest(maxTicks = 40) public void milkPreservesVirusButCuresOtherEffects(GameTestHelper test) {
        var player = player(test, 30); use(player);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect.INSTANCE.apply(player.level(), new ItemStack(Items.MILK_BUCKET), player);
        test.assertTrue(player.hasEffect(StandItems.STAND_VIRUS), "Milk cannot cure Stand Virus");
        test.assertFalse(player.hasEffect(MobEffects.POISON), "Milk still cures other effects");
        test.runAtTickTime(15, () -> {
            test.assertTrue(player.getAttachedOrCreate(StandAttachments.ARROW).paidLevels() >= 1, "Virus still consumes XP after milk");
            test.assertFalse(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Milk does not skip acquisition");
            StandArrowService.cancel(player); test.succeed();
        });
    }

    @GameTest public void beetleDurabilityAndLastUseWork(GameTestHelper test) {
        var player = player(test, 30);
        var stack = new ItemStack(StandItems.STAND_ARROW_BEETLE);
        test.assertValueEqual(stack.getMaxDamage(), 250, "Original beetle durability");
        stack.setDamageValue(249); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        test.assertTrue(use(player) == InteractionResult.SUCCESS, "Final use still starts acquisition");
        test.assertTrue(player.getMainHandItem().isEmpty(), "Last durability breaks arrow");
        test.assertTrue(player.getAttachedOrCreate(StandAttachments.ARROW).infected(), "Broken arrow does not cancel successful piercing");
        StandArrowService.cancel(player); test.succeed();
    }

    @GameTest(maxTicks = 40) public void externalEffectRemovalCancelsWithoutGrant(GameTestHelper test) {
        var player = player(test, 30); use(player);
        player.removeEffect(StandItems.STAND_VIRUS);
        test.runAtTickTime(4, () -> {
            test.assertFalse(player.getAttachedOrCreate(StandAttachments.ARROW).infected(), "Administrator effect removal cancels attempt");
            test.assertFalse(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Effect removal cannot bypass XP acquisition");
            test.succeed();
        });
    }

    @GameTest public void desertPyramidLootAddsRareArrowWithoutReplacingLoot(GameTestHelper test) {
        var server = test.getLevel().getServer();
        var table = server.reloadableRegistries().getLootTable(BuiltInLootTables.DESERT_PYRAMID);
        var other = server.reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
        var params = new LootParams.Builder(test.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        int arrows = 0, vanilla = 0;
        for (int seed = 1; seed <= 5000; seed++) {
            var loot = table.getRandomItems(params, (long) seed);
            int count = loot.stream().filter(item -> item.is(StandItems.STAND_ARROW_BEETLE)).mapToInt(ItemStack::getCount).sum();
            test.assertTrue(count <= 1, "At most one bonus beetle arrow per chest");
            arrows += count;
            vanilla += loot.stream().filter(item -> !(item.getItem() instanceof StandArrowItem)).mapToInt(ItemStack::getCount).sum();
            if (seed <= 100) test.assertFalse(other.getRandomItems(params, (long) seed).stream().anyMatch(item -> item.getItem() instanceof StandArrowItem), "Unrelated chest table unchanged");
        }
        test.assertTrue(arrows >= 70 && arrows <= 135, "Original 2% loot frequency across fixed seeds: " + arrows + "; vanilla items=" + vanilla + "; table=" + BuiltInLootTables.DESERT_PYRAMID);
        test.assertTrue(vanilla > 5000, "Vanilla chest contents remain alongside bonus loot");
        test.succeed();
    }
}
