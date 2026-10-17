package com.github.standobyte.jojo.fabric;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Server-owned port of the original Stand user walk-speed penalty ({@code StandEntityAction.getUserWalkSpeed}).
 * The original slowed the owner's input on the client only; here the server holds one transient
 * {@link Attributes#MOVEMENT_SPEED} modifier that vanilla synchronizes, so the client must never scale input itself.
 * Only that single modifier (identified by {@link #MODIFIER_ID}) is ever touched; other mods' and vanilla modifiers,
 * the flying speed and the global tick rate are left alone. Server thread only.
 */
public final class StandMovement {
    private static final java.util.Map<java.util.UUID, java.util.Map<Identifier, Float>> ACTION_FACTORS = new java.util.HashMap<>();
    public static void actionFactor(ServerPlayer player, Identifier action, float factor) {
        if (!Float.isFinite(factor) || factor < 0 || factor > 1) throw new IllegalArgumentException("Invalid action movement factor");
        ACTION_FACTORS.computeIfAbsent(player.getUUID(), ignored -> new java.util.HashMap<>()).put(action, factor);
        update(player);
    }
    public static void releaseAction(ServerPlayer player, Identifier action) {
        var factors = ACTION_FACTORS.get(player.getUUID());
        if (factors != null) { factors.remove(action); if (factors.isEmpty()) ACTION_FACTORS.remove(player.getUUID()); }
        update(player);
    }
    public static void releaseAllActions(ServerPlayer player) { ACTION_FACTORS.remove(player.getUUID()); clear(player); }
    public static void clearRuntime() { ACTION_FACTORS.clear(); }
    /** {@code StandEntityAction.Builder.userWalkSpeed}: default for actions without an explicit factor (heavy, finger, inhale, zoom). */
    public static final float ACTION_FACTOR = 0.5F;
    /** {@code StandEntityMeleeBarrage.Builder}: {@code standUserWalkSpeed(0.15F)}. */
    public static final float BARRAGE_FACTOR = 0.15F;
    /** {@code StandEntityBlock}: {@code standUserWalkSpeed(0.3F)}. */
    public static final float BLOCK_FACTOR = 0.3F;
    /** {@code StandEntityLightAttack.Builder}: {@code standUserWalkSpeed(1.0F)}, i.e. no penalty. */
    public static final float LIGHT_FACTOR = 1.0F;
    /** Star Platinum's time stop is built with {@code heldWalkSpeed(0)} while the player holds the charge. */
    public static final float TIME_STOP_CHARGE_FACTOR = 0.0F;

    public static final Identifier MODIFIER_ID = RipplesFabric.id("stand_combat_movement");

    /** Multiplier currently wanted for the player's Stand, 1 meaning no penalty. */
    public static float desiredFactor(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || TimeStopService.isFrozen(player)) return 1F;
        float factor = 1F;
        StandManifestation stand = StandService.currentManifestation(player);
        if (stand != null) factor = stand.userWalkSpeed();
        for (float requested : ACTION_FACTORS.getOrDefault(player.getUUID(), java.util.Map.of()).values()) factor = Math.min(factor, requested);
        if (TimeStopService.isCharging(player)) factor = Math.min(factor, TIME_STOP_CHARGE_FACTOR);
        return Math.max(0F, Math.min(1F, factor));
    }

    /** Reconciles the player's modifier with the active Stand action; idempotent and cheap when nothing changed. */
    public static void update(ServerPlayer player) {
        float factor = desiredFactor(player);
        if (factor >= 1F) { clear(player); return; }
        player.setSprinting(false);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double amount = factor - 1.0;
        AttributeModifier existing = speed.getModifier(MODIFIER_ID);
        if (existing != null && existing.amount() == amount) return;
        // ADD_MULTIPLIED_TOTAL multiplies the final value (so it composes with sprinting and other modifiers) by 1 + amount.
        speed.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Removes only this mod's penalty. */
    public static void clear(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(MODIFIER_ID)) speed.removeModifier(MODIFIER_ID);
    }

    private StandMovement() { }
}
