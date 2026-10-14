package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.core.ResolveProgress;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real physics and AI, unlike the stationary damage fixtures used by other combat tests. */
public final class HitReactionGameTests {
    @GameTest(maxTicks = 100) public void starPlatinumLight(GameTestHelper test) { reaction(test, false, 0); }
    @GameTest(maxTicks = 100) public void starPlatinumHeavy(GameTestHelper test) { reaction(test, false, 1); }
    @GameTest(maxTicks = 100) public void starPlatinumUppercut(GameTestHelper test) { reaction(test, false, 2); }
    @GameTest(maxTicks = 100) public void starPlatinumFinger(GameTestHelper test) { reaction(test, false, 3); }
    @GameTest(maxTicks = 100) public void starPlatinumBarrage(GameTestHelper test) { reaction(test, false, 4); }
    @GameTest(maxTicks = 100) public void theWorldLight(GameTestHelper test) { reaction(test, true, 0); }
    @GameTest(maxTicks = 100) public void theWorldHeavy(GameTestHelper test) { reaction(test, true, 1); }
    @GameTest(maxTicks = 100) public void theWorldKick(GameTestHelper test) { reaction(test, true, 2); }
    @GameTest(maxTicks = 100) public void theWorldBarrage(GameTestHelper test) { reaction(test, true, 4); }
    @GameTest(maxTicks = 100) public void theWorldTimePunch(GameTestHelper test) { reaction(test, true, 5); }
    @GameTest(maxTicks = 60) public void starPlatinumRapidPunches(GameTestHelper test) { rapidPunches(test, false); }
    @GameTest(maxTicks = 60) public void theWorldRapidPunches(GameTestHelper test) { rapidPunches(test, true); }

    @GameTest public void barrageUsesOriginalReducedImpulse(GameTestHelper test) { barrageImpulse(test, false, false); }
    @GameTest public void barrageRespectsKnockbackResistance(GameTestHelper test) { barrageImpulse(test, true, false); }
    @GameTest public void resolveBarragePreservesMomentum(GameTestHelper test) { barrageImpulse(test, false, true); }

