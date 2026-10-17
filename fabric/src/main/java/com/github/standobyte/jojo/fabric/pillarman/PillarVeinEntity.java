package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Literal PillarmanVeinEntity parameters and hit/retraction rules over the original segment adapter. */
public final class PillarVeinEntity extends OwnedBodyPartEntity {
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(PillarVeinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(PillarVeinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> X = SynchedEntityData.defineId(PillarVeinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> Y = SynchedEntityData.defineId(PillarVeinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> Z = SynchedEntityData.defineId(PillarVeinEntity.class, EntityDataSerializers.FLOAT);
    private float knockback;
    public PillarVeinEntity(EntityType<? extends PillarVeinEntity> type, Level level) { super(type, level); }
    public PillarVeinEntity(LivingEntity owner, float pitch, float yaw, double x, double y, double z) {
        this(PillarHeatActions.VEINS, owner.level());
        entityData.set(PITCH, pitch); entityData.set(YAW, yaw); entityData.set(X, (float)x); entityData.set(Y, (float)y); entityData.set(Z, (float)z);
        initializeOwner(owner, 25);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(PITCH, 0F); builder.define(YAW, 0F);
        builder.define(X, 0F); builder.define(Y, 0F); builder.define(Z, 0F);
    }
    @Override protected float baseDamage() { return .35F; }
    @Override protected boolean hurtThroughImmunity() { return true; }
    @Override public float knockbackMultiplier() { return 0; }
    @Override protected float maxHardness() { return 0; }
    @Override protected float movementSpeed() { return 16F / ticksLifespan(); }
    @Override public boolean isBodyPart() { return true; }
    @Override protected Vec3 ownerRelativeOffset() { return new Vec3(entityData.get(X), entityData.get(Y), entityData.get(Z)); }
    @Override protected Vec3 originOffset(float yaw, float pitch, double length) { return super.originOffset(yaw + entityData.get(YAW), pitch + entityData.get(PITCH), length); }
    public void addKnockback(float value) { if (Float.isFinite(value) && value >= 0) knockback = value; }
    @Override protected boolean hurtTarget(ServerLevel server, Entity target) {
        if (retracting()) return false;
        int oldFire = target.getRemainingFireTicks(); target.igniteForSeconds(10);
        boolean hurt = super.hurtTarget(server, target);
        if (!hurt) target.setRemainingFireTicks(oldFire);
        return hurt;
    }
    @Override protected void afterEntityHit(Entity target, boolean hurt) {
        if (hurt) {
            if (knockback > 0 && target instanceof LivingEntity living) {
                double yaw = Math.toRadians(getYRot());
                living.knockback(knockback, Math.sin(yaw), -Math.cos(yaw), living.damageSources().generic(), baseDamage());
            }
            retract();
        }
    }
    @Override protected void afterBlockHit(BlockHitResult hit, boolean destroyed) {
        if (!(level() instanceof ServerLevel server) || !PillarHeatExplosion.canGrief(server)) return;
        var pos = hit.getBlockPos(); var state = server.getBlockState(pos);
        if (!PillarHeatExplosion.meltIceAndSnow(server, pos) && !state.getCollisionShape(server, pos).isEmpty()) {
            pos = pos.relative(hit.getDirection());
            if (server.isEmptyBlock(pos) && !retracting()) {
                server.setBlockAndUpdate(pos, PillarWorldContent.BOILING_BLOOD_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, 4));
                server.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH,
                        net.minecraft.sounds.SoundSource.AMBIENT, .2F, 1);
            }
        }
        // Original override intentionally does not call superclass retraction on solid block hits.
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out); out.putFloat("XRotOffset", entityData.get(PITCH)); out.putFloat("YRotOffset", entityData.get(YAW));
        out.putFloat("XOriginOffset", entityData.get(X)); out.putFloat("YOriginOffset", entityData.get(Y)); out.putFloat("ZOriginOffset", entityData.get(Z)); out.putFloat("Knockback", knockback);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in); entityData.set(PITCH, finite(in.getFloatOr("XRotOffset", 0))); entityData.set(YAW, finite(in.getFloatOr("YRotOffset", 0)));
        entityData.set(X, finite(in.getFloatOr("XOriginOffset", 0))); entityData.set(Y, finite(in.getFloatOr("YOriginOffset", 0))); entityData.set(Z, finite(in.getFloatOr("ZOriginOffset", 0)));
        addKnockback(finite(in.getFloatOr("Knockback", 0)));
    }
    private static float finite(float value) { return Float.isFinite(value) ? Math.clamp(value, -4096, 4096) : 0; }
}
