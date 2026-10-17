package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Original delayed-position afterimages, including server mob distraction and source transient origin handling. */
public final class HamonAfterimageEntity extends Entity {
    private static final AttachmentType<List<HamonAfterimageEntity>> IMAGES = AttachmentRegistry.<List<HamonAfterimageEntity>>builder()
            .initializer(ArrayList::new).buildAndRegister(RipplesFabric.id("afterimages"));
    private static final EntityDataAccessor<Integer> ORIGIN = SynchedEntityData.defineId(HamonAfterimageEntity.class, EntityDataSerializers.INT),
            DELAY = SynchedEntityData.defineId(HamonAfterimageEntity.class, EntityDataSerializers.INT), LIFE = SynchedEntityData.defineId(HamonAfterimageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SPEED = SynchedEntityData.defineId(HamonAfterimageEntity.class, EntityDataSerializers.LONG);
    private LivingEntity origin;
    private UUID savedOrigin;
    private int delayedTicks;
    private final ArrayDeque<Position> history = new ArrayDeque<>();
    private record Position(Vec3 position, float pitch, float yaw) { }
    public HamonAfterimageEntity(EntityType<? extends HamonAfterimageEntity> type, Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    public HamonAfterimageEntity(LivingEntity origin, int delay) {
        this(HamonEntities.AFTERIMAGE, origin.level()); this.origin = origin; entityData.set(ORIGIN, origin.getId()); entityData.set(DELAY, delay);
        setPos(origin.position()); setRot(origin.getYRot(), origin.getXRot());
    }
    public HamonAfterimageEntity(LivingEntity origin,int delay,int lifespan,double speed){
        this(origin,delay);setLifeSpan(lifespan<0?Integer.MAX_VALUE:lifespan);entityData.set(SPEED,Double.doubleToLongBits(speed));
    }
    public LivingEntity owner(){return origin();}
    public boolean shouldRender(){return visibleAtCurrentSpeed();}
    public void extendLife(int duration){setLifeSpan(duration<0?Integer.MAX_VALUE:tickCount+duration);}
    public static void initialize() { }
    public static void addAfterimages(LivingEntity entity, int count, int lifespan) {
        if (entity.level().isClientSide()) return;
        var images = entity.getAttachedOrCreate(IMAGES); images.removeIf(Entity::isRemoved); int index = 0;
        for (var image : images) { image.setLifeSpan(lifespan < 0 ? Integer.MAX_VALUE : image.tickCount + lifespan); index++; }
        double base = entity.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), speed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        for (; index < count; index++) {
            var image = new HamonAfterimageEntity(entity, index + 1); image.setLifeSpan(lifespan < 0 ? Integer.MAX_VALUE : lifespan);
            image.entityData.set(SPEED, Double.doubleToLongBits(base + (speed - base) * (index + 1) / count));
            images.add(image); entity.level().addFreshEntity(image);
        }
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(ORIGIN, -1); builder.define(DELAY, 0); builder.define(LIFE, 1200); builder.define(SPEED, 0L); }
    public LivingEntity origin() {
        if (origin == null && level().getEntity(entityData.get(ORIGIN)) instanceof LivingEntity living) origin = living;
        return origin;
    }
    public int delay() { return entityData.get(DELAY); }
    public double speedThreshold() { return Double.longBitsToDouble(entityData.get(SPEED)); }
    public boolean visibleAtCurrentSpeed() { return origin() != null && origin().getAttributeValue(Attributes.MOVEMENT_SPEED) >= speedThreshold(); }
    public void setLifeSpan(int ticks) { entityData.set(LIFE, ticks); }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        super.tick(); delayedTicks++;
        var entity = origin();
        if (entity == null || !entity.isAlive() || !level().isClientSide() && tickCount > entityData.get(LIFE)) { discard(); return; }
        history.addLast(new Position(entity.position(), entity.getXRot(), entity.getYRot()));
        if (delayedTicks > delay()) { var position = history.removeFirst(); setPos(position.position()); setRot(position.yaw(), position.pitch()); }
        if (level() instanceof ServerLevel server && entity.isSprinting() && visibleAtCurrentSpeed()) {
            for (var mob : server.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(8), mob -> mob.getTarget() == entity && mob.hasLineOfSight(this)))
                if (mob.getRandom().nextDouble() < .01) {
                    mob.setTarget(null);
                    ((com.github.standobyte.jojo.fabric.mixin.TimeSkipMobAccessor) mob).jojo$targetSelector().getAvailableGoals().stream()
                            .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
                }
        }
    }
    @Override public boolean isInvisible() { return super.isInvisible() || origin() != null && origin().isInvisible(); }
    @Override public boolean isInvisibleTo(Player player) { return super.isInvisibleTo(player) || origin() != null && origin().isInvisibleTo(player); }
    @Override public boolean displayFireAnimation() { return origin() != null && origin().displayFireAnimation(); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Delay", delay()); output.putInt("Age", tickCount); output.putInt("LifeSpan", entityData.get(LIFE)); output.putDouble("Speed", speedThreshold());
        // Original fresh afterimages leave originUuid unset, so they do not write an Origin on save.
        if (savedOrigin != null && origin() != null) output.putString("Origin", origin().getUUID().toString());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(DELAY, Math.clamp(input.getIntOr("Delay", 0), 0, 1200)); tickCount = Math.max(0, input.getIntOr("Age", 0)); entityData.set(LIFE, input.getIntOr("LifeSpan", input.getIntOr("Life", 0)));
        double speed = input.getDoubleOr("Speed", input.getFloatOr("MinSpeed", 0)); entityData.set(SPEED, Double.doubleToLongBits(Double.isFinite(speed) ? speed : 0));
        try { savedOrigin = UUID.fromString(input.getStringOr("Origin", input.getStringOr("Owner", ""))); } catch (IllegalArgumentException missing) { savedOrigin = null; }
        if (savedOrigin != null && level() instanceof ServerLevel server && server.getEntity(savedOrigin) instanceof LivingEntity living) {
            origin = living; entityData.set(ORIGIN, living.getId()); setPos(living.position()); setRot(living.getYRot(), living.getXRot());
        }
    }
}
