package com.github.standobyte.jojo.fabric.stand.goldexperience;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.TimeStopService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persisted original GE lifeform morph. It owns the one physical material until completion. */
public final class GETransformationEntity extends Entity {
    private static final EntityDataAccessor<Boolean> TURN_BACK = SynchedEntityData.defineId(GETransformationEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FINISHED = SynchedEntityData.defineId(GETransformationEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> HOST = SynchedEntityData.defineId(GETransformationEntity.class, EntityDataSerializers.INT);
    private GETransformationData source = new GETransformationData();
    private Entity target;
    private UUID ownerId;
    private UUID hostId;
    private Vec3 hostOffset;
    private int duration = 1;
    private float renderAsItemTime = 1F / 3;
    private boolean transferred;
    private boolean announced;
    public int actionCooldown;

    public GETransformationEntity(EntityType<? extends GETransformationEntity> type, Level level) { super(type, level); }
    public GETransformationEntity(Level level) { this(GoldExperienceTransformation.TYPE, level); }
    public GETransformationEntity configure(GETransformationData material, Entity lifeform, LivingEntity owner, int ticks) {
        source = material; target = lifeform; ownerId = owner == null ? null : owner.getUUID(); duration = Math.max(1, ticks);
        renderAsItemTime = Math.min(duration / 3F, 20); return this;
    }
    public GETransformationData source() { return source; }
    public Entity target() { return target; }
    public int duration() { return duration; }
    public float renderAsItemTime() { return renderAsItemTime; }
    public UUID ownerUuid() { return ownerId; }
    Optional<Vec3> hostOffset() { return Optional.ofNullable(hostOffset); }
    public boolean turningBack() { return entityData.get(TURN_BACK); }
    public LivingEntity owner() { return ownerId != null && level().getEntity(ownerId) instanceof LivingEntity living ? living : null; }
    public float progressTime(float partialTick) { float age = Math.min(tickCount + partialTick, duration); return turningBack() ? duration - age : age; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(TURN_BACK, false); builder.define(FINISHED, false); builder.define(HOST, -1); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean isInvisible() { return super.isInvisible() || entityData.get(FINISHED); }
    @Override public boolean isPickable() { return super.isPickable() && (hostId == null || getVehicle() == null || !hostId.equals(getVehicle().getUUID())); }

    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        if (!level().isClientSide() && !announced) { announced = true; GoldExperienceMorphPayload.sync(this); }
        if (tickCount >= duration) {
            if (!level().isClientSide()) finish();
            return;
        }
        if (turningBack() && source.entity(level()) == null && source.block() != null) {
            int left = duration - tickCount;
            if (left - 1 <= renderAsItemTime && left > renderAsItemTime) snapTo(Vec3.atBottomCenterOf(blockPosition()));
        }
        double fluidThreshold = getEyeHeight() - .11111111;
        Vec3 motion = getDeltaMovement();
        if (isInWater() && getFluidHeight(FluidTags.WATER) > fluidThreshold) setDeltaMovement(motion.multiply(.99, 1, .99).add(0, motion.y < .06 ? .0005 : 0, 0));
        else if (isInLava() && getFluidHeight(FluidTags.LAVA) > fluidThreshold) setDeltaMovement(motion.multiply(.95, 1, .95).add(0, motion.y < .06 ? .0005 : 0, 0));
        else if (!isNoGravity()) setDeltaMovement(motion.add(0, -.04, 0));
        motion = getDeltaMovement();
        if (!onGround() || motion.horizontalDistanceSqr() > .00001 || (tickCount + getId()) % 4 == 0) {
            move(MoverType.SELF, motion);
            double friction = onGround() ? level().getBlockState(BlockPos.containing(getX(), getY() - 1, getZ())).getBlock().getFriction() * .98 : .98;
            motion = motion.multiply(friction, .98, friction);
            if (onGround() && motion.y < 0) motion = motion.multiply(1, -.5, 1);
            setDeltaMovement(motion);
        }
        if (!level().isClientSide() && !turningBack() && host() instanceof LivingEntity host && host.isAlive()) {
            if (getVehicle() != host) withHost(null);
            else if (tickCount > 40) { bleedHost(); withHost(null); }
            else if (tickCount > 0 && tickCount % 10 == 9) {
                var key = ResourceKey.create(Registries.DAMAGE_TYPE, RipplesFabric.id("arrow_lifeform"));
                com.github.standobyte.jojo.fabric.StandDamage.hurt((ServerLevel) level(), host,
                        new DamageSource(level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key)), 2);
            }
        }
        refreshDimensions(); super.tick();
    }
    private void finish() {
        if (transferred || !(level() instanceof ServerLevel level)) return;
        transferred = true; entityData.set(FINISHED, true);
        Entity spawn = turningBack() ? source.entity(level) : target;
        var block = turningBack() ? source.block() : null;
        if (block != null) {
            BlockPos pos = blockPosition(); block = Block.updateFromNeighbourShapes(block, level, pos);
            if (block.isAir() || !level.getBlockState(pos).canBeReplaced() || !block.canSurvive(level, pos)) {
                if (!(block.getBlock() instanceof BaseFireBlock)) level.levelEvent(2001, pos, Block.getId(block));
                var blockEntity = source.blockEntity(pos, level);
                Block.dropResources(block, level, pos, blockEntity, owner(), ItemStack.EMPTY);
                if (blockEntity instanceof Container container) Containers.dropContents(level, pos, container);
                block = null;
            }
        }
        if (spawn != null) {
            spawn.snapTo(position(), getYRot(), getXRot());
            if (spawn instanceof Mob mob) mob.setPersistenceRequired();
            if (spawn instanceof ItemEntity item) item.setNoPickUpDelay();
            copyStatus(this, spawn);
            if (level.addFreshEntity(spawn)) {
                if (!turningBack()) {
                    GoldExperienceTransformation.born(this, spawn);
                    if (spawn instanceof Mob mob) mob.playAmbientSound();
                    if (hostId != null) bleedHost();
                }
            } else {
                // A saved source UUID already present must never be duplicated.
                Entity existing = level.getEntity(spawn.getUUID());
                if (!turningBack() && existing != null) GoldExperienceTransformation.born(this, existing);
            }
        }
        else if (block != null) {
            BlockPos pos = blockPosition();
            if (GoldExperienceTransformation.mayRestore(owner(), level, pos, block)) {
                if (isOnFire()) GoldExperienceTransformation.restoredBurningBlock(level, pos, block);
                else {
                    level.setBlock(pos, block, 3);
                    var blockEntity = source.blockEntity(pos, level);
                    if (blockEntity != null) level.setBlockEntity(blockEntity);
                }
            }
        }
        discard();
    }
    public void reverse() {
        if (turningBack() || transferred) return;
        entityData.set(TURN_BACK, true);
        final int reverseTicks = 10;
        if (tickCount < reverseTicks) tickCount = duration - tickCount;
        else {
            float previousItemTime = renderAsItemTime;
            if (tickCount > previousItemTime) {
                renderAsItemTime = reverseTicks / 3F;
                duration = (int) ((duration - previousItemTime) * (reverseTicks - renderAsItemTime) / (tickCount - previousItemTime) + renderAsItemTime);
            } else {
                renderAsItemTime = reverseTicks * previousItemTime / tickCount; duration = Mth.ceil(renderAsItemTime);
            }
            tickCount = duration - reverseTicks;
        }
        GoldExperienceTransformation.turtle().reverseUnspawned(target);
        GoldExperienceMorphPayload.sync(this);
    }
    public static GETransformationEntity turnBack(Entity entity, GETransformationData material, LivingEntity owner) {
        if (entity instanceof GETransformationEntity morph) { morph.reverse(); return morph; }
        if (!(entity.level() instanceof ServerLevel level)) return null;
        var morph = new GETransformationEntity(level).configure(material.copy(level), entity, owner, 10);
        morph.entityData.set(TURN_BACK, true);
        if (morph.source.block() != null) {
            var block = morph.source.block(); Vec3 look = entity.getLookAngle();
            block.getProperties().stream().filter(DirectionProperty.class::isInstance).map(DirectionProperty.class::cast).findFirst().ifPresent(property ->
                    property.getPossibleValues().stream().max(Comparator.comparingDouble(direction -> look.dot(Vec3.atLowerCornerOf(direction.getUnitVec3i()))))
                            .ifPresent(direction -> morph.source.rotatedBlock(block.setValue(property, direction))));
        }
        morph.snapTo(entity.position(), entity.getYRot(), entity.getXRot());
        copyStatus(entity, morph);
        if (!level.addFreshEntity(morph)) return null;
        GoldExperienceTransformation.dropEquipment(entity);
        GoldExperienceTransformation.turtle().beforeRevert(entity);
        entity.discard(); return morph;
    }
    static void copyStatus(Entity from, Entity to) {
        if (from.isOnFire()) to.setRemainingFireTicks(((from.getRemainingFireTicks() + 19) / 20) * 20);
        if (from.isPassenger()) to.startRiding(from.getVehicle());
        for (var passenger : List.copyOf(from.getPassengers())) passenger.startRiding(to);
        if (from.hasCustomName() && !(to instanceof ItemEntity)) to.setCustomName(from.getCustomName());
        to.setDeltaMovement(from.getDeltaMovement());
    }
    public GETransformationEntity withHost(LivingEntity host) {
        if (host != null) {
            if (startRiding(host, true, false)) hostOffset = new Vec3(0, host.getBbHeight() - .5, 0);
        } else if (getVehicle() != null && hostId != null && hostId.equals(getVehicle().getUUID())) stopRiding();
        hostId = host == null ? null : host.getUUID();
        if (!level().isClientSide()) entityData.set(HOST, host == null ? -1 : host.getId());
        return this;
    }
    private Entity host() { return level().isClientSide() ? level().getEntity(entityData.get(HOST)) : hostId == null ? null : level().getEntity(hostId); }
    @Override public void rideTick() {
        super.rideTick();
        if (hostOffset != null && getVehicle() == host()) setPos(getVehicle().position().add(hostOffset));
    }
    private void bleedHost() { if (host() instanceof LivingEntity host && host.isAlive()) GoldExperienceTransformation.bleedHost(host, hostOffset); }
    @Override public void refreshDimensions() { Vec3 position = position(); super.refreshDimensions(); setPos(position); }
    @Override public EntityDimensions getDimensions(Pose pose) {
        if (source == null) return EntityDimensions.scalable(1, 1);
        float time = progressTime(0);
        if (time < renderAsItemTime) {
            float scale = 1 - time / renderAsItemTime;
            Entity material = source.entity(level());
            if (material != null) return material.getDimensions(pose).scale(scale);
            if (source.block() != null) return EntityDimensions.scalable(1, 1).scale(scale);
        } else if (target != null) return target.getDimensions(pose).scale(Math.max(0, (time - renderAsItemTime) / (duration - renderAsItemTime)));
        return EntityDimensions.scalable(Math.max(getBbWidth(), .001F), Math.max(getBbHeight(), .001F));
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Age", tickCount); output.putInt("Duration", duration); output.putFloat("RenderItemTime", renderAsItemTime);
        output.putBoolean("TurnBack", turningBack()); output.putInt("ActionCD", actionCooldown);
        output.store("Source", CompoundTag.CODEC, source.write(level()));
        if (target != null) output.store("TargetEntity", CompoundTag.CODEC, GETransformationData.saveEntity(target));
        output.storeNullable("Owner", UUIDUtil.CODEC, ownerId); output.storeNullable("Host", UUIDUtil.CODEC, hostId);
        output.storeNullable("HostOffset", Vec3.CODEC, hostOffset);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        tickCount = Math.max(0, input.getIntOr("Age", 0)); duration = Math.max(1, input.getIntOr("Duration", 1));
        renderAsItemTime = Math.max(.001F, input.getFloatOr("RenderItemTime", Math.min(duration / 3F, 20)));
        entityData.set(TURN_BACK, input.getBooleanOr("TurnBack", false)); actionCooldown = Math.max(0, input.getIntOr("ActionCD", 0));
        source = GETransformationData.read(input.read("Source", CompoundTag.CODEC).orElseGet(CompoundTag::new), level());
        target = input.read("TargetEntity", CompoundTag.CODEC).map(tag -> GETransformationData.loadEntity(tag, level())).orElse(null);
        ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null); hostId = input.read("Host", UUIDUtil.CODEC).orElse(null);
        hostOffset = input.read("HostOffset", Vec3.CODEC).orElse(null);
    }
    void receivePresentation(GoldExperienceMorphPayload payload) {
        tickCount = payload.age(); duration = payload.duration(); renderAsItemTime = payload.itemTime();
        ownerId = payload.owner().orElse(null); target = payload.target().map(view -> view.create(level())).orElse(null);
        source = payload.source().map(view -> view.create(level())).map(entity -> new GETransformationData().entity(entity)).orElseGet(GETransformationData::new);
        if (payload.block().isPresent()) source.block(payload.block().get(), blockPosition(), null);
        entityData.set(TURN_BACK, payload.reverse()); hostOffset = payload.hostOffset().orElse(null); refreshDimensions();
    }
}
