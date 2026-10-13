package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class StandGameTests {
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

    @GameTest public void codecRoundTrip(GameTestHelper test) {
        StandProgress expected = new StandProgress(StandProgress.STAR_PLATINUM, 734.5F, 0.5F, true);
        var json = StandAttachments.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        test.assertValueEqual(StandAttachments.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), expected, "Stand state round trip");
        var unknown = new StandProgress("jojo:future_stand", 50, 0, true);
        var unknownJson = StandAttachments.CODEC.encodeStart(JsonOps.INSTANCE, unknown).getOrThrow();
        test.assertValueEqual(StandAttachments.CODEC.parse(JsonOps.INSTANCE, unknownJson).getOrThrow(), unknown, "Unimplemented IDs must survive saves");
        test.succeed();
    }

    @GameTest public void noPowerCannotSummon(GameTestHelper test) {
        var player = createPlayer(test);
        test.assertFalse(StandService.toggle(player), "A player without a Stand cannot summon one");
        test.assertFalse(StandService.attack(player, false), "A player without a Stand cannot attack");
        test.succeed();
    }

    @GameTest public void grantRequiresOperatorPermission(GameTestHelper test) {
        var player = createPlayer(test);
        var source = player.createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS);
        var dispatcher = test.getLevel().getServer().getCommands().getDispatcher();
        try {
            dispatcher.execute("rotp grant @s star_platinum", source);
            test.fail("Unprivileged grant command unexpectedly executed");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            test.assertFalse(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Rejected command cannot grant a Stand");
        }
        try {
            dispatcher.execute("rotp grant @s star_platinum", source.withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS));
            test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Operator command grants a Stand");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            test.fail(error.getMessage());
        }
        test.succeed();
    }

    @GameTest public void attachmentSurvivesPlayerSave(GameTestHelper test) {
        var player = createPlayer(test);
        var expected = new StandProgress(StandProgress.STAR_PLATINUM, 456, 0.75F, true);
        player.setAttached(StandAttachments.PROGRESS, expected);
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        player.saveWithoutId(output);
        player.setAttached(StandAttachments.PROGRESS, StandProgress.EMPTY);
        player.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, test.getLevel().registryAccess(), output.buildResult()));
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS), expected, "Attachment must survive actual player serialization");
        test.succeed();
    }

    @GameTest public void insufficientStaminaRejectsAttack(GameTestHelper test) {
        var player = createPlayer(test);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        StandService.grant(player);
        test.assertTrue(StandService.toggle(player), "Summon succeeds without spending stamina");
        test.assertFalse(StandService.attack(player, false), "Cannot attack with zero stamina");
        test.assertValueEqual(StandService.current(player).actionPhase(), 0, "Rejected attack must leave the Stand idle");
        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest public void summonsAreIsolatedPerPlayer(GameTestHelper test) {
        var first = createPlayer(test);
        var second = createPlayer(test);
        StandService.grant(first);
        StandService.grant(second);
        test.assertTrue(StandService.toggle(first), "First summon succeeds");
        test.assertTrue(StandService.toggle(second), "Second summon succeeds");
        var firstStand = StandService.current(first);
        var secondStand = StandService.current(second);
        test.assertTrue(firstStand != secondStand && secondStand.owner() == second, "Players own distinct manifestations");
        StandService.clear(first);
        test.assertTrue(StandService.current(second) == secondStand && !secondStand.isRemoved(), "Clearing one Stand preserves another");
        StandService.clear(second);
        test.succeed();
    }

    @GameTest public void summonOwnershipAndClear(GameTestHelper test) {
        var player = createPlayer(test);
        StandService.grant(player);
        test.assertTrue(StandService.toggle(player), "Grant permits summoning");
        var stand = StandService.current(player);
        test.assertTrue(stand != null && stand.owner() == player, "Summon belongs to its player");
        test.assertFalse(StandService.toggle(player), "Same-tick toggle spam is rejected");
        StandService.clear(player);
        test.assertTrue(stand.isRemoved(), "Clear removes manifestation");
        test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).hadStand(), "Clear preserves Stand history");
        test.assertFalse(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Clear removes power");
        test.succeed();
    }

    @GameTest(maxTicks = 40) public void punchDamagesOnlyReachableTarget(GameTestHelper test) {
        var player = createPlayer(test);
        Vec3 start = test.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.snapTo(start, 0, 15);
        player.setNoGravity(true);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 3.5));
        target.setNoGravity(true);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        test.assertTrue(StandService.toggle(player), "Summon succeeds");
        test.assertTrue(StandService.attack(player, false), "Punch starts");
        test.assertFalse(StandService.attack(player, true), "Windup cannot be replaced by spam");
        test.runAtTickTime(4, () -> {
            test.assertValueEqual(target.getHealth(), target.getMaxHealth() - 4, "Light punch deals upstream base damage");
            StandService.dismiss(player);
            test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void barrageSpendsStaminaAndBypassesOnlyItsOwnCooldown(GameTestHelper test) {
        var player = createPlayer(test);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 15);
        player.setNoGravity(true);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 3.5));
        target.setNoGravity(true);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, 100, 0, true));
        StandService.toggle(player);
        test.assertTrue(StandService.startHeld(player, false), "Barrage starts");
        test.assertFalse(StandService.startHeld(player, false), "Repeated start cannot reset duration");
        test.assertFalse(StandService.attack(player, true), "Barrage excludes simultaneous heavy punch");
        test.runAtTickTime(5, () -> {
            test.assertTrue(target.getHealth() < target.getMaxHealth() - 5, "Consecutive barrage ticks must all damage the target");
            float stamina = player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina();
            test.assertTrue(stamina <= 84 && stamina >= 76, "Barrage costs four stamina per tick without action regeneration");
            StandService.stopHeld(player);
            test.assertValueEqual(StandService.current(player).actionPhase(), 0, "Release stops barrage immediately");
            test.assertFalse(StandService.startHeld(player, false), "Barrage recovery cannot be skipped");
            StandService.dismiss(player);
            test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void wallsBlockBarrage(GameTestHelper test) {
        var player = createPlayer(test);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 15);
        player.setNoGravity(true);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 3.5));
        target.setNoGravity(true);
        test.setBlock(new BlockPos(1, 2, 2), Blocks.STONE);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        StandService.toggle(player);
        test.assertTrue(StandService.startHeld(player, false), "Barrage starts");
        test.runAtTickTime(5, () -> {
            test.assertValueEqual(target.getHealth(), target.getMaxHealth(), "Barrage cannot hit through a wall");
            StandService.dismiss(player);
            test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void heldActionExpiresWithoutHeartbeat(GameTestHelper test) {
        var player = createPlayer(test);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        StandService.toggle(player);
        StandService.startHeld(player, true);
        test.runAtTickTime(15, () -> {
            test.assertValueEqual(StandService.current(player).actionPhase(), 0, "Lost connection input cannot leave guard active");
            StandService.dismiss(player);
            test.succeed();
        });
    }

    @GameTest public void guardReducesFrontalDamageAndSpendsStamina(GameTestHelper test) {
        var player = createPlayer(test);
        var attacker = createPlayer(test);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        attacker.snapTo(test.absoluteVec(new Vec3(1.5, 1, 3.5)), 180, 0);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, 100, 0, true));
        StandService.toggle(player);
        test.assertTrue(StandService.startHeld(player, true), "Guard starts");
        test.assertTrue(player.hurtServer(test.getLevel(), player.damageSources().playerAttack(attacker), 10), "Test player must accept a PvP hit");
        test.assertTrue(Math.abs(player.getHealth() - 19.6F) < 0.001F, "Guard applies original physical resistance; actual health=" + player.getHealth());
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 50F, "Guard spends original damage-squared stamina cost");
        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest public void guardDoesNotProtectFromBehindOrEnvironmentalDamage(GameTestHelper test) {
        var player = createPlayer(test);
        var attacker = createPlayer(test);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 2.5)), 0, 0);
        attacker.snapTo(test.absoluteVec(new Vec3(1.5, 1, 0.5)), 0, 0);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, 100, 0, true));
        StandService.toggle(player);
        StandService.startHeld(player, true);
        player.hurtServer(test.getLevel(), player.damageSources().playerAttack(attacker), 4);
        test.assertValueEqual(player.getHealth(), 16F, "Rear hits are not guarded");
        player.damageCooldownTime = 0;
        player.hurtServer(test.getLevel(), player.damageSources().fall(), 4);
        test.assertValueEqual(player.getHealth(), 12F, "Fall damage is not guarded");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 100F, "Unblockable hits spend no guard stamina");
        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest public void rejectedTransferredHitDoesNotSpendGuardStamina(GameTestHelper test) {
        var player = createPlayer(test);
        var attacker = createPlayer(test);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        attacker.snapTo(test.absoluteVec(new Vec3(1.5, 1, 3.5)), 180, 0);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, 100, 0, true));
        StandService.toggle(player);
        StandService.startHeld(player, true);
        var stand = StandService.current(player);
        var source = player.damageSources().playerAttack(attacker);
        test.assertTrue(stand.hurtServer(test.getLevel(), source, 10), "First transferred hit is accepted");
        test.assertTrue(Math.abs(player.getHealth() - 19.6F) < 0.001F, "Transferred damage is guarded exactly once");
        test.assertFalse(stand.hurtServer(test.getLevel(), source, 10), "Vanilla cooldown rejects duplicate damage");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 50F, "Rejected hit must not spend stamina");
        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest(maxTicks = 40) public void barrageStopsOnExhaustion(GameTestHelper test) {
        var player = createPlayer(test);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, 4, 0, true));
        StandService.toggle(player);
        test.assertTrue(StandService.startHeld(player, false), "One tick of stamina permits start");
        test.runAtTickTime(4, () -> {
            test.assertValueEqual(StandService.current(player).actionPhase(), 0, "Barrage stops before spending unavailable stamina");
            test.assertFalse(StandService.startHeld(player, false), "Exhaustion still requires recovery");
            StandService.dismiss(player);
            test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void exhaustedGuardBreaksAndLocksActions(GameTestHelper test) {
        var player = createPlayer(test);
        var attacker = createPlayer(test);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        attacker.snapTo(test.absoluteVec(new Vec3(1.5, 1, 3.5)), 180, 0);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, 25, 0, true));
        StandService.toggle(player);
        StandService.startHeld(player, true);
        player.hurtServer(test.getLevel(), player.damageSources().playerAttack(attacker), 10);
        test.assertTrue(Math.abs(player.getHealth() - 18.8F) < 0.001F, "Exhausted guard partially blocks using remaining stamina");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 0F, "Guard drains remaining stamina");
        test.assertValueEqual(StandService.current(player).actionPhase(), StarPlatinumEntity.PHASE_GUARD_BROKEN, "Break is synchronized");
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        test.assertFalse(StandService.startHeld(player, true), "Restoring stamina cannot bypass guard-break lock");
        test.assertFalse(StandService.attack(player, true), "Guard break prevents attacking");
        test.runAtTickTime(6, () -> {
            test.assertFalse(StandService.toggle(player), "Resummoning cannot bypass guard-break lock after normal toggle cooldown");
            StandService.dismiss(player);
            test.succeed();
        });
    }

    @GameTest(maxTicks = 40) public void wallsBlockPunches(GameTestHelper test) {
        var player = createPlayer(test);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 15);
        player.setNoGravity(true);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 3.5));
        target.setNoGravity(true);
        test.setBlock(new BlockPos(1, 2, 2), Blocks.STONE);
        StandService.grant(player);
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        test.assertTrue(StandService.toggle(player), "Summon succeeds");
        test.assertTrue(StandService.attack(player, false), "Punch starts");
        test.runAtTickTime(4, () -> {
            test.assertValueEqual(target.getHealth(), target.getMaxHealth(), "Cannot punch through a wall");
            StandService.dismiss(player);
            test.succeed();
        });
    }
}
