package com.github.standobyte.jojo.fabric.stand.silverchariot;

import com.github.standobyte.jojo.core.*;
import com.github.standobyte.jojo.fabric.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.*;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Independent source Silver Chariot manifestation: rapier/armor alter stats, task cadence and damage defenses. */
public final class SilverChariotEntity extends Mob implements StandManifestation {
    public enum Kind { NONE, LIGHT, UNARMED, DASH, SWEEP, BARRAGE, BLOCK, RAPIER_LAUNCH, REMOVE_ARMOR }
    /** New source damaging projectiles expose the original deflection/fire contract here. */
    public interface DeflectableProjectile extends PillarmanBaseActions.EvasionProjectile {
        boolean canBeDeflected(Entity context);
        boolean isFiery();
        void setDeflected();
    }
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            KIND = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT), PHASE = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            TICKS = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT), DURATION = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            ARMOR_TICKS = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            SUMMON_MODE = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            SUMMON_VARIANT = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            SUMMON_LOCK = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            SUMMON_WEAKNESS = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            FADE_TOTAL = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            UNSUMMON = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT),
            VISUAL_AGE = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RAPIER = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.BOOLEAN),
            ARMOR = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.BOOLEAN), FIRE = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.BOOLEAN),
            CONTROL = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.BOOLEAN),
            LEFT_PUNCH = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.BOOLEAN),
            SUMMON_ANIMATION = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> FINISHER = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.FLOAT),
            VISUAL_SPEED = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.FLOAT),
            VISUAL_PRECISION = SynchedEntityData.defineId(SilverChariotEntity.class, EntityDataSerializers.FLOAT);
    private UUID ownerUuid;
    private int phaseTicks, duration, recovery, fireTicks, heldLease, controlLease, finisherDecay, delayedHits;
    private float finisher, heavyFinisher, guardDamage;
    private boolean firstPerform, previousLight, transferringDamage;
    private boolean nextOffHand, additionalOffHand;
    private int lastSwingTick = -2;
    private Vec3 anchor, controlInput = Vec3.ZERO, dashMotion = Vec3.ZERO;
    private float controlYaw, controlPitch;
    private final Map<UUID, Integer> heavyHitCooldowns = new HashMap<>();
    private static final Map<LivingEntity, SuspendedTarget> suspendedTargets = new IdentityHashMap<>();
    private record SuspendedTarget(boolean gravityWasDisabled, int ticks) { }
    private float attributesDevelopment = Float.NaN;
    private boolean attributesRapier, attributesArmor;

    public SilverChariotEntity(EntityType<? extends SilverChariotEntity> type, Level level) {
        super(type, level); setNoAi(true); setNoGravity(true); noPhysics = true;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.ATTACK_SPEED, 14)
                .add(Attributes.MOVEMENT_SPEED, .8).add(Attributes.ARMOR, 20).add(Attributes.ARMOR_TOUGHNESS, 12);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b); b.define(OWNER, -1); b.define(KIND, 0); b.define(PHASE, 0); b.define(TICKS, 0); b.define(DURATION, 0);
        b.define(ARMOR_TICKS, 0); b.define(RAPIER, true); b.define(ARMOR, true); b.define(FIRE, false); b.define(CONTROL, false); b.define(FINISHER, 0F);
        b.define(SUMMON_MODE, 0); b.define(SUMMON_VARIANT, 0); b.define(SUMMON_LOCK, 3); b.define(SUMMON_WEAKNESS, 0);
        b.define(FADE_TOTAL, 3); b.define(UNSUMMON, -1); b.define(LEFT_PUNCH, false);
        b.define(VISUAL_SPEED, 14F); b.define(VISUAL_PRECISION, 14F);
        b.define(VISUAL_AGE, 0); b.define(SUMMON_ANIMATION, true);
    }
    @Override public Mob entity() { return this; }
    @Override public StandDefinitions.Definition definition() { return StandDefinitions.definition(SilverChariotActions.ID); }
    @Override public void setOwner(ServerPlayer player) {
        ownerUuid = player.getUUID(); entityData.set(OWNER, player.getId()); setPos(player.position()); setRot(player.getYRot(), player.getXRot());
        entityData.set(SUMMON_VARIANT, random.nextInt(128)); entityData.set(LEFT_PUNCH, player.getMainArm() == HumanoidArm.LEFT); updateAttributes();
    }
    @Override public ServerPlayer owner() { return ownerUuid != null && level().getEntity(ownerUuid) instanceof ServerPlayer player ? player : null; }
    @Override public int ownerId() { return entityData.get(OWNER); }
    public boolean hasRapier() { return entityData.get(RAPIER); }
    public boolean hasArmor() { return entityData.get(ARMOR); }
    public boolean isRapierOnFire() { return entityData.get(FIRE); }
    public int getTicksAfterArmorRemoval() { return entityData.get(ARMOR_TICKS); }
    public Kind attackKind() { return Kind.values()[entityData.get(KIND)]; }
    public int summonMode() { return entityData.get(SUMMON_MODE); }
    public int summonVariant() { return entityData.get(SUMMON_VARIANT); }
    public boolean leftPunch() { return entityData.get(LEFT_PUNCH); }
    public float visualAttackSpeed() { return entityData.get(VISUAL_SPEED); }
    public float visualPrecision() { return entityData.get(VISUAL_PRECISION); }
    public int visualAge() { return entityData.get(VISUAL_AGE); }
    public boolean summonAnimation() { return entityData.get(SUMMON_ANIMATION); }
    public boolean isArmsOnlyMode() { return (summonMode() & 1) != 0; }
    private boolean armsWeakened() { return isArmsOnlyMode() || entityData.get(SUMMON_WEAKNESS) > 0; }
    @Override public HumanoidArm getMainArm() {
        var entity = level().getEntity(ownerId()); return entity instanceof LivingEntity living ? living.getMainArm() : HumanoidArm.RIGHT;
    }
    /** Source onClick creates partial arms only for a new summon; a full active Stand stays full. */
    public void prepareActionSummon(Kind kind, boolean fresh) {
        int requested = switch (kind) { case LIGHT, UNARMED -> 7; case BARRAGE, BLOCK -> 15; default -> 0; };
        if (fresh && requested != 0) setArmsMode(requested);
        else if (isArmsOnlyMode()) {
            if (requested == 0) {
                entityData.set(SUMMON_MODE, 2);
                int ticks = Math.max(20 - (int)(attackSpeed() * 1.25), 0);
                entityData.set(SUMMON_WEAKNESS, ticks); entityData.set(FADE_TOTAL, ticks); entityData.set(UNSUMMON, -1);
                attributesDevelopment = Float.NaN; updateAttributes();
            } else setArmsMode(summonMode() | requested);
        }
    }
    private void setArmsMode(int mode) {
        entityData.set(SUMMON_MODE, mode); entityData.set(SUMMON_LOCK, 0);
        entityData.set(SUMMON_ANIMATION, false);
        attributesDevelopment = Float.NaN; updateAttributes();
    }
    public float renderAlpha() {
        int ticks = Math.max(entityData.get(SUMMON_LOCK), entityData.get(SUMMON_WEAKNESS)), total = entityData.get(FADE_TOTAL);
        float alpha = ticks > 0 && total > 0 ? 1F - ticks / (float)total : 1;
        int unsummon = entityData.get(UNSUMMON); if (unsummon >= 0) alpha *= 1F - unsummon / (float)Math.max(1, total);
        return Mth.clamp(alpha, 0, 1);
    }
    private int unsummonDuration() {
        var player = owner(); if (player != null && StandAttachments.resolve(player).isBurst()) return 3;
        double condition = (efficiency() * 2 + 1) / 3;
        return condition < 1 ? Mth.ceil(7 / condition) : 7;
    }
    @Override public int actionPhase() { return entityData.get(PHASE); }
    @Override public int actionTicks() { return entityData.get(TICKS); }
    @Override public int actionDuration() { return entityData.get(DURATION); }
    @Override public int abilityKind() { return attackKind().ordinal(); }
    @Override public boolean heavyAttack() { return attackKind() == Kind.DASH || attackKind() == Kind.SWEEP; }
    @Override public float finisherCharge() { return entityData.get(FINISHER); }
    @Override public int uiRecoveryTicks() { return actionPhase() == 3 || actionPhase() == 6 ? Math.max(0, duration - phaseTicks) : 0; }
    @Override public boolean allowsStaminaRegen() { return actionPhase() == 0 || actionPhase() == 3; }
    public void setRapier(boolean value) { entityData.set(RAPIER, value); if (!value) entityData.set(FIRE, false); updateAttributes(); }
    public void setArmor(boolean value) { entityData.set(ARMOR, value); if (value) entityData.set(ARMOR_TICKS, 0); updateAttributes(); }
    public void removeRapierFire() { fireTicks = 0; entityData.set(FIRE, false); }
    private void modifier(AttributeInstance attribute, String id, double value, AttributeModifier.Operation operation, boolean active) {
        Identifier key = RipplesFabric.id("silver_chariot_" + id); attribute.removeModifier(key);
        if (active) attribute.addTransientModifier(new AttributeModifier(key, value, operation));
    }
    private void updateAttributes() {
        if (level().isClientSide()) return;
        var player = owner(); float development = player == null ? 0 : player.getAttachedOrCreate(StandAttachments.PROGRESS).development();
        if (development == attributesDevelopment && attributesRapier == hasRapier() && attributesArmor == hasArmor()) return;
        attributesDevelopment = development; attributesRapier = hasRapier(); attributesArmor = hasArmor();
        var stats = definition().stats();
        modifier(getAttribute(Attributes.ATTACK_DAMAGE), "development_power", stats.power(development) - stats.power(), AttributeModifier.Operation.ADD_VALUE, true);
        modifier(getAttribute(Attributes.ATTACK_SPEED), "development_speed", stats.speed(development) - stats.speed(), AttributeModifier.Operation.ADD_VALUE, true);
        modifier(getAttribute(Attributes.MOVEMENT_SPEED), "development_movement", (stats.speed(development) - stats.speed()) * .05, AttributeModifier.Operation.ADD_VALUE, true);
        modifier(getAttribute(Attributes.ATTACK_DAMAGE), "no_rapier_damage", -.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, !hasRapier());
        modifier(getAttribute(Attributes.ATTACK_SPEED), "no_rapier_speed", -.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, !hasRapier());
        modifier(getAttribute(Attributes.ATTACK_SPEED), "no_armor_speed", .5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, !hasArmor());
        modifier(getAttribute(Attributes.MOVEMENT_SPEED), "no_armor_movement", 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, !hasArmor());
        modifier(getAttribute(Attributes.ARMOR), "no_armor", -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, !hasArmor());
        modifier(getAttribute(Attributes.ARMOR_TOUGHNESS), "no_toughness", -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, !hasArmor());
        modifier(getAttribute(Attributes.ATTACK_DAMAGE), "arms_only", -.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, armsWeakened());
    }
    private float development() { var player = owner(); return player == null ? 0 : player.getAttachedOrCreate(StandAttachments.PROGRESS).development(); }
    private double efficiency() {
        var player = owner(); if (player == null) return 1;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        return StandConfig.staminaFree(player) || StandAttachments.resolve(player).isBurst() ? 1 : .25 + Math.min(progress.stamina() / progress.maxStamina() * 1.5, .75);
    }
    public double attackDamage() { return getAttributeValue(Attributes.ATTACK_DAMAGE) * efficiency(); }
    public double attackSpeed() { return getAttributeValue(Attributes.ATTACK_SPEED) * efficiency(); }
    public double precision() { return armsWeakened() ? 0 : definition().stats().precision(development()) * efficiency(); }
    public double durability() { return hasArmor() ? definition().stats().durability(development()) * efficiency() * (armsWeakened() ? .75 : 1) : 0; }
    public double attackRange() { return hasRapier() ? 3.5 : 2.5; }
    private boolean live() {
        var player = owner(); return player != null && player.level() == level() && player.isAlive() && !player.isSpectator()
                && player.getAttachedOrCreate(StandAttachments.PROGRESS).standId().equals(SilverChariotActions.ID) && !TimeStopService.isFrozen(this);
    }
    public boolean canUse(Kind kind) {
        if (!live() || actionPhase() == 6 || entityData.get(UNSUMMON) >= 0 && isArmsOnlyMode()
                || kind != Kind.RAPIER_LAUNCH && VampireCombatService.isStunned(this)) return false;
        if (kind == Kind.REMOVE_ARMOR && (!hasArmor() || !StandParts.has(owner(), StandParts.Part.MAIN_BODY))) return false;
        if (kind == Kind.UNARMED && hasRapier()) return false;
        if (kind != Kind.NONE && kind != Kind.UNARMED && kind != Kind.BLOCK && kind != Kind.REMOVE_ARMOR && !hasRapier()) return false;
        if (attackKind() == kind && (kind == Kind.BARRAGE || kind == Kind.BLOCK)) return true;
        if (actionPhase() == 0 || actionPhase() == 3) return true;
        return attackKind() == Kind.BARRAGE && (kind == Kind.DASH || kind == Kind.SWEEP);
    }
    public boolean start(Kind kind) {
        if (kind == Kind.NONE || !canUse(kind)) return false;
        if (attackKind() == kind && (kind == Kind.BARRAGE || kind == Kind.BLOCK)) { refreshHeld(); return true; }
        updateAttributes(); heavyHitCooldowns.clear(); phaseTicks = 0; recovery = 0; firstPerform = true;
        entityData.set(SUMMON_ANIMATION, false);
        entityData.set(KIND, kind.ordinal());
        switch (kind) {
            case LIGHT, UNARMED -> {
                if (isArmsOnlyMode() && entityData.get(LEFT_PUNCH) == (getMainArm() == HumanoidArm.RIGHT)) setArmsMode(15);
                boolean off;
                if (tickCount - lastSwingTick > 1) { nextOffHand = !nextOffHand; off = nextOffHand; }
                else { off = additionalOffHand; additionalOffHand = !additionalOffHand; }
                lastSwingTick = tickCount; entityData.set(LEFT_PUNCH, off == (getMainArm() == HumanoidArm.RIGHT));
                int windup = StandStatFormulas.getLightAttackWindup(attackSpeed(), finisher, Math.min(guardDamage / 5, 1), !previousLight, random::nextDouble);
                int perform = StandStatFormulas.getLightAttackRecovery(attackSpeed(), finisher);
                recovery = perform * (isArmsOnlyMode() ? 2 : 4); duration = Math.max(1, windup); entityData.set(PHASE, windup > 0 ? 1 : 2);
                if (windup == 0) duration = perform;
            }
            case DASH, SWEEP -> {
                heavyFinisher = finisher; finisher = Math.max(0, finisher - .51F);
                recovery = Math.max(0, StandStatFormulas.getHeavyAttackRecovery(attackSpeed(), heavyFinisher));
                duration = kind == Kind.DASH ? Math.max(StandStatFormulas.getHeavyAttackWindup(attackSpeed(), heavyFinisher), 2)
                        : Math.max(StandStatFormulas.getHeavyAttackWindup(attackSpeed(), heavyFinisher) - 1, 1);
                entityData.set(PHASE, kind == Kind.DASH ? 2 : 1);
                if (kind == Kind.DASH) {
                    if (!manuallyControlled() && attackSpeed() < 24) setPos(owner().position().add(Vec3.directionFromRotation(0, owner().getYRot())));
                    dashMotion = getLookAngle().scale(10D / duration); playOriginal("silver_chariot_dash");
                }
            }
            case BARRAGE -> {
                boolean unlimited = !hasArmor() || StandAttachments.resolve(owner()).isBurst();
                duration = unlimited ? Integer.MAX_VALUE : StandStatFormulas.getBarrageMaxDuration(durability());
                entityData.set(PHASE, 4); heldLease = 12; delayedHits = 0;
                playOriginal(!hasArmor() && getTicksAfterArmorRemoval() < 40 ? "polnareff_fencing" : "polnareff_hora_hora_hora");
            }
            case BLOCK -> { guardDamage = 0; duration = Integer.MAX_VALUE; entityData.set(PHASE, 5); heldLease = 12; }
            default -> { duration = 1; entityData.set(PHASE, 2); }
        }
        previousLight = kind == Kind.LIGHT || kind == Kind.UNARMED; sync(); return true;
    }
    @Override public boolean beginAttack(boolean heavy) { return start(heavy ? finisher >= .5F && ProgressionService.unlocked(owner(), 1) ? Kind.SWEEP : Kind.DASH : hasRapier() ? Kind.LIGHT : Kind.UNARMED); }
    @Override public boolean beginHeld(boolean guard) { return start(guard ? Kind.BLOCK : Kind.BARRAGE); }
    @Override public boolean beginSpecial(int phase) { return phase == Kind.RAPIER_LAUNCH.ordinal() ? start(Kind.RAPIER_LAUNCH) : phase == Kind.REMOVE_ARMOR.ordinal() && start(Kind.REMOVE_ARMOR); }
    @Override public boolean beginUppercut() { return start(Kind.SWEEP); }
    @Override public void refreshHeld() { if (actionPhase() == 4 || actionPhase() == 5) heldLease = 12; }
    @Override public void stopHeld() {
        if (actionPhase() == 4) { recovery = Math.max(0, StandStatFormulas.getBarrageRecovery(attackSpeed())); recover(); }
        else if (actionPhase() == 5) idle();
    }
    private void sync() {
        entityData.set(TICKS, phaseTicks); entityData.set(DURATION, duration); entityData.set(FINISHER, finisher);
        entityData.set(VISUAL_SPEED, (float)attackSpeed()); entityData.set(VISUAL_PRECISION, (float)precision());
    }
    private void idle() {
        if (attackKind() != Kind.BLOCK) guardDamage = 0;
        entityData.set(PHASE, 0); entityData.set(KIND, 0); duration = 0; phaseTicks = 0; previousLight = false;
        if (isArmsOnlyMode() && entityData.get(UNSUMMON) < 0) { entityData.set(FADE_TOTAL, unsummonDuration()); entityData.set(UNSUMMON, 0); playOriginal("silver_chariot_unsummon"); }
        sync();
    }
    private void recover() { if (recovery <= 0) { idle(); return; } entityData.set(PHASE, 3); duration = recovery; phaseTicks = 0; sync(); }
    @Override public float userWalkSpeed() {
        float factor = switch (actionPhase()) { case 4 -> .15F; case 5 -> .3F; case 1, 2 -> previousLight ? 1 : .5F; default -> 1; };
        var player = owner(); if (player != null && ProgressionService.unlocked(player, 4)) factor += (1 - factor) * (hasArmor() ? .5F : 1);
        return factor;
    }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        super.tick(); if (!(level() instanceof ServerLevel)) return;
        entityData.set(VISUAL_AGE, tickCount); if (tickCount > 20) entityData.set(SUMMON_ANIMATION, false);
        if (!live()) { discard(); return; }
        updateAttributes(); if (!hasArmor()) entityData.set(ARMOR_TICKS, Math.min(Integer.MAX_VALUE - 1, getTicksAfterArmorRemoval()) + 1);
        if (fireTicks > 0 && --fireTicks == 0) entityData.set(FIRE, false);
        for (var rapier : level().getEntitiesOfClass(SilverChariotRapierEntity.class, getBoundingBox())) rapier.takeRapier(this);
        if (manuallyControlled() && (--controlLease <= 0 || owner().position().distanceToSqr(anchor) > 4 || owner().isPassenger())) stopControl();
        positionFromOwner();
        int unsummon = entityData.get(UNSUMMON);
        if (unsummon >= 0) {
            if (unsummon >= unsummonDuration()) { discard(); return; }
            entityData.set(UNSUMMON, unsummon + 1); return;
        }
        int lock = entityData.get(SUMMON_LOCK);
        if (lock > 0) { entityData.set(SUMMON_LOCK, lock - 1); return; }
        int weakness = entityData.get(SUMMON_WEAKNESS);
        if (weakness > 0 && !VampireCombatService.isStunned(this)) {
            entityData.set(SUMMON_WEAKNESS, weakness - 1);
            if (weakness == 1) { attributesDevelopment = Float.NaN; updateAttributes(); }
        }
        heavyHitCooldowns.replaceAll((id, ticks) -> ticks - 1); heavyHitCooldowns.values().removeIf(ticks -> ticks <= 0);
        if (VampireCombatService.isStunned(this) && attackKind() != Kind.RAPIER_LAUNCH) { stopHeld(); if (actionPhase() != 6) idle(); return; }
        int phase = actionPhase(); Kind kind = attackKind();
        if (phase == 1) {
            if (++phaseTicks >= duration) {
                entityData.set(PHASE, 2); phaseTicks = 0; firstPerform = true;
                duration = kind == Kind.SWEEP ? 3 : StandStatFormulas.getLightAttackRecovery(attackSpeed(), finisher);
                if (kind == Kind.SWEEP) {
                    playOriginal("silver_chariot_sweep_heavy");
                    ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, getX(), getY(.5), getZ(), 1, 0, 0, 0, 0);
                }
            }
        } else if (phase == 2) {
            perform(kind); firstPerform = false;
            if (actionPhase() == 2 && ++phaseTicks >= duration) recover();
        } else if (phase == 4) {
            if (--heldLease < 0 || ++phaseTicks >= duration || !hasRapier()) stopHeld();
            else { barrageTick(); if (!spend(4)) stopHeld(); }
        } else if (phase == 5) { if (--heldLease < 0) stopHeld(); else phaseTicks++; }
        else if (phase == 3 || phase == 6) { if (++phaseTicks >= duration) idle(); }
        if (actionPhase() == 0) {
            if (finisherDecay > 0) finisherDecay--; else finisher = Math.max(0, finisher - (finisher < .5F ? .0125F : .025F));
        }
        sync();
    }
    private void perform(Kind kind) {
        if (kind == Kind.DASH) {
            if (firstPerform) { attackTarget(StandStatFormulas.getHeavyAttackDamage(attackDamage()), true, 1); spend(50); }
            deflectApproaching();
            boolean forward = (phaseTicks + 1F) / duration <= .5F;
            if (forward) {
                var targets = rayTargets(attackRange(), .25);
                for (var target : targets) hit(target, StandStatFormulas.getHeavyAttackDamage(attackDamage()), true, 1);
                if (targets.isEmpty()) breakTargetBlock();
                setPos(position().add(dashMotion));
            } else if (phaseTicks == (int) (duration * .5F)) { var target = firstTarget(); if (target != null) hit(target, StandStatFormulas.getHeavyAttackDamage(attackDamage()), true, 1); }
            return;
        }
        if (!firstPerform) return;
        switch (kind) {
            case LIGHT, UNARMED -> {
                addFinisher(.1F); attackTarget(StandStatFormulas.getLightAttackDamage(attackDamage()), false, 1);
                playOriginal(kind == Kind.LIGHT ? "silver_chariot_sweep_light" : "stand_punch_swing");
                spend(10);
            }
            case SWEEP -> {
                deflectApproaching();
                for (var target : level().getEntities(this, getBoundingBox().inflate(attackRange(), 0, attackRange()), this::canHarm)) {
                    double cosine = getLookAngle().dot(target.position().subtract(position()).normalize());
                    if (cosine > -.5) hit(target, StandStatFormulas.getHeavyAttackDamage(attackDamage()) * (cosine < .5 ? .5F : 1), true, 1);
                }
                spend(50);
                if (isRapierOnFire()) { var flame = new SilverChariotFlameSwingEntity(this); level().addFreshEntity(flame); removeRapierFire(); }
            }
            case RAPIER_LAUNCH -> {
                var rapier = new SilverChariotRapierEntity(this); level().addFreshEntity(rapier);
                if (!owner().isCreative()) setRapier(false); playOriginal("silver_chariot_rapier_shot");
                if (!owner().isCreative() && !StandAttachments.resolve(owner()).isBurst()) AbilityCooldowns.start(owner(), SilverChariotActions.LAUNCH, 100);
            }
            case REMOVE_ARMOR -> {
                setArmor(false); entityData.set(ARMOR_TICKS, 0); playOriginal("silver_chariot_armor_off");
                com.github.standobyte.jojo.fabric.stand.StandAfterimageEntity.addAfterimages(this, 10, -1);
            }
            default -> { }
        }
    }
    private boolean spend(float amount) {
        var player = owner(); if (player == null) return false;
        if (StandConfig.staminaFree(player) || amount <= 0) return true;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        player.setAttached(StandAttachments.PROGRESS, progress.withStamina(Math.max(0, progress.stamina() - amount)));
        return progress.stamina() >= amount || StandAttachments.resolve(player).isBurst();
    }
    private void barrageTick() {
        int rate = StandStatFormulas.getBarrageHitsPerSecond(attackSpeed()), hits = rate / 20; rate %= 20;
        if (delayedHits > 0) { hits++; delayedHits--; }
        else if (rate > 0) {
            double gap = 20D / rate; int ticksGap = (int) gap;
            if ((duration - phaseTicks + delayedHits) % ticksGap == 0) { if (random.nextDouble() < gap - ticksGap) delayedHits++; else hits++; }
        }
        if (hits > 0) {
            attackTarget(StandStatFormulas.getBarrageHitDamage(attackDamage(), precision()) * hits, false, hits);
            level().playSound(null, blockPosition(), JojoSounds.get("silver_chariot_barrage_swipe"), SoundSource.PLAYERS, .25F, .9F + random.nextFloat() * .2F);
        }
    }
    private void attackTarget(float damage, boolean heavy, int hits) {
        deflectApproaching(); var target = firstTarget();
        if (target != null) hit(target, damage, heavy, hits); else breakTargetBlock();
    }
    private Entity firstTarget() { var targets = rayTargets(attackRange(), 0); return targets.isEmpty() ? null : targets.getFirst(); }
    private List<Entity> rayTargets(double range, double inflate) {
        Vec3 start = getEyePosition(), end = start.add(getLookAngle().scale(range));
        var targets = new ArrayList<Entity>();
        for (var target : level().getEntities(this, getBoundingBox().expandTowards(end.subtract(start)).inflate(1), this::canHarm)) {
            var box = target.getBoundingBox().inflate(target.getPickRadius() + inflate);
            if (precision() > 4) {
                double scale = precision() / 5 + .2;
                box = box.inflate(Math.min((scale - 1) * box.getXsize(), 4) / 2,
                        Math.min((scale - 1) * box.getYsize(), 4) / 2, Math.min((scale - 1) * box.getZsize(), 4) / 2);
            }
            var hit = box.contains(start) ? Optional.of(start) : box.clip(start, end);
            if (hit.isPresent()) targets.add(target);
        }
        targets.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(start))); return targets;
    }
    public boolean canHarm(Entity target) {
        if (target == this || target == owner() || !target.isAlive() || target.isSpectator() || !target.isPickable()) return false;
        if (target instanceof StandManifestation stand && stand.ownerId() == ownerId()) return false;
        if (target instanceof Player player && owner() != null && !owner().canHarmPlayer(player)) return false;
        return !(target instanceof LivingEntity living) || owner() == null || owner().canAttack(living);
    }
    private void hit(Entity target, float damage, boolean heavy, int hits) {
        if (target instanceof Projectile projectile && hasRapier() && canDeflect() && projectile.getOwner() != owner() && deflectProjectile(projectile)) return;
        if (heavy && heavyHitCooldowns.containsKey(target.getUUID())) return;
        if (attackKind() == Kind.BARRAGE && target instanceof AbstractSkeleton) damage *= .75F;
        damage *= (float)StandConfig.server().standPowerMultiplier();
        var server = (ServerLevel) level(); var source = StandDamage.marked(damageSources().mobAttack(this),attackKind()==Kind.BARRAGE?hits:0);
        int oldFire = target.getRemainingFireTicks(); Vec3 oldMotion = target.getDeltaMovement();
        if (hasRapier() && isRapierOnFire()) target.igniteForSeconds(4);
        boolean hurt = target instanceof LivingEntity living ? StandDamage.hurt(server, living, source, damage) : target.hurtServer(server, source, damage);
        if (!hurt) target.setRemainingFireTicks(oldFire);
        if (attackKind() == Kind.LIGHT || attackKind() == Kind.UNARMED) addFinisher(.1F);
        else if (attackKind() == Kind.BARRAGE) addFinisher(.005F * hits);
        if (heavy) heavyHitCooldowns.put(target.getUUID(), 10);
        if (attackKind() == Kind.BARRAGE) {
            boolean burst = StandAttachments.resolve(owner()).isBurst();
            double knockback = burst || target instanceof StandManifestation ? 0 : Math.clamp(attackDamage() * .0075, 0, 1);
            target.setDeltaMovement(oldMotion.add(target.getDeltaMovement().subtract(oldMotion).scale(knockback)));
            if (hurt && burst && target instanceof LivingEntity living) {
                var previous = suspendedTargets.get(living);
                suspendedTargets.put(living, new SuspendedTarget(previous == null ? living.isNoGravity() : previous.gravityWasDisabled(), 3)); living.setNoGravity(true);
                if (living instanceof Mob mob) mob.getNavigation().stop();
            }
        }
        if (hurt && target instanceof LivingEntity living) {
            if (heavy || (attackKind() == Kind.LIGHT || attackKind() == Kind.UNARMED) && guardDamage > 0) {
                Vec3 delta = target.position().subtract(position()); double angle = -Math.atan2(delta.x, delta.z);
                double strength = !heavy ? Math.min(guardDamage / 5, 1) * .5 : attackKind() == Kind.SWEEP ? .5 : attackSpeed() < 24 ? .75 : .125;
                if (attackKind() == Kind.DASH && attackSpeed() >= 24) living.setDeltaMovement(living.getDeltaMovement().add(0, strength * 2, 0));
                else {
                    if (attackKind() == Kind.DASH) angle += Math.toRadians((60 + random.nextFloat() * 30) * (Mth.wrapDegrees(getYRot() - (float) Math.toDegrees(angle)) < 0 ? 1 : -1));
                    living.knockback(strength, Math.sin(angle), -Math.cos(angle), source, damage);
                    living.knockback(strength, Math.sin(angle), -Math.cos(angle), source, damage);
                }
                living.syncVelocity = true;
            }
            if (attackKind() == Kind.UNARMED) playOriginal("stand_punch_light");
        }
    }
    private void addFinisher(float amount) { finisher = Math.min(1, finisher + amount); finisherDecay = 40; }
    public static void tickSuspendedTargets(ServerLevel level) {
        var iterator = suspendedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); var target = entry.getKey(); var state = entry.getValue();
            if (target.level() != level) continue;
            if (!target.isAlive() || state.ticks() <= 1) { target.setNoGravity(state.gravityWasDisabled()); iterator.remove(); }
            else if (!TimeStopService.isFrozen(target)) entry.setValue(new SuspendedTarget(state.gravityWasDisabled(), state.ticks() - 1));
        }
    }
    public static void clearRuntime() {
        suspendedTargets.forEach((target, state) -> target.setNoGravity(state.gravityWasDisabled())); suspendedTargets.clear();
    }
    private void breakTargetBlock() {
        var player = owner(); if (player == null || !((ServerLevel) level()).getGameRules().get(JojoGameRules.BREAK_BLOCKS)) return;
        var hit = level().clip(new ClipContext(getEyePosition(), getEyePosition().add(getLookAngle().scale(attackRange())), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        var pos = hit.getBlockPos(); var state = level().getBlockState(pos);
        float hardness = StandStatFormulas.getStandBreakBlockHardness(state.getDestroySpeed(level(), pos), state.requiresCorrectToolForDrops());
        int harvest = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? 3 : state.is(BlockTags.NEEDS_IRON_TOOL) ? 2 : state.is(BlockTags.NEEDS_STONE_TOOL) ? 1 : 0;
        boolean breakable = hardness >= 0 && (hasRapier() ? hardness <= 1 && harvest <= 0 : StandStatFormulas.isBlockBreakable(attackDamage(), hardness, harvest));
        if (breakable && player.mayBuild() && level().mayInteract(player, pos)
                && net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(player.level(), player, pos, state, level().getBlockEntity(pos)))
            level().destroyBlock(pos, !player.isCreative(), this);
    }
    private boolean canDeflect() { return owner() == null || ProgressionService.unlocked(owner(), 4); }
    private void deflectApproaching() {
        if (!canDeflect()) return;
        double cosine = Math.cos(Math.toRadians(30 + Math.clamp(precision(), 0, 16) * 30 / 16));
        for (var projectile : level().getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(attackRange()), e -> e.isAlive() && !e.isPickable()))
            if (getLookAngle().dot(projectile.getDeltaMovement().reverse().normalize()) >= cosine) deflectProjectile(projectile);
    }
    public boolean deflectProjectile(Projectile projectile) {
        if (!canDeflect() || projectile == null) return false;
        if (projectile instanceof DeflectableProjectile source && !source.canBeDeflected(this)) return false;
        if (projectile instanceof com.github.standobyte.jojo.fabric.pillarman.PillarSandstormEntity) return false;
        if (projectile instanceof com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity body && !body.canBeDeflected(this)) return false;
        projectile.setDeltaMovement(getLookAngle().normalize().scale(projectile.getDeltaMovement().length()));
        projectile.move(MoverType.SELF, projectile.getDeltaMovement()); projectile.syncVelocity = true;
        if (projectile instanceof DeflectableProjectile source) { source.setDeflected(); if (source.isFiery()) { fireTicks = 300; entityData.set(FIRE, true); } }
        if (projectile instanceof com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity body) body.setDeflected();
        return true;
    }
    @Override public float protectOwner(DamageSource source, float damage) {
        var player = owner(); if (player == null || damage <= 0 || actionPhase() != 5 || !canBlockSource(source)) return damage;
        Vec3 sourcePos = source.getSourcePosition(); if (sourcePos == null || sourcePos.subtract(transferringDamage ? position() : player.position()).normalize().dot(getLookAngle()) <= .5) return damage;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS); float cost = StandStatFormulas.getBlockStaminaCost(damage);
        float blocked = StandConfig.staminaFree(player) || cost == 0 ? 1 : Math.min(progress.stamina() / cost, 1);
        if (!StandConfig.staminaFree(player)) player.setAttached(StandAttachments.PROGRESS, progress.withStamina(Math.max(0, progress.stamina() - cost)));
        playOriginal(hasRapier() ? "silver_chariot_block" : "stand_damage_block");
        if (blocked < 1) { entityData.set(PHASE, 6); duration = StandStatFormulas.getBlockingBreakTicks(durability()); phaseTicks = 0; sync(); }
        float remaining = damage * (1 - StandStatFormulas.getPhysicalResistance(0, 0, blocked, damage, StandConfig.server().standResistanceMultiplier())); guardDamage += remaining; return remaining;
    }
    private boolean canBlockSource(DamageSource source) {
        return source.getDirectEntity() != null && !source.is(DamageTypeTags.BYPASSES_ARMOR) && !VampireCombatService.isStunned(this);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        var player = owner(); if (player == null || !StandDamage.canDamageBody(this,source)) return false;
        return super.hurtServer(level, source, damage);
    }
    @Override protected void actuallyHurt(ServerLevel level, DamageSource source, float damage) {
        var player = owner(); if (player == null || !player.isAlive()) return;
        Vec3 sourcePos = source.getSourcePosition();
        if (actionPhase() == 0 && !manuallyControlled() && canBlockSource(source)
                && sourcePos != null && sourcePos.subtract(position()).normalize().dot(getLookAngle()) > .5 && start(Kind.BLOCK)) heldLease = 4;
        damage = getDamageAfterArmorAbsorb(source, damage); damage = getDamageAfterMagicAbsorb(source, damage);
        var resistance = player.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
        if (resistance != null && !source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE))
            damage = Math.max(0, damage * (25 - (resistance.getAmplifier() + 1) * 5) / 25);
        transferringDamage = true;
        try {
            if (canBlockSource(source)) {
                boolean guarding = actionPhase() == 5 && sourcePos != null && sourcePos.subtract(position()).normalize().dot(getLookAngle()) > .5;
                damage = guarding ? protectOwner(source, damage) : damage * (1 - StandStatFormulas.getPhysicalResistance(0, 0, 0, damage, StandConfig.server().standResistanceMultiplier()));
            }
            float remaining = Math.max(0, damage - getAbsorptionAmount()); setAbsorptionAmount(Math.max(0, getAbsorptionAmount() - damage));
            if (remaining > 0) StandDamage.hurt(level, player, new LinkedDamage(source), remaining);
        } finally { transferringDamage = false; }
    }
    /** Source StandLinkDamageSource: armor/magic already resolved on the Stand; source position is absent. */
    private static final class LinkedDamage extends DamageSource {
        private final DamageSource original;
        private LinkedDamage(DamageSource original) { super(original.typeHolder(), original.getDirectEntity(), original.getEntity()); this.original = original; }
        @Override public boolean is(TagKey<DamageType> tag) {
            return tag.equals(DamageTypeTags.BYPASSES_ARMOR) || tag.equals(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                    || tag.equals(DamageTypeTags.BYPASSES_EFFECTS) || tag.equals(DamageTypeTags.BYPASSES_RESISTANCE);
        }
        @Override public Vec3 getSourcePosition() { return null; }
        @Override public float getFoodExhaustion() { return .1F; }
        @Override public boolean scalesWithDifficulty() { return false; }
        @Override public net.minecraft.network.chat.Component getLocalizedDeathMessage(LivingEntity entity) { return original.getLocalizedDeathMessage(entity); }
    }
    @Override public boolean manuallyControlled() { return entityData.get(CONTROL); }
    @Override public Vec3 bodyAnchor() { return anchor; }
    @Override public void translateControlAnchor(Vec3 movement) { if (anchor != null && manuallyControlled()) anchor = anchor.add(movement); }
    @Override public boolean startControl() {
        if (!live() || owner().isPassenger() || !StandParts.has(owner(), StandParts.Part.MAIN_BODY)) return false;
        anchor = owner().position(); controlYaw = owner().getYRot(); controlPitch = owner().getXRot(); controlLease = 12; entityData.set(CONTROL, true); return true;
    }
    @Override public void control(float sideways, float forward, float vertical, float yaw, float pitch) {
        if (!manuallyControlled() || !live() || !Float.isFinite(sideways) || !Float.isFinite(forward) || !Float.isFinite(vertical) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        controlYaw = yaw; controlPitch = Math.clamp(pitch, -90, 90);
        Vec3 facing = Vec3.directionFromRotation(0, yaw), right = new Vec3(-facing.z, 0, facing.x);
        controlInput = facing.scale(Math.clamp(forward, -1, 1)).add(right.scale(Math.clamp(sideways, -1, 1))).add(0, Math.clamp(vertical, -1, 1), 0);
        if (controlInput.lengthSqr() > 1) controlInput = controlInput.normalize(); controlLease = 12;
    }
    @Override public void stopControl() { entityData.set(CONTROL, false); anchor = null; controlInput = Vec3.ZERO; }
    private void positionFromOwner() {
        var player = owner();
        if (manuallyControlled()) {
            player.setPos(anchor); player.setDeltaMovement(Vec3.ZERO);
            Vec3 desired = position().add(controlInput.scale(getAttributeValue(Attributes.MOVEMENT_SPEED)));
            Vec3 relative = desired.subtract(anchor); if (relative.lengthSqr() > 100) desired = anchor.add(relative.normalize().scale(10));
            if (level().hasChunkAt(BlockPos.containing(desired))) setPos(desired); setRot(controlYaw, controlPitch);
        } else if (!(attackKind() == Kind.DASH && actionPhase() == 2)) {
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot()), side = new Vec3(-forward.z, 0, forward.x);
            Vec3 offset = isArmsOnlyMode() ? new Vec3(0, .15, 0) : actionPhase() == 0 ? forward.scale(-.6).add(side.scale(.8)) : forward.scale(actionPhase() == 5 ? .3 : attackKind() == Kind.RAPIER_LAUNCH ? .25 : 1);
            setPos(player.position().add(offset)); setRot(player.getYRot(), player.getXRot());
        }
        setYBodyRot(getYRot()); setYHeadRot(getYRot()); setDeltaMovement(Vec3.ZERO);
    }
    @Override public void playOriginal(String sound) { level().playSound(null, getX(), getY(), getZ(), JojoSounds.get(sound), SoundSource.PLAYERS, 1, 1); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canCollideWith(Entity entity) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public net.minecraft.world.scores.PlayerTeam getTeam() { var player = owner(); return player == null ? super.getTeam() : player.getTeam(); }
}
