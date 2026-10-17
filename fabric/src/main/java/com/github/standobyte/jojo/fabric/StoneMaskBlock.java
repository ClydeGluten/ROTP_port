package com.github.standobyte.jojo.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;

public final class StoneMaskBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty BLOOD_ACTIVATION = BooleanProperty.create("blood_activation");
    public StoneMaskBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.WALL).setValue(FACING, Direction.NORTH).setValue(BLOOD_ACTIVATION, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACE, FACING, BLOOD_ACTIVATION); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> state.getValue(BLOOD_ACTIVATION) ? Block.box(4, 0, 4, 12, 6, 12) : Block.box(4, 0, 4, 12, 1, 12);
            case CEILING -> Block.box(4, 15, 4, 12, 16, 12);
            case WALL -> switch (state.getValue(FACING)) {
                case EAST -> Block.box(0, 4, 4, 1, 12, 12);
                case SOUTH -> Block.box(4, 4, 0, 12, 12, 1);
                case WEST -> Block.box(15, 4, 4, 16, 12, 12);
                default -> Block.box(4, 4, 15, 12, 12, 16);
            };
        };
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) { player.addItem(stack(level, pos, state)); level.removeBlock(pos, false); }
        return InteractionResult.SUCCESS;
    }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean data) { return stack(level, pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) { if (level.getBlockEntity(pos) instanceof StoneMaskBlockEntity mask) mask.setStack(stack); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        var next = super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
        if (next.isAir() && level instanceof ServerLevel server) popResource(server, pos, stack(level, pos, state));
        return next;
    }
    public static ItemStack stack(LevelReader level, BlockPos pos, BlockState state) { return level.getBlockEntity(pos) instanceof StoneMaskBlockEntity mask ? mask.stack() : new ItemStack(state.getBlock()); }
    public static void splash(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StoneMaskBlock)) return;
        level.playSound(null, pos, JojoSounds.get("stone_mask_activation"), net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
        if (state.getValue(FACE) == AttachFace.FLOOR && level.getBlockEntity(pos) instanceof StoneMaskBlockEntity mask) mask.activate();
        else { popResource(level, pos, stack(level, pos, state)); level.removeBlock(pos, false); }
    }
    @Override public void playerDestroy(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos pos, BlockState state, BlockEntity entity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, entity, tool);
        if (entity instanceof StoneMaskBlockEntity mask) VampireAdvancements.onMaskDestroyed(player, this, tool, mask.stack());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StoneMaskBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != VampireBlocks.MASK_BLOCK_ENTITY ? null : (world, pos, block, entity) -> ((StoneMaskBlockEntity) entity).tick();
    }
}
