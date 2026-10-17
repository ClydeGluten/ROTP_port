package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Original VampirismFreeze, SpaceRipperStingyEyes, DarkAura and MCUtil.leap. */
public final class VampireCombatService {
    public static final AttachmentType<VampireCombatState> STATE = AttachmentRegistry.<VampireCombatState>builder()
            .initializer(() -> VampireCombatState.EMPTY).syncWith(VampireCombatState.STREAM_CODEC, AttachmentSyncPredicate.all())
            .buildAndRegister(RipplesFabric.id("vampire_combat"));
    public static final EntityType<SpaceRipperStingyEyesEntity> EYES = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            RipplesFabric.id("space_ripper_stingy_eyes"), EntityType.Builder.<SpaceRipperStingyEyesEntity>of(SpaceRipperStingyEyesEntity::new, MobCategory.MISC)
                    .sized(.25F, .25F).noSummon().clientTrackingRange(8).updateInterval(20)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("space_ripper_stingy_eyes"))));
    public static final Holder<MobEffect> FREEZE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            RipplesFabric.id("freeze"), new FreezeEffect());
    public static final Holder<MobEffect> STUN = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            RipplesFabric.id("stun"), new StunEffect());
    public static final ResourceKey<DamageType> COLD = damageKey("cold");
    public static final ResourceKey<DamageType> EYES_DAMAGE = damageKey("space_ripper_stingy_eyes");
    public static final ResourceKey<DamageType> COLLISION_DAMAGE = damageKey("entity_flew_into");
    private static final Identifier SPEED = RipplesFabric.id("vampire_combat_speed");
    private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();
    private static final Map<Mob, Boolean> STUNNED = new IdentityHashMap<>();
    private static final class Runtime {
        boolean freeze, charging;
        int freezeTicks, chargeTicks, eyesTicks, eyesCooldown, auraCooldown, leapCooldown, heartbeat;
        SpaceRipperStingyEyesEntity[] beams;
    }
    private static ResourceKey<DamageType> damageKey(String name) { return ResourceKey.create(Registries.DAMAGE_TYPE, RipplesFabric.id(name)); }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) tick(player);
            STUNNED.entrySet().removeIf(entry -> {
                if (entry.getKey().isRemoved()) return true;
                if (!isStunned(entry.getKey())) { entry.getKey().setNoAi(entry.getValue()); return true; }
                return false;
            });
            VampireCollisionImpact.tick();
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> !freezeCounter(target, source));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> { if (entity instanceof ServerPlayer player) stop(player); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { RUNTIME.clear(); STUNNED.clear(); VampireCollisionImpact.clear(); });
    }
    private static Runtime runtime(ServerPlayer player) { return RUNTIME.computeIfAbsent(player.getUUID(), ignored -> new Runtime()); }
    public static VampireCombatState state(Player player) { return player.getAttachedOrCreate(STATE); }
    private static boolean eligible(ServerPlayer player, int maxCure, boolean freeHand) {
        return VampireService.progress(player).vampire() && VampireService.progress(player).fullPower()
                && player.isAlive() && !player.isSpectator() && !TimeStopService.isFrozen(player)
                && VampireLifecycle.curingStage(player) <= maxCure && (!freeHand || player.getMainHandItem().isEmpty());
    }
    private static boolean hasBlood(ServerPlayer player, float cost) { return player.isCreative() || VampireService.progress(player).blood() >= cost; }
    private static void spend(ServerPlayer player, float cost) { VampireService.spend(player, cost); }
    public static boolean startFreeze(ServerPlayer player) {
        if (!VampireActionPayload.canStart(player, VampireActionPayload.Action.START_FREEZE) || !eligible(player, 1, true) || !freezeEnvironment(player) || !hasBlood(player, .45F)) return false;
        var state = runtime(player);
        VampireActionPayload.stopAllHeld(player);
        state.freeze = true; state.freezeTicks = 0; state.heartbeat = 0; sync(player, state);
        return true;
    }
    private static boolean freezeEnvironment(ServerPlayer player) {
        return player.level().getDifficulty() != Difficulty.PEACEFUL && !player.isOnFire() && !player.level().environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES);
    }
    public static void stopFreeze(ServerPlayer player) { var state = runtime(player); state.freeze = false; state.freezeTicks = 0; sync(player, state); }
    public static boolean startEyesCharge(ServerPlayer player) {
        var state = runtime(player);
        if (!VampireActionPayload.canStart(player, VampireActionPayload.Action.START_EYES) || !eligible(player, 1, false) || !hasBlood(player, 20) || state.eyesCooldown > 0 || state.beams != null || state.charging) return false;
        VampireActionPayload.stopAllHeld(player);
        state.charging = true; state.chargeTicks = 0; state.heartbeat = 0; sync(player, state); return true;
    }
    /** Original holdToFire(20, true): charge may be held indefinitely; releasing fires only after twenty server ticks. */
    public static void releaseEyesCharge(ServerPlayer player) {
        var state = runtime(player);
        if (state.charging && state.chargeTicks >= 20) fireEyes(player, state);
        else { state.charging = false; state.chargeTicks = 0; }
        sync(player, state);
    }
    public static void cancelEyesCharge(ServerPlayer player) { var state = runtime(player); state.charging = false; state.chargeTicks = 0; sync(player, state); }
    public static void keepHeld(ServerPlayer player) { var state = RUNTIME.get(player.getUUID()); if (state != null) state.heartbeat = 0; }
    public static void stopHeld(ServerPlayer player) {
        var state = runtime(player); state.freeze = false; state.freezeTicks = 0; state.charging = false; state.chargeTicks = 0; sync(player, state);
    }
    public static void stop(ServerPlayer player) {
        var state = RUNTIME.remove(player.getUUID());
        if (state != null && state.beams != null) for (var beam : state.beams) beam.detach();
        player.setAttached(STATE, VampireCombatState.EMPTY);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null) speed.removeModifier(SPEED);
    }
    private static void fireEyes(ServerPlayer player, Runtime state) {
        if (!eligible(player, 1, false) || !hasBlood(player, 20)) { state.charging = false; state.chargeTicks = 0; return; }
        spend(player, 20);
        state.charging = false; state.chargeTicks = 0; state.eyesTicks = 0;
        state.beams = new SpaceRipperStingyEyesEntity[] { new SpaceRipperStingyEyesEntity(player, true), new SpaceRipperStingyEyesEntity(player, false) };
        for (var beam : state.beams) player.level().addFreshEntity(beam);
    }
    private static void tick(ServerPlayer player) {
        var state = RUNTIME.get(player.getUUID());
        if (state == null) return;
        if (!VampireService.progress(player).vampire() || !player.isAlive() || player.isSpectator()) { stop(player); return; }
        if (TimeStopService.isFrozen(player)) return;
        state.eyesCooldown = Math.max(0, state.eyesCooldown - 1);
        state.auraCooldown = Math.max(0, state.auraCooldown - 1);
        state.leapCooldown = Math.max(0, state.leapCooldown - 1);
        if (++state.heartbeat > 15) { state.freeze = false; state.freezeTicks = 0; state.charging = false; state.chargeTicks = 0; }
        if (state.freeze) {
            if (!eligible(player, 1, true) || isStunned(player) || !freezeEnvironment(player) || !hasBlood(player, .45F)) { state.freeze = false; state.freezeTicks = 0; }
            else { state.freezeTicks++; spend(player, .45F); freezeTick(player); }
        }
        if (state.charging) {
            if (!eligible(player, 1, false) || !hasBlood(player, 20)) { state.charging = false; state.chargeTicks = 0; }
            else state.chargeTicks = Math.min(state.chargeTicks + 1, 1_000_000);
        }
        if (state.beams != null) {
            boolean energy = hasBlood(player, 20);
            if (energy) spend(player, 20);
            else if (!player.isCreative()) player.setAttached(VampireAttachments.PROGRESS, VampireService.progress(player).withBlood(0));
            if (state.eyesTicks >= 20 || !energy) {
                for (var beam : state.beams) beam.detach();
                state.beams = null; state.eyesCooldown = (int) (50F * state.eyesTicks / 20); state.eyesTicks = 0;
            } else state.eyesTicks++;
        }
        sync(player, state);
    }
    private static void sync(ServerPlayer player, Runtime state) {
        player.setAttached(STATE, new VampireCombatState(state.freeze, state.freezeTicks, state.charging ? Math.max(1, state.chargeTicks) : 0,
                state.beams == null ? 0 : state.eyesTicks + 1, state.eyesCooldown, state.auraCooldown, state.leapCooldown));
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double factor = state.beams != null ? .3 : state.freeze ? .75 : 1;
            var existing = speed.getModifier(SPEED);
            if (existing != null && existing.amount() != factor - 1 || existing != null && factor == 1) speed.removeModifier(SPEED);
            if (factor != 1 && !speed.hasModifier(SPEED)) speed.addTransientModifier(new AttributeModifier(SPEED, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
    private static void freezeTick(ServerPlayer player) {
        int difficulty = player.level().getDifficulty().getId();
        LivingEntity target = VampireService.target(player, 2);
        if (target != null && !target.isOnFire() && !immuneToCold(target)) {
            float damage = (float) Math.pow(2, difficulty) * .5F;
            if (target instanceof Skeleton skeleton && skeleton.isAlive() && skeleton.getHealth() <= damage) {
                if (difficulty >= 2 && (difficulty == 3 || skeleton.getRandom().nextBoolean()))
                    skeleton.convertTo(EntityTypes.STRAY, ConversionParams.single(skeleton, true, true), converted -> {
                        if (!skeleton.isSilent()) player.level().levelEvent(null, 1026, skeleton.blockPosition(), 0);
                    });
            } else {
                if (target.getType() == EntityTypes.BLAZE || target.getType() == EntityTypes.MAGMA_CUBE || target.getType() == EntityTypes.STRIDER) damage *= 5;
                else if (target.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) damage *= .5F;
                var source = new DamageSource(player.level().registryAccess().getOrThrow(COLD), player);
                if (target.hurtServer(player.level(), source, damage)) {
                    var previous = target.getEffect(FREEZE);
                    int duration = previous == null ? (difficulty + 1) * 50 : previous.getDuration() + (difficulty - 1) * 5 + 1;
                    target.addEffect(new MobEffectInstance(FREEZE, duration, previous == null ? 0 : duration / 100));
                    if (previous == null) sound(player, "vampire_freeze", 1);
                }
            }
        }
        if (player.onGround()) {
            var center = player.blockPosition();
            for (var pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, -1, 4))) {
                if (pos.closerToCenterThan(player.position(), 4) && player.level().getBlockState(pos.above()).isAir()) freezeWater(player, pos);
            }
        }
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        var hit = player.level().clip(new ClipContext(eye.add(look), eye.add(look.scale(8)), ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, player) {
            @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
                return net.minecraft.world.phys.shapes.Shapes.empty();
            }
        });
        if (hit.getType() == HitResult.Type.BLOCK) freezeWater(player, hit.getBlockPos());
    }
    private static void freezeWater(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return;
        var water = level.getBlockState(pos); var ice = Blocks.FROSTED_ICE.defaultBlockState();
        if (!water.is(Blocks.WATER) || !water.getFluidState().isSource()
                || !water.getFluidState().is(Fluids.WATER) || !ice.canSurvive(level, pos) || !level.isUnobstructed(ice, pos, CollisionContext.empty())) return;
        level.setBlockAndUpdate(pos, ice); level.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, 20 + player.getRandom().nextInt(21));
    }
    public static boolean immuneToCold(LivingEntity target) {
        return target.getType() == EntityTypes.SNOW_GOLEM || target.getType() == EntityTypes.STRAY || target.getType() == EntityTypes.POLAR_BEAR
                || target.level() instanceof ServerLevel level && target.isInvulnerableTo(level, new DamageSource(level.registryAccess().getOrThrow(COLD)));
    }
    private static boolean freezeCounter(LivingEntity target, DamageSource source) {
        if (!(target instanceof ServerPlayer player) || !state(player).freeze() || !eligible(player, 1, true) || !freezeEnvironment(player)
                || !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker.isOnFire() || immuneToCold(attacker)) return false;
        int difficulty = player.level().getDifficulty().getId();
        attacker.addEffect(new MobEffectInstance(FREEZE, difficulty * 100, difficulty)); sound(player, "vampire_freeze", 1); return true;
    }
    public static boolean evilAtmosphere(ServerPlayer player) {
        var state = runtime(player); int difficulty = player.level().getDifficulty().getId();
        if (!VampireActionPayload.canStart(player, VampireActionPayload.Action.DARK_AURA) || !eligible(player, 3, false) || difficulty == 0 || !hasBlood(player, 25) || state.auraCooldown > 0) return false;
        double range = 16 * difficulty - 8; int amplifier = (int) Math.floor((difficulty - 1) * 1.5);
        Vec3 center = player.getBoundingBox().getCenter();
        for (var target : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(range), entity -> entity != player)) {
            if (SpeciesHooks.undeadOrVampiric(target)
                    || target instanceof StarPlatinumEntity stand && stand.ownerId() == player.getId()) continue;
            boolean passive = target instanceof AgeableMob; int duration = passive ? 600 : 200;
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, amplifier));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier));
            target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, duration, amplifier));
            if (passive) target.addEffect(new MobEffectInstance(STUN, duration));
        }
        if (difficulty == 3) VampireZombieService.buffOwned(player, range);
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            if (stack.is(Items.POPPY)) { var rose = new ItemStack(Items.WITHER_ROSE, stack.getCount()); rose.applyComponents(stack.getComponentsPatch()); player.setItemInHand(hand, rose); }
        }
        VampireActionPayload.stopAllHeld(player);
        spend(player, 25); state.auraCooldown = player.isCreative() ? 0 : 300; sound(player, "vampire_evil_atmosphere", (float) (range + 16) / 16); sync(player, state); return true;
    }
    public static boolean leap(ServerPlayer player) {
        var state = runtime(player);
        if (!VampireService.progress(player).vampire() || !player.isAlive() || player.isSpectator() || TimeStopService.isFrozen(player)
                || isStunned(player) || VampireLifecycle.curingStage(player) >= 3 || state.leapCooldown > 0
                || !(player.onGround() || player.getVehicle() != null && player.getVehicle().onGround())
                || state.freeze || state.beams != null || player.getAttachedOrCreate(VampireAttachments.HUD).clawTicks() > 0
                || player.getAttachedOrCreate(VampireAttachments.HUD).held() == VampireHudState.Held.GIFT) return false;
        var progress = VampireService.progress(player);
        float strength = Math.max(progress.bloodLevel(player.level().getDifficulty().getId()), 0) * (progress.fullPower() ? 1 : .15F) * .25F;
        if (strength <= 0) return false;
        Vec3 leap = Vec3.directionFromRotation(Math.min(player.getXRot(), -30F), player.getYRot()).scale(strength);
        Entity leaper = player.getVehicle() == null ? player : player.getVehicle();
        leaper.setOnGround(false); if (leaper instanceof LivingEntity living) living.setJumping(true); leaper.needsSync = true;
        leaper.setDeltaMovement(leap.x, leap.y * .5, leap.z); leaper.syncVelocity = true; player.setShiftKeyDown(false);
        var ground = player.level().getBlockState(player.getOnPos());
        if (!ground.isAir()) {
            player.level().sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
                    player.getX(), player.getY(), player.getZ(), (int) (150 * Math.min(.2F + strength / 3, 2.5F)), 0, 0, 0, .15);
            var sound = ground.getSoundType();
            player.level().playSound(null, player.blockPosition(), sound.getBreakSound(), SoundSource.PLAYERS, sound.getVolume() * .25F * strength, sound.getPitch() * .75F);
        }
        state.leapCooldown = player.isCreative() ? 0 : 20; sync(player, state); return true;
    }
    public static void onClawHit(LivingEntity target, ServerPlayer attacker) { VampireCollisionImpact.start(target, attacker); }
    private static void sound(ServerPlayer player, String name, float volume) {
        player.level().playSound(null, player.blockPosition(), JojoSounds.get(name), SoundSource.PLAYERS, volume, 1);
    }
    private static final class FreezeEffect extends MobEffect {
        FreezeEffect() {
            super(MobEffectCategory.HARMFUL, 0xD6D6FF);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, RipplesFabric.id("freeze_movement"), -.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.ATTACK_SPEED, RipplesFabric.id("freeze_attack"), -.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }
        @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            return entity.getRemainingFireTicks() <= 0 && !level.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES);
        }
    }
    public static boolean isStunned(LivingEntity entity){return entity.hasEffect(STUN) || entity.hasEffect(HamonEffects.SHOCK);}
    public static void restoreStunAi(LivingEntity entity){if(entity instanceof Mob mob && !isStunned(mob)){var old=STUNNED.remove(mob);if(old!=null)mob.setNoAi(old);}}
    public static class ImmobilizeEffect extends MobEffect {
        public ImmobilizeEffect(int color) {
            super(MobEffectCategory.HARMFUL,color);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, RipplesFabric.id("stun_movement"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.FLYING_SPEED, RipplesFabric.id("stun_flying"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.ATTACK_SPEED, RipplesFabric.id("stun_attack"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        @Override public boolean shouldApplyEffectTickThisTick(int duration,int amplifier){return true;}
        @Override public boolean applyEffectTick(ServerLevel level,LivingEntity entity,int amplifier){
            if(entity instanceof Player player)player.getAbilities().flying=false;
            entity.setDeltaMovement(0,Math.min(entity.getDeltaMovement().y,0),0);return true;
        }
    }
    public static class StunEffect extends ImmobilizeEffect {
        StunEffect(){this(0x404040);}
        public StunEffect(int color){super(color);}
        @Override public void onEffectStarted(LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide() && entity instanceof Mob mob) { STUNNED.putIfAbsent(mob, false); mob.setNoAi(true); }
        }
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }
        @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            if (entity instanceof Player player) {player.getAbilities().flying = false;player.setSprinting(false);}
            entity.setDeltaMovement(0, Math.min(entity.getDeltaMovement().y, 0), 0);
            if (entity instanceof Mob mob) { STUNNED.putIfAbsent(mob, false); mob.setNoAi(true); }
            if (entity instanceof net.minecraft.world.entity.monster.Creeper creeper)
                ((com.github.standobyte.jojo.fabric.mixin.StunnedCreeperAccess) creeper).jojo$setSwell(-1);
            return true;
        }
    }
    private VampireCombatService() { }
}
