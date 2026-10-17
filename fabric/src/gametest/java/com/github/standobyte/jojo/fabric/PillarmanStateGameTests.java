package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Registered by the root build lane. Real players, damage hooks, entity serialization and respawn. */
public final class PillarmanStateGameTests {
    private static ServerPlayer player(GameTestHelper test) {
        var player = VampireGameTests.player(test, "pillar-test", 1.5);
        test.assertTrue(PillarmanService.grant(player), "Administrative Pillar acquisition succeeds");
        return player;
    }
    private static void ticks(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) { PillarmanBaseActions.keepHeld(player); PillarmanBaseActions.tick(player); }
    }
    private static void close(ServerPlayer player) { PillarmanService.clear(player); player.level().getServer().getPlayerList().remove(player); }

    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void stagesMasksAttributesAndRealSaveDeath(GameTestHelper test) {
        var player = player(test);
        test.assertValueEqual(PillarmanService.state(player).stage(), 1, "Initial stage is 1");
        test.assertValueEqual(PillarmanService.state(player).energy(), 1000F, "Original acquisition adds 1000 energy");
        test.assertValueEqual(player.getMaxHealth(), 40F, "Stage1 adds twenty max health");
        test.assertTrue(PillarmanService.setMode(player, PillarmanProgress.Mode.LIGHT), "Commands can set modes below stage4");
        test.assertTrue(PillarmanService.applyMask(player, false), "Ordinary mask upgrades 1 to 2");
        test.assertValueEqual(player.getMaxHealth(), 60F, "Stage2 adds forty max health");
        test.assertFalse(PillarmanService.applyMask(player, false), "Ordinary mask cannot evolve stage2 again");
        PillarmanService.setStage(player, 3);
        test.assertValueEqual(player.getMaxHealth(), 70F, "Command-only stage3 has level5 buffs");
        test.assertTrue(PillarmanService.applyMask(player, true), "Aja jumps stage3 to 4");
        test.assertValueEqual(player.getMaxHealth(), 80F, "Stage4 has level6 buffs");
        test.assertTrue(PillarmanService.state(player).mode() != PillarmanProgress.Mode.NONE, "Aja selects a real mode");
        PillarmanService.setStoneForm(player, true); PillarmanService.setBlades(player, true);
        var expected = PillarmanService.state(player).withEnergy(321); player.setAttached(PillarmanAttachments.PROGRESS, expected);
        var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(saved); PillarmanService.clear(player);
        player.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), saved.buildResult()));
        PillarmanService.updateAttributes(player);
        test.assertValueEqual(PillarmanService.state(player), expected, "Actual entity serialization retains stage, mode, stone and energy");
        test.assertFalse(player.getAttachedOrCreate(PillarmanAttachments.BLADES), "Blades are not persistent");
        player.setInvulnerableTime(0); player.hurtServer(player.level(), player.damageSources().genericKill(), Float.MAX_VALUE);
        var replacement = player.level().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        test.assertValueEqual(PillarmanService.state(replacement), expected.afterDeath(), "Actual death retains power/energy/mode and clears stone");
        test.assertFalse(replacement.getAttachedOrCreate(PillarmanAttachments.BLADES), "Death clears blades");
        test.assertValueEqual(replacement.getMaxHealth(), 80F, "Respawn restores stage attributes");
        PillarmanService.clear(replacement);
        test.assertValueEqual(replacement.getMaxHealth(), 20F, "Clearing power removes all max-health modifiers");
        test.assertValueEqual(replacement.getAttributeValue(Attributes.ATTACK_DAMAGE), 1D, "Clearing removes attack bonus");
        test.assertValueEqual(PillarmanAttachments.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow(), PillarmanProgress.EMPTY, "Old save without attachment defaults to no power");
        var corrupt = com.google.gson.JsonParser.parseString("{\"pillarman\":true,\"PillarmanStage\":5}");
        test.assertTrue(PillarmanAttachments.CODEC.parse(JsonOps.INSTANCE, corrupt).error().isPresent(), "Out-of-range saved stages cannot grant future powers");
        close(replacement); test.succeed();
    }

    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void heavyPunchTimingAbsorptionOverlapAndNoSpend(GameTestHelper test) {
        var player = player(test); PillarmanService.setStage(player, 2);
        var victim = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3)); victim.setNoGravity(true);
        float health = victim.getHealth(), energy = PillarmanService.state(player).energy();
        test.assertTrue(PillarmanBaseActions.heavyPunch(player), "Empty-hand punch starts");
        ticks(player, 4); test.assertValueEqual(victim.getHealth(), health, "Punch has no early hit before tick5");
        ticks(player, 1); test.assertTrue(victim.getHealth() < health, "Tick5 damages real target");
        test.assertValueEqual(PillarmanService.state(player).energy(), energy, "Heavy punch never spends registered eligibility cost");
        ticks(player, 3); test.assertValueEqual(PillarmanBaseActions.punchTicks(player), 0, "Punch ends at tick8");
        test.assertTrue(PillarmanBaseActions.startHeld(player, PillarmanBaseActions.Held.ABSORPTION), "Absorption starts");
        test.assertTrue(PillarmanBaseActions.heavyPunch(player), "Absorption explicitly permits another instant action");
        test.assertValueEqual(PillarmanBaseActions.held(player), PillarmanBaseActions.Held.ABSORPTION, "Punch does not erase absorption hold");
        ticks(player, 8);
        for (int i = 0; i < 16; i++) PillarmanBaseActions.tick(player);
        test.assertValueEqual(PillarmanBaseActions.held(player), PillarmanBaseActions.Held.NONE, "Stale client input releases held authority");
        test.assertTrue(PillarmanBaseActions.startHeld(player, PillarmanBaseActions.Held.STONE_FORM), "Stone starts");
        test.assertFalse(PillarmanBaseActions.regenerate(player), "Non-absorption holds reject overlapping actions");
        ticks(player, 39); test.assertFalse(PillarmanService.state(player).stoneForm(), "Stone has forty-tick windup");
        ticks(player, 1); test.assertTrue(PillarmanService.state(player).stoneForm(), "Stone activates at tick40");
        PillarmanService.tick(player);
        test.assertValueEqual(player.getEffect(MobEffects.RESISTANCE).getAmplifier(), 3, "Stone grants original Resistance IV");
        test.assertFalse(PillarmanBaseActions.heavyPunch(player), "Stone blocks punch");
        test.assertFalse(VampireHamon.damageRaw(player.level(), player, null, 10), "Shared Hamon damage hook honors stone immunity");
        victim.discard(); close(player); test.succeed();
    }

    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void absorptionUsesLivingTargetsProtectionAndRegenerationEconomy(GameTestHelper test) {
        var player = player(test); PillarmanService.setStage(player, 2);
        var undead = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 2, 3)); undead.setNoGravity(true);
        undead.getAttribute(Attributes.ARMOR).setBaseValue(0);
        float energy = PillarmanService.state(player).energy();
        test.assertTrue(PillarmanBaseActions.absorb(player, undead), "Absorption includes original undead living targets");
        test.assertValueEqual(undead.getHealth(), 18F, "Absorption deals two actual damage");
        test.assertValueEqual(PillarmanService.state(player).energy(), energy + 35 * VampireConfig.server().bloodDrainMultiplier()[player.level().getDifficulty().getId()], "Successful damage grants 35 times difficulty multiplier");
        test.assertValueEqual(undead.getEffect(MobEffects.WEAKNESS).getAmplifier(), 1, "Absorption debuffs always amplifier1");
        test.assertFalse(PillarmanBaseActions.absorb(player, undead), "Ordinary hit immunity prevents repeated same-tick drain");
        undead.discard();
        var hamon = VampireGameTests.player(test, "pillar-hamon", 3);
        HamonService.grant(hamon); HamonService.energy(hamon, 100); float protectedHealth = hamon.getHealth();
        test.assertFalse(PillarmanBaseActions.absorb(player, hamon), "Hamon contact protection prevents absorption");
        test.assertValueEqual(hamon.getHealth(), protectedHealth, "Protected target takes no absorption damage");
        energy = PillarmanService.state(player).energy();
        test.assertTrue(PillarmanBaseActions.regenerate(player), "Regeneration starts at stage2");
        test.assertValueEqual(PillarmanService.state(player).energy(), energy - 40, "Regeneration pays forty immediately");
        test.assertValueEqual(player.getEffect(MobEffects.REGENERATION).getAmplifier(), 4, "Active regen amplifier4");
        test.assertFalse(PillarmanBaseActions.regenerate(player), "Twenty-tick cooldown is server authoritative");
        ticks(player, 20); test.assertTrue(PillarmanBaseActions.regenerate(player), "Regeneration becomes available after twenty ticks");
        HamonService.clear(hamon); hamon.level().getServer().getPlayerList().remove(hamon); close(player); test.succeed();
    }

    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void evasionDamageFiltersSensesAndPassiveThresholds(GameTestHelper test) {
        var player = player(test); PillarmanService.setStage(player, 2);
        var attacker = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 2, 3));
        test.assertTrue(PillarmanBaseActions.startHeld(player, PillarmanBaseActions.Held.EVASION), "Evasion starts at stage2");
        test.assertFalse(player.hurtServer(player.level(), player.damageSources().mobAttack(attacker), 5), "Actual incoming entity attack is evaded by integration hook");
        test.assertTrue(player.hurtServer(player.level(), player.damageSources().generic(), 1), "Environmental damage cannot be evaded");
        ticks(player, 10); PillarmanBaseActions.stopHeld(player);
        test.assertFalse(PillarmanBaseActions.startHeld(player, PillarmanBaseActions.Held.EVASION), "Ten held ticks produce ten cooldown ticks");
        ticks(player, 10); test.assertTrue(PillarmanBaseActions.startHeld(player, PillarmanBaseActions.Held.SENSES), "Senses starts");
        ticks(player, 80); test.assertValueEqual(PillarmanBaseActions.sensesRadius(player), 36D, "Senses radius caps at36");
        PillarmanBaseActions.stopHeld(player);
        player.setAttached(PillarmanAttachments.PROGRESS, PillarmanService.state(player).withEnergy(610)); PillarmanService.tick(player);
        test.assertValueEqual(player.getEffect(MobEffects.REGENERATION).getAmplifier(), 1, "Above thirty percent grants passive regen1");
        player.setAttached(PillarmanAttachments.PROGRESS, PillarmanService.state(player).withEnergy(210)); PillarmanService.tick(player);
        test.assertValueEqual(player.getEffect(MobEffects.REGENERATION).getAmplifier(), 0, "Above ten percent grants passive regen0");
        player.setAttached(PillarmanAttachments.PROGRESS, PillarmanService.state(player).withEnergy(10)); PillarmanService.tick(player);
        test.assertFalse(player.hasEffect(MobEffects.REGENERATION), "Below ten percent removes passive regen");
        test.assertValueEqual(player.getFoodData().getFoodLevel(), 17, "Stage2 fixes food at17");
        attacker.discard(); close(player); test.succeed();
    }
}
