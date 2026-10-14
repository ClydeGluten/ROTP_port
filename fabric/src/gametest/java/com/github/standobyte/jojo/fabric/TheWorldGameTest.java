package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.core.StandStats;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Server GameTests validating The World's combat mechanics, stats, progression, blink, and time punch. */
public final class TheWorldGameTest {

    private static ServerPlayer createPlayer(GameTestHelper test, Vec3 relativePos) {
        var level = test.getLevel();
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.PVP, true, level.getServer());
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "tw-test-" + java.util.UUID.randomUUID().toString().substring(0, 8)), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerableTime(0);
        player.snapTo(test.absoluteVec(relativePos), 0, 0);
        player.setNoGravity(true);
        return player;
    }

    private static ServerPlayer setupTheWorld(GameTestHelper test, int level, float stamina) {
        var player = createPlayer(test, new Vec3(2.5, 1, 2.5));
        StandService.grant(player, StandProgress.THE_WORLD);
        player.setAttached(StandAttachments.THE_WORLD_RESOLVE, ResolveProgress.EMPTY.withLevel(level).withTraining(95));
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.THE_WORLD, stamina, level / 4F, true));
        StandService.toggle(player);
        return player;
    }

    @GameTest
    public void theWorldIdentityAndStats(GameTestHelper test) {
        var player = createPlayer(test, new Vec3(1.5, 1, 1.5));
        StandService.grant(player, StandProgress.THE_WORLD);
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        test.assertTrue(progress.hasTheWorld(), "Player must possess The World");
        test.assertFalse(progress.hasStarPlatinum(), "Player must not have Star Platinum");
        test.assertValueEqual(progress.stats(), StandStats.THE_WORLD, "Progress stats must match The World stats");
        test.assertValueEqual(progress.timeStopUnlock(), 2, "The World unlocks Time Stop at Resolve level 2");

        test.assertTrue(StandService.toggle(player), "Summoning The World succeeds");
        var stand = StandService.current(player);
        test.assertTrue(stand != null, "The World stand entity must exist");
        test.assertTrue(stand.isTheWorld(), "Stand entity must report isTheWorld");
        test.assertValueEqual(stand.getType(), RipplesFabric.THE_WORLD, "Stand entity type must be THE_WORLD");
        test.assertValueEqual(stand.standStats(), StandStats.THE_WORLD, "Stand stats must be THE_WORLD");
        test.assertValueEqual(stand.standName(), "the_world", "Stand name must be the_world");

        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest
    public void starPlatinumSpecialRejection(GameTestHelper test) {
        var twPlayer = setupTheWorld(test, 4, 1000);
        var twStand = StandService.current(twPlayer);

        test.assertFalse(twStand.beginSpecial(StarPlatinumEntity.PHASE_FINGER), "The World must reject Star Finger");
        test.assertFalse(twStand.beginSpecial(StarPlatinumEntity.PHASE_INHALE), "The World must reject Inhale");
        test.assertFalse(twStand.beginSpecial(StarPlatinumEntity.PHASE_ZOOM), "The World must reject Zoom");

        var spPlayer = createPlayer(test, new Vec3(3.5, 1, 3.5));
        StandService.grant(spPlayer, StandProgress.STAR_PLATINUM);
        spPlayer.setAttached(StandAttachments.RESOLVE, ResolveProgress.EMPTY.withLevel(4).withTraining(95));
        spPlayer.setAttached(StandAttachments.PROGRESS, StandProgress.grantStarPlatinum(true));
        StandService.toggle(spPlayer);

        test.assertTrue(TheWorldActions.blink(spPlayer), "Star Platinum unlocks original Quick move at Resolve 4");
        test.assertFalse(TheWorldActions.timePunch(spPlayer), "Star Platinum user cannot Time Punch");

        StandService.dismiss(twPlayer);
        StandService.dismiss(spPlayer);
        test.succeed();
    }

    @GameTest
    public void progressionLocks(GameTestHelper test) {
        var player = createPlayer(test, new Vec3(1.5, 1, 1.5));
        StandService.grant(player, StandProgress.THE_WORLD);
        player.setAttached(StandAttachments.THE_WORLD_RESOLVE, ResolveProgress.EMPTY.withLevel(0));
        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.THE_WORLD, 1000, 0, true));
        StandService.toggle(player);
        var stand = StandService.current(player);

        test.assertFalse(stand.beginUppercut(), "Kick requires Resolve level 1");
        test.assertFalse(TheWorldActions.blink(player), "Blink requires Resolve level 2");
        test.assertFalse(TheWorldActions.timePunch(player), "Time punch requires Resolve level 3");
        test.assertFalse(TimeStopService.toggle(player), "Time Stop requires Resolve level 2");

        player.setAttached(StandAttachments.THE_WORLD_RESOLVE, ResolveProgress.EMPTY.withLevel(1));
        test.assertTrue(stand.beginUppercut(), "Kick unlocked at Resolve level 1");
        stand.stopHeld();

        test.assertFalse(TheWorldActions.blink(player), "Blink still locked at Resolve level 1");
        test.assertFalse(TheWorldActions.timePunch(player), "Time punch still locked at Resolve level 1");
        test.assertFalse(TimeStopService.toggle(player), "Time Stop still locked at Resolve level 1");

        var unlocked = setupTheWorld(test, 2, 2000);
        test.assertTrue(TheWorldActions.blink(unlocked), "Blink unlocked at Resolve level 2");
        test.assertFalse(TimeStopService.toggle(unlocked), "Blink cooldown gates regional Time Stop");
        var stopper = setupTheWorld(test, 2, 2000);
        test.assertTrue(TimeStopService.toggle(stopper), "Time Stop unlocked at Resolve level 2");
        test.assertValueEqual(TimeStopService.chargeTicksRemaining(stopper), 30, "The World uses 30-tick TS charge");
        TimeStopService.cancel(stopper);
        test.assertFalse(TheWorldActions.timePunch(stopper), "Time punch still locked at Resolve level 2");
        var puncher = setupTheWorld(test, 3, 2000);
        test.assertTrue(TheWorldActions.timePunch(puncher), "Time punch unlocked at Resolve level 3");
        StandService.dismiss(unlocked); StandService.dismiss(stopper); StandService.dismiss(puncher);

        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest(maxTicks = 60)
    public void kickDamageAndSweeping(GameTestHelper test) {
        var player = setupTheWorld(test, 1, 1000);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        var stand = StandService.current(player);

        var primary = test.spawn(EntityTypes.HUSK, new BlockPos(1, 1, 3));
        primary.setNoAi(true);
        primary.setNoGravity(true);
        float initialPrimaryHealth = primary.getHealth();

        var bystander = test.spawn(EntityTypes.HUSK, new BlockPos(2, 1, 3));
        bystander.setNoAi(true);
        bystander.setNoGravity(true);
        float initialBystanderHealth = bystander.getHealth();

        test.assertTrue(stand.beginUppercut(), "The World initiates Kick");
        test.assertValueEqual(stand.abilityKind(), 1, "Kick sets abilityKind 1");

        test.startSequence()
                .thenWaitUntil(() -> test.assertTrue(stand.actionPhase() == 2 || primary.getHealth() < initialPrimaryHealth, "Wait for strike"))
                .thenExecute(() -> {
                    test.assertTrue(primary.getHealth() < initialPrimaryHealth, "Primary target took kick damage");
                    test.assertTrue(bystander.getHealth() < initialBystanderHealth, "Bystander took sweeping damage");
                    float primaryDamage = initialPrimaryHealth - primary.getHealth();
                    float bystanderDamage = initialBystanderHealth - bystander.getHealth();
                    test.assertTrue(bystanderDamage > 0 && bystanderDamage <= primaryDamage, "Sweep damage is a proportion of main damage");
                    StandService.dismiss(player);
                })
                .thenSucceed();
    }

    @GameTest
    public void blinkCollisionStaminaAndCooldown(GameTestHelper test) {
        var depleted = setupTheWorld(test, 2, 50);
        test.assertTrue(TheWorldActions.blink(depleted), "Original minimum-duration blink permits depleted stamina");
        test.assertValueEqual(depleted.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(), 0F, "Consumption clamps at zero");
        StandService.dismiss(depleted);
        var player = setupTheWorld(test, 2, 500);

        player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.THE_WORLD, 500, 0.5F, true));

        // Place wall in front of player
        test.setBlock(new BlockPos(2, 1, 5), Blocks.OBSIDIAN);
        test.setBlock(new BlockPos(2, 2, 5), Blocks.OBSIDIAN);

        player.snapTo(test.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0); // Looking towards z+
        test.assertTrue(TheWorldActions.blink(player), "Blink succeeds towards wall");

        // Original block targeting can land on top of an open wall; the full player body must fit.
        Vec3 endPos = player.position();
        test.assertTrue(test.getLevel().noCollision(player, player.getBoundingBox()), "Blink landing must be collision-free");
        test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() < 500, "Stamina consumed on blink");
        test.assertTrue(AbilityCooldowns.remaining(player, 11) > 0, "Blink starts cooldown on key 11");
        test.assertTrue(TimeStopService.cooldownRemaining(player) > 0, "Blink starts shared TimeStop cooldown");

        // Mutual cooldown check: cannot blink while on cooldown
        test.assertFalse(TheWorldActions.blink(player), "Cannot blink while cooldown is active");

        StandService.dismiss(player);
        test.succeed();
    }

    @GameTest(maxTicks = 60)
    public void timePunchValidationAndPositioning(GameTestHelper test) {
        var player = setupTheWorld(test, 3, 1000);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        var stand = StandService.current(player);

        var target = test.spawn(EntityTypes.HUSK, new BlockPos(1, 1, 5));
        target.setNoAi(true);
        target.setNoGravity(true);
        float initialHealth = target.getHealth();

        test.assertTrue(TheWorldActions.timePunch(player), "Time punch initiates");
        test.assertValueEqual(stand.abilityKind(), 2, "Time punch sets abilityKind 2");
        test.assertValueEqual(AbilityCooldowns.remaining(player, 12), 50, "Time punch starts 50 tick cooldown on key 12");

        // Verify Stand repositioned near target
        Vec3 standPos = stand.position();
        test.assertTrue(standPos.distanceTo(target.position()) < 3.0, "Stand instantly positions near target");

        test.startSequence()
                .thenWaitUntil(() -> test.assertTrue(target.getHealth() < initialHealth, "Target took Time Punch damage"))
                .thenExecute(() -> {
                    test.assertTrue(target.getHealth() < initialHealth, "Time punch deals heavy damage");
                    StandService.dismiss(player);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 50)
    public void heavyPunchArmorPiercing(GameTestHelper test) {
        var player = setupTheWorld(test, 1, 1000);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        var stand = StandService.current(player);
        var target = test.spawn(EntityTypes.HUSK, new BlockPos(1, 1, 3));
        target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        float raw = com.github.standobyte.jojo.core.StandStatFormulas.getHeavyAttackDamage(StandStats.THE_WORLD.power(0.25F));
        var source = new net.minecraft.world.damagesource.DamageSource(player.damageSources().playerAttack(player).typeHolder(), stand, player);
        test.assertTrue(stand.beginAttack(true), "The World initiates heavy punch");
        test.startSequence().thenWaitUntil(() -> test.assertTrue(target.getHealth() < 100, "Wait for accepted heavy hit"))
                .thenExecute(() -> {
                    // Equipment modifiers become active on the living-entity tick, not at setItemSlot.
                    float vanilla = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(target, raw, source, target.getArmorValue(),
                            (float) target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS));
                    test.assertTrue(100 - target.getHealth() > vanilla + 0.1F, "Original piercing beats vanilla armor reduction: actual=" + (100 - target.getHealth()) + ", vanilla=" + vanilla + ", raw=" + raw + ", kind=" + stand.abilityKind() + ", power=" + player.getAttachedOrCreate(StandAttachments.PROGRESS).development());
                    StandService.dismiss(player);
                }).thenSucceed();
    }
}
