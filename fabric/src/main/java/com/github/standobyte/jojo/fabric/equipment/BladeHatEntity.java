package com.github.standobyte.jojo.fabric.equipment;

import com.github.standobyte.jojo.fabric.JojoSounds;
import com.github.standobyte.jojo.fabric.StandDamage;
import com.github.standobyte.jojo.fabric.TimeStopService;
import com.github.standobyte.jojo.fabric.mixin.KnifeArrowAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * Port of BladeHatEntity + ItemNbtProjectileEntity + ItemProjectileEntity.
 * Preserves exact thrown ItemStack components/enchantments/durability, return after 30 ticks or entity hit,
 * gravity enable after 100 ticks, cobweb/tripwire/bush cutting, and owner/grounded pickup rules.
 */
public final class BladeHatEntity extends AbstractArrow {
    private static final EntityDataAccessor<Boolean> RETURNING_TO_OWNER =
            SynchedEntityData.defineId(BladeHatEntity.class, EntityDataSerializers.BOOLEAN);

    private ItemStack thrownStack = new ItemStack(EquipmentContent.BLADE_HAT);
    private boolean leftOwner;

    public BladeHatEntity(EntityType<? extends BladeHatEntity> type, Level level) {
        super(type, level);
        initCommon();
    }

    public BladeHatEntity(Level level, LivingEntity thrower, ItemStack stack) {
        super(EquipmentContent.BLADE_HAT_ENTITY, thrower, level, stack.isEmpty() ? new ItemStack(EquipmentContent.BLADE_HAT) : stack.copyWithCount(1), null);
        this.thrownStack = stack.isEmpty() ? new ItemStack(EquipmentContent.BLADE_HAT) : stack.copyWithCount(1);
        if (thrower instanceof Player player && player.getAbilities().instabuild) {
            this.pickup = Pickup.CREATIVE_ONLY;
        }
        initCommon();
    }

    public BladeHatEntity(Level level, double x, double y, double z, ItemStack stack) {
        super(EquipmentContent.BLADE_HAT_ENTITY, x, y, z, level, stack.isEmpty() ? new ItemStack(EquipmentContent.BLADE_HAT) : stack.copyWithCount(1), null);
        this.thrownStack = stack.isEmpty() ? new ItemStack(EquipmentContent.BLADE_HAT) : stack.copyWithCount(1);
        initCommon();
    }

    private void initCommon() {
        setNoGravity(true);
        setBaseDamage(6.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RETURNING_TO_OWNER, false);
    }

    public boolean isReturningToOwner() {
        return entityData.get(RETURNING_TO_OWNER);
    }

    private void setReturningToOwner(boolean returning) {
        entityData.set(RETURNING_TO_OWNER, returning);
    }

    /** Authoritative tracker slot; renderers continue receiving defensive copies. */
    public ItemStack trackingStack() { return thrownStack; }
    public void trackingStack(ItemStack value) { thrownStack=value;setPickupItemStack(value.copy()); }
    public ItemStack getThrownStack() {
        return thrownStack.copy();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(EquipmentContent.BLADE_HAT);
    }

    @Override
    protected ItemStack getPickupItem() {
        return thrownStack.isEmpty() ? getDefaultPickupItem() : thrownStack.copy();
    }

    @Override
    public boolean isInGround() {
        return super.isInGround();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return JojoSounds.get("blade_hat_entity_hit");
    }

    @Override
    public Vec3 getMovementToShoot(double x, double y, double z, float speed, float spread) {
        double noise = 0.0075F * spread;
        return new Vec3(x, y, z).normalize().add(
                random.nextGaussian() * noise,
                random.nextGaussian() * noise,
                random.nextGaussian() * noise).scale(speed);
    }

    @Override
    protected Collection<EntityHitResult> findHitEntities(Vec3 start, Vec3 end) {
        var hit = ProjectileUtil.getEntityHitResult(level(), this, start, end,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(1.0), this::canHitEntity, 0.3F);
        return hit == null ? List.of() : List.of(hit);
    }

