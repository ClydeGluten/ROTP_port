package com.github.standobyte.jojo.fabric.stand;

import com.github.standobyte.jojo.fabric.*;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Source OwnerBoundProjectileEntity: a body-origin segment, not an arrow flying independently of its owner. */
public abstract class OwnedBodyPartEntity extends Projectile implements PillarmanBaseActions.EvasionProjectile {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACHED = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> BLOCK = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> BOUND = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FORWARD = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RETRACTING = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DISTANCE = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(OwnedBodyPartEntity.class, EntityDataSerializers.FLOAT);
    private UUID attachedUuid;
    private boolean deflected;
    private float damageFactor = 1;
    private final Set<Entity> dragged = Collections.newSetFromMap(new IdentityHashMap<>());
    protected OwnedBodyPartEntity(EntityType<? extends OwnedBodyPartEntity> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1); builder.define(ATTACHED, -1); builder.define(BLOCK, Optional.empty());
        builder.define(BOUND, true); builder.define(FORWARD, true); builder.define(RETRACTING, false);
        builder.define(DISTANCE, 0F); builder.define(LIFE, 40); builder.define(AGE, 0); builder.define(SPEED, 1F);
    }
    @Override public void setOwner(Entity owner) { super.setOwner(owner); entityData.set(OWNER, owner == null ? -1 : owner.getId()); }
    public LivingEntity livingOwner() {
        Entity owner = getOwner();
        if (owner == null) owner = level().getEntity(entityData.get(OWNER));
        return owner instanceof LivingEntity living ? living : null;
    }
    public void initializeOwner(LivingEntity owner, int lifespan) {
        setOwner(owner); setLifeSpan(lifespan); setPos(origin(1)); setRot(owner.getYRot(), owner.getXRot());
    }
    public Vec3 origin(float partialTick) { return ownerPosition(partialTick, false); }
    public Vec3 bodyOrigin(float partialTick) { return ownerPosition(partialTick, isBodyPart()); }
    protected Vec3 ownerPosition(float partialTick, boolean bodyRotation) {
        var owner = livingOwner(); if (owner == null) return getPosition(partialTick);
        float yaw = net.minecraft.util.Mth.lerp(partialTick, bodyRotation ? owner.yBodyRotO : owner.yRotO, bodyRotation ? owner.yBodyRot : owner.getYRot());
        return owner.getEyePosition(partialTick).add(ownerRelativeOffset().add(pitchOffset().xRot(-owner.getXRot() * ((float) Math.PI / 180))).yRot(-yaw * ((float) Math.PI / 180)));
    }
    protected Vec3 ownerRelativeOffset() { return new Vec3(0, -.3, 0); }
    protected Vec3 pitchOffset() { return Vec3.ZERO; }
    protected Vec3 originOffset(float yaw, float pitch, double length) { return Vec3.directionFromRotation(pitch, yaw).scale(length); }
    public boolean isBodyPart() { return false; }
    public boolean bound() { return entityData.get(BOUND); }
    public void setBound(boolean bound) { entityData.set(BOUND, bound); }
    public boolean retracting() { return entityData.get(RETRACTING); }
    public void retract() { entityData.set(RETRACTING, true); }
    public boolean movingForward() { return entityData.get(FORWARD); }
    public float distance() { return entityData.get(DISTANCE); }
    public void setLifeSpan(int ticks) { entityData.set(LIFE, Math.clamp(ticks, 1, 12000)); }
    public int ticksLifespan() { return entityData.get(LIFE); }
    public void setSpeedFactor(float value) { if (Float.isFinite(value) && value >= 0) entityData.set(SPEED, Math.min(value, 100)); }
    public void setDamageFactor(float value) { if (Float.isFinite(value) && value >= 0) damageFactor = Math.min(value, 100); }
    protected abstract float movementSpeed();
    protected float retractSpeed() { return movementSpeed(); }
    protected int timeAtFullLength() { return 0; }
    protected abstract float baseDamage();
    protected abstract float maxHardness();
    protected boolean hurtThroughImmunity() { return false; }
    public float knockbackMultiplier() { return 1; }
    @Override public boolean standDamage() { return false; }
    @Override public boolean canBeEvaded(LivingEntity target) { return true; }
    public boolean canBeDeflected(Entity context) { return true; }
    public void setDeflected() { deflected = true; }
    public boolean attachedToEntity() { return entityData.get(ATTACHED) >= 0 || attachedUuid != null; }
    public LivingEntity attachedEntity() {
        var target = level().getEntity(entityData.get(ATTACHED));
        return target instanceof LivingEntity living ? living : null;
    }
    public void attachToEntity(LivingEntity target) {
        if (target.level() != level()) return;
        attachedUuid = target.getUUID(); entityData.set(ATTACHED, target.getId()); entityData.set(BLOCK, Optional.empty());
    }
    public void attachToBlock(BlockPos pos) { attachedUuid = null; entityData.set(ATTACHED, -1); entityData.set(BLOCK, Optional.of(pos.immutable())); }
    public void detachTarget() { attachedUuid = null; entityData.set(ATTACHED, -1); entityData.set(BLOCK, Optional.empty()); }
    protected double attachedTargetHeight() { return .5; }
    public AABB segmentBounds() { return getBoundingBox().expandTowards(bodyOrigin(1).subtract(position())); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (accessor.equals(AGE) && level().isClientSide()) tickCount = entityData.get(AGE);
        super.onSyncedDataUpdated(accessor);
    }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        var owner = livingOwner();
        if (!level().isClientSide() && (tickCount > ticksLifespan() || bound() && (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level()))) { discard(); return; }
        if (bound() && owner == null) return;
        if (!level().isClientSide() && owner != null && entityData.get(OWNER) != owner.getId()) entityData.set(OWNER, owner.getId());
        if (attachedUuid != null && attachedEntity() == null && level() instanceof ServerLevel server) {
            var target = server.getEntity(attachedUuid);
            if (target instanceof LivingEntity living && living.isAlive()) attachToEntity(living);
            else { discard(); return; }
        }
        dragged.forEach(entity -> entity.setDeltaMovement(Vec3.ZERO)); dragged.clear();
        super.tick();
        if (!level().isClientSide()) entityData.set(AGE, tickCount);
        if (level() instanceof ServerLevel server) hitSegment(server);
        Vec3 old = position();
        var target = attachedEntity();
        if (target != null) {
            if (!target.isAlive() || target.isRemoved() || target.level() != level()) { if (!level().isClientSide()) discard(); return; }
            setPos(target.getX(), target.getY(attachedTargetHeight()), target.getZ()); setRot(target.getYRot(), target.getXRot());
        } else if (entityData.get(BLOCK).isPresent()) setPos(Vec3.atCenterOf(entityData.get(BLOCK).get()));
        else if (bound()) {
            float speed = movementSpeed(), retract = retractSpeed();
            float next = distance() + (retracting() ? -retract : movingForward() ? speed : 0) * entityData.get(SPEED);
            updateMotionFlags(speed, retract);
            if (retracting() && next <= 0) { if (!level().isClientSide()) discard(); return; }
            entityData.set(DISTANCE, next); setRot(owner.getYRot(), owner.getXRot());
            Vec3 nextPosition = origin(1).add(originOffset(owner.getYRot(), owner.getXRot(), next));
            if (!level().hasChunkAt(BlockPos.containing(nextPosition))) { if (!level().isClientSide()) discard(); return; }
            setPos(nextPosition);
        } else setPos(old.add(getDeltaMovement()));
        if (bound() || target != null || entityData.get(BLOCK).isPresent()) setDeltaMovement(position().subtract(old));
        needsSync = true;
    }
    /** Source subclasses such as HG grapple own their stop/retraction schedule. */
    protected void updateMotionFlags(float speed, float retract) {
        int stopAt = (int) (speed * retract * (ticksLifespan() - timeAtFullLength()) / (speed + retract) / speed);
        if (movingForward() && tickCount >= stopAt) entityData.set(FORWARD, false);
        if (!retracting() && tickCount >= stopAt + timeAtFullLength()) retract();
    }
    public java.util.Optional<BlockPos> attachedBlock() { return entityData.get(BLOCK); }
    protected boolean eligibleTarget(Entity target) {
        if (!target.isAlive() || target.isSpectator() || !target.isPickable()) return false;
        var owner = livingOwner(); if (owner == null) return true;
        if (target == owner) return deflected;
        if (target instanceof StandManifestation stand && stand.owner() == owner) return deflected;
        if (owner instanceof StandManifestation stand && target == stand.owner()) return deflected;
        if (target instanceof Projectile projectile && projectile.getOwner() != null && projectile.getOwner().getUUID().equals(owner.getUUID())) return false;
        if (owner instanceof Player player && target instanceof Player other && !player.canHarmPlayer(other)) return false;
        return !(target instanceof LivingEntity living) || owner.canAttack(living);
    }
    protected void hitSegment(ServerLevel server) {
        Vec3 start = origin(1), end = position().add(getDeltaMovement());
        record SegmentHit(Entity entity, double distance) { }
        var targets = new ArrayList<SegmentHit>();
        for (var target : server.getEntities(this, new AABB(start, end).inflate(1), this::eligibleTarget)) {
            var bounds = target.getBoundingBox().inflate(target.getPickRadius() + getBbWidth() / 2);
            var clip = bounds.clip(start, end);
            if (bounds.contains(start) || clip.isPresent()) targets.add(new SegmentHit(target, bounds.contains(start) ? 0 : start.distanceToSqr(clip.orElseThrow())));
        }
        targets.sort(Comparator.comparingDouble(SegmentHit::distance));
        // Source tests blocks only when the entire segment misses entities, including entities beyond terrain.
        for (var hit : targets) afterEntityHit(hit.entity(), hurtTarget(server, hit.entity()));
        if (!targets.isEmpty()) return;
        var hit = server.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) afterBlockHit(hit, destroyBlock(server, hit.getBlockPos()));
    }
    protected DamageSource damageSource(ServerLevel server) { return new DamageSource(server.registryAccess().getOrThrow(DamageTypes.ARROW), this, livingOwner()); }
    protected boolean hurtTarget(ServerLevel server, Entity target) {
        var source = damageSource(server); Vec3 velocity = target.getDeltaMovement();
        float amount=baseDamage()*damageFactor*(standDamage()||livingOwner() instanceof StandManifestation?StandConfig.server().standPowerMultiplier():1);
        int previousFire=target.getRemainingFireTicks();
        if(isOnFire())target.igniteForSeconds(5);
        boolean result;
        try {
            result = target instanceof LivingEntity living && hurtThroughImmunity()
                    ? StandDamage.hurt(server, living, source, amount)
                    : target.hurtServer(server, source, amount);
        } finally { if (knockbackMultiplier() == 0) target.setDeltaMovement(velocity); }
        if(!result&&isOnFire())target.setRemainingFireTicks(previousFire);
        return result;
    }
    protected void afterEntityHit(Entity target, boolean hurt) { }
    protected void afterBlockHit(net.minecraft.world.phys.BlockHitResult hit, boolean destroyed) { afterBlockHit(hit.getBlockPos(), destroyed); }
    protected void afterBlockHit(BlockPos pos, boolean destroyed) { if (!destroyed) retract(); }
    protected boolean destroyBlock(ServerLevel server, BlockPos pos) {
        var block = server.getBlockState(pos); float hardness = block.getDestroySpeed(server, pos); var owner = livingOwner();
        if (!server.getGameRules().get(JojoGameRules.BREAK_BLOCKS) || hardness < 0 || hardness > maxHardness()) return false;
        LivingEntity user = owner instanceof StandManifestation stand ? stand.owner() : owner;
        if (user instanceof Player player && (!player.mayBuild() || !server.mayInteract(player, pos)
                || !net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(server, player, pos, block, server.getBlockEntity(pos)))) return false;
        boolean drops=!(user instanceof Player player)||!player.isCreative();
        if(!drops)com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks.remember(server,pos,block,server.getBlockEntity(pos),java.util.List.of());
        return server.destroyBlock(pos,drops,owner);
    }
    protected void dragTarget(Entity target, Vec3 delta) {
        target = target.getRootVehicle();
        if (target instanceof LivingEntity living && VampireCombatService.isStunned(living)) target.move(net.minecraft.world.entity.MoverType.PLAYER, delta);
        else { target.setDeltaMovement(delta); dragged.add(target); }
    }
    @Override public void remove(RemovalReason reason) { dragged.forEach(entity -> entity.setDeltaMovement(Vec3.ZERO)); dragged.clear(); super.remove(reason); }
    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 1; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source)) return false;
        syncVelocity = true; return true; // Source breakProjectile is deliberately empty for attached body parts.
    }
    @Override public net.minecraft.world.scores.PlayerTeam getTeam() { var owner = livingOwner(); return owner == null ? super.getTeam() : owner.getTeam(); }
    @Override public net.minecraft.sounds.SoundSource getSoundSource() { var owner = livingOwner(); return bound() && owner != null ? owner.getSoundSource() : super.getSoundSource(); }
    @Override public boolean isSilent() { var owner = livingOwner(); return bound() && owner != null ? owner.isSilent() : super.isSilent(); }
    @Override public boolean isInvisible() { var owner = livingOwner(); return isBodyPart() && owner != null && owner.isInvisible() || super.isInvisible(); }
    @Override public boolean isInvisibleTo(Player player) { var owner = livingOwner(); return isBodyPart() && owner != null && owner.isInvisibleTo(player) || super.isInvisibleTo(player); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putBoolean("BoundToOwner", bound()); output.putBoolean("IsMovingForward", movingForward()); output.putBoolean("IsRetracting", retracting());
        output.putFloat("Distance", distance()); output.putInt("LifeSpan", entityData.get(LIFE)); output.putInt("Age", tickCount);
        output.putFloat("SpeedFactor", entityData.get(SPEED)); output.putFloat("DamageFactor", damageFactor); output.putBoolean("IsDeflected", deflected);
        if (attachedUuid != null) output.putString("AttachedEntity", attachedUuid.toString());
        entityData.get(BLOCK).ifPresent(pos -> { output.putInt("AttachedBlockX", pos.getX()); output.putInt("AttachedBlockY", pos.getY()); output.putInt("AttachedBlockZ", pos.getZ()); output.putBoolean("HasAttachedBlock", true); });
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); setBound(input.getBooleanOr("BoundToOwner", true)); entityData.set(FORWARD, input.getBooleanOr("IsMovingForward", true)); entityData.set(RETRACTING, input.getBooleanOr("IsRetracting", false));
        float distance = input.getFloatOr("Distance", 0); entityData.set(DISTANCE, Float.isFinite(distance) ? Math.clamp(distance, 0, 4096) : 0);
        setLifeSpan(input.getIntOr("LifeSpan", 40)); tickCount = Math.clamp(input.getIntOr("Age", 0), 0, 12001);
        entityData.set(AGE, tickCount);
        setSpeedFactor(input.getFloatOr("SpeedFactor", 1)); setDamageFactor(input.getFloatOr("DamageFactor", 1)); deflected = input.getBooleanOr("IsDeflected", false);
        String attached = input.getStringOr("AttachedEntity", "");
        try { attachedUuid = attached.isEmpty() ? null : UUID.fromString(attached); } catch (IllegalArgumentException invalid) { attachedUuid = null; }
        if (input.getBooleanOr("HasAttachedBlock", false)) attachToBlock(new BlockPos(input.getIntOr("AttachedBlockX", 0), input.getIntOr("AttachedBlockY", 0), input.getIntOr("AttachedBlockZ", 0)));
    }
}
