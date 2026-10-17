package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.core.StandStatFormulas;
import com.github.standobyte.jojo.fabric.JojoGameRules;
import com.github.standobyte.jojo.fabric.RipplesFabric;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Source checkpoint metadata stays on the real stack; no independent item identity or tracker. */
public final class CDAnchorItem extends Item {
    public static final CDAnchorItem NON_BLOCK = net.minecraft.core.Registry.register(BuiltInRegistries.ITEM,
            RipplesFabric.id("crazy_diamond_non_block_anchor"), new CDAnchorItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, RipplesFabric.id("crazy_diamond_non_block_anchor")))));
    private CDAnchorItem(Properties properties) { super(properties); }

    public static void fillAnchor(ItemStack stack, Level level, BlockPos position, BlockState state) {
        var anchor = new CompoundTag();
        var pos = new CompoundTag(); pos.putInt("x", position.getX()); pos.putInt("y", position.getY()); pos.putInt("z", position.getZ());
        anchor.put("Pos", pos); anchor.putString("Dimension", level.dimension().identifier().toString());
        if (state != null) BlockState.FULL_CODEC.encodeStart(NbtOps.INSTANCE, state).result().ifPresent(tag -> anchor.put("BlockState", tag));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put("CDCheckpoint", anchor));
    }

    public static Optional<BlockPos> destination(Level level, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var anchor = data.getCompound("CDCheckpoint").orElse(null);
        if (anchor == null) return Optional.empty();
        var dimension = anchor.getString("Dimension");
        if (dimension.isEmpty()) {
            if (!level.isClientSide()) CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                var migrated = tag.getCompound("CDCheckpoint").orElseThrow();
                migrated.putString("Dimension", "minecraft:overworld"); tag.put("CDCheckpoint", migrated);
            });
            return Optional.empty(); // Source repairs legacy dimension on this call, uses it on the next.
        }
        if (!dimension.get().equals(level.dimension().identifier().toString())) return Optional.empty();
        return anchor.getCompound("Pos").map(pos -> new BlockPos(pos.getIntOr("x", 0), pos.getIntOr("y", 0), pos.getIntOr("z", 0)));
    }

    public static BlockState blockState(ItemStack stack, BlockItem item) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.getCompound("CDCheckpoint").flatMap(anchor -> anchor.getCompound("BlockState"))
                .flatMap(tag -> BlockState.FULL_CODEC.parse(NbtOps.INSTANCE, tag).result())
                .filter(state -> !state.isAir()).orElse(item.getBlock().defaultBlockState());
    }

    public static ItemStack selected(LivingEntity user) {
        var stack = user.getOffhandItem();
        if (destination(user.level(), stack).isEmpty()) stack = user.getMainHandItem();
        return destination(user.level(), stack).isPresent() ? stack : ItemStack.EMPTY;
    }

    public static boolean breakable(ServerPlayer user, BlockPos pos, double attackDamage) {
        var level = user.level(); var state = level.getBlockState(pos);
        float hardness = StandStatFormulas.getStandBreakBlockHardness(state.getDestroySpeed(level, pos), state.requiresCorrectToolForDrops());
        int harvest = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? 3 : state.is(BlockTags.NEEDS_IRON_TOOL) ? 2 : state.is(BlockTags.NEEDS_STONE_TOOL) ? 1 : 0;
        return !state.isAir() && StandStatFormulas.isBlockBreakable(attackDamage, hardness, harvest)
                && user.mayBuild() && level.mayInteract(user, pos);
    }

    public static boolean make(ServerPlayer user, BlockPos pos, double attackDamage) {
        var level = user.level(); var state = level.getBlockState(pos);
        if (!breakable(user, pos, attackDamage)) return false;
        if (!level.getGameRules().get(JojoGameRules.BREAK_BLOCKS)) {
            // Source's disabled-breaking branch creates this token without filling checkpoint NBT.
            CrazyDiamondRestorationActions.giveItem(user, new ItemStack(NON_BLOCK)); return true;
        }
        var blockEntity = level.getBlockEntity(pos);
        var drops = Block.getDrops(state, level, pos, blockEntity);
        var first = drops.isEmpty() ? ItemStack.EMPTY : drops.getFirst();
        fillAnchor(first, level, pos, state);
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, user, pos, state, blockEntity)
                || !level.destroyBlock(pos, false, user)) return false;
        if (!first.isEmpty()) {
            if (first.getItem() instanceof BlockItem) CrazyDiamondRestorationActions.giveItem(user, first);
            else Block.popResource(level, pos, first);
        }
        return true;
    }

    /** Returns the placement position only when the source stack was actually consumed. */
    public static Optional<BlockPos> move(ServerPlayer user) {
        var stack = selected(user);
        var destination = destination(user.level(), stack);
        if (destination.isEmpty()) return Optional.empty();
        var pos = destination.get(); var center = Vec3.atCenterOf(pos); var vehicle = user.getRootVehicle();
        if (vehicle.distanceToSqr(center) > 16) {
            vehicle.setDeltaMovement(center.subtract(vehicle.position()).normalize().scale(.75));
            vehicle.fallDistance = 0; vehicle.syncVelocity = true; return Optional.empty();
        }
        BlockState restore = stack.getItem() instanceof BlockItem blockItem ? blockState(stack, blockItem) : null;
        if (restore != null && !CrazyDiamondRestorationActions.canReplaceBlock(user.level(), pos, restore)) return Optional.empty();
        stack.shrink(1);
        if (restore != null) {
            if (!user.level().getBlockState(pos).isAir()) user.level().destroyBlock(pos, true);
            user.level().setBlockAndUpdate(pos, restore);
        }
        return Optional.of(pos);
    }
    public static void initialize() { }
}
