package com.github.standobyte.jojo.fabric.stand.hierophant;

import com.github.standobyte.jojo.fabric.StandDamage;
import com.github.standobyte.jojo.fabric.TimeStopService;
import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Fixed-anchor or user-to-Stand barrier; crossing during stopped time records the rip without advancing its effects. */
public final class HGBarrierEntity extends OwnedBodyPartEntity {
    private static final EntityDataAccessor<Integer> USER = SynchedEntityData.defineId(HGBarrierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> ORIGIN = SynchedEntityData.defineId(HGBarrierEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> RIPPED = SynchedEntityData.defineId(HGBarrierEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3fc> RIP_OFFSET = SynchedEntityData.defineId(HGBarrierEntity.class, EntityDataSerializers.VECTOR3);
    private boolean hurtUser;
    private int rippedTicks = -1;
    public HGBarrierEntity(EntityType<? extends HGBarrierEntity> type, Level level) { super(type, level); }
    public HGBarrierEntity(HierophantGreenEntity stand) {
        this(HierophantGreenActions.BARRIER_ENTITY, stand.level()); initializeOwner(stand, 40);
        entityData.set(USER, stand.ownerId()); setPos(stand.getEyePosition().add(ownerRelativeOffset()));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(USER, -1); builder.define(ORIGIN, Optional.empty());
        builder.define(RIPPED, false); builder.define(RIP_OFFSET, new Vector3f());
    }
    public LivingEntity user() { return level().getEntity(entityData.get(USER)) instanceof LivingEntity living ? living : null; }
    public BlockPos getOriginBlockPos() { return entityData.get(ORIGIN).orElse(null); }
    public void setOriginBlockPos(BlockPos pos) { entityData.set(ORIGIN, Optional.of(pos.immutable())); }
    @Override public Vec3 origin(float partialTick) {
        var block = entityData.get(ORIGIN); var user = user();
        return block.isPresent() ? Vec3.atCenterOf(block.get()) : user == null ? position() : user.getPosition(partialTick);
    }
    @Override public Vec3 bodyOrigin(float partialTick) { return origin(partialTick); }
    @Override protected Vec3 ownerRelativeOffset() { return new Vec3(.15, -1.4, 0); }
    @Override public int ticksLifespan() { return Integer.MAX_VALUE; }
    @Override protected float movementSpeed() { return 0; }
    @Override protected float baseDamage() { return 2; }
    @Override protected float maxHardness() { return 0; }
    @Override public float knockbackMultiplier() { return 0; }
    @Override public boolean standDamage() { return true; }
    @Override public boolean isBodyPart() { return true; }
    @Override public boolean isPickable() { return false; }
    @Override protected DamageSource damageSource(ServerLevel server) {
        return new DamageSource(server.registryAccess().getOrThrow(DamageTypes.ARROW), this,
                livingOwner() instanceof HierophantGreenEntity stand ? stand.owner() : livingOwner());
    }
    @Override protected boolean eligibleTarget(Entity target) {
        return super.eligibleTarget(target) && (!(livingOwner() instanceof HierophantGreenEntity stand) || stand.canAttackTarget(target));
    }
    public boolean wasRipped() { return entityData.get(RIPPED); }
    public int rippedTicks() { return rippedTicks; }
    public Optional<Vec3> wasRippedAt() {
        var offset = entityData.get(RIP_OFFSET);
        return wasRipped() ? Optional.of(position().add(offset.x(), offset.y(), offset.z())) : Optional.empty();
    }
    private void ripAt(Vec3 position) {
        if (wasRipped()) return;
        Vec3 offset = position.subtract(position()); entityData.set(RIP_OFFSET, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
        entityData.set(RIPPED, true); rippedTicks = 40;
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor); if (accessor.equals(RIPPED) && wasRipped()) rippedTicks = 40;
    }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) { tickFrozen(); return; }
        var owner = livingOwner(); var user = user();
        if (!level().isClientSide() && (owner == null || !owner.isAlive() || user == null || !user.isAlive() || owner.level() != level())) { discard(); return; }
        if (rippedTicks > 0) {
            if (--rippedTicks == 0) { if (!level().isClientSide()) discard(); return; }
            if (level() instanceof ServerLevel server && !hurtUser && user != null) {
                StandDamage.hurt(server, user, damageSources().generic(), .2F); hurtUser = true;
            }
            return;
        }
        if (level() instanceof ServerLevel server) hitSegment(server);
        if (isRemoved() || wasRipped()) return;
        Vec3 next = attachedBlock().map(Vec3::atCenterOf).orElseGet(() -> owner == null ? position() : owner.getEyePosition().add(ownerRelativeOffset()));
        // Source moveToBlockAttached()/HG moveBoundToOwner() teleport the endpoint without
        // assigning velocity. Keeping its previous displacement would extrapolate the next
        // collision segment beyond the anchor and miss entities crossing the actual string.
        setDeltaMovement(Vec3.ZERO); setPos(next);
    }
    /** Called by root's frozen-entity interception, because vanilla skips tick() entirely in that path. */
    public void tickFrozen() {
        if (!(level() instanceof ServerLevel) || wasRipped()) return;
        var owner = livingOwner(); var user = user();
        if (owner == null || !owner.isAlive() || user == null || !user.isAlive()) { discard(); return; }
        scan(true);
    }
    @Override protected void hitSegment(ServerLevel server) { if (attachedBlock().isPresent() && !wasRipped()) scan(false); }
    private void scan(boolean frozen) {
        Vec3 start = origin(1), end = position().add(getDeltaMovement());
        record Hit(Entity target, Vec3 point) { }
        var hits = new java.util.ArrayList<Hit>();
        for (Entity target : level().getEntities(this, new AABB(start, end).inflate(1), this::eligibleTarget)) {
            var box = target.getBoundingBox().inflate(target.getPickRadius() + getBbWidth() / 2);
            var point = box.clip(start, end);
            if (box.contains(start) || point.isPresent()) hits.add(new Hit(target, point.orElse(start)));
        }
        hits.sort(java.util.Comparator.comparingDouble(hit -> hit.point.distanceToSqr(start)));
        if (frozen) { if (!hits.isEmpty()) ripAt(hits.getFirst().point); return; }
        for (var hit : hits) {
            hit.target.setDeltaMovement(Vec3.ZERO);
            hurtTarget((ServerLevel) level(), hit.target); ripAt(hit.point);
            if (livingOwner() instanceof HierophantGreenEntity stand) {
                stand.getBarriersNet().shootEmeraldsFromBarriers(stand, hit.target.getBoundingBox().getCenter(), 0,
                        20 * stand.staminaCondition(), 1.5F, 2, false);
                stand.playOriginalAt("hierophant_green_barrier_ripped", hit.target.position());
            }
        }
    }
}
