package com.github.standobyte.jojo.fabric.stand.hierophant;

import com.github.standobyte.jojo.core.StandDefinitions;
import com.github.standobyte.jojo.core.StandStatFormulas;
import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Hierophant's source task phases, owner control and barrier anchors, independent of SP/TW combat. */
public final class HierophantGreenEntity extends Mob implements StandManifestation {
    public enum Task { NONE, STRING, BIND, SPLASH, CONCENTRATED, BLOCK, GRAPPLE, GRAPPLE_ENTITY, BARRIER }
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TASK = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REMAINING = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CONTROLLED = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BARRIERS = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> ARMS = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> SUMMON_LOCK = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GRADUAL_SUMMON = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> UNSUMMON = SynchedEntityData.defineId(HierophantGreenEntity.class, EntityDataSerializers.INT);
    private UUID ownerUuid;
    private final HGBarriersNet barriers = new HGBarriersNet();
    private HGBarrierEntity stringToUser, stringFromStand;
    private HGGrapplingStringEntity grapple;
    private Identifier queued;
    private BlockPos barrierTarget;
    private LivingEntity stringTarget;
    private Vec3 stringTargetPoint;
    private int elapsed, phaseDuration, performDuration, recoveryDuration, guardBreak, heldLease, controlLease;
    private float blockedDamage, previousBlockedDamage;
    private Vec3 bodyAnchor, controlInput = Vec3.ZERO;
    private float controlYaw, controlPitch;
    private boolean retracting, fastRetraction, transferringDamage;

