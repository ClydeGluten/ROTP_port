package com.github.standobyte.jojo.fabric.stand;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.restoration.*;
import com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Original physical shards, including weak history linkage and the active reversed NBT-method bug. */
public final class StandBlockShardEntity extends Projectile implements BrokenBlockRecord.RestorableBlockEntity, SilverChariotEntity.DeflectableProjectile {
    private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(StandBlockShardEntity.class, EntityDataSerializers.INT),
            OWNER = SynchedEntityData.defineId(StandBlockShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> ORIGIN = SynchedEntityData.defineId(StandBlockShardEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> RESTORED = SynchedEntityData.defineId(StandBlockShardEntity.class, EntityDataSerializers.BOOLEAN);
    private int restoreTick = 1; private boolean deflected;
    public StandBlockShardEntity(EntityType<? extends StandBlockShardEntity> type, Level level) { super(type, level); }
    public StandBlockShardEntity(ServerLevel level, LivingEntity owner, BlockState block, BlockPos origin) {
        this(StandHeavyImpact.SHARD, level); setOwner(owner); entityData.set(BLOCK, Block.getId(block)); entityData.set(ORIGIN, Optional.ofNullable(origin));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(BLOCK, Block.getId(Blocks.COBBLESTONE.defaultBlockState())); builder.define(OWNER, -1); builder.define(ORIGIN, Optional.empty()); builder.define(RESTORED, false); }
    public BlockState block() { return Block.stateById(entityData.get(BLOCK)); }
    public boolean restored() { return entityData.get(RESTORED); }
    @Override public boolean isEntityAlive() { return isAlive(); }
    @Override public boolean crazyDRestore(BlockPos ignored) { entityData.set(RESTORED, true); return true; }
    @Override public void setOwner(Entity owner) { super.setOwner(owner); entityData.set(OWNER, owner == null ? -1 : owner.getId()); }
    @Override public Entity getOwner() { var owner = super.getOwner(); return owner != null ? owner : level().getEntity(entityData.get(OWNER)); }
    private Entity user() { return getOwner() instanceof StandManifestation stand ? stand.owner() : getOwner(); }
    @Override public boolean standDamage() { return false; }
    @Override public boolean canBeEvaded(LivingEntity context) { return true; }
    @Override public boolean canBeDeflected(Entity context) { return true; }
    @Override public boolean isFiery() { return false; }
    @Override public void setDeflected() { deflected = true; }
    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 1; }
    @Override public void shoot(double x, double y, double z, float speed, float inaccuracy) {
        setDeltaMovement(new Vec3(x, y, z).normalize().add(random.nextGaussian() * .0075 * inaccuracy, random.nextGaussian() * .0075 * inaccuracy, random.nextGaussian() * .0075 * inaccuracy).scale(speed));
        updateRotation(); needsSync = true;
    }
    private static java.util.function.Consumer<StandBlockShardEntity> clientBreak = s -> { };
    public static void setClientBreak(java.util.function.Consumer<StandBlockShardEntity> handler) { clientBreak = Objects.requireNonNull(handler); }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        if (!level().isClientSide() && (tickCount > 100 || getOwner() != null && !getOwner().isAlive())) { discard(); return; }
        super.tick();
        var from = position(); var velocity = getDeltaMovement(); var to = from.add(velocity);
        if (level() instanceof ServerLevel level) {
            HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            double distance = hit.getType() == HitResult.Type.MISS ? from.distanceToSqr(to) : from.distanceToSqr(hit.getLocation());
            for (var entity : level.getEntities(this, new AABB(from, to).inflate(1), this::canHitEntity)) {
                var bounds = entity.getBoundingBox().inflate(entity.getPickRadius()); var point = bounds.contains(from) ? Optional.of(from) : bounds.clip(from, to);
                if (point.isPresent() && from.distanceToSqr(point.get()) < distance) { hit = new EntityHitResult(entity, point.get()); distance = from.distanceToSqr(point.get()); }
            }
            if (hit instanceof EntityHitResult entityHit) {
                var target = entityHit.getEntity(); var source = new DamageSource(level.registryAccess().getOrThrow(DamageTypes.ARROW), this, getOwner());
                float damage = 2.5F * (getOwner() instanceof StandManifestation ? StandConfig.server().standPowerMultiplier() : 1);
                boolean hurt = target instanceof LivingEntity living ? StandDamage.hurt(level, living, source, damage) : target.hurtServer(level, source, damage);
                if (hurt && SourceEraMaterials.glass(block()) && target instanceof LivingEntity living && random.nextFloat() < bleedingChance(living)) bleed(living);
                breakShard();
            } else if (hit instanceof BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) {
                var pos = blockHit.getBlockPos(); var state = level.getBlockState(pos); float hardness = state.getDestroySpeed(level, pos);
                if (hardness >= 0 && hardness <= 0 && level.getGameRules().get(JojoGameRules.BREAK_BLOCKS)
                        && (!(user() instanceof Player player) || player.mayBuild() && level.mayInteract(player, pos)
                        && net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos))))
                    level.destroyBlock(pos, !(user() instanceof Player player) || !player.isCreative(), getOwner());
                breakShard();
            }
        }
        if (isRemoved()) return;
        if (restored() && entityData.get(ORIGIN).isPresent()) {
            if (restoreTick-- == 0 && !level().isClientSide()) { discard(); return; }
            setDeltaMovement(Vec3.atCenterOf(entityData.get(ORIGIN).get()).subtract(position()).scale(.5));
            if (user() instanceof ServerPlayer player && !StandConfig.staminaFree(player)) {
                var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS); var stand = StandService.currentManifestation(player);
                float regen = progress.withStamina(0).regenerate(stand != null, stand == null || stand.allowsStaminaRegen(), player.getFoodData().getFoodLevel(), false).stamina();
                player.setAttached(StandAttachments.PROGRESS, progress.withStamina(Math.max(0, progress.stamina() - regen - 2)));
            }
        }
        var move = getDeltaMovement(); var next = position().add(move);
        setDeltaMovement(move.subtract(0, isNoGravity() ? 0 : .05, 0).scale(.99 * (isInWater() ? .8 : 1)));
        setPos(next); updateRotation();
    }
    @Override protected boolean canHitEntity(Entity target) {
        if (!target.isAlive() || target.isSpectator() || !target.isPickable()) return false;
        var owner = getOwner(); if (target == owner || target == user()) return deflected;
        if (target instanceof Projectile projectile && owner != null && projectile.getOwner() != null && owner.getUUID().equals(projectile.getOwner().getUUID())) return false;
        if (owner instanceof LivingEntity living && target instanceof LivingEntity victim && !living.canAttack(victim)) return false;
        return !(target instanceof Player player && user() instanceof Player attacker && !attacker.canHarmPlayer(player));
    }
    private void breakShard() { level().broadcastEntityEvent(this, (byte) 3); discard(); }
    @Override public void handleEntityEvent(byte id) { if (id == 3 && level().isClientSide()) clientBreak.accept(this); else super.handleEntityEvent(id); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { if (isInvulnerableToBase(source)) return false; breakShard(); return true; }
    public static float bleedingChance(LivingEntity target) { return Math.max(1 - target.getArmorCoverPercentage(), .05F); }
    public static void bleed(LivingEntity target) { target.addEffect(new MobEffectInstance(VampireBlood.BLEEDING, 100, 0, false, false, true)); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("Age", tickCount); output.putBoolean("IsDeflected", deflected);
        // Source addAdditionalSaveData reads the otherwise absent fields from its new save compound.
        entityData.set(BLOCK, Block.getId(Blocks.COBBLESTONE.defaultBlockState())); entityData.set(ORIGIN, Optional.empty()); entityData.set(RESTORED, false);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); tickCount = input.getIntOr("Age", 0); deflected = input.getBooleanOr("IsDeflected", false); }
}
