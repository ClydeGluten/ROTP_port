package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.VampireProgress;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual server-player/attachment/damage/equipment integration for the first original vampire slice.
 * A separate nighttime batch prevents unrelated daytime tests and pending roof skylight work
 * from contaminating tick-zero combat/economy assertions. The UV test still exercises real noon.
 */
public final class VampireGameTests {
    static ServerPlayer player(GameTestHelper test, String name, double z) {
        var level = test.getLevel();
        test.assertFalse(level.isBrightOutside(), "Non-sunlight fixtures start in an isolated real nighttime environment");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL); player.setInvulnerableTime(0); player.setNoGravity(true);
        player.snapTo(test.absoluteVec(new Vec3(2.5, 2, z)), 0, 0);
        player.getFoodData().setFoodLevel(17);
        // Ordinary sunlight shelter, not invulnerability: incoming damage remains testable.
        for (int x = 0; x < 6; x++) for (int zz = 0; zz < 8; zz++) test.setBlock(new BlockPos(x, 5, zz), Blocks.STONE);
        return player;
    }
    private static ServerPlayer vampire(GameTestHelper test, boolean full) {
        var player = player(test, "vampire-test", 1.5);
        VampireService.grant(player, full);
        return player;
    }
    private static void tick(ServerPlayer player, int count) { for (int i = 0; i < count; i++) { VampireService.refreshHeld(player); VampireService.tick(player); } }
    private static void close(ServerPlayer player) { VampireService.clear(player); player.level().getServer().getPlayerList().remove(player); }

    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void weakVampireLeapKeepsOriginalStrengthAndRejectsStun(GameTestHelper test) {
        var player = vampire(test, false);
        player.setAttached(VampireAttachments.PROGRESS, VampireService.progress(player).withBlood(1000));
        player.setOnGround(true);
        player.setXRot(0);
        test.assertTrue(VampireCombatService.leap(player), "Blood Gift vampires retain their weaker leap");
        double strength = VampireService.progress(player).bloodLevel(player.level().getDifficulty().getId()) * .15F * .25F;
        test.assertTrue(Math.abs(player.getDeltaMovement().horizontalDistance() - Math.cos(Math.PI / 6) * strength) < .00001,
                "Weak leap uses the original 0.15 multiplier and upward aim floor");
        VampireCombatService.stop(player);
        player.setOnGround(true);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireCombatService.STUN, 20));
        test.assertFalse(VampireCombatService.leap(player), "A stunned player cannot bypass immobilization with a leap packet");
        close(player); test.succeed();
    }

    @GameTest(environment = "jojo:vampire_night", setupTicks = 5, maxTicks = 60) public void effectApplicabilityAndStunnedCreeperMatchOriginal(GameTestHelper test) {
        var player = player(test, "effect-immunity", 1.5);
        player.setGameMode(GameType.CREATIVE);
        test.assertFalse(player.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireCombatService.STUN, 20)), "Creative players reject immobilization");
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(2, 2, 3));
        cow.setNoAi(true);
        test.assertFalse(cow.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireCombatService.STUN, 20)), "NoAI mobs reject stun without changing their permanent AI state");
        var stray = test.spawnWithNoFreeWill(EntityTypes.STRAY, new Vec3(3, 2, 3));
        test.assertFalse(stray.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireCombatService.FREEZE, 20)), "Cold-immune mobs reject Freeze from every effect source");
        VampireService.grant(player, true);
        test.assertTrue(player.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireCombatService.FREEZE, 20)), "Vampires retain the original cold-immunity exception");
        var creeper = test.spawnWithNoFreeWill(EntityTypes.CREEPER, new Vec3(4, 2, 4));
        creeper.setNoAi(false);
        creeper.ignite();
        ((com.github.standobyte.jojo.fabric.mixin.StunnedCreeperAccess) creeper).jojo$setSwell(20);
        test.assertTrue(creeper.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireCombatService.STUN, 60)), "An active creeper can be stunned");
        test.runAfterDelay(35, () -> {
            test.assertTrue(creeper.isAlive() && creeper.getSwelling(1) <= 0, "Stun resets an ignited creeper fuse each tick beyond its normal explosion time");
            test.assertTrue(cow.isNoAi(), "Rejected stun leaves original NoAI untouched");
            creeper.discard(); cow.discard(); stray.discard(); close(player); test.succeed();
        });
    }

    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void maskTransformsPreservesStandAndDropsAfter101InventoryTicks(GameTestHelper test) {
        var player = player(test, "mask-test", 1.5);
        StandService.grant(player);
        var stand = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        var mask = new ItemStack(VampireItems.STONE_MASK);
        player.setItemSlot(EquipmentSlot.HEAD, mask);
        test.assertTrue(VampireService.activateMask(player), "Blood activates the worn mask");
        test.assertTrue(VampireService.progress(player).fullPower(), "Mask gives full vampirism");
        test.assertValueEqual(VampireService.progress(player).blood(), 300F, "Original acquisition gives 300 blood");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS), stand, "Vampirism remains independent of Stand ownership");
        test.assertValueEqual(mask.getMaxDamage(), 33, "Original helmet material has 33 durability");
        test.assertValueEqual(mask.getDamageValue(), 1, "Activation costs one durability");
        test.assertValueEqual(StoneMaskItem.activationTicks(mask), 101, "Original activated appearance timer");
        test.assertFalse(VampireService.activateMask(player), "Full vampire cannot repeatedly spend mask durability");
        for (int i = 0; i < 100; i++) mask.getItem().inventoryTick(mask, player.level(), player, EquipmentSlot.HEAD);
        test.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD) == mask, "Mask remains worn through tick 100");
        mask.getItem().inventoryTick(mask, player.level(), player, EquipmentSlot.HEAD);
        test.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Mask falls off on tick 101");
        test.assertTrue(!player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(2),
                entity -> entity.getItem().is(VampireItems.STONE_MASK)).isEmpty(), "Used mask remains recoverable");
        close(player); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void weakVampireMaskUpgradeKeepsBloodAndUnlocksClaw(GameTestHelper test) {
        var player = vampire(test, false);
        player.setAttached(VampireAttachments.PROGRESS, VampireService.progress(player).withBlood(690));
        test.assertFalse(VampireService.claw(player), "Blood Gift weak state cannot use claw");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(VampireItems.STONE_MASK));
        test.assertTrue(VampireService.activateMask(player), "Weak state accepts mask upgrade");
        test.assertValueEqual(VampireService.progress(player).blood(), 690F, "Upgrade does not reset stored blood");
        test.assertTrue(VampireService.claw(player), "Full vampire unlocks claw");
        close(player); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void drainUsesOriginalDamageImmunityBloodAndDebuffs(GameTestHelper test) {
        var player = vampire(test, false);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(2.5, 3, 3)); cow.setNoGravity(true);
        float health = cow.getHealth(), blood = VampireService.progress(player).blood();
        test.assertTrue(VampireService.drain(player, cow), "Living animal can be drained");
        test.assertValueEqual(cow.getHealth(), health - 2, "One successful drain deals exactly two damage");
        test.assertValueEqual(cow.getDeltaMovement(), Vec3.ZERO, "Original blood drain suppresses knockback so the victim stays within reach");
        test.assertValueEqual(VampireService.progress(player).blood(), blood + VampireProgress.drainGain(player.level().getDifficulty().getId()), "Animal blood uses difficulty multiplier");
        test.assertFalse(VampireService.drain(player, cow), "Drain respects vanilla hit immunity");
        for (var effect : java.util.List.of(MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS, MobEffects.NAUSEA)) {
            test.assertValueEqual(cow.getEffect(effect).getDuration(), 40, "Successful two-damage drain adds forty ticks of weakness effects");
            test.assertValueEqual(cow.getEffect(effect).getAmplifier(), player.level().getDifficulty().getId() - 1, "Debuff tier follows original difficulty");
        }
        cow.damageCooldownTime = 0;
        VampireService.drain(player, cow);
        test.assertValueEqual(cow.getEffect(MobEffects.WEAKNESS).getDuration(), 80, "Subsequent accepted drain extends debuffs");
        close(player); cow.discard(); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void humanBloodMultipliersAndUndeadExclusionMatchOriginal(GameTestHelper test) {
        var player = vampire(test, false);
        var human = player(test, "drain-target", 3);
        float blood = VampireService.progress(player).blood();
        test.assertTrue(VampireService.drain(player, human), "Human player can be drained");
        test.assertValueEqual(VampireService.progress(player).blood(), blood + 5 * VampireProgress.drainGain(player.level().getDifficulty().getId()), "Player blood multiplier is five");
        var villager = test.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(2.5, 2, 3));
        blood = VampireService.progress(player).blood(); VampireService.drain(player, villager);
        test.assertValueEqual(VampireService.progress(player).blood(), blood + 4 * VampireProgress.drainGain(player.level().getDifficulty().getId()), "NPC blood multiplier is four");
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 2, 3));
        test.assertFalse(VampireService.canDrainBloodFrom(zombie), "Undead cannot feed vampires");
        VampireService.grant(human, false);
        test.assertFalse(VampireService.canDrainBloodFrom(human), "Vampire players cannot feed another vampire");
        close(player); close(human); villager.discard(); zombie.discard(); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void bloodGiftRequiresSixtyTicksAndPaysBothOriginalCosts(GameTestHelper test) {
        var donor = vampire(test, true); var recipient = player(test, "gift-target", 3);
        donor.setAttached(VampireAttachments.PROGRESS, VampireService.progress(donor).withBlood(700));
        recipient.setHealth(6);
        test.assertTrue(VampireService.startHeld(donor, VampireHudState.Held.GIFT), "Weak human at two-block range can receive gift");
        double speed = donor.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(RipplesFabric.id("vampire_action_speed")).amount();
        test.assertTrue(Math.abs(speed + .7) < .0001, "Blood Gift slows movement to thirty percent");
        tick(donor, 59);
        test.assertFalse(VampireService.progress(recipient).vampire(), "Gift cannot transform before sixty ticks");
        float health = donor.getHealth(); tick(donor, 1);
        test.assertTrue(VampireService.progress(recipient).vampire(), "Full hold transforms recipient");
        test.assertFalse(VampireService.progress(recipient).fullPower(), "Blood Gift gives weak vampirism");
        test.assertValueEqual(donor.getHealth(), health - 10, "Donor pays ten health");
        test.assertTrue(VampireService.progress(donor).blood() > 399 && VampireService.progress(donor).blood() <= 400, "Sixty ticks cost 300 blood plus ordinary decay");
        test.assertValueEqual(recipient.getHealth(), recipient.getMaxHealth(), "Recipient heals to vampire maximum");
        test.assertValueEqual(donor.getAttachedOrCreate(VampireAttachments.HUD).held(), VampireHudState.Held.NONE, "Gift ends after one transformation");
        close(donor); close(recipient); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void giftRejectsHealthyTargetsLowDonorsAndCancelsOnLostTarget(GameTestHelper test) {
        var donor = vampire(test, true); var recipient = player(test, "gift-invalid", 3);
        test.assertFalse(VampireService.startHeld(donor, VampireHudState.Held.GIFT), "Healthy human is ineligible");
        recipient.setHealth(6); donor.setHealth(10);
        test.assertFalse(VampireService.startHeld(donor, VampireHudState.Held.GIFT), "Donor must have more than ten health");
        donor.setHealth(20); donor.setAttached(VampireAttachments.PROGRESS, VampireService.progress(donor).withBlood(700));
        test.assertTrue(VampireService.startHeld(donor, VampireHudState.Held.GIFT), "Valid donor begins");
        tick(donor, 10); donor.setYRot(180); tick(donor, 1);
        test.assertValueEqual(donor.getAttachedOrCreate(VampireAttachments.HUD).held(), VampireHudState.Held.NONE, "Leaving target cancels gift");
        test.assertFalse(VampireService.progress(recipient).vampire(), "Interrupted gift cannot transform");
        close(donor); close(recipient); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void heldActionsRespectLineOfSightAndHeartbeatExpiry(GameTestHelper test) {
        var player = vampire(test, false); var cow = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(2.5, 3, 3)); cow.setNoGravity(true);
        test.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        test.assertTrue(VampireService.target(player, 2) == null, "Two-block blood drain cannot pass through stone");
        test.assertTrue(VampireService.startHeld(player, VampireHudState.Held.DRAIN), "Hold may start without a valid target");
        float health = cow.getHealth();
        for (int i = 0; i < 16; i++) VampireService.tick(player);
        test.assertValueEqual(cow.getHealth(), health, "No damage crosses wall");
        test.assertValueEqual(player.getAttachedOrCreate(VampireAttachments.HUD).held(), VampireHudState.Held.NONE, "Lost input heartbeat cannot leave drain running");
        close(player); cow.discard(); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void gettingHitCancelsDrainButClawCanStartWhileDraining(GameTestHelper test) {
        var player = vampire(test, true);
        test.assertTrue(VampireService.startHeld(player, VampireHudState.Held.DRAIN), "Drain starts");
        test.assertTrue(VampireService.claw(player), "Original drain allows another action");
        player.hurtServer(player.level(), player.damageSources().generic(), 1);
        test.assertValueEqual(player.getAttachedOrCreate(VampireAttachments.HUD).held(), VampireHudState.Held.NONE, "Accepted incoming attack cancels drain");
        close(player); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void clawHitsAtFifthTickUsesAttackDamageAndStrongKnockback(GameTestHelper test) {
        var player = vampire(test, true);
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(2.5, 3, 3)); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.setHealth(100);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 4;
        test.assertTrue(VampireService.claw(player), "Full vampire may swipe with free main hand");
        test.assertFalse(VampireService.claw(player), "Repeated packet cannot restart an active claw");
        tick(player, 4); test.assertValueEqual(target.getHealth(), 100F, "No impact during windup");
        tick(player, 1); test.assertValueEqual(target.getHealth(), 100 - damage, "Fifth tick uses original attribute-plus-four formula");
        test.assertTrue(target.getDeltaMovement().z >= 1.9, "Original claw applies strength-two knockback");
        test.assertTrue(VampireService.progress(player).blood() > 299, "Original overridden claw does not actually consume advertised 45 blood");
        tick(player, 7); test.assertValueEqual(player.getAttachedOrCreate(VampireAttachments.HUD).clawTicks(), 0, "Claw ends after twelve ticks");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        test.assertFalse(VampireService.claw(player), "Claw requires a free main hand");
        close(player); target.discard(); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void clawBlockHitBreaksOrthogonalCrossAndLeavesUnbreakableBlocks(GameTestHelper test) {
        var player = vampire(test, true);
        player.setOnGround(true);
        var center = new BlockPos(2, 3, 4);
        test.setBlock(center, Blocks.DIRT);
        for (var direction : net.minecraft.core.Direction.values()) if (direction != net.minecraft.core.Direction.NORTH) test.setBlock(center.relative(direction), Blocks.DIRT);
        test.setBlock(center.above(), Blocks.BEDROCK);
        VampireService.claw(player); tick(player, 5);
        test.assertTrue(test.getBlockState(center).isAir(), "Claw destroys aimed block");
        test.assertTrue(test.getBlockState(center.below()).isAir(), "Claw also destroys adjacent block");
        test.assertTrue(test.getBlockState(center.above()).is(Blocks.BEDROCK), "Negative hardness remains unbreakable");
        close(player); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void originalPassiveHealthBloodTiersFoodAirAndHealingCost(GameTestHelper test) {
        var player = vampire(test, true); int difficulty = player.level().getDifficulty().getId();
        test.assertValueEqual(player.getMaxHealth(), 20F + difficulty * 20, "Health boost is difficulty times five amplifier levels");
        player.setAttached(VampireAttachments.PROGRESS, VampireService.progress(player).withBlood(1000));
        player.setAirSupply(0); player.getFoodData().setFoodLevel(1); VampireService.tick(player);
        int tier = VampireService.progress(player).bloodLevel(difficulty);
        test.assertValueEqual(player.getEffect(MobEffects.REGENERATION).getAmplifier(), Math.min(tier - 2, 4), "Regeneration tier follows blood");
        test.assertValueEqual(player.getFoodData().getFoodLevel(), 17, "Original vampire food level stays seventeen");
        test.assertValueEqual(player.getAirSupply(), player.getMaxAirSupply(), "Vampires do not drown");
        player.setHealth(player.getMaxHealth() - 4);
        float blood = VampireService.progress(player).blood(); player.heal(2);
        test.assertValueEqual(player.getHealth(), player.getMaxHealth() - 2, "Real LivingEntity.heal applies healing");
        test.assertValueEqual(VampireService.progress(player).blood(), blood - 2 * VampireProgress.healCost(difficulty), "Every real heal pays original blood cost");
        player.setAttached(VampireAttachments.PROGRESS, VampireService.progress(player).withBlood(0));
        float health = player.getHealth(); player.heal(10);
        test.assertValueEqual(player.getHealth(), health, "Empty blood prevents healing");
        close(player); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void sunBurnPreventsHealingEvenWithBlood(GameTestHelper test) {
        var player = vampire(test, true); player.setHealth(10);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireItems.SUN_BURN, 60));
        float blood = VampireService.progress(player).blood(); player.heal(10);
        test.assertValueEqual(player.getHealth(), 10F, "Original sunburn suppresses vampire healing");
        test.assertValueEqual(VampireService.progress(player).blood(), blood, "Rejected healing spends no blood");
        close(player); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void weakDeathLosesVampirismFullDeathKeepsBloodAndPower(GameTestHelper test) {
        var weak = vampire(test, false);
        weak.hurtServer(weak.level(), weak.damageSources().genericKill(), Float.MAX_VALUE);
        test.assertFalse(weak.isAlive(), "Weak fixture actually dies before respawn");
        var weakReplacement = weak.level().getServer().getPlayerList().respawn(weak, false, Entity.RemovalReason.KILLED);
        test.assertFalse(VampireService.progress(weakReplacement).vampire(), "Weak Blood Gift state is lost on actual death respawn");
        close(weakReplacement);
        var full = vampire(test, true); full.setAttached(VampireAttachments.PROGRESS, VampireService.progress(full).withBlood(643));
        full.hurtServer(full.level(), full.damageSources().genericKill(), Float.MAX_VALUE);
        test.assertFalse(full.isAlive(), "Full fixture actually dies before respawn");
        var replacement = full.level().getServer().getPlayerList().respawn(full, false, Entity.RemovalReason.KILLED);
        test.assertTrue(VampireService.progress(replacement).fullPower(), "Full vampire remains a vampire after respawn");
        test.assertValueEqual(VampireService.progress(replacement).blood(), 643F, "Original full vampire keeps stored blood after death");
        test.assertValueEqual(replacement.getAttachedOrCreate(VampireAttachments.HUD), VampireHudState.EMPTY, "Transient action state does not survive death");
        close(replacement); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5, maxTicks = 40) public void sunlightDealsFourBypassesHelmetAndStopsUnderRoof(GameTestHelper test) {
        var player = vampire(test, true);
        player.setGameMode(GameType.CREATIVE);
        Vec3 shelter = player.position();
        player.snapTo(player.getX(), 300, player.getZ(), 0, 0);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        test.runAfterDelay(10, () -> {
            var clock = player.level().dimensionType().defaultClock().orElseThrow();
            long previousTime = player.level().clockManager().getInstance(clock).totalTicks();
            float previousRain = player.level().getRainLevel(1), previousThunder = player.level().getThunderLevel(1);
            player.level().setRainLevel(0); player.level().setThunderLevel(0);
            player.level().clockManager().setTotalTicks(clock, 6000);
            player.level().environmentAttributes().invalidateTickCache(); player.level().updateSkyBrightness();
            try {
            test.assertTrue(player.level().isBrightOutside() && !player.level().isRaining(), "GameTest fixture is a clear daytime world; fixed=" + player.level().dimensionType().hasFixedTime() + ", sky=" + player.level().getSkyDarken() + ", rain=" + player.level().isRaining());
            test.assertTrue(player.level().canSeeSky(player.blockPosition().above(2)), "Sun fixture has open sky");
            player.setGameMode(GameType.SURVIVAL); player.setInvulnerableTime(0); player.damageCooldownTime = 0;
            float health = player.getHealth(); VampireService.tickSunDamage(player);
            test.assertValueEqual(player.getHealth(), health - 4, "Original ultraviolet damage ignores helmet armor");
            test.assertValueEqual(player.getEffect(VampireItems.SUN_BURN).getDuration(), 60, "First accepted sun hit gives sixty ticks of sunburn");
            test.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Sunburn adds original weakness");
            Vec3 openSky = player.position(); player.snapTo(shelter, 0, 0);
            test.assertFalse(player.level().canSeeSky(player.blockPosition().above(2)), "Preexisting shelter has propagated sky occlusion");
            player.damageCooldownTime = 0; health = player.getHealth(); VampireService.tickSunDamage(player);
            test.assertValueEqual(player.getHealth(), health, "Solid overhead cover prevents sunlight damage");
            player.snapTo(openSky, 0, 0);
            player.level().setRainLevel(1); player.damageCooldownTime = 0;
            VampireService.tickSunDamage(player);
            test.assertValueEqual(player.getHealth(), health, "Original rain prevents sunlight damage even under open sky");
            player.level().setRainLevel(0);
            player.level().clockManager().setTotalTicks(clock, 18000);
            player.level().environmentAttributes().invalidateTickCache(); player.level().updateSkyBrightness();
            VampireService.tickSunDamage(player);
            test.assertValueEqual(player.getHealth(), health, "Original night prevents sunlight damage");
            close(player); test.succeed();
            } finally {
                player.level().setRainLevel(previousRain); player.level().setThunderLevel(previousThunder);
                player.level().clockManager().setTotalTicks(clock, previousTime);
                player.level().environmentAttributes().invalidateTickCache(); player.level().updateSkyBrightness();
            }
        });
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5, maxTicks = 30) public void disconnectQueuesVampireActionCleanupOnServerThread(GameTestHelper test) {
        var player = vampire(test, true);
        test.assertTrue(VampireService.claw(player), "Claw fixture creates transient action and movement state");
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try { net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.connection, player.level().getServer()); }
            catch (Throwable thrown) { failure.set(thrown); }
        }, "vampire-disconnect-test");
        worker.start();
        try { worker.join(3000); } catch (InterruptedException error) { throw new AssertionError(error); }
        test.assertFalse(worker.isAlive(), "Network callback only enqueues cleanup");
        test.assertTrue(failure.get() == null, "Network thread cannot mutate synced attachments: " + failure.get());
        test.assertTrue(player.getAttachedOrCreate(VampireAttachments.HUD).clawTicks() > 0, "Action remains untouched until server queue executes");
        test.runAfterDelay(2, () -> {
            test.assertValueEqual(player.getAttachedOrCreate(VampireAttachments.HUD), VampireHudState.EMPTY, "Server cleanup clears transient action");
            test.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(RipplesFabric.id("vampire_action_speed")) == null, "Server cleanup removes held movement penalty");
            test.assertTrue(VampireService.progress(player).vampire(), "Disconnect preserves persistent power");
            close(player); test.succeed();
        });
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void milkKeepsOriginalHiddenPassivesWithoutLosingMaximumHealth(GameTestHelper test) {
        var player = vampire(test, true);
        float maximum = player.getMaxHealth(), health = player.getHealth();
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireItems.SUN_BURN, 60, 0, false, false, false));
        var milk = new ItemStack(Items.MILK_BUCKET);
        milk.finishUsingItem(player.level(), player);
        test.assertValueEqual(player.getMaxHealth(), maximum, "Milk cannot remove the vampire health boost even momentarily");
        test.assertValueEqual(player.getHealth(), health, "Milk cannot destroy vampire health above the human maximum");
        test.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "Original hidden vampire night vision survives milk");
        test.assertFalse(player.hasEffect(MobEffects.POISON), "Ordinary negative effects remain curable");
        test.assertTrue(player.hasEffect(VampireItems.SUN_BURN), "Original Sun Burn cannot be cured with milk");
        test.assertTrue(player.removeEffect(VampireItems.SUN_BURN), "Consumable immunity does not block explicit removal of Sun Burn");
        test.assertFalse(player.removeEffect(MobEffects.NIGHT_VISION), "Direct effect removal also preserves original hidden passives");
        player.forceAddEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0, false, true, true), player);
        test.assertTrue(player.removeEffect(MobEffects.NIGHT_VISION), "Visible potion effects are not protected as vampire passives");
        close(player); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void bloodGiftContinuesAfterHitWhenHealthConditionsStillHold(GameTestHelper test) {
        var donor = vampire(test, true); var recipient = player(test, "gift-hit-target", 3);
        donor.setAttached(VampireAttachments.PROGRESS, VampireService.progress(donor).withBlood(700)); recipient.setHealth(6);
        test.assertTrue(VampireService.startHeld(donor, VampireHudState.Held.GIFT), "Gift fixture begins");
        tick(donor, 10);
        donor.hurtServer(donor.level(), donor.damageSources().generic(), 1);
        test.assertValueEqual(donor.getAttachedOrCreate(VampireAttachments.HUD).held(), VampireHudState.Held.GIFT, "Original gift does not cancel simply because donor was hit");
        test.assertValueEqual(donor.getAttachedOrCreate(VampireAttachments.HUD).heldTicks(), 10, "Damage preserves gift hold progress");
        tick(donor, 50);
        test.assertTrue(VampireService.progress(recipient).vampire(), "Still-eligible damaged donor completes the original sixty-tick gift");
        close(donor); close(recipient); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void persistenceCodecRoundTripsAndRejectsCorruptBlood(GameTestHelper test) {
        var progress = new VampireProgress(true, true, 521.25F);
        var encoded = VampireAttachments.CODEC.encodeStart(JsonOps.INSTANCE, progress).getOrThrow();
        test.assertValueEqual(VampireAttachments.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(), progress, "Persistent vampire attachment preserves state exactly");
        var bad = com.google.gson.JsonParser.parseString("{\"vampire\":true,\"blood\":-1}");
        test.assertTrue(VampireAttachments.CODEC.parse(JsonOps.INSTANCE, bad).error().isPresent(), "Corrupt negative resource fails gracefully");
        var badHuman = com.google.gson.JsonParser.parseString("{\"vampire\":false,\"full_power\":true,\"blood\":200}");
        test.assertTrue(VampireAttachments.CODEC.parse(JsonOps.INSTANCE, badHuman).error().isPresent(), "Invalid human vampire fields cannot grant power");
        test.succeed();
    }
}
