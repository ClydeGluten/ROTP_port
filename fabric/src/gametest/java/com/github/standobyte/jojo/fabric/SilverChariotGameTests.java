package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.stand.silverchariot.*;
import com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedFireBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Two grouped world contracts; presentation/input acceptance is owned by38 and the root client lane. */
public final class SilverChariotGameTests {
    private static ServerPlayer player(GameTestHelper test) {
        var player = VampireGameTests.player(test, "silver-chariot", 1.5);
        var progress = new StandProgress(StandProgress.SILVER_CHARIOT, 0, 0, true);
        player.setAttached(StandAttachments.PROGRESS, progress.withStamina(progress.maxStamina())); ProgressionService.setLevel(player, 4); return player;
    }
    private static SilverChariotEntity stand(ServerPlayer player) {
        var stand = new SilverChariotEntity(SilverChariotActions.STAND, player.level()); stand.setOwner(player); player.level().addFreshEntity(stand);
        for (int i = 0; i < 3; i++) tick(stand); return stand;
    }
    private static void tick(net.minecraft.world.entity.Entity entity) { entity.commonTick(); entity.tick(); }
    private static void close(ServerPlayer player, SilverChariotEntity stand) { stand.discard(); player.level().getServer().getPlayerList().remove(player); }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void rapierLaunchRicochetExactOwnerRecoveryAndRemoval(GameTestHelper test) {
        var player = player(test); var stand = stand(player); var other = stand(player);
        SilverChariotRapierEntity rapier = null;
        try {
            other.prepareActionSummon(SilverChariotEntity.Kind.LIGHT, true);
            test.assertValueEqual(other.summonMode(), 7, "Light auto-summons main arm with original source bits");
            test.assertValueEqual(other.attackDamage(), 6.75D, "Partial arms reduce fully developed damage25 percent");
            test.assertValueEqual(other.precision(), 0D, "Partial arms have no precision");
            other.prepareActionSummon(SilverChariotEntity.Kind.BLOCK, false);
            test.assertValueEqual(other.summonMode(), 15, "Block adds second arm");
            other.prepareActionSummon(SilverChariotEntity.Kind.RAPIER_LAUNCH, false);
            test.assertFalse(other.isArmsOnlyMode(), "Full action upgrades partial summon");
            test.assertValueEqual(other.attackDamage(), 9D, "Fully developed speed makes source partial-arm transition weakness zero ticks");
            for (int i = 0; i < 3; i++) tick(other);
            test.assertValueEqual(other.attackDamage(), 9D, "Full transition keeps fully developed damage");
            other.setPos(test.absoluteVec(new Vec3(6, 1, 6)));
            test.assertTrue(stand.start(SilverChariotEntity.Kind.RAPIER_LAUNCH), "Source launch starts"); tick(stand);
            test.assertFalse(stand.hasRapier(), "Noncreative launch removes rapier");
            rapier = player.level().getEntitiesOfClass(SilverChariotRapierEntity.class, stand.getBoundingBox().inflate(8)).getFirst();
            test.assertValueEqual(rapier.getOwner(), stand, "Projectile owns exact manifestation");
            test.assertValueEqual(rapier.damageAmount(), 12F, "No-rapier modifier does not change base-attribute projectile damage");
            test.assertValueEqual(AbilityCooldowns.remaining(player, SilverChariotActions.LAUNCH), 100, "Launch cooldown100");
            test.assertFalse(SilverChariotActions.RAPIER.canSerialize(), "Source rapier noSave registration");
            test.assertFalse(SilverChariotActions.RAPIER.canSummon(), "Source rapier noSummon registration");
            test.setBlock(new BlockPos(4, 4, 3), Blocks.STONE); test.setBlock(new BlockPos(0, 4, 3), Blocks.STONE);
            rapier.setPos(test.absoluteVec(new Vec3(2.5, 4.5, 3.5))); rapier.setDeltaMovement(2, 0, 0); tick(rapier);
            test.assertValueEqual(rapier.ricochetCount(), 1, "Bounded reflected ray ricochets");
            test.assertTrue(rapier.getDeltaMovement().x < 0, "Hit X face reflects X velocity");
            test.assertValueEqual(rapier.damageAmount(), 12F, "Dormant getDamageFinalCalc bonus stays dormant");
            test.assertTrue(rapier.leftOwner(), "Moving clear of original owner enables recovery");
            rapier.takeRapier(other); test.assertTrue(rapier.isAlive(), "Fresh manifestation cannot claim old rapier");
            rapier.takeRapier(stand); test.assertTrue(stand.hasRapier() && rapier.isRemoved(), "Exact living owner recovers rapier");
            test.assertValueEqual(AbilityCooldowns.remaining(player, SilverChariotActions.LAUNCH), 0, "Recovery clears unlocked launch cooldown");
            rapier = new SilverChariotRapierEntity(stand); player.level().addFreshEntity(rapier); stand.discard(); tick(rapier);
            test.assertTrue(rapier.isRemoved(), "Loaded removed owner removes projectile");
            test.assertTrue(other.hasRapier(), "Fresh summon keeps original default rapier"); test.succeed();
        } finally { if (rapier != null) rapier.discard(); other.discard(); close(player, stand); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void armorAttackBarrageFieryDeflectionAndSweep(GameTestHelper test) {
        var player = player(test); var stand = stand(player); SilverChariotFlameSwingEntity flame = null;
        var victim = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3.5)); victim.setNoGravity(true);
        try {
            test.assertTrue(stand.start(SilverChariotEntity.Kind.REMOVE_ARMOR), "Armor action starts"); tick(stand);
            test.assertFalse(stand.hasArmor(), "Armor removed");
            test.assertTrue(Math.abs(stand.getAttributeValue(Attributes.MOVEMENT_SPEED) - 2.4375) < .0001, "Armor-off movement adds150 percent after development addition");
            test.assertValueEqual(stand.attackSpeed(), 26.25D, "Armor-off attack speed adds50 percent after development addition");
            test.assertValueEqual(stand.durability(), 0D, "Armor-off has zero durability");
            test.assertValueEqual(stand.getArmorValue(), 0, "Armor-off has zero armor");
            stand.setRapier(false);
            test.assertValueEqual(stand.attackDamage(), 6.75D, "No-rapier damage is75 percent of developed power");
            test.assertValueEqual(stand.attackSpeed(), 17.5D, "Source additive base speed modifiers combine after development addition");
            test.assertValueEqual(stand.attackRange(), 2.5D, "No-rapier reach falls by one");
            test.assertFalse(stand.start(SilverChariotEntity.Kind.BARRAGE), "No rapier forbids barrage"); stand.setRapier(true);
            float health = victim.getHealth(); test.assertTrue(stand.start(SilverChariotEntity.Kind.LIGHT), "Armed light attack starts");
            for (int i = 0; i < 20; i++) tick(stand);
            test.assertTrue(victim.getHealth() < health, "Light attack damages aimed world entity");
            test.assertTrue(stand.finisherCharge() >= .19F, "Perform and entity punch both add finisher");
            test.assertTrue(stand.start(SilverChariotEntity.Kind.BARRAGE), "Unarmored barrage starts");
            test.assertValueEqual(stand.actionDuration(), Integer.MAX_VALUE, "Armor-off overrides finite barrage duration"); stand.stopHeld();
            for (int i = 0; i < 10; i++) tick(stand);
            flame = new SilverChariotFlameSwingEntity(stand); player.level().addFreshEntity(flame);
            test.assertTrue(stand.deflectProjectile(flame), "Resolve4 deflects fiery source projectile");
            test.assertTrue(stand.isRapierOnFire(), "Fiery deflection burns rapier"); flame.discard();
            test.assertTrue(stand.start(SilverChariotEntity.Kind.SWEEP), "Fiery sweep starts");
            for (int i = 0; i < 20 && stand.isRapierOnFire(); i++) tick(stand);
            test.assertFalse(stand.isRapierOnFire(), "Fiery sweep consumes blade fire");
            flame = player.level().getEntitiesOfClass(SilverChariotFlameSwingEntity.class, stand.getBoundingBox().inflate(8)).getFirst();
            test.assertFalse(flame.canBeEvaded(victim), "Source flame cannot be evaded");
            test.setBlock(new BlockPos(3, 4, 3), Blocks.ICE);
            test.assertTrue(SilverChariotFlameSwingEntity.meltIceAndSnow(player.level(), Blocks.ICE.defaultBlockState(), test.absolutePos(new BlockPos(3, 4, 3))), "Ice melts");
            test.assertTrue(test.getBlockState(new BlockPos(3, 4, 3)).is(Blocks.WATER), "Solid ice becomes water outside ultrawarm dimension");
            test.assertTrue(MagiciansRedFireBlock.BLOCK.stateWithAge(player.level(), test.absolutePos(new BlockPos(3, 4, 3)), 7).is(MagiciansRedFireBlock.BLOCK), "Fire age retains custom block");
            flame.clearFire(); test.assertTrue(flame.isRemoved(), "Extinguishing removes Stand flame"); test.succeed();
        } finally { if (flame != null) flame.discard(); victim.discard(); close(player, stand); }
    }
}