    private static void barrageImpulse(GameTestHelper test, boolean resistant, boolean resolve) {
        var player = setup(test, false);
        if (resolve) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(StandItems.RESOLVE, 100));
        var cow = test.spawn(EntityTypes.COW, new Vec3(3.5, 1, 4.5));
        cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistant ? 1 : 0);
        cow.setOnGround(true);
        Vec3 original = resistant || resolve ? new Vec3(0.2, 0, 0.1) : Vec3.ZERO;
        cow.setDeltaMovement(original);
        test.assertTrue(StandService.startHeld(player, false), "Barrage starts");
        var stand = StandService.current(player);
        // Tick the attacker only so the target's physics cannot consume the impulse before inspection.
        for (int tick = 0; tick < 5 && cow.getHealth() == cow.getMaxHealth(); tick++) stand.tick();
        test.assertTrue(cow.getHealth() < cow.getMaxHealth(), "Barrage lands");
        if (resistant || resolve) test.assertValueEqual(cow.getDeltaMovement(), original, "Zero-strength barrage preserves existing momentum");
        else test.assertTrue(Math.abs(cow.getDeltaMovement().z - 0.048) < 0.00001,
                "Power 16 barrage uses original 0.4 * 16 * 0.0075 impulse: " + cow.getDeltaMovement());
        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest public void standHitPreservesExistingVanillaImmunity(GameTestHelper test) {
        var player = setup(test, false);
        var cow = test.spawn(EntityTypes.COW, new Vec3(3.5, 1, 4.5));
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        cow.setHealth(100);
        var vanilla = player.damageSources().playerAttack(player);
        var source = new net.minecraft.world.damagesource.DamageSource(vanilla.typeHolder(), StandService.current(player), player);
        test.assertTrue(cow.hurtServer(test.getLevel(), vanilla, 8), "First vanilla hit lands");
        int cooldown = cow.damageCooldownTime;
        test.assertTrue(StandDamage.hurt(test.getLevel(), cow, source, 4), "Stand hit bypasses previous hit immunity");
        test.assertValueEqual(cow.getHealth(), 88F, "Stand hit deals full damage during vanilla cooldown");
        test.assertValueEqual(cow.damageCooldownTime, cooldown, "Stand preserves vanilla timer");
        test.assertFalse(cow.hurtServer(test.getLevel(), vanilla, 8), "Equal vanilla hit remains rejected");
        test.assertTrue(cow.hurtServer(test.getLevel(), vanilla, 10), "Stronger vanilla hit still lands");
        test.assertValueEqual(cow.getHealth(), 86F, "Original lastHurt preserved: stronger vanilla hit applies only excess damage");
        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest public void standHitDoesNotInventVanillaImmunity(GameTestHelper test) {
        var player = setup(test, false);
        var cow = test.spawn(EntityTypes.COW, new Vec3(3.5, 1, 4.5));
        var vanilla = player.damageSources().playerAttack(player);
        var source = new net.minecraft.world.damagesource.DamageSource(vanilla.typeHolder(), StandService.current(player), player);
        test.assertTrue(StandDamage.hurt(test.getLevel(), cow, source, 4), "Stand hit lands");
        test.assertValueEqual(cow.damageCooldownTime, 0, "Fresh target keeps its previous zero cooldown");
        test.assertTrue(cow.hurtServer(test.getLevel(), vanilla, 1), "First ordinary attack remains accepted after Stand damage");
        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest public void rejectedStandHitPreservesTarget(GameTestHelper test) {
        var player = setup(test, false);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var cow = test.spawn(EntityTypes.COW, new Vec3(3.5, 1, 4.5));
        cow.setPermanentlyInvulnerable(true);
        cow.damageCooldownTime = 7;
        var source = new net.minecraft.world.damagesource.DamageSource(player.damageSources().playerAttack(player).typeHolder(), StandService.current(player), player);
        test.assertFalse(StandDamage.hurt(test.getLevel(), cow, source, 4), "Entity invulnerability still rejects Stand hits");
        test.assertValueEqual(cow.getHealth(), cow.getMaxHealth(), "Rejected hit deals no damage");
        test.assertValueEqual(cow.damageCooldownTime, 7, "Rejected hit restores original timer");
        test.assertTrue(cow.getLastDamageSource() == null && cow.getDeltaMovement().equals(Vec3.ZERO), "Rejected hit cannot cause panic or knockback");
        StandService.dismiss(player);
        test.succeed();
    }

    private static ServerPlayer setup(GameTestHelper test, boolean world) {
        var level = test.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "hit-reaction"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.snapTo(test.absoluteVec(new Vec3(3.5, 1, 2.5)), 0, 15);
        player.setNoGravity(true);
        player.setAttached(StandAttachments.PROGRESS, StandProgress.grant(world ? StandProgress.THE_WORLD : StandProgress.STAR_PLATINUM, true));
        player.setAttached(StandAttachments.RESOLVE, ResolveProgress.EMPTY.withLevel(4).withTraining(95));
        test.assertTrue(StandService.toggle(player), "Stand summons");
        test.forEveryBlockInStructure(pos -> {
            if (pos.getY() == 0) test.setBlock(pos, Blocks.STONE);
        });
        return player;
    }

    private static void reaction(GameTestHelper test, boolean world, int move) {
        var player = setup(test, world);
        var cow = test.spawn(EntityTypes.COW, new Vec3(3.5, 1, 4.5));
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        cow.setHealth(100);
        cow.setOnGround(true);
        double speed = cow.getAttributeValue(Attributes.MOVEMENT_SPEED);
        // Keep active AI, but prevent random walking before impact from confusing the physics measurement.
        cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Vec3 start = cow.position();
        boolean started = switch (move) {
            case 0, 1 -> StandService.attack(player, move == 1);
            case 2 -> StandService.uppercut(player);
            case 3 -> StandService.special(player, StarPlatinumEntity.PHASE_FINGER);
            case 4 -> StandService.startHeld(player, false);
            case 5 -> TheWorldActions.timePunch(player);
            default -> throw new AssertionError("Unknown test move");
        };
        test.assertTrue(started, "Action starts");
        test.startSequence()
                .thenWaitUntil(() -> test.assertTrue(cow.getHealth() < 100, "Wait for actual Stand hit"))
                .thenExecute(() -> {
                    test.assertTrue(cow.getDeltaMovement().horizontalDistanceSqr() > 0.001
                                    || cow.position().subtract(start).horizontalDistanceSqr() > 0.001,
                            "Hit must apply horizontal knockback; actual=" + cow.getDeltaMovement());
                    test.assertTrue(cow.getLastDamageSource().is(DamageTypeTags.PANIC_CAUSES), "Stand damage triggers vanilla panic");
                    test.assertTrue(cow.getLastHurtByMob() == player, "Stand hit credits its owner as attacker");
                    cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
                    StandService.dismiss(player);
                })
                .thenWaitUntil(() -> test.assertTrue(cow.isPanicking(), "Live cow starts panic AI"))
                .thenWaitUntil(() -> test.assertTrue(cow.position().distanceToSqr(start) > 0.04, "Knockback and panic move the cow"))
                .thenSucceed();
    }

    private static void rapidPunches(GameTestHelper test, boolean world) {
        var player = setup(test, world);
        var stand = StandService.current(player);
        var cow = test.spawn(EntityTypes.COW, new Vec3(3.5, 1, 4.5));
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        cow.setHealth(100);
        Vec3 start = cow.position();
        float[] firstHealth = {100};
        test.assertTrue(StandService.attack(player, false), "First punch starts");
        test.startSequence()
                .thenWaitUntil(() -> test.assertValueEqual(stand.actionPhase(), 3, "First punch reaches cancellable recovery"))
                .thenExecute(() -> {
                    test.assertTrue(cow.getHealth() < 100, "First punch lands");
                    firstHealth[0] = cow.getHealth();
                    cow.setPos(start);
                    cow.setDeltaMovement(Vec3.ZERO);
                    test.assertTrue(StandService.attack(player, false), "Second punch cancels recovery at the normal action cadence");
                })
                .thenWaitUntil(() -> test.assertValueEqual(stand.actionPhase(), 3, "Second punch reaches recovery"))
                .thenExecute(() -> {
                    test.assertTrue(cow.getHealth() < firstHealth[0], "Rapid follow-up must damage and react, not vanish into vanilla invulnerability");
                    test.assertTrue(cow.getDeltaMovement().horizontalDistanceSqr() > 0.001, "Second punch knocks back again");
                    StandService.dismiss(player);
                }).thenSucceed();
    }
}
