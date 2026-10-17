package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.github.standobyte.jojo.fabric.pillarman.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real WIND action/projectile/world contracts. Root owns registration and the only runner. */
public final class PillarWindGameTests {
    private static void tickEntity(net.minecraft.world.entity.Entity entity) {
        // 26.3 advances entity age in commonTick, separately from the virtual tick body.
        entity.commonTick(); entity.tick();
    }
    private static ServerPlayer player(GameTestHelper test) {
        var player = VampireGameTests.player(test, "pillar-wind", 1.5);
        PillarmanService.grant(player); PillarmanService.setMode(player, PillarmanProgress.Mode.WIND); return player;
    }
    private static void close(ServerPlayer player) {
        for (var entity : player.level().getEntitiesOfClass(PillarSandstormEntity.class, player.getBoundingBox().inflate(150))) if (entity.livingOwner() == player) entity.discard();
        PillarWindActions.clear(player); PillarmanService.clear(player); player.level().getServer().getPlayerList().remove(player);
    }
    private static void tick(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) { PillarWindActions.keepHeld(player); PillarWindActions.tick(player); }
    }
    private static int count(ServerPlayer player) { return player.level().getEntitiesOfClass(PillarSandstormEntity.class, player.getBoundingBox().inflate(10), e -> !e.isRemoved() && e.livingOwner() == player).size(); }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void windModeAtStageOneAndOriginalChargeCadence(GameTestHelper test) {
        var player = player(test);
        try {
            float energy = PillarmanService.state(player).energy();
            test.assertValueEqual(PillarmanService.state(player).stage(), 1, "Mode command does not imply stage4");
            test.assertTrue(PillarWindActions.smallSandstorm(player), "Source WIND-only gate works at stage1");
            test.assertValueEqual(PillarmanService.state(player).energy(), energy - 20, "Small storm costs20");
            test.assertFalse(PillarWindActions.smallSandstorm(player), "Small storm cooldown blocks spam");
            tick(player, 10); test.assertValueEqual(PillarWindActions.state(player).smallCooldown(), 0, "Small cooldown is10");
            test.assertTrue(PillarWindActions.startHeld(player, PillarWindActions.Held.DIVINE), "Shifted Divine starts");
            int before = count(player); energy = PillarmanService.state(player).energy();
            tick(player, 39);
            test.assertValueEqual(count(player), before, "No projectile before40 charge ticks");
            test.assertValueEqual(PillarmanService.state(player).energy(), energy, "Windup costs no energy");
            tick(player, 1); test.assertValueEqual(count(player), before + 1, "First Divine emission at40");
            tick(player, 1); test.assertValueEqual(count(player), before + 1, "Odd held tick does not emit");
            tick(player, 1); test.assertValueEqual(count(player), before + 2, "Divine emits every two ticks");
            test.assertValueEqual(PillarmanService.state(player).energy(), energy - 9, "Active hold spends3 every tick, including odd tick");
            PillarWindActions.stopHeld(player); tick(player, 2);
            test.assertValueEqual(count(player), before + 2, "Release stops emissions");
            PillarmanService.setMode(player, PillarmanProgress.Mode.LIGHT);
            test.assertFalse(PillarWindActions.smallSandstorm(player), "Wrong mode rejects WIND");
            test.succeed();
        } finally { close(player); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void stormsPierceEntitiesThroughImmunityAndPersistDecay(GameTestHelper test) {
        var player = player(test);
        var first = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3));
        var second = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 6));
        first.setNoGravity(true); second.setNoGravity(true);
        try {
            var storm = new PillarSandstormEntity(player).configure(false, 1.5F, 6, 40);
            storm.setPos(test.absoluteVec(new Vec3(2.5, 3, 1.5))); storm.setDeltaMovement(0, 0, .6); player.level().addFreshEntity(storm);
            float health1 = first.getHealth(), health2 = second.getHealth(); first.damageCooldownTime = 17; second.damageCooldownTime = 17;
            // Stop beyond both targets, before the test enclosure wall.
            for (int i = 0; i < 9; i++) tickEntity(storm);
            test.assertTrue(first.getHealth() < health1 && second.getHealth() < health2, "One surviving storm reaches multiple real targets");
            test.assertValueEqual(first.damageCooldownTime, 17, "Existing hurt immunity remains unchanged");
            test.assertFalse(storm.isRemoved(), "Entity collision does not destroy the projectile");
            test.assertFalse(storm.canBeEvaded(player) || storm.canBeDeflected(player), "Source rejects evasion and deflection");
            storm.tickCount = 35;
            test.assertValueEqual(storm.damageAmount(), 3F, "Second half damage cannot fall below50 percent");
            var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess());
            storm.saveWithoutId(saved); storm.discard();
            var loaded = new PillarSandstormEntity(PillarWindActions.SANDSTORM_ENTITY, player.level());
            loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), saved.buildResult()));
            player.level().addFreshEntity(loaded);
            test.assertTrue(loaded.livingOwner() == player, "Saved projectile retains real owner UUID");
            test.assertValueEqual(loaded.damageAmount(), 3F, "Age/damage/lifetime survive actual entity serialization");
            loaded.setPos(loaded.position().add(0, 100, 0)); loaded.setDeltaMovement(Vec3.ZERO);
            for (int i = 0; i < 5; i++) tickEntity(loaded);
            test.assertFalse(loaded.isRemoved(), "Storm remains alive through its configured tick40");
            tickEntity(loaded);
            test.assertTrue(loaded.isRemoved(), "Source lifetime eventually removes saved storm");
            test.succeed();
        } finally { first.discard(); second.discard(); close(player); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void terrainCollisionRespectsHardnessAndBreakRule(GameTestHelper test) {
        var player = player(test); boolean old = player.level().getGameRules().get(JojoGameRules.BREAK_BLOCKS);
        var block = new BlockPos(2, 3, 4);
        try {
            for (int caseIndex = 0; caseIndex < 3; caseIndex++) {
                boolean breaking = caseIndex != 2;
                player.level().getGameRules().set(JojoGameRules.BREAK_BLOCKS, breaking, player.level().getServer());
                test.setBlock(block, caseIndex == 1 ? Blocks.OBSIDIAN : Blocks.STONE);
                var storm = new PillarSandstormEntity(player).configure(false, 1.5F, 6, 40);
                storm.setPos(test.absoluteVec(new Vec3(2.5, 3.5, 2.5))); storm.setDeltaMovement(0, 0, 1); player.level().addFreshEntity(storm);
                for (int i = 0; i < 3 && !storm.isRemoved(); i++) tickEntity(storm);
                test.assertTrue(storm.isRemoved(), "Block hit always ends source storm");
                test.assertValueEqual(test.getBlockState(block).isAir(), caseIndex == 0, "Only allowed hardness<=5 terrain breaks");
            }
            test.succeed();
        } finally { player.level().getGameRules().set(JojoGameRules.BREAK_BLOCKS, old, player.level().getServer()); close(player); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void cloakSunProtectionAndRiftSelfDamageFollowHeldLifecycle(GameTestHelper test) {
        var player = player(test);
        try {
            float energy = PillarmanService.state(player).energy();
            test.assertTrue(PillarWindActions.startHeld(player, PillarWindActions.Held.CLOAK), "Cloak starts"); tick(player, 1);
            test.assertTrue(player.hasEffect(MobEffects.INVISIBILITY) && SpeciesHooks.sunProtected(player), "Cloak refreshes actual invisibility and shared sunlight protection");
            test.assertValueEqual(player.getEffect(PillarWindActions.SUN_RESISTANCE).getDuration(), 5, "Source sun resistance lasts5");
            test.assertValueEqual(PillarmanService.state(player).energy(), energy - 2, "Cloak costs2 per applied tick");
            tick(player, 199);
            test.assertValueEqual(PillarWindActions.state(player).held(), PillarWindActions.Held.NONE, "Source maximum ends hold before tick200 effect application");
            test.assertValueEqual(PillarWindActions.state(player).cloakCooldown(), 100, "Cloak applies fixed100 cooldown");
            test.assertValueEqual(PillarmanService.state(player).energy(), energy - 398, "199 actual effect ticks spend398");
            test.assertFalse(PillarWindActions.startHeld(player, PillarWindActions.Held.CLOAK), "Cloak cooldown cannot be bypassed");
            test.assertTrue(PillarWindActions.startHeld(player, PillarWindActions.Held.RIFT), "Rift uses separate action cooldown");
            float health = player.getHealth(); tick(player, 39);
            test.assertValueEqual(player.getHealth(), health, "No Rift self damage during windup");
            tick(player, 1);
            test.assertTrue(player.getHealth() < health, "Rift emission deals ordinary one-point self damage");
            var rifts = player.level().getEntitiesOfClass(PillarSandstormEntity.class, player.getBoundingBox().inflate(10), PillarSandstormEntity::atmospheric);
            test.assertValueEqual(rifts.size(), 1, "Rift spawns its own real projectile");
            test.assertValueEqual(rifts.getFirst().radius(), .5F, "Rift radius differs from Divine");
            test.assertValueEqual(rifts.getFirst().ticksLifespan(), 60, "Rift duration60");
            for (int i = 0; i < 16; i++) PillarWindActions.tick(player);
            test.assertFalse(PillarWindActions.isHolding(player), "Missing heartbeat cancels held Rift");
            test.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0, "Stop restores walking");
            test.succeed();
        } finally { close(player); }
    }
}
