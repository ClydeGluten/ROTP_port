package com.github.standobyte.jojo.fabric.stand.hierophant;

import com.github.standobyte.jojo.fabric.StandActionRegistry;
import com.github.standobyte.jojo.fabric.TimeStopService;
import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Held block grapple or shifted entity pull; these strings intentionally do not automatically retract halfway. */
public final class HGGrapplingStringEntity extends OwnedBodyPartEntity {
    private static final EntityDataAccessor<Boolean> BINDING = SynchedEntityData.defineId(HGGrapplingStringEntity.class, EntityDataSerializers.BOOLEAN);
    private boolean placedBarrier;
    public HGGrapplingStringEntity(EntityType<? extends HGGrapplingStringEntity> type, Level level) { super(type, level); }
    public HGGrapplingStringEntity(HierophantGreenEntity owner, boolean binding) {
        this(HierophantGreenActions.GRAPPLING_STRING, owner.level()); entityData.set(BINDING, binding); initializeOwner(owner, 40);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(BINDING, false);
    }
    public boolean bindsEntities() { return entityData.get(BINDING); }
    @Override public int ticksLifespan() { return attachedToEntity() || attachedBlock().isPresent() ? Integer.MAX_VALUE : 40; }
    @Override protected float movementSpeed() { return 4; }
    @Override protected void updateMotionFlags(float speed, float retract) { }
    @Override protected float baseDamage() { return 0; }
    @Override protected float maxHardness() { return 0; }
    @Override public boolean standDamage() { return true; }
    @Override public boolean isBodyPart() { return true; }
    @Override protected Vec3 ownerRelativeOffset() { return new Vec3(-.3, -.2, .55); }
    @Override protected boolean eligibleTarget(Entity target) {
        var owner = livingOwner();
        if (!(target instanceof LivingEntity) || target == owner || !target.isAlive() || target.isSpectator()) return false;
        return !(owner instanceof HierophantGreenEntity stand) || target != stand.user() || !stand.followingUser();
    }
    @Override protected boolean hurtTarget(ServerLevel server, Entity target) {
        if (attachedEntity() == null && bindsEntities() && target instanceof LivingEntity living
                && !HierophantGreenActions.isBlocking(living)) {
            attachToEntity(living); sound(); return true;
        }
        return false;
    }
    @Override protected void afterBlockHit(BlockHitResult hit, boolean destroyed) {
        if (level().getBlockState(hit.getBlockPos()).is(Blocks.BARRIER)) { discard(); return; }
        if (!destroyed && !bindsEntities()) {
            if (attachedBlock().isEmpty()) { attachToBlock(hit.getBlockPos()); sound(); }
            if (!placedBarrier && livingOwner() instanceof HierophantGreenEntity stand) {
                if (stand.hasBarrierAttached()) stand.attachBarrier(hit.getBlockPos());
                placedBarrier = true;
            }
        }
    }
    private void sound() {
        playSound(com.github.standobyte.jojo.fabric.JojoSounds.get("hierophant_green_grapple_catch"), 1, 1);
    }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this) || livingOwner() != null && TimeStopService.isFrozen(livingOwner())) return;
        super.tick(); if (isRemoved()) return;
        var stand = livingOwner() instanceof HierophantGreenEntity hg ? hg : null;
        if (!level().isClientSide() && (stand == null || stand.owner() == null
                || StandActionRegistry.held(stand.owner()).filter(id -> id.equals(bindsEntities()
                ? HierophantGreenActions.GRAPPLE_ENTITY : HierophantGreenActions.GRAPPLE)).isEmpty())) { discard(); return; }
        LivingEntity target = attachedEntity();
        if (target != null && stand != null) {
            Vec3 toward = stand.position().subtract(target.position());
            if (!target.isAlive() || toward.lengthSqr() < 4) { if (!level().isClientSide()) discard(); return; }
            dragTarget(target, toward.normalize()); target.fallDistance = 0;
        } else if (attachedBlock().isPresent() && stand != null) {
            Vec3 toward = position().subtract(stand.position());
            if (toward.lengthSqr() <= 4) { if (!level().isClientSide()) discard(); return; }
            Entity pulled = stand.followingUser() && stand.user() != null ? stand.user() : stand;
            pulled = pulled.getRootVehicle(); pulled.setDeltaMovement(toward.normalize().scale(2)); pulled.fallDistance = 0;
            pulled.syncVelocity = true;
        }
    }
}
