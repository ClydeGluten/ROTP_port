package com.github.standobyte.jojo.core;

import java.util.Objects;

/** Persistent player state. Summoned entities and attack timers are deliberately transient. */
public record StandProgress(String standId, float stamina, float development, boolean hadStand) {
    public static final String STAR_PLATINUM = "jojo:star_platinum";
    public static final String THE_WORLD = "jojo:the_world";
    public static final StandProgress EMPTY = new StandProgress("", 0, 0, false);

    public StandProgress {
        Objects.requireNonNull(standId, "standId");
        if (!Float.isFinite(stamina) || stamina < 0) throw new IllegalArgumentException("Invalid stamina");
        if (!Float.isFinite(development) || development < 0 || development > 1) {
            throw new IllegalArgumentException("Development must be between zero and one");
        }
    }

    public boolean hasStarPlatinum() { return STAR_PLATINUM.equals(standId); }
    public boolean hasTheWorld() { return THE_WORLD.equals(standId); }
    public boolean hasStand() { return hasStarPlatinum() || hasTheWorld(); }
    public StandStats stats() { return hasTheWorld() ? StandStats.THE_WORLD : StandStats.STAR_PLATINUM; }
    public int timeStopUnlock() { return hasTheWorld() ? 2 : 4; }
    public float maxStamina() { return hasStand() ? stats().maxStamina(development) : 0; }

    public static StandProgress grant(String standId, boolean creative) {
        var empty = new StandProgress(standId, 0, 0, true);
        if (!empty.hasStand()) throw new IllegalArgumentException("Unknown playable Stand: " + standId);
        return creative ? empty.withStamina(empty.maxStamina()) : empty;
    }

    public static StandProgress grantStarPlatinum(boolean creative) {
        return new StandProgress(STAR_PLATINUM, creative ? StandStats.STAR_PLATINUM.maxStamina(0) : 0, 0, true);
    }

    public StandProgress withStamina(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Stamina must be finite");
        return new StandProgress(standId, Math.clamp(value, 0, maxStamina()), development, hadStand);
    }

    public StandProgress regenerate(boolean summoned, boolean actionAllowsRegen, int foodLevel, boolean creative) {
        if (!hasStand()) return this;
        if (creative) return withStamina(maxStamina());
        float gain = summoned ? (actionAllowsRegen ? 1.5F : 0) : 3F;
        if (foodLevel > 17) gain *= 1.25F;
        gain *= StandStatFormulas.getStaminaMultiplier(stats().durability(development));
        float next = Math.clamp(stamina + gain, 0, maxStamina());
        return next == stamina ? this : withStamina(next);
    }
}
