package com.github.standobyte.jojo.fabric;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.PlayerTeam;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/** HungryZombieEntity and its four owner goals, adapted from the retained Forge sources. */
public final class HungryZombieEntity extends Zombie {
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> OWNER =
            SynchedEntityData.defineId(HungryZombieEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    private double distanceFromOwner = -1;
    private boolean summonedFromAbility;

    public HungryZombieEntity(EntityType<? extends HungryZombieEntity> type, Level level) {
        super(type, level);
        xpReward = (int) (xpReward * 1.5);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
    }
    public UUID getOwnerUUID() { return entityData.get(OWNER).map(EntityReference::getUUID).orElse(null); }
    public void setOwner(LivingEntity owner) { entityData.set(OWNER, Optional.ofNullable(owner).map(EntityReference::of)); }
    public LivingEntity getOwner() {
        UUID id = getOwnerUUID();
        Player owner = id == null ? null : level().getPlayerByUUID(id);
        if (owner != null && !level().isClientSide()
                && (!VampireService.progress(owner).vampire() || VampireLifecycle.curingStage(owner) >= 4)) {
            setOwner(null);
            return null;
        }
        return owner;
    }
    public boolean isEntityOwner(LivingEntity entity) { return entity != null && entity.getUUID().equals(getOwnerUUID()); }
    public void setSummonedFromAbility() { summonedFromAbility = true; }
    public boolean wasSummonedFromAbility() { return summonedFromAbility; }
    public boolean farFromOwner(double distance) { return distanceFromOwner > distance * distance; }

    @Override protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(6, new FollowOwnerGoal());
        targetSelector.addGoal(1, new OwnerTargetGoal(true));
        targetSelector.addGoal(2, new OwnerTargetGoal(false));
    }
    @Override protected void addBehaviourGoals() {
        goalSelector.addGoal(2, new ZombieAttackGoal(this, 1, false));
        goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1, true, 4, this::canBreakDoors));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false) {
            @Override public boolean canUse() {
                return super.canUse() && (getOwner() == null || farFromOwner(16));
            }
        });
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        entityData.get(OWNER).ifPresent(owner -> owner.store(output, "Owner"));
        output.putBoolean("AbilitySummon", summonedFromAbility);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(OWNER, Optional.ofNullable(EntityReference.<LivingEntity>read(input, "Owner")));
        summonedFromAbility = input.getBooleanOr("AbilitySummon", false);
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            LivingEntity owner = getOwner();
            distanceFromOwner = owner == null ? -1 : distanceToSqr(owner);
        }
    }
    @Override protected boolean convertsInWater() { return false; }
    // AbilitySummon is only a persisted marker upstream. Both forms retain vanilla Zombie sun sensitivity.
    @Override protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) { }
    @Override protected void dropExperience(ServerLevel level, Entity killer) {
        if (!(killer instanceof LivingEntity living) || !isEntityOwner(living)) super.dropExperience(level, killer);
    }
    @Override public boolean canAttack(LivingEntity target) { return !isEntityOwner(target) && super.canAttack(target); }
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return !(target instanceof Player victim && owner instanceof Player player) || player.canHarmPlayer(victim);
    }
    @Override public PlayerTeam getTeam() {
        LivingEntity owner = getOwner();
        return owner == null ? super.getTeam() : owner.getTeam();
    }
    public Boolean ownerAlliance(Entity entity) {
        LivingEntity owner = getOwner();
        return owner == null ? null : entity == owner || owner.isAlliedTo(entity);
    }
    @Override public boolean canBeLeashed() { return !isLeashed() && getOwnerUUID() != null; }
    @Override public boolean canHaveALeashAttachedTo(Entity holder) {
        return holder instanceof LivingEntity living && isEntityOwner(living) && !isLeashed();
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!super.doHurtTarget(level, target)) return false;
        if (getMainHandItem().isEmpty() && target instanceof LivingEntity living
                && living.getArmorCoverPercentage() <= random.nextFloat() && VampireZombieService.canBleed(living)) {
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (VampireZombieService.drainBlood(this, living, damage / 5)) heal(damage / 2);
        }
        return true;
    }
    @Override public boolean killedEntity(ServerLevel level, LivingEntity dead, DamageSource source) {
        if (level.getDifficulty() != Difficulty.EASY) createZombie(level, getOwner(), dead, isPersistenceRequired());
        return true;
    }
    public static boolean createZombie(ServerLevel level, LivingEntity owner, LivingEntity dead, boolean makePersistent) {
        var difficulty = level.getDifficulty();
        if (!(difficulty == Difficulty.HARD || difficulty == Difficulty.NORMAL && dead.getRandom().nextBoolean()
                || difficulty == Difficulty.EASY && dead.getRandom().nextFloat() <= 0.125F)) return false;
        if (!(dead instanceof Villager || dead instanceof AbstractIllager)) return false;
        Mob original = (Mob) dead;
        var zombie = original.convertTo(VampireZombieService.HUNGRY_ZOMBIE, ConversionParams.single(original, true, true),
                EntitySpawnReason.CONVERSION, converted -> {
                    converted.finalizeSpawn(level, level.getCurrentDifficultyAt(converted.blockPosition()),
                            EntitySpawnReason.CONVERSION, new ZombieGroupData(false, true));
                    converted.setOwner(owner);
                });
        if (zombie == null) return false;
        // The original makePersistent argument is unused; conversion retains the source mob's persistence.
        if (!dead.isSilent()) level.levelEvent(null, 1026, dead.blockPosition(), 0);
        return true;
    }

    private final class FollowOwnerGoal extends Goal {
        private LivingEntity owner;
        private int recalculate;
        private float oldWaterCost;
        FollowOwnerGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            owner = getOwner();
            return owner != null && !owner.isSpectator() && distanceToSqr(owner) >= 100;
        }
        @Override public boolean canContinueToUse() { return !getNavigation().isDone() && distanceToSqr(owner) > 4; }
        @Override public void start() {
            recalculate = 0;
            oldWaterCost = getPathfindingMalus(PathType.WATER);
            setPathfindingMalus(PathType.WATER, 0);
        }
        @Override public void stop() {
            owner = null;
            getNavigation().stop();
            setPathfindingMalus(PathType.WATER, oldWaterCost);
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            getLookControl().setLookAt(owner, 10, getMaxHeadXRot());
            if (--recalculate <= 0) {
                recalculate = 10;
                if (!isLeashed() && !isPassenger() && !farFromOwner(12)) getNavigation().moveTo(owner, 1);
            }
        }
    }
    private final class OwnerTargetGoal extends TargetGoal {
        private final boolean defend;
        private int timestamp;
        private LivingEntity selected;
        OwnerTargetGoal(boolean defend) {
            super(HungryZombieEntity.this, false);
            this.defend = defend;
            setFlags(EnumSet.of(Flag.TARGET));
        }
        @Override public boolean canUse() {
            LivingEntity owner = getOwner();
            if (owner == null || farFromOwner(12)) return false;
            selected = defend ? owner.getLastHurtByMob() : owner.getLastHurtMob();
            int changed = defend ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
            DamageSource source = selected == null ? null : selected.getLastDamageSource();
            // Preserve the source's explicit bloodDrain filter for following an owner's attack.
            return changed != timestamp && (defend || source == null || source.getMsgId().startsWith("bloodDrain"))
                    && canAttack(selected, TargetingConditions.DEFAULT) && wantsToAttack(selected, owner);
        }
        @Override public void start() {
            setTarget(selected);
            LivingEntity owner = getOwner();
            if (owner != null) timestamp = defend ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
            super.start();
        }
    }
}
