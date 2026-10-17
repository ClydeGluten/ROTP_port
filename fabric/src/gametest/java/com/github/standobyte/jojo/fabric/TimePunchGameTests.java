package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.core.StandProgress;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** TheWorldTSHeavyAttack parity: instant travel precedes a real five-tick punch task. */
public final class TimePunchGameTests {
    @GameTest
    public void timePunchCooldownIsIndependentFromBaseAndBlink(GameTestHelper test) {
        var player = setup(test, false, 2000);
        var stand = StandService.current(player);
        target(test, new Vec3(3.5, 3, 5.5));
        TimeStopService.setCooldown(player, 120);
        AbilityCooldowns.start(player, TheWorldActions.COOLDOWN_BLINK, 60);

        test.assertTrue(TheWorldActions.timePunch(player), "Base Time Stop and blink cooldowns do not block Time Punch");
        test.assertValueEqual(TheWorldActions.timePunchCooldown(player), 50, "Time Punch starts its own original 50-tick cooldown");
        test.assertValueEqual(TimeStopService.cooldownRemaining(player), 120, "Time Punch does not replace the base cooldown");
        test.assertValueEqual(TheWorldActions.blinkCooldown(player), 60, "Time Punch does not replace or advance the blink cooldown");
        advanceToPhase(test, stand, 0);
        test.assertFalse(TheWorldActions.timePunch(player), "Its own cooldown still rejects Time Punch after the action finishes");
        test.assertValueEqual(TheWorldActions.timePunchCooldown(player), 50, "Entity-only ticks cannot advance server-owned cooldowns");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void creativeTimePunchHasNoStaminaCostOrCooldown(GameTestHelper test) {
        var player = setup(test, true, 500);
        var stand = StandService.current(player);
        target(test, new Vec3(3.5, 3, 5.5));
        test.assertTrue(TheWorldActions.timePunch(player), "Creative Time Punch starts");
        test.assertValueEqual(TheWorldActions.timePunchCooldown(player), 0, "Creative cooldown is zero");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 500F, "Creative travel consumes no stamina");
        advanceToPhase(test, stand, 0);
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 500F, "Creative punch consumes no stamina");
        test.assertTrue(TheWorldActions.timePunch(player), "Creative can repeat after the action completes without waiting for a cooldown");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void depletedStaminaStillStartsAnUnmovedTimePunch(GameTestHelper test) {
        var player = setup(test, false, 0);
        var stand = StandService.current(player);
        target(test, new Vec3(3.5, 3, 5.5));
        Vec3 original = stand.position();
        int playerAge = player.tickCount;
        int standAge = stand.tickCount;
        test.assertTrue(TheWorldActions.timePunch(player), "Original action permits a depleted stamina pool");
        assertVector(test, stand.position(), original, "Zero implied-time budget cannot teleport the Stand");
        test.assertValueEqual(player.tickCount, playerAge, "Zero-budget travel skips no player ticks");
        test.assertValueEqual(stand.tickCount, standAge, "Zero-budget travel skips no Stand ticks");
        test.assertValueEqual(stand.actionTicks(), 5, "The real five-tick windup still starts");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 0F, "Travel consumption clamps to zero");
        advanceToPhase(test, stand, 2);
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 0F, "Perform consumption also clamps to zero");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void instantCostPrecedesTheOrdinaryPunchCost(GameTestHelper test) {
        var player = setup(test, false, 170);
        var stand = StandService.current(player);
        target(test, new Vec3(3.5, 3, 4.5));
        Vec3 original = stand.position();
        int age = player.tickCount;
        float perTick = TheWorldActions.tickingCost(player);
        test.assertTrue(TheWorldActions.timePunch(player), "Limited-stamina Time Punch starts");
        int skipped = player.tickCount - age;
        test.assertTrue(skipped >= 10 && skipped <= 11, "Budget reserves only the instant component's 90 stamina before travel");
        test.assertTrue(stand.position().distanceToSqr(original) > 0.01, "Remaining travel budget moves the Stand before the punch cost is charged");
        float afterTravel = Math.max(0, 170 - 90 - skipped * perTick);
        assertStamina(test, player, afterTravel, "Before perform, only 90 plus implied ticking cost has been consumed");
        for (int tick = 0; tick < 4; tick++) stand.tick();
        test.assertValueEqual(stand.actionPhase(), 1, "Four real ticks remain inside the original windup");
        assertStamina(test, player, afterTravel, "Ordinary punch cost is not prepaid during windup");
        stand.tick();
        test.assertValueEqual(stand.actionPhase(), 2, "Fifth real tick performs the punch");
        assertStamina(test, player, Math.max(0, afterTravel - 50), "Perform charges the ordinary 50 stamina exactly once");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void impliedTicksAdvanceAgeWithoutSimulatingEntities(GameTestHelper test) {
        var player = setup(test, false, 2000);
        var stand = StandService.current(player);
        var target = target(test, new Vec3(3.5, 3, 5.5));
        player.setHealth(10);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));
        player.setRemainingFireTicks(80);
        player.setAirSupply(100);
        player.damageCooldownTime = 7;
        player.setDeltaMovement(0.2, 0.1, 0.3);
        AbilityCooldowns.start(player, RipplesFabric.id("test_unrelated_cooldown"), 80);
        int playerAge = player.tickCount;
        int standAge = stand.tickCount;
        int targetAge = target.tickCount;
        Vec3 body = player.position();
        Vec3 momentum = player.getDeltaMovement();

        test.assertTrue(TheWorldActions.timePunch(player), "Time Punch starts without executing ordinary entity ticks");
        int skipped = player.tickCount - playerAge;
        test.assertTrue(skipped >= 10, "Original implied windup advances the player's age");
        test.assertValueEqual(stand.tickCount - standAge, skipped, "Stand and owner advance by the same implied tick count");
        test.assertValueEqual(target.tickCount, targetAge, "Target age is not advanced");
        assertVector(test, player.position(), body, "Implied ticks do not simulate player physics");
        assertVector(test, player.getDeltaMovement(), momentum, "Implied ticks preserve player momentum");
        test.assertValueEqual(player.getHealth(), 10F, "Implied ticks do not apply fire damage or regeneration");
        test.assertValueEqual(player.getEffect(MobEffects.REGENERATION).getDuration(), 200, "Implied ticks do not advance effects");
        test.assertValueEqual(player.getRemainingFireTicks(), 80, "Implied ticks do not advance fire");
        test.assertValueEqual(player.getAirSupply(), 100, "Implied ticks do not advance breathing");
        test.assertValueEqual(player.damageCooldownTime, 7, "Implied ticks do not advance damage immunity");
        test.assertValueEqual(AbilityCooldowns.remaining(player, RipplesFabric.id("test_unrelated_cooldown")), 80, "Implied ticks do not advance unrelated cooldowns");
        test.assertValueEqual(target.getHealth(), 100F, "Instant travel does not perform the delayed punch");
        test.assertValueEqual(stand.actionTicks(), 5, "Implied ticks do not consume the real windup");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void shiftBackshotKeepsTheAcquiredTarget(GameTestHelper test) {
        var player = setup(test, true, 500);
        var stand = StandService.current(player);
        var target = target(test, new Vec3(3.5, 3, 5.5));
        target.setYRot(90);
        player.setShiftKeyDown(true);
        Vec3 expected = timePunchDestination(player, stand, target, true);
        test.assertTrue(TheWorldActions.timePunch(player), "Shift Time Punch starts");
        assertVector(test, stand.position(), expected, "Backshot reverses the approach offset, independent of the target's facing");
        test.assertTrue(stand.getZ() > target.getZ(), "Shift places the Stand beyond the target along the user's sight line");

        Vec3 oldTarget = target.position();
        target.setPos(oldTarget.add(1, 0, 0));
        var replacement = target(test, test.relativeVec(oldTarget));
        stand.lookAt(EntityAnchorArgument.Anchor.EYES, replacement.getBoundingBox().getCenter());
        advanceToPhase(test, stand, 2);
        test.assertTrue(target.getHealth() < 100, "The task retains its original target after the target moves off the original ray");
        test.assertValueEqual(replacement.getHealth(), 100F, "A new entity on the ray cannot steal the stored Time Punch target");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void removedTimePunchTargetCannotBeReplaced(GameTestHelper test) {
        var player = setup(test, true, 500);
        var stand = StandService.current(player);
        var target = target(test, new Vec3(3.5, 3, 5.5));
        test.assertTrue(TheWorldActions.timePunch(player), "Time Punch acquires a target");
        Vec3 oldTarget = target.position();
        target.discard();
        var replacement = target(test, test.relativeVec(oldTarget));
        advanceToPhase(test, stand, 2);
        test.assertValueEqual(replacement.getHealth(), 100F, "An invalid stored target produces a miss instead of retargeting");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void manualControlUsesStandAimAndKeepsTeleportThroughWindup(GameTestHelper test) {
        var player = setup(test, true, 500);
        var stand = StandService.current(player);
        player.setYRot(180);
        test.assertTrue(stand.startControl(), "Manual control starts");
        stand.setYRot(0);
        stand.setXRot(0);
        stand.control(0, 0, 0, 0, 0);
        var target = target(test, new Vec3(3.5, 3, 5.5));
        Vec3 body = player.position();
        Vec3 expected = timePunchDestination(stand, stand, target, false);

        test.assertTrue(TheWorldActions.timePunch(player), "Stand-facing target is acquired while the player faces away");
        assertVector(test, stand.position(), expected, "Manual Time Punch traces from the Stand's eyes");
        for (int tick = 0; tick < 4; tick++) {
            stand.control(0, 1, 0, 90, 0);
            player.setPos(body.add(0, -0.1, 0));
            player.setDeltaMovement(0, -0.1, 0);
            stand.tick();
            assertVector(test, player.position(), body, "Airborne manual owner stays anchored during Time Punch");
            assertVector(test, player.getDeltaMovement(), Vec3.ZERO, "Manual anchoring cancels falling momentum during Time Punch");
            assertVector(test, stand.position(), expected, "Manual movement cannot overwrite the teleport during windup");
        }
        stand.tick();
        test.assertTrue(target.getHealth() < 100, "Manual Time Punch hits the acquired target");
        assertVector(test, player.position(), body, "Manual Time Punch leaves the user's body in place");
        test.assertTrue(stand.manuallyControlled(), "Time Punch does not drop manual control");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void timePunchCancelsOtherRecoveryButNotWindupOrItself(GameTestHelper test) {
        var player = setup(test, true, 500);
        var stand = StandService.current(player);
        test.assertTrue(stand.beginAttack(true), "Ordinary heavy punch begins");
        test.assertFalse(TheWorldActions.timePunch(player), "Time Punch cannot interrupt another action's windup");
        advanceToPhase(test, stand, 3);
        target(test, new Vec3(3.5, 3, 5.5));
        int age = player.tickCount;
        test.assertTrue(TheWorldActions.timePunch(player), "Time Punch cancels another action's recovery");
        test.assertTrue(player.tickCount - age >= 30, "Cancelling another task adds the original 20 implied recovery ticks");
        test.assertValueEqual(stand.actionTicks(), 5, "Recovery cancellation starts a fresh five-tick windup");
        advanceToPhase(test, stand, 3);
        test.assertValueEqual(TheWorldActions.timePunchCooldown(player), 0, "Creative removes cooldown as a confounder");
        test.assertFalse(TheWorldActions.timePunch(player), "Time Punch cannot cancel its own recovery even without cooldown");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void timePunchCancelsBarrageRecovery(GameTestHelper test) {
        var player = setup(test, true, 500);
        var stand = StandService.current(player);
        test.assertTrue(stand.beginHeld(false), "Barrage begins");
        test.assertFalse(TheWorldActions.timePunch(player), "Time Punch cannot interrupt an active barrage");
        stand.tick();
        stand.stopHeld();
        test.assertTrue(stand.uiRecoveryTicks() > 0, "Released barrage has an actual recovery to cancel");
        target(test, new Vec3(3.5, 3, 5.5));
        int age = player.tickCount;
        test.assertTrue(TheWorldActions.timePunch(player), "Time Punch cancels another action's barrage recovery");
        test.assertTrue(player.tickCount - age >= 30, "Barrage recovery also adds 20 implied ticks");
        test.assertValueEqual(stand.uiRecoveryTicks(), 0, "Cancelled barrage recovery no longer blocks the new action");
        test.assertValueEqual(stand.actionTicks(), 5, "Barrage recovery cancellation starts the normal windup");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void timePunchTrainingRoundsBeforeResolveMultiplier(GameTestHelper test) {
        for (boolean burst : new boolean[] {false, true}) {
            var player = setup(test, false, 2000);
            var state = new ResolveProgress(4, burst ? 32500 : 0, 0, 1, -1, java.util.List.of(), true,
                    burst ? 600 : 0, 4, 35);
            ProgressionService.update(player, state);
            int age = player.tickCount;
            test.assertTrue(TheWorldActions.timePunch(player), "Time Punch begins for training check");
            int implied = player.tickCount - age;
            float expected = 35 + (int) (implied * 0.1F) * (burst ? 4 : 1);
            test.assertValueEqual(ProgressionService.state(player).trainingPoints(), expected,
                    "Original learning truncates first, then applies Resolve's fourfold multiplier");
            finish(player);
        }
        test.succeed();
    }

    private static ServerPlayer setup(GameTestHelper test, boolean creative, float stamina) {
        var level = test.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "time-punch-test"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.setInvulnerableTime(0);
        // Elevated fixtures keep eye-height placement clear of floor collision while testing teleport geometry.
        player.snapTo(test.absoluteVec(new Vec3(3.5, 3, 2.5)), 0, 0);
        player.setNoGravity(true);
        player.setAttached(StandAttachments.THE_WORLD_RESOLVE, ResolveProgress.EMPTY.withLevel(4).withTraining(95));
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.THE_WORLD, stamina, 1, true));
        test.assertTrue(StandService.toggle(player), "The World summons");
        return player;
    }

    private static LivingEntity target(GameTestHelper test, Vec3 position) {
        var target = test.spawnWithNoFreeWill(EntityTypes.HUSK, position);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(100);
        return target;
    }

    private static Vec3 timePunchDestination(LivingEntity aiming, StarPlatinumEntity stand, LivingEntity target, boolean behind) {
        double offset = 0.5 + stand.getBbWidth() + target.getBbWidth() / 2.0;
        Vec3 approach = aiming.getEyePosition().subtract(target.getEyePosition()).normalize().scale(offset);
        if (behind) approach = approach.scale(-1);
        return target.getEyePosition().add(approach).subtract(0, stand.getEyeHeight(), 0);
    }

    private static void advanceToPhase(GameTestHelper test, StarPlatinumEntity stand, int phase) {
        for (int tick = 0; tick < 100 && stand.actionPhase() != phase; tick++) stand.tick();
        test.assertValueEqual(stand.actionPhase(), phase, "Stand reaches action phase " + phase + " within 100 real entity ticks");
    }

    private static void finish(ServerPlayer player) {
        StandService.clear(player);
        player.connection.disconnect(new DisconnectionDetails(Component.literal("Time Punch test complete")));
    }

    private static void assertVector(GameTestHelper test, Vec3 actual, Vec3 expected, String message) {
        test.assertTrue(actual.distanceToSqr(expected) < 1.0E-10, message + "; expected=" + expected + ", actual=" + actual);
    }

    private static void assertStamina(GameTestHelper test, ServerPlayer player, float expected, String message) {
        float actual = player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina();
        test.assertTrue(Math.abs(actual - expected) < 0.0001F, message + "; expected=" + expected + ", actual=" + actual);
    }
}
