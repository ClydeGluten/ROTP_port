package com.github.standobyte.jojo.fabric.stand.restoration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** One map per actual chunk, without a global cache, time limit, or capacity limit. */
public final class BlockHistory {
    private final Map<BlockPos, BrokenBlockRecord> blocks = new HashMap<>();
    private final Map<BlockPos, Integer> pendingXp = new HashMap<>();
    private boolean loaded;
    private LevelChunk owner;

    public BlockHistory() { }
    BlockHistory(List<BrokenBlockRecord> records) {
        for (var record : records) blocks.put(record.pos, record);
        loaded = true;
    }

    void bind(LevelChunk chunk) {
        owner = chunk;
        if (loaded) {
            // Source's first post-load tick discards occupied positions. Do not force another chunk.
            blocks.values().removeIf(record -> record.pos.getX() >> 4 != chunk.getPos().x()
                    || record.pos.getZ() >> 4 != chunk.getPos().z()
                    || chunk.getLevel().isOutsideBuildHeight(record.pos)
                    || !chunk.getBlockState(record.pos).canBeReplaced());
            loaded = false;
            changed();
        }
    }

    private void changed() { if (owner != null) owner.markUnsaved(); }
    public BrokenBlockRecord getBrokenBlockAt(BlockPos pos) { return blocks.get(pos); }
    public boolean wasBlockBroken(BlockPos pos) { return blocks.containsKey(pos); }
    public int size() { return blocks.size(); }
    public Stream<BrokenBlockRecord> getBrokenBlocks() { return List.copyOf(blocks.values()).stream(); }
    List<BrokenBlockRecord> snapshot() { return List.copyOf(blocks.values()); }

    public void saveBrokenBlock(BrokenBlockRecord record) {
        Integer xp = pendingXp.remove(record.pos);
        if (xp != null) record.setDroppedXp(xp);
        blocks.put(record.pos, record);
        changed();
    }

    public void setDroppedXp(BlockPos pos, int xp) {
        if (xp <= 0) return;
        var record = blocks.get(pos);
        if (record == null) pendingXp.put(pos.immutable(), xp);
        else record.setDroppedXp(xp);
        changed();
    }

    public void removeBrokenBlock(BlockPos pos) { if (blocks.remove(pos) != null) changed(); }
    public void reset() { blocks.clear(); changed(); }

    /** Only loaded full chunks participate; history queries never generate or load terrain. */
    public static BlockHistory at(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return null;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        var history = chunk.getAttachedOrCreate(BlockHistorySavedData.DATA);
        history.bind(chunk);
        return history;
    }
}
