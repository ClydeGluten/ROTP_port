package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** GameplayEventHandler's tracked potion packets, including visuals on other players and mobs. */
public final class TrackedEffects {
    private static final Set<Identifier> TRACKED = Stream.of("resolve", "spirit_vision", "integrated_stand", "time_stop", "immobilize", "stun", "hamon_shock",
            "hypnosis", "hamon_spread", "full_invisibility", "sun_burn", "stand_virus", "freeze", "bleeding")
            .map(RipplesFabric::id).collect(Collectors.toUnmodifiableSet());
    public static boolean tracked(Holder<MobEffect> effect) { return TRACKED.contains(BuiltInRegistries.MOB_EFFECT.getKey(effect.value())); }
    public static void initialize() {
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof LivingEntity living) for (var effect : living.getActiveEffects()) {
                if (tracked(effect.getEffect())) player.connection.send(new ClientboundUpdateMobEffectPacket(entity.getId(), effect, false));
            }
        });
    }
    /** Vanilla sends effects to their owning player and passengers; the original adds all entity trackers. */
    public static void updated(LivingEntity entity, MobEffectInstance effect) {
        if (!(entity.level() instanceof ServerLevel) || !tracked(effect.getEffect())) return;
        var packet = new ClientboundUpdateMobEffectPacket(entity.getId(), effect, false);
        for (var player : PlayerLookup.tracking(entity)) player.connection.send(packet);
    }
    public static void removed(LivingEntity entity, MobEffectInstance effect) {
        if (!(entity.level() instanceof ServerLevel) || !tracked(effect.getEffect())) return;
        var packet = new ClientboundRemoveMobEffectPacket(entity.getId(), effect.getEffect());
        for (var player : PlayerLookup.tracking(entity)) player.connection.send(packet);
    }
    private TrackedEffects() { }
}
