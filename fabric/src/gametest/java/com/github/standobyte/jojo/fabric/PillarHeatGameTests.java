package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.PillarmanProgress.Mode;
import com.github.standobyte.jojo.fabric.pillarman.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;

/** Three world scenarios for source timing, real collision/fire/fluid and explosive consumption. */
public final class PillarHeatGameTests {
    private static void tickEntity(net.minecraft.world.entity.Entity entity) {
        // 26.3 advances entity age in commonTick, separately from the virtual tick body.
        entity.commonTick(); entity.tick();
    }
    private static ServerPlayer player(GameTestHelper test) {
        var player = VampireGameTests.player(test, "pillar-heat", 1.5);
        PillarmanService.grant(player); PillarmanService.setMode(player, Mode.HEAT); return player;
    }
    private static void close(ServerPlayer player) {
        PillarHeatActions.clear(player); PillarBodyParts.clear(player); PillarmanService.clear(player);
        player.level().getServer().getPlayerList().remove(player);
    }
    @GameTest(environment="jojo:vampire_night", setupTicks=5)
    public void sourceVeinTimingAndReleaseCosts(GameTestHelper test) {
        var player = player(test); float energy = PillarmanService.state(player).energy();
        test.assertTrue(PillarHeatActions.erratic(player), "HEAT mode works below stage4, as source");
        test.assertValueEqual(PillarmanService.state(player).energy(), energy - 20, "Erratic spends20 at start");
        for (int i = 0; i < 9; i++) PillarHeatActions.tick(player);
        test.assertTrue(player.level().getEntitiesOfClass(PillarVeinEntity.class, player.getBoundingBox().inflate(3)).isEmpty(), "No veins before tick10");
        PillarHeatActions.tick(player);
        var veins = player.level().getEntitiesOfClass(PillarVeinEntity.class, player.getBoundingBox().inflate(3));
        test.assertValueEqual(veins.size(), 10, "Erratic emits two five-vein fans"); veins.forEach(Entity::discard);
        for (int i = 10; i < 40; i++) PillarHeatActions.tick(player);
        test.assertFalse(PillarHeatActions.active(player), "Continuous action ends at40");
        test.assertTrue(PillarHeatActions.giant(player), "Giant charge starts");
        for (int i = 0; i < 29; i++) { PillarHeatActions.keepHeld(player); PillarHeatActions.tick(player); }
        PillarHeatActions.release(player, true);
        test.assertValueEqual(PillarmanService.state(player).energy(), energy - 20, "Early release spends nothing");
        test.assertTrue(PillarHeatActions.giant(player), "No cooldown for canceled charge");
        for (int i = 0; i < 30; i++) { PillarHeatActions.keepHeld(player); PillarHeatActions.tick(player); }
        test.assertTrue(PillarHeatActions.active(player), "Source holdType waits for release after threshold");
        PillarHeatActions.release(player, true);
        test.assertValueEqual(player.level().getEntitiesOfClass(PillarVeinEntity.class, player.getBoundingBox().inflate(3)).size(), 32, "Giant emits four eight-vein fans");
        test.assertValueEqual(PillarmanService.state(player).energy(), energy - 145, "Giant spends125 on release");
        test.assertValueEqual(AbilityCooldowns.remaining(player, PillarHeatActions.GIANT), 100, "Source cooldown100");
        test.assertValueEqual(player.getEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE).getDuration(), 600, "Source resistance600");
        close(player); test.succeed();
    }
    @GameTest(environment="jojo:vampire_night", setupTicks=5)
    public void veinsBurnRetractAndPlaceRealBlood(GameTestHelper test) {
        var player = player(test);
        var victim = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 3)); victim.setNoGravity(true);
        victim.damageCooldownTime = 17; float health = victim.getHealth();
        var vein = new PillarVeinEntity(player, 0, 0, 0, -.5, 0); player.level().addFreshEntity(vein);
        for (int i = 0; i < 8 && victim.getHealth() == health; i++) tickEntity(vein);
        test.assertTrue(victim.getHealth() < health && victim.isOnFire(), "Vein hits through immunity and burns");
        test.assertTrue(vein.retracting(), "Successful hit starts retraction");
        float hitHealth = victim.getHealth(); tickEntity(vein);
        test.assertValueEqual(victim.getHealth(), hitHealth, "Retracting vein does not hit twice");
        test.assertValueEqual(victim.damageCooldownTime, 17, "Vein preserves ordinary hit immunity");
        victim.discard(); vein.discard();
        test.setBlock(new BlockPos(2, 3, 4), Blocks.STONE);
        var terrainVein = new PillarVeinEntity(player, 0, 0, 0, -.5, 0); player.level().addFreshEntity(terrainVein);
        for (int i = 0; i < 8; i++) tickEntity(terrainVein);
        var blood = test.getBlockState(new BlockPos(2, 3, 3));
        test.assertTrue(blood.is(PillarWorldContent.BOILING_BLOOD_BLOCK) && blood.getValue(LiquidBlock.LEVEL) == 4, "Solid hit places original level4 boiling blood");
        test.setBlock(new BlockPos(3, 2, 3), Blocks.ICE);
        test.assertTrue(PillarHeatExplosion.meltIceAndSnow(test.getLevel(), test.absolutePos(new BlockPos(3, 2, 3))), "Original ice material melts");
        test.assertTrue(test.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.WATER), "Full ice becomes actual water");
        close(player); test.succeed();
    }
    @GameTest(environment="jojo:vampire_night", setupTicks=5)
    public void detonationFiltersAlliesAndEatenTntIsQuiet(GameTestHelper test) {
        var player = player(test);
        var victim = test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2, 4)); victim.setNoGravity(true);
        float before = victim.getHealth(), ownHealth = player.getHealth();
        var explosion = new PillarHeatExplosion(player); explosion.explode();
        test.assertTrue(victim.getHealth() < before, "Explosion damages eligible living target");
        test.assertValueEqual(player.getHealth(), ownHealth, "Blast excludes source; action separately applies40 self damage");
        test.assertFalse(explosion.affectedBlocks().isEmpty(), "Original rays produce actual terrain positions");
        var tnt = EntityTypes.TNT.create(player.level(), EntitySpawnReason.COMMAND); tnt.setPos(player.position()); tnt.setFuse(1); player.level().addFreshEntity(tnt);
        test.assertTrue(PillarHeatActions.eatExplosive(player, tnt, net.minecraft.world.InteractionHand.MAIN_HAND), "HEAT eats primed TNT");
        test.assertTrue(tnt.isRemoved(), "Consumed TNT no longer exists");
        PillarmanService.tick(player);
        test.assertValueEqual(player.getHealth(), ownHealth, "Swallowed fuse makes quiet sound only");
        var cart = EntityTypes.TNT_MINECART.create(player.level(), EntitySpawnReason.COMMAND);
        cart.setPos(player.position().add(0, 0, 2)); cart.setCustomName(net.minecraft.network.chat.Component.literal("Swallowed TNT cart"));
        player.level().addFreshEntity(cart); cart.primeFuse(null);
        var passenger = test.spawnWithNoFreeWill(EntityTypes.CHICKEN, new Vec3(2.5, 2, 3));
        test.assertTrue(passenger.startRiding(cart, true, true), "Passenger fixture rides primed cart");
        var cartId = cart.getUUID();
        test.assertTrue(PillarHeatActions.eatExplosive(player, cart, net.minecraft.world.InteractionHand.MAIN_HAND), "HEAT eats a primed TNT minecart");
        var replacement = player.level().getEntity(cartId);
        test.assertTrue(cart.isRemoved() && replacement != null && replacement.getType() == EntityTypes.MINECART, "Converted minecart preserves UUID and remains in the real world");
        test.assertValueEqual(replacement.getCustomName(), cart.getCustomName(), "Replacement retains saved cart state");
        test.assertTrue(passenger.getVehicle() == replacement, "Replacement retains existing passengers");
        passenger.discard(); replacement.discard();
        victim.discard(); close(player); test.succeed();
    }
}
