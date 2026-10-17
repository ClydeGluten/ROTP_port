package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ZombieProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Separate zombie-player power. Root connects shared lifecycle callbacks exactly once. */
public final class ZombieService {
    private static final Map<LivingEntity, Runtime> RUNTIME = new WeakHashMap<>();
    private static final net.minecraft.resources.Identifier ACTION_SPEED = RipplesFabric.id("zombie_action_speed");
    private static final List<Holder<MobEffect>> PASSIVES = List.of(MobEffects.HEALTH_BOOST, MobEffects.STRENGTH,
            MobEffects.SPEED, MobEffects.HASTE, MobEffects.JUMP_BOOST, MobEffects.NIGHT_VISION);
    private static final class Runtime {
        ZombieAttachments.Held held = ZombieAttachments.Held.NONE;
        int heldTicks, clawTicks, fadeTicks, leapCooldown, heartbeat, level = Integer.MIN_VALUE, difficulty = -1;
        boolean disguise, devouring;
    }
    public static void initialize() { ZombieAttachments.initialize(); ZombieActionPayload.initialize(); }
    public static ZombieProgress progress(LivingEntity entity) { return entity.getAttachedOrCreate(ZombieAttachments.PROGRESS); }
    public static boolean hasZombie(LivingEntity entity) { return progress(entity).zombie(); }
    private static Runtime runtime(LivingEntity entity) { return RUNTIME.computeIfAbsent(entity, ignored -> new Runtime()); }
    private static int difficulty(LivingEntity entity) { return entity.level().getDifficulty().getId(); }
    private static float maximum(LivingEntity entity) { return Math.max(1, 1000 * VampireConfig.server().maxBloodMultiplier()[difficulty(entity)]); }
    public static boolean freeMainHand(LivingEntity entity) {
        var hand = entity.getMainHandItem();
        return hand.isEmpty() || hand.is(HamonItems.GLOVES) || hand.is(HamonItems.BUBBLE_GLOVES);
    }

