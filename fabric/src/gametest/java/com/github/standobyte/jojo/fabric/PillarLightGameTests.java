package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.github.standobyte.jojo.fabric.pillarman.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Three grouped real-world contracts; root registers and runs them in the shared server lane. */
public final class PillarLightGameTests {
    private static ServerPlayer player(GameTestHelper test) {
        var player = VampireGameTests.player(test, "pillar-light", 1.5);
        PillarmanService.grant(player); PillarmanService.setMode(player, PillarmanProgress.Mode.LIGHT); return player;
    }
    private static void tick(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) { PillarLightActions.keepHeld(player); PillarLightActions.tick(player); }
    }
    private static void close(ServerPlayer player) {
        PillarLightActions.clear(player); PillarmanService.clear(player); player.level().getServer().getPlayerList().remove(player);
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void flashVisibilityChargeAndFullInvisibility(GameTestHelper test) {
        var player = player(test);
        var near = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(3.5, 2, 3));
        var far = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(3.5, 2, 7));
        var blocked = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(.5, 2, 1.5));
        try {
            for (int y = 2; y <= 4; y++) for (int z = 0; z <= 3; z++) test.setBlock(new BlockPos(1, y, z), Blocks.STONE);
            float energy = PillarmanService.state(player).energy();
            test.assertTrue(PillarLightActions.flash(player), "LIGHT at stage1 charges Flash"); tick(player, 39);
            test.assertFalse(near.hasEffect(MobEffects.BLINDNESS), "Charge does not apply Flash early");
            test.assertValueEqual(PillarmanService.state(player).energy(), energy, "Charge spends on fire only"); tick(player, 1);
            test.assertValueEqual(near.getEffect(MobEffects.BLINDNESS).getDuration(), 200, "Near visible target blinded200");
            test.assertValueEqual(far.getEffect(MobEffects.BLINDNESS).getDuration(), 80, "Far visible target blinded80");
            test.assertValueEqual(near.getEffect(VampireCombatService.STUN).getDuration(), 60, "Nonplayer stunned60");
            test.assertFalse(blocked.hasEffect(MobEffects.BLINDNESS), "Opaque wall blocks target line of sight");
            test.assertValueEqual(PillarmanService.state(player).energy(), energy - 25, "Flash costs25");
            test.assertValueEqual(AbilityCooldowns.remaining(player, PillarLightActions.FLASH), 80, "Flash cooldown80");
            test.assertFalse(PillarLightActions.flash(player), "Flash cooldown rejects repeat");
            test.assertTrue(PillarLightActions.decoy(player), "Shift variation has separate cooldown"); tick(player, 49);
            test.assertFalse(PillarLightActions.hidden(player), "Decoy waits50"); tick(player, 1);
            test.assertValueEqual(player.getEffect(PillarLightActions.FULL_INVISIBILITY).getDuration(), 100, "Full invisibility lasts100");
            test.assertTrue(PillarLightActions.uncurable(PillarLightActions.FULL_INVISIBILITY), "Source full invisibility cannot be cured by milk");
            test.assertValueEqual(PillarmanService.state(player).energy(), energy - 65, "Decoy costs40");
            test.assertValueEqual(AbilityCooldowns.remaining(player, PillarLightActions.DECOY), 120, "Decoy cooldown120");
            test.succeed();
        } finally { near.discard(); far.discard(); blocked.discard(); close(player); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void bladeSlashAndGroundedUnlimitedDash(GameTestHelper test) {
        var player = player(test);
        var target = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3)); target.setNoGravity(true);
        try {
            float health = target.getHealth();
            test.assertTrue(PillarLightActions.slash(player), "Slash starts with free hand"); tick(player, 7);
            test.assertValueEqual(target.getHealth(), health, "Slash waits until tick8"); tick(player, 1);
            test.assertTrue(target.getHealth() < health, "Real aimed target receives slash"); tick(player, 12);
            test.assertFalse(PillarLightActions.active(player), "Slash ends20");
            player.setOnGround(false); test.assertFalse(PillarLightActions.dashCharge(player), "Airborne dash cannot start");
            player.setOnGround(true); test.assertTrue(PillarLightActions.dashCharge(player), "Grounded dash charges"); tick(player, 50);
            test.assertValueEqual(PillarLightActions.state(player).phase(), PillarLightActions.Phase.DASH_CHARGE, "Source override holds beyond40");
            PillarLightActions.release(player, true);
            test.assertValueEqual(PillarLightActions.state(player).phase(), PillarLightActions.Phase.DASH, "Release enters continuous dash");
            test.assertTrue(player.getDeltaMovement().length() > 1, "Dash launch uses restored movement attribute");
            health = player.getHealth(); player.hurtServer(player.level(), player.damageSources().generic(), 3);
            test.assertValueEqual(player.getHealth(), health, "Integrated damage hook cancels generic damage while dashing");
            target.snapTo(player.position().add(0, 0, .5)); target.damageCooldownTime = 0; health = target.getHealth(); tick(player, 1);
            test.assertTrue(target.getHealth() < health, "Low frontal sweep hits real target"); health = target.getHealth();
            target.damageCooldownTime = 0; tick(player, 1); test.assertValueEqual(target.getHealth(), health, "One dash damages each UUID once");
            tick(player, 13); test.assertFalse(PillarLightActions.active(player), "Dash ends15");
            test.assertFalse(PillarLightCombat.cancelIncomingDamage(player, player.damageSources().generic()), "Dash immunity stops immediately");
            test.succeed();
        } finally { target.discard(); close(player); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void barrageImmunityProjectileDefenseTerrainAndLifecycle(GameTestHelper test) {
        var player = player(test);
        var target = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3)); target.setNoGravity(true);
        var snowball = EntityTypes.SNOWBALL.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        var ribs = new PillarRibsEntity(player, 0, 0, 0, 0);
        var storm = new PillarSandstormEntity(player);
        boolean old = player.level().getGameRules().get(JojoGameRules.BREAK_BLOCKS);
        try {
            test.assertTrue(PillarLightActions.barrage(player), "Barrage starts");
            float health = target.getHealth(); target.damageCooldownTime = 17; tick(player, 2);
            test.assertTrue(target.getHealth() < health - (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * .2F, "Multiple barrage hits bypass existing damage immunity");
            test.assertValueEqual(target.damageCooldownTime, 17, "Barrage restores original immunity timer");
            snowball.setPos(player.position().add(0, 1, 1)); snowball.setDeltaMovement(0, 0, -1); player.level().addFreshEntity(snowball);
            var source = player.damageSources().thrown(snowball, player);
            test.assertTrue(PillarLightCombat.cancelIncomingDamage(player, source), "Approaching ordinary projectile is defended");
            snowball.setDeltaMovement(0, 0, 1);
            test.assertFalse(PillarLightCombat.cancelIncomingDamage(player, source), "Receding projectile is outside defense cone");
            test.assertTrue(PillarLightCombat.cancelIncomingDamage(player, player.damageSources().thrown(ribs, player)), "Evadeable nonstand modded projectile defended");
            test.assertFalse(PillarLightCombat.cancelIncomingDamage(player, player.damageSources().thrown(storm, player)), "Custom evasion immunity respected");
            target.discard(); var block = new BlockPos(2, 3, 3); test.setBlock(block, Blocks.DIRT);
            player.level().getGameRules().set(JojoGameRules.BREAK_BLOCKS, false, player.level().getServer()); tick(player, 1);
            test.assertFalse(test.getBlockState(block).isAir(), "Break rule prevents barrage terrain changes");
            player.level().getGameRules().set(JojoGameRules.BREAK_BLOCKS, true, player.level().getServer()); tick(player, 1);
            test.assertTrue(test.getBlockState(block).isAir(), "Allowed low-hardness block breaks in real world");
            for (int i = 0; i < 21; i++) PillarLightActions.tick(player);
            test.assertFalse(PillarLightActions.active(player), "Stale heartbeat clears barrage");
            test.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "Cleanup restores movement");
            test.assertTrue(PillarLightActions.barrage(player), "Barrage restarts after cleanup");
            PillarmanService.setMode(player, PillarmanProgress.Mode.WIND); tick(player, 1);
            test.assertFalse(PillarLightActions.active(player), "Changing mode clears active LIGHT action");
            test.assertFalse(PillarLightActions.slash(player), "Wrong mode cannot start LIGHT");
            test.succeed();
        } finally {
            player.level().getGameRules().set(JojoGameRules.BREAK_BLOCKS, old, player.level().getServer());
            target.discard(); snowball.discard(); ribs.discard(); storm.discard(); close(player);
        }
    }
}