    @Override
    public void tick() {
        if (TimeStopService.isFrozen(this)) {
            return;
        }
        if (!leftOwner) {
            Entity owner = getOwner();
            leftOwner = owner == null || !getBoundingBox().inflate(0.3D).intersects(owner.getBoundingBox());
        }
        super.tick();
        if (!isReturningToOwner() && shouldReturn()) {
            changeMovementAfterHit();
        }
        if (tickCount > 100) {
            setNoGravity(false);
        } else if (!isInGround() && level() instanceof ServerLevel server) {
            HitResult rayTrace = level().clip(new ClipContext(
                    position(), position().add(getDeltaMovement()),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
            if (rayTrace.getType() == HitResult.Type.BLOCK) {
                BlockPos blockPos = ((BlockHitResult) rayTrace).getBlockPos();
                Block block = level().getBlockState(blockPos).getBlock();
                if (block == Blocks.COBWEB || block == Blocks.TRIPWIRE || block instanceof BushBlock) {
                    server.destroyBlock(blockPos, true);
                }
            }
        }
    }

    private boolean shouldReturn() {
        return tickCount > 30;
    }

    private void changeMovementAfterHit() {
        if (!isReturningToOwner()) {
            setDeltaMovement(getDeltaMovement().reverse());
            setReturningToOwner(true);
            needsSync = true;
        }
    }

    @Override
    protected void onHit(HitResult result) {
        HitResult.Type type = result.getType();
        if (type != HitResult.Type.MISS && level() instanceof ServerLevel server) {
            if (!thrownStack.isEmpty() && getOwner() instanceof LivingEntity shooter) {
                thrownStack.hurtAndBreak(1, server, shooter instanceof ServerPlayer sp ? sp : null, item -> discard());
                if (isRemoved()) {
                    return;
                }
            }
        }
        super.onHit(result);
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        BlockState blockState = level().getBlockState(blockHitResult.getBlockPos());
        setSoundEvent(blockState.getSoundType().getBreakSound());
        super.onHitBlock(blockHitResult);
        shakeTime = 0;
        setNoGravity(false);
        setSoundEvent(getDefaultHitGroundSoundEvent());
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity.is(getOwner()) || !super.canHitEntity(entity)) {
            return false;
        }
        if (entity instanceof Projectile other && getOwner() != null && other.getOwner() != null
                && getOwner().getUUID().equals(other.getOwner().getUUID())) {
            return false;
        }
        return true;
    }

    public double getEffectiveBaseDamage() {
        double base = ((KnifeArrowAccess) (Object) this).jojo$getBaseDamage();
        if (!thrownStack.isEmpty() && level() != null) {
            var lookup = level().registryAccess().lookup(Registries.ENCHANTMENT);
            if (lookup.isPresent()) {
                var sharpness = lookup.get().get(Enchantments.SHARPNESS);
                if (sharpness.isPresent()) {
                    int lvl = EnchantmentHelper.getItemEnchantmentLevel(sharpness.get(), thrownStack);
                    if (lvl > 0) {
                        // Original 1.16.5 SharpnessDamageEnchantment.getDamageBonus(level, UNDEFINED): 1.0 + 0.5 * (level - 1)
                        base += 0.5D * lvl + 0.5D;
                    }
                }
            }
        }
        return base;
    }

    protected float getActualDamage() {
        float damage = (float) (getDeltaMovement().length() * getEffectiveBaseDamage());
        return isCritArrow() ? damage + random.nextInt((int) (damage / 2) + 2) : damage;
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Entity target = entityHitResult.getEntity();
        Entity thrower = getOwner();
        if (thrower instanceof LivingEntity living) {
            living.setLastHurtMob(target);
        }
        int prevFireTicks = target.getRemainingFireTicks();
        boolean enderman = target.getType() == EntityTypes.ENDERMAN;
        if (isOnFire() && !enderman) {
            target.igniteForSeconds(5);
        }
        var damageSource = damageSources().arrow(this, thrower == null ? this : thrower);
        // ItemProjectileEntity uses normal damage immunity, unlike DamagingEntity bullets.
        boolean hurt = target.hurtServer(server, damageSource, getActualDamage());
        if (hurt) {
            if (enderman) {
                return;
            }
            if (target instanceof LivingEntity livingTarget) {
                if (thrower instanceof LivingEntity) {
                    EnchantmentHelper.doPostAttackEffects(server, target, damageSource);
                }
                doPostHurtEffects(livingTarget);
            }
            playSound(getHitGroundSoundEvent(), 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            // BladeHatEntity.isRemovedOnEntityHit() is false: reverses and returns to owner instead of discarding.
            changeMovementAfterHit();
        } else {
            target.setRemainingFireTicks(prevFireTicks);
            setDeltaMovement(getDeltaMovement().scale(-0.1D));
            setYRot(getYRot() + 180.0F);
            yRotO += 180.0F;
            if (getDeltaMovement().lengthSqr() < 1.0E-7D) {
                changeMovementAfterHit();
            }
        }
        needsSync = true;
    }

    @Override
    public void playerTouch(Player player) {
        if (!level().isClientSide()) {
            Entity shooter = getOwner();
            if (isInGround() || shooter == null || shooter.getUUID().equals(player.getUUID())) {
                boolean canPickUp = (this.pickup == Pickup.ALLOWED
                        || this.pickup == Pickup.CREATIVE_ONLY && player.getAbilities().instabuild)
                        && (isInGround() || isNoPhysics() || leftOwner);
                if (canPickUp) {
                    if (this.pickup == Pickup.ALLOWED && !player.getInventory().add(getPickupItem())) {
                        return;
                    }
                    player.take(this, 1);
                    discard();
                }
            }
        }
    }

    @Override
    protected void tickDespawn() {
        if (this.pickup != Pickup.ALLOWED) {
            super.tickDespawn();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Age", tickCount);
        output.putBoolean("Returning", isReturningToOwner());
        output.putBoolean("LeftOwner", leftOwner);
        if (!thrownStack.isEmpty()) {
            output.store("Item", ItemStack.OPTIONAL_CODEC, thrownStack);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        tickCount = input.getIntOr("Age", 0);
        setReturningToOwner(input.getBooleanOr("Returning", false));
        leftOwner = input.getBooleanOr("LeftOwner", false);
        thrownStack = input.read("Item", ItemStack.OPTIONAL_CODEC)
                .filter(s -> !s.isEmpty())
                .orElseGet(() -> new ItemStack(EquipmentContent.BLADE_HAT));
    }
}
