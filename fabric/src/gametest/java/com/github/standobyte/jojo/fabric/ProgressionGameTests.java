package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Real players and vanilla accepted-hit paths, including lethal damage and rejected hits. */
public final class ProgressionGameTests {
    static ServerPlayer player(GameTestHelper test) {
        var level = test.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "r-" + java.util.UUID.randomUUID().toString().substring(0, 8)), false);
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
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        StandService.toggle(player);
        return player;
    }
    private static DamageSource standDamage(ServerPlayer player) {
        return new DamageSource(player.damageSources().playerAttack(player).typeHolder(), StandService.current(player), player);
    }
    private static LivingEntity enemy(GameTestHelper test) {
        var enemy = test.spawn(EntityTypes.HUSK, new net.minecraft.core.BlockPos(4, 1, 4));
        enemy.setNoAi(true); enemy.setNoGravity(true);
        return enemy;
    }

    @GameTest public void newSurvivalGatesAllAdvancedMovesWithoutSpending(GameTestHelper test) {
        var player = player(test); var stand = StandService.current(player);
        float stamina = player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina();
        test.assertFalse(stand.beginUppercut(), "Direct entity entry point rejects locked Uppercut");
        test.assertFalse(StandService.special(player, StarPlatinumEntity.PHASE_INHALE), "Inhale needs level two");
        test.assertFalse(stand.beginSpecial(StarPlatinumEntity.PHASE_FINGER), "Star Finger needs level three");
        test.assertFalse(TimeStopService.toggle(player), "Time Stop needs level four");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), stamina, "Denied moves spend nothing");
        test.assertValueEqual(AbilityCooldowns.remaining(player, StarPlatinumEntity.PHASE_FINGER), 0, "Denied move starts no cooldown");
        test.assertTrue(StandService.attack(player, false), "Basic combat remains usable");
        StandService.dismiss(player); test.succeed();
    }

    @GameTest public void hostileHitsAwardOriginalRawDamageAndPassiveHitsDoNot(GameTestHelper test) {
        var player = player(test); var enemy = enemy(test);
        test.assertTrue(enemy.hurtServer(test.getLevel(), standDamage(player), 4), "Accepted Stand hit");
        test.assertValueEqual(ProgressionService.state(player).resolve(), 4F, "Stand hit gains raw damage once");
        test.assertFalse(enemy.hurtServer(test.getLevel(), standDamage(player), 4), "Vanilla rejects duplicate immunity hit");
        test.assertValueEqual(ProgressionService.state(player).resolve(), 4F, "Rejected hit gives nothing");
        var cow = test.spawn(EntityTypes.COW, new net.minecraft.core.BlockPos(5, 1, 4));
        cow.hurtServer(test.getLevel(), standDamage(player), 4);
        test.assertValueEqual(ProgressionService.state(player).resolve(), 4F, "Passive animals give nothing");
        enemy.damageCooldownTime = 0;
        enemy.hurtServer(test.getLevel(), player.damageSources().playerAttack(player), 4);
        test.assertTrue(Math.abs(ProgressionService.state(player).resolve() - 6.4F) < 0.001F, "User attack gains half damage with previous attack boost");
        StandService.dismiss(player); test.succeed();
    }

    @GameTest public void lethalOverkillCountsButHitsWithoutSummonedStandDoNot(GameTestHelper test) {
        var player = player(test); var enemy = enemy(test);
        enemy.setHealth(1);
        enemy.hurtServer(test.getLevel(), standDamage(player), 30);
        test.assertFalse(enemy.isAlive(), "Hit kills target");
        test.assertValueEqual(ProgressionService.state(player).resolve(), 30F, "Original uncapped lethal raw damage counts");
        StandService.dismiss(player); var other = enemy(test);
        other.hurtServer(test.getLevel(), player.damageSources().playerAttack(player), 4);
        test.assertValueEqual(ProgressionService.state(player).resolve(), 30F, "User damage needs active Stand");
        test.succeed();
    }

    @GameTest public void naturalCombatLevelsUpAndChangesStats(GameTestHelper test) {
        var player = player(test);
        for (int i = 0; i < 24; i++) enemy(test).hurtServer(test.getLevel(), standDamage(player), 25);
        var progress = ProgressionService.state(player);
        test.assertValueEqual(progress.level(), 1, "Combat reaches original 2500 threshold");
        test.assertTrue(progress.isBurst() && player.hasEffect(StandItems.RESOLVE), "Level-up grants real Resolve effect");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).development(), 0.25F, "Original quarter development per level");
        test.assertTrue(StandService.uppercut(player), "New Uppercut is usable");
        test.assertFalse(StandService.special(player, StarPlatinumEntity.PHASE_FINGER), "Higher move stays locked");
        StandService.dismiss(player); test.succeed();
    }

    @GameTest public void resolveResistanceAppliesAfterAbsorptionAndExcludesVoid(GameTestHelper test) {
        var player = player(test);
        ProgressionService.update(player, ResolveProgress.EMPTY.attack(1250, 20));
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(2);
        player.setAbsorptionAmount(2);
        test.assertValueEqual(player.getAbsorptionAmount(), 2F, "Fixture has two absorption points");
        player.hurtServer(test.getLevel(), player.damageSources().magic(), 6);
        test.assertValueEqual(player.getAbsorptionAmount(), 0F, "Absorption spends its full two points first");
        test.assertTrue(Math.abs(player.getHealth() - (20 - 4 * (1 - 0.33335F))) < 0.001F, "Resolve reduces remaining health damage; health=" + player.getHealth() + ", resolve=" + ProgressionService.state(player).resolve());
        player.damageCooldownTime = 0; float health = player.getHealth();
        player.hurtServer(test.getLevel(), player.damageSources().fellOutOfWorld(), 2);
        test.assertTrue(Math.abs(player.getHealth() - (health - 2)) < 0.001F, "Void ignores Resolve");
        StandService.dismiss(player); test.succeed();
    }

    @GameTest public void deathRespawnKeepsLevelsTrainingAndCombatRecords(GameTestHelper test) {
        var player = player(test);
        ProgressionService.setLevel(player, 4); ProgressionService.setTraining(player, 12.5F);
        ProgressionService.update(player, ProgressionService.state(player).attack(100, 20));
        player.hurtServer(test.getLevel(), player.damageSources().genericKill(), Float.MAX_VALUE);
        test.assertFalse(player.isAlive(), "Fixture dies");
        var replacement = player.level().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        var saved = ProgressionService.state(replacement);
        test.assertValueEqual(saved.level(), 4, "Level survives actual respawn");
        test.assertValueEqual(saved.trainingPoints(), 12.5F, "Training survives actual respawn");
        test.assertValueEqual(saved.resolve(), 0F, "Combat meter resets");
        test.assertTrue(saved.records().contains(100F), "Peak retained for future Resolve boost");
        test.assertFalse(replacement.hasEffect(StandItems.RESOLVE), "No stale effect after death");
        test.succeed();
    }

    @GameTest public void creativeBypassesGatesWithoutChangingEarnedProgress(GameTestHelper test) {
        var player = player(test); player.setGameMode(GameType.CREATIVE);
        test.assertTrue(StandService.special(player, StarPlatinumEntity.PHASE_FINGER), "Creative can use locked Star Finger");
        test.assertValueEqual(ProgressionService.state(player).level(), 0, "Bypass does not fabricate earned levels");
        test.assertValueEqual(ProgressionService.timeStopTicks(player), 100, "Creative gets full duration");
        player.setGameMode(GameType.SURVIVAL);
        test.assertFalse(ProgressionService.unlocked(player, 3), "Survival restores earned gate");
        StandService.dismiss(player); test.succeed();
    }

    @GameTest(maxTicks = 20) public void milkEndsBurstWithoutErasingLevelOrTraining(GameTestHelper test) {
        var player = player(test);
        ProgressionService.update(player, ResolveProgress.EMPTY.withLevel(4).withTraining(20).attack(32500, 20));
        net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect.INSTANCE.apply(test.getLevel(),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MILK_BUCKET), player);
        test.runAtTickTime(3, () -> {
            var state = ProgressionService.state(player);
            test.assertFalse(state.isBurst(), "Milk ends beneficial Resolve");
            test.assertValueEqual(state.level(), 4, "Earned level stays");
            test.assertValueEqual(state.trainingPoints(), 20F, "Training stays");
            StandService.dismiss(player); test.succeed();
        });
    }

    @GameTest public void progressionCodecRoundTripsFullCombatState(GameTestHelper test) {
        var state = ResolveProgress.EMPTY.withLevel(4).withTraining(12.5F).attack(500, 8).resetCombat(true).attack(10, 20);
        var encoded = StandAttachments.RESOLVE_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, state).getOrThrow();
        test.assertValueEqual(StandAttachments.RESOLVE_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow(), state,
                "Persistent schema retains records, combat timers and training");
        test.assertValueEqual(StandAttachments.RESOLVE_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow(), ResolveProgress.EMPTY,
                "Missing optional fields initialize safely");
        test.succeed();
    }

    @GameTest public void operatorControlsAreRestrictedAndClearRetainsEarnedProgress(GameTestHelper test) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = player(test);
        var server = player.level().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var command = dispatcher.getRoot().getChild("rotp").getChild("progression");
        test.assertFalse(command.canUse(player.createCommandSourceStack()), "Normal players cannot change progression");
        test.assertTrue(command.canUse(server.createCommandSourceStack()), "Server operator can change progression");
        String target = player.getGameProfile().name();
        dispatcher.execute("rotp progression " + target + " level 3", server.createCommandSourceStack());
        dispatcher.execute("rotp progression " + target + " training 12.5", server.createCommandSourceStack());
        test.assertValueEqual(ProgressionService.state(player).level(), 3, "Level command works through dispatcher");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).development(), 0.75F, "Command updates stats");
        StandService.clear(player); StandService.grant(player);
        test.assertValueEqual(ProgressionService.state(player).level(), 3, "Clear/regrant retains original per-Stand levels");
        test.assertValueEqual(ProgressionService.state(player).trainingPoints(), 12.5F, "Clear/regrant retains learning");
        dispatcher.execute("rotp progression " + target + " reset", server.createCommandSourceStack());
        test.assertValueEqual(ProgressionService.state(player), ResolveProgress.EMPTY, "Explicit reset clears progression");
        test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Reset keeps ownership");
        test.succeed();
    }

    @GameTest(maxTicks = 100, environment = "jojo:progression_time_stop") public void freshTimeStopLastsFiveTicksTrainsAndChargesScaledStamina(GameTestHelper test) {
        var player = player(test); ProgressionService.setLevel(player, 4);
        player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(2250));
        test.runAtTickTime(6, () -> { StandService.toggle(player); test.assertTrue(TimeStopService.toggle(player), "Unlocked untrained Time Stop starts"); });
        test.runAtTickTime(55, () -> {
            test.assertFalse(TimeStopService.isActive(player), "Five-tick stop has finished");
            test.assertValueEqual(ProgressionService.state(player).trainingPoints(), 1.25F, "Five actual ticks award 1.25 points once");
            test.assertValueEqual(ProgressionService.timeStopTicks(player), 6, "Next duration grows to six ticks");
            float stamina = player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina();
            test.assertTrue(stamina >= 1125 && stamina < 1200, "Untrained cost is 225 plus five ticks at 180, then regen");
            test.assertTrue(TimeStopService.cooldownRemaining(player) > 0 && TimeStopService.cooldownRemaining(player) <= 15, "Original three-times-elapsed cooldown");
            StandService.dismiss(player); test.succeed();
        });
    }
}
