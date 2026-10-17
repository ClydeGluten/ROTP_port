package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Source CustomExplosion ray/impact formula and PillarmanExplosion's boiling-blood finish. */
public final class PillarHeatExplosion extends ServerExplosion {
    private final ServerPlayer owner;
    private final ExplosionDamageCalculator calculator;
    private List<BlockPos> affected = List.of();
    public PillarHeatExplosion(ServerPlayer owner) {
        super(owner.level(), owner, new DamageSource(owner.registryAccess().getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,
                        RipplesFabric.id("pillarman_detonation")))), new EntityBasedExplosionDamageCalculator(owner), owner.position(), 3, true, BlockInteraction.DESTROY);
        this.owner = owner; calculator = new EntityBasedExplosionDamageCalculator(owner);
    }
    public List<BlockPos> affectedBlocks() { return List.copyOf(affected); }
    public static boolean canGrief(ServerLevel level) { return level.getGameRules().get(GameRules.MOB_GRIEFING); }
    public static boolean meltIceAndSnow(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE))) return false;
        if (level.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES) || !state.isCollisionShapeFullBlock(level, pos)) level.removeBlock(pos, false);
        else { level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState()); level.updateNeighborsAt(pos, Blocks.WATER); }
        return true;
    }
    private List<BlockPos> traceBlocks() {
        var result = new HashSet<BlockPos>(); var level = level();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            if (x != 0 && x != 15 && y != 0 && y != 15 && z != 0 && z != 15) continue;
            Vec3 step = new Vec3(x / 15F * 2 - 1, y / 15F * 2 - 1, z / 15F * 2 - 1).normalize().scale(.3);
            Vec3 position = center();
            for (float power = radius() * (.7F + level.getRandom().nextFloat() * .6F); power > 0; power -= .225F) {
                BlockPos pos = BlockPos.containing(position); if (!level.isInWorldBounds(pos)) break;
                var state = level.getBlockState(pos); var resistance = calculator.getBlockExplosionResistance(this, level, pos, state, state.getFluidState());
                if (resistance.isPresent()) power -= (resistance.get() + .3F) * .3F;
                if (power > 0 && calculator.shouldBlockExplode(this, level, pos, state, power)) result.add(pos);
                position = position.add(step);
            }
        }
        return new ArrayList<>(result);
    }
    private boolean canHarm(Entity entity) {
        if (entity == owner || !(entity instanceof LivingEntity living) || !owner.canAttack(living)) return false;
        var team = owner.getTeam();
        return team == null || !team.isAlliedTo(entity.getTeam()) || team.isAllowFriendlyFire();
    }
    @Override public int explode() {
        affected = traceBlocks();
        double diameter = radius() * 2;
        for (var entity : level().getEntities(owner, new AABB(center(), center()).inflate(diameter + 1), this::canHarm)) {
            if (entity.ignoreExplosion(this)) continue;
            double distance = entity.position().distanceTo(center()) / diameter;
            Vec3 direction = entity.getEyePosition().subtract(center());
            if (distance > 1 || direction.length() <= 1E-4) continue;
            double impact = (1 - distance) * getSeenPercent(center(), entity);
            float damage = (float)((impact * impact + impact) / 2 * 7 * diameter + 1);
            entity.hurtServer(level(), getDamageSource(), damage);
            // CustomExplosion computes protection damping but actually passes undamped impact to hurtEntity.
            Vec3 motion = direction.normalize().scale(impact);
            entity.setDeltaMovement(entity.getDeltaMovement().add(motion)); entity.syncVelocity = true;
            if (entity instanceof ServerPlayer player && !player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) getHitPlayers().put(player, motion);
        }
        var shuffled = new ArrayList<>(affected); Collections.shuffle(shuffled, new Random(level().getRandom().nextLong()));
        var drops = new ArrayList<Drop>();
        for (var pos : shuffled) {
            var state = level().getBlockState(pos);
            if (!state.isAir()) state.onExplosionHit(level(), pos, this, (stack, at) -> collect(drops, stack, at));
        }
        for (var drop : drops) Block.popResource(level(), drop.pos, drop.stack);
        if (canGrief(level())) for (var pos : affected) {
            if (level().isEmptyBlock(pos)) {
                if (!level().isEmptyBlock(pos.below()) && level().getRandom().nextDouble() < .25)
                    level().setBlockAndUpdate(pos, PillarWorldContent.BOILING_BLOOD_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));
                else level().setBlockAndUpdate(pos, BaseFireBlock.getState(level(), pos));
            } else meltIceAndSnow(level(), pos);
        }
        level().playSound(null, center().x, center().y, center().z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4,
                (1 + (level().getRandom().nextFloat() - level().getRandom().nextFloat()) * .2F) * .7F);
        level().sendParticles(ParticleTypes.EXPLOSION_EMITTER, center().x, center().y, center().z, 1, 0, 0, 0, 0);
        return affected.size();
    }
    private record Drop(ItemStack stack, BlockPos pos) { }
    private static void collect(List<Drop> drops, ItemStack stack, BlockPos pos) {
        for (var drop : drops) if (ItemStack.isSameItemSameComponents(drop.stack, stack)) {
            int moved = Math.min(stack.getCount(), Math.max(0, Math.min(16, drop.stack.getMaxStackSize()) - drop.stack.getCount()));
            drop.stack.grow(moved); stack.shrink(moved); if (stack.isEmpty()) return;
        }
        drops.add(new Drop(stack, pos.immutable()));
    }
}
