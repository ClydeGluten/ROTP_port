package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.VampireCombatService;
import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public final class PillarRibsEntity extends OwnedBodyPartEntity {
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(PillarRibsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(PillarRibsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_X = SynchedEntityData.defineId(PillarRibsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Y = SynchedEntityData.defineId(PillarRibsEntity.class, EntityDataSerializers.FLOAT);
    private boolean dealtDamage;
    public PillarRibsEntity(EntityType<? extends PillarRibsEntity> type, Level level) { super(type, level); }
    public PillarRibsEntity(LivingEntity owner, float pitch, float yaw, double x, double y) {
        this(PillarBodyParts.RIBS, owner.level());
        entityData.set(PITCH, pitch); entityData.set(YAW, yaw); entityData.set(OFFSET_X, (float) x); entityData.set(OFFSET_Y, (float) y);
        initializeOwner(owner, 21);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(YAW, 0F); builder.define(PITCH, 0F); builder.define(OFFSET_X, 0F); builder.define(OFFSET_Y, 0F);
    }
    @Override protected float baseDamage() { return .5F; }
    @Override protected boolean hurtThroughImmunity() { return true; }
    @Override public float knockbackMultiplier() { return 0; }
    @Override protected float maxHardness() { return 0; }
    @Override protected float movementSpeed() { return 8F / ticksLifespan(); }
    @Override public int ticksLifespan() { return super.ticksLifespan() + (attachedToEntity() ? 20 : 0); }
    @Override public boolean isBodyPart() { return true; }
    @Override protected Vec3 ownerRelativeOffset() { return new Vec3(entityData.get(OFFSET_X), entityData.get(OFFSET_Y), 0); }
    @Override protected Vec3 originOffset(float yaw, float pitch, double distance) { return super.originOffset(yaw + entityData.get(YAW), pitch + entityData.get(PITCH), distance); }
    @Override protected boolean hurtTarget(ServerLevel server, Entity target) { return !dealtDamage && super.hurtTarget(server, target); }
    @Override protected void afterEntityHit(Entity target, boolean hurt) {
        if (!hurt) return;
        dealtDamage = true;
        if (target instanceof LivingEntity living && !living.isBlocking()
                && living.getUseItem().get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS) == null
                && !(living instanceof com.github.standobyte.jojo.fabric.StandManifestation stand
                    && stand.actionPhase() == com.github.standobyte.jojo.fabric.StarPlatinumEntity.PHASE_GUARD)) {
            attachToEntity(living);
            living.addEffect(new MobEffectInstance(VampireCombatService.STUN, Math.max(1, ticksLifespan() - tickCount)));
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putFloat("YRotOffset", entityData.get(YAW)); output.putFloat("XRotOffset", entityData.get(PITCH));
        output.putFloat("XOriginOffset", entityData.get(OFFSET_X)); output.putFloat("YOriginOffset", entityData.get(OFFSET_Y)); output.putBoolean("DealtDamage", dealtDamage);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); entityData.set(YAW, finite(input.getFloatOr("YRotOffset", 0), 360)); entityData.set(PITCH, finite(input.getFloatOr("XRotOffset", 0), 360));
        entityData.set(OFFSET_X, finite(input.getFloatOr("XOriginOffset", 0), 4)); entityData.set(OFFSET_Y, finite(input.getFloatOr("YOriginOffset", 0), 4));
        dealtDamage = input.getBooleanOr("DealtDamage", attachedToEntity());
    }
    private static float finite(float value, float bound) { return Float.isFinite(value) ? Math.clamp(value, -bound, bound) : 0; }
}
