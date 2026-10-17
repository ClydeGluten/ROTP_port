package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.pillarman.PillarTempleStructure;
import com.github.standobyte.jojo.fabric.pillarman.PillarWorldContent;
import com.github.standobyte.jojo.fabric.pillarman.SlumberingPillarBlock;
import com.github.standobyte.jojo.fabric.pillarman.SlumberingPillarBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Three grouped real-world scenarios; registration and execution stay in root's shared lane. */
public final class PillarWorldGameTests {
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5)
    public void sleepingSculptureAllFacesContactAndReload(GameTestHelper test) {
        var level = test.getLevel(); var player = test.makeMockPlayer(GameType.CREATIVE);
        var block = PillarWorldContent.SLUMBERING_PILLARMAN;
        BlockPos base = test.absolutePos(new BlockPos(4, 2, 4));
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int broken = 0; broken < 6; broken++) {
            var state = block.defaultBlockState().setValue(SlumberingPillarBlock.FACING, facing);
            level.setBlockAndUpdate(base, state); block.setPlacedBy(level, base, state, player, new ItemStack(block));
            for (int part = 0; part < 6; part++) {
                BlockPos pos = SlumberingPillarBlock.posByPart(state, base, part);
                test.assertTrue(level.getBlockState(pos).is(block) && level.getBlockState(pos).getValue(SlumberingPillarBlock.PART) == part, "All six original parts place for each facing");
                test.assertTrue((level.getBlockEntity(pos) instanceof SlumberingPillarBlockEntity) == (part == 4), "Only bottom-left part owns saved absorption");
            }
            BlockPos breakPos = SlumberingPillarBlock.posByPart(state, base, broken);
            block.playerWillDestroy(level, breakPos, level.getBlockState(breakPos), player); level.removeBlock(breakPos, false);
            for (int part = 0; part < 6; part++) test.assertFalse(level.getBlockState(SlumberingPillarBlock.posByPart(state, base, part)).is(block), "Breaking any source part removes the full sculpture");
        }
        var state = block.defaultBlockState(); level.setBlockAndUpdate(base, state); block.setPlacedBy(level, base, state, player, new ItemStack(block));
        var victim = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(4.5, 2, 4.5));
        Difficulty old = level.getDifficulty(); level.getServer().setDifficulty(Difficulty.HARD, true);
        try {
            float health = victim.getHealth();
            test.assertTrue(SlumberingPillarBlock.absorbContact(state, level, base, victim), "Real collision consequence deals source absorption");
            test.assertValueEqual(victim.getHealth(), health - 4, "Source contact damage is four");
            test.assertTrue(victim.hasEffect(VampireCombatService.STUN), "Successful contact applies ten-tick stun");
            test.assertFalse(SlumberingPillarBlock.absorbContact(state, level, base, victim), "Ordinary hurt immunity prevents duplicate absorption");
            var controller = (SlumberingPillarBlockEntity) level.getBlockEntity(base);
            test.assertValueEqual(controller.getAbsorbedLife(), 1, "Counter increments only for successful damage");
            var saved = controller.saveWithFullMetadata(level.registryAccess()); level.removeBlockEntity(base);
            var loaded = BlockEntity.loadStatic(base, state, saved, level.registryAccess()); level.setBlockEntity(loaded);
            test.assertTrue(loaded instanceof SlumberingPillarBlockEntity restored && restored.getAbsorbedLife() == 1, "Actual block entity save/reload preserves absorbed life");
            HamonService.grant(victim); HamonService.energy(victim, 100); victim.damageCooldownTime = 0;
            float energy = HamonService.state(victim).energy();
            test.assertFalse(SlumberingPillarBlock.absorbContact(state, level, base, victim), "Hamon protects against sleeping Pillar contact");
            test.assertValueEqual(HamonService.state(victim).energy(), energy - 2, "Hamon pays half the prevented damage");
            HamonService.clear(victim); level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            test.assertFalse(SlumberingPillarBlock.absorbContact(state, level, base, victim), "Peaceful has no absorption");
            test.assertFalse(PillarmanService.hasPillarman(victim), "Sculpture never grants player or mob Pillar power");
        } finally { level.getServer().setDifficulty(old, true); victim.discard(); block.playerWillDestroy(level, base, state, player); level.removeBlock(base, false); }
        test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", structure = "jojo:pillarman_temple/corridor", setupTicks = 5)
    public void realTempleCorridorEngravingsAndFourPieceLayout(GameTestHelper test) {
        var level = test.getLevel(); var manager = level.getStructureTemplateManager();
        for (String name : new String[]{"building", "stairway", "corridor", "bossroom"})
            test.assertTrue(manager.get(RipplesFabric.id("pillarman_temple/" + name)).isPresent(), "Original temple template loads: " + name);
        var pieces = new StructurePiecesBuilder(); PillarTempleStructure.addPieces(manager, new BlockPos(0, 100, 0), pieces, RandomSource.create(17));
        test.assertValueEqual(pieces.build().pieces().size(), 4, "Original building/stairway/corridor/bossroom layout retained");
        var engravings = level.getEntitiesOfClass(PillarWorldContent.EngravingEntity.class, test.getBounds().inflate(1));
        test.assertValueEqual(engravings.size(), 48, "Real corridor template spawns all forty-eight original engravings");
        test.assertTrue(engravings.stream().allMatch(e -> e.getTextureId() >= 0 && e.getTextureId() < 8 && e.survives()), "Legacy texture/facing data loads safely and decorations survive");
        var source = engravings.getFirst(); var saved = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING); source.saveWithoutId(saved);
        var restored = new PillarWorldContent.EngravingEntity(PillarWorldContent.ENGRAVING, level);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
        test.assertValueEqual(restored.getTextureId(), source.getTextureId(), "Engraving texture survives save/load");
        test.assertValueEqual(restored.getDirection(), source.getDirection(), "Engraving facing survives save/load");
        restored.discard(); test.succeed();
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5, maxTicks = 100)
    public void boilingBloodBucketFallingFlowAndSubmergedContact(GameTestHelper test) {
        var level = test.getLevel(); BlockPos source = test.absolutePos(new BlockPos(3, 5, 3));
        test.assertTrue(PillarWorldContent.BOILING_BLOOD.is(FluidTags.LAVA) && PillarWorldContent.FLOWING_BOILING_BLOOD.is(FluidTags.LAVA), "Both original forms participate in vanilla lava behavior");
        test.assertTrue(PillarWorldContent.BOILING_BLOOD_BUCKET.emptyContents(null, level, source, null), "Bucket places actual source fluid");
        test.assertTrue(level.getFluidState(source).isSource(), "Placed bucket is a source");
        var stack = PillarWorldContent.BOILING_BLOOD_BLOCK.pickupBlock(null, level, source, level.getBlockState(source));
        test.assertTrue(stack.is(PillarWorldContent.BOILING_BLOOD_BUCKET) && level.getFluidState(source).isEmpty(), "Source pickup returns correct bucket and removes fluid");
        level.setBlockAndUpdate(source, PillarWorldContent.BOILING_BLOOD_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, 4));
        test.assertTrue(PillarWorldContent.BOILING_BLOOD_BLOCK.pickupBlock(null, level, source, level.getBlockState(source)).isEmpty(), "Flowing blood is not collectible");
        level.setBlockAndUpdate(source, PillarWorldContent.BOILING_BLOOD_BLOCK.defaultBlockState());
        BlockPos contact = test.absolutePos(new BlockPos(6, 2, 6));
        level.setBlockAndUpdate(contact.below(), Blocks.STONE_SLAB.defaultBlockState());
        level.setBlockAndUpdate(contact, PillarWorldContent.BOILING_BLOOD_BLOCK.defaultBlockState());
        var victim = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(6.5, 2.1, 6.5)); victim.setNoGravity(true);
        float health = victim.getHealth();
        test.runAfterDelay(40, () -> {
            test.assertTrue(level.getFluidState(source.below()).getType().isSame(PillarWorldContent.BOILING_BLOOD), "Actual fluid ticks spread downward");
            test.assertTrue(level.getFluidState(source.below()).getValue(FlowingFluid.FALLING), "Downward flow retains falling state");
            test.assertTrue(victim.getHealth() < health || victim.isDeadOrDying(), "Submerged living entity receives inherited lava damage without collision-shape crash");
            victim.discard(); test.succeed();
        });
    }
}
