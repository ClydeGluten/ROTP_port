package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.core.StandProgress;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Original item volleys and projectile behavior, including the five-tick Time Stop flight exception. */
public final class KnifeGameTests {
    private record Volley(String stand, int count, boolean shift, boolean creative, int thrown, int remaining, int cooldown) { }

    @GameTest
    public void useMatchesOriginalVolleyConsumptionAndCooldown(GameTestHelper test) {
        var cases = List.of(
                new Volley(null, 16, false, false, 8, 8, 24),
                new Volley(StandProgress.STAR_PLATINUM, 16, false, false, 8, 8, 24),
                new Volley(StandProgress.THE_WORLD, 16, false, false, 16, 0, 24),
                new Volley(StandProgress.THE_WORLD, 9, false, false, 9, 0, 24),
                new Volley(StandProgress.THE_WORLD, 8, false, false, 8, 0, 24),
                new Volley(StandProgress.THE_WORLD, 16, true, false, 1, 15, 3),
                new Volley(null, 16, true, false, 1, 15, 3),
                new Volley(StandProgress.THE_WORLD, 1, false, true, 2, 1, 3),
                new Volley(StandProgress.THE_WORLD, 16, false, true, 16, 16, 24));
        test.assertValueEqual(new ItemStack(StandItems.KNIFE).getMaxStackSize(), 16, "Original knife maximum stack is sixteen");
        for (Volley scenario : cases) {
            var player = player(test, scenario.creative());
            player.setShiftKeyDown(scenario.shift());
            if (scenario.stand() != null) {
                player.setAttached(StandAttachments.PROGRESS, new StandProgress(scenario.stand(), 0, 0, true));
                test.assertTrue(StandService.toggle(player), "Stand summons without progression or stamina requirements for knife throws");
                StandService.current(player).setPos(player.position().add(1, 0, 0));
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StandItems.KNIFE, scenario.count()));
            test.assertTrue(use(player).consumesAction(), scenario + ": actual item use succeeds");
            var knives = ownedKnives(player);
            test.assertValueEqual(knives.size(), scenario.thrown(), scenario + ": primary volley and remaining-stack Stand volley match");
            test.assertValueEqual(player.getMainHandItem().getCount(), scenario.remaining(), scenario + ": exact Survival or Creative consumption");
            for (var knife : knives) {
                test.assertTrue(knife.getOwner() == player, "Both player and Stand volleys credit the player");
                test.assertValueEqual(knife.getTimeStopFlightTicks(), 5, "Every hand-thrown knife receives five lifetime flight ticks");
                test.assertValueEqual(knife.pickup, scenario.creative() ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED,
                        "Creative projectiles cannot create recoverable Survival ammunition");
                test.assertTrue(Double.isFinite(knife.getDeltaMovement().lengthSqr()) && knife.getDeltaMovement().lengthSqr() > 0,
                        "Actual item use launches each knife with finite nonzero velocity");
                var stand = StandService.current(player);
                if (stand != null && stand.isTheWorld() && Math.abs(knife.getX() - stand.getX()) < 0.00001) {
                    Vec3 origin = knife.position();
                    Vec3 launch = knife.getDeltaMovement();
                    test.assertTrue(stand.getBoundingBox().contains(origin), "Bonus knife starts inside The World's body");
                    knife.setTimeStopped(true);
                    knife.tick();
                    test.assertFalse(knife.isRemoved(), "Stand-origin knife survives its first actual flight tick");
                    test.assertValueEqual(knife.getTimeStopFlightTicks(), 4, "Exiting its own Stand cannot trigger an early frozen entity impact");
                    test.assertTrue(knife.position().subtract(origin).dot(launch) > 0
                                    && knife.getDeltaMovement().dot(launch) > 0,
                            "Stand-origin knife exits forward without bouncing from its own Stand");
                    knife.setTimeStopped(false);
                }
            }

            var cooldownProbe = new ItemStack(StandItems.KNIFE);
            test.assertTrue(player.getCooldowns().isOnCooldown(cooldownProbe), "Throw starts an item cooldown");
            player.setItemInHand(InteractionHand.MAIN_HAND, cooldownProbe.copy());
            use(player);
            test.assertValueEqual(ownedKnives(player).size(), scenario.thrown(), "Normal item-use entry point rejects a second volley during cooldown");
            for (int tick = 1; tick < scenario.cooldown(); tick++) player.getCooldowns().tick();
            test.assertTrue(player.getCooldowns().isOnCooldown(cooldownProbe), "Cooldown lasts three ticks per primary knife, regardless of the Stand volley");
            player.getCooldowns().tick();
            test.assertFalse(player.getCooldowns().isOnCooldown(cooldownProbe), "Cooldown expires on the exact original tick");
            knives.forEach(Entity::discard);
            finish(player);
        }
        test.succeed();
    }

    @GameTest
    public void knifeDamageIsFractionalAndPreservesPriorHitImmunity(GameTestHelper test) {
        var player = player(test, false);
        var target = target(test, new Vec3(3.5, 3, 4.5));
        var vanilla = player.damageSources().playerAttack(player);
        test.assertTrue(target.hurtServer(test.getLevel(), vanilla, 8), "Initial ordinary attack establishes vanilla immunity");
        int cooldown = target.damageCooldownTime;
        for (int hit = 0; hit < 2; hit++) {
            var knife = new TestKnife(test.getLevel(), player);
            knife.setDeltaMovement(0, 0, 1.25);
            knife.hit(target);
            test.assertTrue(knife.isRemoved(), "Accepted knife hit consumes its projectile");
            test.assertTrue(target.getLastDamageSource().getEntity() == player
                            && target.getLastDamageSource().getDirectEntity() == knife,
                    "Knife damage retains the player and direct projectile source");
            test.assertValueEqual(target.damageCooldownTime, cooldown, "Each knife preserves the previous vanilla immunity timer");
        }
        assertClose(test, target.getHealth(), 87, "Two speed-1.25 knives deal exactly 2.5 damage each, without arrow-style rounding");
        test.assertTrue(target.getLastHurtByMob() == player, "Knife hits credit the owner for target reactions");
        test.assertFalse(target.hurtServer(test.getLevel(), vanilla, 8), "Original lastHurt still rejects an equal ordinary hit");
        test.assertTrue(target.hurtServer(test.getLevel(), vanilla, 10), "A stronger ordinary hit remains accepted");
        assertClose(test, target.getHealth(), 85, "The stronger ordinary hit applies only two excess damage");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void rejectedKnifeHitBouncesAndRestoresFire(GameTestHelper test) {
        var player = player(test, false);
        var target = target(test, new Vec3(3.5, 3, 4.5));
        target.setPermanentlyInvulnerable(true);
        target.setRemainingFireTicks(7);
        var knife = new TestKnife(test.getLevel(), player);
        knife.setDeltaMovement(1, 2, 3);
        knife.setRemainingFireTicks(80);
        knife.hit(target);
        test.assertFalse(knife.isRemoved(), "Rejected knife remains available to bounce and land");
        assertVector(test, knife.getDeltaMovement(), new Vec3(-0.1, -0.2, -0.3), "Rejected hit reverses velocity at one tenth speed");
        test.assertValueEqual(target.getHealth(), 100F, "Invulnerability rejects damage");
        test.assertValueEqual(target.getRemainingFireTicks(), 7, "Rejected burning knife restores the target's previous fire timer");
        var slow = new TestKnife(test.getLevel(), player);
        slow.setPos(test.absoluteVec(new Vec3(2.5, 3, 2.5)));
        slow.setDeltaMovement(0, 0, 0.00001);
        slow.hit(target);
        test.assertTrue(slow.isRemoved(), "A rejected projectile below the original rebound-speed threshold is removed");
        int drops = test.getLevel().getEntitiesOfClass(ItemEntity.class, slow.getBoundingBox().inflate(1),
                item -> item.getItem().is(StandItems.KNIFE)).stream().mapToInt(item -> item.getItem().getCount()).sum();
        test.assertValueEqual(drops, 1, "A nearly stationary rejected Survival knife drops exactly one recoverable item");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void rejectedFrontTargetPreventsSameTickDamageBehindIt(GameTestHelper test) {
        var front = target(test, new Vec3(3.5, 3, 3.5));
        var behind = target(test, new Vec3(3.5, 3, 5));
        front.setPermanentlyInvulnerable(true);
        var knife = knife(test, new Vec3(3.5, 4, 1.5), new Vec3(0, 0, 4));
        knife.tick();
        test.assertValueEqual(front.getHealth(), 100F, "Front target rejects the actual ray impact");
        test.assertValueEqual(behind.getHealth(), 100F, "Original non-piercing knife cannot hit a second target behind a rejected first contact");
        test.assertFalse(knife.isRemoved(), "Rejected knife survives to travel back from its first contact");
        test.assertTrue(knife.getDeltaMovement().z < 0, "Actual ray impact retains the original rebound direction");
        assertVector(test, knife.position(), test.absoluteVec(new Vec3(3.5, 4, 1.1)),
                "Rejected hit advances from the original pre-impact position using reversed velocity");
        knife.discard();
        test.succeed();
    }

    @GameTest
    public void newbornKnifeHasOriginalFixedTargetMargin(GameTestHelper test) {
        var target = target(test, new Vec3(3.5, 3, 4.5));
        var knife = new TestKnife(test.getLevel());
        knife.setPos(target.getBoundingBox().maxX + 0.15, target.getY() + 1, target.getZ() - 2);
        knife.setNoGravity(true);
        knife.setDeltaMovement(0, 0, 3);
        test.getLevel().addFreshEntity(knife);
        test.assertValueEqual(knife.tickCount, 0, "Fixture starts on the projectile's first flight tick");
        knife.tick();
        test.assertTrue(target.getHealth() < 100, "Original fixed 0.3 target margin catches a newborn knife 0.15 outside the physical hitbox");
        test.assertTrue(knife.isRemoved(), "Margin contact is a real accepted impact");
        test.succeed();
    }

    @GameTest
    public void launchSpreadMatchesOriginalSeededGaussianVolley(GameTestHelper test) {
        var knife = new TestKnife(test.getLevel());
        knife.getRandom().setSeed(42L);
        knife.shoot(0, 0, 1, 1.5F, 16F);
        // Golden 1.16.5 Projectile.shoot vector for Java Random seed 42; spread is not renormalized.
        assertVector(test, knife.getDeltaMovement(), new Vec3(0.20554295219090676, 0.16549342711783538, 1.32902400918125),
                "Wide volleys preserve original Gaussian spread and its fractional speed-dependent damage");
        test.succeed();
    }

    @GameTest
    public void singletonDamageAndToolRulesMatchOriginalItem(GameTestHelper test) {
        var knife = new ItemStack(StandItems.KNIFE);
        double[] damage = {0};
        double[] attackSpeed = {0};
        knife.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage[0] += modifier.amount();
            if (attribute.equals(Attributes.ATTACK_SPEED)) attackSpeed[0] += modifier.amount();
        });
        assertClose(test, damage[0], 2, "Exactly one knife grants the original +2 main-hand attack damage");
        assertClose(test, attackSpeed[0], 0, "Knife does not impose a sword-style attack-speed modifier");
        double[] tooltipDamage = {0};
        knife.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier, display) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) tooltipDamage[0] += modifier.amount();
        });
        assertClose(test, tooltipDamage[0], 2, "Tooltip iterator reports the same single-knife damage as equipment");
        double[] offhandDamage = {0};
        knife.forEachModifier(EquipmentSlot.OFFHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) offhandDamage[0] += modifier.amount();
        });
        assertClose(test, offhandDamage[0], 0, "Knife weapon bonus does not apply in the offhand");
        test.assertValueEqual(knife.getDestroySpeed(Blocks.COBWEB.defaultBlockState()), 15F, "Single knife cuts cobweb at original speed");
        test.assertValueEqual(knife.getDestroySpeed(Blocks.OAK_LEAVES.defaultBlockState()), 1.5F, "Single knife has the original plant speed");
        test.assertValueEqual(knife.getDestroySpeed(Blocks.STONE.defaultBlockState()), 1F, "Other blocks retain ordinary mining speed");
        test.assertTrue(knife.isCorrectToolForDrops(Blocks.COBWEB.defaultBlockState()), "Knife is a valid cobweb harvesting tool");
        knife.setCount(2);
        damage[0] = 0;
        knife.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage[0] += modifier.amount();
        });
        assertClose(test, damage[0], 0, "Increasing the stack above one removes the weapon bonus immediately");
        test.assertValueEqual(knife.getDestroySpeed(Blocks.COBWEB.defaultBlockState()), 1F, "Multiple knives do not retain single-knife mining speed");
        test.succeed();
    }

    @GameTest
    public void knifeStripsLogsWithoutConsumptionAndDispenserCreatesRecoverableProjectile(GameTestHelper test) {
        var player = player(test, false);
        var stack = new ItemStack(StandItems.KNIFE, 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos log = new BlockPos(3, 1, 3);
        test.setBlock(log, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        var hit = new BlockHitResult(Vec3.atCenterOf(test.absolutePos(log)), Direction.UP, test.absolutePos(log), false);
        test.assertTrue(StandItems.KNIFE.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                "A full knife stack can strip logs through actual item interaction");
        test.assertTrue(test.getBlockState(log).is(Blocks.STRIPPED_OAK_LOG), "Knife produces the correct stripped log");
        test.assertValueEqual(test.getBlockState(log).getValue(RotatedPillarBlock.AXIS), Direction.Axis.X, "Stripping preserves log orientation");
        test.assertValueEqual(stack.getCount(), 16, "Stripping consumes no knives");
        test.assertValueEqual(stack.getDamageValue(), 0, "Stripping adds no invented durability cost");
        test.setBlock(log, Blocks.COPPER_BLOCK.waxed().unaffected());
        StandItems.KNIFE.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        test.assertTrue(test.getBlockState(log).is(Blocks.COPPER_BLOCK.waxed().unaffected()), "Original stripping does not gain modern axe wax removal");

        var projectileItem = (ProjectileItem) StandItems.KNIFE;
        var projectile = projectileItem.asProjectile(test.getLevel(), test.absoluteVec(new Vec3(3.5, 4, 3.5)), stack, Direction.SOUTH);
        test.assertTrue(projectile instanceof KnifeEntity, "Modern dispenser API creates the knife entity");
        var knife = (KnifeEntity) projectile;
        test.assertTrue(knife.getOwner() == null, "Dispensed knives have no invented player owner");
        test.assertValueEqual(knife.pickup, AbstractArrow.Pickup.ALLOWED, "Dispensed knives are recoverable");
        test.assertValueEqual(knife.getTimeStopFlightTicks(), 0, "Original dispenser path grants no hand-throw Time Stop grace");
        projectileItem.shoot(knife, 0, 0, 1, 1.1F, 0);
        double forwardSpeed = 1.1F / Math.sqrt(1 + (double) 0.1F * 0.1F);
        assertClose(test, knife.getDeltaMovement().z, forwardSpeed, "Dispenser launch preserves speed while adding the original slight lift");
        assertClose(test, knife.getDeltaMovement().y, forwardSpeed * 0.1F, "Dispenser launch includes original 0.1 direction lift");
        finish(player);
        test.succeed();
    }

    @GameTest
    public void ownerProjectilesAndDisabledPvpStayProtected(GameTestHelper test) {
        var owner = player(test, false);
        var other = player(test, false);
        var knife = new TestKnife(test.getLevel(), owner);
        var friendly = new TestKnife(test.getLevel(), owner);
        var hostile = new TestKnife(test.getLevel(), other);
        test.assertFalse(knife.accepts(owner), "A newly launched knife cannot hit its owner before leaving the owner");
        test.assertFalse(knife.accepts(friendly), "Knives cannot hit another projectile from the same owner");
        test.assertTrue(knife.accepts(hostile), "Another owner's pickable knife is not excluded as friendly ammunition");
        boolean previousPvp = test.getLevel().getGameRules().get(GameRules.PVP);
        try {
            test.getLevel().getGameRules().set(GameRules.PVP, false, test.getLevel().getServer());
            float health = other.getHealth();
            knife.setDeltaMovement(0, 0, 1.5);
            knife.hit(other);
            test.assertValueEqual(other.getHealth(), health, "Projectile damage respects the server's PvP rule");
        } finally {
            test.getLevel().getGameRules().set(GameRules.PVP, previousPvp, test.getLevel().getServer());
            finish(owner);
            finish(other);
        }
        test.succeed();
    }

    @GameTest
    public void fiveFrozenFlightTicksBelongToTheProjectileLifetime(GameTestHelper test) {
        var knife = knife(test, new Vec3(1.5, 4.5, 1.5), new Vec3(0.2, 0, 0));
        knife.setTimeStopFlightTicks(5);
        knife.setTimeStopped(true);
        for (int tick = 0; tick < 5; tick++) {
            Vec3 before = knife.position();
            knife.tick();
            test.assertTrue(knife.position().distanceToSqr(before) > 0.001, "Each of the first five frozen ticks advances flight");
            test.assertValueEqual(knife.getTimeStopFlightTicks(), 4 - tick, "Frozen flight decrements the stored budget once per tick");
        }
        Vec3 stopped = knife.position();
        Vec3 momentum = knife.getDeltaMovement();
        test.assertTrue(knife.isTimeStopFrozen(), "Exhausted grace freezes the knife");
        for (int tick = 0; tick < 4; tick++) knife.tick();
        assertVector(test, knife.position(), stopped, "Sixth and later frozen ticks cannot move the knife");
        assertVector(test, knife.getDeltaMovement(), momentum, "Frozen flight preserves the resume velocity");
        knife.setTimeStopped(false);
        knife.tick();
        test.assertTrue(knife.position().distanceToSqr(stopped) > 0.001, "Resume restores flight");
        stopped = knife.position();
        knife.setTimeStopped(true);
        knife.tick();
        assertVector(test, knife.position(), stopped, "A second Time Stop cannot grant another five flight ticks");
        knife.discard();
        test.succeed();
    }

    @GameTest
    public void frozenEntityContactWaitsAndReevaluatesTheTargetOnResume(GameTestHelper test) {
        var original = target(test, new Vec3(3.5, 3, 4.5));
        var knife = knife(test, new Vec3(3.5, 4, 2.5), new Vec3(0, 0, 1.5));
        knife.setTimeStopFlightTicks(5);
        knife.setTimeStopped(true);
        for (int tick = 0; tick < 5 && !knife.isTimeStopFrozen(); tick++) knife.tick();
        test.assertTrue(knife.isTimeStopFrozen() && !knife.isRemoved(), "Entity contact freezes the projectile without consuming it");
        test.assertValueEqual(knife.getTimeStopFlightTicks(), 0, "Entity contact immediately exhausts the remaining flight grace");
        test.assertValueEqual(original.getHealth(), 100F, "Frozen contact does not apply projectile damage");
        test.assertTrue(knife.getZ() < original.getBoundingBox().minZ, "Knife retains its position before the deferred contact");
        test.assertTrue(knife.getDeltaMovement().length() > 1, "The original flight velocity remains available after contact");
        original.setPos(original.position().add(2, 0, 0));
        var replacement = target(test, new Vec3(3.5, 3, 4.5));
        knife.setTimeStopped(false);
        knife.tick();
        test.assertValueEqual(original.getHealth(), 100F, "Moved target is not damaged by a replayed stale collision");
        test.assertTrue(replacement.getHealth() < 100, "Resumed flight traces the world again and hits the entity now in its path");
        test.assertTrue(knife.isRemoved(), "Successful resumed impact consumes the projectile once");
        test.succeed();
    }

    @GameTest
    public void blockContactStillHappensDuringFrozenFlightGrace(GameTestHelper test) {
        test.setBlock(new BlockPos(3, 4, 4), Blocks.STONE);
        var knife = knife(test, new Vec3(3.5, 4.5, 2.5), new Vec3(0, 0, 2));
        knife.setTimeStopFlightTicks(5);
        knife.setTimeStopped(true);
        knife.tick();
        test.assertTrue(knife.embedded(), "A solid block embeds the knife immediately during grace");
        test.assertFalse(knife.isRemoved(), "Embedded knife remains recoverable");
        test.assertValueEqual(knife.getTimeStopFlightTicks(), 4, "A block hit does not masquerade as a deferred entity collision");
        test.assertFalse(knife.isNoGravity(), "Ground impact restores normal gravity behavior");
        knife.discard();
        test.succeed();
    }

    @GameTest
    public void pickupRequiresGroundAndRespectsCreativeAndInventoryRules(GameTestHelper test) {
        var owner = player(test, false);
        var collector = player(test, false);
        var knife = new TestKnife(test.getLevel(), owner);
        test.getLevel().addFreshEntity(knife);
        knife.playerTouch(owner);
        test.assertFalse(knife.isRemoved(), "Owner cannot catch an airborne knife");
        knife.ground();
        knife.playerTouch(collector);
        test.assertTrue(knife.isRemoved(), "Another player can recover an embedded Survival knife");
        test.assertValueEqual(knifeCount(collector), 1, "Recovering an embedded knife adds exactly one item");

        for (int slot = 0; slot < 36; slot++) collector.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        var fullInventory = new TestKnife(test.getLevel(), owner);
        test.getLevel().addFreshEntity(fullInventory);
        fullInventory.ground();
        fullInventory.playerTouch(collector);
        test.assertFalse(fullInventory.isRemoved(), "A full inventory leaves the embedded item in the world");

        var creative = player(test, true);
        var creativeKnife = new TestKnife(test.getLevel(), creative);
        test.getLevel().addFreshEntity(creativeKnife);
        creativeKnife.ground();
        creativeKnife.playerTouch(owner);
        test.assertFalse(creativeKnife.isRemoved(), "Survival cannot pick up Creative ammunition");
        int before = knifeCount(creative);
        creativeKnife.playerTouch(creative);
        test.assertTrue(creativeKnife.isRemoved(), "Creative can clear its embedded projectile");
        test.assertValueEqual(knifeCount(creative), before, "Creative-only pickup does not add a duplicate item");
        fullInventory.discard();
        finish(owner);
        finish(collector);
        finish(creative);
        test.succeed();
    }

    @GameTest
    public void saveLoadPreservesKnifeOwnerFlightBudgetAndPickup(GameTestHelper test) {
        var owner = player(test, false);
        var knife = new TestKnife(test.getLevel(), owner);
        knife.setPos(test.absoluteVec(new Vec3(2.5, 4, 3.5)));
        knife.setDeltaMovement(0.2, -0.1, 0.3);
        knife.setTimeStopFlightTicks(2);
        knife.setTimeStopped(true);
        knife.setKnifeTexVariant(KnifeEntity.TexVariant.SCALPEL);
        knife.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        knife.tickCount = 123;
        knife.ground();
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        knife.saveWithoutId(output);
        var restored = new TestKnife(test.getLevel());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), output.buildResult()));
        test.assertTrue(restored.getOwner() == owner, "Projectile owner survives actual entity serialization");
        test.assertValueEqual(restored.getTimeStopFlightTicks(), 2, "Reload preserves the remaining lifetime grace budget");
        test.assertValueEqual(restored.getKnifeTexVariant(), KnifeEntity.TexVariant.SCALPEL, "Texture variant survives serialization");
        test.assertValueEqual(restored.getKnifeTexture(), knife.getKnifeTexture(), "Restored projectile resolves the same texture");
        test.assertValueEqual(restored.pickup, AbstractArrow.Pickup.CREATIVE_ONLY, "Creative pickup restriction survives serialization");
        test.assertValueEqual(restored.tickCount, 123, "Original custom projectile Age survives serialization");
        test.assertTrue(restored.embedded(), "Embedded state survives serialization");
        assertVector(test, restored.position(), knife.position(), "Position survives serialization");
        assertVector(test, restored.getDeltaMovement(), knife.getDeltaMovement(), "Resume velocity survives serialization");
        test.assertTrue(restored.pickupItem().is(StandItems.KNIFE), "Restored projectile yields the correct pickup item");
        finish(owner);
        test.succeed();
    }

    @GameTest
    public void flyingKnifeCutsCobwebAndTripwire(GameTestHelper test) {
        var obstacle = new BlockPos(3, 4, 4);
        test.setBlock(obstacle, Blocks.COBWEB);
        var knife = knife(test, new Vec3(3.5, 4.05, 3.1), new Vec3(0, 0, 0.5));
        knife.tick();
        test.assertTrue(test.getBlockState(obstacle).isAir(), "Knife's next-segment outline trace cuts cobweb");
        assertClose(test, knife.getDeltaMovement().z, 0.5 * 0.99 * 0.8, "Cobweb cutting applies the original extra 0.8 velocity multiplier");
        knife.discard();
        test.setBlock(obstacle, Blocks.TRIPWIRE);
        var tripwireKnife = knife(test, new Vec3(3.5, 4.05, 3.1), new Vec3(0, 0, 0.5));
        tripwireKnife.tick();
        test.assertTrue(test.getBlockState(obstacle).isAir(), "Knife's next-segment outline trace cuts tripwire");
        assertClose(test, tripwireKnife.getDeltaMovement().z, 0.5 * 0.99, "Tripwire cutting adds no cobweb slowdown");
        tripwireKnife.discard();
        test.succeed();
    }

    @GameTest(environment = "jojo:knife_time_stop", maxTicks = 110)
    public void regionalTimeStopAllowsGraceThenResumesActualFlight(GameTestHelper test) {
        var owner = player(test, true);
        owner.setAttached(StandAttachments.PROGRESS, StandProgress.grant(StandProgress.THE_WORLD, true));
        owner.setAttached(StandAttachments.THE_WORLD_RESOLVE, ResolveProgress.EMPTY.withLevel(4).withTraining(95));
        test.assertTrue(StandService.toggle(owner), "The World summons");
        test.assertTrue(TimeStopService.toggle(owner), "Actual regional Time Stop starts charging");
        TestKnife[] projectile = new TestKnife[1];
        Vec3[] stopped = new Vec3[1];
        test.startSequence()
                .thenWaitUntil(() -> test.assertTrue(TimeStopService.isActive(owner), "Wait for regional Time Stop activation"))
                .thenExecute(() -> {
                    projectile[0] = knife(test, new Vec3(2.5, 4.5, 1.5), new Vec3(0, 0, 0.2));
                    projectile[0].setTimeStopFlightTicks(5);
                })
                .thenIdle(7)
                .thenExecute(() -> {
                    var knife = projectile[0];
                    test.assertValueEqual(knife.getTimeStopFlightTicks(), 0, "Server tick mixin permits exactly the flight grace before freezing");
                    test.assertTrue(knife.isTimeStopFrozen(), "Regional freeze reaches the projectile after grace");
                    double distance = knife.position().distanceTo(test.absoluteVec(new Vec3(2.5, 4.5, 1.5)));
                    test.assertTrue(distance > 0.8 && distance < 1.1, "Five actual flight steps occur before regional ticking stops");
                    stopped[0] = knife.position();
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    assertVector(test, projectile[0].position(), stopped[0], "Regional freeze keeps exhausted knife stationary");
                    TimeStopService.cancel(owner);
                })
                .thenWaitUntil(() -> test.assertTrue(projectile[0].position().distanceToSqr(stopped[0]) > 0.01,
                        "Authoritative resume restarts actual projectile movement"))
                .thenExecute(() -> {
                    test.assertValueEqual(projectile[0].getTimeStopFlightTicks(), 0, "Resume does not refill lifetime grace");
                    projectile[0].discard();
                    finish(owner);
                })
                .thenSucceed();
    }

    private static net.minecraft.world.InteractionResult use(ServerPlayer player) {
        return player.gameMode.useItem(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    private static List<KnifeEntity> ownedKnives(ServerPlayer player) {
        return player.level().getEntitiesOfClass(KnifeEntity.class, player.getBoundingBox().inflate(16), knife -> knife.getOwner() == player);
    }

    private static TestKnife knife(GameTestHelper test, Vec3 position, Vec3 velocity) {
        var knife = new TestKnife(test.getLevel());
        knife.setPos(test.absoluteVec(position));
        knife.setNoGravity(true);
        knife.setDeltaMovement(velocity);
        test.getLevel().addFreshEntity(knife);
        return knife;
    }

    private static LivingEntity target(GameTestHelper test, Vec3 position) {
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, position);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(100);
        return target;
    }

    private static ServerPlayer player(GameTestHelper test, boolean creative) {
        var level = test.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "knife-test"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.setInvulnerableTime(0);
        player.snapTo(test.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        player.setNoGravity(true);
        return player;
    }

    private static int knifeCount(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item.is(StandItems.KNIFE)) count += item.getCount();
        }
        return count;
    }

    private static void finish(ServerPlayer player) {
        StandService.clear(player);
        player.connection.disconnect(new DisconnectionDetails(Component.literal("Knife test complete")));
    }

    private static void assertClose(GameTestHelper test, double actual, double expected, String message) {
        test.assertTrue(Math.abs(actual - expected) < 0.00001, message + "; expected=" + expected + ", actual=" + actual);
    }

    private static void assertVector(GameTestHelper test, Vec3 actual, Vec3 expected, String message) {
        test.assertTrue(actual.distanceToSqr(expected) < 1.0E-10, message + "; expected=" + expected + ", actual=" + actual);
    }

    private static final class TestKnife extends KnifeEntity {
        private TestKnife(Level level) { super(RipplesFabric.KNIFE, level); }
        private TestKnife(Level level, LivingEntity owner) { super(level, owner); }
        private void hit(Entity target) { onHit(new EntityHitResult(target)); }
        private boolean accepts(Entity target) { return canHitEntity(target); }
        private void ground() { setInGround(true); }
        private boolean embedded() { return isInGround(); }
        private ItemStack pickupItem() { return getPickupItem(); }
    }
}
