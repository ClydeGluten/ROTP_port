package com.github.standobyte.jojo.fabric.stand.restoration;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** ChunkCap.PrevBlockInfo; the original forgetting loop is inactive. */
public final class BrokenBlockRecord {
    public final BlockPos pos;
    public final BlockState state;
    public final List<ItemStack> drops;
    public final boolean keep;
    private final int tickCount;
    private int xp;
    private List<WeakReference<RestorableBlockEntity>> shards = List.of();

    public BrokenBlockRecord(BlockPos pos, BlockState state, List<ItemStack> drops, boolean keep) {
        this(pos, state, drops, keep, 0, 0);
    }

    public BrokenBlockRecord(BlockPos pos, BlockState state, List<ItemStack> drops, boolean keep, int tickCount, int xp) {
        this.pos = pos.immutable();
        this.state = state;
        this.drops = drops.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        this.keep = keep;
        this.tickCount = Math.max(0, tickCount);
        this.xp = Math.max(0, xp);
    }

    public int tickCount() { return tickCount; }
    public int droppedXp() { return xp; }
    void setDroppedXp(int value) { xp = Math.max(0, value); }

    /** Weak references intentionally do not survive chunk serialization, as in the source. */
    public void withEntities(RestorableBlockEntity... entities) {
        shards = new ArrayList<>(entities.length);
        for (var entity : entities) shards.add(new WeakReference<>(entity));
    }

    public boolean onRestore() {
        for (var reference : shards) {
            var shard = reference.get();
            if (shard != null && shard.isEntityAlive()) return shard.crazyDRestore(pos);
        }
        return true;
    }

    public interface RestorableBlockEntity {
        boolean isEntityAlive();
        boolean crazyDRestore(BlockPos pos);
    }
}
