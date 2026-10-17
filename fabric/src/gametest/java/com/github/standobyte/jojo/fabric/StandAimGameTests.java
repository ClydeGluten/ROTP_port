package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** User report: both manifestations and their actual damage must follow vertical aim. */
public final class StandAimGameTests {
    @GameTest public void starPlatinumLightAboveAndBelow(GameTestHelper test) { exercise(test, false, 0); }
    @GameTest public void starPlatinumHeavyAboveAndBelow(GameTestHelper test) { exercise(test, false, 1); }
    @GameTest public void starPlatinumBarrageAboveAndBelow(GameTestHelper test) { exercise(test, false, 2); }
    @GameTest public void theWorldLightAboveAndBelow(GameTestHelper test) { exercise(test, true, 0); }
    @GameTest public void theWorldHeavyAboveAndBelow(GameTestHelper test) { exercise(test, true, 1); }
    @GameTest public void theWorldBarrageAboveAndBelow(GameTestHelper test) { exercise(test, true, 2); }

    private static void exercise(GameTestHelper test, boolean world, int attack) {
        var player = ProgressionGameTests.player(test);
        player.setGameMode(GameType.CREATIVE);
        StandService.dismiss(player);
        if (world) StandService.grant(player, StandProgress.THE_WORLD);
        for (float pitch : new float[] {-60, 60}) {
            player.snapTo(test.absoluteVec(new Vec3(3.5, 4, 1.5)), 0, pitch);
            var stand = new StarPlatinumEntity(world ? RipplesFabric.THE_WORLD : RipplesFabric.STAR_PLATINUM, player.level());
            stand.setOwner(player);
            player.level().addFreshEntity(stand);
            var target = test.spawnWithNoFreeWill(EntityTypes.CHICKEN, new Vec3(3.5, 4, 3));
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            target.setHealth(100);
            Vec3 center = player.getEyePosition().add(player.getLookAngle().scale(1.7));
            target.setPos(center.subtract(0, target.getBbHeight() / 2.0, 0));
            var horizontal = test.spawnWithNoFreeWill(EntityTypes.CHICKEN, new Vec3(3.5, 4, 3));
            horizontal.setNoGravity(true);
            horizontal.setPos(player.getEyePosition().add(0, -horizontal.getBbHeight() / 2.0, 1.7));
            test.assertTrue(attack == 2 ? stand.beginHeld(false) : stand.beginAttack(attack == 1), "Vertical attack starts");
            stand.tick();
            Vec3 offset = stand.position().subtract(player.position());
            test.assertTrue(offset.normalize().dot(player.getLookAngle().normalize()) > 0.99999,
                    "Melee manifestation follows full pitch, including vertical offset: " + offset);
            test.assertTrue(Math.abs(offset.y) >= 0.4 && offset.length() >= 0.499 && offset.length() <= (attack == 2 ? 1.501 : 2.001),
                    "Original min/max offset bounds apply above and below the player");
            for (int tick = 0; tick < 30 && target.getHealth() == 100; tick++) {
                stand.refreshHeld();
                stand.tick();
            }
            test.assertTrue(target.getHealth() < 100, "Attack damages the reachable target at pitch " + pitch);
            test.assertValueEqual(horizontal.getHealth(), horizontal.getMaxHealth(), "Horizontal decoy is outside the vertical attack ray");
            target.discard();
            horizontal.discard();
            stand.discard();
        }
        test.succeed();
    }
}
