package com.github.standobyte.jojo.fabric.stand.silverchariot;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedFireBlock;
import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Active SCFlameSwingEntity/MRFlame inheritance: a twenty-tick, extinguishable, terrain-affecting Stand flame. */
public final class SilverChariotFlameSwingEntity extends Projectile implements SilverChariotEntity.DeflectableProjectile {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SilverChariotFlameSwingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START_X = SynchedEntityData.defineId(SilverChariotFlameSwingEntity.class, EntityDataSerializers.LONG),
            START_Y = SynchedEntityData.defineId(SilverChariotFlameSwingEntity.class, EntityDataSerializers.LONG), START_Z = SynchedEntityData.defineId(SilverChariotFlameSwingEntity.class, EntityDataSerializers.LONG);
    private LivingEntity cachedOwner;
    private boolean deflected;
    private float damageFactor = 1;
    public SilverChariotFlameSwingEntity(EntityType<? extends SilverChariotFlameSwingEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public SilverChariotFlameSwingEntity(SilverChariotEntity stand) {
        this(SilverChariotActions.FLAME, stand.level()); setOwner(stand); damageFactor = (float) stand.attackDamage() / 8;
        LivingEntity origin = stand;
        setPos(origin.getEyePosition().add(new Vec3(0, -.3, .75).add(new Vec3(0, .2, 0).xRot(-origin.getXRot() * ((float) Math.PI / 180)))
                .yRot(-origin.getYRot() * ((float) Math.PI / 180))));
        entityData.set(START_X, Double.doubleToLongBits(getX())); entityData.set(START_Y, Double.doubleToLongBits(getY())); entityData.set(START_Z, Double.doubleToLongBits(getZ()));
        setRot(stand.getYRot(), stand.getXRot()); Vec3 direction = stand.getLookAngle(); shoot(direction.x, direction.y, direction.z, 1.5F, 0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1); builder.define(START_X, 0L); builder.define(START_Y, 0L); builder.define(START_Z, 0L);
    }
    @Override public void setOwner(Entity owner) { super.setOwner(owner); cachedOwner = owner instanceof LivingEntity living ? living : null; entityData.set(OWNER, owner == null ? -1 : owner.getId()); }
    @Override public LivingEntity getOwner() {
        if (cachedOwner == null) {
            Entity found = super.getOwner(); if (found == null) found = level().getEntity(entityData.get(OWNER));
            if (found instanceof LivingEntity living) cachedOwner = living;
        }
        return cachedOwner;
    }
    public Vec3 getStartingPos() { return new Vec3(Double.longBitsToDouble(entityData.get(START_X)), Double.longBitsToDouble(entityData.get(START_Y)), Double.longBitsToDouble(entityData.get(START_Z))); }
    @Override public boolean standDamage() { return true; }
    @Override public boolean canBeEvaded(LivingEntity target) { return false; }
    @Override public boolean canBeDeflected(Entity context) { return true; }
    @Override public boolean isFiery() { return true; }
    @Override public boolean isOnFire() { return false; }
    @Override public void setDeflected() { deflected = true; }
    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 1; }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        var owner = getOwner();
        if (!level().isClientSide() && (tickCount > 20 || owner != null && !owner.isAlive())) { discard(); return; }
        if (isInWater() || level().isRainingAt(blockPosition())) { clearFire(); return; }
        super.tick();
        if (level() instanceof ServerLevel server) {
            Vec3 from = position(), to = from.add(getDeltaMovement());
            HitResult result = server.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
            double closest = result.getType() == HitResult.Type.MISS ? from.distanceToSqr(to) : from.distanceToSqr(result.getLocation());
            for (var target : server.getEntities(this, new AABB(from, to).inflate(1), entity -> SilverChariotRapierEntity.eligible(this, owner, entity, deflected))) {
                var bounds = target.getBoundingBox().inflate(target.getPickRadius());
                var location = bounds.contains(from) ? java.util.Optional.of(from) : bounds.clip(from, to);
                if (location.isPresent() && from.distanceToSqr(location.get()) <= closest) { closest = from.distanceToSqr(location.get()); result = new EntityHitResult(target, location.get()); }
            }
            if (result instanceof EntityHitResult hit) { hurtTarget(server, hit.getEntity()); discard(); }
            else if (result instanceof BlockHitResult hit && result.getType() == HitResult.Type.BLOCK) hitBlock(server, hit);
        }
        setPos(position().add(getDeltaMovement())); updateRotation();
    }
    private void hurtTarget(ServerLevel server, Entity target) {
        int oldFire = target.getRemainingFireTicks(); Vec3 oldMotion = target.getDeltaMovement(); target.igniteForSeconds(10);
        var source = new DamageSource(server.registryAccess().getOrThrow(DamageTypes.FIREBALL), this, getOwner());
        float damage = damageFactor * (float)StandConfig.server().standPowerMultiplier();
        boolean hurt = target instanceof LivingEntity living ? StandDamage.hurt(server, living, source, damage) : target.hurtServer(server, source, damage);
        if (!hurt) target.setRemainingFireTicks(oldFire);
        target.setDeltaMovement(oldMotion.add(target.getDeltaMovement().subtract(oldMotion).scale(.1)));
    }
    private void hitBlock(ServerLevel server, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos(); BlockState state = server.getBlockState(pos);
        var owner = getOwner(); var user = owner instanceof StandManifestation stand ? stand.owner() : owner;
        boolean permitted = !(user instanceof net.minecraft.world.entity.player.Player player)
                || player.mayBuild() && server.mayInteract(player, pos)
                && PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(server, player, pos, state, server.getBlockEntity(pos));
        if (permitted && server.getGameRules().get(JojoGameRules.BREAK_BLOCKS)) {
            var flammable = FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
            if (flammable != null && flammable.getBurnOdds() > 0) {
                if (state.getBlock() instanceof TntBlock) {
                    RestorationHooks.remember(server, pos, state, server.getBlockEntity(pos), java.util.List.of());
                    TntBlock.prime(server, pos, owner, ItemStack.EMPTY); server.removeBlock(pos, false);
                }
            } else if (!(state.getBlock() instanceof BaseFireBlock) && state.getDestroySpeed(server, pos) == 0) {
                boolean drops = !(user instanceof net.minecraft.world.entity.player.Player player) || !player.isCreative();
                if (!drops) RestorationHooks.remember(server, pos, state, server.getBlockEntity(pos), java.util.List.of());
                server.destroyBlock(pos, drops, owner);
            }
        }
        if (server.getGameRules().get(GameRules.MOB_GRIEFING)) {
            state = server.getBlockState(pos);
            if (!meltIceAndSnow(server, state, pos) && !state.getCollisionShape(server, pos).isEmpty()) {
                BlockPos firePos = pos.relative(hit.getDirection());
                if (server.isEmptyBlock(firePos)) server.setBlockAndUpdate(firePos, MagiciansRedFireBlock.BLOCK.getStateForPlacement(server, firePos));
            }
        }
        if (server.getBlockState(pos).isCollisionShapeFullBlock(server, pos)) discard();
    }
    public static boolean meltIceAndSnow(ServerLevel server, BlockState state, BlockPos pos) {
        if (!state.is(BlockTags.ICE) && !state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) return false;
        if (server.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES)
                || !state.isCollisionShapeFullBlock(server, pos)) {
            RestorationHooks.remember(server, pos, state, server.getBlockEntity(pos), java.util.List.of()); server.removeBlock(pos, false);
        }
        else server.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        return true;
    }
    @Override public void clearFire() {
        super.clearFire();
        if (level() instanceof ServerLevel server && !isRemoved()) {
            server.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1, 1);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 8, getBbWidth() / 2, getBbHeight() / 2, getBbWidth() / 2, 0); discard();
        }
    }
    @Override public boolean hurtServer(ServerLevel server, DamageSource source, float damage) {
        if (isInvulnerableToBase(source)) return false; discard(); return true;
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.putBoolean("IsDeflected", deflected); output.putFloat("DamageFactor", damageFactor); }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); cachedOwner = null; deflected = input.getBooleanOr("IsDeflected", false);
        float factor = input.getFloatOr("DamageFactor", 1); damageFactor = Float.isFinite(factor) ? Math.max(0, factor) : 1;
    }
}
