package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.mixin.KnifeArrowAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** KnifeEntity and its ItemProjectileEntity behavior, adapted from the original Forge source. */
public class KnifeEntity extends AbstractArrow {
    private static final EntityDataAccessor<Integer> FLIGHT_TICKS = SynchedEntityData.defineId(KnifeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TEXTURE = SynchedEntityData.defineId(KnifeEntity.class, EntityDataSerializers.INT);
    private boolean timeStopped;
    private Vec3 stoppedHitMotion;
    private Vec3 tickStartPosition;
    private Vec3 survivingHitMotion;

    public enum TexVariant {
        KNIFE("knife"), SCALPEL("knife_scalpel"), FISH("knife_fish");
        final Identifier texture;
        TexVariant(String name) { texture = RipplesFabric.id("textures/entity/projectiles/" + name + ".png"); }
    }

    public KnifeEntity(EntityType<? extends KnifeEntity> type, Level level) { super(type, level); }
    public KnifeEntity(Level level, LivingEntity owner) {
        super(RipplesFabric.KNIFE, owner, level, new ItemStack(StandItems.KNIFE), null);
        if (owner instanceof Player player && player.getAbilities().instabuild) pickup = Pickup.CREATIVE_ONLY;
    }
    public KnifeEntity(Level level, double x, double y, double z) {
        super(RipplesFabric.KNIFE, x, y, z, level, new ItemStack(StandItems.KNIFE), null);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLIGHT_TICKS, 0);
        builder.define(TEXTURE, 0);
    }
    public void setTimeStopFlightTicks(int ticks) { entityData.set(FLIGHT_TICKS, Math.max(0, ticks)); }
    public int getTimeStopFlightTicks() { return entityData.get(FLIGHT_TICKS); }
    public void setTimeStopped(boolean stopped) { timeStopped = stopped; }
    public boolean isTimeStopFrozen() { return timeStopped && getTimeStopFlightTicks() == 0; }
    public TexVariant getKnifeTexVariant() { return TexVariant.values()[Math.clamp(entityData.get(TEXTURE), 0, TexVariant.values().length - 1)]; }
    public void setKnifeTexVariant(TexVariant variant) { entityData.set(TEXTURE, variant.ordinal()); }
    public Identifier getKnifeTexture() { return getKnifeTexVariant().texture; }
    @Override public boolean isInGround() { return super.isInGround(); }
    @Override public boolean isPickable() { return true; }
    @Override protected ItemStack getDefaultPickupItem() { return new ItemStack(StandItems.KNIFE); }
    @Override protected SoundEvent getDefaultHitGroundSoundEvent() { return JojoSounds.get("knife_hit"); }

