package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.PillarmanProgress;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.advancements.triggers.PlayerTrigger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Authoritative original Pillar state/economy; shared species integration is deliberately outside this module. */
public final class PillarmanService {
    private static final Map<UUID, ArrayList<Integer>> FUSES = new HashMap<>();
    private static final Map<UUID, PillarmanProgress> PASSIVE_STATE = new HashMap<>();
    private static final PlayerTrigger EVOLVE = trigger("pillarman_evolve");
    private static final PlayerTrigger EVOLVE_AJA = trigger("pillarman_evolve_aja");
    private static final PlayerTrigger WIND = trigger("pillarman_wind_mode");
    private static final PlayerTrigger HEAT = trigger("pillarman_heat_mode");
    private static final PlayerTrigger LIGHT = trigger("pillarman_light_mode");
    private static PlayerTrigger trigger(String name) { return Registry.register(BuiltInRegistries.TRIGGER_TYPES, RipplesFabric.id(name), new PlayerTrigger()); }
    public static void initialize() { PillarmanAttachments.initialize(); PillarmanActionPayload.initialize(); }
    public static void onDeath(LivingEntity entity) { if (hasPillarman(entity)) resetTransient(entity, true); }
    public static void copyAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        var retained = alive ? state(oldPlayer) : state(oldPlayer).afterDeath();
        resetTransient(oldPlayer, !alive); resetTransient(newPlayer, !alive);
        newPlayer.setAttached(PillarmanAttachments.PROGRESS, retained); updateAttributes(newPlayer);
    }
    public static void disconnect(ServerPlayer player) { resetTransient(player, false); }
    public static void clearAllRuntime() { FUSES.clear(); PASSIVE_STATE.clear(); PillarmanBaseActions.clearAll(); com.github.standobyte.jojo.fabric.pillarman.PillarBodyParts.clearAllRuntime(); com.github.standobyte.jojo.fabric.pillarman.PillarWindActions.clearAllRuntime(); com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.clearAllRuntime(); com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.clearAllRuntime(); }
    public static PillarmanProgress state(LivingEntity entity) { return entity.getAttachedOrCreate(PillarmanAttachments.PROGRESS); }
    public static boolean hasPillarman(LivingEntity entity) { return state(entity).pillarman(); }
    /** Administrative acquisition, with the same non-Stand exclusivity as source NonStandPower. */
    public static boolean grant(ServerPlayer player) {
        if (hasPillarman(player) || HamonService.hasHamon(player) || VampireService.progress(player).vampire() || ZombieService.hasZombie(player)) return false;
        player.setAttached(PillarmanAttachments.PROGRESS, PillarmanProgress.acquired(maximumEnergy(player, 1)));
        updateAttributes(player); sound(player, "pillar_man_awakening", 1, 1); return true;
    }
    public static void clear(LivingEntity entity) {
        entity.setAttached(PillarmanAttachments.PROGRESS, PillarmanProgress.EMPTY);
        resetTransient(entity, false); updateAttributes(entity);
        removePassive(entity, MobEffects.REGENERATION); removePassive(entity, MobEffects.NIGHT_VISION);
    }
    public static boolean setStage(LivingEntity entity, int stage) {
        if (!hasPillarman(entity) || stage < 1 || stage > 4 || entity.level().isClientSide()) return false;
        entity.setAttached(PillarmanAttachments.PROGRESS, state(entity).withStage(stage, maximumEnergy(entity, stage)));
        updateAttributes(entity);
        if (entity instanceof ServerPlayer player) {
            if (stage == 2 || stage == 3) EVOLVE.trigger(player);
            // The source intentionally triggers Aja's advancement at stage 3, not the mask's stage 4.
            if (stage == 3) EVOLVE_AJA.trigger(player);
        }
        return true;
    }
    public static boolean setMode(LivingEntity entity, PillarmanProgress.Mode mode) {
        if (!hasPillarman(entity) || mode == null || entity.level().isClientSide()) return false;
        entity.setAttached(PillarmanAttachments.PROGRESS, state(entity).withMode(mode));
        if (entity instanceof ServerPlayer player) switch (mode) {
            case WIND -> { WIND.trigger(player); setBlades(entity, false); sound(player, "pillar_man_wind_mode", 1, 1); }
            case HEAT -> { HEAT.trigger(player); setBlades(entity, false); sound(player, "pillar_man_heat_mode", 1, 1); }
            case LIGHT -> { LIGHT.trigger(player); sound(player, "pillar_man_light_mode", 1, 1); }
            default -> { }
        }
        return true;
    }
    public static void setStoneForm(LivingEntity entity, boolean enabled) {
        if (hasPillarman(entity) && !entity.level().isClientSide()) entity.setAttached(PillarmanAttachments.PROGRESS, state(entity).withStoneForm(enabled));
    }
    public static void setBlades(LivingEntity entity, boolean visible) { entity.setAttached(PillarmanAttachments.BLADES, hasPillarman(entity) && visible); }
    /** Return true only when an existing Pillar actually evolves; caller handles item wear/timer. */
    public static boolean applyMask(ServerPlayer player, boolean aja) {
        if (!hasPillarman(player) || player.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        if (aja && state(player).stage() < 4) {
            setStage(player, 4); setMode(player, PillarmanProgress.Mode.values()[1 + player.getRandom().nextInt(3)]); return true;
        }
        return !aja && state(player).stage() == 1 && setStage(player, 2);
    }
    public static float maximumEnergy(LivingEntity entity) { return maximumEnergy(entity, state(entity).stage()); }
    private static float maximumEnergy(LivingEntity entity, int stage) { return 1000 * VampireConfig.current(entity.level().isClientSide()).maxBloodMultiplier()[entity.level().getDifficulty().getId()] * stage; }
    public static void addEnergy(LivingEntity entity, float amount) { if (hasPillarman(entity) && Float.isFinite(amount)) entity.setAttached(PillarmanAttachments.PROGRESS, state(entity).withEnergy(state(entity).energy() + amount)); }
    public static boolean spend(LivingEntity entity, float amount) {
        if (!hasPillarman(entity) || !Float.isFinite(amount) || amount < 0) return false;
        if (entity instanceof Player player && player.isCreative()) return true;
        if (state(entity).energy() < amount) return false;
        addEnergy(entity, -amount); return true;
    }
    public static void onFoodEaten(LivingEntity entity, int nutrition) { if (nutrition > 0) addEnergy(entity, nutrition * 10F); }
    public static float consumeEnergyOnHeal(LivingEntity entity, float amount) {
        if (!hasPillarman(entity)) return amount;
        if (entity.hasEffect(VampireItems.SUN_BURN)) return 0;
        if (state(entity).stage() <= 1) return amount;
        float cost = VampireConfig.server().bloodHealCost()[entity.level().getDifficulty().getId()];
        float heal = Math.max(0, Math.min(amount, Math.min(cost == 0 ? Float.POSITIVE_INFINITY : state(entity).energy() / cost, entity.getMaxHealth() - entity.getHealth())));
        spend(entity, heal * cost); return heal;
    }
    public static boolean highLifeForce(LivingEntity entity) { return hasPillarman(entity) && state(entity).stage() > 1; }
    public static float staminaFactor(LivingEntity entity) { return hasPillarman(entity) ? state(entity).stage() : 1; }
    public static float targetResolveMultiplier(LivingEntity entity) { return staminaFactor(entity); }
    /** Forge's removed SWIM_SPEED attribute was a multiplier on water acceleration. */
    public static float swimmingAcceleration(LivingEntity entity, float acceleration) { return hasPillarman(entity) ? acceleration * (1 + .15F * state(entity).attributeLevel()) : acceleration; }
    public static void addEatenTntFuse(LivingEntity entity, int fuse) { if (hasPillarman(entity) && fuse >= 0) FUSES.computeIfAbsent(entity.getUUID(), id -> new ArrayList<>()).add(fuse); }
    public static void resetTransient(LivingEntity entity, boolean death) {
        if (entity instanceof ServerPlayer player) {
            com.github.standobyte.jojo.fabric.pillarman.PossessionService.stopPillar(player);
            com.github.standobyte.jojo.fabric.pillarman.PillarWindActions.clear(player);
            com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.clear(player);
            com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.clear(player);
        }
        FUSES.remove(entity.getUUID()); PASSIVE_STATE.remove(entity.getUUID()); PillarmanBaseActions.clear(entity); com.github.standobyte.jojo.fabric.pillarman.PillarBodyParts.clear(entity); setBlades(entity, false);
        if (death && hasPillarman(entity)) entity.setAttached(PillarmanAttachments.PROGRESS, state(entity).afterDeath());
    }
    public static void tick(ServerPlayer player) {
        if (!hasPillarman(player)) return;
        if (!player.isAlive()) { resetTransient(player, true); return; }
        if (TimeStopService.isFrozen(player)) return;
        var value = state(player).withMaximum(maximumEnergy(player));
        if (!player.isCreative()) value = value.withEnergy(value.energy() - VampireConfig.server().bloodTickDown()[player.level().getDifficulty().getId()] * value.stage());
        if (!value.equals(state(player))) player.setAttached(PillarmanAttachments.PROGRESS, value);
        updateAttributes(player);
        if (value.stage() > 1) { player.getFoodData().setFoodLevel(17); player.setAirSupply(player.getMaxAirSupply()); }
        var previous = PASSIVE_STATE.put(player.getUUID(), value);
        if (previous == null || previous.energy() != value.energy() || previous.stage() != value.stage()) {
            passive(player, MobEffects.NIGHT_VISION, value.stage() > 1 ? 0 : -1);
            passive(player, MobEffects.REGENERATION, value.regenerationAmplifier());
        }
        if (value.stoneForm()) {
            player.addEffect(new MobEffectInstance(VampireCombatService.STUN, 20, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 3, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
        }
        var fuses = FUSES.get(player.getUUID());
        if (fuses != null) {
            fuses.replaceAll(fuse -> fuse - 1);
            fuses.removeIf(fuse -> {
                if (fuse > 0) return false;
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                        net.minecraft.sounds.SoundSource.BLOCKS, .1F, (1 + (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * .2F) * .7F);
                return true;
            });
            if (fuses.isEmpty()) FUSES.remove(player.getUUID());
        }
        PillarmanBaseActions.tick(player);
    }
    public static void updateAttributes(LivingEntity entity) {
        int level = hasPillarman(entity) ? state(entity).attributeLevel() : 0;
        modifier(entity, Attributes.ATTACK_DAMAGE, "attack_damage", level);
        modifier(entity, Attributes.ATTACK_SPEED, "attack_speed", .15 * level);
        modifier(entity, Attributes.MOVEMENT_SPEED, "movement_speed", .01 * level);
        modifier(entity, Attributes.MAX_HEALTH, "max_health", 10 * level);
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
    }
    private static void modifier(LivingEntity entity, Holder<Attribute> type, String name, double amount) {
        var attribute = entity.getAttribute(type); if (attribute == null) return;
        var id = RipplesFabric.id("pillarman_" + name); var old = attribute.getModifier(id);
        if (old != null && old.amount() == amount) return;
        attribute.removeModifier(id);
        if (amount != 0) attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }
    private static void removePassive(LivingEntity entity, Holder<MobEffect> type) {
        var old = entity.getEffect(type); if (old != null && !old.showIcon() && !old.isVisible()) entity.removeEffect(type);
    }
    private static void passive(LivingEntity entity, Holder<MobEffect> type, int amplifier) {
        var old = entity.getEffect(type);
        if (amplifier < 0) { entity.removeEffect(type); return; }
        if (old == null || old.getAmplifier() < amplifier || !old.showIcon() && old.getAmplifier() != amplifier) {
            entity.removeEffect(type); entity.addEffect(new MobEffectInstance(type, -1, amplifier, false, false, false));
        }
    }
    public static boolean protectsPassive(LivingEntity entity, Holder<MobEffect> type) {
        if (!hasPillarman(entity)) return false;
        var old = entity.getEffect(type);
        int expected = type == MobEffects.NIGHT_VISION ? state(entity).stage() > 1 ? 0 : -1 : type == MobEffects.REGENERATION ? state(entity).regenerationAmplifier() : -1;
        return expected >= 0 && old != null && !old.showIcon() && !old.isVisible() && old.getAmplifier() == expected;
    }
    public static void sound(ServerPlayer player, String name, float volume, float pitch) { player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), JojoSounds.get(name), player.getSoundSource(), volume, pitch); }
    private PillarmanService() { }
}
