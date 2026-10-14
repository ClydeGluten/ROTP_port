package com.github.standobyte.jojo.core;

/** Base and fully developed stats from upstream ModStandsInit, independent of Minecraft. */
public record StandStats(double power, double maxPower, double speed, double maxSpeed,
                         double durability, double maxDurability, double precision,
                         double effectiveRange, double maxRange) {
    public static final StandStats STAR_PLATINUM = new StandStats(16, 18.5, 16, 19, 16, 20, 20, 2, 10);

    public static final StandStats THE_WORLD = new StandStats(16, 19, 16, 18.5, 16, 20, 12, 5, 10);

    public double power(float development) { return power + (maxPower - power) * progress(development); }
    public double speed(float development) { return speed + (maxSpeed - speed) * progress(development); }
    public double durability(float development) { return durability + (maxDurability - durability) * progress(development); }
    public float maxStamina(float development) {
        return 1000 * StandStatFormulas.getStaminaMultiplier(durability(development));
    }

    private static float progress(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Development must be finite");
        return Math.clamp(value, 0, 1);
    }
}
