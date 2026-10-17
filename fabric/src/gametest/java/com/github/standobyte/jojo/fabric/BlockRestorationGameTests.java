package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.stand.restoration.BlockHistory;
import com.github.standobyte.jojo.fabric.stand.restoration.BlockHistorySavedData;
import com.github.standobyte.jojo.fabric.stand.restoration.BrokenBlockRecord;
import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.phys.AABB;

/** Three grouped world cases; root registers and runs them in the shared integration lane. */
public final class BlockRestorationGameTests {
    @GameTest public void actualBreakRestoresStateWithActualDropsAndXpAndExcludesInventories(GameTestHelper test) {
        var level = test.getLevel();
        var player = test.makeMockServerPlayer(GameType.SURVIVAL);
        var pos = test.absolutePos(new BlockPos(2, 2, 2));
        var state = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        level.setBlockAndUpdate(pos, state);
        RestorationHooks.forget(level, pos);
        RestorationHooks.setDroppedXp(level, pos, 7);
        test.assertTrue(level.destroyBlock(pos, true, player), "Actual server break succeeds");
        var history = BlockHistory.at(level, pos);
        var record = history.getBrokenBlockAt(pos);
        test.assertTrue(record != null && record.state.equals(state), "Registered actual-loot hook preserves full block state");
        test.assertTrue(record.droppedXp() == 7 && record.drops.size() == 1 && record.drops.getFirst().is(Items.OAK_STAIRS),
                "Positive pre-loot XP and actual generated loot attach to the break");
        var nearby = new AABB(pos).inflate(2);
        var drops = level.getEntitiesOfClass(ItemEntity.class, nearby);
        var materials = drops.stream().map(ItemEntity::getItem).toList();
        player.giveExperiencePoints(6);
        var failed = restore(level, player, record, false, materials);
        test.assertTrue(failed.blocksPlaced.isEmpty() && player.totalExperience == 6
                && materials.stream().mapToInt(ItemStack::getCount).sum() == 1, "Insufficient XP consumes no drops");
        player.giveExperiencePoints(1);
        var restored = restore(level, player, record, false, materials);
        test.assertTrue(restored.blocksPlaced.contains(pos) && level.getBlockState(pos).equals(state), "Original state is restored");
        test.assertTrue(player.totalExperience == 0 && materials.stream().mapToInt(ItemStack::getCount).sum() == 0,
                "Restoration consumes the existing drop and XP exactly once");
        test.assertFalse(history.wasBlockBroken(pos), "Successful restoration forgets its original position");
        test.assertTrue(restore(level, player, record, false, materials).blocksPlaced.isEmpty(), "Repeating a restore cannot duplicate a block");
        for (var block : List.of(Blocks.CHEST, VampireBlocks.STONE_MASK)) {
            var excludedPos = pos.east();
            level.setBlockAndUpdate(excludedPos, block.defaultBlockState());
            RestorationHooks.forget(level, excludedPos);
            test.assertTrue(level.getBlockEntity(excludedPos) != null, "Fixture has an actual block entity");
            level.destroyBlock(excludedPos, true, player);
            test.assertFalse(BlockHistory.at(level, excludedPos).wasBlockBroken(excludedPos), "Inventories and Stone Mask are excluded");
        }
        RestorationHooks.remember(level, pos.above(), Blocks.FIRE.defaultBlockState(), null, List.of());
        test.assertFalse(BlockHistory.at(level, pos.above()).wasBlockBroken(pos.above()), "Original FireBlock is excluded");
        drops.forEach(ItemEntity::discard);
        test.succeed();
    }

    @GameTest public void cancellationCreativeCostsAndSupportedSandKeepOriginalOrdering(GameTestHelper test) {
        var level = test.getLevel();
        var player = test.makeMockServerPlayer(GameType.CREATIVE);
        var pos = test.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        RestorationHooks.forget(level, pos);
        try (var capture = RestorationHooks.begin(level, pos, level.getBlockState(pos), null)) {
            RestorationHooks.captureDrops(level, pos, level.getBlockState(pos), null, List.of(new ItemStack(Items.COBBLESTONE)));
            RestorationHooks.setDroppedXp(level, pos, 5);
            test.assertFalse(capture.commit(false), "Cancelled mutation does not commit its captured loot or XP");
        }
        test.assertFalse(BlockHistory.at(level, pos).wasBlockBroken(pos), "Cancellation leaves no restoration record");
        try (var capture = RestorationHooks.begin(level, pos, level.getBlockState(pos), null)) {
            capture.captureDrops(List.of(new ItemStack(Items.COBBLESTONE)));
            capture.captureXp(5);
            level.removeBlock(pos, false);
            test.assertTrue(capture.commit(true), "Successful custom mutation commits once");
            test.assertFalse(capture.commit(true), "Repeated commit is inert");
        }
        var record = BlockHistory.at(level, pos).getBrokenBlockAt(pos);
        var materials = new ItemStack(Items.COBBLESTONE);
        test.assertTrue(restore(level, player, record, true, List.of(materials)).blocksPlaced.isEmpty(), "Creative still needs affordable XP");
        player.giveExperiencePoints(5);
        test.assertTrue(restore(level, player, record, true, List.of(materials)).blocksPlaced.contains(pos), "Creative with XP restores");
        test.assertTrue(materials.isEmpty() && player.totalExperience == 5, "Creative consumes available materials but skips XP debit");

        var sandPos = pos.east(2);
        level.setBlockAndUpdate(sandPos.below(), Blocks.STONE.defaultBlockState());
        level.removeBlock(sandPos, false);
        var sand = new BrokenBlockRecord(sandPos, Blocks.SAND.defaultBlockState(), List.of(), false);
        var stonePos = pos.above();
        level.removeBlock(stonePos, false);
        var stone = new BrokenBlockRecord(stonePos, Blocks.STONE.defaultBlockState(), List.of(), false);
        var history = BlockHistory.at(level, sandPos);
        history.saveBrokenBlock(sand);
        BlockHistory.at(level, stonePos).saveBrokenBlock(stone);
        var ordered = RestorationHooks.restoreBlocks(level, player, Stream.of(stone, sand), Comparator.comparingInt(block -> block.pos.distManhattan(pos)),
                1, true, false, true, player, List.of());
        test.assertTrue(ordered.blocksPlaced.contains(sandPos) && level.isEmptyBlock(stonePos), "Supported falling blocks precede nearer ordinary blocks");
        level.setBlockAndUpdate(stonePos, Blocks.GOLD_BLOCK.defaultBlockState());
        test.assertTrue(restore(level, player, stone, true, List.of()).blocksPlaced.isEmpty(), "Occupied positions are not overwritten");
        test.assertFalse(BlockHistory.at(level, stonePos).wasBlockBroken(stonePos), "forgetFailed removes occupied records");
        test.succeed();
    }

