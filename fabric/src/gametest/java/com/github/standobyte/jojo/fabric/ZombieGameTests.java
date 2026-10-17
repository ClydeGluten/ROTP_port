package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ZombieProgress;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** Real player, damage, persistence and input lifecycle scenarios for the root build lane. */
public final class ZombieGameTests {
    private static ServerPlayer zombie(GameTestHelper test) {
        var player = VampireGameTests.player(test, "zombie-test", 1.5);
        test.assertTrue(ZombieService.grant(player), "Administrator grants the independent zombie power");
        return player;
    }
    private static void tick(ServerPlayer player, int ticks) {
        for (int i = 0; i < ticks; i++) { ZombieService.refreshHeld(player); ZombieService.tick(player); }
    }
    private static void close(ServerPlayer player) {
        ZombieService.clear(player);
        player.level().getServer().getPlayerList().remove(player);
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void grantDevourUsesRealDamageHealingAndUntamedConversion(GameTestHelper test) {
        var server = test.getLevel().getServer();
        var difficulty = test.getLevel().getDifficulty();
        server.setDifficulty(net.minecraft.world.Difficulty.HARD, true);
        try {
        var player = zombie(test);
        var victim = test.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(2.5, 3, 3));
        victim.setNoGravity(true); victim.setHealth(2);
        player.setHealth(player.getMaxHealth() - 10);
        player.setAttached(ZombieAttachments.PROGRESS, ZombieService.progress(player).withBlood(100));
        test.assertTrue(ZombieService.startHeld(player, ZombieAttachments.Held.DEVOUR), "Free hand starts devour");
        float health = player.getHealth(); tick(player, 1);
        test.assertTrue(victim.isDeadOrDying() || victim.isRemoved(), "Devour damages the actual raycast target");
        test.assertTrue(player.getHealth() > health, "Devour heals its owner");
        test.assertTrue(ZombieService.progress(player).blood() > 100, "Successful drain refunds healing and gains blood");
        var zombies = player.level().getEntitiesOfClass(HungryZombieEntity.class, victim.getBoundingBox().inflate(1));
        test.assertTrue(!zombies.isEmpty(), "Hard difficulty always converts an eligible drained villager");
        test.assertTrue(zombies.stream().allMatch(z -> z.getOwnerUUID() == null), "Zombie-player creates untamed hungry zombies");
        test.assertTrue(player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(VampireAdvancements.PEOPLE_DRAINED)) >= 1,
                "Devour awards original people-drained statistic");
        test.assertTrue(player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(VampireAdvancements.ZOMBIES_CREATED)) >= 1,
                "Untamed conversion awards original zombie-created statistic");
        zombies.forEach(net.minecraft.world.entity.Entity::discard);
        close(player); victim.discard(); test.succeed();
        } finally { server.setDifficulty(difficulty, true); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void clawTimingDisguiseAndHeldCleanup(GameTestHelper test) {
        var player = zombie(test);
        var victim = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(2.5, 3, 3));
        victim.setNoGravity(true); victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); victim.setHealth(100);
        float blood = ZombieService.progress(player).blood();
        test.assertTrue(ZombieService.claw(player), "Claw starts"); tick(player, 4);
        test.assertValueEqual(victim.getHealth(), 100F, "Four ticks of windup do no damage"); tick(player, 1);
        test.assertValueEqual(victim.getHealth(), 100F - (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4, "Fifth tick hits");
        tick(player, 3);
        test.assertValueEqual(player.getAttachedOrCreate(ZombieAttachments.HUD).clawTicks(), 0, "Claw ends at eight ticks");
        test.assertTrue(ZombieService.progress(player).blood() > blood - 2, "Nominal sixty-blood claw cost is not consumed");
        test.assertTrue(ZombieService.startHeld(player, ZombieAttachments.Held.DISGUISE), "Disguise starts"); tick(player, 59);
        test.assertFalse(ZombieService.progress(player).disguised(), "Disguise needs sixty held ticks"); tick(player, 1);
        test.assertTrue(ZombieService.progress(player).disguised(), "Disguise toggles at sixty ticks");
        test.assertFalse(ZombieService.claw(player), "Disguise disables claw");
        test.assertFalse(player.hasEffect(MobEffects.STRENGTH), "Disguise suppresses strength");
        test.assertFalse(player.hasEffect(MobEffects.REGENERATION), "Zombie power never grants passive regeneration");
        test.assertTrue(ZombieService.startHeld(player, ZombieAttachments.Held.DISGUISE), "Disguise can be disabled"); tick(player, 60);
        ZombieService.startHeld(player, ZombieAttachments.Held.DEVOUR);
        for (int i = 0; i < 16; i++) ZombieService.tick(player);
        test.assertValueEqual(player.getAttachedOrCreate(ZombieAttachments.HUD).held(), ZombieAttachments.Held.NONE, "Lost heartbeat releases devour");
        ZombieService.startHeld(player, ZombieAttachments.Held.DEVOUR);
        player.hurtServer(player.level(), player.damageSources().generic(), 1);
        test.assertValueEqual(player.getAttachedOrCreate(ZombieAttachments.HUD).held(), ZombieAttachments.Held.NONE, "Incoming damage hook releases devour");
        close(player); victim.discard(); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void saveReloadCloneAndDeathPreserveOriginalLifecycle(GameTestHelper test) {
        var player = zombie(test);
        player.setAttached(ZombieAttachments.PROGRESS, ZombieService.progress(player).withBlood(321).withDisguise(true));
        var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(saved);
        ZombieService.clear(player);
        player.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), saved.buildResult()));
        test.assertTrue(ZombieService.hasZombie(player) && ZombieService.progress(player).disguised(), "Actual entity save/load retains zombie and disguise");
        test.assertValueEqual(ZombieService.progress(player).blood(), 321F, "Save/load retains blood");
        var clone = player.level().getServer().getPlayerList().respawn(player, true, net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION);
        test.assertTrue(ZombieService.hasZombie(clone), "Non-death clone keeps power");
        test.assertTrue(ZombieService.progress(clone).disguised(), "Non-death clone keeps disguise");
        clone.setInvulnerableTime(0);
        clone.hurtServer(clone.level(), clone.damageSources().genericKill(), Float.MAX_VALUE);
        var replacement = clone.level().getServer().getPlayerList().respawn(clone, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        test.assertValueEqual(ZombieService.progress(replacement), ZombieProgress.EMPTY, "Death loses power and disguise");
        close(replacement); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void devourReachFoodGlovesLeapAndDisconnectUseRealWorldHooks(GameTestHelper test) {
        var player = zombie(test);
        var victim = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(2.5, 3, 3)); victim.setNoGravity(true);
        test.setBlock(new net.minecraft.core.BlockPos(2, 3, 2), net.minecraft.world.level.block.Blocks.STONE);
        test.assertFalse(ZombieService.devour(player, victim), "Two-block devour cannot penetrate walls");
        test.setBlock(new net.minecraft.core.BlockPos(2, 3, 2), net.minecraft.world.level.block.Blocks.AIR);
        player.setAttached(ZombieAttachments.PROGRESS, ZombieService.progress(player).withBlood(100));
        player.setHealth(player.getMaxHealth() - 8);
        var apple = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE);
        apple.finishUsingItem(player.level(), player);
        test.assertValueEqual(ZombieService.progress(player).blood(), 100F, "Non-meat food adds no zombie blood");
        var beef = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF);
        float health = player.getHealth(); beef.finishUsingItem(player.level(), player);
        test.assertValueEqual(ZombieService.progress(player).blood(), 180F, "Meat adds nutrition times ten without vampire healing charge");
        test.assertValueEqual(player.getHealth(), health + 8, "Meat heals original nutrition");
        player.setAttached(ZombieAttachments.PROGRESS, ZombieService.progress(player).withBlood(1000));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(HamonItems.GLOVES));
        test.assertTrue(ZombieService.startHeld(player, ZombieAttachments.Held.DEVOUR), "Open-finger gloves count as a free main hand");
        test.assertTrue(ZombieService.claw(player), "Held devour permits concurrent claw");
        test.assertValueEqual(player.getAttachedOrCreate(ZombieAttachments.HUD).held(), ZombieAttachments.Held.DEVOUR, "Claw does not cancel source-permitted devour");
        ZombieService.stopHeld(player); tick(player, 8);
        player.setOnGround(true);
        float blood = ZombieService.progress(player).blood();
        test.assertTrue(ZombieService.leap(player), "Zombie leap is available");
        test.assertValueEqual(ZombieService.progress(player).blood(), blood, "Leap costs no blood");
        test.assertValueEqual(player.getAttachedOrCreate(ZombieAttachments.HUD).leapCooldown(), 20, "Leap has twenty-tick cooldown");
        player.setOnGround(true);
        test.assertFalse(ZombieService.leap(player), "Repeated leap cannot bypass cooldown");
        ZombieService.startHeld(player, ZombieAttachments.Held.DEVOUR);
        ZombieService.disconnect(player);
        test.assertValueEqual(player.getAttachedOrCreate(ZombieAttachments.HUD), ZombieAttachments.Hud.EMPTY, "Disconnect clears all transient state");
        test.assertTrue(ZombieService.hasZombie(player), "Disconnect preserves persistent power");
        close(player); victim.discard(); test.succeed();
    }
}
