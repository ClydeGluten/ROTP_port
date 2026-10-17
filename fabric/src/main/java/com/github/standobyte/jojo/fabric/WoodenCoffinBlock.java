package com.github.standobyte.jojo.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;

/** Original oak coffin, with the 26.3 AbstractBedBlock supplying native sleep/respawn integration. */
public final class WoodenCoffinBlock extends AbstractBedBlock {
    public static final BooleanProperty CLOSED = BooleanProperty.create("coffin_lid_closed");
    private static final BedRule COFFIN_RULE = new BedRule(BedRule.Rule.ALWAYS, BedRule.Rule.ALWAYS, false, false, java.util.Optional.empty());
    private static final VoxelShape BOTTOM = Block.box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape WEST = Block.box(0, 0, 0, 2, 10, 16), EAST = Block.box(14, 0, 0, 16, 10, 16);
    private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 10, 2), SOUTH = Block.box(0, 0, 14, 16, 10, 16);
    private final DyeColor color;
    public WoodenCoffinBlock(DyeColor color, Properties properties) {
        super(properties); this.color = color;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT).setValue(OCCUPIED, false).setValue(CLOSED, false));
    }
    public DyeColor getColor() { return color; }
    @Override protected EnvironmentAttribute<BedRule> getBedEnvironmentAttribute() { return EnvironmentAttributes.BED_RULE; }
    @Override public BedRule getBedRule(Level level, BlockPos pos) { return super.getBedRule(level, pos).canSetSpawn(level) ? COFFIN_RULE : BedRule.DESTROY_ON_USE; }
    @Override public java.util.OptionalDouble getSleepHeight(BlockState state, Level level, BlockPos pos) { return java.util.OptionalDouble.of(.1875); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.CONSUME;
        if (state.getValue(PART) == BedPart.FOOT) { pos = pos.relative(state.getValue(FACING)); state = level.getBlockState(pos); }
        if (!state.is(this)) return InteractionResult.CONSUME;
        if (getBedRule(level, pos).destroyOnUse()) return destroyOnUse(state, level, pos, player);
        sleepInside(level, pos, state, (ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }
    public void sleepInside(Level level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (player.isShiftKeyDown() || state.getValue(OCCUPIED)) {
            level.setBlock(pos, state.setValue(CLOSED, !state.getValue(CLOSED)), 3);
            if (!player.isShiftKeyDown()) player.sendOverlayMessage(Component.translatable("block.minecraft.bed.occupied"));
            return;
        }
        player.setAttached(VampireAttachments.COFFIN_RESPAWN, false);
        player.startSleepInBed(this, state, getBedRule(level, pos), pos).ifLeft(problem -> {
            if (problem != null && problem.message() != null) player.sendOverlayMessage(problem.message());
        });
    }
    @Override protected InteractionResult destroyOnUse(BlockState state, Level level, BlockPos pos, Player player) {
        level.removeBlock(pos, false);
        var other = pos.relative(state.getValue(FACING).getOpposite());
        if (level.getBlockState(other).is(this)) level.removeBlock(other, false);
        for (var living : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(6), e -> e.isAlive() && !e.isSpectator())) {
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100)); living.clearFire();
        }
        level.explode(null, level.damageSources().badRespawnPointExplosion(Vec3.atCenterOf(pos)), null,
                pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 5, false, Level.ExplosionInteraction.BLOCK);
        if (level instanceof net.minecraft.server.level.ServerLevel server) VampireBlood.splash(server, Vec3.atCenterOf(pos), 16, 10);
        return InteractionResult.SUCCESS;
    }
    @Override protected void destroyOnLeave(Level level, BlockPos pos) { }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(CLOSED); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        BlockState result = super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
        if (result.is(this) && direction == getConnectedDirection(state) && neighbor.is(this)) result = result.setValue(CLOSED, neighbor.getValue(CLOSED));
        return result;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(CLOSED)) return Block.box(0, 0, 0, 16, 13, 16);
        Direction direction = state.getValue(FACING);
        if (state.getValue(PART) == BedPart.HEAD) direction = direction.getOpposite();
        return switch (direction) {
            case NORTH -> Shapes.or(BOTTOM, WEST, EAST, SOUTH);
            case SOUTH -> Shapes.or(BOTTOM, WEST, EAST, NORTH);
            case EAST -> Shapes.or(BOTTOM, WEST, NORTH, SOUTH);
            default -> Shapes.or(BOTTOM, EAST, NORTH, SOUTH);
        };
    }
}
