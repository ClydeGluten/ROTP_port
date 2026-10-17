package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.stand.hierophant.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Real registered intents and world collisions; root enables HG only with its complete presentation delivery. */
public final class HierophantGreenGameTests {
    private static ServerPlayer player(GameTestHelper test, int resolve) {
        var player = VampireGameTests.player(test, "hierophant-test", 1.5);
        player.snapTo(test.absoluteVec(new Vec3(2.5, 1, 1.5)), 0, 0);
        StandService.grant(player, HierophantGreenActions.STAND_ID);
        ProgressionService.update(player, ProgressionService.state(player).withLevel(resolve));
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(HierophantGreenActions.STAND_ID, 1500, 0, true));
        test.assertTrue(StandService.toggle(player), "Actual HG factory summons the integrated definition");
        var stand = stand(player);
        for (int i = 0; i < 20 && stand.summonLockTicks() > 0; i++) tick(stand);
        return player;
    }
    private static HierophantGreenEntity stand(ServerPlayer player) { return (HierophantGreenEntity) StandService.currentManifestation(player); }
    private static void tick(Entity entity) { entity.commonTick(); entity.tick(); }
    private static void close(ServerPlayer player) { StandService.dismiss(player); player.level().getServer().getPlayerList().remove(player); }
    @GameTest(environment="jojo:vampire_night", setupTicks=5)
    public void registeredStringsBindTrackTheirOwnerHitWallsAndDismiss(GameTestHelper test) {
        var player = player(test, 0); var stand = stand(player);
        try {
            test.assertTrue(stand.startControl(), "Remote control creates source owner tether");
            stand.setPos(test.absoluteVec(new Vec3(2.5, 1, 1.5)));
            var target = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1, 5)); target.setNoGravity(true);
            test.assertTrue(HierophantGreenActions.dispatch(player, HierophantGreenActions.BIND), "Registered shifted binding action starts");
            tick(stand);
            var strings = test.getLevel().getEntitiesOfClass(HGStringEntity.class, stand.getBoundingBox().inflate(12), e -> e.livingOwner() == stand);
            test.assertValueEqual(strings.size(), 4, "Three ring strings plus center for binding");
            test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 1425F, "Task pays75 exactly once on PERFORM");
            test.assertValueEqual(AbilityCooldowns.remaining(player, HierophantGreenActions.BIND), 125, "Binding source25+100 cooldown");
            for (int i = 0; i < 12 && strings.stream().noneMatch(s -> s.attachedEntity() == target); i++) for (var string : strings) if (!string.isRemoved()) tick(string);
            test.assertTrue(strings.stream().anyMatch(s -> s.attachedEntity() == target), "Actual segment collision attaches a binding string");
            test.assertTrue(target.hasEffect(HamonEffects.IMMOBILIZE), "Binding applies inherited immobilization");
            var attached = strings.stream().filter(s -> s.attachedEntity() == target).findFirst().orElseThrow();
            Vec3 oldOrigin = attached.origin(1); stand.setPos(stand.position().add(1, 0, 0)); tick(attached);
            test.assertTrue(attached.origin(1).distanceToSqr(oldOrigin) > .9, "Body origin follows the moving actual Stand");
            test.assertTrue(attached.position().distanceToSqr(target.position().add(0, target.getBbHeight() * .5, 0)) < .01, "Attached end remains on target");
            target.discard(); strings.forEach(Entity::discard);
            for (int i = 0; i < 30 && stand.task() != HierophantGreenEntity.Task.NONE; i++) tick(stand);
            test.assertTrue(HierophantGreenActions.dispatch(player, HierophantGreenActions.ATTACK), "Registered normal string action starts");
            tick(stand);
            var normal = test.getLevel().getEntitiesOfClass(HGStringEntity.class, stand.getBoundingBox().inflate(12), e -> e.livingOwner() == stand);
            test.assertValueEqual(normal.size(), 8, "Seven ring strings plus center for normal attack");
            normal.forEach(Entity::discard);
            var wallPos = new BlockPos(4, 2, 4); test.setBlock(wallPos, Blocks.STONE);
            stand.setPos(test.absoluteVec(new Vec3(4.5, 1.6, 2.5))); stand.setYRot(0); stand.setXRot(0);
            var wallString = new HGStringEntity(stand, 0, 0, false, 16); stand.addBodyProjectile(wallString);
            for (int i = 0; i < 8 && !wallString.retracting(); i++) tick(wallString);
            test.assertTrue(wallString.retracting(), "Unbreakable wall retracts owner-bound string");
            test.assertTrue(test.getBlockState(wallPos).is(Blocks.STONE), "String hardness0 does not break stone");
            StandService.dismiss(player);
            test.assertTrue(wallString.isRemoved(), "Dismissal immediately cleans owned bodies");
            test.assertTrue(test.getLevel().getEntitiesOfClass(HGBarrierEntity.class, player.getBoundingBox().inflate(110), b -> b.livingOwner() == stand).isEmpty(), "Dismissal removes remote tether");
            StandParts.remove(player, StandParts.Part.ARMS);
            test.assertFalse(HierophantGreenActions.dispatch(player, HierophantGreenActions.BLOCK), "Missing arms rejects actual auto-summon action");
            test.assertTrue(StandService.currentManifestation(player) == null, "Rejected provisional summon is rolled back");
            StandParts.add(player, StandParts.Part.ARMS);
            test.assertTrue(HierophantGreenActions.dispatch(player, HierophantGreenActions.BLOCK), "Guard auto-summons its original arms mode");
            var arms = stand(player);
            test.assertTrue(arms.isArmsOnlyMode() && arms.summonLockTicks() == 0, "Arms summon skips full-body lock");
            test.assertValueEqual(arms.attackDamage(), 6D, "Arms retain source25percent power penalty");
            StandActionRegistry.dispatch(player, HierophantGreenActions.BLOCK, StandActionRegistry.Phase.RELEASE, false, StandActionRegistry.Extra.EMPTY);
            for (int i = 0; i < 16 && !arms.isRemoved(); i++) tick(arms);
            test.assertTrue(arms.isRemoved(), "Released idle arms fade and unsummon automatically");
            test.succeed();
        } finally { close(player); }
    }
    @GameTest(environment="jojo:vampire_night", setupTicks=5)
    public void emeraldHitTrainingUnlocksConcentrationAndPersistsWithCooldown(GameTestHelper test) {
        var player = player(test, 3); var stand = stand(player);
        try {
            StandActionRegistry.unlockAvailable(player);
            test.assertFalse(HierophantGreenActions.dispatch(player, HierophantGreenActions.CONCENTRATED), "Resolve alone never unlocks concentrated splash");
            StandActionTraining.set(player, HierophantGreenActions.SPLASH, .999F);
            var target = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(3.5, 1, 5.5)); target.setNoGravity(true);
            var emerald = new HGEmeraldEntity(stand, true); emerald.setPos(test.absoluteVec(new Vec3(3.5, 2, 4))); emerald.setDeltaMovement(0, 0, 2); emerald.setDamageFactor(1);
            test.getLevel().addFreshEntity(emerald); target.damageCooldownTime = 20; tick(emerald);
            test.assertTrue(emerald.isRemoved() && target.getHealth() < target.getMaxHealth(), "Actual emerald collides and bypasses previous hit immunity");
            test.assertValueEqual(target.damageCooldownTime, 20, "Emerald preserves immunity for unrelated attacks");
            test.assertValueEqual(StandActionTraining.points(player, HierophantGreenActions.SPLASH, 1), 1F, "Successful eligible hit adds0.002 source learning");
            test.assertTrue(StandActionTraining.unlocked(player, HierophantGreenActions.CONCENTRATED), "Maximum splash learning unlocks its actual shift action");
            target.discard();
            test.assertTrue(HierophantGreenActions.dispatch(player, HierophantGreenActions.CONCENTRATED), "Trained concentrated action starts");
            for (int i = 0; i < 5; i++) tick(stand);
            test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 1470F, "Five perform ticks consume6 each without generic double charge");
            var projectiles = test.getLevel().getEntitiesOfClass(HGEmeraldEntity.class, stand.getBoundingBox().inflate(8), e -> e.getOwner() == stand);
            test.assertTrue(projectiles.size() >= 10 && projectiles.size() <= 15, "Concentrated2.5 fire rate makes two or three per tick at source base speed");
            test.assertTrue(projectiles.stream().allMatch(e -> e.breaksBlocks() && Math.abs(e.getDeltaMovement().length() - 3) < .1), "Concentration carries source block-breaking and velocity3");
            var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess()); player.saveWithoutId(out);
            StandActionTraining.clear(player); AbilityCooldowns.reset(player);
            player.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), out.buildResult()));
            test.assertTrue(StandActionTraining.unlocked(player, HierophantGreenActions.CONCENTRATED), "Actual player reload retains unlocked concentration");
            test.assertValueEqual(AbilityCooldowns.remaining(player, HierophantGreenActions.CONCENTRATED), 65, "Reload retains source5+60 cooldown");
            test.succeed();
        } finally { close(player); }
    }
    @GameTest(environment="jojo:vampire_night", setupTicks=5)
    public void barrierNetworkFundsShotsRipsAndGrappleReleaseCleansAttachments(GameTestHelper test) {
        var player = player(test, 3); var stand = stand(player);
        try {
            var first = new BlockPos(1, 3, 3); var second = new BlockPos(5, 3, 3);
            test.setBlock(first, Blocks.STONE); test.setBlock(second, Blocks.STONE);
            test.assertTrue(stand.attachBarrier(test.absolutePos(first)), "First anchor starts a connected string");
            test.assertValueEqual(stand.getPlacedBarriersCount(), 0, "Loose first anchor is not a placed segment");
            test.assertTrue(stand.attachBarrier(test.absolutePos(second)), "Second anchor completes the first barrier");
            test.assertValueEqual(stand.getPlacedBarriersCount(), 1, "Completed barrier enters graph");
            stand.getBarriersNet().tick();
            player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(0));
            test.assertValueEqual(stand.getBarriersNet().shootEmeraldsFromBarriers(stand, test.absoluteVec(new Vec3(3.5, 3.5, 7)), 0, 20, 3, 8, true), 0, "No stamina yields no barrier emeralds");
            stand.getBarriersNet().tick(); player.setAttached(StandAttachments.PROGRESS, player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(1500));
            test.assertValueEqual(stand.getBarriersNet().shootEmeraldsFromBarriers(stand, test.absoluteVec(new Vec3(3.5, 3.5, 7)), 0, 20, 3, 8, true), 1, "Short segment emits one funded point batch");
            test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 1497F, "Source spends3 per selected firing point");
            var barrier = stand.getBarriersNet().barriers().getFirst();
            tick(barrier); // OwnerBoundProjectile moves a freshly attached endpoint on its next tick.
            var crossing = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(3.5, 2.5, 3.5)); crossing.setNoGravity(true);
            stand.getBarriersNet().tick(); tick(barrier);
            test.assertTrue(barrier.wasRipped() && barrier.wasRippedAt().isPresent(), "World crossing records source rip point");
            float health = player.getHealth(); tick(barrier);
            test.assertTrue(player.getHealth() < health, "Rip transfers source0.2 owner damage once");
            stand.getBarriersNet().tick(); test.assertValueEqual(stand.getBarriersNet().getSize(), 0, "Ripped segment is pruned from firing graph");
            crossing.discard();
            stand.startControl(); stand.setPos(test.absoluteVec(new Vec3(3.5, 1.8, 1.5))); stand.setYRot(0); stand.setXRot(0);
            for (int y = 2; y <= 4; y++) test.setBlock(new BlockPos(3, y, 6), Blocks.STONE);
            test.assertTrue(HierophantGreenActions.dispatch(player, HierophantGreenActions.GRAPPLE), "Registered held grapple starts"); tick(stand);
            var grapple = test.getLevel().getEntitiesOfClass(HGGrapplingStringEntity.class, stand.getBoundingBox().inflate(4)).getFirst();
            for (int i = 0; i < 3 && grapple.attachedBlock().isEmpty(); i++) tick(grapple);
            test.assertTrue(grapple.attachedBlock().isPresent(), "Actual grapple attaches to block collision");
            test.assertTrue(stand.getDeltaMovement().length() > 1.9, "Attached block grapple pulls remote Stand at source speed2");
            StandActionRegistry.dispatch(player, HierophantGreenActions.GRAPPLE, StandActionRegistry.Phase.RELEASE, false, StandActionRegistry.Extra.EMPTY);
            test.assertTrue(grapple.isRemoved(), "Matching authoritative release removes grapple");
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
            test.assertFalse(barrier.save(output), "Source noSave flag keeps transient barrier out of world saves");
            test.succeed();
        } finally { close(player); }
    }
}
