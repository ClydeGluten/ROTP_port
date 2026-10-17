package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.pillarman.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real segment collision, attachment serialization and lifecycle tests; root owns the only test runner. */
public final class PillarBodyPartGameTests {
    private static void tickEntity(net.minecraft.world.entity.Entity entity) {
        // 26.3 advances entity age in commonTick, separately from the virtual tick body.
        entity.commonTick(); entity.tick();
    }
    private static ServerPlayer player(GameTestHelper test) {
        var player = VampireGameTests.player(test, "pillar-body", 1.5);
        PillarmanService.grant(player); PillarmanService.setStage(player, 2); return player;
    }
    private static void close(ServerPlayer player) {
        PillarBodyParts.clear(player); PillarmanService.clear(player); player.level().getServer().getPlayerList().remove(player);
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void hornDamagesThroughImmunityAndRetracts(GameTestHelper test) {
        var player = player(test);
        var victim = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3)); victim.setNoGravity(true);
        float energy = PillarmanService.state(player).energy(), health = victim.getHealth();
        test.assertTrue(PillarBodyParts.horn(player), "Stage2 horn action spawns");
        test.assertValueEqual(PillarmanService.state(player).energy(), energy - 15, "Horn spends15");
        test.assertFalse(PillarBodyParts.horn(player), "Horn cooldown rejects duplicate input");
        var horn = player.level().getEntitiesOfClass(PillarHornEntity.class, player.getBoundingBox().inflate(2)).getFirst();
        victim.damageCooldownTime = 17; Vec3 before = victim.getDeltaMovement();
        for (int i = 0; i < 10; i++) tickEntity(horn);
        test.assertTrue(victim.getHealth() <= health - 1, "Horn segment repeatedly hits through ordinary invulnerability");
        test.assertValueEqual(victim.damageCooldownTime, 17, "Other damage immunity is preserved");
        test.assertValueEqual(victim.getDeltaMovement(), before, "Horn adds zero knockback");
        float extended = horn.distance();
        for (int limit=0; limit<40 && !horn.isRemoved() && horn.tickCount < 34; limit++) tickEntity(horn);
        test.assertTrue(horn.retracting(), "Horn reverses after source extension plus four-tick pause");
        test.assertTrue(horn.distance() < 10.9F && extended > 0, "Retraction reduces extension");
        for (int i = 0; i < 12 && !horn.isRemoved(); i++) tickEntity(horn);
        test.assertTrue(horn.isRemoved(), "Horn retracts fully within source lifetime");
        victim.discard(); close(player); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void terrainHardnessAndOwnerLossEndBodyParts(GameTestHelper test) {
        var player = player(test);
        boolean previousBreaking = test.getLevel().getGameRules().get(JojoGameRules.BREAK_BLOCKS);
        test.getLevel().getGameRules().set(JojoGameRules.BREAK_BLOCKS, true, test.getLevel().getServer());
        test.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
        var horn = new PillarHornEntity(player); player.level().addFreshEntity(horn);
        for (int i = 0; i < 8; i++) tickEntity(horn);
        test.assertTrue(test.getBlockState(new BlockPos(2, 3, 3)).isAir(), "Horn breaks source-eligible stone terrain");
        horn.discard(); test.setBlock(new BlockPos(2, 3, 3), Blocks.OBSIDIAN);
        var blocked = new PillarHornEntity(player); player.level().addFreshEntity(blocked);
        for (int i = 0; i < 8 && !blocked.isRemoved(); i++) tickEntity(blocked);
        test.assertTrue(test.getBlockState(new BlockPos(2, 3, 3)).is(Blocks.OBSIDIAN), "Hardness above5 remains intact");
        test.assertTrue(blocked.retracting(), "Unbreakable terrain retracts horn");
        blocked.discard();
        var orphan = new PillarHornEntity(player); player.level().addFreshEntity(orphan);
        var origin = player.position(); var originalLevel = player.level();
        var nether = originalLevel.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        test.assertTrue(nether != null, "Dimension fixture exists");
        player.teleportTo(nether, 0, 100, 0, java.util.Set.of(), 0, 0, true); tickEntity(orphan);
        test.assertTrue(orphan.isRemoved(), "Owner changing dimensions removes orphan segment");
        player.teleportTo(originalLevel, origin.x, origin.y, origin.z, java.util.Set.of(), 0, 0, true);
        var dyingOwnerPart = new PillarHornEntity(player); player.level().addFreshEntity(dyingOwnerPart);
        player.setHealth(0); tickEntity(dyingOwnerPart);
        test.assertTrue(dyingOwnerPart.isRemoved(), "Owner death removes body part before further damage");
        test.getLevel().getGameRules().set(JojoGameRules.BREAK_BLOCKS, previousBreaking, test.getLevel().getServer());
        close(player); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void ribsAttachOnlyOnceAndPersistTarget(GameTestHelper test) {
        var player = player(test);
        var victim = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3)); victim.setNoGravity(true); victim.setNoAi(false);
        float energy = PillarmanService.state(player).energy();
        test.assertTrue(PillarBodyParts.ribs(player), "Ribs action starts");
        var ribs = player.level().getEntitiesOfClass(PillarRibsEntity.class, player.getBoundingBox().inflate(2));
        test.assertValueEqual(ribs.size(), 8, "Source emits eight ribs");
        test.assertValueEqual(PillarmanService.state(player).energy(), energy - 60, "Ribs spend60");
        var rib = ribs.getFirst(); for (int i = 1; i < ribs.size(); i++) ribs.get(i).discard();
        for (int i = 0; i < 12 && !rib.attachedToEntity(); i++) tickEntity(rib);
        test.assertTrue(rib.attachedEntity() == victim, "First successful unblocked hit binds victim");
        test.assertValueEqual(rib.ticksLifespan(), 41, "Attachment adds source twenty-tick lifetime");
        test.assertValueEqual(victim.getEffect(VampireCombatService.STUN).getDuration(), 41 - rib.tickCount, "Stun spans all remaining attached lifetime");
        float health = victim.getHealth(); tickEntity(rib); tickEntity(rib);
        test.assertValueEqual(victim.getHealth(), health, "Attached rib never deals repeated damage");
        var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess());
        rib.saveWithoutId(saved); rib.discard();
        var restored = new PillarRibsEntity(PillarBodyParts.RIBS, player.level());
        restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), saved.buildResult()));
        player.level().addFreshEntity(restored); tickEntity(restored);
        test.assertTrue(restored.attachedEntity() == victim, "Real entity save/load restores attachment UUID");
        test.assertValueEqual(victim.getHealth(), health, "Reload cannot reset one-hit flag");
        for (int limit=0; limit<50 && !restored.isRemoved() && restored.tickCount <= 43; limit++) tickEntity(restored);
        test.assertTrue(restored.isRemoved(), "Attached rib ends after extended lifetime");
        victim.discard(); close(player); test.succeed();
    }
}
