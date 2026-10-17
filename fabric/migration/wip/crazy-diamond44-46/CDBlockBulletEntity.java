package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotEntity;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Physical block projectile: source 5 damage, homing delay, original block-specific hit effects. */
public final class CDBlockBulletEntity extends CDProjectile {
    private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(CDBlockBulletEntity.class, EntityDataSerializers.INT),
            TARGET = SynchedEntityData.defineId(CDBlockBulletEntity.class, EntityDataSerializers.INT);
    private UUID targetUuid;
    public CDBlockBulletEntity(EntityType<? extends CDBlockBulletEntity> type, Level level) { super(type, level); }
    public CDBlockBulletEntity(CrazyDiamondEntity stand, Block block) {
        this(CrazyDiamondCombatActions.BLOCK_BULLET_ENTITY, stand.level()); setOwner(stand); setBlock(block);
        setShootingPos(stand.owner()); damageFactor = (float) stand.attackDamage() / 8; speedFactor = stand.attackSpeed() / 8;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(BLOCK, 0); builder.define(TARGET, -1); }
    public void setBlock(Block block) { entityData.set(BLOCK, BuiltInRegistries.BLOCK.getId(block)); }
    public Block block() { return BuiltInRegistries.BLOCK.byId(entityData.get(BLOCK)); }
    public void setTarget(Entity target) { targetUuid = target == null ? null : target.getUUID(); entityData.set(TARGET, target == null ? -1 : target.getId()); }
    public Entity homingTarget() {
        Entity target = level().getEntity(entityData.get(TARGET));
        if (target == null && targetUuid != null && level() instanceof ServerLevel server) {
            target = server.getEntity(targetUuid); if (target != null) entityData.set(TARGET, target.getId());
        }
        return target;
    }
    @Override protected int lifespan() { return 6000; }
    @Override protected float baseDamage() { return 5; }
    @Override protected float breakHardness() { return 1; }
    @Override public void tick() {
        super.tick();
        if (isRemoved() || TimeStopService.isFrozen(this)) return;
        var target = homingTarget();
        if (target == null) return;
        if (!target.isAlive()) { setTarget(null); return; }
        if (tickCount >= 10) {
            setDeltaMovement(target.getBoundingBox().getCenter().subtract(position()).normalize().scale(getDeltaMovement().length()));
            if (user() instanceof ServerPlayer player && !StandConfig.staminaFree(player)) {
                var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
                var stand = StandService.currentManifestation(player);
                float regen = progress.withStamina(0).regenerate(stand != null, stand == null || stand.allowsStaminaRegen(),
                        player.getFoodData().getFoodLevel(), false).stamina();
                player.setAttached(StandAttachments.PROGRESS, progress.withStamina(Math.max(0, progress.stamina() - regen - 2)));
            }
        }
    }
    @Override protected boolean hurtTarget(ServerLevel level, Entity target) {
        int previousFire = target.getRemainingFireTicks();
        if (block() == Blocks.MAGMA_BLOCK) target.igniteForSeconds(4);
        boolean hurt = super.hurtTarget(level, target);
        if (block() == Blocks.MAGMA_BLOCK) { if (!hurt) target.setRemainingFireTicks(previousFire); return hurt; }
        if (hurt && block() == Blocks.NOTE_BLOCK) {
            // Source's instrument array predates mob-head/trumpet instruments.
            var instruments = NoteBlockInstrument.values();
            var sound = instruments[random.nextInt(16)].getSoundEvent().value();
            target.playSound(sound, 5, (float) Math.pow(2, (random.nextInt(24) - 12) / 12D));
        } else if (hurt && target instanceof LivingEntity living) {
            int amplifier = block() == Blocks.ICE ? 0 : block() == Blocks.PACKED_ICE ? 1 : block() == Blocks.BLUE_ICE ? 2 : -1;
            if (amplifier >= 0) living.addEffect(new MobEffectInstance(VampireCombatService.FREEZE, 60, amplifier));
        }
        return hurt;
    }
    @Override protected void breakProjectile(HitResult hit) {
        if (level() instanceof ServerLevel server && (block() == Blocks.INFESTED_CHISELED_STONE_BRICKS || block() == Blocks.INFESTED_COBBLESTONE
                || block() == Blocks.INFESTED_CRACKED_STONE_BRICKS || block() == Blocks.INFESTED_MOSSY_STONE_BRICKS
                || block() == Blocks.INFESTED_STONE || block() == Blocks.INFESTED_STONE_BRICKS)) {
            var silverfish = EntityTypes.SILVERFISH.create(server, EntitySpawnReason.TRIGGERED);
            if (silverfish != null) { silverfish.snapTo(getX(), getY(.5), getZ(), 0, 0); server.addFreshEntity(silverfish); silverfish.spawnAnim(); }
        }
        super.breakProjectile(hit);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.storeNullable("HomingTarget", UUIDUtil.CODEC, targetUuid);
        // Original writes the block only to spawn data, so a save does not introduce a new block-persistence promise.
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); targetUuid = input.read("HomingTarget", UUIDUtil.CODEC).orElse(null); }
}

