package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.VampireProgress;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** First original vampire slice: mask, blood gift, blood economy, passives, drain and claw. */
public final class VampireService {
    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> CAN_DRAIN = net.minecraft.tags.TagKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("vampire_can_drain"));
    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> CANNOT_DRAIN = net.minecraft.tags.TagKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("vampire_cannot_drain"));
    private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();
    private static final net.minecraft.resources.Identifier ACTION_SPEED = RipplesFabric.id("vampire_action_speed");
    private static final List<Holder<MobEffect>> PASSIVES = List.of(MobEffects.HEALTH_BOOST, MobEffects.REGENERATION,
            MobEffects.STRENGTH, MobEffects.SPEED, MobEffects.HASTE, MobEffects.JUMP_BOOST, MobEffects.NIGHT_VISION,
            MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS, MobEffects.BLINDNESS);
    private static final List<Holder<MobEffect>> DRAIN_EFFECTS = List.of(MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS, MobEffects.NAUSEA);
    private static final class Runtime {
        VampireHudState.Held held = VampireHudState.Held.NONE;
        int heldTicks, clawTicks, heartbeat, passiveLevel = Integer.MIN_VALUE, passiveDifficulty = -1, passiveCuringStage = -1;
    }
    public static void initialize() {
        VampireAttachments.initialize();
        VampireActionPayload.initialize();
        VampireLifecycle.initialize();
        VampireResurrectionPayload.initialize();
        ServerTickEvents.END_SERVER_TICK.register(server -> { for (var player : server.getPlayerList().getPlayers()) tick(player); });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, actual, blocked) -> {
            if (actual > 0 && entity instanceof ServerPlayer player
                    && player.getAttachedOrCreate(VampireAttachments.HUD).held() == VampireHudState.Held.DRAIN) stopHeld(player);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> { if (entity instanceof ServerPlayer player) clearRuntime(player); });
        // Fabric copies attachments in this same event. Run after its default phase so
        // automatic copyOnDeath cannot overwrite the original weak/full retention rule.
        var respawnPhase = RipplesFabric.id("vampire_after_attachment_copy");
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, respawnPhase);
        ServerPlayerEvents.AFTER_RESPAWN.register(respawnPhase, (oldPlayer, newPlayer, alive) -> {
            var state = oldPlayer.getAttachedOrCreate(VampireAttachments.PROGRESS);
            newPlayer.setAttached(VampireAttachments.PROGRESS, alive ? state : VampireConfig.server().keepVampirismOnDeath() ? state.afterDeath() : VampireProgress.EMPTY);
            clearRuntime(newPlayer);
            if (newPlayer.getAttachedOrCreate(VampireAttachments.PROGRESS).vampire()) updatePassives(newPlayer, runtime(newPlayer));
            else if (state.vampire()) newPlayer.setAttached(VampireHamon.MEMORY, VampireHamon.Memory.EMPTY);
            VampireLifecycle.onRespawn(newPlayer);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (server.isSameThread()) clearRuntime(handler.player);
            else server.execute(() -> clearRuntime(handler.player));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNTIME.clear());
    }
    private static Runtime runtime(ServerPlayer player) { return RUNTIME.computeIfAbsent(player.getUUID(), id -> new Runtime()); }
    public static VampireProgress progress(Player player) { return player.getAttachedOrCreate(VampireAttachments.PROGRESS); }
    public static boolean activateMask(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || TimeStopService.isFrozen(player)) return false;
        var head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof StoneMaskItem)) return false;
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) { message(player, "stone_mask_peaceful"); return false; }
        if (PillarmanService.hasPillarman(player)) {
            if (!PillarmanService.applyMask(player, head.is(VampireItems.AJA_STONE_MASK))) return false;
            StoneMaskItem.activate(head);
            head.hurtAndBreak(1, player, EquipmentSlot.HEAD);
            sound(player, "stone_mask_activation_entity", 1, 1);
            return true;
        }
        if (head.is(VampireItems.AJA_STONE_MASK)) {
            VampireAdvancements.onMaskSuicide(player);
            player.hurtServer(player.level(), damage(player.level(), "stone_mask", null), 1000);
            return false;
        }
        if (progress(player).fullPower() || !SpeciesHooks.canBecomeVampire(player)) return false;
        boolean firstAwakening = !progress(player).vampire();
        if (firstAwakening) VampireHamon.rememberOnTransformation(player);
        player.setAttached(VampireAttachments.PROGRESS, progress(player).withFullPower().withMaximum(maximumBlood(player)));
        if (firstAwakening) { notifyControls(player); VampireAdvancements.onTransformation(player); }
        updatePassives(player, runtime(player));
        StoneMaskItem.activate(head);
        head.hurtAndBreak(1, player, EquipmentSlot.HEAD);
        sound(player, "stone_mask_activation_entity", 1, 1);
        return true;
    }
    /** A server-authoritative blood splash hook for bleeding/projectile integrations. */
    public static void splashBlood(ServerLevel level, Vec3 position, double radius) {
        VampireBlocks.splashBlood(level, position, radius);
        for (var wearer : level.getEntitiesOfClass(ServerPlayer.class, new AABB(position, position).inflate(radius), p -> p.isAlive() && !p.isSpectator())) {
            if (level.clip(new ClipContext(position, wearer.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wearer)).getType() == HitResult.Type.MISS)
                activateMask(wearer);
        }
    }
    public static boolean grant(ServerPlayer player, boolean fullPower) {
        if (progress(player).vampire() || !SpeciesHooks.canBecomeVampire(player)) return false;
        VampireHamon.rememberOnTransformation(player);
        player.setAttached(VampireAttachments.PROGRESS, VampireProgress.transform(fullPower).withMaximum(maximumBlood(player)));
        notifyControls(player);
        VampireAdvancements.onTransformation(player);
        updatePassives(player, runtime(player));
        return true;
    }
    public static void clear(ServerPlayer player) {
        VampireCombatService.stop(player); VampireHamon.stop(player);
        clearRuntime(player);
        if (progress(player).vampire() || player.getAttachedOrCreate(VampireHamon.MEMORY).formerUser())
            player.setAttached(VampireHamon.MEMORY, VampireHamon.Memory.EMPTY);
        player.setAttached(VampireAttachments.PROGRESS, VampireProgress.EMPTY);
        player.setAttached(VampireAttachments.CURING, 0);
        for (var effect : PASSIVES) { var current = player.getEffect(effect); if (current != null && !current.showIcon()) player.removeEffect(effect); }
        player.removeEffect(VampireItems.SUN_BURN);
    }
    public static boolean canAct(ServerPlayer player) {
        return progress(player).vampire() && player.isAlive() && !player.isSpectator() && !TimeStopService.isFrozen(player)
                && player.getMainHandItem().isEmpty() && !VampireCombatService.isStunned(player);
    }
    public static boolean claw(ServerPlayer player) {
        if (!VampireActionPayload.canStart(player, VampireActionPayload.Action.CLAW) || !canAct(player) || !progress(player).fullPower() || !player.isCreative() && progress(player).blood() < 45) return false;
        Runtime state = runtime(player);
        if (state.clawTicks > 0 || state.held == VampireHudState.Held.GIFT) return false;
        VampireActionPayload.stopAllHeld(player);
        // The original override deliberately does not consume the advertised 45 energy.
        state.clawTicks = 1;
        updateActionSpeed(player, state);
        sync(player, state);
        return true;
    }
    public static boolean startHeld(ServerPlayer player, VampireHudState.Held held) {
        if (held == VampireHudState.Held.NONE || !VampireActionPayload.canStart(player, held == VampireHudState.Held.DRAIN ? VampireActionPayload.Action.START_DRAIN : VampireActionPayload.Action.START_GIFT) || !canAct(player) || player.level().getDifficulty() == Difficulty.PEACEFUL) return false;
        if (VampireLifecycle.curingStage(player) > (held == VampireHudState.Held.DRAIN ? 2 : 3)) return false;
        Runtime state = runtime(player);
        if (state.clawTicks > 0) return false;
        if (held == VampireHudState.Held.GIFT && (!canGift(player, target(player, 2, true)) || !player.isCreative() && progress(player).blood() < 5)) return false;
        VampireActionPayload.stopAllHeld(player);
        state.held = held; state.heldTicks = 0; state.heartbeat = 0;
        updateActionSpeed(player, state);
        sync(player, state);
        return true;
    }
    public static void refreshHeld(ServerPlayer player) { var state = RUNTIME.get(player.getUUID()); if (state != null) state.heartbeat = 0; }
    public static void stopHeld(ServerPlayer player) {
        var state = RUNTIME.get(player.getUUID());
        if (state == null) return;
        state.held = VampireHudState.Held.NONE; state.heldTicks = 0;
        updateActionSpeed(player, state); sync(player, state);
    }
    private static void clearRuntime(ServerPlayer player) {
        RUNTIME.remove(player.getUUID());
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(ACTION_SPEED);
        player.setAttached(VampireAttachments.HUD, VampireHudState.EMPTY);
    }
    public static void tick(ServerPlayer player) {
        var progress = progress(player);
        if (!progress.vampire() || !player.isAlive()) return;
        if (TimeStopService.isFrozen(player)) return;
        Runtime state = runtime(player);
        progress = progress.withMaximum(maximumBlood(player));
        int curingStage = VampireLifecycle.curingStage(player);
        float blood = curingStage >= 4 ? 0 : progress.blood() - (curingStage > 0
                ? progress.maxBlood() * Math.max(VampireLifecycle.cureProgress(player), .25F) / 200
                : player.isCreative() ? 0 : VampireConfig.server().bloodTickDown()[player.level().getDifficulty().getId()]);
        player.setAttached(VampireAttachments.PROGRESS, progress.withBlood(blood));
        player.getFoodData().setFoodLevel(17);
        player.setAirSupply(player.getMaxAirSupply());
        updatePassives(player, state);
        VampireLifecycle.tick(player);
        tickSunDamage(player);
        if (state.clawTicks > 0) {
            // ContinuousActionInstance checks requirements when starting, not again during its animation.
            int tick = state.clawTicks++;
            if (tick == 3) { sound(player, "vampire_swipe", 1, 1.25F); player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true); }
            if (tick == 5) clawHit(player);
            if (tick >= 12) state.clawTicks = 0;
        }
        if (state.held != VampireHudState.Held.NONE) {
            if (!canAct(player) || player.level().getDifficulty() == Difficulty.PEACEFUL || ++state.heartbeat > 15
                    || VampireLifecycle.curingStage(player) > (state.held == VampireHudState.Held.DRAIN ? 2 : 3)) stopHeld(player);
            else {
                LivingEntity target = target(player, 2, state.held == VampireHudState.Held.GIFT);
                if (state.held == VampireHudState.Held.DRAIN) {
                    if (target != null && canDrainBloodFrom(target)) {
                        if (state.heldTicks % 20 == 0) sound(player, "vampire_blood_drain", 1, 1);
                        drain(player, target);
                    }
                    state.heldTicks++;
                } else if (!canGift(player, target) || !player.isCreative() && progress(player).blood() < 5) stopHeld(player);
                else {
                    spend(player, 5);
                    if (++state.heldTicks >= 60) { gift(player, (ServerPlayer) target); stopHeld(player); }
                }
            }
        }
        updateActionSpeed(player, state);
        sync(player, state);
    }
    private static void sync(ServerPlayer player, Runtime state) {
        var value = new VampireHudState(state.held, state.heldTicks, state.clawTicks);
        if (!value.equals(player.getAttachedOrCreate(VampireAttachments.HUD))) player.setAttached(VampireAttachments.HUD, value);
    }
    private static void updateActionSpeed(ServerPlayer player, Runtime state) {
        double multiplier = state.clawTicks > 0 ? 0.5 : state.held == VampireHudState.Held.GIFT ? 0.3 : 1;
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var old = attribute.getModifier(ACTION_SPEED);
        if (old != null && old.amount() == multiplier - 1) return;
        attribute.removeModifier(ACTION_SPEED);
        if (multiplier != 1) attribute.addTransientModifier(new AttributeModifier(ACTION_SPEED, multiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static boolean trainedHamon(LivingEntity entity) {
        var memory = entity.getAttachedOrCreate(VampireHamon.MEMORY);
        return HamonService.hasHamon(entity) || !memory.formerUser() && (memory.strength() > 0 || !memory.technique().isEmpty());
    }
    private static boolean canGift(ServerPlayer donor, LivingEntity target) {
        return donor.getHealth() > 10 && target instanceof ServerPlayer recipient && recipient != donor && !recipient.isRemoved()
                && !recipient.isSpectator() && !progress(recipient).vampire() && SpeciesHooks.canBecomeVampire(recipient)
                && !trainedHamon(recipient) && recipient.getHealth() <= 6;
    }
    private static void gift(ServerPlayer donor, ServerPlayer recipient) {
        boolean wasDead = recipient.getHealth() <= 0;
        if (!canGift(donor, recipient) || !grant(recipient, false)) return;
        donor.hurtServer(donor.level(), damage(donor.level(), "blood_gift", null), 10);
        recipient.heal(recipient.getMaxHealth());
        recipient.deathTime = 0;
        if (wasDead) {
            if (!recipient.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY) && !recipient.isSpectator()) {
                recipient.setExperienceLevels(0); recipient.setExperiencePoints(0);
            }
            VampireResurrectionPayload.send(recipient);
        }
    }
    public static boolean canDrainBloodFrom(LivingEntity target) {
        if (!target.isAlive() || target.typeHolder().is(CANNOT_DRAIN)) return false;
        if (target.typeHolder().is(CAN_DRAIN)) return true;
        if (SpeciesHooks.undeadOrVampiric(target)) return false;
        return target instanceof Player || target instanceof AgeableMob || target instanceof Npc || target instanceof AbstractIllager || target instanceof WaterAnimal;
    }
    public static boolean drain(ServerPlayer attacker, LivingEntity victim) {
        if (!canAct(attacker) || attacker.level().getDifficulty() == Difficulty.PEACEFUL || !canDrainBloodFrom(victim)) return false;
        if (!victim.hurtServer(attacker.level(), damage(attacker.level(), "blood_drain", attacker), 2)) return false;
        victim.indicateDamage(attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ());
        float gain = VampireConfig.server().bloodDrainMultiplier()[attacker.level().getDifficulty().getId()];
        if (victim instanceof Player) gain *= 5;
        else if (victim instanceof Npc || victim instanceof AbstractIllager) gain *= 4;
        if (trainedHamon(victim)) gain *= 1.5F;
        var freeze = victim.getEffect(VampireCombatService.FREEZE);
        if (freeze != null) gain *= 1 - Math.min((freeze.getAmplifier() + 1) * .2F, 1);
        attacker.setAttached(VampireAttachments.PROGRESS, progress(attacker).withBlood(progress(attacker).blood() + gain));
        if (!victim.isAlive()) VampireZombieService.onDrainedKill(attacker, victim);
        int amplifier = attacker.level().getDifficulty().getId() - 1;
        for (var effect : DRAIN_EFFECTS) {
            var old = victim.getEffect(effect);
            victim.addEffect(new MobEffectInstance(effect, (old == null ? 0 : old.getDuration()) + 40, amplifier));
        }
        return true;
    }
    /** The source uses the normal invulnerability window; a held drain is not damage every tick. */
    private static void clawHit(ServerPlayer player) {
        LivingEntity target = target(player, 8);
        if (target != null) {
            var source = player.damageSources().playerAttack(player);
            float amount = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 4;
            if (target.hurtServer(player.level(), source, amount)) {
                player.level().playSound(null, target.getX(), target.getEyeY(), target.getZ(), JojoSounds.get("vampire_claw_lacerate"), target.getSoundSource(), 1.2F, 0.8F);
                target.knockback(2, player.getX() - target.getX(), player.getZ() - target.getZ(), source, amount);
                target.syncVelocity = true;
                VampireCombatService.onClawHit(target, player);
            }
        } else {
            var hit = player.level().clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(10)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && player.level().getGameRules().get(JojoGameRules.BREAK_BLOCKS)) {
                var pos = hit.getBlockPos();
                destroyBlock(player, pos);
                for (var direction : net.minecraft.core.Direction.values()) destroyBlock(player, pos.relative(direction));
                sound(player, "heavy_punch", 1.5F, 1.2F);
            }
        }
    }
    private static void destroyBlock(ServerPlayer player, BlockPos pos) {
        if (!player.level().getGameRules().get(JojoGameRules.BREAK_BLOCKS) || !player.mayBuild() || !player.level().mayInteract(player, pos)) return;
        var state = player.level().getBlockState(pos);
        if (state.isAir()) return;
        float hardness = state.getDestroySpeed(player.level(), pos);
        if (hardness < 0) return;
        double duration = player.isCreative() ? 0 : hardness / (player.getDestroySpeed(state) / 2);
        if (!player.hasCorrectToolForDrops(state)) duration /= 3;
        if (duration <= 2.5 * Math.sqrt(player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                && net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(player.level(), player, pos, state, player.level().getBlockEntity(pos)))
            player.level().destroyBlock(pos, !player.isCreative(), player);
    }
    public static LivingEntity target(ServerPlayer player, double range) { return target(player, range, false); }
    private static LivingEntity target(ServerPlayer player, double range, boolean dyingPlayers) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(range));
        var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearest = block.getType() == HitResult.Type.MISS ? range * range : start.distanceToSqr(block.getLocation());
        LivingEntity result = null;
        for (var candidate : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
                entity -> entity != player && (entity.isAlive() || dyingPlayers && entity instanceof ServerPlayer && !entity.isRemoved()) && !entity.isSpectator() && !(entity instanceof StarPlatinumEntity))) {
            var bounds = candidate.getBoundingBox().inflate(candidate.getPickRadius());
            var hit = bounds.contains(start) ? java.util.Optional.of(start) : bounds.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) <= nearest) { result = candidate; nearest = start.distanceToSqr(hit.get()); }
        }
        return result;
    }
    private static void updatePassives(ServerPlayer player, Runtime runtime) {
        var progress = progress(player);
        int difficulty = player.level().getDifficulty().getId(), level = progress.bloodLevel(difficulty);
        int stage = VampireLifecycle.curingStage(player);
        if (runtime.passiveLevel == level && runtime.passiveDifficulty == difficulty && runtime.passiveCuringStage == stage) return;
        runtime.passiveLevel = level; runtime.passiveDifficulty = difficulty; runtime.passiveCuringStage = stage;
        for (int i = 0; i < PASSIVES.size(); i++) {
            var type = PASSIVES.get(i); var current = player.getEffect(type); int amplifier = VampireLifecycle.passiveAmplifier(player, type);
            if (amplifier >= 0) {
                if (current == null || current.getAmplifier() < amplifier || current.getAmplifier() != amplifier && !current.showIcon()) {
                    float missing = player.getMaxHealth() - player.getHealth();
                    boolean increased = type == MobEffects.HEALTH_BOOST && (current == null || current.getAmplifier() < amplifier);
                    player.removeEffect(type);
                    player.addEffect(new MobEffectInstance(type, -1, amplifier, false, false, false));
                    if (type == MobEffects.HEALTH_BOOST) player.setHealth(increased ? player.getMaxHealth() - missing : Math.min(player.getHealth(), player.getMaxHealth()));
                }
            } else if (current != null) player.removeEffect(type);
        }
    }
    /** Original GameplayEventHandler.cancelPotionRemoval protects active hidden vampire passives. */
    public static boolean protectsPassive(LivingEntity entity, Holder<MobEffect> type) {
        if (!(entity instanceof Player player) || !progress(player).vampire()) return false;
        var current = entity.getEffect(type);
        if (current == null || current.showIcon() || current.isVisible()) return false;
        int expected = VampireLifecycle.passiveAmplifier(player, type);
        return expected >= 0 && current.getAmplifier() == expected;
    }
    public static float payHealing(LivingEntity entity, float amount) {
        if (!(entity instanceof ServerPlayer player) || !progress(player).vampire() || !player.isAlive()) return amount;
        if (player.hasEffect(VampireItems.SUN_BURN)) return 0;
        float cost = VampireConfig.server().bloodHealCost()[player.level().getDifficulty().getId()];
        float heal = Math.max(0, Math.min(amount, Math.min(cost == 0 ? Float.POSITIVE_INFINITY : progress(player).blood() / cost, player.getMaxHealth() - player.getHealth())));
        spend(player, heal * cost);
        return heal;
    }
    public static void spend(ServerPlayer player, float amount) {
        if (!player.isCreative()) player.setAttached(VampireAttachments.PROGRESS, progress(player).withBlood(progress(player).blood() - amount));
    }
    public static void tickSunDamage(ServerPlayer player) {
        var level = player.level();
        if (!SpeciesHooks.takesSunDamage(player) || VampireLifecycle.isSleepingInCoffin(player) || player.damageCooldownTime > 10 || !level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()
                || !level.isBrightOutside() || level.isRaining() || level.isThundering()) return;
        BlockPos pos = BlockPos.containing(player.getX(), Math.round(player.getY(1)), player.getZ());
        if (player.getVehicle() instanceof AbstractBoat) pos = pos.above();
        if (player.getLightLevelDependentMagicValue() <= 0.5F || !level.canSeeSky(pos)) return;
        if (player.hurtServer(level, damage(level, "ultraviolet", null), 4)) {
            var old = player.getEffect(VampireItems.SUN_BURN);
            int duration = old == null ? 60 : old.getDuration() + 60 / Math.max(level.getDifficulty().getId(), 1);
            int amplifier = old == null ? 0 : duration / 60;
            player.addEffect(new MobEffectInstance(VampireItems.SUN_BURN, duration, amplifier, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier, false, false, true));
        }
    }
    private static float maximumBlood(Player player) { return VampireProgress.MAX_BLOOD * VampireConfig.server().maxBloodMultiplier()[player.level().getDifficulty().getId()]; }
    private static DamageSource damage(ServerLevel level, String name, LivingEntity attacker) {
        var holder = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, RipplesFabric.id(name)));
        return new DamageSource(holder, attacker);
    }
    private static void sound(ServerPlayer player, String name, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), JojoSounds.get(name), player.getSoundSource(), volume, pitch);
    }
    private static void message(ServerPlayer player, String key) { player.sendOverlayMessage(Component.translatable("jojo.chat.message." + key)); }
    /** Original PowerBaseImpl.onNewPowerGiven offers controls help; keybind components honor local remapping. */
    private static void notifyControls(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("jojo.fabric.vampire.awakened", Component.keybind("jojo.key.non_stand_mode"),
                Component.keybind("key.jojo.fabric.progress")));
    }
    private VampireService() { }
}
