package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StandManifestation;
import com.github.standobyte.jojo.fabric.VampireBlood;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.gamerules.GameRules;

/** Shared CrazyDiamondHeal active methods used by Previous State and plan 45's healing action. */
public final class CrazyDiamondHealing {
    public static final class Session { private int bleedingTimer = -1; }
    public interface HasHealth {
        float restorationHealth();
        float restorationMaxHealth();
        void setRestorationHealth(float health);
    }

    public static boolean validTarget(Entity target, LivingEntity user) {
        return target != user && (target instanceof LivingEntity || target instanceof HasHealth || target instanceof AbstractBoat);
    }

    public static boolean heal(Entity target, double speed, Session session) {
        if (!(target.level() instanceof ServerLevel)) return false;
        if (target instanceof LivingEntity living) return healLivingEntity(living, speed, session);
        if (target instanceof HasHealth health && health.restorationHealth() < health.restorationMaxHealth()) {
            health.setRestorationHealth(health.restorationHealth() + health.restorationMaxHealth() / 40 * (float) speed);
            return true;
        }
        if (target instanceof AbstractBoat boat && boat.getDamage() > 0) {
            boat.setDamage(Math.max(boat.getDamage() - (float) speed, 0));
            return true;
        }
        return false;
    }

    public static boolean healLivingEntity(LivingEntity entity, double speed, Session session) {
        if (!(entity.level() instanceof ServerLevel)) return false;
        LivingEntity user = entity instanceof StandManifestation stand && stand.owner() != null ? stand.owner() : entity;
        if (entity.deathTime > 0) {
            if (entity.deathTime > 15) return false;
            user.deathTime = Math.max(user.deathTime - 2, 0);
            entity.deathTime = user.deathTime;
            if (user.deathTime <= 0 && user.getHealth() <= 0) {
                user.setHealth(.001F);
                resurrect(user);
            }
            return true;
        }
        float healingSpeed = (float) speed;
        boolean healed = user.getHealth() < user.getMaxHealth() || user.hasEffect(VampireBlood.BLEEDING);
        if (user.getHealth() < user.getMaxHealth()) user.setHealth(user.getHealth() + .5F * healingSpeed);
        int interval = (int) (20 / healingSpeed);
        if (session.bleedingTimer < 0) session.bleedingTimer = interval / 2;
        if (session.bleedingTimer++ >= interval) {
            session.bleedingTimer = 0;
            var bleeding = user.getEffect(VampireBlood.BLEEDING);
            if (bleeding != null) {
                user.removeEffect(VampireBlood.BLEEDING);
                if (bleeding.getAmplifier() > 0) user.addEffect(new MobEffectInstance(VampireBlood.BLEEDING,
                        bleeding.getDuration(), bleeding.getAmplifier() - 1, bleeding.isAmbient(), bleeding.isVisible(), bleeding.showIcon()));
            }
        }
        return healed;
    }

    private static void resurrect(LivingEntity entity) {
        entity.deathTime = 0;
        var message = new ResetDeath(entity.getId());
        for (var player : PlayerLookup.tracking(entity)) if (ServerPlayNetworking.canSend(player, ResetDeath.TYPE)) ServerPlayNetworking.send(player, message);
        if (entity instanceof ServerPlayer player) {
            if (ServerPlayNetworking.canSend(player, ResetDeath.TYPE)) ServerPlayNetworking.send(player, message);
            if (!player.level().getGameRules().get(GameRules.KEEP_INVENTORY) && !player.isSpectator()) {
                player.setExperienceLevels(0);
                player.setExperiencePoints(0);
            }
        }
    }

    public record ResetDeath(int entityId) implements CustomPacketPayload {
        public static final Type<ResetDeath> TYPE = new Type<>(RipplesFabric.id("cd_reset_death"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetDeath> CODEC = StreamCodec.of(
                (buffer, packet) -> buffer.writeVarInt(packet.entityId), buffer -> new ResetDeath(buffer.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        /** Client integration is registered by plan 46; avoids the health/death packet ordering race. */
        public void apply(Entity entity) {
            if (entity instanceof LivingEntity living) {
                if (living.isDeadOrDying()) living.setHealth(.0001F);
                living.deathTime = 0;
            }
        }
    }

    private static boolean initialized;
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.clientboundPlay().register(ResetDeath.TYPE, ResetDeath.CODEC);
    }
    private CrazyDiamondHealing() { }
}
