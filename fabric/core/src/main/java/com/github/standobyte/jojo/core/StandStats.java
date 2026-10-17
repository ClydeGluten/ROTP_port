package com.github.standobyte.jojo.core;

/** Base and fully developed stats from upstream ModStandsInit, independent of Minecraft. */
public record StandStats(double power, double maxPower, double speed, double maxSpeed,
                         double durability, double maxDurability, double precision,
                         double maxPrecision, double effectiveRange, double maxRange,
                         double armor, double armorToughness) {
    public static final StandStats EMPTY = new StandStats(0, 0, 0, 0, 0, 0, 0, 0, 0);

    /** Compatibility constructor for the original SP/TW draft's fixed precision. */
    public StandStats(double power, double maxPower, double speed, double maxSpeed,
                      double durability, double maxDurability, double precision,
                      double effectiveRange, double maxRange) {
        this(power, maxPower, speed, maxSpeed, durability, maxDurability, precision, precision,
                effectiveRange, maxRange, 0, 0);
    }
    public static final StandStats STAR_PLATINUM = new StandStats(16, 18.5, 16, 19, 16, 20, 20, 2, 10);

    public static final StandStats THE_WORLD = new StandStats(16, 19, 16, 18.5, 16, 20, 12, 5, 10);
    public static final StandStats HIEROPHANT_GREEN = new StandStats(8, 9, 10, 12, 8, 10, 8, 10, 50, 100, 0, 0);
    public static final StandStats SILVER_CHARIOT = new StandStats(8, 9, 14, 17.5, 11, 12, 11.5, 16, 10, 10, 20, 12);
    public static final StandStats MAGICIANS_RED = new StandStats(11, 13, 11, 12, 12, 13, 8, 9, 5, 10, 0, 0);
    public static final StandStats CRAZY_DIAMOND = new StandStats(14, 17, 14, 16.5, 11, 13, 11, 12, 2, 4, 0, 0);
    public static final StandStats GOLD_EXPERIENCE = new StandStats(9, 10, 14, 15, 7, 7, 10, 2, 4);
    public static final StandStats MR_PRESIDENT = new StandStats(0, 0, 0, 0, 14, 14, 0, 0, 0);

    public double power(float development) { return power + (maxPower - power) * progress(development); }
    public double speed(float development) { return speed + (maxSpeed - speed) * progress(development); }
    public double durability(float development) { return durability + (maxDurability - durability) * progress(development); }
    public double precision(float development) { return precision + (maxPrecision - precision) * progress(development); }
    public float maxStamina(float development) {
        return 1000 * StandStatFormulas.getStaminaMultiplier(durability(development));
    }

    private static float progress(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Development must be finite");
        return Math.clamp(value, 0, 1);
    }
}
