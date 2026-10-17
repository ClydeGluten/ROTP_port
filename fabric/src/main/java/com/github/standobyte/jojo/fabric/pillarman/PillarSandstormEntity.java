package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Source free-flying Divine Sandstorm projectile, shared by Small Sandstorm and Atmospheric Rift. */
public final class PillarSandstormEntity extends Projectile implements PillarmanBaseActions.EvasionProjectile {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PillarSandstormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(PillarSandstormEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(PillarSandstormEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(PillarSandstormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(PillarSandstormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ATMOSPHERIC = SynchedEntityData.defineId(PillarSandstormEntity.class, EntityDataSerializers.BOOLEAN);
    public PillarSandstormEntity(EntityType<? extends PillarSandstormEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public PillarSandstormEntity(LivingEntity owner) {
        this(PillarWindActions.SANDSTORM_ENTITY, owner.level()); setOwner(owner); entityData.set(OWNER, owner.getId());
        setPos(owner.getEyePosition().add(0, .8, 0)); setRot(owner.getYRot(), owner.getXRot());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1); builder.define(RADIUS, 1.5F); builder.define(DAMAGE, 6F); builder.define(LIFE, 40); builder.define(AGE, 0); builder.define(ATMOSPHERIC, false);
    }
    public PillarSandstormEntity configure(boolean atmospheric, float radius, float damage, int duration) {
        if (!Float.isFinite(radius) || radius <= 0 || radius > 16 || !Float.isFinite(damage) || damage < 0 || damage > 10000 || duration < 1 || duration > 12000)
            throw new IllegalArgumentException("Invalid sandstorm parameters");
        entityData.set(ATMOSPHERIC, atmospheric); entityData.set(RADIUS, radius); entityData.set(DAMAGE, damage); entityData.set(LIFE, duration); refreshDimensions(); return this;
    }
    public float radius() { return entityData.get(RADIUS); }
    public int ticksLifespan() { return entityData.get(LIFE); }
    public boolean atmospheric() { return entityData.get(ATMOSPHERIC); }
    public LivingEntity livingOwner() {
        Entity owner = getOwner(); if (owner == null) owner = level().getEntity(entityData.get(OWNER)); return owner instanceof LivingEntity living ? living : null;
    }
    public float wearOffMultiplier() { return Math.min(2 - 2F * tickCount / ticksLifespan(), 1); }
    public float damageAmount() { return entityData.get(DAMAGE) * Math.max(wearOffMultiplier(), .5F); }
    public int particlesPerTick() { return Math.max((int) (radius() * (atmospheric() ? 10 : 2) * wearOffMultiplier()), 1); }
    public float knockbackMultiplier() { return .1F; }
    @Override public boolean canBeEvaded(LivingEntity context) { return false; }
    public boolean canBeDeflected(Entity context) { return false; }
    @Override public boolean standDamage() { return false; }
    @Override public boolean deflect(ProjectileDeflection deflection, Entity entity, EntityReference<Entity> owner, boolean fromAttack, Vec3 position) { return false; }
    @Override public boolean deflect(ProjectileDeflection deflection, Entity entity, EntityReference<Entity> owner, boolean fromAttack, double speed) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.scalable(radius() * 1.2F, radius() * 1.2F); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == RADIUS) refreshDimensions();
        if (accessor == AGE && level().isClientSide()) tickCount = entityData.get(AGE);
    }
    public void shootFrom(LivingEntity owner, float speed, float spread) {
        setPos(getX(), getY() - radius(), getZ());
        Vec3 direction = owner.getLookAngle();
        // Original1.16 projectile Gaussian perturbation, with no addition of shooter velocity.
        Vec3 velocity = direction.normalize().add(random.nextGaussian() * .0075 * spread, random.nextGaussian() * .0075 * spread, random.nextGaussian() * .0075 * spread).scale(speed);
        setDeltaMovement(velocity);
        setYRot((float) -Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
        setXRot((float) -Math.toDegrees(Math.atan2(velocity.y, Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z))));
        yRotO = getYRot(); xRotO = getXRot();
    }
    private boolean eligibleTarget(Entity target) {
        if (target == this || !target.isAlive() || target.isSpectator() || !target.isPickable()) return false;
        var owner = livingOwner(); if (owner == null) return true;
        if (target == owner) return false;
        if (target instanceof Projectile projectile && projectile.getOwner() != null && projectile.getOwner().getUUID().equals(owner.getUUID())) return false;
        if (owner instanceof Player player && target instanceof Player other && !player.canHarmPlayer(other)) return false;
        return !(target instanceof LivingEntity living) || owner.canAttack(living);
    }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        var owner = livingOwner();
        if (!level().isClientSide() && (tickCount > ticksLifespan() || owner != null && !owner.isAlive())) { discard(); return; }
        super.tick();
        if (level() instanceof ServerLevel server) {
            entityData.set(AGE, tickCount); if (owner != null) entityData.set(OWNER, owner.getId());
            Vec3 start = position(), end = start.add(getDeltaMovement());
            var block = server.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 until = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            var entityHit = ProjectileUtil.getEntityHitResult(server, this, start, until, getBoundingBox().expandTowards(getDeltaMovement()).inflate(1), this::eligibleTarget, .3F);
            if (entityHit != null) hitEntity(server, entityHit.getEntity());
            else if (block.getType() == HitResult.Type.BLOCK) { destroyBlock(server, block.getBlockPos()); discard(); }
        }
        if (!isRemoved()) setPos(position().add(getDeltaMovement()));
        if (level().isClientSide()) {
            Vec3 center = getBoundingBox().getCenter();
            for (int i = 0; i < particlesPerTick(); i++) {
                // Original uses random-1 (negative octant), not a centered sphere distribution.
                Vec3 point = center.add(new Vec3(random.nextDouble() - 1, random.nextDouble() - 1, random.nextDouble() - 1).normalize().scale(random.nextDouble() * radius()));
                level().addParticle(atmospheric() ? PillarWindActions.RIFT : PillarWindActions.SANDSTORM, point.x, point.y, point.z, 0, 0, 0);
            }
        }
    }
    private void hitEntity(ServerLevel level, Entity target) {
        var source = new DamageSource(level.registryAccess().getOrThrow(DamageTypes.ARROW), this, livingOwner());
        boolean hurt = target instanceof LivingEntity living ? StandDamage.hurt(level, living, source, damageAmount()) : target.hurtServer(level, source, damageAmount());
        if (hurt && target instanceof LivingEntity living) {
            double strength = radius() * .035F * (1 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            living.setDeltaMovement(living.getDeltaMovement().add(Vec3.directionFromRotation(getXRot(), getYRot()).scale(strength))); living.syncVelocity = true;
        }
        // breakProjectile(ENTITY) deliberately does nothing: later ticks may hit this or another target.
    }
    private void destroyBlock(ServerLevel level, BlockPos pos) {
        var block = level.getBlockState(pos); float hardness = block.getDestroySpeed(level, pos); var owner = livingOwner();
        if (!level.getGameRules().get(JojoGameRules.BREAK_BLOCKS) || hardness < 0 || hardness > 5) return;
        if (owner instanceof Player player && (!level.mayInteract(player, pos)
                || !net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, block, level.getBlockEntity(pos)))) return;
        level.destroyBlock(pos, !(owner instanceof Player player) || !player.isCreative(), owner);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (isInvulnerableToBase(source)) return false; discard(); return true;
    }
    @Override public net.minecraft.world.scores.PlayerTeam getTeam() { var owner = livingOwner(); return owner == null ? super.getTeam() : owner.getTeam(); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putFloat("Radius", radius()); output.putFloat("Damage", entityData.get(DAMAGE));
        output.putInt("Duration", ticksLifespan()); output.putBoolean("atmosphericRift", atmospheric()); output.putInt("Age", tickCount);
        output.putFloat("Particles", radius() * (atmospheric() ? 10 : 2)); output.putFloat("XOriginOffset", 0);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        float radius = input.getFloatOr("Radius", 1.5F), damage = input.getFloatOr("Damage", 6);
        configure(input.getBooleanOr("atmosphericRift", false), Float.isFinite(radius) ? Math.clamp(radius, .01F, 16) : 1.5F,
                Float.isFinite(damage) ? Math.clamp(damage, 0, 10000) : 0, Math.clamp(input.getIntOr("Duration", 40), 1, 12000));
        tickCount = Math.clamp(input.getIntOr("Age", 0), 0, 12001); entityData.set(AGE, tickCount);
    }
}