    public HierophantGreenEntity(EntityType<? extends HierophantGreenEntity> type, Level level) {
        super(type, level); noPhysics = true; setNoGravity(true); setNoAi(true); setPersistenceRequired();
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(OWNER, -1); builder.define(TASK, 0); builder.define(PHASE, 0);
        builder.define(REMAINING, 0); builder.define(DURATION, 0); builder.define(CONTROLLED, false); builder.define(BARRIERS, 0);
        builder.define(ARMS, (byte) 0); builder.define(SUMMON_LOCK, StandStatFormulas.getSummonLockTicks(10));
        builder.define(GRADUAL_SUMMON, 0); builder.define(UNSUMMON, -1);
    }
    @Override public Mob entity() { return this; }
    @Override public StandDefinitions.Definition definition() { return StandDefinitions.definition(HierophantGreenActions.STAND_ID); }
    @Override public void setOwner(ServerPlayer user) { ownerUuid = user.getUUID(); entityData.set(OWNER, user.getId()); setPos(user.position()); setRot(user.getYRot(), user.getXRot()); }
    public LivingEntity user() { return level().getEntity(ownerId()) instanceof LivingEntity living ? living : null; }
    @Override public ServerPlayer owner() { return ownerUuid != null && level().getEntity(ownerUuid) instanceof ServerPlayer player ? player : null; }
    @Override public int ownerId() { return entityData.get(OWNER); }
    public Task task() { return Task.values()[Math.clamp(entityData.get(TASK), 0, Task.values().length - 1)]; }
    public int taskPhase() { return entityData.get(PHASE); }
    @Override public int actionPhase() { return guardBreak > 0 ? 6 : task() == Task.BLOCK ? 5 : taskPhase(); }
    @Override public int actionTicks() { return entityData.get(REMAINING); }
    @Override public int actionDuration() { return entityData.get(DURATION); }
    public int phaseElapsed() { return actionDuration() - actionTicks(); }
    @Override public int abilityKind() { return task().ordinal(); }
    @Override public boolean heavyAttack() { return false; }
    @Override public float finisherCharge() { return 0; }
    @Override public int uiRecoveryTicks() { return guardBreak > 0 ? guardBreak : taskPhase() == 3 ? actionTicks() : 0; }
    @Override public boolean allowsStaminaRegen() { return task() == Task.NONE; }
    @Override public float userWalkSpeed() { return manuallyControlled() || task() == Task.NONE || taskPhase() == 3 ? 1 : task() == Task.BLOCK ? .3F : isGrapple(task()) ? 0 : .5F; }
    @Override public boolean manuallyControlled() { return entityData.get(CONTROLLED); }
    @Override public Vec3 bodyAnchor() { return bodyAnchor; }
    @Override public void translateControlAnchor(Vec3 movement) { if (bodyAnchor != null && manuallyControlled()) bodyAnchor = bodyAnchor.add(movement); }
    public boolean followingUser() { return !manuallyControlled() && !retracting; }
    public boolean isRetractingToUser() { return retracting; }
    public boolean hasAcceleratedRetraction() { return fastRetraction; }
    public boolean isArmsOnlyMode() { return (entityData.get(ARMS) & 1) != 0; }
    public boolean wasSummonedAsArms() { return (entityData.get(ARMS) & 2) != 0; }
    public boolean showMainArm() { return (entityData.get(ARMS) & 4) != 0; }
    public boolean showOffArm() { return (entityData.get(ARMS) & 8) != 0; }
    public int summonLockTicks() { return entityData.get(SUMMON_LOCK); }
    public int gradualSummonTicks() { return entityData.get(GRADUAL_SUMMON); }
    public int unsummonTicks() { return entityData.get(UNSUMMON); }
    public void setArmsOnlyMode() { setArmsOnlyMode(true, true); }
    public void setArmsOnlyMode(boolean main, boolean off) {
        if (level().isClientSide()) return;
        entityData.set(ARMS, (byte) (3 | (main ? 4 : 0) | (off ? 8 : 0))); entityData.set(SUMMON_LOCK, 0);
    }
    public void fullSummonFromArms() {
        if (level().isClientSide() || !isArmsOnlyMode()) return;
        entityData.set(ARMS, (byte) 2); entityData.set(GRADUAL_SUMMON, StandStatFormulas.getSummonLockTicks(attackSpeed()));
        entityData.set(UNSUMMON, -1);
    }
    public int getUnsummonDuration() {
        if (user() instanceof Player player && StandAttachments.resolve(player).isBurst()) return isArmsOnlyMode() ? 3 : 5;
        return (int) Math.ceil((isArmsOnlyMode() ? 7 : 10) / ((staminaCondition() * 2 + 1) / 3));
    }
    private boolean armsWeakness() { return isArmsOnlyMode() || gradualSummonTicks() > 0; }
    public double staminaCondition() {
        if (!(user() instanceof Player user) || StandConfig.staminaFree(user) || StandAttachments.resolve(user).isBurst()) return 1;
        var progress = user.getAttachedOrCreate(StandAttachments.PROGRESS);
        return .25 + Math.min(progress.stamina() / progress.maxStamina() * 1.5, .75);
    }
    private float development() { return user() == null ? 0 : user().getAttachedOrCreate(StandAttachments.PROGRESS).development(); }
    public double efficiency() { return staminaCondition() * (user() == null ? 1 : StandStatFormulas.rangeStrengthFactor(50, 100, distanceTo(user()))); }
    public double attackSpeed() { return definition().stats().speed(development()) * efficiency(); }
    public double attackDamage() { return definition().stats().power(development()) * efficiency() * (armsWeakness() ? .75 : 1); }
    public double durability() { return definition().stats().durability(development()) * efficiency() * (armsWeakness() ? .75 : 1); }
    public double projectileFireRate() { return attackSpeed() / definition().stats().speed(); }
    public float projectileDamageFactor() { return (float) attackDamage() / 8; }
    public float projectileInaccuracy(float base) { return Math.max((base + 1) * 8 / Math.max(armsWeakness() ? 0 : (float) (definition().stats().precision(development()) * efficiency()), 4) - 1, 0); }
    public float guardCounter() { return Math.min((task() == Task.BLOCK ? blockedDamage : previousBlockedDamage) / 5, 1); }
    public boolean canAttackTarget(Entity target) {
        if (target == this || target == user() || !target.isAlive() || target.isSpectator()) return false;
        if (target instanceof Player player && owner() != null && !owner().canHarmPlayer(player)) return false;
        return !(target instanceof LivingEntity living) || canAttack(living);
    }
    public HitResult aim(double range) {
        LivingEntity aiming = manuallyControlled() ? this : user(); if (aiming == null) return BlockHitResult.miss(position(), getDirection(), blockPosition());
        Vec3 start = aiming.getEyePosition(), end = start.add(aiming.getLookAngle().scale(range));
        HitResult result = level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, aiming));
        double closest = result.getType() == HitResult.Type.MISS ? range * range : start.distanceToSqr(result.getLocation());
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1), this::canAttackTarget)) {
            var box = target.getBoundingBox().inflate(target.getPickRadius()); var hit = box.clip(start, end);
            double distance = box.contains(start) ? 0 : hit.map(start::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
            if (distance < closest) { result = new EntityHitResult(target, hit.orElse(start)); closest = distance; }
        }
        return result;
    }
    public boolean canStart(Task next) {
        if (guardBreak > 0 || VampireCombatService.isStunned(this) || owner() == null || VampireCombatService.isStunned(owner())) return false;
        if (next == Task.BARRIER && (!canPlaceBarrier() || aim(10).getType() != HitResult.Type.BLOCK)) return false;
        if (task() == next && isHeld(next)) return true;
        if (task() == next && !isSplash(next)) return false;
        return true; // Non-cancelable finite tasks queue the next finite action in begin(), as in checkTaskCancelling.
    }
    public boolean begin(Identifier action) {
        Task next = HierophantGreenActions.task(action); if (next == Task.NONE || !canStart(next)) return false;
        if (task() == next && isHeld(next)) { refreshHeld(); return true; }
        if (!canCancelFor(next)) {
            if (!isHeld(next)) queued = action;
            return false;
        }
        boolean sameSplash = isSplash(task()) && isSplash(next);
        stopCurrent(false); queued = null; entityData.set(UNSUMMON, -1);
        entityData.set(TASK, next.ordinal()); elapsed = 0; heldLease = 12;
        performDuration = switch (next) {
            case STRING, BIND -> (int) Math.ceil((next == Task.STRING ? 16 : 24) / Math.max(attackSpeed() / 8, .125));
            case SPLASH -> 30;
            case CONCENTRATED -> 5;
            case BLOCK, GRAPPLE, GRAPPLE_ENTITY -> Integer.MAX_VALUE;
            default -> 1;
        };
        recoveryDuration = isSplash(next) && !noSplashPhases() ? 40 : 0;
        int windup = isSplash(next) && !sameSplash && !noSplashPhases() ? StandStatFormulas.getHeavyAttackWindup(attackSpeed(), 0) : 0;
        var target = aim(next == Task.BARRIER ? 10 : 8);
        barrierTarget = next == Task.BARRIER && target instanceof BlockHitResult block ? block.getBlockPos() : null;
        stringTarget = (next == Task.STRING || next == Task.BIND) && target instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living ? living : null;
        stringTargetPoint = (next == Task.STRING || next == Task.BIND) && target.getType() != HitResult.Type.MISS ? target.getLocation() : null;
        phase(windup > 0 ? 1 : 2, windup > 0 ? windup : performDuration);
        HierophantGreenActions.startCooldown(owner(), action);
        if (isSplash(next)) playOriginal("kakyoin_emerald_splash");
        StandMovement.update(owner()); return true;
    }
    private boolean noSplashPhases() { return owner() != null && (ProgressionService.unlocked(owner(), 3) || StandAttachments.resolve(owner()).isBurst()); }
    private boolean canCancelFor(Task next) {
        return unsummonTicks() < 0 && (task() == Task.NONE || isHeld(task()) || isSplash(task()) && taskPhase() == 3 || task() == Task.BIND && isSplash(next));
    }
    private static boolean isGrapple(Task task) { return task == Task.GRAPPLE || task == Task.GRAPPLE_ENTITY; }
    private static boolean isHeld(Task task) { return task == Task.BLOCK || isGrapple(task); }
    private static boolean isSplash(Task task) { return task == Task.SPLASH || task == Task.CONCENTRATED; }
    private void phase(int phase, int duration) {
        elapsed = 0; phaseDuration = duration; entityData.set(PHASE, phase); entityData.set(REMAINING, duration); entityData.set(DURATION, duration);
        if (phase == 2) {
            if (task() == Task.STRING || task() == Task.BIND) playOriginal("hierophant_green_tentacles");
            if (isSplash(task())) playOriginal("hierophant_green_emerald_splash");
        }
    }
    @Override public boolean beginAttack(boolean shifted) { return owner() != null && HierophantGreenActions.dispatch(owner(), shifted ? HierophantGreenActions.BIND : HierophantGreenActions.ATTACK); }
    @Override public boolean beginHeld(boolean guard) { return guard && owner() != null && HierophantGreenActions.dispatch(owner(), HierophantGreenActions.BLOCK); }
    @Override public boolean beginSpecial(int phase) { return false; } // HG has no SP/TW ordinal specials.
    @Override public boolean beginUppercut() { return false; } // No source HG finisher action.
    @Override public void refreshHeld() { heldLease = 12; }
    @Override public void stopHeld() { if (isHeld(task())) stopCurrent(false); if (owner() != null) StandMovement.update(owner()); }
    private void stopCurrent(boolean runQueued) {
        if (task() != Task.NONE) { previousBlockedDamage = blockedDamage > 0 ? previousBlockedDamage + blockedDamage : 0; blockedDamage = 0; }
        if (grapple != null) { grapple.discard(); grapple = null; }
        taskReset();
        if (runQueued && queued != null && owner() != null) {
            var next = queued; queued = null; HierophantGreenActions.dispatch(owner(), next);
        }
    }
    private void taskReset() { entityData.set(TASK, 0); phase(0, 0); stringTarget = null; stringTargetPoint = null; barrierTarget = null; }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        super.tick(); if (!(level() instanceof ServerLevel)) return;
        var user = owner();
        if (user == null || !user.isAlive() || user.isSpectator() || !user.getAttachedOrCreate(StandAttachments.PROGRESS).standId().equals(HierophantGreenActions.STAND_ID)) { discard(); return; }
        barriers.tick(); entityData.set(BARRIERS, barriers.getSize());
        if (queued != null && canCancelFor(HierophantGreenActions.task(queued))) {
            var next = queued; queued = null; HierophantGreenActions.dispatch(user, next);
        }
        if (guardBreak > 0) { guardBreak--; entityData.set(PHASE, 6); entityData.set(REMAINING, guardBreak); }
        else if (taskPhase() == 6) phase(0, 0);
        updatePosition(user);
        if (summonLockTicks() > 0) entityData.set(SUMMON_LOCK, summonLockTicks() - 1);
        else if (!VampireCombatService.isStunned(this)) {
            // A replacement action is installed after the registry releases its previous hold. Start
            // the automatic arms fade only once that complete transition leaves the Stand idle.
            if (task() == Task.NONE && isArmsOnlyMode() && unsummonTicks() < 0) { entityData.set(UNSUMMON, 0); playOriginal("hierophant_green_unsummon"); }
            if (unsummonTicks() >= 0 && task() == Task.NONE) {
                if (unsummonTicks() >= getUnsummonDuration()) { StandService.dismiss(user); return; }
                entityData.set(UNSUMMON, unsummonTicks() + 1);
            } else if (task() != Task.NONE) tickTask(user);
            if (gradualSummonTicks() > 0) entityData.set(GRADUAL_SUMMON, gradualSummonTicks() - 1);
        }
        setYBodyRot(getYRot()); setYHeadRot(getYRot()); setDeltaMovement(Vec3.ZERO);
    }
    private void tickTask(ServerPlayer user) {
        Task task = task();
        if (isHeld(task) && (--heldLease < 0 || !HierophantGreenActions.canPay(user, isGrapple(task) ? 1 : 0))) { stopCurrent(true); return; }
        var definition = StandActionRegistry.definition(HierophantGreenActions.id(task)).orElseThrow();
        if (!StandActionRegistry.partsAvailable(user, definition)) { stopCurrent(false); queued = null; return; }
        if (taskPhase() == 2) {
            if (elapsed == 0) {
                switch (task) {
                    case STRING, BIND -> { fireStrings(task == Task.BIND); HierophantGreenActions.consumeStamina(user, 75); }
                    case GRAPPLE, GRAPPLE_ENTITY -> { grapple = new HGGrapplingStringEntity(this, task == Task.GRAPPLE_ENTITY); level().addFreshEntity(grapple); }
                    case BARRIER -> { if (barrierTarget != null) attachBarrier(barrierTarget); }
                    default -> { }
                }
            }
            if (isSplash(task)) { fireSplash(task == Task.CONCENTRATED); HierophantGreenActions.consumeStamina(user, task == Task.CONCENTRATED ? 6 : 3); }
            else if (isGrapple(task)) HierophantGreenActions.consumeStamina(user, 1);
        }
        elapsed++;
        entityData.set(REMAINING, Math.max(0, phaseDuration - elapsed));
        if (elapsed >= phaseDuration && !isHeld(task)) {
            if (taskPhase() == 1) phase(2, performDuration);
            else if (taskPhase() == 2 && recoveryDuration > 0) phase(3, recoveryDuration);
            else stopCurrent(true);
        }
    }
    private void fireStrings(boolean binding) {
        int ring = binding ? 3 : 7;
        for (int i = 0; i <= ring; i++) {
            double angle = (double) i / ring * Math.PI * 2;
            // Original MathUtil.xRotYRotOffsets(angle,10): radius1 at depth10, not a ten-degree cone.
            double x2 = Math.cos(angle) * Math.cos(angle), y2 = Math.sin(angle) * Math.sin(angle);
            float yaw = i == ring ? 0 : (float) Math.toDegrees(Math.acos(Math.sqrt((x2 + 100) / (x2 + y2 + 100)))) * (angle > Math.PI ? -1 : 1);
            float pitch = i == ring ? 0 : (float) Math.toDegrees(Math.acos(Math.sqrt((y2 + 100) / (x2 + y2 + 100)))) * (angle > Math.PI / 2 && angle < Math.PI * 1.5 ? -1 : 1);
            var string = new HGStringEntity(this, yaw, pitch, binding, performDuration);
            if (!binding) string.addKnockback(guardCounter());
            addBodyProjectile(string);
        }
    }
    public void addBodyProjectile(OwnedBodyPartEntity projectile) {
        projectile.setDamageFactor(projectileDamageFactor()); projectile.setSpeedFactor((float) attackSpeed() / 8); level().addFreshEntity(projectile);
    }
    private void fireSplash(boolean concentrated) {
        int count = HierophantGreenActions.fractionCount(random, projectileFireRate() * (concentrated ? 2.5 : 1));
        for (int i = 0; i < count; i++) {
            var emerald = new HGEmeraldEntity(this, true); emerald.setBreakBlocks(concentrated); emerald.setLowerKnockback(!concentrated);
            Vec3 direction = getLookAngle(); emerald.shoot(direction.x, direction.y, direction.z, concentrated ? 3 : 2, projectileInaccuracy(concentrated ? 1 : 8));
            emerald.setDamageFactor(projectileDamageFactor()); level().addFreshEntity(emerald);
        }
        if (getPlacedBarriersCount() > 0) {
            HitResult target = aim(100);
            if (target.getType() != HitResult.Type.MISS) barriers.shootEmeraldsFromBarriers(this, target.getLocation(), elapsed,
                    Math.min(getPlacedBarriersCount() / 5, 9) * staminaCondition(), 3, 8, true);
        }
    }
    private Vec3 followingOffset(ServerPlayer user) {
        if (isArmsOnlyMode()) return new Vec3(0, 0, .15).yRot(-(float) Math.toRadians(user.getYRot()));
        boolean front = task() == Task.BLOCK || isSplash(task()) && taskPhase() == 2;
        return new Vec3(front ? 0 : -.75, task() == Task.NONE && user.isShiftKeyDown() ? 0 : .2,
                front ? task() == Task.BLOCK ? .3 : .5 : -.75).yRot(-(float) Math.toRadians(user.getYRot()));
    }
    private void updatePosition(ServerPlayer user) {
        if (manuallyControlled() && (--controlLease <= 0 || bodyAnchor == null || user.position().distanceToSqr(bodyAnchor) > 4 || user.isPassenger())) stopControl();
        if (manuallyControlled()) {
            user.setPos(bodyAnchor); user.setDeltaMovement(Vec3.ZERO);
            float actionSpeed = task() == Task.NONE || taskPhase() == 3 ? 1 : task() == Task.BLOCK ? .3F : isGrapple(task()) ? 0 : .5F;
            Vec3 movement = controlInput.scale(StandStatFormulas.getMovementSpeed(definition().stats().speed(development())) * actionSpeed);
            Vec3 desired = position().add(movement), relative = desired.subtract(bodyAnchor);
            if (relative.lengthSqr() > 10000) desired = bodyAnchor.add(relative.normalize().scale(100));
            if (level().hasChunkAt(BlockPos.containing(desired))) { noPhysics = false; move(MoverType.SELF, desired.subtract(position())); }
            setRot(controlYaw, controlPitch);
        } else if (retracting) {
            noPhysics = true;
            if (distanceToSqr(user) < 4) {
                retracting = false; fastRetraction = false;
                if (stringToUser != null && stringToUser == stringFromStand) { stringToUser.discard(); stringToUser = null; stringFromStand = null; }
            } else {
                Vec3 target = user.position().add(followingOffset(user));
                double speed = StandStatFormulas.getMovementSpeed(definition().stats().speed(development())) * (fastRetraction ? 3 : 1);
                setPos(position().add(target.subtract(position()).normalize().scale(Math.min(speed, position().distanceTo(target)))));
            }
        } else { noPhysics = true; setPos(user.position().add(followingOffset(user))); setRot(user.getYRot(), user.getXRot()); }
        if (stringTarget != null && stringTarget.isAlive() && stringTarget.level() == level()) lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, stringTarget.getBoundingBox().getCenter());
        else if (stringTargetPoint != null) lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, stringTargetPoint);
    }
    @Override public boolean startControl() {
        var user = owner(); if (user == null || !user.isAlive() || user.isSpectator() || manuallyControlled() || guardBreak > 0) return false;
        bodyAnchor = user.position(); controlYaw = user.getYRot(); controlPitch = user.getXRot(); controlInput = Vec3.ZERO; controlLease = 12;
        retracting = false; fastRetraction = false; entityData.set(CONTROLLED, true); createUserString(); return true;
    }
    @Override public void control(float sideways, float forward, float vertical, float yaw, float pitch) {
        if (!manuallyControlled() || !Float.isFinite(sideways) || !Float.isFinite(forward) || !Float.isFinite(vertical)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(sideways) > 1 || Math.abs(forward) > 1 || Math.abs(vertical) > 1) return;
        controlYaw = net.minecraft.util.Mth.wrapDegrees(yaw); controlPitch = Math.clamp(pitch, -90, 90);
        Vec3 direction = Vec3.directionFromRotation(0, controlYaw);
        double sneak = vertical < 0 ? .5 : 1;
        controlInput = direction.scale(forward * sneak).add(new Vec3(direction.z, 0, -direction.x).scale(sideways * sneak)).add(0, vertical, 0);
        if (controlInput.lengthSqr() > 1) controlInput = controlInput.normalize(); controlLease = 12;
    }
    @Override public void stopControl() {
        boolean wasControlled = manuallyControlled(); entityData.set(CONTROLLED, false); bodyAnchor = null; controlInput = Vec3.ZERO; controlLease = 0;
        if (wasControlled && user() != null) {
            retracting = distanceToSqr(user()) >= 4;
            fastRetraction = stringToUser != null && stringToUser.isAlive() && stringToUser == stringFromStand;
            if (!retracting && fastRetraction) { stringToUser.discard(); stringToUser = null; stringFromStand = null; fastRetraction = false; }
        }
    }
    public HGBarriersNet getBarriersNet() { return barriers; }
    public int getPlacedBarriersCount() { return entityData.get(BARRIERS); }
    public int getMaxBarriersPlaceable() { return owner() != null && ProgressionService.unlocked(owner(), 4) ? 100 : 15; }
    public boolean canPlaceBarrier() { return getPlacedBarriersCount() < getMaxBarriersPlaceable(); }
    public boolean hasBarrierAttached() { return getPlacedBarriersCount() > 0 || stringFromStand != null && stringFromStand.isAlive() && stringFromStand != stringToUser; }
    public void createUserString() {
        if (level().isClientSide() || stringToUser != null && stringToUser.isAlive()) return;
        stringToUser = new HGBarrierEntity(this);
        if (stringFromStand != null && stringFromStand.isAlive()) stringFromStand.discard();
        stringFromStand = stringToUser; level().addFreshEntity(stringFromStand);
    }
    public boolean attachBarrier(BlockPos pos) {
        if (level().isClientSide() || !canPlaceBarrier() || owner() == null || !level().hasChunkAt(pos)) return false;
        if (stringFromStand != null && stringFromStand.isAlive()) {
            if (pos.equals(stringFromStand.getOriginBlockPos())) return false;
            // Source snapshots firing points before the newly attached endpoint moves on its next entity tick.
            stringFromStand.attachToBlock(pos);
            barriers.add(stringFromStand); entityData.set(BARRIERS, barriers.getSize());
        }
        stringFromStand = new HGBarrierEntity(this); stringFromStand.setOriginBlockPos(pos); level().addFreshEntity(stringFromStand);
        playOriginal("hierophant_green_barrier_placed"); return true;
    }
    @Override public float protectOwner(DamageSource source, float damage) {
        var user = owner(); if (user == null || damage <= 0) return damage;
        Vec3 origin = source.getSourcePosition();
        if (task() == Task.BLOCK && source.getDirectEntity() != null && origin != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && origin.subtract(transferringDamage ? position() : user.position()).normalize().dot(user.getLookAngle()) > .5) {
            var progress = user.getAttachedOrCreate(StandAttachments.PROGRESS);
            var result = com.github.standobyte.jojo.core.HeldCombat.guard(damage, progress.stamina(), durability(), attackDamage(), StandConfig.staminaFree(user), StandConfig.server().standResistanceMultiplier());
            if (!StandConfig.staminaFree(user)) user.setAttached(StandAttachments.PROGRESS, progress.withStamina(result.stamina()));
            blockedDamage += damage;
            if (result.broken()) { stopCurrent(false); guardBreak = StandStatFormulas.getBlockingBreakTicks(durability()); entityData.set(DURATION, guardBreak); }
            return result.damage();
        }
        if (transferringDamage && source.getDirectEntity() != null && !source.is(DamageTypeTags.BYPASSES_ARMOR))
            return damage * (1 - StandStatFormulas.getPhysicalResistance(durability(), attackDamage(), 0, damage, StandConfig.server().standResistanceMultiplier()));
        return damage;
    }
    @Override public boolean hurtServer(ServerLevel server, DamageSource source, float amount) {
        var user = owner(); if (user == null || !StandDamage.canDamageBody(this,source)) return false;
        transferringDamage = true; try { return user.hurtServer(server, source, amount); } finally { transferringDamage = false; }
    }
    @Override public void playOriginal(String name) { playOriginalAt(name, position()); }
    public void playOriginalAt(String name, Vec3 pos) { level().playSound(null, pos.x, pos.y, pos.z, JojoSounds.get(name), SoundSource.PLAYERS, 1, 1); }
    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            queued = null; barriers.clear();
            if (stringToUser != null) stringToUser.discard(); if (stringFromStand != null) stringFromStand.discard(); if (grapple != null) grapple.discard();
            // Source bodies are session entities; even long strings outside the tracking range must disappear on dismissal.
            if (level() instanceof ServerLevel server) for (Entity entity : server.getAllEntities())
                if (entity instanceof OwnedBodyPartEntity body && body.livingOwner() == this || entity instanceof HGEmeraldEntity emerald && emerald.getOwner() == this) entity.discard();
        }
        super.remove(reason);
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canCollideWith(Entity other) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
