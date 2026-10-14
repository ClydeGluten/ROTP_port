package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;

/** Server-owned adapter for the human Star Platinum progression slice. */
public final class ProgressionService {
    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> migrate(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!ownsStand(player) || !player.isAlive() || TimeStopService.isFrozen(player)) continue;
                ResolveProgress before = state(player);
                // Milk/administrator removal ends the original beneficial Resolve effect.
                ResolveProgress next = before.isBurst() && !player.hasEffect(StandItems.RESOLVE)
                        ? before.resetCombat(false) : before.tick(StandService.current(player) != null, player.getHealth() >= player.getMaxHealth());
                update(player, next);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && ownsStand(player)) update(player, state(player).resetCombat(true));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            ResolveProgress previous = state(oldPlayer);
            update(newPlayer, alive ? previous : previous.resetCombat(true));
        });
    }

    public static ResolveProgress state(ServerPlayer player) { return StandAttachments.resolve(player); }
    private static boolean ownsStand(ServerPlayer player) { return player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand(); }

    /** Prior drafts saved development but had no Resolve attachment. Preserve that earned value and ownership. */
    private static void migrate(ServerPlayer player) {
        if (ownsStand(player) && player.getAttached(StandAttachments.resolveType(player)) == null) {
            int level = Math.clamp(Math.round(player.getAttachedOrCreate(StandAttachments.PROGRESS).development() * 4), 0, 4);
            update(player, ResolveProgress.EMPTY.withLevel(level));
        }
    }

    public static void onGrant(ServerPlayer player) {
        ResolveProgress progress = state(player).resetCombat(false);
        update(player, progress);
        applyDevelopment(player, progress);
    }

    public static void onClear(ServerPlayer player) { update(player, state(player).resetCombat(false)); }

    public static boolean unlocked(ServerPlayer player, int level) { return state(player).unlocked(level, player.isCreative()); }
    public static boolean require(ServerPlayer player, int level, String ability) {
        if (unlocked(player, level)) return true;
        player.sendOverlayMessage(Component.translatable("jojo.fabric.progress.locked_action", Component.translatable(ability), level));
        return false;
    }

    public static void setLevel(ServerPlayer player, int level) {
        StandService.dismiss(player);
        update(player, state(player).withLevel(level));
        applyDevelopment(player, state(player));
    }

    public static void setTraining(ServerPlayer player, float points) { update(player, state(player).withTraining(points)); }
    public static void reset(ServerPlayer player) {
        StandService.dismiss(player);
        update(player, ResolveProgress.EMPTY);
        applyDevelopment(player, state(player));
    }

    private static void applyDevelopment(ServerPlayer player, ResolveProgress next) {
        StandProgress power = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (!power.hasStand()) return;
        StandProgress developed = new StandProgress(power.standId(), power.stamina(), next.development(), power.hadStand());
        player.setAttached(StandAttachments.PROGRESS, developed.withStamina(power.stamina()));
    }

    public static void update(ServerPlayer player, ResolveProgress next) {
        ResolveProgress previous = state(player);
        if (!previous.equals(next)) player.setAttached(StandAttachments.resolveType(player), next);
        if (next.level() != previous.level()) {
            applyDevelopment(player, next);
            if (next.level() > previous.level()) player.sendSystemMessage(Component.translatable("jojo.fabric.progress.level_up", next.level(),
                    Component.keybind("key.jojo.fabric.progress")));
        }
        if (next.isBurst() && !player.hasEffect(StandItems.RESOLVE)) {
            player.addEffect(new MobEffectInstance(StandItems.RESOLVE, next.burstTicks(), next.burstLevel(), false, false, true));
        } else if (!next.isBurst() && player.hasEffect(StandItems.RESOLVE)) player.removeEffect(StandItems.RESOLVE);
    }

    /** Called only from vanilla's accepted damage path, before armor, while the target is alive. */
    public static void onAttack(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0 || !target.isAlive() || !(source.getEntity() instanceof ServerPlayer player)
                || target == player || !ownsStand(player) || player.isSpectator()) return;
        LivingEntity resolvedTarget = target instanceof StarPlatinumEntity stand ? stand.owner() : target;
        if (resolvedTarget == null || resolvedTarget == player || !eligible(resolvedTarget)) return;
        boolean standDamage = source.getDirectEntity() instanceof StarPlatinumEntity;
        if (!standDamage && StandService.current(player) == null) return;
        float points = amount * (standDamage ? 1 : 0.5F);
        ResolveProgress current = state(player);
        if (resolvedTarget.hasEffect(StandItems.RESOLVE)) points *= Math.max(1 / (current.resolve() / current.maxResolve() + 0.2F), 1);
        update(player, current.attack(points, player.getHealth()));
    }

    private static boolean eligible(LivingEntity target) {
        return target instanceof Player || target.getType().getCategory() == MobCategory.MONSTER
                || target instanceof Mob mob && mob.isAggressive();
    }

    /** Runs after armor, magic and absorption, at the final health subtraction. */
    public static float incomingHealth(ServerPlayer player, DamageSource source, float proposedHealth) {
        if (!ownsStand(player) || proposedHealth >= player.getHealth()) return proposedHealth;
        float damage = player.getHealth() - proposedHealth;
        if (source.getEntity() != null && source.getEntity() != player) {
            update(player, state(player).attacked(damage, player.getHealth(), player.getMaxHealth()));
        }
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) return proposedHealth;
        return player.getHealth() - damage * (1 - state(player).damageReduction());
    }

    public static int timeStopTicks(ServerPlayer player) { return player.isCreative() ? 100 : state(player).timeStopTicks(); }
    public static void trainTimeStop(ServerPlayer player, int elapsedTicks) {
        if (ownsStand(player) && player.isAlive() && !player.isCreative()) {
            var old = state(player);
            float rate = player.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld() ? 0.1F : 0.25F;
            update(player, old.withTraining(Math.min(95, old.trainingPoints() + Math.max(0, elapsedTicks) * rate * (old.isBurst() ? 4 : 1))));
        }
    }
    private ProgressionService() { }
}
