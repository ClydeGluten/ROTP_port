package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.stand.crazydiamond.*;
import com.github.standobyte.jojo.fabric.stand.restoration.*;
import com.github.standobyte.jojo.fabric.stand.tracking.StandEffectStore;
import com.github.standobyte.jojo.fabric.pillarman.PossessionService;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import static com.github.standobyte.jojo.fabric.CrazyDiamondRestorationGameTests.*;

/** Grouped world cases use the actual registered CD manifestation/actions and saved entrapment entity. */
public final class CrazyDiamondCombatGameTests {
    private static void runTask(CrazyDiamondEntity stand, int ticks) { for (int i = 0; i < ticks && stand.isAlive(); i++) stand.tick(); }
    @GameTest public void heldHealingReversesDeathWindowBleedingAndOwnerLink(GameTestHelper test) {
        var player = player(test);
        try {
            ProgressionService.setLevel(player, 1);
            var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND); cow.setNoAi(true); cow.setNoGravity(true);
            cow.setPos(test.absoluteVec(new Vec3(2.5, 2, 4))); cow.setHealth(2); player.level().addFreshEntity(cow);
            cow.addEffect(new MobEffectInstance(VampireBlood.BLEEDING, 100, 1)); aim(player, cow.getBoundingBox().getCenter());
            test.assertTrue(press(player, CrazyDiamondCombatActions.HEAL), "Source healing action accepts another living target");
            ticks(player, CrazyDiamondCombatActions.HEAL, 20);
            test.assertTrue(cow.getHealth() > 2 && (!cow.hasEffect(VampireBlood.BLEEDING) || cow.getEffect(VampireBlood.BLEEDING).getAmplifier() == 0), "Held ticks heal and reduce bleeding");
            cow.setHealth(0); cow.deathTime = 14; ticks(player, CrazyDiamondCombatActions.HEAL, 7);
            test.assertTrue(cow.getHealth() > 0 && cow.deathTime == 0, "Active 14-tick death window rewinds by two and revives");
            cow.setHealth(0); cow.deathTime = 16; ticks(player, CrazyDiamondCombatActions.HEAL, 1);
            test.assertTrue(cow.getHealth() == 0 && cow.deathTime == 16, "Past15 death ticks cannot be revived");
            stop(player, CrazyDiamondCombatActions.HEAL); cow.discard();
            var stand = (CrazyDiamondEntity) StandService.currentManifestation(player); player.setHealth(5);
            test.assertFalse(CrazyDiamondHealing.validTarget(player, player), "Direct user healing is excluded");
            test.assertTrue(CrazyDiamondHealing.heal(stand, 1, new CrazyDiamondHealing.Session()) && player.getHealth() == 5.5F,
                    "Healing the actual manifestation follows its owner link");
        } finally { close(player); }
        test.succeed();
    }
    @GameTest public void blockProjectileConsumesSourceMaterialAndSelectsDriedBlood(GameTestHelper test) {
        var player = player(test);
        try {
            ProgressionService.setLevel(player, 4); player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GLASS, 2));
            var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND); cow.setNoAi(true); cow.setNoGravity(true); cow.setPos(test.absoluteVec(new Vec3(4, 2, 7))); player.level().addFreshEntity(cow);
            StandEffectStore.getOrCreate(player, CDRecoveryModifiers.DRIED_BLOOD, cow, CompoundTag::new);
            player.setXRot(0); player.setYRot(0); test.assertTrue(press(player, CrazyDiamondCombatActions.BLOCK_BULLET), "Original hard-material offhand shot starts");
            var stand = (CrazyDiamondEntity) StandService.currentManifestation(player); runTask(stand, 25);
            var bullets = player.level().getEntitiesOfClass(CDBlockBulletEntity.class, player.getBoundingBox().inflate(10));
            test.assertTrue(player.getOffhandItem().getCount() == 1 && bullets.size() == 1, "One completed shot consumes exactly one source block");
            var bullet = bullets.getFirst(); test.assertTrue(bullet.block() == Blocks.GLASS && bullet.homingTarget() == cow, "Shot keeps source block and owner-scoped dried-blood target");
            bullet.setPos(cow.position().add(0, .5, -1)); bullet.setDeltaMovement(0, 0, 2); float health = cow.getHealth(); bullet.tick();
            test.assertTrue(cow.getHealth() < health && bullet.isRemoved(), "Actual physical projectile collision hurts and removes the projectile");
            cow.discard();
        } finally { close(player); }
        test.succeed();
    }
    @GameTest public void heldObjectsAnchorsAndSavedAngeloPossessionKeepTheirSourceOwnership(GameTestHelper test) {
        var player = player(test); var victim = player(test);
        try {
            ProgressionService.setLevel(player, 3); player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SNOWBALL, 2));
            player.setXRot(-90); test.assertTrue(press(player, CrazyDiamondCombatActions.HEAVY), "Heavy task takes one usable object");
            var stand = (CrazyDiamondEntity) StandService.currentManifestation(player);
            test.assertTrue(stand.getMainHandItem().is(Items.SNOWBALL) && player.getOffhandItem().getCount() == 1, "Real Stand hand owns the split object");
            StandService.dismiss(player); test.assertTrue(player.getInventory().countItem(Items.SNOWBALL) == 2, "Task removal returns held object once");
            var anchorPos = test.absolutePos(new BlockPos(2, 2, 4)); player.level().setBlockAndUpdate(anchorPos, Blocks.STONE.defaultBlockState());
            aim(player, Vec3.atCenterOf(anchorPos)); test.assertTrue(press(player, CrazyDiamondCombatActions.ANCHOR_MAKE), "Anchor action starts against an eligible source block");
            stand = (CrazyDiamondEntity) StandService.currentManifestation(player); runTask(stand, 25);
            ItemStack anchor = ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) { var item = player.getInventory().getItem(i); if (CDAnchorItem.destination(player.level(), item).isPresent()) { anchor = item; player.getInventory().setItem(i, ItemStack.EMPTY); break; } }
            test.assertTrue(!anchor.isEmpty() && player.level().getBlockState(anchorPos).isAir(), "Anchor exists on the actual generated block drop");
            player.setItemInHand(InteractionHand.OFF_HAND, anchor); CDAnchorItem.move(player);
            test.assertTrue(player.getOffhandItem().isEmpty() && player.level().getBlockState(anchorPos).is(Blocks.STONE), "Nearby anchor restores source state and consumes the token");
            var lower = test.absolutePos(new BlockPos(5, 2, 5));
            var rock = AngeloRockEntity.form(player.level(), victim,
                    new BrokenBlockRecord(lower, Blocks.STONE.defaultBlockState(), List.of(), false),
                    new BrokenBlockRecord(lower.above(), Blocks.GRANITE.defaultBlockState(), List.of(), false), true);
            for (int i = 0; i < 40; i++) rock.tick();
            test.assertTrue(PossessionService.host(victim) == rock && !PossessionService.state(victim).asAlive(), "Completed rock owns the shared non-exitable possession");
            test.assertFalse(PossessionService.requestExit(victim), "Shift cannot escape Angelo");
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess()); rock.saveWithoutId(output);
            var loaded = new AngeloRockEntity(CrazyDiamondCombatActions.ANGELO_ENTITY, player.level()); loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), output.buildResult()));
            test.assertTrue(loaded.isFullyFormed() && loaded.lowerBlock().is(Blocks.STONE) && loaded.upperBlock().is(Blocks.GRANITE), "Saved entrapment keeps both original block appearances");
            PossessionService.onDisconnect(victim); test.assertTrue(PossessionService.state(victim) != null, "Logout retains Angelo session state");
            PossessionService.tick(victim); test.assertTrue(PossessionService.host(victim) == rock, "Rejoin lifecycle resolves the same saved host");
            rock.breakRock(); PossessionService.tick(victim);
            test.assertTrue(!victim.isAlive() && !PossessionService.isPossessing(victim), "Breaking the host ends possession and kills its trapped user");
            loaded.discard();
        } finally { close(victim); close(player); }
        test.succeed();
    }
}
