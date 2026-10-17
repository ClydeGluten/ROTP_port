package com.github.standobyte.jojo.core;

import java.util.Objects;

/** Persistent player state. Summoned entities and attack timers are deliberately transient. */
public record StandProgress(String standId, float stamina, float development, boolean hadStand,
        float staminaMultiplier, float regenerationMultiplier) {
    public static final String STAR_PLATINUM = "jojo:star_platinum";
    public static final String THE_WORLD = "jojo:the_world";
    public static final String HIEROPHANT_GREEN = "jojo:hierophant_green";
    public static final String SILVER_CHARIOT = "jojo:silver_chariot";
    public static final String MAGICIANS_RED = "jojo:magicians_red";
    public static final String CRAZY_DIAMOND = "jojo:crazy_diamond";
    public static final String GOLD_EXPERIENCE = "jojo:gold_experience";
    public static final String BOY_II_MAN = "jojo:boy_ii_man";
    public static final String MR_PRESIDENT = "jojo:mr_president";
    public static final StandProgress EMPTY = new StandProgress("", 0, 0, false);
    public StandProgress(String standId, float stamina, float development, boolean hadStand) {
        this(standId, stamina, development, hadStand, 1, 1);
    }

    public StandProgress {
        Objects.requireNonNull(standId, "standId");
        if (!Float.isFinite(staminaMultiplier) || staminaMultiplier < 1 || !Float.isFinite(regenerationMultiplier)
                || regenerationMultiplier < 1) throw new IllegalArgumentException("Invalid non-Stand stamina multiplier");
        if (!Float.isFinite(stamina) || stamina < 0) throw new IllegalArgumentException("Invalid stamina");
        if (!Float.isFinite(development) || development < 0 || development > 1) {
            throw new IllegalArgumentException("Development must be between zero and one");
        }
    }

    public boolean hasStarPlatinum() { return STAR_PLATINUM.equals(standId); }
    public boolean hasTheWorld() { return THE_WORLD.equals(standId); }
    public boolean hasStand() { return StandDefinitions.find(standId).isPresent(); }
    public StandStats stats() { return StandDefinitions.find(standId).map(StandDefinitions.Definition::stats).orElse(StandStats.EMPTY); }
    public boolean canStopTime() { return StandDefinitions.find(standId).map(StandDefinitions.Definition::canStopTime).orElse(false); }
    public int timeStopUnlock() { return StandDefinitions.find(standId).filter(StandDefinitions.Definition::canStopTime)
            .map(StandDefinitions.Definition::timeStopUnlock).orElse(Integer.MAX_VALUE); }
    public float maxStamina() { return hasStand() ? stats().maxStamina(development) * staminaMultiplier : 0; }
    public StandProgress withMultipliers(float maximum, float regeneration) {
        if (maximum == staminaMultiplier && regeneration == regenerationMultiplier) return this;
        var next = new StandProgress(standId, stamina, development, hadStand, maximum, regeneration);
        return next.withStamina(stamina);
    }

    public static StandProgress grant(String standId, boolean creative) {
        var empty = new StandProgress(standId, 0, 0, true);
        if (!StandDefinitions.find(standId).map(StandDefinitions.Definition::playable).orElse(false))
            throw new IllegalArgumentException("Unknown playable Stand: " + standId);
        return creative ? empty.withStamina(empty.maxStamina()) : empty;
    }

    public static StandProgress grantStarPlatinum(boolean creative) {
        return new StandProgress(STAR_PLATINUM, creative ? StandStats.STAR_PLATINUM.maxStamina(0) : 0, 0, true);
    }

    public StandProgress withStamina(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Stamina must be finite");
        return new StandProgress(standId, Math.clamp(value, 0, maxStamina()), development, hadStand, staminaMultiplier, regenerationMultiplier);
    }

    public StandProgress regenerate(boolean summoned, boolean actionAllowsRegen, int foodLevel, boolean creative) {
        if (!hasStand()) return this;
        if (creative) return withStamina(maxStamina());
        float gain = summoned ? (actionAllowsRegen ? 1.5F : 0) : 3F;
        if (foodLevel > 17) gain *= 1.25F;
        gain *= StandStatFormulas.getStaminaMultiplier(stats().durability(development)) * regenerationMultiplier;
        float next = Math.clamp(stamina + gain, 0, maxStamina());
        return next == stamina ? this : withStamina(next);
    }
}