    @GameTest public void realChunkSerializationHonorsConfigAndPrunesConflictingLoadedHistory(GameTestHelper test) {
        var level = test.getLevel();
        var pos = test.absolutePos(new BlockPos(2, 2, 2));
        var chunk = level.getChunkAt(pos);
        boolean previous = BlockHistorySavedData.saveDestroyedBlocks();
        var original = chunk.getAttached(BlockHistorySavedData.DATA);
        try {
            // Use only this fixture's records when exercising full chunk serialization.
            chunk.setAttached(BlockHistorySavedData.DATA, new BlockHistory());
            level.removeBlock(pos, false);
            BlockHistorySavedData.setSaveDestroyedBlocks(false);
            RestorationHooks.remember(level, pos, Blocks.STONE.defaultBlockState(), null, List.of(new ItemStack(Items.COBBLESTONE)));
            test.assertTrue(BlockHistory.at(level, pos).wasBlockBroken(pos), "Config off keeps runtime capture active");
            test.assertTrue(roundTripChunk(level, chunk).getAttachedOrCreate(BlockHistorySavedData.DATA).size() == 0,
                    "Config off omits history from actual chunk serialization");
            BlockHistorySavedData.setSaveDestroyedBlocks(true);
            var record = new BrokenBlockRecord(pos, Blocks.STONE.defaultBlockState(), List.of(new ItemStack(Items.COBBLESTONE, 2)), true, 12345, 9);
            BlockHistory.at(level, pos).saveBrokenBlock(record);
            var loadedChunk = roundTripChunk(level, chunk);
            BlockHistorySavedData.onChunkLoad(level, loadedChunk);
            var loaded = loadedChunk.getAttached(BlockHistorySavedData.DATA).getBrokenBlockAt(pos);
            test.assertTrue(loaded != null && loaded.keep && loaded.tickCount() == 12345 && loaded.droppedXp() == 9
                    && loaded.drops.getFirst().getCount() == 2, "Actual chunk read retains Keep, TickCount, XP, and drops");
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var enabledTag = BlockHistorySavedData.CODEC.encodeStart(ops, BlockHistory.at(level, pos)).getOrThrow();
            var withMissingBlock = ((CompoundTag) enabledTag).copy();
            var invalid = withMissingBlock.getListOrEmpty("Blocks").getCompoundOrEmpty(0).copy();
            invalid.getCompoundOrEmpty("State").putString("Name", "jojo:missing_restoration_test_block");
            withMissingBlock.getListOrEmpty("Blocks").add(invalid);
            test.assertTrue(BlockHistorySavedData.CODEC.parse(ops, withMissingBlock).getOrThrow().size() == 1,
                    "Missing registry entry is skipped without losing valid neighboring history");
            BlockHistorySavedData.setSaveDestroyedBlocks(false);
            test.assertTrue(BlockHistorySavedData.CODEC.parse(ops, enabledTag).getOrThrow().size() == 0, "Config off also ignores previously saved history");
            BlockHistorySavedData.setSaveDestroyedBlocks(true);
            level.setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState());
            var conflicting = roundTripChunk(level, chunk);
            BlockHistorySavedData.onChunkLoad(level, conflicting);
            test.assertFalse(conflicting.getAttached(BlockHistorySavedData.DATA).wasBlockBroken(pos), "First load prunes nonreplaceable conflicts");
        } finally {
            BlockHistorySavedData.setSaveDestroyedBlocks(previous);
            if (original == null) chunk.removeAttached(BlockHistorySavedData.DATA);
            else chunk.setAttached(BlockHistorySavedData.DATA, original);
        }
        test.succeed();
    }

    private static LevelChunk roundTripChunk(ServerLevel level, LevelChunk chunk) {
        var tag = SerializableChunkData.copyOf(level, chunk).write();
        var data = SerializableChunkData.parse(level, level.palettedContainerFactory(), tag);
        return ((ImposterProtoChunk) data.read(level, level.getPoiManager(),
                new RegionStorageInfo("restoration-gametest", level.dimension(), "chunk"), chunk.getPos())).getWrapped();
    }

    private static RestorationHooks.RestoreResult restore(ServerLevel level, Player player, BrokenBlockRecord record,
            boolean creative, List<ItemStack> materials) {
        return RestorationHooks.restoreBlocks(level, player, Stream.of(record), null, 1, creative, false, true, player, materials);
    }
}
