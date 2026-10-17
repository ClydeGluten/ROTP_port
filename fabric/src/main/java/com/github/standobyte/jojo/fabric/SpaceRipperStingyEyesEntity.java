package com.github.standobyte.jojo.fabric;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Original growing owner-bound eye beam, then its detached traveling segment. */
public final class SpaceRipperStingyEyesEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> BOUND = SynchedEntityData.defineId(SpaceRipperStingyEyesEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RIGHT = SynchedEntityData.defineId(SpaceRipperStingyEyesEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(SpaceRipperStingyEyesEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SpaceRipperStingyEyesEntity.class, EntityDataSerializers.INT);
    private double distance;
    private boolean retracting;
    private Vec3 detachedOrigin;
    public SpaceRipperStingyEyesEntity(EntityType<? extends SpaceRipperStingyEyesEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public SpaceRipperStingyEyesEntity(LivingEntity owner, boolean rightEye) {
        this(VampireCombatService.EYES, owner.level()); setOwner(owner); entityData.set(OWNER, owner.getId()); entityData.set(RIGHT, rightEye);
        setPos(origin(1)); setRot(owner.getYRot(), owner.getXRot());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BOUND, true); builder.define(RIGHT, false); builder.define(LENGTH, 0F); builder.define(OWNER, -1);
    }
    public boolean bound() { return entityData.get(BOUND); }
    public Entity boundOwner() { return ownerEntity(); }
    private Entity ownerEntity() {
        Entity owner = getOwner(); return owner == null ? level().getEntity(entityData.get(OWNER)) : owner;
    }
    public Vec3 origin(float partialTick) {
        if (!bound()) return detachedOrigin == null ? getPosition(partialTick).subtract(getLookAngle().scale(entityData.get(LENGTH)))
                : detachedOrigin.add(getPosition(partialTick).subtract(position()));
        Entity owner = ownerEntity();
        if (owner == null) return getPosition(partialTick);
        float yaw = net.minecraft.util.Mth.lerp(partialTick, owner.yRotO, owner.getYRot());
        return owner.getEyePosition(partialTick).add(new Vec3(entityData.get(RIGHT) ? -.09375 : .09375, -.2, 0)
                .add(new Vec3(0, .2, 0).xRot(-owner.getXRot() * ((float) Math.PI / 180)))
                .yRot(-yaw * ((float) Math.PI / 180)));
    }
    private float speed() { return .5F + level().getDifficulty().getId() * .25F; }
    public void detach() {
        if (!bound() || level().isClientSide()) return;
        detachedOrigin = origin(1);
        entityData.set(LENGTH, (float) position().distanceTo(detachedOrigin));
        entityData.set(BOUND, false);
        setDeltaMovement(position().subtract(detachedOrigin).normalize().scale(speed()));
        syncVelocity = true; needsSync = true;
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        // Metadata arrives after the spawn position, with the eye side before owner id.
        // Seed late-tracking clients from the existing beam instead of growing from zero.
        if (accessor.equals(OWNER) && level().isClientSide() && bound() && ownerEntity() != null)
            distance = position().distanceTo(origin(1));
        if (accessor.equals(BOUND) && !bound()) {
            Entity owner = ownerEntity();
            if (owner != null) detachedOrigin = owner.getEyePosition().add(new Vec3(entityData.get(RIGHT) ? -.09375 : .09375, -.2, 0)
                    .add(new Vec3(0, .2, 0).xRot(-owner.getXRot() * ((float) Math.PI / 180))).yRot(-owner.getYRot() * ((float) Math.PI / 180)));
        }
        super.onSyncedDataUpdated(accessor);
    }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        super.tick();
        if (level() instanceof ServerLevel server) hitSegment(server);
        Vec3 before = position();
        if (bound()) {
            Entity owner = ownerEntity();
            if (owner == null || !owner.isAlive()) { if (!level().isClientSide()) discard(); return; }
            setRot(owner.getYRot(), owner.getXRot());
            distance += retracting ? -speed() : speed();
            if (distance <= 0) { discard(); return; }
            Vec3 next = origin(1).add(owner.getLookAngle().scale(distance));
            setDeltaMovement(next.subtract(before)); setPos(next);
        } else {
            setPos(before.add(getDeltaMovement()));
            if (detachedOrigin != null) detachedOrigin = detachedOrigin.add(getDeltaMovement());
        }
        if (level() instanceof ServerLevel server) {
            if (tickCount > 20) detach();
            int lifespan = bound() ? 50 : (int) Math.floor(entityData.get(LENGTH) / speed() * 20) + 20;
            if (tickCount >= lifespan) discard();
        }
    }
    private void hitSegment(ServerLevel level) {
        Vec3 start = origin(1), end = position().add(getDeltaMovement());
        // Original rayTraceMultipleEntities returns every intersected entity; only
        // an empty entity result falls back to the block ray, even behind a wall.
        var box = new AABB(start, end).inflate(1);
        Entity owner = ownerEntity(); boolean hitEntity = false;
        for (var target : level.getEntities(this, box, entity -> entity.isAlive() && entity != owner && entity.isPickable() && !entity.isSpectator())) {
            if (target instanceof StarPlatinumEntity stand && owner != null && stand.ownerId() == owner.getId()) continue;
            if (target instanceof Projectile projectile && owner != null && projectile.getOwner() != null && projectile.getOwner().getUUID().equals(owner.getUUID())) continue;
            if (owner instanceof Player player && target instanceof Player other && !player.canHarmPlayer(other)) continue;
            var bounds = target.getBoundingBox().inflate(target.getPickRadius() + getBbWidth() / 2);
            if (bounds.clip(start, end).isEmpty() && !bounds.contains(start)) continue;
            hitEntity = true;
            var source = new DamageSource(level.registryAccess().getOrThrow(VampireCombatService.EYES_DAMAGE), this, owner);
            if (target instanceof LivingEntity living) StandDamage.hurt(level, living, source, 1 + level.getDifficulty().getId());
            else target.hurtServer(level, source, 1 + level.getDifficulty().getId());
        }
        if (hitEntity) return;
        var blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            var pos = blockHit.getBlockPos(); var state = level.getBlockState(pos); float hardness = state.getDestroySpeed(level, pos);
            boolean allowed = level.getGameRules().get(JojoGameRules.BREAK_BLOCKS) && hardness >= 0 && hardness <= 3;
            if (owner instanceof Player player) allowed &= player.mayBuild() && level.mayInteract(player, pos);
            if (allowed) level.destroyBlock(pos, !(owner instanceof Player player) || !player.isCreative(), owner);
            else retracting = true;
        }
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    public AABB beamBounds() { return getBoundingBox().expandTowards(origin(1).subtract(position())); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putBoolean("BoundToOwner", bound()); output.putBoolean("RightEye", entityData.get(RIGHT));
        output.putFloat("Length", entityData.get(LENGTH)); output.putDouble("Distance", distance); output.putBoolean("Retracting", retracting);
        if (detachedOrigin != null) { output.putDouble("OriginX", detachedOrigin.x); output.putDouble("OriginY", detachedOrigin.y); output.putDouble("OriginZ", detachedOrigin.z); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); entityData.set(RIGHT, input.getBooleanOr("RightEye", false)); entityData.set(LENGTH, input.getFloatOr("Length", 0));
        distance = input.getDoubleOr("Distance", 0); retracting = input.getBooleanOr("Retracting", false);
        entityData.set(BOUND, input.getBooleanOr("BoundToOwner", true));
        if (!bound()) detachedOrigin = new Vec3(input.getDoubleOr("OriginX", getX()), input.getDoubleOr("OriginY", getY()), input.getDoubleOr("OriginZ", getZ()));
    }
}