    /** Admin-command acquisition only. No survival infection or turning-vampire owner is invented. */
    public static boolean grant(ServerPlayer player) {
        if (hasZombie(player) || HamonService.hasHamon(player) || VampireService.progress(player).vampire()
                || PillarmanService.hasPillarman(player)) return false;
        var granted = ZombieProgress.grant(maximum(player));
        player.setAttached(ZombieAttachments.PROGRESS, player.isCreative() ? granted.withBlood(granted.maxBlood()) : granted);
        updatePassives(player, runtime(player)); sync(player, runtime(player));
        return true;
    }
    public static void clear(LivingEntity entity) {
        boolean had = hasZombie(entity);
        entity.setAttached(ZombieAttachments.PROGRESS, ZombieProgress.EMPTY);
        clearRuntime(entity);
        if (had) for (var effect : PASSIVES) {
            var current = entity.getEffect(effect);
            if (current != null && !current.showIcon() && !current.isVisible()) entity.removeEffect(effect);
        }
    }
    public static void onDeath(LivingEntity entity) { if (hasZombie(entity)) clear(entity); }
    public static void copyAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        var retained = alive ? progress(oldPlayer) : ZombieProgress.EMPTY;
        clearRuntime(oldPlayer); clearRuntime(newPlayer);
        newPlayer.setAttached(ZombieAttachments.PROGRESS, retained);
        if (retained.zombie()) { updatePassives(newPlayer, runtime(newPlayer)); sync(newPlayer, runtime(newPlayer)); }
    }
    public static void disconnect(ServerPlayer player) {
        var server = player.level().getServer();
        if (server.isSameThread()) clearRuntime(player); else server.execute(() -> clearRuntime(player));
    }
    public static void stopServer() { RUNTIME.clear(); }
    private static void clearRuntime(LivingEntity entity) {
        RUNTIME.remove(entity);
        var speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(ACTION_SPEED);
        entity.setAttached(ZombieAttachments.HUD, ZombieAttachments.Hud.EMPTY);
        entity.setAttached(ZombieAttachments.VISUAL, ZombieAttachments.Visual.EMPTY);
    }
    public static boolean canAct(ServerPlayer player) {
        return hasZombie(player) && player.isAlive() && !player.isSpectator() && !TimeStopService.isFrozen(player)
                && !VampireCombatService.isStunned(player);
    }
    public static boolean claw(ServerPlayer player) {
        if (!canAct(player) || progress(player).disguised() || !freeMainHand(player)
                || !player.isCreative() && progress(player).blood() < 60) return false;
        var state = runtime(player);
        if (state.clawTicks > 0 || state.held == ZombieAttachments.Held.DISGUISE) return false;
        // The original consumesEnergy override is empty. Devour permits this concurrent action.
        state.clawTicks = 1;
        updateSpeed(player, state); sync(player, state); return true;
    }
    public static boolean startHeld(ServerPlayer player, ZombieAttachments.Held held) {
        if (!canAct(player) || held == ZombieAttachments.Held.NONE) return false;
        var state = runtime(player);
        if (state.clawTicks > 0 || state.held == held || state.held == ZombieAttachments.Held.DISGUISE) return false;
        if (held == ZombieAttachments.Held.DEVOUR && (progress(player).disguised()
                || !freeMainHand(player) || difficulty(player) == 0)) return false;
        state.held = held; state.heldTicks = state.heartbeat = 0;
        sync(player, state); return true;
    }
    public static void refreshHeld(ServerPlayer player) { var state = RUNTIME.get(player); if (state != null) state.heartbeat = 0; }
    public static void stopHeld(ServerPlayer player) {
        var state = RUNTIME.get(player);
        if (state != null) { state.held = ZombieAttachments.Held.NONE; state.heldTicks = state.heartbeat = 0; state.devouring = false; sync(player, state); }
    }
    /** Root calls after accepted nonzero damage; Disguise retains the original default hold behavior. */
    public static void onDamage(LivingEntity entity) {
        var state = RUNTIME.get(entity);
        if (entity instanceof ServerPlayer player && state != null && state.held == ZombieAttachments.Held.DEVOUR) stopHeld(player);
    }
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        if (!hasZombie(entity) || !entity.isAlive()) {
            if (hasZombie(entity) && !entity.isAlive()) clear(entity);
            else if (RUNTIME.containsKey(entity)) clearRuntime(entity);
            return;
        }
        var state = runtime(entity);
        if (entity.isSpectator() || entity.isRemoved()) { clearRuntime(entity); return; }
        if (TimeStopService.isFrozen(entity)) {
            // Preserve animation time while preventing a lost key release during stopped time from sticking.
            if (entity instanceof ServerPlayer player && state.held != ZombieAttachments.Held.NONE && ++state.heartbeat > 15) stopHeld(player);
            return;
        }
        var value = progress(entity).withMaximum(maximum(entity));
        float decay = entity instanceof Player player && player.isCreative() ? 0 : VampireConfig.server().bloodTickDown()[difficulty(entity)];
        entity.setAttached(ZombieAttachments.PROGRESS, value.withBlood(value.blood() - decay));
        if (entity instanceof Player player) player.getFoodData().setFoodLevel(17);
        entity.setAirSupply(entity.getMaxAirSupply());
        updatePassives(entity, state);
        if (state.leapCooldown > 0) state.leapCooldown--;
        if (state.fadeTicks > 0) state.fadeTicks--;
        state.devouring = false;
        if (entity instanceof ServerPlayer player) {
            if (state.clawTicks > 0) {
                int tick = state.clawTicks++;
                if (tick == 3) { sound(player, "zombie_swipe", 1, 1.25F); player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true); }
                if (tick == 5) clawHit(player);
                if (tick >= 8) state.clawTicks = 0;
            }
            if (state.held != ZombieAttachments.Held.NONE) {
                if (!canAct(player) || ++state.heartbeat > 15) stopHeld(player);
                else if (state.held == ZombieAttachments.Held.DEVOUR) {
                    if (progress(player).disguised() || !freeMainHand(player) || difficulty(player) == 0) stopHeld(player);
                    else {
                        var target = VampireService.target(player, 2);
                        if (target != null && canDevour(target)) {
                            state.devouring = true;
                            devour(player, target);
                        }
                        state.heldTicks++;
                    }
                } else if (++state.heldTicks >= 60) {
                    player.setAttached(ZombieAttachments.PROGRESS, progress(player).withDisguise(!progress(player).disguised()));
                    stopHeld(player); updatePassives(player, state);
                }
            }
        }
        updateSpeed(entity, state); sync(entity, state);
    }
    public static boolean canDevour(LivingEntity target) {
        return target.isAlive() && !target.isSpectator() && VampireBlood.canBleed(target)
                && !target.typeHolder().is(EntityTypeTags.UNDEAD) && !hasZombie(target)
                && !PillarmanService.hasPillarman(target)
                && (!(target instanceof Player player) || !VampireService.progress(player).vampire());
    }
    /** Revalidates even direct server callers; the client never selects a victim entity ID. */
    public static boolean devour(ServerPlayer player, LivingEntity target) {
        if (!canAct(player) || progress(player).disguised() || !freeMainHand(player) || difficulty(player) == 0
                || target.level() != player.level() || !canDevour(target) || VampireService.target(player, 2) != target) return false;
        float gain = VampireConfig.server().bloodDrainMultiplier()[difficulty(player)];
        if (target instanceof Player) gain *= 5;
        else if (target instanceof Npc || target instanceof AbstractIllager) gain *= 4;
        if (HamonService.hasHamon(target)) gain *= 1.5F;
        var freeze = target.getEffect(VampireCombatService.FREEZE);
        if (freeze != null) gain *= 1 - Math.min((freeze.getAmplifier() + 1) * .2F, 1);
        // Uses the vanilla damage window; rejected repeated hits award neither blood nor healing.
        if (!VampireZombieService.drainBlood(player, target, 2)) return false;
        target.indicateDamage(player.getX() - target.getX(), player.getZ() - target.getZ());
        addBlood(player, gain);
        float before = player.getHealth(); player.heal(gain * .5F);
        float healed = player.getHealth() - before;
        if (healed > 0) addBlood(player, healed * VampireConfig.server().bloodHealCost()[difficulty(player)]);
        if (target.isDeadOrDying()) {
            boolean created = HungryZombieEntity.createZombie(player.level(), null, target, false);
            VampireAdvancements.onDrainedKill(player, target, created);
        }
        return true;
    }
    private static void clawHit(ServerPlayer player) {
        var target = VampireService.target(player, 8);
        if (target != null) {
            var source = player.damageSources().playerAttack(player);
            float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 4;
            if (target.hurtServer(player.level(), source, damage)) {
                player.level().playSound(null, target.getX(), target.getEyeY(), target.getZ(), JojoSounds.get("zombie_claw_lacerate"), target.getSoundSource(), 1.2F, .8F);
                target.knockback(2, player.getX() - target.getX(), player.getZ() - target.getZ(), source, damage);
                target.syncVelocity = true; VampireCombatService.onClawHit(target, player);
            }
        } else {
            var hit = player.level().clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(10)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                destroyBlock(player, hit.getBlockPos()); sound(player, "heavy_punch", 1.5F, 1.2F);
            }
        }
    }
    private static void destroyBlock(ServerPlayer player, BlockPos pos) {
        if (!player.level().getGameRules().get(JojoGameRules.BREAK_BLOCKS) || !player.mayBuild() || !player.level().mayInteract(player, pos)) return;
        var block = player.level().getBlockState(pos);
        if (block.isAir()) return;
        float hardness = block.getDestroySpeed(player.level(), pos);
        if (hardness < 0) return;
        double duration = player.isCreative() ? 0 : hardness / (player.getDestroySpeed(block) / 2);
        if (!player.hasCorrectToolForDrops(block)) duration /= 3;
        if (duration <= 2.5 * Math.sqrt(player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                && net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(player.level(), player, pos, block, player.level().getBlockEntity(pos)))
            player.level().destroyBlock(pos, !player.isCreative(), player);
        else {
            var sound = block.getSoundType();
            player.level().playSound(null, pos, sound.getHitSound(), net.minecraft.sounds.SoundSource.BLOCKS, (sound.getVolume() + 1) / 8, sound.getPitch() * .5F);
        }
    }
    public static boolean leap(ServerPlayer player) {
        if (!canAct(player)) return false;
        var state = runtime(player);
        if (state.leapCooldown > 0 || state.clawTicks > 0 || state.held == ZombieAttachments.Held.DISGUISE
                || !(player.onGround() || player.getVehicle() != null && player.getVehicle().onGround())) return false;
        float strength = progress(player).leapStrength(difficulty(player));
        if (strength <= 0) return false;
        Vec3 motion = Vec3.directionFromRotation(Math.min(player.getXRot(), -30), player.getYRot()).scale(strength);
        var leaper = player.getVehicle() == null ? player : player.getVehicle();
        leaper.setOnGround(false); if (leaper instanceof LivingEntity living) living.setJumping(true);
        leaper.needsSync = true; leaper.setDeltaMovement(motion.x, motion.y * .5, motion.z); leaper.syncVelocity = true;
        player.setShiftKeyDown(false);
        var ground = player.level().getBlockState(player.getOnPos());
        if (!ground.isAir()) {
            player.level().sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
                    player.getX(), player.getY(), player.getZ(), (int) (150 * Math.min(.2F + strength / 3, 2.5F)), 0, 0, 0, .15);
            var sound = ground.getSoundType();
            player.level().playSound(null, player.blockPosition(), sound.getBreakSound(), player.getSoundSource(), sound.getVolume() * .25F * strength, sound.getPitch() * .75F);
        }
        state.leapCooldown = player.isCreative() ? 0 : 20; sync(player, state); return true;
    }
    public static int passiveAmplifier(LivingEntity entity, Holder<MobEffect> effect) {
        if (!hasZombie(entity)) return -1;
        if (effect == MobEffects.HEALTH_BOOST) return difficulty(entity) * 2;
        if (effect == MobEffects.NIGHT_VISION) return 0;
        if (PASSIVES.contains(effect)) return progress(entity).disguised() ? -1 : progress(entity).bloodLevel(difficulty(entity)) - 5;
        return -1;
    }
    private static void updatePassives(LivingEntity entity, Runtime state) {
        int level = progress(entity).bloodLevel(difficulty(entity)); boolean disguise = progress(entity).disguised();
        if (state.level == level && state.difficulty == difficulty(entity) && state.disguise == disguise) return;
        state.level = level; state.difficulty = difficulty(entity); state.disguise = disguise;
        for (var effect : PASSIVES) {
            var current = entity.getEffect(effect); int expected = passiveAmplifier(entity, effect);
            if (expected >= 0 && (current == null || current.getAmplifier() < expected || !current.showIcon() && current.getAmplifier() != expected)) {
                float missing = entity.getMaxHealth() - entity.getHealth();
                boolean increased = effect == MobEffects.HEALTH_BOOST && (current == null || current.getAmplifier() < expected);
                entity.removeEffect(effect);
                entity.addEffect(new MobEffectInstance(effect, -1, expected, false, false, false));
                if (effect == MobEffects.HEALTH_BOOST) entity.setHealth(increased ? entity.getMaxHealth() - missing : Math.min(entity.getHealth(), entity.getMaxHealth()));
            } else if (expected < 0 && current != null) entity.removeEffect(effect);
        }
    }
    public static boolean protectsPassive(LivingEntity entity, Holder<MobEffect> effect) {
        var current = entity.getEffect(effect);
        return hasZombie(entity) && current != null && !current.showIcon() && !current.isVisible()
                && passiveAmplifier(entity, effect) >= 0 && current.getAmplifier() == passiveAmplifier(entity, effect);
    }
    /** Original VampirismUtil.consumeEnergyOnHeal excludes zombies. Root must not charge vampire blood here. */
    public static float payHealing(LivingEntity entity, float amount) { return amount; }
    public static void addBlood(LivingEntity entity, float amount) {
        if (hasZombie(entity)) entity.setAttached(ZombieAttachments.PROGRESS, progress(entity).withBlood(progress(entity).blood() + amount));
    }
    public static boolean isMeat(ItemStack food) {
        // Food.isMeat in the original 1.16 catalog, including rotten flesh; modern food components lost this flag.
        return food.is(Items.BEEF) || food.is(Items.COOKED_BEEF) || food.is(Items.PORKCHOP) || food.is(Items.COOKED_PORKCHOP)
                || food.is(Items.CHICKEN) || food.is(Items.COOKED_CHICKEN) || food.is(Items.MUTTON) || food.is(Items.COOKED_MUTTON)
                || food.is(Items.RABBIT) || food.is(Items.COOKED_RABBIT) || food.is(Items.ROTTEN_FLESH);
    }
    public static void onFoodEaten(LivingEntity entity, ItemStack food, int nutrition) {
        if (!entity.level().isClientSide() && hasZombie(entity) && nutrition > 0 && isMeat(food)) {
            addBlood(entity, nutrition * 10F); entity.heal(nutrition);
        }
    }
    public static int maxStaminaFactor(LivingEntity entity) { return progress(entity).maxStaminaFactor(difficulty(entity)); }
    public static int staminaRegenFactor(LivingEntity entity) { return progress(entity).staminaRegenFactor(difficulty(entity)); }
    public static boolean isHighSaturation(LivingEntity entity) { return hasZombie(entity) && progress(entity).blood() / progress(entity).maxBlood() >= .8F; }
    /** Original zombie players are undead for UV, instant potions, Smite and Hamon regardless of disguise. */
    public static boolean isUndead(LivingEntity entity) { return hasZombie(entity); }
    public static void tickSunDamage(LivingEntity entity) {
        if (!hasZombie(entity) || !(entity.level() instanceof ServerLevel level)
                || !entity.isAlive() || entity.isSpectator() || TimeStopService.isFrozen(entity) || entity.damageCooldownTime > 10
                || !(entity instanceof Player) && !VampireConfig.server().undeadSunDamage()
                || entity instanceof Player player && VampireLifecycle.isSleepingInCoffin(player)
                || !level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling() || !level.isBrightOutside()
                || level.isRaining() || level.isThundering() || entity.getLightLevelDependentMagicValue() <= .5F) return;
        if (entity.getActiveEffects().stream().anyMatch(effect -> net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                .getKey(effect.getEffect().value()).equals(RipplesFabric.id("sun_resistance")))) return;
        var pos = BlockPos.containing(entity.getX(), Math.round(entity.getY(1)), entity.getZ());
        if (entity.getVehicle() instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat) pos = pos.above();
        if (!level.canSeeSky(pos)) return;
        var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, RipplesFabric.id("ultraviolet")));
        if (entity.hurtServer(level, new DamageSource(type), 4)) {
            var old = entity.getEffect(VampireItems.SUN_BURN);
            int duration = old == null ? 60 : old.getDuration() + 60 / Math.max(difficulty(entity), 1);
            int amplifier = old == null ? 0 : duration / 60;
            entity.addEffect(new MobEffectInstance(VampireItems.SUN_BURN, duration, amplifier, false, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier, false, false, true));
        }
    }
    private static void updateSpeed(LivingEntity entity, Runtime state) {
        var attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        double multiplier = state.clawTicks > 0 ? -.5 : 0;
        var old = attribute.getModifier(ACTION_SPEED);
        if (old != null && old.amount() == multiplier) return;
        attribute.removeModifier(ACTION_SPEED);
        if (multiplier != 0) attribute.addTransientModifier(new AttributeModifier(ACTION_SPEED, multiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static void sync(LivingEntity entity, Runtime state) {
        var hud = new ZombieAttachments.Hud(state.held, state.heldTicks, state.clawTicks, state.leapCooldown);
        if (!hud.equals(entity.getAttachedOrCreate(ZombieAttachments.HUD))) entity.setAttached(ZombieAttachments.HUD, hud);
        var old = entity.getAttachedOrCreate(ZombieAttachments.VISUAL);
        if (state.clawTicks > 0) state.fadeTicks = 0;
        else if (old.clawTicks() > 0) state.fadeTicks = 17;
        boolean disguised = progress(entity).disguised();
        var visual = new ZombieAttachments.Visual(hasZombie(entity) && !disguised,
                hasZombie(entity) && disguised && entity.getAttachedOrCreate(VampirePresentation.GLOW_EYES),
                state.clawTicks, state.fadeTicks, entity.getMainArm() == HumanoidArm.LEFT, state.devouring);
        if (!visual.equals(old)) entity.setAttached(ZombieAttachments.VISUAL, visual);
    }
    private static void sound(ServerPlayer player, String id, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), JojoSounds.get(id), player.getSoundSource(), volume, pitch);
    }
    private ZombieService() { }
}
