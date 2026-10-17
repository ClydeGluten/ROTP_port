package com.github.standobyte.jojo.fabric.stand.restoration;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Original ChunkCap save/load adapted to a chunk attachment, not level-wide saved data. */
public final class BlockHistorySavedData {
    private static volatile boolean saveDestroyedBlocks;
    private static boolean initialized;

    public static final Codec<BlockHistory> CODEC = new Codec<>() {
        @Override public <T> DataResult<Pair<BlockHistory, T>> decode(DynamicOps<T> ops, T input) {
            return CompoundTag.CODEC.decode(ops, input).map(pair -> Pair.of(load(ops, pair.getFirst()), pair.getSecond()));
        }
        @Override public <T> DataResult<T> encode(BlockHistory history, DynamicOps<T> ops, T prefix) {
            return CompoundTag.CODEC.encode(save(ops, history), ops, prefix);
        }
    };
    public static final AttachmentType<BlockHistory> DATA = AttachmentRegistry.<BlockHistory>builder()
            .initializer(BlockHistory::new).persistent(CODEC).buildAndRegister(RipplesFabric.id("broken_blocks"));

    /** Root calls once during bootstrap, before any chunk attachments are read. */
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerChunkEvents.CHUNK_LOAD.register((level,chunk,newlyGenerated)->onChunkLoad(level,chunk));
    }

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        if (chunk.getLevel() != level) return;
        var history = chunk.getAttached(DATA);
        if (history != null) history.bind(chunk);
    }

    /** Original common option: default false, and never a runtime capture switch. */
    public static void setSaveDestroyedBlocks(boolean enabled) { saveDestroyedBlocks = enabled; }
    public static boolean saveDestroyedBlocks() { return saveDestroyedBlocks; }

    private static <T> CompoundTag save(DynamicOps<T> ops, BlockHistory history) {
        var result = new CompoundTag();
        if (!saveDestroyedBlocks) return result;
        var blocks = new ListTag();
        for (var record : history.snapshot()) {
            var tag = new CompoundTag();
            var pos = new CompoundTag();
            pos.putInt("X", record.pos.getX()); pos.putInt("Y", record.pos.getY()); pos.putInt("Z", record.pos.getZ());
            tag.put("Pos", pos);
            // CODEC now emits a string for default states; FULL_CODEC preserves source's State compound.
            tag.put("State", encodeTag(BlockState.FULL_CODEC, ops, record.state));
            tag.putBoolean("Keep", record.keep);
            tag.putInt("TickCount", record.tickCount());
            tag.putInt("Xp", record.droppedXp());
            var drops = new ListTag();
            for (var stack : record.drops) if (!stack.isEmpty()) drops.add(encodeTag(ItemStack.CODEC, ops, stack));
            tag.put("Drops", drops);
            blocks.add(tag);
        }
        result.put("Blocks", blocks);
        return result;
    }

    private static <T> BlockHistory load(DynamicOps<T> ops, CompoundTag tag) {
        var records = new ArrayList<BrokenBlockRecord>();
        if (saveDestroyedBlocks) {
            for (var element : tag.getListOrEmpty("Blocks")) {
                if (!(element instanceof CompoundTag block)) continue;
                var posTag = block.getCompound("Pos");
                var stateTag = block.getCompound("State");
                var dropsTag = block.getList("Drops");
                if (posTag.isEmpty() || stateTag.isEmpty() || dropsTag.isEmpty()) continue;
                var position = posTag.get();
                if (position.getInt("X").isEmpty() || position.getInt("Y").isEmpty() || position.getInt("Z").isEmpty()) continue;
                var pos = new BlockPos(position.getIntOr("X", 0), position.getIntOr("Y", 0), position.getIntOr("Z", 0));
                var state = decodeTag(BlockState.FULL_CODEC, ops, stateTag.get());
                if (state == null || !RestorationHooks.eligible(state,
                        state.getBlock() instanceof EntityBlock entityBlock ? entityBlock.newBlockEntity(pos, state) : null)) continue;
                var drops = new ArrayList<ItemStack>();
                for (var dropTag : dropsTag.get()) {
                    var stack = decodeTag(ItemStack.CODEC, ops, dropTag);
                    if (stack != null && !stack.isEmpty()) drops.add(stack);
                }
                records.add(new BrokenBlockRecord(pos, state, drops, block.getBooleanOr("Keep", false),
                        block.getIntOr("TickCount", 0), block.getIntOr("Xp", 0)));
            }
        }
        return new BlockHistory(records);
    }

    private static <A, T> Tag encodeTag(Codec<A> codec, DynamicOps<T> ops, A value) {
        return ops.convertTo(NbtOps.INSTANCE, codec.encodeStart(ops, value).getOrThrow());
    }
    private static <A, T> A decodeTag(Codec<A> codec, DynamicOps<T> ops, Tag tag) {
        return codec.parse(ops, NbtOps.INSTANCE.convertTo(ops, tag)).result().orElse(null);
    }

    private BlockHistorySavedData() { }
}
