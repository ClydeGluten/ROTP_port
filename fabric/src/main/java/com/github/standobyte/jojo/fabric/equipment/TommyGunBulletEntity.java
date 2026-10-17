package com.github.standobyte.jojo.fabric.equipment;

import com.github.standobyte.jojo.fabric.JojoGameRules;
import com.github.standobyte.jojo.fabric.StandDamage;
import com.github.standobyte.jojo.fabric.TimeStopService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedList;
import java.util.List;

/**
 * Port of TommyGunBulletEntity + ModdedProjectileEntity + DamagingEntity.
 * 40-tick lifespan, 2.0 base damage piercing invulnerability ticks, breaks blocks with hardness <= 0.3
 * and continues flight at 0.9x speed when destroying soft blocks, plus client trace positions for beam trail.
 */
public final class TommyGunBulletEntity extends Projectile {
    public static final int MAX_LIFESPAN = 40;
    public static final float BASE_DAMAGE = 2.0F;
    public static final float MAX_HARDNESS_BREAKABLE = 0.3F;

    public Vec3 initialPos;
    public final List<Vec3> tracePos = new LinkedList<>();
    private boolean deflected;

    public TommyGunBulletEntity(EntityType<? extends TommyGunBulletEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public TommyGunBulletEntity(LivingEntity shooter, Level level) {
        this(EquipmentContent.TOMMY_GUN_BULLET, level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        setRot(shooter.getYRot(), shooter.getXRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public void shootFromRotation(Entity shooter, float velocity, float inaccuracy) {
        // ModdedProjectileEntity deliberately does not inherit the shooter's velocity.
        Vec3 direction = Vec3.directionFromRotation(shooter.getXRot(), shooter.getYRot());
        shoot(direction.x, direction.y, direction.z, velocity, inaccuracy);
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        double noise = 0.0075F * inaccuracy;
        Vec3 movement = new Vec3(x, y, z).normalize().add(
                random.nextGaussian() * noise,
                random.nextGaussian() * noise,
                random.nextGaussian() * noise).scale(velocity);
        setDeltaMovement(movement);
        double horizontal = movement.horizontalDistance();
        setYRot((float) (Mth.atan2(movement.x, movement.z) * (180.0D / Math.PI)));
        setXRot((float) (Mth.atan2(movement.y, horizontal) * (180.0D / Math.PI)));
        this.yRotO = getYRot();
        this.xRotO = getXRot();
    }

    @Override
    public void tick() {
        if (TimeStopService.isFrozen(this)) {
            return;
        }
        if (tickCount == 5) {
            setNoGravity(false);
        }
        Entity owner = getOwner();
        if (!level().isClientSide() && (tickCount > MAX_LIFESPAN || (owner != null && !owner.isAlive()))) {
            discard();
            return;
        }
        super.tick();
        checkHit();
        if (isRemoved()) return;
        moveProjectile();
        if (level().isClientSide()) {
            Vec3 pos = position();
            boolean addPos = true;
            if (tracePos.size() > 1) {
                Vec3 lastPos = tracePos.getLast();
                addPos = pos.distanceToSqr(lastPos) >= 0.0625D;
            }
            if (addPos) {
                tracePos.add(pos);
            }
        }
    }

    private void moveProjectile() {
        Vec3 motion = getDeltaMovement();
        double x = getX() + motion.x;
        double y = getY() + motion.y;
        double z = getZ() + motion.z;
        if (!isNoGravity() && !constVelocity()) {
            motion = motion.subtract(0.0D, getGravityAcceleration(), 0.0D);
        }
        double inertia = getInertia();
        if (!constVelocity() && inertia < 1.0D) {
            motion = motion.scale(inertia);
        }
        setDeltaMovement(motion);
        rotateTowardsMovement();
        setPos(x, y, z);
    }

    protected boolean constVelocity() {
        return true;
    }

    protected double getGravityAcceleration() {
        return 1.5D;
    }

    protected double getInertia() {
        return 0.99D;
    }

    private void rotateTowardsMovement() {
        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-7D) {
            setYRot((float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI)));
            setXRot((float) (Mth.atan2(motion.y, motion.horizontalDistance()) * (180.0D / Math.PI)));
        }
    }

    protected void checkHit() {
        HitResult[] rayTrace = rayTrace();
        for (HitResult result : rayTrace) {
            if (result.getType() != HitResult.Type.MISS && !isRemoved()) {
                onHit(result);
            }
        }
    }

    protected HitResult[] rayTrace() {
        return new HitResult[]{getHitResult()};
    }

    protected HitResult getHitResult() {
        Vec3 pos = position();
        Vec3 nextPos = pos.add(getDeltaMovement());
        HitResult hitResult = level().clip(new ClipContext(
                pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hitResult.getType() != HitResult.Type.MISS) {
            nextPos = hitResult.getLocation();
        }
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                level(), this, pos, nextPos,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(1.0D),
                this::canHitEntity, 0.3F);
        if (entityHitResult != null) {
            hitResult = entityHitResult;
        }
        return hitResult;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!entity.isPickable() || !EntitySelector.NO_SPECTATORS.test(entity)) {
            return false;
        }
        Entity owner = getOwner();
        if (owner == null) {
            return true;
        }
        if (entity.is(owner)) {
            return deflected;
        }
        if (owner instanceof LivingEntity livingOwner && entity instanceof LivingEntity livingTarget) {
            if (!livingOwner.canAttack(livingTarget)) return false;
            if (owner instanceof Player player && entity instanceof Player target && !player.canHarmPlayer(target)) return false;
            return !entity.isPassengerOfSameVehicle(owner);
        }
        if (entity instanceof Projectile other && other.getOwner() != null
                && owner.getUUID().equals(other.getOwner().getUUID())) {
            return false;
        }
        return true;
    }

    public float getDamageAmount() {
        return BASE_DAMAGE;
    }

    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 1.0F; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source)) return false;
        discard(); return true;
    }
    @Override protected void onDeflection(boolean fromAttack) {
        super.onDeflection(fromAttack);
        deflected = true;
        needsSync = true;
        if (level().isClientSide()) tracePos.add(position());
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        if (level() instanceof ServerLevel server) {
            Entity target = entityHitResult.getEntity();
            Entity owner = getOwner();
            DamageSource damageSource = new DamageSource(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW), this, owner == null ? this : owner);
            boolean hurt = target instanceof LivingEntity living
                    ? StandDamage.hurt(server, living, damageSource, getDamageAmount())
                    : target.hurtServer(server, damageSource, getDamageAmount());
            if (hurt && owner instanceof LivingEntity livingOwner) {
                livingOwner.setLastHurtMob(target);
            }
            discard();
        }
        super.onHitEntity(entityHitResult);
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (level() instanceof ServerLevel server) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            boolean brokenBlock = destroyBlock(server, blockHitResult);
            afterBlockHit(blockPos, brokenBlock);
        }
    }

    private boolean destroyBlock(ServerLevel server, BlockHitResult blockHitResult) {
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState blockState = server.getBlockState(blockPos);
        if (canBreakBlock(server, blockPos, blockState)) {
            Entity owner = getOwner();
            Entity checkEntity = owner != null ? owner : this;
            if (!server.getGameRules().get(JojoGameRules.BREAK_BLOCKS)) {
                return false;
            }
            if (checkEntity instanceof Player player && !server.mayInteract(player, blockPos)) {
                return false;
            }
            boolean dropItem = !(owner instanceof Player player && player.getAbilities().instabuild);
            return server.destroyBlock(blockPos, dropItem, owner != null ? owner : this);
        }
        return false;
    }

    protected boolean canBreakBlock(Level level, BlockPos blockPos, BlockState blockState) {
        Block block = blockState.getBlock();
        float hardness = blockState.getDestroySpeed(level, blockPos);
        return hardness >= 0.0F && hardness <= MAX_HARDNESS_BREAKABLE && block != Blocks.AIR && !blockState.liquid();
    }

    protected void afterBlockHit(BlockPos blockPos, boolean destroyedBlock) {
        if (destroyedBlock) {
            if (!level().isClientSide()) {
                setDeltaMovement(getDeltaMovement().scale(0.9D));
                checkHit();
            }
        } else {
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Age", tickCount);
        output.putBoolean("IsDeflected", deflected);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        tickCount = input.getIntOr("Age", 0);
        deflected = input.getBooleanOr("IsDeflected", false);
    }
}
