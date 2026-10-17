package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.core.*;
import com.github.standobyte.jojo.fabric.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.*;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Crazy Diamond's source common Stand task behavior, with recovery-owned modifiers and partial arms. */
public final class CrazyDiamondEntity extends Mob implements RestorationSelection.Stand {
    public enum Kind { NONE, LIGHT, HEAVY, FINISHER, BARRAGE, BLOCK, BLOCK_BULLET, BLOOD_CUTTER, ANCHOR_MAKE }
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT),
            KIND = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT), PHASE = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT),
            TICKS = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT), DURATION = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT),
            SUMMON_MODE = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT), SUMMON_VARIANT = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT),
            SUMMON_LOCK = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT), SUMMON_WEAKNESS = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT),
            FADE_TOTAL = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT), UNSUMMON = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT),
            MODIFIER = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT), AFFECTED = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CONTROL = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.BOOLEAN),
            LEFT_PUNCH = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> FINISHER = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.FLOAT),
            VISUAL_SPEED = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.FLOAT), VISUAL_PRECISION = SynchedEntityData.defineId(CrazyDiamondEntity.class, EntityDataSerializers.FLOAT);
    private UUID ownerUuid, targetUuid;
    private BlockPos targetBlock;
    private int phaseTicks, duration, recovery, heldLease, controlLease, finisherDecay, delayedHits;
    private float finisher, heavyFinisher, guardDamage, damageDealt;
    private boolean firstPerform, previousLight, transferringDamage, modifierTriggered;
    private Vec3 anchor, controlInput = Vec3.ZERO;
    private float controlYaw, controlPitch, attributesDevelopment = Float.NaN;
    private boolean attributesWeakened;
    private CDRecoveryModifiers.Part hitPart;
    public CrazyDiamondEntity(EntityType<? extends CrazyDiamondEntity> type, Level level) { super(type, level); setNoAi(true); setNoGravity(true); noPhysics = true; }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.ATTACK_SPEED, 14)
                .add(Attributes.MOVEMENT_SPEED, .8).add(Attributes.ATTACK_KNOCKBACK, 0).add(Attributes.ARMOR, 0).add(Attributes.ARMOR_TOUGHNESS, 0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b); b.define(OWNER, -1); b.define(KIND, 0); b.define(PHASE, 0); b.define(TICKS, 0); b.define(DURATION, 0);
        b.define(SUMMON_MODE, 0); b.define(SUMMON_VARIANT, 0); b.define(SUMMON_LOCK, 3); b.define(SUMMON_WEAKNESS, 0);
        b.define(FADE_TOTAL, 3); b.define(UNSUMMON, -1); b.define(MODIFIER, 0); b.define(AFFECTED, -1); b.define(CONTROL, false); b.define(LEFT_PUNCH, false);
        b.define(FINISHER, 0F); b.define(VISUAL_SPEED, 14F); b.define(VISUAL_PRECISION, 11F);
    }
    @Override public Mob entity() { return this; }
    @Override public StandDefinitions.Definition definition() { return StandDefinitions.definition(CrazyDiamondCombatActions.ID); }
    @Override public void setOwner(ServerPlayer player) {
        ownerUuid = player.getUUID(); entityData.set(OWNER, player.getId()); setPos(player.position()); setRot(player.getYRot(), player.getXRot());
        entityData.set(SUMMON_VARIANT, random.nextInt(128)); entityData.set(LEFT_PUNCH, player.getMainArm() == HumanoidArm.LEFT); updateAttributes();
    }
    @Override public ServerPlayer owner() { return ownerUuid != null && level().getEntity(ownerUuid) instanceof ServerPlayer player ? player : null; }
    @Override public int ownerId() { return entityData.get(OWNER); }
    public Kind attackKind() { return Kind.values()[entityData.get(KIND)]; }
    public int summonMode() { return entityData.get(SUMMON_MODE); }
    public int summonVariant() { return entityData.get(SUMMON_VARIANT); }
    public boolean isArmsOnlyMode() { return (summonMode() & 1) != 0; }
    private boolean weakened() { return isArmsOnlyMode() || entityData.get(SUMMON_WEAKNESS) > 0; }
    public boolean leftPunch() { return entityData.get(LEFT_PUNCH); }
    public float visualAttackSpeed() { return entityData.get(VISUAL_SPEED); }
    public float visualPrecision() { return entityData.get(VISUAL_PRECISION); }
    public int modifierKind() { return entityData.get(MODIFIER); }
    public Entity affectedTarget() { return level().getEntity(entityData.get(AFFECTED)); }
    public CDRecoveryModifiers.Part hitPart() { return hitPart; }
    public float damageDealt() { return damageDealt; }
    @Override public HumanoidArm getMainArm() { var user = level().getEntity(ownerId()); return user instanceof LivingEntity living ? living.getMainArm() : HumanoidArm.RIGHT; }
    public void prepareActionSummon(Kind kind, boolean fresh) {
        int mode = kind == Kind.LIGHT ? 7 : kind == Kind.BARRAGE || kind == Kind.BLOCK ? 15 : 0;
        prepareMode(mode, fresh);
    }
    @Override public void prepareRestorationSummon(RestorationSelection.Mode mode, boolean fresh) {
        prepareMode(mode == RestorationSelection.Mode.TERRAIN ? 0 : mode == RestorationSelection.Mode.HEAL ? 7 : 11, fresh);
    }
    public void prepareAnchorSummon(boolean fresh) { prepareMode(11, fresh); }
    private void prepareMode(int mode, boolean fresh) {
        entityData.set(UNSUMMON, -1);
        if (fresh && mode != 0 || isArmsOnlyMode() && mode != 0) {
            entityData.set(SUMMON_MODE, fresh ? mode : summonMode() | mode); entityData.set(SUMMON_LOCK, 0);
        } else if (isArmsOnlyMode()) {
            entityData.set(SUMMON_MODE, 2); int lock = StandStatFormulas.getSummonLockTicks(attackSpeed());
            entityData.set(SUMMON_WEAKNESS, lock); entityData.set(FADE_TOTAL, lock);
        }
        updateAttributes();
    }
    public float renderAlpha() {
        int ticks = Math.max(entityData.get(SUMMON_LOCK), entityData.get(SUMMON_WEAKNESS)), total = entityData.get(FADE_TOTAL);
        float alpha = ticks > 0 && total > 0 ? 1F - ticks / (float) total : 1;
        int unsummon = entityData.get(UNSUMMON); if (unsummon >= 0) alpha *= 1F - unsummon / (float) Math.max(1, total);
        return Mth.clamp(alpha, 0, 1);
    }
    private float development() { var player = owner(); return player == null ? 0 : player.getAttachedOrCreate(StandAttachments.PROGRESS).development(); }
    private void updateAttributes() {
        if (level().isClientSide()) return;
        float development = development(); boolean weakened = weakened();
        if (attributesDevelopment == development && attributesWeakened == weakened) return;
        attributesDevelopment = development; attributesWeakened = weakened;
        var stats = definition().stats();
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.power(development));
        getAttribute(Attributes.ATTACK_SPEED).setBaseValue(stats.speed(development));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(StandStatFormulas.getMovementSpeed(stats.speed(development)));
        var id = RipplesFabric.id("crazy_diamond_arms_only"); var damage = getAttribute(Attributes.ATTACK_DAMAGE); damage.removeModifier(id);
        if (weakened) damage.addTransientModifier(new AttributeModifier(id, -.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public double efficiency() {
        var player = owner(); if (player == null) return 1;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        double condition = StandConfig.staminaFree(player) || StandAttachments.resolve(player).isBurst() ? 1
                : .25 + Math.min(progress.stamina() / progress.maxStamina() * 1.5, .75);
        return condition * StandStatFormulas.rangeStrengthFactor(2, 4, distanceTo(player));
    }
    public double attackDamage() { return getAttributeValue(Attributes.ATTACK_DAMAGE) * efficiency(); }
    public double attackSpeed() { return getAttributeValue(Attributes.ATTACK_SPEED) * efficiency(); }
    @Override public double restorationAttackSpeed() { return attackSpeed(); }
    public double precision() { return weakened() ? 0 : definition().stats().precision(development()) * efficiency(); }
    public double durability() { return definition().stats().durability(development()) * efficiency() * (weakened() ? .75 : 1); }
    public float projectileInaccuracy(float base) { return Math.max((base + 1) * 8 / Math.max((float) precision(), 4) - 1, 0); }
    @Override public int actionPhase() { return RestorationSelection.state(this).mode() != RestorationSelection.Mode.NONE ? 2 : entityData.get(PHASE); }
    @Override public int actionTicks() { var state = RestorationSelection.state(this); return state.mode() != RestorationSelection.Mode.NONE ? state.ticks() : entityData.get(TICKS); }
    @Override public int actionDuration() { return RestorationSelection.state(this).mode() != RestorationSelection.Mode.NONE ? Integer.MAX_VALUE : entityData.get(DURATION); }
    @Override public int abilityKind() { return attackKind().ordinal(); }
    @Override public boolean heavyAttack() { return attackKind() == Kind.HEAVY || attackKind() == Kind.FINISHER; }
    @Override public float finisherCharge() { return entityData.get(FINISHER); }
    @Override public int uiRecoveryTicks() { return actionPhase() == 3 || actionPhase() == 6 ? Math.max(0, duration - phaseTicks) : 0; }
    @Override public boolean allowsStaminaRegen() { return actionPhase() == 0 || actionPhase() == 3; }
    @Override public float userWalkSpeed() { return switch (actionPhase()) { case 4 -> .15F; case 5 -> .3F; case 1, 2 -> attackKind() == Kind.LIGHT ? 1 : .5F; default -> 1; }; }
    private boolean live() {
        var player = owner(); return player != null && player.level() == level() && player.isAlive() && !player.isSpectator()
                && player.getAttachedOrCreate(StandAttachments.PROGRESS).standId().equals(CrazyDiamondCombatActions.ID) && !TimeStopService.isFrozen(this);
    }
    public boolean canUse(Kind kind) {
        if (!live() || VampireCombatService.isStunned(this) || actionPhase() == 6) return false;
        if (attackKind() == kind && (kind == Kind.BARRAGE || kind == Kind.BLOCK)) return true;
        return actionPhase() == 0 || actionPhase() == 3 || attackKind() == Kind.BARRAGE && (kind == Kind.HEAVY || kind == Kind.FINISHER);
    }
    @Override public boolean canUseRestoration(RestorationSelection.Mode mode) {
        return live() && !VampireCombatService.isStunned(this) && (entityData.get(PHASE) == 0 || entityData.get(PHASE) == 3)
                && (RestorationSelection.state(this).mode() == RestorationSelection.Mode.NONE || RestorationSelection.state(this).mode() == mode);
    }
    @Override public void beginRestoration() { idle(); entityData.set(UNSUMMON, -1); }
    public boolean start(Kind kind) {
        if (kind == Kind.NONE || !canUse(kind)) return false;
        if (attackKind() == kind && (kind == Kind.BARRAGE || kind == Kind.BLOCK)) { refreshHeld(); return true; }
        returnHeldItem(); updateAttributes(); entityData.set(UNSUMMON, -1); phaseTicks = 0; recovery = 0; firstPerform = true;
        targetUuid = null; targetBlock = null; damageDealt = 0; hitPart = null; modifierTriggered = false; entityData.set(MODIFIER, 0); entityData.set(AFFECTED, -1);
        var target = RestorationSelection.target(owner(), this); if (target.entity() != null) targetUuid = target.entity().getUUID();
        if (kind == Kind.ANCHOR_MAKE) targetBlock = blockTarget();
        entityData.set(KIND, kind.ordinal()); entityData.set(LEFT_PUNCH, !leftPunch());
        switch (kind) {
            case LIGHT -> {
                if (isArmsOnlyMode() && leftPunch() == (getMainArm() == HumanoidArm.RIGHT)) prepareMode(15, false);
                int windup = StandStatFormulas.getLightAttackWindup(attackSpeed(), finisher, Math.min(guardDamage / 5, 1), !previousLight, random::nextDouble);
                int perform = StandStatFormulas.getLightAttackRecovery(attackSpeed(), finisher);
                recovery = perform * (isArmsOnlyMode() ? 2 : 4); duration = Math.max(windup, 1); entityData.set(PHASE, windup > 0 ? 1 : 2);
                if (windup == 0) duration = perform;
            }
            case HEAVY, FINISHER -> {
                heavyFinisher = finisher; duration = Math.max(StandStatFormulas.getHeavyAttackWindup(attackSpeed(), finisher), 1);
                recovery = Math.max(0, StandStatFormulas.getHeavyAttackRecovery(attackSpeed(), heavyFinisher)); finisher = Math.max(0, finisher - .51F); entityData.set(PHASE, 1);
                if (kind == Kind.HEAVY && CDRecoveryModifiers.canUseItem(owner().getOffhandItem())) setItemInHand(InteractionHand.MAIN_HAND, owner().getOffhandItem().split(1));
                if (kind == Kind.FINISHER && target.entity() instanceof LivingEntity) hitPart = CDRecoveryModifiers.Part.aimedAt(target.entity(), owner());
                playOriginal("crazy_diamond_dora_long");
            }
            case BARRAGE -> { duration = StandAttachments.resolve(owner()).isBurst() ? Integer.MAX_VALUE : StandStatFormulas.getBarrageMaxDuration(durability()); entityData.set(PHASE, 4); heldLease = 12; delayedHits = 0; playOriginal("crazy_diamond_dorarara"); }
            case BLOCK -> { duration = Integer.MAX_VALUE; entityData.set(PHASE, 5); heldLease = 12; guardDamage = 0; }
            case BLOCK_BULLET, BLOOD_CUTTER, ANCHOR_MAKE -> {
                duration = kind == Kind.BLOCK_BULLET ? 15 : kind == Kind.BLOOD_CUTTER ? 5 : 10;
                recovery = kind == Kind.ANCHOR_MAKE ? 5 : 0;
                entityData.set(PHASE, 1); if (kind != Kind.ANCHOR_MAKE) playOriginal("crazy_diamond_fix_started");
            }
            default -> { }
        }
        previousLight = kind == Kind.LIGHT; sync(); return true;
    }
    @Override public boolean beginAttack(boolean heavy) { return start(heavy ? finisher >= .5F && ProgressionService.unlocked(owner(), 1) ? Kind.FINISHER : Kind.HEAVY : Kind.LIGHT); }
    @Override public boolean beginHeld(boolean guard) { return start(guard ? Kind.BLOCK : Kind.BARRAGE); }
    @Override public boolean beginSpecial(int kind) { return kind >= 0 && kind < Kind.values().length && start(Kind.values()[kind]); }
    @Override public boolean beginUppercut() { return start(Kind.FINISHER); }
    @Override public void refreshHeld() { if (actionPhase() == 4 || actionPhase() == 5) heldLease = 12; }
    @Override public void stopHeld() { if (actionPhase() == 4) { recovery = Math.max(0, StandStatFormulas.getBarrageRecovery(attackSpeed())); recover(); } else if (actionPhase() == 5) idle(); }
    private void sync() { entityData.set(TICKS, phaseTicks); entityData.set(DURATION, duration); entityData.set(FINISHER, finisher); entityData.set(VISUAL_SPEED, (float) attackSpeed()); entityData.set(VISUAL_PRECISION, (float) precision()); }
    private void returnHeldItem() { var held = getMainHandItem(); if (!held.isEmpty() && owner() != null) { setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); CrazyDiamondRestorationActions.giveItem(owner(), held); } }
    private void idle() { returnHeldItem(); entityData.set(PHASE, 0); entityData.set(KIND, 0); entityData.set(MODIFIER, 0); duration = 0; phaseTicks = 0; previousLight = false; sync(); }
    private void recover() { if (recovery <= 0) idle(); else { entityData.set(PHASE, 3); duration = recovery; phaseTicks = 0; sync(); } }
    public boolean attachModifier(CDRecoveryModifiers.Modifier modifier) {
        if (modifier == CDRecoveryModifiers.Modifier.NONE || modifierKind() != 0 || !CDRecoveryModifiers.canAttach(this, modifier)) return false;
        entityData.set(MODIFIER, modifier.ordinal()); if (modifier == CDRecoveryModifiers.Modifier.ANGELO && affectedTarget() instanceof LivingEntity living) CDRecoveryModifiers.beginAngelo(this, living);
        return true;
    }
    @Override public void tick() {
        if (TimeStopService.isFrozen(this)) return;
        super.tick(); if (!(level() instanceof ServerLevel)) return;
        if (!live()) { returnHeldItem(); discard(); return; }
        updateAttributes();
        if (manuallyControlled() && (--controlLease <= 0 || owner().position().distanceToSqr(anchor) > 4 || owner().isPassenger())) stopControl();
        positionFromOwner();
        if (RestorationSelection.state(this).mode() != RestorationSelection.Mode.NONE) { entityData.set(UNSUMMON, -1); sync(); return; }
        int lock = entityData.get(SUMMON_LOCK); if (lock > 0) { entityData.set(SUMMON_LOCK, lock - 1); return; }
        int weakness = entityData.get(SUMMON_WEAKNESS); if (weakness > 0 && !VampireCombatService.isStunned(this)) entityData.set(SUMMON_WEAKNESS, weakness - 1);
        if (VampireCombatService.isStunned(this)) { stopHeld(); if (actionPhase() != 6) idle(); return; }
        var kind = attackKind(); int phase = actionPhase();
        if (phase == 1) {
            if (++phaseTicks >= duration) { entityData.set(PHASE, 2); phaseTicks = 0; firstPerform = true; duration = kind == Kind.LIGHT ? StandStatFormulas.getLightAttackRecovery(attackSpeed(), finisher) : 1; }
        } else if (phase == 2) {
            if (firstPerform) perform(kind); firstPerform = false;
            if (++phaseTicks >= duration) recover();
        } else if (phase == 4) {
            if (--heldLease < 0 || phaseTicks >= duration) stopHeld();
            else { barrageTick(); phaseTicks++; if (!spend(4)) stopHeld(); }
        } else if (phase == 5) { if (--heldLease < 0) stopHeld(); else phaseTicks++; }
        else if (phase == 3 || phase == 6) {
            if (phase == 3 && !modifierTriggered && phaseTicks >= duration - 1) { CDRecoveryModifiers.finishRecovery(this); modifierTriggered = true; }
            if (++phaseTicks >= duration) idle();
        }
        if (actionPhase() == 0) {
            if (finisherDecay > 0) finisherDecay--; else finisher = Math.max(0, finisher - (finisher < .5F ? .0125F : .025F));
            if (isArmsOnlyMode()) {
                int unsummon = entityData.get(UNSUMMON);
                int fade = StandAttachments.resolve(owner()).isBurst() ? 3 : Mth.ceil(7 / ((efficiency() * 2 + 1) / 3));
                if (unsummon < 0) { entityData.set(FADE_TOTAL, fade); playOriginal("crazy_diamond_unsummon"); }
                if (unsummon >= fade) { discard(); return; }
                entityData.set(UNSUMMON, unsummon + 1);
            }
        }
        sync();
    }
    private void perform(Kind kind) {
        switch (kind) {
            case LIGHT -> { addFinisher(.1F); attackTarget(StandStatFormulas.getLightAttackDamage(attackDamage()), 1); spend(10); }
            case HEAVY, FINISHER -> { attackTarget(StandStatFormulas.getHeavyAttackDamage(attackDamage()), 1); spend(50); }
            case BLOCK_BULLET -> { CrazyDiamondCombatActions.fireBlockBullet(this); spend(40); }
            case BLOOD_CUTTER -> { CrazyDiamondCombatActions.fireBloodCutter(this); spend(25); }
            case ANCHOR_MAKE -> { if (targetBlock != null) CDAnchorItem.make(owner(), targetBlock, attackDamage()); playOriginal("crazy_diamond_punch_heavy"); spend(25); }
            default -> { }
        }
    }
    public boolean spend(float amount) {
        var player = owner(); if (player == null) return false; if (StandConfig.staminaFree(player) || amount <= 0) return true;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        player.setAttached(StandAttachments.PROGRESS, progress.withStamina(Math.max(0, progress.stamina() - amount)));
        return progress.stamina() >= amount || StandAttachments.resolve(player).isBurst();
    }
    private void barrageTick() {
        int rate = StandStatFormulas.getBarrageHitsPerSecond(attackSpeed()), hits = rate / 20; rate %= 20;
        if (delayedHits > 0) { hits++; delayedHits--; }
        else if (rate > 0) { double gap = 20D / rate; if ((duration - phaseTicks + delayedHits) % (int) gap == 0) { if (random.nextDouble() < gap % 1) delayedHits++; else hits++; } }
        if (hits > 0) attackTarget(StandStatFormulas.getBarrageHitDamage(attackDamage(), precision()) * hits, hits);
    }
    private Entity target() { return targetUuid == null ? null : level().getEntity(targetUuid); }
    public boolean canHarm(Entity target) {
        if (target == this || target == owner() || !target.isAlive() || target.isSpectator() || !target.isPickable()) return false;
        if (target instanceof StandManifestation stand && stand.ownerId() == ownerId()) return false;
        if (target instanceof Player player && !owner().canHarmPlayer(player)) return false;
        return !(target instanceof LivingEntity living) || owner().canAttack(living);
    }
    private Entity rayTarget() {
        var start = getEyePosition(); var end = start.add(getLookAngle().scale(2.5)); Entity closest = null; double distance = Double.POSITIVE_INFINITY;
        for (var entity : level().getEntities(this, getBoundingBox().expandTowards(end.subtract(start)).inflate(1), this::canHarm)) {
            var box = entity.getBoundingBox().inflate(entity.getPickRadius());
            if (precision() > 4) { double extra = precision() / 5 - .8; box = box.inflate(Math.min(extra * box.getXsize(), 4) / 2, Math.min(extra * box.getYsize(), 4) / 2, Math.min(extra * box.getZsize(), 4) / 2); }
            if ((box.contains(start) || box.clip(start, end).isPresent()) && entity.distanceToSqr(start) < distance) { closest = entity; distance = entity.distanceToSqr(start); }
        }
        return closest;
    }
    private BlockPos blockTarget() {
        var hit = level().clip(new ClipContext(owner().getEyePosition(), owner().getEyePosition().add(owner().getLookAngle().scale(owner().blockInteractionRange())), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, owner()));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }
    private void attackTarget(float damage, int hits) {
        var target = heavyAttack() ? target() : rayTarget(); if (target != null && (!canHarm(target) || target.distanceTo(this) > 4)) target = null;
        if (target == null) {
            var pos = blockTarget();
            if (pos != null && CDAnchorItem.breakable(owner(), pos, attackDamage()) && owner().level().getGameRules().get(JojoGameRules.BREAK_BLOCKS)) {
                if (heavyAttack()) CDRecoveryModifiers.heavyBlockHit(this, pos);
                else owner().level().destroyBlock(pos, !owner().isCreative(), this);
            }
            playOriginal(heavyAttack() ? "crazy_diamond_punch_heavy" : "crazy_diamond_punch_light"); return;
        }
        var server = (ServerLevel) level(); var source = new DamageSource(server.registryAccess().getOrThrow(DamageTypes.MOB_ATTACK), this, owner());
        float before = target instanceof LivingEntity living ? living.getHealth() : 0;
        var oldVelocity = target.getDeltaMovement();
        if (attackKind() == Kind.HEAVY && target instanceof LivingEntity living) damage = armorPiercing(damage, (float) attackDamage() * .01F, living);
        if (modifierKind() == CDRecoveryModifiers.Modifier.ANGELO.ordinal() && target instanceof LivingEntity living) damage = Math.min(damage, Math.max(0, living.getHealth() - .0001F));
        boolean hurt = target instanceof LivingEntity living ? StandDamage.standHurt(server, living, source, damage) : target.hurtServer(server, source, damage * StandConfig.server().standPowerMultiplier());
        entityData.set(AFFECTED, target.getId()); damageDealt = target instanceof LivingEntity living ? Math.max(0, before - living.getHealth()) : 0;
        if (attackKind() == Kind.LIGHT) addFinisher(.1F); else if (attackKind() == Kind.BARRAGE) addFinisher(.005F * hits);
        if (attackKind() == Kind.BARRAGE) {
            boolean resolve = StandAttachments.resolve(owner()).isBurst();
            double knockback = resolve || target instanceof StandManifestation ? 0 : Math.clamp(attackDamage() * .0075, 0, 1);
            target.setDeltaMovement(oldVelocity.add(target.getDeltaMovement().subtract(oldVelocity).scale(knockback)));
            if (hurt && resolve && target instanceof LivingEntity living) CDRecoveryModifiers.suspend(living);
        }
        if (hurt && target instanceof LivingEntity living) {
            if (heavyAttack()) living.damageCooldownTime = 10;
            if (heavyAttack() || attackKind() == Kind.LIGHT && guardDamage > 0) {
                double strength = heavyAttack() ? .5 + attackDamage() / (8 - heavyFinisher * 4) : Math.min(guardDamage / 5, 1) * .5;
                var facing = getLookAngle(); living.knockback(strength, -facing.x, -facing.z, source, damage); living.syncVelocity = true;
                if (heavyAttack()) CDRecoveryModifiers.startImpact(this, living);
            }
            living.setLastHurtByMob(owner()); living.setLastHurtByPlayer(owner(), 100);
        }
        playOriginal(heavyAttack() ? "crazy_diamond_punch_heavy" : attackKind() == Kind.BARRAGE ? "crazy_diamond_punch_barrage" : "crazy_diamond_punch_light");
    }
    /** Original DamageUtil.addArmorPiercing and MathUtil.inverseArmorProtectionDamage. */
    private static float armorPiercing(float damage, float piercing, LivingEntity target) {
        float armor = target.getArmorValue(); if (armor <= 0 || piercing <= 0) return damage;
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float absorbed = damage * (1 - Math.clamp(armor - damage / (2 + toughness / 4), armor * .2F, 20) / 25);
        float desired = absorbed + Math.clamp(piercing, 0, 1) * (damage - absorbed);
        float f = armor / 25 - 1, f2 = 25 * (1 + toughness / 8);
        return Math.clamp(f2 * (f + (float) Math.sqrt(f * f + 2 * desired / f2)), desired / (1 - armor / 125), 5 * desired);
    }
    private void addFinisher(float amount) { finisher = Math.min(1, finisher + amount); finisherDecay = 40; }
    @Override public float protectOwner(DamageSource source, float damage) {
        var player = owner(); if (player == null || damage <= 0 || actionPhase() != 5 || source.is(DamageTypeTags.BYPASSES_ARMOR)) return damage;
        var origin = source.getSourcePosition(); if (origin == null || origin.subtract(transferringDamage ? position() : player.position()).normalize().dot(getLookAngle()) <= .5) return damage;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS); float cost = StandStatFormulas.getBlockStaminaCost(damage);
        float blocked = StandConfig.staminaFree(player) || cost == 0 ? 1 : Math.min(progress.stamina() / cost, 1); spend(cost); playOriginal("stand_block");
        if (blocked < 1) { entityData.set(PHASE, 6); duration = StandStatFormulas.getBlockingBreakTicks(durability()); phaseTicks = 0; }
        float remaining = damage * (1 - StandStatFormulas.getPhysicalResistance(durability(), attackDamage(), blocked, damage, StandConfig.server().standResistanceMultiplier())); guardDamage += remaining; return remaining;
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return owner() != null && source.getEntity() != owner() && source.getEntity() != this && super.hurtServer(level, source, damage); }
    @Override protected void actuallyHurt(ServerLevel level, DamageSource source, float damage) {
        var user = owner(); if (user == null || !user.isAlive()) return;
        var origin = source.getSourcePosition();
        if (actionPhase() == 0 && !manuallyControlled() && origin != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)
                && origin.subtract(position()).normalize().dot(getLookAngle()) > .5 && start(Kind.BLOCK)) heldLease = 4;
        damage = getDamageAfterMagicAbsorb(source, getDamageAfterArmorAbsorb(source, damage));
        var resistance = user.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
        if (resistance != null && !source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE))
            damage = Math.max(0, damage * (25 - (resistance.getAmplifier() + 1) * 5) / 25);
        transferringDamage = true;
        try {
            if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) damage = actionPhase() == 5 ? protectOwner(source, damage)
                    : damage * (1 - StandStatFormulas.getPhysicalResistance(durability(), attackDamage(), 0, damage, StandConfig.server().standResistanceMultiplier()));
            float remaining = Math.max(0, damage - getAbsorptionAmount()); setAbsorptionAmount(Math.max(0, getAbsorptionAmount() - damage));
            if (remaining > 0) StandDamage.hurt(level, user, new LinkedDamage(source), remaining);
        } finally { transferringDamage = false; }
    }
    private static final class LinkedDamage extends DamageSource {
        private final DamageSource original;
        private LinkedDamage(DamageSource original) { super(original.typeHolder(), original.getDirectEntity(), original.getEntity()); this.original = original; }
        @Override public boolean is(TagKey<DamageType> tag) { return tag.equals(DamageTypeTags.BYPASSES_ARMOR) || tag.equals(DamageTypeTags.BYPASSES_ENCHANTMENTS) || tag.equals(DamageTypeTags.BYPASSES_EFFECTS) || tag.equals(DamageTypeTags.BYPASSES_RESISTANCE); }
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
        controlYaw = yaw; controlPitch = Math.clamp(pitch, -90, 90); var facing = Vec3.directionFromRotation(0, yaw); var right = new Vec3(-facing.z, 0, facing.x);
        controlInput = facing.scale(Math.clamp(forward, -1, 1)).add(right.scale(Math.clamp(sideways, -1, 1))).add(0, Math.clamp(vertical, -1, 1), 0);
        if (controlInput.lengthSqr() > 1) controlInput = controlInput.normalize(); controlLease = 12;
    }
    @Override public void stopControl() { entityData.set(CONTROL, false); anchor = null; controlInput = Vec3.ZERO; }
    private void positionFromOwner() {
        var user = owner();
        if (manuallyControlled()) {
            user.setPos(anchor); user.setDeltaMovement(Vec3.ZERO); var desired = position().add(controlInput.scale(getAttributeValue(Attributes.MOVEMENT_SPEED)));
            var relative = desired.subtract(anchor); if (relative.lengthSqr() > 16) desired = anchor.add(relative.normalize().scale(4));
            if (level().hasChunkAt(BlockPos.containing(desired))) setPos(desired); setRot(controlYaw, controlPitch);
        } else {
            var forward = Vec3.directionFromRotation(0, user.getYRot()); var side = new Vec3(-forward.z, 0, forward.x);
            var restoration = RestorationSelection.state(this); Vec3 offset;
            boolean fixItem = restoration.mode() == RestorationSelection.Mode.REPAIR || restoration.mode() == RestorationSelection.Mode.PREVIOUS_STATE && restoration.targetId() < 0;
            if (isArmsOnlyMode()) offset = new Vec3(0, .15, 0);
            else if (fixItem) offset = side.scale(user.getMainArm() == HumanoidArm.LEFT ? -.667 : .667).add(0, .2, 0);
            else if (attackKind() == Kind.BLOCK_BULLET) offset = side.scale(user.getMainArm() == HumanoidArm.LEFT ? -.25 : .25).add(0, -.5, 0);
            else if (attackKind() == Kind.BLOOD_CUTTER) offset = side.scale(-.1).add(0, -.5, 0);
            else offset = actionPhase() == 0 ? forward.scale(-.6).add(side.scale(.8)) : forward.scale(actionPhase() == 5 ? .3 : 1);
            setPos(user.position().add(offset)); setRot(fixItem && !isArmsOnlyMode() ? user.yBodyRot + (getMainArm() == HumanoidArm.RIGHT ? 15 : -15) : user.getYRot(), user.getXRot());
        }
        setYBodyRot(getYRot()); setYHeadRot(getYRot()); setDeltaMovement(Vec3.ZERO);
    }
    @Override public void remove(RemovalReason reason) { if (!level().isClientSide()) returnHeldItem(); super.remove(reason); }
    @Override public void playOriginal(String sound) { level().playSound(null, getX(), getY(), getZ(), JojoSounds.get(sound), SoundSource.PLAYERS, 1, 1); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canCollideWith(Entity entity) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public net.minecraft.world.scores.PlayerTeam getTeam() { var player = owner(); return player == null ? super.getTeam() : player.getTeam(); }
}