/** Active ModdedProjectileEntity/DamagingEntity behavior shared by both CD physical projectiles. */
abstract class CDProjectile extends Projectile implements SilverChariotEntity.DeflectableProjectile {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(CDProjectile.class, EntityDataSerializers.INT);
    protected float damageFactor = 1;
    protected double speedFactor = 1;
    private boolean deflected;
    protected CDProjectile(EntityType<? extends CDProjectile> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(OWNER, -1); }
    @Override public void setOwner(Entity owner) { super.setOwner(owner); entityData.set(OWNER, owner == null ? -1 : owner.getId()); }
    @Override public Entity getOwner() { var owner = super.getOwner(); return owner != null ? owner : level().getEntity(entityData.get(OWNER)); }
    protected Entity user() { return getOwner() instanceof StandManifestation stand ? stand.owner() : getOwner(); }
    protected void setShootingPos(Entity shooter) { setPos(shooter.getEyePosition().add(0, -.3, 0)); setRot(shooter.getYRot(), shooter.getXRot()); }
    protected abstract int lifespan();
    protected abstract float baseDamage();
    protected abstract float breakHardness();
    @Override public boolean standDamage() { return false; }
    @Override public boolean canBeEvaded(LivingEntity context) { return true; }
    @Override public boolean canBeDeflected(Entity context) { return true; }
    @Override public boolean isFiery() { return false; }
    @Override public void setDeflected() { deflected = true; }
    @Override protected void onDeflection(boolean byPlayer) { super.onDeflection(byPlayer); deflected = true; }
    @Override public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        var motion = new Vec3(x, y, z).normalize().add(random.nextGaussian() * .0075 * inaccuracy,
                random.nextGaussian() * .0075 * inaccuracy, random.nextGaussian() * .0075 * inaccuracy).scale(velocity);
        setDeltaMovement(motion); setYRot((float) Math.toDegrees(Math.atan2(motion.x, motion.z)));
        setXRot((float) Math.toDegrees(Math.atan2(motion.y, motion.horizontalDistance())));
        yRotO = getYRot(); xRotO = getXRot(); needsSync = true;
    }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        var owner = getOwner();
        if (!level().isClientSide() && (tickCount > lifespan() || owner != null && !owner.isAlive())) { discard(); return; }
        super.tick();
        var start = position(); var end = start.add(getDeltaMovement());
        if (level() instanceof ServerLevel server) {
            HitResult hit = server.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            double nearest = hit.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) : start.distanceToSqr(hit.getLocation());
            for (var target : server.getEntities(this, new AABB(start, end).inflate(1), this::canHitEntity)) {
                var box = target.getBoundingBox().inflate(.3); var point = box.clip(start, end);
                double distance = box.contains(start) ? 0 : point.map(start::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
                if (distance < nearest) { nearest = distance; hit = new EntityHitResult(target, point.orElse(start)); }
            }
            if (hit instanceof EntityHitResult entityHit) { hurtTarget(server, entityHit.getEntity()); breakProjectile(hit); }
            else if (hit instanceof BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) {
                var pos = blockHit.getBlockPos(); var state = server.getBlockState(pos); float hardness = state.getDestroySpeed(server, pos);
                var user = user();
                if (server.getGameRules().get(JojoGameRules.BREAK_BLOCKS) && hardness >= 0 && hardness <= breakHardness()
                        && (!(user instanceof Player player) || player.mayBuild() && server.mayInteract(player, pos)
                        && net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(server, player, pos, state, server.getBlockEntity(pos))))
                    server.destroyBlock(pos, !(user instanceof Player player) || !player.isCreative(), getOwner());
                breakProjectile(hit);
            }
        }
        if (!isRemoved()) setPos(end);
    }
    @Override protected boolean canHitEntity(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !entity.isPickable()) return false;
        var owner = getOwner();
        if (entity == owner || entity == user()) return deflected;
        if (entity instanceof Projectile projectile && owner != null && projectile.getOwner() == owner) return false;
        if (owner instanceof LivingEntity living && entity instanceof LivingEntity target && !living.canAttack(target)) return false;
        return !(entity instanceof Player player && user() instanceof Player user && !user.canHarmPlayer(player));
    }
    protected boolean hurtTarget(ServerLevel level, Entity target) {
        var source = new DamageSource(level.registryAccess().getOrThrow(DamageTypes.ARROW), this, getOwner());
        float damage = baseDamage() * damageFactor * (getOwner() instanceof StandManifestation ? StandConfig.server().standPowerMultiplier() : 1);
        boolean hurt = target instanceof LivingEntity living ? StandDamage.hurt(level, living, source, damage) : target.hurtServer(level, source, damage);
        if (hurt && isOnFire()) target.igniteForSeconds(5);
        if (hurt && getOwner() instanceof StandManifestation && target instanceof LivingEntity living && user() instanceof LivingEntity user) {
            living.setLastHurtByMob(user); if (user instanceof Player player) living.setLastHurtByPlayer(player, 100);
        }
        return hurt;
    }
    protected void breakProjectile(HitResult hit) { if (!level().isClientSide()) discard(); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source)) return false; breakProjectile(null); return true;
    }
    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 1; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putFloat("DamageFactor", damageFactor); output.putDouble("SpeedFactor", speedFactor);
        output.putInt("Age", tickCount); output.putBoolean("IsDeflected", deflected);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); damageFactor = input.getFloatOr("DamageFactor", 0); speedFactor = input.getDoubleOr("SpeedFactor", 0);
        tickCount = input.getIntOr("Age", 0); deflected = input.getBooleanOr("IsDeflected", false);
    }
}
