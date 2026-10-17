package com.github.standobyte.jojo.fabric.stand.hierophant;

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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Constant-velocity source emerald, with per-hit splash learning only for directly fired emeralds. */
public final class HGEmeraldEntity extends Projectile implements PillarmanBaseActions.EvasionProjectile {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(HGEmeraldEntity.class, EntityDataSerializers.INT);
    private boolean lowerKnockback, breakBlocks, grantsTraining, deflected;
    private float damageFactor = 1;
    public HGEmeraldEntity(EntityType<? extends HGEmeraldEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public HGEmeraldEntity(HierophantGreenEntity owner, boolean grantsTraining) {
        this(HierophantGreenActions.EMERALD, owner.level()); setOwner(owner); this.grantsTraining = grantsTraining;
        setPos(owner.getEyePosition().add(new Vec3(0, -.3, .75).yRot(-(float) Math.toRadians(owner.getYRot()))));
        setRot(owner.getYRot(), owner.getXRot());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(OWNER, -1); }
    @Override public void setOwner(Entity owner) { super.setOwner(owner); entityData.set(OWNER, owner == null ? -1 : owner.getId()); }
    @Override public Entity getOwner() { var owner = super.getOwner(); return owner != null ? owner : level().getEntity(entityData.get(OWNER)); }
    public void setLowerKnockback(boolean value) { lowerKnockback = value; }
    public void setBreakBlocks(boolean value) { breakBlocks = value; }
    public boolean breaksBlocks() { return breakBlocks; }
    public void setDamageFactor(float factor) { if (Float.isFinite(factor)) damageFactor = Math.clamp(factor, 0, 100); }
    public float damageFactor() { return damageFactor; }
    @Override public boolean standDamage() { return true; }
    @Override public boolean canBeEvaded(LivingEntity target) { return true; }
    public boolean canBeDeflected(Entity context) { return true; }
    public void setDeflected() { deflected = true; }
    @Override protected void onDeflection(boolean byPlayer) { super.onDeflection(byPlayer); deflected = true; }
    @Override public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        // 1.16's Gaussian spread is part of the source's accuracy formula.
        Vec3 direction = new Vec3(x, y, z).normalize().add(random.nextGaussian() * .0075 * inaccuracy,
                random.nextGaussian() * .0075 * inaccuracy, random.nextGaussian() * .0075 * inaccuracy).scale(velocity);
        setDeltaMovement(direction);
        setYRot((float) Math.toDegrees(Math.atan2(direction.x, direction.z)));
        setXRot((float) Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
        yRotO = getYRot(); xRotO = getXRot(); needsSync = true;
    }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        Entity owner = getOwner();
        if (!level().isClientSide() && (tickCount > 100 || owner == null || !owner.isAlive() || owner.level() != level())) { discard(); return; }
        super.tick();
        Vec3 start = position(), end = start.add(getDeltaMovement());
        if (level() instanceof ServerLevel server) {
            HitResult result = server.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            double nearest = result.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) : start.distanceToSqr(result.getLocation());
            for (var target : server.getEntities(this, new AABB(start, end).inflate(1), this::canHitEntity)) {
                var box = target.getBoundingBox().inflate(.3); var point = box.clip(start, end);
                double distance = box.contains(start) ? 0 : point.map(start::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
                if (distance < nearest) { nearest = distance; result = new EntityHitResult(target, point.orElse(start)); }
            }
            if (result instanceof EntityHitResult hit) hit(server, hit.getEntity());
            else if (result instanceof BlockHitResult hit && result.getType() != HitResult.Type.MISS) { destroyBlock(server, hit.getBlockPos()); discard(); }
        }
        if (!isRemoved()) setPos(end);
    }
    @Override protected boolean canHitEntity(Entity target) {
        if (!target.isAlive() || target.isSpectator() || !target.isPickable()) return false;
        Entity owner = getOwner();
        if (target == owner || owner instanceof StandManifestation stand && target == stand.owner()) return deflected;
        if (target instanceof Projectile projectile && projectile.getOwner() != null && projectile.getOwner() == owner) return false;
        if (owner instanceof HierophantGreenEntity stand) return stand.canAttackTarget(target);
        return !(owner instanceof LivingEntity living) || !(target instanceof LivingEntity victim) || living.canAttack(victim);
    }
    private void hit(ServerLevel server, Entity target) {
        Entity cause = getOwner() instanceof StandManifestation stand ? stand.owner() : getOwner();
        var source = new DamageSource(server.registryAccess().getOrThrow(DamageTypes.ARROW), this, cause);
        Vec3 velocity = target.getDeltaMovement();
        float damage=damageFactor*StandConfig.server().standPowerMultiplier();
        boolean hurt = target instanceof LivingEntity living ? StandDamage.hurt(server, living, source, damage)
                : target.hurtServer(server, source, damage);
        if (hurt && isOnFire()) target.igniteForSeconds(5);
        if (lowerKnockback) target.setDeltaMovement(velocity.add(target.getDeltaMovement().subtract(velocity).scale(.5)));
        if (hurt && getOwner() instanceof HierophantGreenEntity stand && stand.owner() != null) {
            if (target instanceof LivingEntity living) { living.setLastHurtByMob(stand.owner()); living.setLastHurtByPlayer(stand.owner(), 100); }
            if (grantsTraining && givesTraining(target)) HierophantGreenActions.trainSplash(stand.owner());
        }
        discard();
    }
    private static boolean givesTraining(Entity target) {
        return target.isAlive() && (target instanceof Player || target instanceof StandManifestation
                || target.getType().getCategory() == MobCategory.MONSTER || target instanceof Mob mob && mob.isAggressive());
    }
    private void destroyBlock(ServerLevel server, BlockPos pos) {
        var state = server.getBlockState(pos); float hardness = state.getDestroySpeed(server, pos);
        if (!server.getGameRules().get(JojoGameRules.BREAK_BLOCKS) || hardness < 0 || hardness > (breakBlocks ? 1.5F : 0)) return;
        Entity user = getOwner() instanceof StandManifestation stand ? stand.owner() : getOwner();
        if (user instanceof Player player && (!player.mayBuild() || !server.mayInteract(player, pos)
                || !net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(server, player, pos, state, server.getBlockEntity(pos)))) return;
        server.destroyBlock(pos, !(user instanceof Player player) || !player.isCreative(), getOwner());
    }
    @Override public boolean hurtServer(ServerLevel server, DamageSource source, float amount) {
        if (isInvulnerableToBase(source)) return false; discard(); return true;
    }
    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 1; }
}
