package com.github.standobyte.jojo.fabric.stand.restoration;

import com.github.standobyte.jojo.fabric.StoneMaskBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-only adapters for original capture and Crazy Diamond's shared restoration helpers. */
public final class RestorationHooks {
    private static final ThreadLocal<ArrayDeque<Capture>> CAPTURES = ThreadLocal.withInitial(ArrayDeque::new);

    private record PlayerBreak(ServerLevel level,BlockPos pos) {}
    private static final ThreadLocal<PlayerBreak> PLAYER_BREAK=new ThreadLocal<>();
    public static boolean duringPlayerBreak(ServerLevel level,BlockPos pos,java.util.function.BooleanSupplier action){
        var previous=PLAYER_BREAK.get();PLAYER_BREAK.set(new PlayerBreak(level,pos.immutable()));
        try{return action.getAsBoolean();}finally{if(previous==null)PLAYER_BREAK.remove();else PLAYER_BREAK.set(previous);}
    }
    public static void capturePlayerBreakXp(ServerLevel level,BlockPos pos,int xp){
        var active=PLAYER_BREAK.get();
        if(active!=null&&active.level==level&&active.pos.equals(pos))setDroppedXp(level,pos,xp);
    }

    static boolean eligible(BlockState state, BlockEntity entity) {
        return state != null && !state.isAir() && !(state.getBlock() instanceof FireBlock)
                && !(entity instanceof Container) && !(entity instanceof StoneMaskBlockEntity);
    }

    /** Source direct hook: remembers immediately, including original pre-mutation fire/melt calls. */
    public static void remember(ServerLevel level, BlockPos pos, BlockState state, BlockEntity entity, List<ItemStack> actualDrops) {
        if (!eligible(state, entity)) return;
        var history = BlockHistory.at(level, pos);
        if (history != null) history.saveBrokenBlock(new BrokenBlockRecord(pos, state, actualDrops, false));
    }

    /** Receives only the already-generated loot result. Never calls Block.getDrops or rolls loot. */
    public static void captureDrops(ServerLevel level, BlockPos pos, BlockState state, BlockEntity entity, List<ItemStack> generatedLoot) {
        List<ItemStack> drops = level.getGameRules().get(GameRules.BLOCK_DROPS) ? generatedLoot : List.of();
        var capture = active(level, pos);
        if (capture != null) capture.captureDrops(drops);
        else remember(level, pos, state, entity, drops);
    }

    /** Mirrors positive BreakEvent XP, including XP reported before loot generation. */
    public static void setDroppedXp(ServerLevel level, BlockPos pos, int xp) {
        if (xp <= 0) return;
        var capture = active(level, pos);
        if (capture != null) capture.captureXp(xp);
        else {
            var history = BlockHistory.at(level, pos);
            if (history != null) history.setDroppedXp(pos, xp);
        }
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        var history = BlockHistory.at(level, pos);
        if (history != null) history.removeBrokenBlock(pos);
    }

    /** Optional success-owned route for protected/cancellable custom mutations. Use try-with-resources. */
    public static Capture begin(ServerLevel level, BlockPos pos, BlockState state, BlockEntity entity) {
        var capture = new Capture(level, pos, state, entity);
        CAPTURES.get().push(capture);
        return capture;
    }

    private static Capture active(ServerLevel level, BlockPos pos) {
        for (var capture : CAPTURES.get()) if (capture.level == level && capture.pos.equals(pos)) return capture;
        return null;
    }

    public static final class Capture implements AutoCloseable {
        private final ServerLevel level;
        private final BlockPos pos;
        private final BlockState state;
        private final boolean eligible;
        private List<ItemStack> drops = List.of();
        private int xp;
        private boolean closed;

        private Capture(ServerLevel level, BlockPos pos, BlockState state, BlockEntity entity) {
            this.level = level; this.pos = pos.immutable(); this.state = state;
            eligible = RestorationHooks.eligible(state, entity);
        }
        public void captureDrops(List<ItemStack> generatedLoot) {
            if (!closed) drops = generatedLoot.stream().map(ItemStack::copy).toList();
        }
        public void captureXp(int value) { if (!closed && value > 0) xp = value; }
        public boolean commit(boolean destroyed) {
            if (closed) return false;
            close();
            if (!destroyed || !eligible) return false;
            var history = BlockHistory.at(level, pos);
            if (history == null || level.getBlockState(pos).equals(state)) return false;
            history.saveBrokenBlock(new BrokenBlockRecord(pos, state, drops, false));
            if (xp > 0) history.setDroppedXp(pos, xp);
            return true;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            var captures = CAPTURES.get();
            captures.remove(this);
            if (captures.isEmpty()) CAPTURES.remove();
        }
    }

