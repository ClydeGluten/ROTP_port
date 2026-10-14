package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Loaded data and vanilla integration paths beyond the direct projectile/use tests. */
public final class KnifeItemGameTests {
    @GameTest
    public void loadedRecipeCraftsFourKnivesFromIronAboveStick(GameTestHelper test) {
        var level = test.getLevel();
        var recipes = level.getServer().getRecipeManager();
        // A translated two-cell pattern in the ordinary 2x2 player crafting grid.
        var input = CraftingInput.of(2, 2, List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.STICK)));
        var holder = recipes.getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElseThrow(() -> new AssertionError("Loaded knife recipe does not match iron above a stick"));
        test.assertValueEqual(holder.id().identifier(), RipplesFabric.id("knife"), "Loaded custom recipe wins the matching input");
        ItemStack result = holder.value().assemble(input);
        test.assertTrue(result.is(StandItems.KNIFE), "Actual recipe assembly returns the registered knife item");
        test.assertValueEqual(result.getCount(), 4, "Original recipe yields four knives");
        test.assertValueEqual(result.getMaxStackSize(), 16, "Crafted knives retain the original sixteen-item stack limit");
        var sideways = CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STICK)));
        var upsideDown = CraftingInput.of(1, 2, List.of(new ItemStack(Items.STICK), new ItemStack(Items.IRON_INGOT)));
        test.assertFalse(holder.value().matches(sideways, level), "Knife recipe does not silently become shapeless");
        test.assertFalse(holder.value().matches(upsideDown, level), "Iron must remain above the stick");
        test.succeed();
    }

    @GameTest
    public void equipmentRefreshesWhenKnifeCountChangesInPlace(GameTestHelper test) {
        var wearer = test.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(2.5, 3, 2.5));
        wearer.setNoGravity(true);
        double base = wearer.getAttributeValue(Attributes.ATTACK_DAMAGE);
        var knife = new ItemStack(StandItems.KNIFE);
        wearer.setItemSlot(EquipmentSlot.MAINHAND, knife);
        wearer.tick();
        assertDamage(test, wearer, base + 2, "Equipping one knife updates the actual entity damage attribute");
        knife.grow(1);
        wearer.tick();
        assertDamage(test, wearer, base, "Growing the same held stack removes its single-knife bonus");
        knife.shrink(1);
        wearer.tick();
        assertDamage(test, wearer, base + 2, "Shrinking the same held stack to one restores the weapon bonus");
        wearer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        wearer.setItemSlot(EquipmentSlot.OFFHAND, knife);
        wearer.tick();
        assertDamage(test, wearer, base, "Moving the knife to the offhand removes the main-hand bonus");
        wearer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        wearer.tick();
        double sword = wearer.getAttributeValue(Attributes.ATTACK_DAMAGE);
        wearer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(StandItems.KNIFE, 2));
        wearer.tick();
        test.assertTrue(sword > base, "Sword fixture establishes a different equipment modifier");
        assertDamage(test, wearer, base, "Switching from another weapon to a knife bundle leaves no stale modifier");
        wearer.discard();
        test.succeed();
    }

    @GameTest
    public void cobwebHarvestUsesOriginalStringLootForSingleAndStackedKnives(GameTestHelper test) {
        BlockPos pos = test.absolutePos(new BlockPos(3, 3, 3));
        var web = Blocks.COBWEB.defaultBlockState();
        for (int count : new int[] {1, 2, 16}) {
            var tool = new ItemStack(StandItems.KNIFE, count);
            test.assertTrue(tool.isCorrectToolForDrops(web), "Original cobweb harvest eligibility does not depend on count");
            test.assertValueEqual(tool.getDestroySpeed(web), count == 1 ? 15F : 1F, "Only a single knife receives the cobweb speed bonus");
            var drops = Block.getDrops(web, test.getLevel(), pos, null, null, tool);
            test.assertValueEqual(drops.stream().filter(stack -> stack.is(Items.STRING)).mapToInt(ItemStack::getCount).sum(),
                    1, "Vanilla loot table gives one string when harvested with knives");
            test.assertFalse(drops.stream().anyMatch(stack -> stack.is(Items.COBWEB)), "Knives do not inherit shears or Silk Touch drops");
        }
        test.succeed();
    }

    @GameTest
    public void dispenserBlockConsumesOneKnifeAtOriginalLaunchPosition(GameTestHelper test) {
        BlockPos relative = new BlockPos(3, 4, 3);
        BlockPos absolute = test.absolutePos(relative);
        test.setBlock(relative, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH));
        var dispenser = test.getBlockEntity(relative, DispenserBlockEntity.class);
        dispenser.setItem(0, new ItemStack(StandItems.KNIFE, 16));
        // Run the dispenser's scheduled activation, including its registered behavior and
        // inventory writeback, before any spawned projectile receives a simulation tick.
        var state = test.getBlockState(relative);
        state.tick(test.getLevel(), absolute, test.getLevel().getRandom());
        var knives = test.getLevel().getEntitiesOfClass(KnifeEntity.class, new AABB(absolute).inflate(2), knife -> knife.getOwner() == null);
        test.assertValueEqual(knives.size(), 1, "Actual dispenser activation creates one knife projectile");
        test.assertValueEqual(dispenser.getItem(0).getCount(), 15, "Actual dispenser activation consumes exactly one inventory knife");
        var knife = knives.getFirst();
        var expected = Vec3.atCenterOf(absolute).add(0, 0, 0.7);
        test.assertTrue(knife.position().distanceTo(expected) < 0.00001,
                "Original dispenser starts at center plus 0.7 facing, with no modern 0.1 vertical spawn offset");
        test.assertValueEqual(knife.tickCount, 0, "Spawn-position assertion precedes projectile simulation");
        test.assertValueEqual(knife.pickup, AbstractArrow.Pickup.ALLOWED, "Dispensed knives remain recoverable Survival ammunition");
        test.assertValueEqual(knife.getTimeStopFlightTicks(), 0, "Dispenser does not grant the hand-thrown flight exception");
        test.assertTrue(knife.getDeltaMovement().z > 0.8, "Registered dispenser behavior launches toward its facing");
        knife.discard();
        test.succeed();
    }

    @GameTest
    public void stuckKnivesDecayWithoutResettingTheRunningTimer(GameTestHelper test) {
        var target = stuckTarget(test);
        StuckKnives.add(target);
        tick(target, 100);
        StuckKnives.add(target);
        tick(target, 479);
        test.assertValueEqual(StuckKnives.count(target), 2, "Adding another knife preserves the original running removal timer");
        target.tick();
        test.assertValueEqual(StuckKnives.count(target), 1, "First knife disappears on the original 580th living tick");
        tick(target, 579);
        test.assertValueEqual(StuckKnives.count(target), 1, "The remaining knife starts a fresh 580-tick timer");
        target.tick();
        test.assertValueEqual(StuckKnives.count(target), 0, "Final embedded knife expires exactly on its timer");
        for (int i = 0; i < 31; i++) StuckKnives.add(target);
        target.tick();
        test.assertValueEqual(StuckKnives.count(target), 30, "More than thirty knives lose exactly one per tick");
        target.tick();
        test.assertValueEqual(StuckKnives.count(target), 29, "Thirty embedded knives also remove one on the next tick");
        target.discard();
        test.succeed();
    }

    @GameTest
    public void livingEntityReloadKeepsKnifeCountButRestartsRemovalTime(GameTestHelper test) {
        var target = stuckTarget(test);
        StuckKnives.add(target);
        StuckKnives.add(target);
        tick(target, 100);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        target.saveWithoutId(output);
        var restored = EntityTypes.COW.create(test.getLevel(), EntitySpawnReason.COMMAND);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), output.buildResult()));
        test.assertValueEqual(StuckKnives.count(restored), 2, "Actual entity serialization restores embedded knife count");
        tick(target, 460);
        test.assertValueEqual(StuckKnives.count(target), 1, "Original entity retains the elapsed portion of its timer");
        tick(restored, 559);
        test.assertValueEqual(StuckKnives.count(restored), 2, "Transient removal time is not serialized with the count");
        restored.tick();
        test.assertValueEqual(StuckKnives.count(restored), 1, "Reloaded two-knife count gets the original fresh 560-tick timer");
        target.discard();
        restored.discard();
        test.succeed();
    }

    private static LivingEntity stuckTarget(GameTestHelper test) {
        var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(3.5, 3, 3.5));
        target.setNoGravity(true);
        return target;
    }

    private static void tick(LivingEntity entity, int ticks) {
        for (int i = 0; i < ticks; i++) entity.tick();
    }

    private static void assertDamage(GameTestHelper test, LivingEntity entity, double expected, String message) {
        test.assertTrue(Math.abs(entity.getAttributeValue(Attributes.ATTACK_DAMAGE) - expected) < 0.00001, message);
    }
}
