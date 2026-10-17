package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.mixin.restoration.*;
import com.github.standobyte.jojo.fabric.pillarman.PossessionService;
import com.github.standobyte.jojo.fabric.stand.StandHeavyImpact;
import com.github.standobyte.jojo.fabric.stand.restoration.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Source rock formation, retained mob loot and non-exitable player possession. */
public final class AngeloRockEntity extends Entity implements PossessionService.ExternalHost {
    public static final Identifier EFFECT = RipplesFabric.id("turn_into_angelo_rock");
    public static boolean cancelPlayerHitSound;
    public static Mob mobLootFortune;
    private static boolean keepMobsInside = true, initialized;
    private static final EntityDataAccessor<Optional<BlockPos>> ATTACH = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> CREATED = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ANIMATION = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.INT),
            TARGET = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.INT),
            LOWER = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.INT), UPPER = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.INT);
    private static final Codec<BrokenBlockRecord> BLOCK_CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("Pos").forGetter(b -> b.pos), BlockState.FULL_CODEC.fieldOf("State").forGetter(b -> b.state),
            ItemStack.CODEC.listOf().fieldOf("Drops").forGetter(b -> b.drops), Codec.BOOL.optionalFieldOf("Keep", false).forGetter(b -> b.keep),
            Codec.INT.optionalFieldOf("TickCount", 0).forGetter(BrokenBlockRecord::tickCount), Codec.INT.optionalFieldOf("Xp", 0).forGetter(BrokenBlockRecord::droppedXp)
    ).apply(i, BrokenBlockRecord::new));
    private final Map<BlockPos, BrokenBlockRecord> blocks = new HashMap<>();
    private final List<ItemStack> drops = new ArrayList<>();
    private final List<Integer> responses = new ArrayList<>();
    private boolean keepMob, useHurtSound, dropBlocks = true, breaking;
    private Mob mob; private DamageSource lastAttack;
    public AngeloRockEntity(EntityType<? extends AngeloRockEntity> type, Level level) { super(type, level); }
    public static AngeloRockEntity form(ServerLevel level, LivingEntity target, BrokenBlockRecord lower, BrokenBlockRecord upper, boolean keepMob) {
        var rock = new AngeloRockEntity(CrazyDiamondCombatActions.ANGELO_ENTITY, level);
        rock.blocks.put(lower.pos, lower); rock.blocks.put(upper.pos, upper); rock.keepMob = keepMob;
        rock.setPos(Vec3.atBottomCenterOf(lower.pos)); rock.setYRot(Direction.fromYRot(target.getYRot()).toYRot());
        rock.entityData.set(ANIMATION, 40); rock.entityData.set(TARGET, target.getId());
        rock.entityData.set(LOWER, Block.getId(lower.state)); rock.entityData.set(UPPER, Block.getId(upper.state));
        level.addFreshEntity(rock); return rock;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ATTACH, Optional.empty()); builder.define(CREATED, false); builder.define(DAMAGE, 0F); builder.define(ANIMATION, 0);
        builder.define(TARGET, -1); builder.define(LOWER, Block.getId(Blocks.STONE.defaultBlockState())); builder.define(UPPER, Block.getId(Blocks.STONE.defaultBlockState()));
    }
    public BlockState lowerBlock() { return Block.stateById(entityData.get(LOWER)); }
    public BlockState upperBlock() { return Block.stateById(entityData.get(UPPER)); }
    public float damageRatio() { return entityData.get(DAMAGE) / (20 * Math.max(blocks.size(), 2)); }
    public float creationProgress(float partialTick) { return entityData.get(ANIMATION) <= 0 ? 1 : (40 - entityData.get(ANIMATION) + partialTick) / 40F; }
    public boolean isFullyFormed() { return entityData.get(ANIMATION) <= 0; }
    public Entity formationTarget() { return level().getEntity(entityData.get(TARGET)); }
    @Override public boolean isEntrapped(ServerPlayer player) {
        if (!isAlive() || player.level() != level()) return false;
        var state = PossessionService.state(player);
        return formationTarget() == player || entityData.get(CREATED) && state != null && !state.asAlive() && state.host().equals(getUUID());
    }
    @Override public void grunt(ServerPlayer player) { player.playSound(JojoSounds.get("angelo_rock_grunt"), 1, 1); }
    @Override public boolean isPickable() { return true; }
    @Override public boolean canBeCollidedWith(Entity entity) { return isAlive(); }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (!isFullyFormed()) return InteractionResult.FAIL;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (mob != null) mob.setPos(position());
        String stand = player.getAttachedOrCreate(StandAttachments.PROGRESS).standId();
        String sound = stand.equals("jojo:crazy_diamond") ? "josuke_yo_angelo" : stand.contains("echoes") ? "koichi_yo_angelo" : null;
        if (sound != null) { level().playSound(null, player.blockPosition(), JojoSounds.get(sound), SoundSource.PLAYERS, 1, 1); responses.add(30); }
        else respond();
        return InteractionResult.CONSUME;
    }
    private void respond() { if (mob != null) { mob.setPos(position()); if (useHurtSound) ((AngeloMobAccess) mob).jojo$playHurtSound(level().damageSources().generic()); else mob.playAmbientSound(); } }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!source.is(DamageTypes.PLAYER_ATTACK) || !(source.getEntity() instanceof LivingEntity attacker)) return false;
        boolean creative = attacker instanceof Player player && player.isCreative();
        if (creative) { dropBlocks = false; lastAttack = source; cancelPlayerHitSound = true; breakRock(); return true; }
        var item = attacker.getMainHandItem(); if (item.isEmpty() || !item.is(ItemTags.PICKAXES)) return false;
        amount = (item.getDestroySpeed(lowerBlock()) + item.getDestroySpeed(upperBlock())) / 2;
        int efficiency = EnchantmentHelper.getItemEnchantmentLevel(registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), item);
        if (efficiency > 0) amount += efficiency * efficiency + 1;
        if (lowerBlock().requiresCorrectToolForDrops() && !item.isCorrectToolForDrops(lowerBlock()) && upperBlock().requiresCorrectToolForDrops() && !item.isCorrectToolForDrops(upperBlock())) amount *= .3F;
        var sound = (random.nextBoolean() ? lowerBlock() : upperBlock()).getSoundType();
        level.playSound(null, getX(), getY(.5), getZ(), sound.getHitSound(), getSoundSource(), (sound.getVolume() + 1) / 8, sound.getPitch() * .5F);
        lastAttack = source; cancelPlayerHitSound = true; entityData.set(DAMAGE, entityData.get(DAMAGE) + Math.max(amount, 1));
        if (entityData.get(DAMAGE) >= 40) { item.hurtAndBreak(1, attacker, InteractionHand.MAIN_HAND); doBreak(); }
        return true;
    }
    public void breakRock() { if (!level().isClientSide()) { entityData.set(DAMAGE, Float.MAX_VALUE); doBreak(); } }
    private void doBreak() {
        if (breaking || !(level() instanceof ServerLevel level)) return; breaking = true;
        for (var record : blocks.values()) RestorationHooks.remember(level, record.pos, record.state, null, record.drops);
        if (dropBlocks && level.getGameRules().get(GameRules.ENTITY_DROPS)) for (var drop : drops) {
            var item = new ItemEntity(level, getX() + .5, getY(), getZ() + .5, drop); item.setDefaultPickUpDelay(); level.addFreshEntity(item);
        }
        if (mob != null && mob.isRemoved() && lastAttack != null && level.getGameRules().get(GameRules.MOB_DROPS)) {
            mob.setPos(position()); if (lastAttack.getEntity() instanceof Player player) mob.setLastHurtByPlayer(player, 1);
            mobLootFortune = mob;
            try { ((AngeloLivingAccess) mob).jojo$dropAllDeathLoot(level, lastAttack); } finally { mobLootFortune = null; }
        }
        level.broadcastEntityEvent(this, (byte) 3); discard();
    }
    @Override public void tick() {
        super.tick(); cancelPlayerHitSound = false;
        if (!level().isClientSide()) {
            responses.replaceAll(ticks -> ticks - 1); responses.removeIf(ticks -> { if (ticks <= 0) { respond(); return true; } return false; });
            if (entityData.get(ANIMATION) > 0) entityData.set(ANIMATION, entityData.get(ANIMATION) - 1);
        }
        if (formationTarget() instanceof LivingEntity target && !entityData.get(CREATED)) {
            target.hurtTime = 0; target.deathTime = 0; target.walkAnimation.stop();
            target.addEffect(new MobEffectInstance(HamonEffects.IMMOBILIZE, 10, 0, false, false, true)); target.setPos(position()); target.setPose(Pose.STANDING);
            if (isFullyFormed() && !level().isClientSide()) {
                if (target instanceof ServerPlayer player) { target.removeAllEffects(); PossessionService.beginExternal(player, this); }
                else { target.discard(); if (keepMob && target instanceof Mob captured) { mob = captured; useHurtSound = ((AngeloMobAccess) captured).jojo$ambientSound() == null; } }
                entityData.set(CREATED, true); entityData.set(TARGET, -1);
            }
        }
        var attached = entityData.get(ATTACH).orElse(null);
        if (attached == null && !level().isClientSide()) { attached = blockPosition(); entityData.set(ATTACH, Optional.of(attached)); }
        if (isPassenger()) { attached = null; setYRot(getVehicle().getYRot()); }
        else if (!level().isClientSide() && attached != null) {
            var next = pistonMove(attached, attached); if (next == null) next = pistonMove(attached, attached.above());
            if (next != null) entityData.set(ATTACH, Optional.of(next));
        }
        if (attached != null) { super.setPos(attached.getX() + .5, attached.getY(), attached.getZ() + .5); setBoundingBox(new AABB(getX() - .5, getY(), getZ() - .5, getX() + .5, getY() + getBbHeight(), getZ() + .5)); }
    }
    private BlockPos pistonMove(BlockPos attached, BlockPos check) {
        var state = level().getBlockState(check);
        if (state.is(Blocks.MOVING_PISTON) || state.is(Blocks.PISTON_HEAD)) { var next = attached.relative(state.getValue(BlockStateProperties.FACING)); if (level().isEmptyBlock(next) && level().isEmptyBlock(next.above())) return next; }
        return null;
    }
    @Override public void setPos(double x, double y, double z) { super.setPos(x, y, z); if (tickCount != 0 && entityData != null) entityData.set(ATTACH, Optional.of(BlockPos.containing(x, y, z))); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        output.storeNullable("Attach", BlockPos.CODEC, entityData.get(ATTACH).orElse(null)); output.putBoolean("Created", entityData.get(CREATED));
        output.putInt("CreationAnim", entityData.get(ANIMATION)); output.putFloat("RockDamage", entityData.get(DAMAGE));
        output.store("RockBlocks", BLOCK_CODEC.listOf(), List.copyOf(blocks.values())); output.store("BlockDrops", ItemStack.CODEC.listOf(), drops);
        output.putBoolean("KeepMob", keepMob);
        if (mob != null) {
            var mobOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess()); mob.saveWithoutId(mobOutput);
            var tag = mobOutput.buildResult(); tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString()); tag.remove("Passengers");
            output.store("AngeloMob", CompoundTag.CODEC, tag); output.putBoolean("NoAmbient", useHurtSound);
        }
        // Original does not save the unfinished formation's target resolver.
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(ATTACH, input.read("Attach", BlockPos.CODEC)); entityData.set(CREATED, input.getBooleanOr("Created", false));
        entityData.set(ANIMATION, input.getIntOr("CreationAnim", 0)); entityData.set(DAMAGE, input.getFloatOr("RockDamage", 0));
        blocks.clear(); input.read("RockBlocks", BLOCK_CODEC.listOf()).orElse(List.of()).forEach(record -> blocks.put(record.pos, record));
        drops.clear(); drops.addAll(input.read("BlockDrops", ItemStack.CODEC.listOf()).orElse(List.of())); keepMob = input.getBooleanOr("KeepMob", false);
        entityData.set(LOWER, Block.getId(blocks.containsKey(blockPosition()) ? blocks.get(blockPosition()).state : Blocks.STONE.defaultBlockState()));
        entityData.set(UPPER, Block.getId(blocks.containsKey(blockPosition().above()) ? blocks.get(blockPosition().above()).state : Blocks.STONE.defaultBlockState()));
        input.read("AngeloMob", CompoundTag.CODEC).ifPresent(tag -> {
            var loaded = EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tag), level(), new EntitySpawnRequest(EntitySpawnReason.LOAD, true)).orElse(null);
            if (loaded instanceof Mob captured) { mob = captured; captured.discard(); }
        });
        useHurtSound = input.getBooleanOr("NoAmbient", false);
    }
    public static void setKeepMobsInside(boolean enabled) { keepMobsInside = enabled; }
    public static void initialize() {
        if (initialized) return; initialized = true;
        StandEffects.register(EFFECT, new EffectHandler());
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damage) -> {
            if (!(entity.level() instanceof ServerLevel level)) return true;
            for (var possibleOwner : level.getAllEntities()) if (possibleOwner instanceof LivingEntity owner) {
                var effect = StandEffects.find(owner, EFFECT, entity.getUUID()).orElse(null); if (effect == null) continue;
                var data = effect.data(); var rock = data.read("Entity", UUIDUtil.CODEC).map(level::getEntity).orElse(null);
                if (!data.getBooleanOr("TriedSummonRock", false) || rock != null && rock.isAlive()) { entity.setHealth(.0001F); return false; }
            }
            return true;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> EffectHandler.RUNTIME.clear());
    }
    private static final class EffectHandler implements StandEffects.Handler {
        static final Map<UUID, Formation> RUNTIME = new HashMap<>();
        public void start(LivingEntity user, StandEffects.Entry effect, Entity target) { RUNTIME.computeIfAbsent(effect.id(), ignored -> Formation.load(user, effect.data())); }
        public boolean needsTarget() { return false; }
        public boolean shouldClearTarget(Entity target) { return false; }
        public boolean removeOn(StandEffects.Removal reason) { return reason != StandEffects.Removal.LOGOUT; }
        public StandEffects.Entry tick(LivingEntity user, StandEffects.Entry effect, Entity target) {
            if (!(user.level() instanceof ServerLevel level)) return effect;
            var formation = RUNTIME.computeIfAbsent(effect.id(), ignored -> Formation.load(user, effect.data()));
            if (target != null) formation.lastPos = target.blockPosition();
            if (target instanceof LivingEntity living && !formation.tried) {
                if (StandHeavyImpact.active(living)) return effect;
                formation.tryForm(level, user, living); formation.tried = true;
            }
            var rock = formation.rock == null ? null : level.getEntity(formation.rock);
            if ((formation.tried || target == null) && (rock == null || rock instanceof AngeloRockEntity angelo && angelo.isFullyFormed()) && formation.blocks.isEmpty()) return null;
            if (!formation.blocks.isEmpty()) {
                var center = rock != null ? rock.blockPosition() : target != null ? target.blockPosition() : formation.lastPos != null ? formation.lastPos : user.blockPosition();
                formation.restore(level, user, target, center);
            }
            return effect.withData(formation.save(user));
        }
        public void stop(LivingEntity user, StandEffects.Entry effect, Entity target) {
            var formation = RUNTIME.remove(effect.id()); if (formation == null) formation = Formation.load(user, effect.data());
            if (user.level() instanceof ServerLevel level && formation.rock != null && level.getEntity(formation.rock) instanceof AngeloRockEntity rock && !rock.isFullyFormed()) rock.breakRock();
        }
    }
    private static final class Formation {
        boolean tried, keepMob = keepMobsInside, allFailed; UUID rock; BlockPos lastPos;
        final Map<BlockPos, BrokenBlockRecord> blocks = new HashMap<>(); final Set<BlockPos> failed = new HashSet<>();
        static Formation load(LivingEntity user, CompoundTag data) {
            var result = new Formation(); result.tried = data.getBooleanOr("TriedSummonRock", false); result.keepMob = data.getBooleanOr("SaveMob", keepMobsInside);
            result.rock = data.read("Entity", UUIDUtil.CODEC).orElse(null); result.lastPos = data.read("LastTargetPos", BlockPos.CODEC).orElse(null);
            BLOCK_CODEC.listOf().parse(user.registryAccess().createSerializationContext(NbtOps.INSTANCE), data.getListOrEmpty("FixBlocks")).result().orElse(List.of()).forEach(record -> result.blocks.put(record.pos, record));
            return result;
        }
        CompoundTag save(LivingEntity user) {
            var data = new CompoundTag(); data.putBoolean("TriedSummonRock", tried); data.putBoolean("SaveMob", keepMob);
            if (rock != null) data.store("Entity", UUIDUtil.CODEC, rock); if (lastPos != null) data.store("LastTargetPos", BlockPos.CODEC, lastPos);
            data.put("FixBlocks", BLOCK_CODEC.listOf().encodeStart(user.registryAccess().createSerializationContext(NbtOps.INSTANCE), List.copyOf(blocks.values())).getOrThrow()); return data;
        }
        private record Pair(BrokenBlockRecord lower, BrokenBlockRecord upper, int priority, BlockPos breakPos) {
            double distance(Vec3 target) { var block = lower != null ? lower : upper; return target.distanceToSqr(block.pos.getX() + .5, block.pos.getY() + (lower == null ? 1 : 0), block.pos.getZ() + .5); }
        }
        void tryForm(ServerLevel level, LivingEntity user, LivingEntity target) {
            for (var pos : StandHeavyImpact.destroyedBlocks(target)) { var history = BlockHistory.at(level, pos); var record = history == null ? null : history.getBrokenBlockAt(pos); if (record != null) blocks.put(pos, record); }
            if (target.getHealth() > Math.max(10, target.getHealth() * .05F)) return;
            var stones = new HashMap<BlockPos, BrokenBlockRecord>(); blocks.forEach((pos, record) -> { if (CDRecoveryModifiers.stoneMaterial(record.state)) stones.put(pos, record); }); if (stones.size() < 2) return;
            var candidates = new ArrayList<Pair>(); var point = target.position();
            for (var block : stones.values()) {
                var above = stones.get(block.pos.above()); var below = stones.get(block.pos.below());
                if (above != null && point.distanceToSqr(Vec3.atBottomCenterOf(block.pos)) <= 3) { candidates.add(new Pair(block, above, 1, null)); continue; }
                if (below != null && point.distanceToSqr(Vec3.atBottomCenterOf(below.pos)) <= 3) { candidates.add(new Pair(below, block, 1, null)); continue; }
                if (!blocks.containsKey(block.pos.above())) { var current = level.getBlockState(block.pos.above()); if (current.isAir() || CDRecoveryModifiers.stoneMaterial(current)) { candidates.add(new Pair(block, null, 2, current.isAir() ? null : block.pos.above())); continue; } }
                if (!blocks.containsKey(block.pos.below())) { var current = level.getBlockState(block.pos.below()); if (current.isAir() || CDRecoveryModifiers.stoneMaterial(current)) candidates.add(new Pair(null, block, 2, current.isAir() ? null : block.pos.below())); }
            }
            var pair = candidates.stream().sorted(Comparator.comparingInt(Pair::priority).thenComparingDouble(entry -> entry.distance(point))).findAny().orElse(null); if (pair == null) return;
            var lower = pair.lower; var upper = pair.upper; var history = BlockHistory.at(level, (lower != null ? lower : upper).pos);
            if (lower == null || upper == null) {
                BrokenBlockRecord missing;
                if (pair.breakPos != null) { var old = level.getBlockState(pair.breakPos); if (user instanceof Player p && p.isCreative()) RestorationHooks.remember(level, pair.breakPos, old, level.getBlockEntity(pair.breakPos), List.of()); level.destroyBlock(pair.breakPos, !(user instanceof Player p && p.isCreative())); missing = history == null ? null : history.getBrokenBlockAt(pair.breakPos); }
                else { var present = lower != null ? lower : upper; stones.remove(present.pos); boolean lowerMissing = lower == null; missing = stones.values().stream().min(Comparator.comparingDouble(record -> point.distanceToSqr(record.pos.getX() + .5, record.pos.getY() + (lowerMissing ? 0 : 1), record.pos.getZ() + .5))).orElse(null); }
                if (lower == null) lower = missing; else upper = missing;
            }
            if (lower == null || upper == null) return;
            if (history != null) { history.removeBrokenBlock(lower.pos); history.removeBrokenBlock(upper.pos); } // Original uses only the chosen block's chunk.
            blocks.remove(lower.pos); blocks.remove(upper.pos);
            if (!upper.pos.equals(lower.pos.above())) upper = new BrokenBlockRecord(lower.pos.above(), upper.state, upper.drops, upper.keep);
            if (!lower.pos.equals(upper.pos.below())) lower = new BrokenBlockRecord(upper.pos.below(), lower.state, lower.drops, lower.keep);
            level.playSound(null, user.blockPosition(), JojoSounds.get("josuke_pray_for_eternity"), SoundSource.PLAYERS, 1, 1);
            var formed = form(level, target, lower, upper, keepMob); rock = formed.getUUID();
            var items = items(level, user, target, formed.blockPosition());
            RestorationHooks.consumeNeededItems(upper.drops, items, formed.drops); RestorationHooks.consumeNeededItems(lower.drops, items, formed.drops);
        }
        void restore(ServerLevel level, LivingEntity user, Entity target, BlockPos center) {
            var result = RestorationHooks.restoreBlocks(level, user, blocks.values().stream().filter(record -> !failed.contains(record.pos)), Comparator.comparingInt(record -> record.pos.distManhattan(center)),
                    2, user instanceof Player p && p.isCreative(), false, true, null, items(level, user, target, center));
            if (!result.blocksToForget.isEmpty() && !result.blocksTried.isEmpty()) { result.blocksToForget.forEach(blocks::remove); allFailed = false; }
            else { allFailed |= failed.isEmpty(); failed.addAll(result.blocksTried); if (result.blocksTried.isEmpty() || failed.size() >= blocks.size()) { if (allFailed) blocks.clear(); else failed.clear(); } }
            if (!result.blocksPlaced.isEmpty() && user instanceof ServerPlayer player && StandService.currentManifestation(player) != null)
                publishRestored(StandService.currentManifestation(player).entity(), result.blocksPlaced);
        }
        private void publishRestored(Mob stand, java.util.Set<BlockPos> positions) {
            var state = RestorationSelection.state(stand);
            RestorationSelection.publish(stand, state.mode(), state.ticks(), stand.level().getEntity(state.targetId()), state.entityWorked(), new ArrayList<>(positions));
        }
        List<ItemStack> items(ServerLevel level, LivingEntity user, Entity target, BlockPos center) {
            var source = RestorationHooks.SourceType.PLAYER_INVENTORY;
            return RestorationHooks.sourceItemStacks(new AABB(Vec3.atLowerCornerOf(center), Vec3.atLowerCornerOf(center)).inflate(8), Vec3.atBottomCenterOf(center), user, level,
                    target instanceof Player ? source.from(target) : null, RestorationHooks.SourceType.MOB_HELD.fromAllNearby(), RestorationHooks.SourceType.ITEM_ENTITY.fromAllNearby(),
                    user instanceof Player ? source.from(user) : null, CrazyDiamondRestorationActions.useOtherPlayersInventories() ? source.fromAllNearby().sort() : null);
        }
    }
}