    public static boolean blockCanBePlaced(ServerLevel level, BlockPos pos, BlockState state) {
        return !level.isOutsideBuildHeight(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                && level.getBlockState(pos).canBeReplaced();
    }

    public static RestoreResult restoreBlocks(ServerLevel level, Entity trackedEntity, Stream<BrokenBlockRecord> blocks,
            Comparator<BrokenBlockRecord> sort, long limit, boolean creative, boolean randomizePos, boolean forgetFailed,
            Player playerWithXp, List<ItemStack> itemsSource) {
        var result = new RestoreResult();
        if (limit == 0 || trackedEntity.level() != level || playerWithXp != null && playerWithXp.level() != level) return result;
        blocks = blocks.filter(block -> {
            if (level.getChunkSource().getChunkNow(block.pos.getX() >> 4, block.pos.getZ() >> 4) == null
                    || restorationExclude(block, level)) return false;
            if (blockCanBePlaced(level, block.pos, block.state)) return true;
            if (forgetFailed) result.blocksToForget.add(block.pos);
            return false;
        });
        if (sort != null) blocks = blocks.sorted(Comparator.comparingInt((BrokenBlockRecord block) ->
                block.state.getBlock() instanceof FallingBlock && !level.isEmptyBlock(block.pos.below()) ? 1 : 2).thenComparing(sort));
        if (limit >= 0) blocks = blocks.limit(limit);
        blocks.forEach(block -> {
            if (!block.onRestore()) return;
            result.blocksTried.add(block.pos);
            if (tryPlaceBlock(level, block, creative, randomizePos, playerWithXp, itemsSource)) {
                result.blocksPlaced.add(block.pos);
                result.blocksToForget.add(block.pos);
            }
        });
        result.blocksToForget.forEach(pos -> forget(level, pos));
        return result;
    }

    public static final class RestoreResult {
        public final Set<BlockPos> blocksTried = new HashSet<>();
        public final Set<BlockPos> blocksPlaced = new HashSet<>();
        public final Set<BlockPos> blocksToForget = new HashSet<>();
    }

    private static boolean restorationExclude(BrokenBlockRecord block, ServerLevel level) {
        if (block.state.getBlock() instanceof FallingBlock && level.isEmptyBlock(block.pos.below())) {
            var history = BlockHistory.at(level, block.pos);
            if (history != null && history.wasBlockBroken(block.pos.below())) return true;
        }
        return !block.state.canSurvive(level, block.pos);
    }

    private static boolean tryPlaceBlock(ServerLevel level, BrokenBlockRecord block, boolean creative, boolean randomizePos,
            Player playerWithXp, List<ItemStack> itemsSource) {
        int xp = block.droppedXp();
        // Source tests affordability even for creative users, before consuming anything.
        if (xp > 0 && (playerWithXp == null || playerWithXp.totalExperience < xp)) return false;
        var pos = block.pos;
        if (randomizePos) {
            // Source assigns blockPos immediately. Its later history check does not restore the old position.
            pos = pos.offset(level.getRandom().nextBoolean() ? level.getRandom().nextInt(3) - 1 : 0,
                    level.getRandom().nextInt(2) + 1, level.getRandom().nextBoolean() ? level.getRandom().nextInt(3) - 1 : 0);
        }
        if (!blockCanBePlaced(level, pos, block.state) || !block.state.canSurvive(level, pos)
                || !(consumeNeededItems(block.drops, itemsSource, null) || creative)) return false;
        if (!creative && playerWithXp != null && xp > 0) playerWithXp.giveExperiencePoints(-xp);
        // Original consumption precedes placement; neither setBlock failure nor a changed neighbor refunds costs.
        var state = Block.updateFromNeighbourShapes(block.state, level, pos);
        level.setBlockAndUpdate(pos, state);
        return true;
    }

    /** Original matching/allocation order, including its multi-stack collection omission. */
    public static boolean consumeNeededItems(List<ItemStack> cost, List<ItemStack> source, List<ItemStack> consumed) {
        if (cost.isEmpty()) return true;
        if (cost.size() == 1 && cost.getFirst().getCount() == 1) {
            for (var item : source) if (matches(cost.getFirst(), item)) {
                if (consumed != null) consumed.add(item.copyWithCount(1));
                item.shrink(1);
                return true;
            }
            return false;
        }
        var needed = cost.stream().map(ItemStack::copy).toList();
        var found = new ArrayList<List<ItemStack>>(needed.size());
        for (int i = 0; i < needed.size(); i++) found.add(new ArrayList<>());
        for (var item : source) {
            for (int i = 0; i < needed.size(); i++) if (matches(needed.get(i), item)) {
                found.get(i).add(item);
                break;
            }
        }
        for (int i = 0; i < needed.size(); i++) {
            if (found.get(i).stream().mapToInt(ItemStack::getCount).sum() < needed.get(i).getCount()) return false;
        }
        for (int i = 0; i < needed.size(); i++) {
            var remaining = needed.get(i);
            for (var item : found.get(i)) {
                int count = Math.min(remaining.getCount(), item.getCount());
                item.shrink(count); remaining.shrink(count);
                if (remaining.isEmpty()) break;
            }
        }
        return true;
    }

    private static boolean matches(ItemStack needed, ItemStack candidate) {
        return !candidate.isEmpty() && ItemStack.isSameItemSameComponents(needed, candidate);
    }

    /** Source order: nearby mob hands, dropped items, own inventory, optional nearest other inventories. */
    public static List<ItemStack> sourceItemStacks(AABB area, Vec3 center, LivingEntity user, ServerLevel level,
            boolean useOtherPlayersInventories) {
        return sourceItemStacks(area, center, user, level, SourceType.MOB_HELD.fromAllNearby(),
                SourceType.ITEM_ENTITY.fromAllNearby(), user instanceof Player ? SourceType.PLAYER_INVENTORY.from(user) : null,
                useOtherPlayersInventories ? SourceType.PLAYER_INVENTORY.fromAllNearby().sort() : null);
    }

    /** Explicit order is used by Angelo Rock to consume its target's inventory first. */
    public static List<ItemStack> sourceItemStacks(AABB area, Vec3 center, LivingEntity user, ServerLevel level,
            ItemsSource... order) {
        var nearby = level.getEntities(user, area, entity -> !entity.isSpectator() && !entity.isRemoved());
        var result = new ArrayList<ItemStack>();
        for (var source : order) {
            if (source == null) continue;
            Stream<Entity> entities = source.entities == null ? nearby.stream().filter(entity -> source.type == sourceType(entity))
                    : Stream.of(source.entities).filter(Objects::nonNull);
            if (source.sort) entities = entities.sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
            entities.forEach(entity -> {
                switch (source.type) {
                    case MOB_HELD -> {
                        var mob = (LivingEntity) entity;
                        for (var hand : InteractionHand.values()) if (!mob.getItemInHand(hand).isEmpty()) result.add(mob.getItemInHand(hand));
                    }
                    case ITEM_ENTITY -> {
                        var stack = ((ItemEntity) entity).getItem();
                        if (!stack.isEmpty()) result.add(stack);
                    }
                    case PLAYER_INVENTORY -> addInventory(result, (Player) entity);
                    case OTHER -> { }
                }
            });
        }
        return result;
    }

    private static SourceType sourceType(Entity entity) {
        if (entity instanceof ItemEntity) return SourceType.ITEM_ENTITY;
        if (entity instanceof Player) return SourceType.PLAYER_INVENTORY;
        if (entity instanceof Mob) return SourceType.MOB_HELD;
        return SourceType.OTHER;
    }

    public enum SourceType {
        MOB_HELD, ITEM_ENTITY, PLAYER_INVENTORY, OTHER;
        public ItemsSource fromAllNearby() { return new ItemsSource(this, null); }
        public ItemsSource from(Entity... entities) { return new ItemsSource(this, entities); }
    }

    public static final class ItemsSource {
        private final SourceType type;
        private final Entity[] entities;
        private boolean sort;
        private ItemsSource(SourceType type, Entity[] entities) { this.type = type; this.entities = entities; }
        public ItemsSource sort() { sort = true; return this; }
    }

    private static void addInventory(List<ItemStack> result, Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (!inventory.getItem(i).isEmpty()) result.add(inventory.getItem(i));
    }

    public static Stream<BrokenBlockRecord> getBlocksInRange(ServerLevel level, LivingEntity user, Vec3i center,
            int range, Predicate<BrokenBlockRecord> filter) {
        if (user.level() != level || range < 0) return Stream.empty();
        var records = new ArrayList<BrokenBlockRecord>();
        for (int x = center.getX() - range >> 4; x <= (center.getX() + range >> 4); x++) {
            for (int z = center.getZ() - range >> 4; z <= (center.getZ() + range >> 4); z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                var history = chunk.getAttached(BlockHistorySavedData.DATA);
                if (history == null) continue;
                history.bind(chunk);
                history.getBrokenBlocks().filter(block -> block.pos.distManhattan(center) <= range
                        && !user.getBoundingBox().intersects(new AABB(block.pos)) && filter.test(block)).forEach(records::add);
            }
        }
        return records.stream();
    }

    public static Vec3i eyePos(Entity camera) {
        var eye = camera.getEyePosition();
        return new Vec3i((int) Math.round(eye.x), (int) Math.round(eye.y), (int) Math.round(eye.z));
    }

    public static boolean blockPosSelectedForRestoration(BrokenBlockRecord block, Vec3 look, Vec3 eye,
            Vec3i center, boolean resolve, boolean aimedOnly) {
        if (block.pos.distManhattan(center) > 12) return false;
        return aimedOnly ? new AABB(block.pos).clip(eye, eye.add(look.scale(24))).isPresent()
                : look.dot(Vec3.atCenterOf(block.pos).subtract(eye).normalize()) >= (resolve ? 0 : 0.7071);
    }

    private RestorationHooks() { }
}
