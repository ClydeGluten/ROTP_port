package com.github.standobyte.jojo.fabric.stand.silverchariot;

import com.github.standobyte.jojo.fabric.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Original indefinite, unsaved rapier: exact Stand owner, at most 100 bounded ricochets, no ricochet damage bonus. */
public final class SilverChariotRapierEntity extends Projectile implements SilverChariotEntity.DeflectableProjectile {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SilverChariotRapierEntity.class, EntityDataSerializers.INT);
    private LivingEntity cachedOwner;
    private int ricochets;
    private boolean leftOwner, deflected;
    public SilverChariotRapierEntity(EntityType<? extends SilverChariotRapierEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public SilverChariotRapierEntity(SilverChariotEntity stand) {
        this(SilverChariotActions.RAPIER, stand.level()); setOwner(stand);
        LivingEntity origin = stand;
        setPos(origin.getEyePosition().add(new Vec3(0, -.29, .375)
                .add(new Vec3(0, 0, 1.375).xRot(-origin.getXRot() * ((float) Math.PI / 180)))
                .yRot(-origin.getYRot() * ((float) Math.PI / 180))));
        setRot(stand.getYRot(), stand.getXRot()); Vec3 direction = stand.getLookAngle(); shoot(direction.x, direction.y, direction.z, 2, 0);
        if (stand.isRapierOnFire()) igniteForSeconds(Integer.MAX_VALUE / 20F);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(OWNER, -1); }
    @Override public void setOwner(Entity owner) { super.setOwner(owner); cachedOwner = owner instanceof LivingEntity living ? living : null; entityData.set(OWNER, owner == null ? -1 : owner.getId()); }
    @Override public LivingEntity getOwner() {
        if (cachedOwner == null) {
            Entity found = super.getOwner(); if (found == null) found = level().getEntity(entityData.get(OWNER));
            if (found instanceof LivingEntity living) cachedOwner = living;
        }
        return cachedOwner;
    }
    public int ricochetCount() { return ricochets; }
    public boolean leftOwner() { return leftOwner; }
    public float damageAmount() { return (float) (getOwner() == null ? 8 : getOwner().getAttributeBaseValue(Attributes.ATTACK_DAMAGE)) * 1.5F * (float) StandConfig.server().standPowerMultiplier(); }
    @Override public boolean standDamage() { return true; }
    @Override public boolean canBeEvaded(LivingEntity target) { return true; }
    @Override public boolean canBeDeflected(Entity context) { return true; }
    @Override public boolean isFiery() { return false; }
    @Override public void setDeflected() { deflected = true; }
    @Override public boolean displayFireAnimation() { return false; }
    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 1; }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        var owner = getOwner();
        if (!level().isClientSide() && owner != null && !owner.isAlive()) { discard(); return; }
        super.tick();
        if (!leftOwner && owner != null) leftOwner = super.canHitEntity(owner);
        if (level() instanceof ServerLevel server) {
            HitResult hit = trace(position(), position().add(getDeltaMovement()), false);
            if (hit instanceof EntityHitResult entityHit) hurtTarget(server, entityHit.getEntity());
            else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) bounceOrSettle(blockHit);
        }
        setPos(position().add(getDeltaMovement())); updateRotation();
    }
    @Override protected boolean canHitEntity(Entity target) {
        return eligible(this, getOwner(), target, deflected) && (!(target instanceof Skeleton) || random.nextFloat() >= .05F);
    }
    static boolean eligible(Projectile projectile, LivingEntity owner, Entity target, boolean deflected) {
        if (!target.isAlive() || target.isSpectator() || !target.isPickable()) return false;
        if (owner == null) return true;
        if (target == owner || owner instanceof StandManifestation stand && target == stand.owner()) return deflected;
        if (target instanceof Projectile other && other.getOwner() != null && owner.getUUID().equals(other.getOwner().getUUID())) return false;
        var user = owner instanceof StandManifestation stand ? stand.owner() : owner;
        if (user instanceof Player player && target instanceof Player victim && !player.canHarmPlayer(victim)) return false;
        return !(target instanceof LivingEntity living) || owner.canAttack(living);
    }
    private HitResult trace(Vec3 from, Vec3 to, boolean ricochetProbe) {
        var block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double closest = block.getType() == HitResult.Type.MISS ? from.distanceToSqr(to) : from.distanceToSqr(block.getLocation());
        HitResult result = block;
        for (var target : level().getEntities(this, new AABB(from, to).inflate(1), entity -> ricochetProbe ? entity.isAlive() && !entity.isSpectator() : canHitEntity(entity))) {
            var bounds = target.getBoundingBox().inflate(target.getPickRadius() + (ricochetProbe ? 1 : 0));
            var location = bounds.contains(from) ? java.util.Optional.of(from) : bounds.clip(from, to);
            if (location.isPresent() && from.distanceToSqr(location.get()) <= closest) {
                closest = from.distanceToSqr(location.get()); result = new EntityHitResult(target, location.get());
            }
        }
        return result;
    }
    private void bounceOrSettle(BlockHitResult hit) {
        Vec3 motion = getDeltaMovement();
        if (ricochets < 100) {
            var sound = level().getBlockState(hit.getBlockPos()).getSoundType();
            level().playSound(null, hit.getBlockPos(), sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 8F, sound.getPitch() * .5F);
            Vec3 reflected = switch (hit.getDirection().getAxis()) { case X -> new Vec3(-motion.x, motion.y, motion.z); case Y -> new Vec3(motion.x, -motion.y, motion.z); case Z -> new Vec3(motion.x, motion.y, -motion.z); };
            if (reflected.lengthSqr() > 0 && trace(position(), position().add(reflected.normalize().scale(16)), true).getType() != HitResult.Type.MISS) {
                setDeltaMovement(reflected); updateRotation(); ricochets++; syncVelocity = true; return;
            }
        }
        setPos(hit.getLocation()); setDeltaMovement(Vec3.ZERO); syncVelocity = true;
    }
    private void hurtTarget(ServerLevel server, Entity target) {
        var source = new DamageSource(server.registryAccess().getOrThrow(DamageTypes.ARROW), this, getOwner());
        boolean hurt = target instanceof LivingEntity living ? StandDamage.hurt(server, living, source, damageAmount()) : target.hurtServer(server, source, damageAmount());
        if (hurt && isOnFire()) target.igniteForSeconds(5);
    }
    @Override public void playerTouch(Player player) {
        if (!level().isClientSide() && getOwner() instanceof SilverChariotEntity stand && !stand.manuallyControlled() && stand.owner() == player) takeRapier(stand);
    }
    public void takeRapier(SilverChariotEntity stand) {
        if (level().isClientSide() || !isAlive() || !leftOwner || stand != getOwner()) return;
        stand.level().playSound(null, stand.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1, 1); stand.setRapier(true);
        if (stand.owner() != null && ProgressionService.unlocked(stand.owner(), 2)) AbilityCooldowns.start(stand.owner(), SilverChariotActions.LAUNCH, 0);
        discard();
    }
    @Override public boolean hurtServer(ServerLevel server, DamageSource source, float damage) {
        if (isInvulnerableToBase(source)) return false; syncVelocity = true; return true;
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("Ricochets", ricochets); output.putBoolean("IsDeflected", deflected);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); cachedOwner = null; ricochets = Math.clamp(input.getIntOr("Ricochets", 0), 0, 100); deflected = input.getBooleanOr("IsDeflected", false);
    }
}
