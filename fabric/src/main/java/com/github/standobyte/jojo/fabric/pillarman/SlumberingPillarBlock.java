package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.HamonCharges;
import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.VampireCombatService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source two-wide, three-high sculpture. Parts 0/1 top, 2/3 middle, 4/5 bottom. */
public final class SlumberingPillarBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("pillarman_part", 0, 5);
    public static final int CONTROLLER_PART = 4;
    private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 16, 8), SOUTH = Block.box(0, 0, 8, 16, 16, 16),
            WEST = Block.box(0, 0, 0, 8, 16, 16), EAST = Block.box(8, 0, 0, 16, 16, 16);
    public SlumberingPillarBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, CONTROLLER_PART)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos base = context.getClickedPos();
        Direction right = context.getHorizontalDirection().getClockWise();
        Level level = context.getLevel();
        for (int x = 0; x < 2; x++) for (int y = 0; y < 3; y++) {
            BlockPos pos = base.above(y).relative(right, x);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        for (int part = 0; part < 6; part++) if (part != CONTROLLER_PART) level.setBlock(posByPart(state, pos, part), state.setValue(PART, part), 3);
    }
    public static BlockPos posByPart(BlockState state, BlockPos start, int desired) {
        int current = state.getValue(PART);
        return start.relative(state.getValue(FACING).getClockWise(), desired % 2 - current % 2).below(desired / 2 - current / 2);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        for (int part = 0; part < 6; part++) if (part != state.getValue(PART)) {
            BlockPos otherPos = posByPart(state, pos, part);
            BlockState other = level.getBlockState(otherPos);
            if (other.is(this) && other.getValue(PART) == part) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, otherPos, Block.getId(other));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return mirror == Mirror.NONE ? state : state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) { case SOUTH -> SOUTH; case EAST -> EAST; case WEST -> WEST; default -> NORTH; };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return state.getValue(PART) == CONTROLLER_PART ? new SlumberingPillarBlockEntity(pos, state) : null; }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean stepOn) {
        absorbContact(state, level, pos, entity);
    }
    /** Same authoritative path used by collision and focused world checks. */
    public static boolean absorbContact(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || level.getDifficulty() == Difficulty.PEACEFUL || !(entity instanceof LivingEntity living)) return false;
        var damage = server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, RipplesFabric.id("pillarman_absorption")));
        DamageSource source = new DamageSource(damage);
        if (HamonCharges.protectFromContact(living, source, 4) || !living.hurtServer(server, source, 4)) return false;
        living.addEffect(new MobEffectInstance(VampireCombatService.STUN, 10));
        if (level.getBlockEntity(posByPart(state, pos, CONTROLLER_PART)) instanceof SlumberingPillarBlockEntity controller) controller.incrementAbsorbed();
        return true;
    }
}
