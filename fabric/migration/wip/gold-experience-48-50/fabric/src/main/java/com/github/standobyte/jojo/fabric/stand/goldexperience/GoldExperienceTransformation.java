package com.github.standobyte.jojo.fabric.stand.goldexperience;

import com.github.standobyte.jojo.core.StandStats;
import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** GoldExperienceCreateLifeform and its continuous effect, retaining source material as one transaction. */
public final class GoldExperienceTransformation {
    public static final Identifier CREATE = RipplesFabric.id("gold_experience_create_lifeform");
    public static final Identifier REVERT = RipplesFabric.id("gold_experience_revert_lifeform");
    public static final Identifier CREATED = RipplesFabric.id("ge_created_lifeform");
    public static final EntityType<GETransformationEntity> TYPE;
    static {
        var key = ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("ge_lifeform"));
        TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<GETransformationEntity>of(GETransformationEntity::new, MobCategory.MISC)
                .sized(1, 1).noSummon().build(key));
    }
    public static final AttachmentType<Integer> ANIMAL_AGE = AttachmentRegistry.<Integer>builder().initializer(() -> 0)
            .persistent(Codec.intRange(0, Integer.MAX_VALUE)).copyOnDeath().buildAndRegister(RipplesFabric.id("ge_animal_age_cooldown"));
    public static final AttachmentType<List<MobEffectInstance>> PRODUCT_EFFECTS = AttachmentRegistry.<List<MobEffectInstance>>builder().initializer(List::of)
            .persistent(MobEffectInstance.CODEC.listOf(0, 128)).buildAndRegister(RipplesFabric.id("ge_product_effects"));
    public static final AttachmentType<UUID> NEUTRAL_OWNER = AttachmentRegistry.<UUID>builder().persistent(UUIDUtil.CODEC)
            .buildAndRegister(RipplesFabric.id("ge_neutral_owner"));
    public interface EquipmentDrop { void jojo$dropGEEquipment(ServerLevel level); }
    public interface TargetGoals { GoalSelector jojo$geTargetSelector(); }
    public interface TurtleBridge {
        boolean isTurtle(Entity entity);
        void created(Entity turtle, LivingEntity owner, GETransformationEntity morph, Entity formerItemHolder);
        void reverseUnspawned(Entity turtle);
        void beforeRevert(Entity turtle);
    }
    private static TurtleBridge turtleBridge;
    public static void registerTurtleBridge(TurtleBridge bridge) { if (turtleBridge != null) throw new IllegalStateException("GE turtle bridge already registered"); turtleBridge = Objects.requireNonNull(bridge); }
    static TurtleBridge turtle() {
        return new TurtleBridge() {
            @Override public boolean isTurtle(Entity entity) { return turtleBridge != null && entity != null && turtleBridge.isTurtle(entity); }
            @Override public void created(Entity entity, LivingEntity user, GETransformationEntity morph, Entity holder) { if (isTurtle(entity)) turtleBridge.created(entity, user, morph, holder); }
            @Override public void reverseUnspawned(Entity entity) { if (isTurtle(entity)) turtleBridge.reverseUnspawned(entity); }
            @Override public void beforeRevert(Entity entity) { if (isTurtle(entity)) turtleBridge.beforeRevert(entity); }
        };
    }
    private static final TagKey<Item> RAW_FISH = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("forge", "raw_fishes"));
    private static final Set<Entity> REFLECTING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Mob> TARGET_GOAL_INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean initialized;
    public static void initialize() {
        if (initialized) return; initialized = true;
        GoldExperienceMorphPayload.initialize(); GoldExperienceItemActions.initialize(); GEAnimalProducts.initialize();
        StandEffects.register(CREATED, new StandEffects.Handler() {
            @Override public void start(LivingEntity user, StandEffects.Entry effect, Entity target) { prepareLifeform(user, target, GETransformationData.read(effect.data(), user.level())); }
            @Override public boolean shouldClearTarget(Entity target) { return false; }
            @Override public boolean needsTarget() { return true; }
            @Override public StandEffects.Entry tick(LivingEntity user, StandEffects.Entry effect, Entity target) {
                // A removed morph can have finished while its owner was unloaded. The newborn UUID is known before spawning.
                if (target == null) {
                    Entity savedTarget = effect.target().map(user.level()::getEntity).orElse(null);
                    if (savedTarget instanceof LivingEntity living && living.isDeadOrDying()) return null;
                    Entity newborn = effect.data().read("TargetLifeform", UUIDUtil.CODEC).map(user.level()::getEntity).orElse(null);
                    if (newborn != null) { target = newborn; effect = effect.withTarget(newborn); prepareLifeform(user, newborn, GETransformationData.read(effect.data(), user.level())); }
                }
                if (target instanceof LivingEntity living && living.isDeadOrDying()) return null;
                // Active source uses the morph's current dimensions until the newborn replaces it.
                if (!consume(user, tickingCost(target))) return null;
                if (target != null) {
                    if (target.distanceToSqr(user) > 128 * 128) return null;
                    var source = GETransformationData.read(effect.data(), user.level());
                    Entity followed = source.followTarget() == null ? null : user.level().getEntity(source.followTarget());
                    if (followed != null) {
                        if (source.followMode() == GETransformationData.FollowMode.DELIVERY && followed.distanceToSqr(target) < 4) return null;
                        if (target instanceof Mob mob && !mob.isAggressive()) mob.getNavigation().moveTo(followed, 1);
                    }
                }
                return effect;
            }
            @Override public void stop(LivingEntity user, StandEffects.Entry effect, Entity target) {
                if (target != null) GETransformationEntity.turnBack(target, GETransformationData.read(effect.data(), user.level()), user);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int age = player.getAttachedOrCreate(ANIMAL_AGE); if (age > 0) player.setAttached(ANIMAL_AGE, age - 1);
            }
        });
    }
    public static float tickingCost(Entity target) {
        if (target == null) return .2F;
        double strength = attackStrength(target); float multiplier = Mth.clamp(volume(target), 1, 3);
        if (strength > 0) multiplier *= Mth.clamp(strength, 2, 6) * .45 + .3;
        return .2F * multiplier;
    }
    public static double attackStrength(Entity target) {
        return target instanceof LivingEntity living && living.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? living.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
    }
    public static float volume(Entity target) { return target.getBbWidth() * target.getBbWidth() * target.getBbHeight(); }
    public static int creationTicks(LivingEntity user, Entity target, boolean useNativeHabitat) {
        double speed = StandStats.GOLD_EXPERIENCE.speed(user.getAttachedOrCreate(StandAttachments.PROGRESS).development());
        double ticks = 240 / Math.max(speed, 1) + Math.ceil(volume(target) * (1 + attackStrength(target) * .125) * Mth.clamp(100 - speed * 2, 0, 100));
        if (useNativeHabitat && LifeformsEncountered.isNative(user, target.getType())) ticks *= .5;
        return Math.max(1, (int) ticks);
    }
    public static boolean consume(LivingEntity user, float amount) {
        if (user instanceof Player player && StandConfig.staminaFree(player)) return true;
        var progress = user.getAttachedOrCreate(StandAttachments.PROGRESS);
        boolean enough = progress.stamina() >= amount || user instanceof Player player && StandAttachments.resolve(player).isBurst();
        user.setAttached(StandAttachments.PROGRESS, progress.withStamina(Math.max(0, progress.stamina() - amount))); return enough;
    }
    public static boolean livingItem(ItemStack item) {
        if (item.isEmpty()) return false;
        if (item.getItem() instanceof BlockItem block) return HamonCharges.livingBlock(block.getBlock().defaultBlockState());
        return item.getItem() instanceof EggItem || item.is(RAW_FISH) || item.is(Items.COD) || item.is(Items.SALMON) || item.is(Items.TROPICAL_FISH)
                || item.is(Items.PUFFERFISH) || item.getItem() instanceof MobBucketItem
                || BuiltInRegistries.ITEM.getKey(item.getItem()).equals(RipplesFabric.id("gold_experience_body_tissue"));
    }
    public static boolean canGiveLifeTo(ItemStack item) { return !livingItem(item) || item.getItem() instanceof MobBucketItem; }
    public static boolean canCreate(ServerPlayer player, LifeformSubtypes.Key selection) {
        return LifeformsEncountered.eligible(player, selection) && StandEffects.entries(player).size() < 128
                && StandEffectStore.targetsInRange(player, CREATED, -1).count() < 16;
    }
    public static Entity createLifeform(ServerPlayer user, LifeformSubtypes.Key type) {
        Entity entity = LifeformSubtypes.create(type, user.level()).orElse(null); if (entity == null) return null;
        // Empty loot persists independently of the source effect, just as DeathLootTable in the original spawn NBT.
        var save = GETransformationData.saveEntity(entity); save.putString("DeathLootTable", "minecraft:empty");
        entity.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, user.level().registryAccess(), save));
        if (entity instanceof AbstractPiglin piglin && !user.level().dimensionType().piglinSafe()) piglin.setImmuneToZombification(true);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(user.level(), user.level().getCurrentDifficultyAt(user.blockPosition()), EntitySpawnReason.COMMAND, null);
            for (EquipmentSlot slot : EquipmentSlot.values()) mob.setItemSlot(slot, ItemStack.EMPTY);
            if (mob instanceof AgeableMob animal) {
                int age = user.getAttachedOrCreate(ANIMAL_AGE); user.setAttached(ANIMAL_AGE, Math.min(Integer.MAX_VALUE - 3000, age) + 3000); animal.setAge(age);
                if (animal instanceof AbstractHorse horse) horse.setTemper(0);
            }
        }
        return entity;
    }
    private record Origin(GETransformationData data, Vec3 position, float yaw, float pitch, Vec3 motion, int fire,
                          LivingEntity host, Entity formerHolder, Component customName, Runnable consume) { }
    public static Optional<GETransformationEntity> create(ServerPlayer user, LifeformSubtypes.Key selection, Optional<UUID> selectedMark) {
        if (!canCreate(user, selection)) return Optional.empty();
        Entity lifeform = createLifeform(user, selection); if (lifeform == null) return Optional.empty();
        StandManifestation stand = StandService.currentManifestation(user); Entity performer = stand != null && stand.manuallyControlled() ? stand.entity() : user;
        Origin origin = null;
        if (selectedMark.isPresent()) {
            var marked = GoldExperienceItemActions.selected(user, selectedMark.get());
            if (marked.isEmpty()) return Optional.empty();
            origin = fromMarked(user, marked.get());
        }
        HitResult aimed = aim(user, performer);
        if (origin == null && aimed instanceof EntityHitResult hit) origin = fromEntity(user, hit.getEntity(), () -> hit.getEntity().discard());
        if (origin == null && !user.getOffhandItem().isEmpty() && canGiveLifeTo(user.getOffhandItem())) {
            ItemStack held = user.getOffhandItem(); Vec3 look = performer.getLookAngle();
            Vec3 pos = performer.position().add(new Vec3(look.x, 0, look.z).normalize().scale(lifeform.getBbWidth() + 1));
            origin = fromInventory(user, held.copyWithCount(1), user, user.blockPosition(), pos, performer.getYRot(), null, () -> held.shrink(1));
        }
        if (origin == null && aimed instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) origin = fromBlock(user, hit.getBlockPos(), lifeform, performer.getYRot());
        if (origin == null || !origin.data.hasMaterial() || origin.data.write(user.level()).sizeInBytes() > 65000) return Optional.empty();
        int ticks = creationTicks(user, lifeform, false); // Active source overload checks target's player capability, which ordinary lifeforms lack.
        var morph = new GETransformationEntity(user.level()).configure(origin.data, lifeform, user, ticks);
        morph.snapTo(origin.position, origin.yaw, origin.pitch); morph.setDeltaMovement(origin.motion); morph.setRemainingFireTicks(origin.fire);
        if (origin.customName != null) morph.setCustomName(origin.customName);
        LivingEntity recent = lastHurt(user, stand == null ? null : stand.entity());
        if (recent != null && (origin.data.followTarget() == null || recent.getUUID().equals(origin.data.followTarget())
                && origin.data.followMode() != GETransformationData.FollowMode.AGGRO_TRACK)) origin.data.follow(recent.getUUID(), GETransformationData.FollowMode.AGGRO_FORGETFUL, user);
        lifeform.snapTo(morph.position(), morph.getYRot(), morph.getXRot()); lifeform.setYHeadRot(morph.getYRot());
        if (morph.hasCustomName()) lifeform.setCustomName(morph.getCustomName());
        if (!user.level().addFreshEntity(morph)) return Optional.empty();
        origin.consume.run();
        if (origin.host != null) morph.withHost(origin.host);
        CompoundTag effectData = origin.data.write(user.level()); effectData.store("TargetLifeform", UUIDUtil.CODEC, lifeform.getUUID());
        StandEffects.add(user, CREATED, morph, effectData);
        prepareLifeform(user, lifeform, origin.data);
        if (selectedMark.isPresent()) GoldExperienceItemActions.clearMarks(user);
        turtle().created(lifeform, user, morph, origin.formerHolder);
        if (!user.isCreative()) { morph.actionCooldown = Math.max(ticks / 2, 1); AbilityCooldowns.start(user, CREATE, morph.actionCooldown); }
        return Optional.of(morph);
    }
    private static LivingEntity lastHurt(LivingEntity user, LivingEntity stand) {
        LivingEntity fromUser = user.getLastHurtMob(), fromStand = stand == null ? null : stand.getLastHurtMob();
        if (fromUser == null) return fromStand;
        return fromStand == null || user.getLastHurtMobTimestamp() >= stand.getLastHurtMobTimestamp() ? fromUser : fromStand;
    }
    public static HitResult aim(ServerPlayer user, Entity performer) {
        Vec3 start = performer.getEyePosition(), end = start.add(performer.getLookAngle().scale(10));
        HitResult result = user.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, performer));
        double closest = result.getType() == HitResult.Type.MISS ? 100 : start.distanceToSqr(result.getLocation());
        for (Entity entity : user.level().getEntities(performer, new AABB(start, end).inflate(1), GoldExperienceTransformation::validEntityMaterial)) {
            var box = entity.getBoundingBox().inflate(entity.getPickRadius()); var hit = box.clip(start, end);
            double distance = box.contains(start) ? 0 : hit.map(start::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
            double range = entity instanceof ItemEntity ? 100 : 64;
            if (distance < closest && distance <= range) { result = new EntityHitResult(entity, hit.orElse(start)); closest = distance; }
        }
        return result;
    }
    public static boolean validEntityMaterial(Entity entity) {
        if (!entity.isAlive() || entity instanceof LivingEntity) return false;
        if (entity instanceof ItemEntity item) return !livingItem(item.getItem());
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return entity instanceof net.minecraft.world.entity.item.PrimedTnt || entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal
                || entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat || type.equals(RipplesFabric.id("road_roller"));
    }
    private static Origin fromEntity(ServerPlayer user, Entity entity, Runnable remove) {
        CompoundTag saved = GETransformationData.saveEntity(entity); if (saved.sizeInBytes() > 65000) return null;
        Entity copy = GETransformationData.loadEntity(saved, user.level()); if (copy == null) return null;
        if (copy instanceof ItemEntity item) item.getItem().remove(TrackedItemIdentity.COMPONENT);
        if (copy instanceof AbstractArrow arrow) arrow.getPickupItemStackOrigin().remove(TrackedItemIdentity.COMPONENT);
        var data = new GETransformationData().entity(copy);
        if (entity instanceof ItemEntity item && item.getOwner() != null) data.follow(item.getOwner().getUUID(), GETransformationData.FollowMode.TRACK, user);
        Vec3 motion = entity instanceof AbstractArrow arrow && arrow.isInGround() ? Vec3.ZERO : entity.getDeltaMovement();
        return new Origin(data, entity.position(), entity.getYRot(), entity.getXRot(), motion, entity.getRemainingFireTicks(), null, null, entity.getCustomName(), remove);
    }
    private static Origin fromInventory(ServerPlayer user, ItemStack material, LivingEntity thrower, BlockPos fishPosition, Vec3 pos, float yaw, Entity formerHolder, Runnable remove) {
        ItemStack copy = material.copyWithCount(1); copy.remove(TrackedItemIdentity.COMPONENT); Item item = copy.getItem();
        Entity entity;
        if (item instanceof BucketItem bucket) entity = new ItemEntity(user.level(), 0, 0, 0,
                bucket.getContent().getBucket() == Items.AIR ? copy : new ItemStack(bucket.getContent().getBucket()));
        else if (item instanceof SplashPotionItem) entity = new ThrownSplashPotion(user.level(), thrower, copy);
        else if (item instanceof LingeringPotionItem) entity = new ThrownLingeringPotion(user.level(), thrower, copy);
        else if (copy.is(Items.ENDER_PEARL)) entity = new ThrownEnderpearl(user.level(), thrower, copy);
        else entity = new ItemEntity(user.level(), 0, 0, 0, copy);
        var data = new GETransformationData().entity(entity);
        Runnable consume = () -> {
            if (item instanceof BucketItem bucket && bucket.getContent().getBucket() != Items.AIR) bucket.checkExtraContent(thrower, user.level(), material, fishPosition);
            if (item instanceof MolotovItem && thrower instanceof Player player && MolotovItem.useFire(player, user.level())) data.entity(new MolotovEntity(user.level(), thrower, copy));
            remove.run();
        };
        return new Origin(data, pos, yaw, 0, Vec3.ZERO, 0, null, formerHolder, material.get(DataComponents.CUSTOM_NAME), consume);
    }
    private static Origin fromMarked(ServerPlayer user, ItemTrackingService.Resolved marked) {
        var kind = marked.location().kind(); Entity holder = marked.entity(); UUID id = marked.identity().id();
        Runnable extract = () -> ItemTrackingService.extract(user, id).orElseThrow(() -> new IllegalStateException("Validated GE material moved during server action"));
        if (kind == ItemTrackingService.Kind.ENTITY_IS_ITEM && holder != null) return fromEntity(user, holder, extract);
        if ((kind == ItemTrackingService.Kind.STUCK_ARROW || kind == ItemTrackingService.Kind.STUCK_KNIFE) && holder instanceof LivingEntity host) {
            AbstractArrow projectile = kind == ItemTrackingService.Kind.STUCK_ARROW ? new Arrow(user.level(), user, new ItemStack(Items.ARROW), null) : new KnifeEntity(user.level(), user);
            projectile.pickup = AbstractArrow.Pickup.ALLOWED;
            var data = new GETransformationData().entity(projectile).follow(host.getUUID(), GETransformationData.FollowMode.AGGRO_TRACK, user);
            return new Origin(data, host.position(), host.getYRot(), host.getXRot(), Vec3.ZERO, 0, host, null, null, extract);
        }
        if (kind == ItemTrackingService.Kind.ENTITY_HAS_ITEM && holder != null) {
            Vec3 pos = holder.position();
            if (holder instanceof net.minecraft.world.entity.decoration.ItemFrame) pos = pos.add(holder.getLookAngle().scale(.5));
            return fromInventory(user, marked.stack(), holder instanceof LivingEntity living ? living : user, holder.blockPosition(), pos, holder.getYRot(), holder != user ? holder : null, extract);
        }
        if (kind == ItemTrackingService.Kind.BLOCK_HAS_ITEM && marked.location().block().isPresent()) {
            BlockPos pos = marked.location().block().get();
            return fromInventory(user, marked.stack(), user, pos.above(), new Vec3(pos.getX(), pos.getY() + 1, pos.getZ()), 0, null, extract);
        }
        return null;
    }
    private static Origin fromBlock(ServerPlayer user, BlockPos pos, Entity lifeform, float yaw) {
        var level = user.level(); BlockState state = level.getBlockState(pos); var blockEntity = level.getBlockEntity(pos);
        if (state.isAir() || HamonCharges.livingBlock(state) || !level.getGameRules().get(JojoGameRules.BREAK_BLOCKS)
                || !user.mayBuild() || !level.mayInteract(user, pos) || !user.isCreative() && state.getDestroySpeed(level, pos) < 0
                || !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, user, pos, state, blockEntity)) return null;
        var data = new GETransformationData().block(state, pos, blockEntity);
        if (BuiltInRegistries.ENTITY_TYPE.getKey(lifeform.getType()).getPath().contains("pigeon") && blockEntity instanceof Container inventory) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack item = inventory.getItem(slot); Component name = item.get(DataComponents.CUSTOM_NAME);
                if (item.is(Items.NAME_TAG) && name != null && !name.getString().trim().isEmpty()) {
                    String playerName = name.getString(); var player = level.getServer().getPlayerList().getPlayerByName(playerName);
                    UUID uuid = player != null ? player.getUUID() : UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    data.follow(uuid, GETransformationData.FollowMode.DELIVERY, user); break;
                }
            }
        }
        return new Origin(data, Vec3.atLowerCornerOf(pos), yaw, 0, Vec3.ZERO, 0, null, null, null, () -> removeBlockMaterial(level, pos));
    }
    private static void removeBlockMaterial(ServerLevel level, BlockPos pos) {
        // The block entity has already been serialized; removing it first suppresses vanilla container spilling.
        level.removeBlockEntity(pos); level.removeBlock(pos, false);
    }
    public static boolean revert(ServerPlayer user, UUID effectId) {
        var effect = StandEffectStore.byId(user, effectId).filter(entry -> entry.type().equals(CREATED)).orElse(null);
        Entity target = effect == null ? null : StandEffects.target(user, effect);
        if (target == null || target.distanceToSqr(user) >= 128 * 128) return false;
        if (target instanceof GETransformationEntity morph) AbilityCooldowns.start(user, CREATE, Math.max(0, AbilityCooldowns.remaining(user, CREATE) - morph.actionCooldown));
        StandEffects.remove(user, effectId); return true;
    }
    static void born(GETransformationEntity morph, Entity newborn) {
        LivingEntity user = morph.owner(); if (user == null) return;
        var entries = new ArrayList<>(StandEffects.entries(user));
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.type().equals(CREATED) && entry.target().filter(morph.getUUID()::equals).isPresent()) entries.set(i, entry.withTarget(newborn));
        }
        user.setAttached(StandEffects.DATA, new StandEffects.State(entries)); StandEffectStore.ownerChanged(user);
        prepareLifeform(user, newborn, morph.source());
    }
    private static void prepareLifeform(LivingEntity user, Entity target, GETransformationData data) {
        if (target == null) return;
        if (target instanceof Mob) target.setAttached(NEUTRAL_OWNER, user.getUUID());
        var effects = data.productEffects(user.level()); if (!effects.isEmpty()) target.setAttached(PRODUCT_EFFECTS, effects);
        if (target instanceof Mob mob && data.followTarget() != null) {
            if (data.followMode() == GETransformationData.FollowMode.AGGRO_TRACK && TARGET_GOAL_INSTALLED.add(mob))
                ((TargetGoals) mob).jojo$geTargetSelector().addGoal(0, new SpecificTarget(mob, data.followTarget()));
            else if (data.followMode() == GETransformationData.FollowMode.AGGRO_FORGETFUL && mob.getTarget() == null
                    && target.level().getEntity(data.followTarget()) instanceof LivingEntity followed) mob.setTarget(followed);
        }
    }
    public static boolean allowNaturalTarget(Mob mob, LivingEntity candidate) { return !Objects.equals(mob.getAttached(NEUTRAL_OWNER), candidate.getUUID()); }
    private static final class SpecificTarget extends TargetGoal {
        private final UUID targetId;
        private SpecificTarget(Mob mob, UUID targetId) { super(mob, false, false); this.targetId = targetId; }
        @Override public boolean canUse() { return mob.level().getEntity(targetId) instanceof LivingEntity living && living.isAlive(); }
        @Override public void start() { if (mob.level().getEntity(targetId) instanceof LivingEntity living) { mob.setTarget(living); targetMob = living; } super.start(); }
    }
    public static boolean reflect(LivingEntity target, DamageSource source, float amount) {
        if (!(target.level() instanceof ServerLevel level) || REFLECTING.contains(target) || !(source.getEntity() instanceof LivingEntity attacker)) return false;
        var effect = StandEffectStore.targeting(target, CREATED).findFirst();
        if (effect.isEmpty() || StandEffectStore.targeting(attacker, CREATED).findAny().isPresent()) return false;
        REFLECTING.add(attacker);
        try { attacker.hurtServer(level, source, amount); }
        finally { REFLECTING.remove(attacker); }
        return true;
    }
    public static void dropEquipment(Entity entity) {
        if (entity instanceof LivingEntity living && entity.level() instanceof ServerLevel level) {
            ((EquipmentDrop) living).jojo$dropGEEquipment(level);
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals("fox") && !living.getMainHandItem().isEmpty()) {
                living.spawnAtLocation(level, living.getMainHandItem()); living.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
    }
    public static void bleedHost(LivingEntity host, Vec3 offset) { host.addEffect(new MobEffectInstance(VampireBlood.BLEEDING, 200, 1, false, false, true)); }
    public static boolean mayRestore(LivingEntity owner, ServerLevel level, BlockPos pos, BlockState state) {
        return !(owner instanceof ServerPlayer player) || player.mayBuild() && level.mayInteract(player, pos);
    }
    public static void restoredBurningBlock(ServerLevel level, BlockPos pos, BlockState state) {
        // Source calls catchFire instead of placing any burning block. Vanilla TNT overrides that hook; ordinary blocks do nothing.
        if (state.is(Blocks.TNT)) TntBlock.prime(level, pos);
    }
    private GoldExperienceTransformation() { }
}
