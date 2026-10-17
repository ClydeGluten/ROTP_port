package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.mixin.TimeSkipMobAccessor;
import com.github.standobyte.jojo.fabric.mixin.VampireTargetConditionsAccess;
import com.github.standobyte.jojo.fabric.mixin.VampireTargetGoalAccess;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Original VampirismZombieSummon and VampirismUtil's narrowly scoped mob-goal edits. */
public final class VampireZombieService {
    public static final EntityType<HungryZombieEntity> HUNGRY_ZOMBIE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            RipplesFabric.id("hungry_zombie"), EntityType.Builder.of(HungryZombieEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("hungry_zombie"))));
    public static final Item SPAWN_EGG = registerEgg();
    public static final AttachmentType<Integer> SUMMON_COOLDOWN = AttachmentRegistry.<Integer>builder()
            .initializer(() -> 0).persistent(Codec.intRange(0, 100)).copyOnDeath()
            .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("vampire_zombie_summon_cooldown"));
    private static final Set<Mob> EDITED = Collections.newSetFromMap(new WeakHashMap<>());

    private static Item registerEgg() {
        var key = ResourceKey.create(Registries.ITEM, RipplesFabric.id("hungry_zombie_spawn_egg"));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(HUNGRY_ZOMBIE)));
    }
    public static void initialize() {
        FabricDefaultAttributeRegistry.register(HUNGRY_ZOMBIE, HungryZombieEntity.createAttributes());
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(SPAWN_EGG));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> { if (entity instanceof Mob mob) editMobGoals(mob); });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                int cooldown = cooldown(player);
                if (cooldown > 0 && !TimeStopService.isFrozen(player)) player.setAttached(SUMMON_COOLDOWN, cooldown - 1);
            }
        });
    }
    public static int cooldown(Player player) { return player.getAttachedOrCreate(SUMMON_COOLDOWN); }
    public static boolean summon(ServerPlayer player) {
        var progress = VampireService.progress(player);
        if (!progress.fullPower() || !player.isAlive() || player.isSpectator() || TimeStopService.isFrozen(player)
                || VampireLifecycle.curingStage(player) > 3 || cooldown(player) > 0
                || player.getAttachedOrCreate(VampireAttachments.HUD).held() == VampireHudState.Held.GIFT
                || !player.isCreative() && progress.blood() < 100) return false;
        var level = player.level();
        int difficulty = level.getDifficulty().getId();
        if (difficulty == 0) return false;
        var area = new AABB(player.getX() - 16, level.getMinY(), player.getZ() - 16,
                player.getX() + 16, level.getMaxY() + 1, player.getZ() + 16);
        if (level.getEntitiesOfClass(HungryZombieEntity.class, area).size() > difficulty * 10) {
            player.sendOverlayMessage(Component.translatable("jojo.message.action_condition.zombies_limit"));
            return false;
        }
        for (int i = 0; i < difficulty; i++) {
            var zombie = new HungryZombieEntity(HUNGRY_ZOMBIE, level);
            zombie.setSummonedFromAbility();
            zombie.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            zombie.setOwner(player);
            level.addFreshEntity(zombie);
        }
        VampireService.spend(player, 100);
        if (!player.isCreative()) player.setAttached(SUMMON_COOLDOWN, 100);
        VampireAdvancements.onSummoned(player, difficulty);
        return true;
    }
    public static boolean onDrainedKill(ServerPlayer attacker, LivingEntity victim) {
        if (!victim.isDeadOrDying()) return false;
        boolean created = HungryZombieEntity.createZombie(attacker.level(), attacker, victim, false);
        VampireAdvancements.onDrainedKill(attacker, victim, created);
        return created;
    }
    /** Evil Atmosphere only calls this on Hard. The original entitiesAround helper uses a cube, not a sphere. */
    public static void buffOwned(ServerPlayer player, double range) {
        var center = player.getBoundingBox().getCenter();
        for (var zombie : player.level().getEntitiesOfClass(HungryZombieEntity.class,
                new AABB(center, center).inflate(range), entity -> entity.isEntityOwner(player))) {
            zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 1));
            zombie.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 300, 0));
            zombie.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 300, 0));
        }
    }
    /** The zombie's bonus bite uses canBleed, which is broader than vampire drain eligibility. */
    public static boolean canBleed(LivingEntity entity) {
        return VampireBlood.canBleed(entity);
    }
    static boolean drainBlood(LivingEntity attacker, LivingEntity target, float amount) {
        if (!(attacker.level() instanceof ServerLevel level)) return false;
        var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, RipplesFabric.id("blood_drain")));
        if (!target.hurtServer(level, new DamageSource(type, attacker), amount)) return false;
        int amplifier = level.getDifficulty().getId() - 1;
        if (amplifier >= 0) {
            for (var effect : java.util.List.of(MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS, MobEffects.NAUSEA)) {
                var old = target.getEffect(effect);
                target.addEffect(new MobEffectInstance(effect, (int) Math.floor(20 * amount)
                        + (old == null ? 0 : old.getDuration()), amplifier));
            }
        }
        return true;
    }
    private static void editMobGoals(Mob mob) {
        if (!EDITED.add(mob)) return;
        var goals = ((TimeSkipMobAccessor) mob).jojo$targetSelector();
        if (mob.getType().getCategory() == MobCategory.MONSTER) {
            for (var wrapped : goals.getAvailableGoals()) {
                if (!(wrapped.getGoal() instanceof NearestAttackableTargetGoal<?> goal)) continue;
                var access = (VampireTargetGoalAccess) goal;
                if (access.jojo$targetType() != Player.class) continue;
                var conditions = access.jojo$targetConditions();
                var original = ((VampireTargetConditionsAccess) conditions).jojo$selector();
                conditions.selector((candidate, level) -> (original == null || original.test(candidate, level))
                        && (VampireConfig.server().vampiresAggroMobs() || !(candidate instanceof Player player)
                        || !SpeciesHooks.neutralToMonsters(player)));
            }
        } else if (mob instanceof IronGolem) {
            goals.addGoal(3, new NearestAttackableTargetGoal<>(mob, Player.class, 5, false, false,
                    (candidate, level) -> candidate instanceof Player player && SpeciesHooks.targetedByGolem(player)));
        }
    }
    private VampireZombieService() { }
}
