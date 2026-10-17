package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Source JojoModUtil/VampirismUtil distinctions shared by every non-Stand power. */
public final class SpeciesHooks {
    public static void initialize() {
        ZombieService.initialize();
        PillarmanService.initialize();
        com.github.standobyte.jojo.fabric.pillarman.PillarBodyParts.initialize();
        com.github.standobyte.jojo.fabric.pillarman.PossessionService.initialize();
        com.github.standobyte.jojo.fabric.pillarman.PillarWindActions.initialize();
        com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.initialize();
        com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.initialize();
        SpeciesCommands.initialize();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                ZombieService.tick(player);
                PillarmanService.tick(player);
                com.github.standobyte.jojo.fabric.pillarman.PillarBodyParts.tick(player);
                com.github.standobyte.jojo.fabric.pillarman.PossessionService.tick(player);
                com.github.standobyte.jojo.fabric.pillarman.PillarWindActions.tick(player);
                com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.tick(player);
                com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.tick(player);
                if (!TimeStopService.isFrozen(player) && player.isAlive()) {
                    ZombieService.tickSunDamage(player);
                    if (PillarmanService.hasPillarman(player)) VampireService.tickSunDamage(player);
                }
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            boolean solarOrHamon = source.is(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DAMAGE_TYPE, RipplesFabric.id("ultraviolet")))
                    || source.is(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DAMAGE_TYPE, RipplesFabric.id("hamon")));
            return !(solarOrHamon && stoneForm(entity)) && !PillarmanBaseActions.evade(entity, source)
                    && !com.github.standobyte.jojo.fabric.pillarman.PillarLightCombat.cancelIncomingDamage(entity, source);
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, actual, blocked) -> {
            if (actual > 0) ZombieService.onDamage(entity);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            ZombieService.onDeath(entity); PillarmanService.onDeath(entity);
            if(entity instanceof ServerPlayer player) com.github.standobyte.jojo.fabric.pillarman.PossessionService.onDeath(player);
            StandEffects.cleanup(entity, StandEffects.Removal.DEATH);
        });
        var phase = RipplesFabric.id("species_after_attachment_copy");
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, phase);
        ServerPlayerEvents.AFTER_RESPAWN.register(phase, (oldPlayer, newPlayer, alive) -> {
            ZombieService.copyAfterRespawn(oldPlayer, newPlayer, alive);
            PillarmanService.copyAfterRespawn(oldPlayer, newPlayer, alive);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PillarmanService.updateAttributes(handler.player);
            com.github.standobyte.jojo.fabric.pillarman.PossessionService.onJoin(handler.player);
        });
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof ServerPlayer owner) com.github.standobyte.jojo.fabric.pillarman.PossessionService.syncTo(owner, player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            ZombieService.disconnect(handler.player); PillarmanService.disconnect(handler.player);
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ZombieService.stopServer(); PillarmanService.clearAllRuntime();
            com.github.standobyte.jojo.fabric.pillarman.PossessionService.clearAllRuntime();
        });
    }

    public static boolean vampire(LivingEntity entity) { return entity instanceof Player player && VampireService.progress(player).vampire(); }
    /** The original player's vanilla UNDEAD attribute: Pillar Man deliberately stays outside it. */
    public static boolean undeadPlayer(LivingEntity entity) { return vampire(entity) || ZombieService.hasZombie(entity); }
    public static boolean undeadOrVampiric(LivingEntity entity) {
        return entity.typeHolder().is(EntityTypeTags.UNDEAD) || undeadPlayer(entity) || PillarmanService.hasPillarman(entity);
    }
    public static boolean hasNonStand(LivingEntity entity) { return HamonService.hasHamon(entity) || vampire(entity) || ZombieService.hasZombie(entity) || PillarmanService.hasPillarman(entity); }
    public static boolean canBecomeVampire(LivingEntity entity) { return !ZombieService.hasZombie(entity) && !PillarmanService.hasPillarman(entity); }
    public static boolean stoneForm(LivingEntity entity) { return PillarmanService.hasPillarman(entity) && PillarmanService.state(entity).stoneForm(); }
    public static boolean sunProtected(LivingEntity entity) {
        return stoneForm(entity) || entity.getActiveEffects().stream().anyMatch(effect ->
                RipplesFabric.id("sun_resistance").equals(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value())));
    }
    public static boolean takesSunDamage(LivingEntity entity) { return undeadOrVampiric(entity) && !sunProtected(entity); }
    public static boolean protectsPassive(LivingEntity entity, Holder<MobEffect> effect) {
        return VampireService.protectsPassive(entity, effect) || ZombieService.protectsPassive(entity, effect)
                || PillarmanService.protectsPassive(entity, effect);
    }
    public static float payHealing(LivingEntity entity, float amount) {
        return PillarmanService.hasPillarman(entity) ? PillarmanService.consumeEnergyOnHeal(entity, amount)
                : VampireService.payHealing(entity, amount);
    }
    public static void onFoodEaten(LivingEntity entity, ItemStack item) {
        if (entity.level().isClientSide()) return;
        var food = item.get(DataComponents.FOOD);
        if (food == null) return;
        ZombieService.onFoodEaten(entity, item, food.nutrition());
        PillarmanService.onFoodEaten(entity, food.nutrition());
    }
    public static boolean neutralToMonsters(Player player) {
        return vampire(player) && VampireLifecycle.curingStage(player) < 3 || ZombieService.hasZombie(player)
                || PillarmanService.hasPillarman(player) && (stoneForm(player) || PillarmanService.state(player).stage() > 1);
    }
    public static boolean targetedByGolem(Player player) {
        return vampire(player) && VampireLifecycle.curingStage(player) < 4 || PillarmanService.hasPillarman(player);
    }
    public static float staminaMaximum(Player player) {
        if (PillarmanService.hasPillarman(player)) return PillarmanService.staminaFactor(player);
        if (ZombieService.hasZombie(player)) return ZombieService.maxStaminaFactor(player);
        return vampire(player) ? Math.max((VampireService.progress(player).bloodLevel(player.level().getDifficulty().getId()) - 4) * 2, 1) : 1;
    }
    public static float staminaRegeneration(Player player) {
        if (PillarmanService.hasPillarman(player)) return PillarmanService.staminaFactor(player);
        if (ZombieService.hasZombie(player)) return ZombieService.staminaRegenFactor(player);
        if (HamonService.hasHamon(player)) return 1 + HamonService.state(player).breathingLevel() * .01F;
        return vampire(player) ? Math.max((VampireService.progress(player).bloodLevel(player.level().getDifficulty().getId()) - 4) * 4, 1) : 1;
    }
    public static boolean highLifeForce(Player player) {
        return PillarmanService.highLifeForce(player) || ZombieService.isHighSaturation(player)
                || vampire(player) && VampireService.progress(player).blood() >= VampireService.progress(player).maxBlood() * .8F;
    }
    public static float targetResolveMultiplier(Player player) {
        return PillarmanService.hasPillarman(player) ? PillarmanService.targetResolveMultiplier(player)
                : vampire(player) ? (float) Math.pow(2, Math.max(player.level().getDifficulty().getId() - 1, 0)) : 1;
    }
    private SpeciesHooks() { }
}
