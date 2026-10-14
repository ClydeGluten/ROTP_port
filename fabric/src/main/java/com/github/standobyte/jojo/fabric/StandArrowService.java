package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;

/** Original default Arrow/Stand Virus rules, with the migrated Star Platinum as the available pool. */
public final class StandArrowService {
    private static final ResourceKey<DamageType> VIRUS_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RipplesFabric.id("stand_virus"));

    public static void initialize() {
        StandAssignments.initialize();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) tick(player);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) cancel(player);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            var previous = oldPlayer.getAttachedOrCreate(StandAttachments.ARROW);
            newPlayer.setAttached(StandAttachments.ARROW, alive ? previous : previous.cancel());
        });
    }

    public static boolean pierce(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || TimeStopService.isFrozen(player)) return false;
        var progress = player.getAttachedOrCreate(StandAttachments.ARROW);
        if (progress.infected() || player.hasEffect(StandItems.STAND_VIRUS)) {
            player.sendOverlayMessage(Component.translatable("jojo.fabric.arrow.infected"));
            return false;
        }
        if (!player.getAttachedOrCreate(StandAttachments.PROGRESS).standId().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("jojo.chat.message.already_have_stand"));
            return false;
        }
        if (player.isCreative()) complete(player, progress);
        else {
            var infection = progress.begin();
            player.setAttached(StandAttachments.ARROW, infection);
            player.addEffect(new MobEffectInstance(StandItems.STAND_VIRUS, infection.remainingTicks(), 3, false, false, true));
            player.sendSystemMessage(Component.translatable("jojo.fabric.arrow.started", infection.requiredLevels()));
        }
        player.hurtServer(player.level(), player.damageSources().playerAttack(player), Math.min(1F, Math.max(player.getHealth() - 1, 0)));
        return true;
    }

    private static void tick(ServerPlayer player) {
        var progress = player.getAttachedOrCreate(StandAttachments.ARROW);
        if (!player.isAlive()) { if (progress.infected() || progress.healing()) cancel(player); return; }
        if (TimeStopService.isFrozen(player)) return;
        if (progress.infected()) {
            if (!player.getAttachedOrCreate(StandAttachments.PROGRESS).standId().isEmpty()) { cancel(player); return; }
            // External effect removal cancels the attempt; milk preserves this uncurable effect through its consume hook.
            if (!player.hasEffect(StandItems.STAND_VIRUS) && progress.remainingTicks() > 1) { cancel(player); return; }
            int paid = progress.paidLevels();
            if (progress.remainingTicks() % 10 == 0) {
                boolean hasLevel = player.isCreative() || player.experienceLevel > 0;
                if (hasLevel) paid++;
                player.giveExperienceLevels(-1);
                float damage = hasLevel ? 0.75F : 7.5F;
                if (hasLevel && damage > player.getHealth()) damage = 0.001F;
                player.hurtServer(player.level(), new DamageSource(player.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(VIRUS_DAMAGE)), damage);
                if (!player.isAlive()) { cancel(player); return; }
            }
            if (paid >= progress.requiredLevels() || progress.remainingTicks() <= 1) complete(player, progress);
            else player.setAttached(StandAttachments.ARROW, new ArrowProgress(progress.acquired(), progress.remainingTicks() - 1,
                    paid, progress.requiredLevels(), false));
        } else if (progress.healing()) {
            if (player.getHealth() < player.getMaxHealth()) player.heal(0.25F);
            else player.setAttached(StandAttachments.ARROW, progress.cancel());
        }
    }

    private static void complete(ServerPlayer player, ArrowProgress progress) {
        String standId = StandAssignments.allocateStand(player);
        StandService.grant(player, standId);
        player.setAttached(StandAttachments.ARROW, progress.acquiredStand());
        player.sendSystemMessage(Component.translatable("jojo.fabric.arrow.awakened", Component.keybind("key.jojo.fabric.summon")));
    }

    public static void cancel(ServerPlayer player) {
        var progress = player.getAttachedOrCreate(StandAttachments.ARROW);
        if (progress.infected() || progress.healing()) player.setAttached(StandAttachments.ARROW, progress.cancel());
        player.removeEffect(StandItems.STAND_VIRUS);
    }
    private StandArrowService() { }
}
