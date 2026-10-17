package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.StandHeavyImpact;
import com.github.standobyte.jojo.fabric.stand.SourceEraMaterials;
import com.github.standobyte.jojo.fabric.mixin.restoration.FireworkRestorationAccess;
import com.github.standobyte.jojo.fabric.stand.tracking.StandEffectStore;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.*;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Active recovery modifiers share their struck target and damage with the original heavy task. */
public final class CDRecoveryModifiers {
    public enum Modifier { NONE, LEAVE_OBJECT, FACE, ARMS, LEGS, ANGELO }
    public enum Part {
        HEAD, TORSO_ARMS, LEGS;
        public static Part aimedAt(Entity target, Entity aiming) {
            var eye = aiming.getEyePosition(); var bounds = target.getBoundingBox();
            var nearest = new Vec3(Math.clamp(eye.x, bounds.minX, bounds.maxX), Math.clamp(eye.y, bounds.minY, bounds.maxY), Math.clamp(eye.z, bounds.minZ, bounds.maxZ));
            double relativeY = eye.add(aiming.getLookAngle().scale(eye.distanceTo(nearest))).y - target.getY();
            return relativeY < target.getBbHeight() * .375 ? LEGS : relativeY < target.getBbHeight() * .75 ? TORSO_ARMS : HEAD;
        }
    }
    public static final Identifier DRIED_BLOOD = RipplesFabric.id("dried_blood_drops");
    public static final Holder<MobEffect> FACE = effect("misshapen_face"), ARMS = effect("misshapen_arms"), LEGS = effect("misshapen_legs");
    private static final Map<Predicate<ItemStack>, BiConsumer<LivingEntity, ItemUse>> ITEM_ACTIONS = new HashMap<>();
    private record ItemUse(ItemStack item, LivingEntity user) { }
    private record Suspended(boolean noGravity, int ticks) { }
    private static final Map<LivingEntity, Suspended> SUSPENDED = new IdentityHashMap<>();
    private static boolean initialized;
    private static Holder<MobEffect> effect(String id) { return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, RipplesFabric.id(id), new MobEffect(MobEffectCategory.HARMFUL, 0x808080) { }); }

    public static void initialize() {
        if (initialized) return; initialized = true;
        initializeItems(); StandHeavyImpact.initialize(); AngeloRockEntity.initialize();
        StandEffects.register(DRIED_BLOOD, new StandEffects.Handler() {
            public void start(LivingEntity user, StandEffects.Entry effect, Entity target) { }
            public StandEffects.Entry tick(LivingEntity user, StandEffects.Entry effect, Entity target) {
                var data = effect.data(); int ticks = data.getIntOr("BloodTicks", 0);
                if (target != null) ticks += target.isInWaterOrBubble() ? 30 : target.isInWaterOrRain() ? 2 : 1;
                if (ticks >= 6000) return null;
                data.putInt("BloodTicks", ticks); return effect.withData(data);
            }
            public void stop(LivingEntity user, StandEffects.Entry effect, Entity target) { }
            public boolean needsTarget() { return true; }
            public boolean removeOn(StandEffects.Removal reason) { return reason != StandEffects.Removal.LOGOUT; }
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            SUSPENDED.entrySet().removeIf(entry -> {
                var target = entry.getKey(); var state = entry.getValue(); if (target.level() != level) return false;
                if (!target.isAlive() || state.ticks <= 1) { target.setNoGravity(state.noGravity); return true; }
                if (!TimeStopService.isFrozen(target)) entry.setValue(new Suspended(state.noGravity, state.ticks - 1));
                return false;
            });
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SUSPENDED.forEach((entity, state) -> entity.setNoGravity(state.noGravity)); SUSPENDED.clear(); });
    }
    public static void suspend(LivingEntity target) {
        var previous = SUSPENDED.get(target); SUSPENDED.put(target, new Suspended(previous == null ? target.isNoGravity() : previous.noGravity, 3));
        target.setNoGravity(true); if (target instanceof Mob mob) mob.getNavigation().stop();
    }
    public static LivingEntity userOf(LivingEntity target) { return target instanceof StandManifestation stand && stand.owner() != null ? stand.owner() : target; }
    public static boolean canAttach(CrazyDiamondEntity stand, Modifier modifier) {
        if (stand.modifierKind() != 0 || stand.owner() == null || stand.actionPhase() != 3) return false;
        var target = stand.affectedTarget();
        if (!(target instanceof LivingEntity living)) return false;
        if (modifier == Modifier.LEAVE_OBJECT) return stand.attackKind() == CrazyDiamondEntity.Kind.HEAVY && canUseItem(stand.getMainHandItem());
        if (modifier == Modifier.ANGELO) return stand.attackKind() == CrazyDiamondEntity.Kind.FINISHER
                && stand.owner().level().getGameRules().get(JojoGameRules.BREAK_BLOCKS)
                && StandEffects.entries(stand.owner()).stream().noneMatch(effect -> effect.type().equals(AngeloRockEntity.EFFECT));
        if (stand.attackKind() != CrazyDiamondEntity.Kind.FINISHER || !(userOf(living) instanceof Player) || stand.hitPart() == null) return false;
        return switch (modifier) { case FACE -> stand.hitPart() == Part.HEAD; case ARMS -> stand.hitPart() == Part.TORSO_ARMS; case LEGS -> stand.hitPart() == Part.LEGS; default -> false; };
    }
    public static void finishRecovery(CrazyDiamondEntity stand) {
        if (!(stand.affectedTarget() instanceof LivingEntity target) || !target.isAlive()) return;
        var modifier = Modifier.values()[stand.modifierKind()];
        if (modifier == Modifier.LEAVE_OBJECT) {
            // Original excludes vanilla skeletons and all Stand manifestations at trigger time.
            if (target.getType() == EntityTypes.SKELETON || target instanceof StandManifestation) return;
            var item = stand.getMainHandItem(); boolean used = false;
            if (!item.isEmpty()) {
                var use = new ItemUse(item, stand.owner());
                for (var action : ITEM_ACTIONS.entrySet()) if (action.getKey().test(item)) { action.getValue().accept(target, use); used = true; break; }
                if (item.getItem() instanceof StandArrowItem && target instanceof ServerPlayer player) used |= StandArrowService.pierce(player);
                if (used) item.shrink(1);
            }
            if (used) target.setHealth(target.getHealth() + stand.damageDealt() * .5F);
        } else if (modifier == Modifier.FACE || modifier == Modifier.ARMS || modifier == Modifier.LEGS) {
            var user = userOf(target);
            switch (modifier) {
                case FACE -> { debuff(user, MobEffects.NAUSEA, 60, 0); debuff(user, FACE, 200, 0); }
                case ARMS -> { debuff(user, MobEffects.WEAKNESS, 60, 0); debuff(user, MobEffects.MINING_FATIGUE, 60, 1); debuff(user, ARMS, 200, 0); }
                case LEGS -> { debuff(user, MobEffects.SLOWNESS, 60, 1); debuff(user, LEGS, 200, 0); }
                default -> { }
            }
            user.setHealth(user.getHealth() + stand.damageDealt() * .5F);
        }
    }
    private static void debuff(LivingEntity target, Holder<MobEffect> effect, int duration, int amplifier) { target.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true)); }
    public static boolean canUseItem(ItemStack item) { return !item.isEmpty() && (item.getItem() instanceof StandArrowItem || ITEM_ACTIONS.keySet().stream().anyMatch(predicate -> predicate.test(item))); }
    private static void item(Item item, BiConsumer<LivingEntity, ItemUse> effect) { ITEM_ACTIONS.put(stack -> stack.is(item), effect); }
    private static void initializeItems() {
        item(Items.CHORUS_FRUIT, (target, use) -> chorus(target, use.user));
        item(Items.ENDER_EYE, (target, use) -> eye(target, use.item, use.user));
        item(Items.FIREWORK_ROCKET, (target, use) -> target.level().addFreshEntity(new FireworkFlight(target, use.item)));
        item(Items.SNOWBALL, (target, use) -> target.addEffect(new MobEffectInstance(VampireCombatService.FREEZE, 40)));
        item(Items.SNOW, (target, use) -> target.addEffect(new MobEffectInstance(VampireCombatService.FREEZE, 80)));
        item(Items.SNOW_BLOCK, (target, use) -> target.addEffect(new MobEffectInstance(VampireCombatService.FREEZE, 120, 1)));
        item(Items.ICE, (target, use) -> target.addEffect(new MobEffectInstance(VampireCombatService.FREEZE, 200, 1)));
        item(Items.PACKED_ICE, (target, use) -> target.addEffect(new MobEffectInstance(VampireCombatService.FREEZE, 200, 2)));
        item(Items.BLUE_ICE, (target, use) -> target.addEffect(new MobEffectInstance(VampireCombatService.FREEZE, 200, 3)));
        item(Items.FIRE_CHARGE, (target, use) -> target.igniteForSeconds(5));
        item(Items.BLAZE_POWDER, (target, use) -> target.igniteForSeconds(10));
        item(Items.BLAZE_ROD, (target, use) -> target.igniteForSeconds(20));
        item(Items.LAVA_BUCKET, (target, use) -> target.igniteForSeconds(20));
        item(Items.GLOWSTONE_DUST, (target, use) -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100)));
        item(Items.SPECTRAL_ARROW, (target, use) -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200)));
        item(Items.GLOWSTONE, (target, use) -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400)));
        item(Items.EXPERIENCE_BOTTLE, (target, use) -> giveXp(target));
        item(Items.MILK_BUCKET, (target, use) -> use.item.copy().finishUsingItem(target.level(), target));
        ITEM_ACTIONS.put(stack -> stack.has(DataComponents.POTION_CONTENTS) && stack.get(DataComponents.POTION_CONTENTS).getAllEffects().iterator().hasNext(),
                (target, use) -> use.item.get(DataComponents.POTION_CONTENTS).getAllEffects().forEach(effect -> target.addEffect(new MobEffectInstance(effect))));
        ITEM_ACTIONS.put(stack -> stack.has(DataComponents.FOOD), (target, use) -> use.item.copy().finishUsingItem(target.level(), target));
        item(Items.ENCHANTED_GOLDEN_APPLE, (target, use) -> VampireLifecycle.onEnchantedApple(target));
    }
    private static void chorus(LivingEntity target, LivingEntity user) {
        if (target instanceof Enderman) { target.heal(6); return; }
        var previous = target.position(); var random = target.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double y = Math.clamp(target.getY() + random.nextInt(16) - 8, 0, target.level().getMaxY());
            var middle = user == null ? previous : target.position().add(user.getLookAngle().scale(12));
            double x = user == null ? previous.x + (random.nextDouble() + 1) * (random.nextBoolean() ? 1 : -1) * 8 : middle.x + (random.nextDouble() - .5) * 8;
            double z = user == null ? previous.z + (random.nextDouble() + 1) * (random.nextBoolean() ? 1 : -1) * 8 : middle.z + (random.nextDouble() - .5) * 8;
            if (target.isPassenger()) target.stopRiding();
            if (target.randomTeleport(x, y, z, true, state -> state.blocksMotion())) {
                var sound = target.getType() == EntityTypes.FOX ? SoundEvents.FOX_TELEPORT : SoundEvents.CHORUS_FRUIT_TELEPORT;
                target.setYRot(random.nextFloat() * 360); target.yRotO = target.getYRot();
                target.level().playSound(null, previous.x, previous.y, previous.z, sound, SoundSource.PLAYERS, 1, 1); target.playSound(sound, 1, 1); break;
            }
        }
    }
    private static void giveXp(LivingEntity target) {
        target.level().levelEvent(2002, target.blockPosition().above(), 3694022);
        int xp = 3 + target.getRandom().nextInt(5) + target.getRandom().nextInt(5);
        if (target instanceof Player player) {
            var mending = target.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
            var repairable = new ArrayList<ItemStack>();
            for (var slot : new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD }) {
                var item = target.getItemBySlot(slot); if (item.isDamaged() && EnchantmentHelper.getItemEnchantmentLevel(mending, item) > 0) repairable.add(item);
            }
            if (!repairable.isEmpty()) { var item = repairable.get(target.getRandom().nextInt(repairable.size())); int repaired = Math.min(xp * 2, item.getDamageValue()); xp -= repaired / 2; item.setDamageValue(item.getDamageValue() - repaired); }
            if (xp > 0) player.giveExperiencePoints(xp);
        } else while (xp > 0) { int amount = ExperienceOrb.getExperienceValue(xp); xp -= amount; target.level().addFreshEntity(new ExperienceOrb(target.level(), target.getX(), target.getY(), target.getZ(), amount)); }
    }
    private static void eye(LivingEntity target, ItemStack item, LivingEntity user) {
        if (!(target.level() instanceof ServerLevel level)) return;
        var destination = level.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, target.blockPosition(), 100, false);
        if (destination == null) return;
        var eye = new EyeFlight(target); eye.setItem(item); eye.signalTo(Vec3.atCenterOf(destination));
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.NEUTRAL, .5F, .4F / (target.getRandom().nextFloat() * .4F + .8F));
        level.levelEvent(1003, target.blockPosition(), 0); level.addFreshEntity(eye);
        if (target instanceof ServerPlayer player) CriteriaTriggers.USED_ENDER_EYE.trigger(player, destination);
        if (user instanceof ServerPlayer player) CriteriaTriggers.USED_ENDER_EYE.trigger(player, destination);
    }
    /** Root calls this from the existing VampireBlood.splash path; do not recursively call that path here. */
    public static void markBlood(ServerLevel level, Vec3 position, double radius, float amount, LivingEntity owner) {
        if (owner == null || !StandActionTraining.unlocked(owner, CrazyDiamondCombatActions.BLOOD_CUTTER)) return;
        var area = new AABB(position.subtract(radius, radius, radius), position.add(radius, radius, radius));
        for (var target : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && !entity.isSpectator())) {
            if (level.clip(new ClipContext(position, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target)).getType() != HitResult.Type.MISS) continue;
            if (target.getRandom().nextFloat() < amount / 5 && CDBloodCutterEntity.canHaveBloodDropsOn(target, owner))
                StandEffectStore.getOrCreate(owner, DRIED_BLOOD, target, CompoundTag::new);
        }
    }
    public static void splashBlood(ServerLevel level, Vec3 position, double radius, float amount, LivingEntity owner) {
        VampireBlood.splash(level, position, radius, amount, owner);
        // Shared VampireBlood.splash integration invokes markBlood exactly once for cutter and ordinary blood alike.
    }
    public static void startImpact(CrazyDiamondEntity stand, LivingEntity target) { StandHeavyImpact.start(stand, userOf(target), stand.attackDamage(), stand.precision()); }
    public static void collide(Entity entity, Vec3 requested, Vec3 actual) { StandHeavyImpact.collide(entity, requested, actual); }
    public static void heavyBlockHit(CrazyDiamondEntity stand, BlockPos pos) { StandHeavyImpact.punchBlock(stand, new BlockHitResult(Vec3.atCenterOf(pos), Direction.getApproximateNearest(stand.getLookAngle()).getOpposite(), pos, false), stand.attackDamage(), stand.precision()); }
    public static void beginAngelo(CrazyDiamondEntity stand, LivingEntity target) {
        var user = userOf(target);
        if (StandHeavyImpact.active(user) && StandEffects.find(stand.owner(), AngeloRockEntity.EFFECT, user.getUUID()).isEmpty())
            StandEffects.add(stand.owner(), AngeloRockEntity.EFFECT, user, new CompoundTag());
    }

    public static boolean stoneMaterial(BlockState state) { return SourceEraMaterials.stone(state); }
    public static boolean hardMaterial(BlockState state) { return SourceEraMaterials.hard(state); }

    public static final class EyeFlight extends EyeOfEnder {
        private Entity inside; private UUID insideUuid;
        public EyeFlight(EntityType<? extends EyeOfEnder> type, Level level) { super(type, level); }
        public EyeFlight(LivingEntity target) { this(CrazyDiamondCombatActions.EYE_INSIDE, target.level()); inside = target; insideUuid = target.getUUID(); setPos(target.getX(), target.getY(.5), target.getZ()); target.startRiding(this, true, true); }
        @Override protected void positionRider(Entity rider, Entity.MoveFunction move) { if (hasPassenger(rider)) move.accept(rider, getX(), getY() + (getBbHeight() - rider.getBbHeight()) / 2, getZ()); }
        @Override public void tick() {
            if (!level().isClientSide()) {
                if (tickCount >= 75) {
                    if (level() instanceof ServerLevel server) for (var passenger : getPassengers()) if (passenger instanceof LivingEntity living) StandDamage.hurt(server, living, damageSources().magic(), 2);
                    playSound(SoundEvents.ENDER_EYE_DEATH, 1, 1); level().levelEvent(2003, blockPosition(), 0); discard(); return;
                }
                if (inside == null && insideUuid != null) inside = level().getEntity(insideUuid);
                if (inside != null && inside.getVehicle() != this) inside.startRiding(this, true, true);
            }
            super.tick();
        }
        @Override public boolean shouldRender(double x, double y, double z) { return !isVehicle() && super.shouldRender(x, y, z); }
        @Override protected net.minecraft.network.chat.Component getTypeName() { return EntityTypes.EYE_OF_ENDER.getDescription(); }
    }
    public static final class FireworkFlight extends FireworkRocketEntity {
        private Entity inside; private UUID insideUuid;
        public FireworkFlight(EntityType<? extends FireworkRocketEntity> type, Level level) { super(type, level); }
        public FireworkFlight(LivingEntity target, ItemStack item) {
            this(CrazyDiamondCombatActions.FIREWORK_INSIDE, target.level()); inside = target; insideUuid = target.getUUID(); setPos(target.getX(), target.getY(.5), target.getZ()); target.startRiding(this, true, true);
            entityData.set(FireworkRestorationAccess.jojo$restorationFireworkItem(), item.copy());
            ((FireworkRestorationAccess) this).jojo$restorationLifetime((1 + item.getOrDefault(DataComponents.FIREWORKS, new Fireworks(0, List.of())).flightDuration()) * 10);
            setDeltaMovement(random.nextGaussian() * .001, .05, random.nextGaussian() * .001);
        }
        @Override protected void positionRider(Entity rider, Entity.MoveFunction move) { if (hasPassenger(rider)) move.accept(rider, getX(), getY() + (getBbHeight() - rider.getBbHeight()) / 2, getZ()); }
        @Override public void tick() {
            if (!level().isClientSide()) { if (inside == null && insideUuid != null) inside = level().getEntity(insideUuid); if (inside != null && inside.getVehicle() != this) inside.startRiding(this, true, true); }
            super.tick();
        }
        @Override public boolean shouldRender(double x, double y, double z) { return !isVehicle() && super.shouldRender(x, y, z); }
        @Override protected net.minecraft.network.chat.Component getTypeName() { return EntityTypes.FIREWORK_ROCKET.getDescription(); }
    }
    private CDRecoveryModifiers() { }
}
