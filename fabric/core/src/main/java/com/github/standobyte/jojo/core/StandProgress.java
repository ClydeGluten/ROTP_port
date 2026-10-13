package com.github.standobyte.jojo.core;

import java.util.Objects;

/** Persistent player state. Summoned entities and attack timers are deliberately transient. */
public record StandProgress(String standId, float stamina, float development, boolean hadStand) {
    public static final String STAR_PLATINUM = "jojo:star_platinum";
    public static final StandProgress EMPTY = new StandProgress("", 0, 0, false);

    public StandProgress {
        Objects.requireNonNull(standId, "standId");
        if (!Float.isFinite(stamina) || stamina < 0) throw new IllegalArgumentException("Invalid stamina");
        if (!Float.isFinite(development) || development < 0 || development > 1) {
            throw new IllegalArgumentException("Development must be between zero and one");
        }
    }

    public boolean hasStarPlatinum() { return STAR_PLATINUM.equals(standId); }
    public float maxStamina() { return hasStarPlatinum() ? StandStats.STAR_PLATINUM.maxStamina(development) : 0; }

    public static StandProgress grantStarPlatinum(boolean creative) {
        return new StandProgress(STAR_PLATINUM, creative ? StandStats.STAR_PLATINUM.maxStamina(0) : 0, 0, true);
    }

    public StandProgress withStamina(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Stamina must be finite");
        return new StandProgress(standId, Math.clamp(value, 0, maxStamina()), development, hadStand);
    }

    public StandProgress regenerate(boolean summoned, boolean actionAllowsRegen, int foodLevel, boolean creative) {
        if (!hasStarPlatinum()) return this;
        if (creative) return withStamina(maxStamina());
        float gain = summoned ? (actionAllowsRegen ? 1.5F : 0) : 3F;
        if (foodLevel > 17) gain *= 1.25F;
        gain *= StandStatFormulas.getStaminaMultiplier(StandStats.STAR_PLATINUM.durability(development));
        float next = Math.clamp(stamina + gain, 0, maxStamina());
        return next == stamina ? this : withStamina(next);
    }
}
