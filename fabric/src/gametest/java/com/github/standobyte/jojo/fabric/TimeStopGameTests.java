package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** A separate environment creates a separate batch, so regional freezing cannot pause unrelated tests. */
public final class TimeStopGameTests {
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



    private static net.minecraft.server.level.ServerPlayer caster(GameTestHelper test, float stamina) {
        var player = createPlayer(test);
        StandService.grant(player);
        player.setAttached(StandAttachments.RESOLVE, com.github.standobyte.jojo.core.ResolveProgress.EMPTY.withLevel(4).withTraining(95)); StandService.toggle(player);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, stamina, 0, true));
        return player;
    }

    @GameTest(environment = "jojo:time_stop", maxTicks = 420)
    public void freezesAndResumesActualWorldTicks(GameTestHelper test) {
        var player = createPlayer(test);
        test.assertFalse(TimeStopService.toggle(player), "No power cannot stop time");
        StandService.grant(player);
        player.setAttached(StandAttachments.RESOLVE, com.github.standobyte.jojo.core.ResolveProgress.EMPTY.withLevel(4).withTraining(95));
        test.assertFalse(TimeStopService.toggle(player), "Unsummoned Stand cannot stop time");
        StandService.toggle(player);
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.STAR_PLATINUM, 100, 0, true));
        test.assertFalse(TimeStopService.toggle(player), "Upfront stamina is required");
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        test.assertTrue(TimeStopService.toggle(player), "Charge starts");
        test.assertValueEqual(TimeStopService.chargeTicksRemaining(player), 40, "Original charge duration");
        test.assertTrue(TimeStopService.toggle(player), "Charge can be cancelled");
        test.assertValueEqual(TimeStopService.cooldownRemaining(player), 20, "Cancelling charge has bounded cooldown");
        test.assertFalse(TimeStopService.toggle(player), "Charge spam cannot bypass cooldown");
        var victim = createPlayer(test);
        victim.snapTo(test.absoluteVec(new Vec3(4.5, 1, 1.5)), 0, 0);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 1, 3.5)); cow.setNoGravity(true);
        var arrow = new Arrow(test.getLevel(), player, new ItemStack(Items.ARROW), null);
        arrow.snapTo(test.absoluteVec(new Vec3(2.5, 4, 2.5)), 0, 0); arrow.setNoGravity(true); arrow.setDeltaMovement(0, 0.05, 0);
        test.getLevel().addFreshEntity(arrow);
        var stand = StandService.current(player);
        int[] clocks = new int[5];
        Vec3[] arrowPosition = new Vec3[1];
        BlockPos button = new BlockPos(1, 1, 5), water = new BlockPos(3, 2, 3), furnace = new BlockPos(3, 1, 5);
        test.runAtTickTime(25, () -> {
            player.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
            test.assertTrue(TimeStopService.toggle(player), "Charge can restart after cooldown");
            test.assertFalse(StandService.attack(player, true), "Charge blocks competing Stand attacks");
        });
        test.runAtTickTime(66, () -> {
            test.assertTrue(TimeStopService.isActive(player), "Charge completes through actual server ticks");
            int elapsed = 100 - TimeStopService.ticksRemaining(player);
            float expected = 2000 - 225 - 9 * elapsed;
            test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), expected, "Original activation and per-tick cost without regeneration");
            test.assertTrue(TimeStopService.isFrozen(cow) && TimeStopService.isFrozen(arrow) && TimeStopService.isFrozen(victim), "Mob, projectile and other player are frozen");
            test.assertFalse(TimeStopService.isFrozen(player) || TimeStopService.isFrozen(stand), "Caster and owned Stand are immune");
            test.assertFalse(TimeStopService.toggle(victim), "A frozen player cannot take caster immunity");
            cow.damageCooldownTime = 20;
            clocks[0] = cow.tickCount; clocks[1] = arrow.tickCount; clocks[2] = victim.tickCount;
            clocks[3] = player.tickCount; clocks[4] = stand.tickCount; arrowPosition[0] = arrow.position();
            test.setBlock(new BlockPos(1, 1, 6), Blocks.STONE);
            test.setBlock(button, Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.POWERED, true));
            test.getLevel().scheduleTick(test.absolutePos(button), Blocks.STONE_BUTTON, 2);
            test.setBlock(water, Blocks.WATER);
            test.setBlock(furnace, Blocks.FURNACE);
            var container = test.getBlockEntity(furnace, net.minecraft.world.level.block.entity.FurnaceBlockEntity.class);
            container.setItem(0, new ItemStack(Items.IRON_ORE)); container.setItem(1, new ItemStack(Items.COAL));
        });
        test.runAtTickTime(72, () -> {
            test.assertValueEqual(cow.tickCount, clocks[0], "Mob ticking really stops");
            test.assertValueEqual(arrow.tickCount, clocks[1], "Projectile ticking really stops");
            test.assertValueEqual(victim.tickCount, clocks[2], "Other player entity clock stops");
            test.assertValueEqual(arrow.position(), arrowPosition[0], "Projectile position stays fixed");
            test.assertTrue(player.tickCount > clocks[3] && stand.tickCount > clocks[4], "Caster and Stand keep ticking");
            test.assertTrue(test.getBlockState(button).getValue(BlockStateProperties.POWERED), "Scheduled block tick stays queued");
            test.assertTrue(test.getBlockState(water.below()).isAir(), "Scheduled fluid spread stays queued");
            test.assertFalse(test.getBlockState(furnace).getValue(BlockStateProperties.LIT), "Furnace block entity does not advance");
            test.assertTrue(cow.damageCooldownTime >= 12 && cow.damageCooldownTime <= 16, "Frozen damage cooldown advances once per server tick");
            Vec3 victimPosition = victim.position();
            victim.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(victimPosition.add(3, 0, 0), true, false));
            test.assertValueEqual(victim.position(), victimPosition, "Server rejects movement packets from a frozen player");
            float health = cow.getHealth();
            test.assertTrue(cow.hurtServer(test.getLevel(), cow.damageSources().playerAttack(player), 4), "Caster can damage frozen targets");
            test.assertTrue(cow.getHealth() < health, "Damage actually changes frozen target health");
        });
        test.runAtTickTime(75, () -> {
            test.assertTrue(TimeStopService.toggle(player), "Early resume succeeds");
            test.assertFalse(TimeStopService.isFrozen(cow), "Resume removes entity freeze");
            test.assertTrue(TimeStopService.cooldownRemaining(player) >= 20, "Resume applies cooldown");
        });
        test.runAtTickTime(82, () -> {
            test.assertTrue(cow.tickCount > clocks[0] && arrow.tickCount > clocks[1] && victim.tickCount > clocks[2], "All frozen clocks resume");
            test.assertFalse(test.getBlockState(button).getValue(BlockStateProperties.POWERED), "Queued block tick executes after resume");
            test.assertTrue(!test.getBlockState(water.below()).getFluidState().isEmpty(), "Queued fluid spread resumes");
            test.assertTrue(test.getBlockState(furnace).getValue(BlockStateProperties.LIT), "Furnace resumes after time stop");
            int cooldown = TimeStopService.cooldownRemaining(player);
            StandService.dismiss(player); StandService.grant(player);
        player.setAttached(StandAttachments.RESOLVE, com.github.standobyte.jojo.core.ResolveProgress.EMPTY.withLevel(4).withTraining(95));
            test.assertValueEqual(TimeStopService.cooldownRemaining(player), cooldown, "Dismissal and regrant cannot clear cooldown");
        });
        var lowStamina = new net.minecraft.server.level.ServerPlayer[1];
        test.runAtTickTime(125, () -> {
            lowStamina[0] = caster(test, 234);
            test.assertTrue(TimeStopService.toggle(lowStamina[0]), "Minimum one-active-tick stamina starts charge");
        });
        test.runAtTickTime(172, () -> {
            test.assertFalse(TimeStopService.isActive(lowStamina[0]), "Exhaustion resumes time automatically");
            test.assertValueEqual(TimeStopService.cooldownRemaining(lowStamina[0]), 0, "Original three-tick cooldown for one stopped tick has elapsed");
            StandService.clear(lowStamina[0]);
        });
        var fullDuration = new net.minecraft.server.level.ServerPlayer[1];
        test.runAtTickTime(195, () -> {
            fullDuration[0] = caster(test, 2000);
            test.assertTrue(TimeStopService.toggle(fullDuration[0]), "Fresh caster charges");
            StandService.dismiss(fullDuration[0]);
            test.assertFalse(TimeStopService.isCharging(fullDuration[0]), "Dismissal cancels charge immediately");
            test.assertTrue(TimeStopService.cooldownRemaining(fullDuration[0]) > 0, "Charge invalidation preserves cooldown");
        });
        test.runAtTickTime(220, () -> {
            StandService.toggle(fullDuration[0]);
            test.assertTrue(TimeStopService.toggle(fullDuration[0]), "Time stop starts after invalidation cooldown");
        });
        test.runAtTickTime(265, () -> test.assertTrue(TimeStopService.isActive(fullDuration[0]), "Full duration stop activates"));
        test.runAtTickTime(362, () -> {
            test.assertFalse(TimeStopService.isActive(fullDuration[0]), "Stop ends at its 100-tick maximum");
            test.assertTrue(TimeStopService.cooldownRemaining(fullDuration[0]) >= 295, "Full duration produces 300-tick cooldown");
            StandService.clear(fullDuration[0]); StandService.clear(player); StandService.clear(victim);
            test.succeed();
        });
    }
}
