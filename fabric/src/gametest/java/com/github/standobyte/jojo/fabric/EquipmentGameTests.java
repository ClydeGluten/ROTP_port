package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.equipment.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** World/entity tests for source equipment paths; client presentation remains a separate client check. */
public final class EquipmentGameTests {
    private static ServerPlayer player(GameTestHelper test) { return VampireGameTests.player(test,"equipment",1.5); }
    private static void close(ServerPlayer player) { player.level().getServer().getPlayerList().remove(player); }

    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void hatSneakEquipsAndNormalUsePreservesThrownComponents(GameTestHelper test) {
        var user=player(test); var stack=new ItemStack(EquipmentContent.BLADE_HAT);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Speedwagon")); stack.setDamageValue(7);
        stack.enchant(test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS),3);
        var original=stack.copy();
        user.setItemInHand(InteractionHand.MAIN_HAND,stack); user.setShiftKeyDown(true);
        test.assertTrue(EquipmentContent.BLADE_HAT.use(test.getLevel(),user,InteractionHand.MAIN_HAND).consumesAction(),"Sneak equips empty head slot");
        var worn=user.getItemBySlot(EquipmentSlot.HEAD);
        test.assertTrue(ItemStack.isSameItemSameComponents(worn,original),"Equipping retains the named enchanted stack");
        user.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(EquipmentContent.BLADE_HAT));
        test.assertFalse(EquipmentContent.BLADE_HAT.use(test.getLevel(),user,InteractionHand.MAIN_HAND).consumesAction(),"Source refuses occupied armor slot");
        user.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY); user.setItemInHand(InteractionHand.MAIN_HAND,worn); user.setShiftKeyDown(false);
        var expected=worn.copy(); EquipmentContent.BLADE_HAT.use(test.getLevel(),user,InteractionHand.MAIN_HAND);
        var hats=test.getLevel().getEntitiesOfClass(BladeHatEntity.class,user.getBoundingBox().inflate(4),hat->hat.getOwner()==user);
        test.assertValueEqual(hats.size(),1,"Actual use spawns exactly one owned hat");
        var hat=hats.getFirst(); test.assertTrue(ItemStack.isSameItemSameComponents(hat.getThrownStack(),expected),"Throw retains components");
        test.assertValueEqual(hat.pickup,AbstractArrow.Pickup.ALLOWED,"Survival throw can be recovered");
        // VampireGameTests.player puts a stone shelter at localY5; keep this flight below it and outside the owner's box.
        hat.setPos(user.position().add(0,1.5,2.5));hat.setDeltaMovement(0,0,.75);hat.tickCount=30;
        // Minecraft26.3 advances entity age in commonTick before the virtual projectile tick.
        hat.commonTick();hat.tick();
        test.assertTrue(hat.isReturningToOwner() && hat.getDeltaMovement().z<0,"Tick31 reverses the original flight direction");
        hat.playerTouch(user);
        test.assertTrue(hat.isRemoved(),"Owner can catch after the hat has left their bounding box");
        test.assertTrue(user.getInventory().contains(s->ItemStack.isSameItemSameComponents(s,expected)),"Catch returns the original item");
        close(user);test.succeed();
    }

    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void wallImpactAndReloadPreserveHatAfterOwnerLogout(GameTestHelper test) {
        var user=player(test);var stack=new ItemStack(EquipmentContent.BLADE_HAT);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Retained hat"));
        var wall=new BlockPos(3,3,4);test.setBlock(wall,Blocks.STONE);
        var hat=new BladeHatEntity(test.getLevel(),user,stack);hat.setPos(test.absoluteVec(new Vec3(3.5,3.5,3.2)));hat.setDeltaMovement(0,0,1);
        test.getLevel().addFreshEntity(hat);hat.tick();
        test.assertTrue(hat.isInGround(),"Actual arrow movement collides with wall");
        test.assertValueEqual(hat.getThrownStack().getDamageValue(),1,"Owner-thrown impact spends one durability");
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,test.getLevel().registryAccess());hat.saveWithoutId(output);
        hat.discard();close(user);
        var restored=new BladeHatEntity(EquipmentContent.BLADE_HAT_ENTITY,test.getLevel());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING,test.getLevel().registryAccess(),output.buildResult()));test.getLevel().addFreshEntity(restored);
        test.assertTrue(restored.isInGround(),"Wall attachment survives serialization");
        test.assertValueEqual(restored.getThrownStack().getDamageValue(),1,"Impact durability survives serialization");
        var picker=player(test);restored.playerTouch(picker);
        test.assertTrue(restored.isRemoved(),"Grounded hat stays recoverable after owner logout");
        test.assertTrue(picker.getInventory().contains(s->s.is(EquipmentContent.BLADE_HAT)&&s.getHoverName().getString().equals("Retained hat")),"Other player recovers the retained named stack");
        close(picker);test.succeed();
    }

    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void dispenserEquipsBeforeThrowing(GameTestHelper test) {
        var pos=new BlockPos(2,2,2);test.setBlock(pos,Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.EAST));
        var absolute=test.absolutePos(pos);var level=test.getLevel();
        var source=new BlockSource(level,absolute,level.getBlockState(absolute),(DispenserBlockEntity)level.getBlockEntity(absolute));
        var target=test.spawn(EntityTypes.ARMOR_STAND,new Vec3(3.5,2,2.5));
        var first=new ItemStack(EquipmentContent.BLADE_HAT);BladeHatItem.DISPENSE_BEHAVIOR.dispense(source,first);
        test.assertTrue(first.isEmpty()&&target.getItemBySlot(EquipmentSlot.HEAD).is(EquipmentContent.BLADE_HAT),"Nearby eligible entity receives armor first");
        var second=new ItemStack(EquipmentContent.BLADE_HAT);second.set(DataComponents.CUSTOM_NAME,Component.literal("Dispenser hat"));
        BladeHatItem.DISPENSE_BEHAVIOR.dispense(source,second);
        var hats=level.getEntitiesOfClass(BladeHatEntity.class,target.getBoundingBox().inflate(3));
        test.assertTrue(second.isEmpty()&&hats.size()==1,"Occupied head falls back to exactly one thrown hat");
        var hat=hats.getFirst();test.assertTrue(hat.getOwner()==null&&hat.pickup==AbstractArrow.Pickup.ALLOWED,"Dispensed item is ownerless and recoverable");
        test.assertValueEqual(hat.getThrownStack().getHoverName().getString(),"Dispenser hat","Dispenser preserves item components");
        hat.discard();target.discard();test.succeed();
    }

    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void gunReloadFiringCadenceAndCreativeAmmoMatchSource(GameTestHelper test) {
        var user=player(test);var gun=new ItemStack(EquipmentContent.TOMMY_GUN);user.setItemInHand(InteractionHand.MAIN_HAND,gun);
        user.getInventory().add(new ItemStack(Items.IRON_NUGGET,11));user.getInventory().add(new ItemStack(Items.GUNPOWDER,2));user.setShiftKeyDown(true);
        test.assertTrue(EquipmentContent.TOMMY_GUN.use(test.getLevel(),user,InteractionHand.MAIN_HAND).consumesAction(),"Sneak use reloads from real inventory");
        test.assertValueEqual(TommyGunItem.getAmmo(gun),11,"One nugget per round and eight rounds per gunpowder");
        test.assertFalse(user.getInventory().contains(s->s.is(Items.IRON_NUGGET)||s.is(Items.GUNPOWDER)),"Partial powder batch rounds up to two powder");
        for(int i=0;i<21;i++)user.getCooldowns().tick();test.assertTrue(user.getCooldowns().isOnCooldown(gun),"Reload cooldown is two ticks per loaded round");
        user.getCooldowns().tick();test.assertFalse(user.getCooldowns().isOnCooldown(gun),"Cooldown ends at22ticks");
        user.setShiftKeyDown(false);EquipmentContent.TOMMY_GUN.use(test.getLevel(),user,InteractionHand.MAIN_HAND);
        for(int remaining=100;remaining>=96;remaining--)EquipmentContent.TOMMY_GUN.onUseTick(test.getLevel(),user,gun,remaining);
        test.assertValueEqual(TommyGunItem.getAmmo(gun),8,"Five use ticks fire three rounds");
        var bullets=test.getLevel().getEntitiesOfClass(TommyGunBulletEntity.class,user.getBoundingBox().inflate(4),b->b.getOwner()==user);
        test.assertValueEqual(bullets.size(),3,"Actual use creates one bullet per even elapsed tick");
        user.releaseUsingItem();test.assertFalse(user.isUsingItem(),"Release ends synced active-use state used by client loop audio");
        user.setGameMode(GameType.CREATIVE);EquipmentContent.TOMMY_GUN.onUseTick(test.getLevel(),user,gun,100);
        test.assertValueEqual(TommyGunItem.getAmmo(gun),8,"Creative firing retains ammunition");
        test.getLevel().getEntitiesOfClass(TommyGunBulletEntity.class,user.getBoundingBox().inflate(4),b->b.getOwner()==user).forEach(Entity::discard);
        close(user);test.succeed();
    }

    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void bulletsHitFirstFlightSegmentAndPierceDamageImmunity(GameTestHelper test) {
        var user=player(test);var target=test.spawnWithNoFreeWill(EntityTypes.COW,new Vec3(3.5,3,4.5));
        target.setHealth(10);target.damageCooldownTime=20;
        for(int i=0;i<2;i++) {
            var bullet=new TommyGunBulletEntity(user,test.getLevel());bullet.setPos(test.absoluteVec(new Vec3(3.5,3.5,3.2)));bullet.setDeltaMovement(0,0,2);
            test.getLevel().addFreshEntity(bullet);bullet.tick();
            test.assertTrue(bullet.isRemoved(),"First flight segment hits the nearby entity instead of tunneling past it");
        }
        test.assertValueEqual(target.getHealth(),6F,"Two bullets each deal source2damage through immunity");
        test.assertValueEqual(target.damageCooldownTime,20,"Bullets preserve existing damage immunity for other attacks");
        var expired=new TommyGunBulletEntity(user,test.getLevel());expired.tickCount=41;test.getLevel().addFreshEntity(expired);expired.tick();
        test.assertTrue(expired.isRemoved(),"Source40tick lifespan expires");
        target.discard();close(user);test.succeed();
    }

    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void bulletsBreakSoftBlocksAndStopAtStone(GameTestHelper test) {
        var user=player(test);var level=test.getLevel();var soft=new BlockPos(3,3,4);var hard=new BlockPos(3,3,5);
        test.setBlock(soft,Blocks.GLASS);test.setBlock(hard,Blocks.STONE);
        var bullet=new TommyGunBulletEntity(user,level);bullet.setPos(test.absoluteVec(new Vec3(3.5,3.5,3.2)));bullet.setDeltaMovement(0,0,1);
        level.addFreshEntity(bullet);bullet.tick();test.assertTrue(test.getBlockState(soft).isAir(),"Bullet destroys source hardness0.3glass");
        test.assertTrue(Math.abs(bullet.getDeltaMovement().z-.9)<.00001,"Soft block penetration keeps90percent velocity");
        bullet.tick();bullet.tick();test.assertTrue(bullet.isRemoved()&&test.getBlockState(hard).is(Blocks.STONE),"Hard wall stops the bullet");
        close(user);test.succeed();
    }

    @GameTest
    public void originalPaintingsArePlaceableWithSourceDimensions(GameTestHelper test) {
        var registry=test.getLevel().registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
        var mona=registry.getOrThrow(EquipmentContent.MONA_LISA_PAINTING);var hands=registry.getOrThrow(EquipmentContent.HANDS_PAINTING);
        test.assertTrue(mona.value().width()==2&&mona.value().height()==3,"Mona Lisa retains32x48pixel dimensions");
        test.assertTrue(hands.value().width()==1&&hands.value().height()==1,"Hands retains16x16pixel dimensions");
        var placeable=net.minecraft.tags.TagKey.create(Registries.PAINTING_VARIANT,net.minecraft.resources.Identifier.withDefaultNamespace("placeable"));
        test.assertTrue(mona.is(placeable)&&hands.is(placeable),"Ordinary painting placement can select both source paintings");
        test.succeed();
    }
}
