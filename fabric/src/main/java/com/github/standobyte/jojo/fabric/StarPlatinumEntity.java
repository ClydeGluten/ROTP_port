package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.AttackTimeline;
import com.github.standobyte.jojo.core.HeldCombat;
import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.core.StandStatFormulas;
import com.github.standobyte.jojo.core.StandStats;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import java.util.UUID;

/** First migrated manifestation: following, server-authoritative light/heavy punches and damage transfer. */
public final class StarPlatinumEntity extends Mob {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(StarPlatinumEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(StarPlatinumEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HEAVY = SynchedEntityData.defineId(StarPlatinumEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PHASE_TICKS = SynchedEntityData.defineId(StarPlatinumEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_DURATION = SynchedEntityData.defineId(StarPlatinumEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ABILITY = SynchedEntityData.defineId(StarPlatinumEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CONTROLLED = SynchedEntityData.defineId(StarPlatinumEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> FINISHER = SynchedEntityData.defineId(StarPlatinumEntity.class, EntityDataSerializers.FLOAT);
    public static final int PHASE_FINGER = 7, PHASE_INHALE = 8, PHASE_ZOOM = 9, PHASE_TIME_CHARGE = 10;
    private int special, specialElapsed, specialLease;
    private final java.util.Set<Integer> fingerHits = new java.util.HashSet<>();
    private int controlLease;
    private Vec3 controlInput = Vec3.ZERO;
    private Vec3 bodyAnchor;
    private float controlYaw, controlPitch;
    private final AttackTimeline action = new AttackTimeline();
    private final HeldCombat held = new HeldCombat();
    public static final int PHASE_BARRAGE = 4;
    public static final int PHASE_GUARD = 5;
    public static final int PHASE_GUARD_BROKEN = 6;
    private static final ResourceKey<DamageType> BARRAGE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RipplesFabric.id("stand_barrage"));
    private boolean transferringDamage;
    private UUID ownerUuid;
    private float finisher;
    private float heavyFinisher;
    private int finisherDecay;
    private boolean previousLight;

    public StarPlatinumEntity(EntityType<? extends StarPlatinumEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setNoAi(true);
        setPersistenceRequired();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, -1);
        builder.define(ABILITY, 0);
        builder.define(CONTROLLED, false);
        builder.define(FINISHER, 0F);
        builder.define(PHASE, 0);
        builder.define(HEAVY, false);
        builder.define(PHASE_TICKS, 0);
        builder.define(PHASE_DURATION, 0);
    }

    public void setOwner(ServerPlayer player) {
        ownerUuid = player.getUUID();
        entityData.set(OWNER, player.getId());
        setPos(player.position());
        setYRot(player.getYRot());
    }

    public ServerPlayer owner() {
        return ownerUuid != null && level().getEntity(ownerUuid) instanceof ServerPlayer player ? player : null;
    }

    public int ownerId() { return entityData.get(OWNER); }
    public int actionPhase() { return entityData.get(PHASE); }
    public boolean heavyAttack() { return entityData.get(HEAVY); }
    public float finisherCharge() { return entityData.get(FINISHER); }
    public int actionTicks() { return entityData.get(PHASE_TICKS); }
    public int actionDuration() { return entityData.get(PHASE_DURATION); }
    public int abilityKind() { return entityData.get(ABILITY); }
    public boolean manuallyControlled() { return entityData.get(CONTROLLED); }
    public Vec3 bodyAnchor() { return bodyAnchor; }
    public boolean allowsStaminaRegen() { return action.phase() == AttackTimeline.Phase.IDLE && held.mode() == HeldCombat.Mode.NONE && special == 0; }
    private boolean available() { return special == 0 && action.canStart() && held.canStart(); }

    /**
     * Read-only port of {@code StandEntity.getUserWalkSpeed()}: the owner's walk-speed multiplier for the current action.
     * Light punches, recovery, idle and guard-break are 1; manual control locks the body instead of slowing it.
     */
    public float userWalkSpeed() {
        if (manuallyControlled()) return 1F;
        if (special != 0) return StandMovement.ACTION_FACTOR;
        if (held.mode() == HeldCombat.Mode.BARRAGE) return StandMovement.BARRAGE_FACTOR;
        if (held.mode() == HeldCombat.Mode.GUARD) return StandMovement.BLOCK_FACTOR;
        AttackTimeline.Phase phase = action.phase();
        if (phase != AttackTimeline.Phase.WINDUP && phase != AttackTimeline.Phase.PERFORM) return 1F;
        return action.heavy() ? StandMovement.ACTION_FACTOR : StandMovement.LIGHT_FACTOR;
    }

    public boolean beginSpecial(int phase) {
        ServerPlayer player = owner();
        if (player == null || !player.isAlive() || player.isSpectator() || !available()
                || !player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum()
                || (phase != PHASE_FINGER && phase != PHASE_INHALE && phase != PHASE_ZOOM)
                || AbilityCooldowns.remaining(player, phase) > 0) return false;
        int required = phase == PHASE_FINGER ? 3 : phase == PHASE_INHALE ? 2 : 0;
        if (!ProgressionService.require(player, required, phase == PHASE_FINGER ? "key.jojo.fabric.finger" : "key.jojo.fabric.inhale")) return false;
        float cost = phase == PHASE_FINGER ? 375 : phase == PHASE_INHALE ? 2 : 0;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (!player.isCreative() && progress.stamina() < cost) return false;
        if (phase == PHASE_FINGER) {
            if (!player.isCreative()) player.setAttached(StandAttachments.PROGRESS, progress.withStamina(progress.stamina() - cost));
            AbilityCooldowns.start(player, phase, ProgressionService.state(player).isBurst() ? 20 : 80);
        }
        action.cancel(); special = phase; specialElapsed = 0; specialLease = 12;
        fingerHits.clear(); entityData.set(ABILITY, 0);
        playOriginal(phase == PHASE_FINGER ? "star_platinum_star_finger" : phase == PHASE_INHALE ? "star_platinum_inhale" : "star_platinum_zoom");
        syncAction();
        return true;
    }

    public boolean beginUppercut() {
        if (owner() == null || !ProgressionService.require(owner(), 1, "key.jojo.fabric.uppercut")) return false;
        if (!beginAttack(true)) return false;
        entityData.set(ABILITY, 1);
        return true;
    }

    public boolean startControl() {
        ServerPlayer player = owner();
        if (player == null || !player.isAlive() || player.isSpectator() || manuallyControlled() || !available()) return false;
        bodyAnchor = player.position(); controlYaw = player.getYRot(); controlPitch = player.getXRot();
        controlInput = Vec3.ZERO; controlLease = 12; entityData.set(CONTROLLED, true);
        return true;
    }

    public void control(float sideways, float forward, float vertical, float yaw, float pitch) {
        if (!manuallyControlled() || !Float.isFinite(sideways) || !Float.isFinite(forward) || !Float.isFinite(vertical)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(sideways) > 1 || Math.abs(forward) > 1 || Math.abs(vertical) > 1) return;
        controlYaw = net.minecraft.util.Mth.wrapDegrees(yaw); controlPitch = net.minecraft.util.Mth.clamp(pitch, -90, 90);
        Vec3 direction = Vec3.directionFromRotation(0, controlYaw);
        controlInput = direction.scale(forward).add(new Vec3(direction.z, 0, -direction.x).scale(sideways)).add(0, vertical, 0);
        if (controlInput.lengthSqr() > 1) controlInput = controlInput.normalize();
        controlLease = 12;
    }

    public void stopControl() {
        controlInput = Vec3.ZERO; controlLease = 0; bodyAnchor = null; entityData.set(CONTROLLED, false);
    }

    private void endSpecial() {
        if (special == PHASE_INHALE && owner() != null) AbilityCooldowns.start(owner(), special, Math.max(1, specialElapsed));
        special = 0; specialElapsed = 0;
    }

    public boolean beginHeld(boolean guard) {
        ServerPlayer player = owner();
        if (player == null || !player.isAlive() || player.isSpectator() || !available()) return false;
        StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (!progress.hasStarPlatinum() || (!player.isCreative() && progress.stamina() < (guard ? 1 : HeldCombat.BARRAGE_STAMINA_PER_TICK))) return false;
        double durability = StandStats.STAR_PLATINUM.durability(progress.development());
        double speed = StandStats.STAR_PLATINUM.speed(progress.development());
        boolean started = held.start(guard ? HeldCombat.Mode.GUARD : HeldCombat.Mode.BARRAGE,
                guard ? Integer.MAX_VALUE : StandStatFormulas.getBarrageMaxDuration(durability),
                guard ? 0 : Math.max(0, StandStatFormulas.getBarrageRecovery(speed)));
        if (started) {
            action.cancel();
            previousLight = false;
            syncAction();
        }
        return started;
    }

    public void stopHeld() { held.stop(); if (special == PHASE_INHALE || special == PHASE_ZOOM) endSpecial(); syncAction(); }
    public void refreshHeld() { held.refresh(); if (special == PHASE_INHALE || special == PHASE_ZOOM) specialLease = 12; }

    public boolean beginAttack(boolean heavy) {
        ServerPlayer player = owner();
        if (player == null || !player.isAlive() || player.isSpectator() || !available()) return false;
        StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (!progress.hasStarPlatinum()) return false;
        float cost = heavy ? 50F : 10F;
        if (!player.isCreative() && progress.stamina() < cost) {
            player.setAttached(StandAttachments.PROGRESS, progress.withStamina(0));
            return false;
        }
        double speed = StandStats.STAR_PLATINUM.speed(progress.development());
        int windup = heavy ? StandStatFormulas.getHeavyAttackWindup(speed, finisher)
                : StandStatFormulas.getLightAttackWindup(speed, finisher, 0, !previousLight);
        int perform = heavy ? 1 : StandStatFormulas.getLightAttackRecovery(speed, finisher);
        int recovery = heavy ? StandStatFormulas.getHeavyAttackRecovery(speed, finisher) : perform * 4;
        if (!action.start(heavy, windup, perform, recovery)) return false;
        if (!player.isCreative()) player.setAttached(StandAttachments.PROGRESS, progress.withStamina(progress.stamina() - cost));
        previousLight = !heavy;
        entityData.set(HEAVY, heavy);
        entityData.set(ABILITY, heavy && finisher >= 0.5F && ProgressionService.unlocked(player, 1) ? 1 : 0);
        if (heavy) {
            heavyFinisher = finisher;
            finisher = Math.max(0, finisher - 0.51F);
        }
        entityData.set(FINISHER, finisher);
        syncAction();
        playOriginal(heavy ? "star_platinum_ora_long" : "star_platinum_ora");
        return true;
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) return;
        ServerPlayer player = owner();
        if (player == null || !player.isAlive() || player.isSpectator()
                || !player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum()) {
            discard();
            return;
        }
        if (manuallyControlled() && (--controlLease <= 0 || player.position().distanceToSqr(bodyAnchor) > 4 || player.isPassenger())) stopControl();
        if (manuallyControlled()) {
            player.setPos(bodyAnchor); player.setDeltaMovement(Vec3.ZERO);
            Vec3 desired = position().add(controlInput.scale(0.3));
            Vec3 relative = desired.subtract(bodyAnchor);
            double range = StandStats.STAR_PLATINUM.maxRange();
            if (relative.lengthSqr() > range * range) desired = bodyAnchor.add(relative.normalize().scale(range));
            if (level().hasChunkAt(net.minecraft.core.BlockPos.containing(desired))) setPos(desired);
            setYRot(controlYaw); setXRot(controlPitch);
        } else {
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            boolean attacking = action.phase() != AttackTimeline.Phase.IDLE || held.mode() != HeldCombat.Mode.NONE || special != 0 || TimeStopService.isCharging(player);
            Vec3 offset = attacking ? forward.scale(0.8) : forward.scale(-0.6).add(side.scale(0.8));
            setPos(player.position().add(offset).add(0, 0.05, 0));
            setYRot(player.getYRot()); setXRot(player.getXRot());
        }
        setYBodyRot(getYRot()); setYHeadRot(getYRot());
        setDeltaMovement(Vec3.ZERO);
        tickSpecial(player);
        if (action.tick()) strike(player);
        tickHeld(player);
        syncAction();
        if (held.mode() != HeldCombat.Mode.BARRAGE && action.phase() == AttackTimeline.Phase.IDLE) {
            if (finisherDecay > 0) finisherDecay--;
            else finisher = Math.max(0, finisher - (finisher < 0.5F ? 0.0125F : 0.025F));
        }
        entityData.set(FINISHER, finisher);
        if (action.phase() == AttackTimeline.Phase.IDLE) previousLight = false;
    }

    private void syncAction() {
        ServerPlayer chargingOwner = owner();
        if (chargingOwner != null && TimeStopService.isCharging(chargingOwner)) {
            entityData.set(PHASE, PHASE_TIME_CHARGE); entityData.set(PHASE_DURATION, 40);
            entityData.set(PHASE_TICKS, TimeStopService.chargeTicksRemaining(chargingOwner));
            return;
        }
        if (special != 0) {
            int duration = special == PHASE_FINGER ? 20 : special == PHASE_INHALE ? 80 : 0;
            entityData.set(PHASE, special); entityData.set(PHASE_DURATION, duration);
            entityData.set(PHASE_TICKS, duration - specialElapsed); entityData.set(HEAVY, false);
            return;
        }
        if (held.guardBreakTicks() > 0) {
            entityData.set(PHASE, PHASE_GUARD_BROKEN);
            entityData.set(PHASE_TICKS, held.guardBreakTicks());
            entityData.set(PHASE_DURATION, held.guardBreakDuration());
            return;
        }
        if (held.mode() != HeldCombat.Mode.NONE) {
            entityData.set(PHASE, held.mode() == HeldCombat.Mode.BARRAGE ? PHASE_BARRAGE : PHASE_GUARD);
            int duration = held.mode() == HeldCombat.Mode.BARRAGE ? held.limit() : 0;
            entityData.set(PHASE_TICKS, duration - held.elapsed());
            entityData.set(PHASE_DURATION, duration);
            entityData.set(HEAVY, false);
            return;
        }
        entityData.set(PHASE, action.phase().ordinal());
        entityData.set(PHASE_TICKS, action.ticksRemaining());
        entityData.set(PHASE_DURATION, action.phaseDuration());
    }

    private LivingEntity findTarget(ServerPlayer player) {
        return findTarget(player, attackOrigin(player), attackDirection(player), StandStats.STAR_PLATINUM.effectiveRange());
    }

    private Vec3 attackOrigin(ServerPlayer player) { return manuallyControlled() ? getEyePosition() : player.getEyePosition(); }
    private Vec3 attackDirection(ServerPlayer player) { return manuallyControlled() ? getLookAngle() : player.getLookAngle(); }
    private LivingEntity findTarget(ServerPlayer player, Vec3 start, Vec3 direction, double range) {
        Vec3 end = start.add(direction.scale(range));
        HitResult wall = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearest = wall.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) : start.distanceToSqr(wall.getLocation());
        LivingEntity target = null;
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(start, end).inflate(1),
                entity -> entity != player && !(entity instanceof StarPlatinumEntity) && entity.isAlive() && !entity.isSpectator())) {
            var hit = candidate.getBoundingBox().inflate(0.15).clip(start, end);
            double distance = candidate.getBoundingBox().contains(start) ? 0 : hit.map(start::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
            if (distance < nearest
                    && (!(candidate instanceof ServerPlayer other) || player.canHarmPlayer(other))) {
                target = candidate;
                nearest = distance;
            }
        }
        return target;
    }

    private void strike(ServerPlayer player) {
        LivingEntity target = findTarget(player);
        if (target != null) {
            StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
            double power = StandStats.STAR_PLATINUM.power(progress.development());
            float damage = action.heavy() ? StandStatFormulas.getHeavyAttackDamage(power) : StandStatFormulas.getLightAttackDamage(power);
            DamageSource source = new DamageSource(damageSources().playerAttack(player).typeHolder(), this, player);
            if (abilityKind() == 1 && target.isBlocking()) {
                if (target instanceof net.minecraft.world.entity.player.Player blockingPlayer) blockingPlayer.getCooldowns().addCooldown(target.getUseItem(), 100);
                target.stopUsingItem();
            }
            boolean hit = target.hurtServer((ServerLevel) level(), source, damage);
            if (hit && !action.heavy()) finisher = Math.min(1, finisher + 0.1F);
            if (hit && action.heavy()) {
                Vec3 direction = attackDirection(player);
                target.knockback(abilityKind() == 1 ? 0.5 : 0.6, -direction.x, -direction.z, source, damage);
                if (abilityKind() == 1) {
                    Vec3 movement = target.getDeltaMovement();
                    target.setDeltaMovement(movement.x, Math.max(movement.y, 0.65 + power / 16 * heavyFinisher * 0.5), movement.z);
                    target.syncVelocity = true;
                }
            }
        }
        playOriginal(action.heavy() ? "star_platinum_punch_heavy" : "star_platinum_punch_light");
        if (!action.heavy()) finisher = Math.min(1, finisher + 0.1F);
        finisherDecay = 40;
    }

    private void tickSpecial(ServerPlayer player) {
        if (special == 0) return;
        specialElapsed++;
        if (special == PHASE_FINGER) {
            double reach = specialElapsed <= 12 ? specialElapsed * 0.3 : specialElapsed <= 16 ? 3.6 : Math.max(0, 3.6 - (specialElapsed - 16) * 0.9);
            Vec3 origin = getEyePosition().add(0, -0.2, 0);
            LivingEntity target = findTarget(player, origin, attackDirection(player), reach);
            if (target != null && fingerHits.add(target.getId())) {
                target.hurtServer((ServerLevel) level(), new DamageSource(damageSources().playerAttack(player).typeHolder(), this, player), 4.5F);
                playOriginal("star_platinum_punch_light");
            }
            if (specialElapsed >= 20) endSpecial();
            return;
        }
        if (--specialLease <= 0 || (special == PHASE_INHALE && specialElapsed > 80)) { endSpecial(); return; }
        if (special == PHASE_ZOOM) return;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (!player.isCreative()) {
            if (progress.stamina() < 2) { endSpecial(); return; }
            player.setAttached(StandAttachments.PROGRESS, progress.withStamina(progress.stamina() - 2));
        }
        Vec3 mouth = position().add(0, getBbHeight() * 0.75, 0).add(getLookAngle().scale(0.35));
        for (Entity target : level().getEntities(this, getBoundingBox().inflate(12), e -> e != player && !(e instanceof StarPlatinumEntity) && !e.isSpectator())) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(mouth);
            double distance = delta.length();
            if (distance <= 0.7 || distance > 12 || getLookAngle().dot(delta.normalize()) <= 0.886
                    || (target instanceof ServerPlayer other && !player.canHarmPlayer(other))) continue;
            if (level().clip(new ClipContext(mouth, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
            Vec3 pull = delta.normalize().scale(-0.5);
            target.setDeltaMovement(distance > 2 ? target.getDeltaMovement().add(pull.scale(1 / distance)) : pull.scale(Math.max(distance - 1, 0)));
            target.syncVelocity = true;
            if (distance < 4 && target instanceof LivingEntity living && living.getAirSupply() > -20) living.setAirSupply(living.getAirSupply() - 5);
        }
        if (specialElapsed % 2 == 0) {
            Vec3 particle = mouth.add(getLookAngle().scale(4));
            ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, particle.x, particle.y, particle.z, 2, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private void tickHeld(ServerPlayer player) {
        StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (held.mode() == HeldCombat.Mode.BARRAGE && !player.isCreative()
                && progress.stamina() < HeldCombat.BARRAGE_STAMINA_PER_TICK) held.stop();
        int swings = held.tick(StandStatFormulas.getBarrageHitsPerSecond(StandStats.STAR_PLATINUM.speed(progress.development())));
        if (held.mode() != HeldCombat.Mode.BARRAGE) return;
        if (!player.isCreative()) {
            player.setAttached(StandAttachments.PROGRESS, progress.withStamina(progress.stamina() - HeldCombat.BARRAGE_STAMINA_PER_TICK));
        }
        if (held.elapsed() % 10 == 1) playOriginal("star_platinum_ora");
        LivingEntity target = swings > 0 ? findTarget(player) : null;
        if (target == null) return;
        float damage = swings * StandStatFormulas.getBarrageHitDamage(StandStats.STAR_PLATINUM.power(progress.development()),
                StandStats.STAR_PLATINUM.precision());
        DamageSource source = new DamageSource(level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BARRAGE_DAMAGE), this, player);
        Vec3 motion = target.getDeltaMovement();
        if (target.hurtServer((ServerLevel) level(), source, damage)) {
            // Upstream groups multiple swings into one hit per tick; preserve position during the rush.
            target.setDeltaMovement(motion);
            finisher = Math.min(1, finisher + swings * 0.005F);
            finisherDecay = 40;
            if (held.elapsed() % 2 == 1) playOriginal("star_platinum_punch_barrage");
        }
    }

    private boolean canBlock(DamageSource source, Vec3 origin, Vec3 view) {
        Vec3 position = source.getSourcePosition();
        return source.getDirectEntity() != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && position != null
                && position.subtract(origin).normalize().dot(view) > 0.5;
    }

    public float protectOwner(DamageSource source, float damage) {
        ServerPlayer player = owner();
        if (player == null || damage <= 0) return damage;
        if (held.mode() == HeldCombat.Mode.GUARD
                && canBlock(source, transferringDamage ? position() : player.position(), player.getLookAngle())) {
            return applyGuard(player, damage);
        }
        if (transferringDamage && source.getDirectEntity() != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
            double durability = StandStats.STAR_PLATINUM.durability(progress.development());
            double power = StandStats.STAR_PLATINUM.power(progress.development());
            return damage * (1 - StandStatFormulas.getPhysicalResistance(durability, power, 0, damage, 1));
        }
        return damage;
    }

    private float applyGuard(ServerPlayer player, float damage) {
        StandProgress progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        double durability = StandStats.STAR_PLATINUM.durability(progress.development());
        HeldCombat.GuardResult result = HeldCombat.guard(damage, progress.stamina(), durability,
                StandStats.STAR_PLATINUM.power(progress.development()), player.isCreative());
        if (!player.isCreative()) player.setAttached(StandAttachments.PROGRESS, progress.withStamina(result.stamina()));
        if (result.broken()) {
            held.breakGuard(StandStatFormulas.getBlockingBreakTicks(durability));
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("jojo.fabric.guard_broken"));
        }
        syncAction();
        return result.damage();
    }

    public void playOriginal(String name) {
        level().playSound(null, getX(), getY(), getZ(), JojoSounds.get(name), SoundSource.PLAYERS, 1, 1);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        ServerPlayer player = owner();
        if (player == null || source.getEntity() == player || source.getEntity() == this) return false;
        // The player's accepted-hit hook applies resistance once. Rejected cooldown hits must not spend guard stamina.
        transferringDamage = true;
        try { return player.hurtServer(level, source, amount); }
        finally { transferringDamage = false; }
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean canCollideWith(Entity other) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