    @Override public Vec3 getMovementToShoot(double x, double y, double z, float speed, float uncertainty) {
        // 1.16 Projectile.shoot used Gaussian noise, not the current triangular distribution.
        double spread = (double) 0.0075F * uncertainty;
        return new Vec3(x, y, z).normalize().add(random.nextGaussian() * spread,
                random.nextGaussian() * spread, random.nextGaussian() * spread).scale(speed);
    }
    @Override protected java.util.Collection<EntityHitResult> findHitEntities(Vec3 start, Vec3 end) {
        // Original arrows trace the nearest contact with a fixed 0.3 margin. Modern
        // arrows widen it with age and can visit another target after a rejected hit.
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level(), this, start, end,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(1), this::canHitEntity, 0.3F);
        return hit == null ? java.util.List.of() : java.util.List.of(hit);
    }

    @Override public void tick() {
        if (isTimeStopFrozen()) return;
        if (timeStopped) setTimeStopFlightTicks(getTimeStopFlightTicks() - 1);
        tickStartPosition = position();
        survivingHitMotion = null;
        super.tick();
        if (stoppedHitMotion != null) {
            // Modern arrows move to the collision point before onHit. Restore the original
            // pre-impact position so thaw reruns collision and a moving target can evade it.
            setPos(tickStartPosition);
            setDeltaMovement(stoppedHitMotion);
            stoppedHitMotion = null;
        } else if (survivingHitMotion != null && !isRemoved()) {
            // Legacy arrows moved from their pre-hit position with the new velocity,
            // including a bounce; modern arrows otherwise remain at the contact point.
            setPos(tickStartPosition.add(survivingHitMotion));
        }
        survivingHitMotion = null;
        if (!isInGround() && level() instanceof ServerLevel server) {
            var hit = level().clip(new ClipContext(position(), position().add(getDeltaMovement()),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                var block = level().getBlockState(hit.getBlockPos());
                if (block.is(Blocks.COBWEB)) {
                    server.destroyBlock(hit.getBlockPos(), true);
                    setDeltaMovement(getDeltaMovement().scale(0.8));
                } else if (block.is(Blocks.TRIPWIRE)) server.destroyBlock(hit.getBlockPos(), true);
            }
        }
        // Original tracking interval is twenty ticks. Force a normal tracking update at the
        // final flight tick; synchronized remaining budget never replenishes on another stop.
        if (isTimeStopFrozen()) needsSync = true;
    }

    @Override protected ProjectileDeflection hitTargetOrDeflectSelf(HitResult hit) {
        if (timeStopped && hit.getType() == HitResult.Type.ENTITY) {
            stopBeforeEntityHit();
            return ProjectileDeflection.NONE;
        }
        return super.hitTargetOrDeflectSelf(hit);
    }
    @Override protected void onHit(HitResult hit) {
        if (timeStopped && hit.getType() == HitResult.Type.ENTITY) stopBeforeEntityHit();
        else super.onHit(hit);
    }
    private void stopBeforeEntityHit() {
        if (stoppedHitMotion == null) stoppedHitMotion = getDeltaMovement();
        setDeltaMovement(Vec3.ZERO);
        setTimeStopFlightTicks(0);
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        setSoundEvent(getDefaultHitGroundSoundEvent());
        super.onHitBlock(hit);
        shakeTime = 0;
        setNoGravity(false);
    }
    @Override protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        if (target instanceof Projectile other && getOwner() != null && other.getOwner() != null
                && getOwner().getUUID().equals(other.getOwner().getUUID())) return false;
        return true;
    }
    protected float getActualDamage() {
        float damage = (float) (getDeltaMovement().length() * ((KnifeArrowAccess) this).jojo$getBaseDamage());
        return isCritArrow() ? damage + random.nextInt((int) (damage / 2) + 2) : damage;
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel server)) return;
        Entity target = hit.getEntity(), thrower = getOwner();
        if (thrower instanceof LivingEntity living) living.setLastHurtMob(target);
        boolean enderman = target.getType() == net.minecraft.world.entity.EntityTypes.ENDERMAN;
        int fireTicks = target.getRemainingFireTicks();
        if (isOnFire() && !enderman) target.igniteForSeconds(5);
        var source = damageSources().arrow(this, thrower == null ? this : thrower);
        boolean hurt = target instanceof LivingEntity living
                ? StandDamage.hurt(server, living, source, getActualDamage())
                : target.hurtServer(server, source, getActualDamage());
        if (hurt) {
            if (enderman) { survivingHitMotion = getDeltaMovement(); return; }
            if (target instanceof LivingEntity living) {
                if (thrower instanceof LivingEntity) EnchantmentHelper.doPostAttackEffects(server, target, source);
                doPostHurtEffects(living);
            }
            playSound(getHitGroundSoundEvent(), 1F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            discard();
        } else {
            target.setRemainingFireTicks(fireTicks);
            setDeltaMovement(getDeltaMovement().scale(-0.1));
            survivingHitMotion = getDeltaMovement();
            setYRot(getYRot() + 180F);
            yRotO += 180F;
            if (getDeltaMovement().lengthSqr() < 1.0E-7) {
                if (pickup == Pickup.ALLOWED) spawnAtLocation(server, getPickupItem(), 0.1F);
                discard();
            }
        }
    }
    @Override protected void doPostHurtEffects(LivingEntity target) { StuckKnives.add(target); com.github.standobyte.jojo.fabric.stand.tracking.TrackingHooks.projectileEmbedded(this,target,true); }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Age", tickCount);
        output.putBoolean("TimeStop", timeStopped);
        output.putInt("TimeStopTicks", getTimeStopFlightTicks());
        output.putString("KnifeType", getKnifeTexVariant().name());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        tickCount = Math.max(0, input.getIntOr("Age", 0));
        timeStopped = input.getBooleanOr("TimeStop", false);
        setTimeStopFlightTicks(input.getIntOr("TimeStopTicks", 0));
        try { setKnifeTexVariant(TexVariant.valueOf(input.getStringOr("KnifeType", "KNIFE"))); }
        catch (IllegalArgumentException ignored) { setKnifeTexVariant(TexVariant.KNIFE); }
    }
}
