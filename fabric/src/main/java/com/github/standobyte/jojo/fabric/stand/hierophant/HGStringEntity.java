package com.github.standobyte.jojo.fabric.stand.hierophant;

import com.github.standobyte.jojo.fabric.HamonEffects;
import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Eight attacking or four binding body strings; collision and retraction use the source owner-bound base. */
public final class HGStringEntity extends OwnedBodyPartEntity {
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(HGStringEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(HGStringEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BINDING = SynchedEntityData.defineId(HGStringEntity.class, EntityDataSerializers.BOOLEAN);
    private boolean dealtDamage;
    private float counterKnockback;

    public HGStringEntity(EntityType<? extends HGStringEntity> type, Level level) { super(type, level); }
    public HGStringEntity(HierophantGreenEntity owner, float yaw, float pitch, boolean binding, int lifespan) {
        this(HierophantGreenActions.STRING, owner.level());
        entityData.set(YAW, yaw); entityData.set(PITCH, pitch); entityData.set(BINDING, binding);
        initializeOwner(owner, lifespan);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(YAW, 0F); builder.define(PITCH, 0F); builder.define(BINDING, false);
    }
    public boolean isBinding() { return entityData.get(BINDING); }
    public void addKnockback(float value) { counterKnockback = Math.max(0, value); }
    @Override public boolean standDamage() { return true; }
    @Override protected float baseDamage() { return isBinding() ? .5F : 1F; }
    @Override protected boolean hurtThroughImmunity() { return true; }
    @Override public float knockbackMultiplier() { return 0; }
    @Override protected float maxHardness() { return 0; }
    @Override public int ticksLifespan() { return super.ticksLifespan() + (isBinding() && attachedToEntity() ? 10 : 0); }
    @Override protected float movementSpeed() { return 16F / ticksLifespan(); }
    @Override public boolean isBodyPart() { return true; }
    @Override protected Vec3 pitchOffset() { return new Vec3(0, 0, .25); }
    @Override protected Vec3 originOffset(float yaw, float pitch, double length) {
        return super.originOffset(yaw + entityData.get(YAW), pitch + entityData.get(PITCH), length);
    }
    @Override protected boolean hurtTarget(ServerLevel server, Entity target) {
        return !dealtDamage && super.hurtTarget(server, target);
    }
    @Override protected DamageSource damageSource(ServerLevel server) {
        return new DamageSource(server.registryAccess().getOrThrow(DamageTypes.ARROW), this,
                livingOwner() instanceof HierophantGreenEntity stand ? stand.owner() : livingOwner());
    }
    @Override protected boolean eligibleTarget(Entity target) {
        return super.eligibleTarget(target) && (!(livingOwner() instanceof HierophantGreenEntity stand) || stand.canAttackTarget(target));
    }
    @Override protected void afterEntityHit(Entity target, boolean hurt) {
        if (!hurt) return;
        dealtDamage = true;
        if (target instanceof LivingEntity living && livingOwner() instanceof HierophantGreenEntity stand && stand.owner() != null) {
            living.setLastHurtByMob(stand.owner()); living.setLastHurtByPlayer(stand.owner(), 100);
        }
        if (isBinding()) {
            if (target instanceof LivingEntity living && !HierophantGreenActions.isBlocking(living)) {
                attachToEntity(living);
                living.addEffect(new MobEffectInstance(HamonEffects.IMMOBILIZE, Math.max(1, ticksLifespan() - tickCount)));
            }
        } else {
            if (counterKnockback > 0 && target instanceof LivingEntity living) {
                Vec3 direction = Vec3.directionFromRotation(0, getYRot());
                living.knockback(counterKnockback, -direction.x, -direction.z, damageSource((ServerLevel) level()), baseDamage());
            }
            retract();
        }
    }
}
