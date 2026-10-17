package com.github.standobyte.jojo.core;

/** ZombieData's persistent disguise and NonStandPower's blood; Stand ownership is independent. */
public record ZombieProgress(boolean zombie, float blood, float maxBlood, boolean disguised) {
    public static final ZombieProgress EMPTY = new ZombieProgress(false, 0, 1000, false);
    public ZombieProgress {
        if (!Float.isFinite(blood) || !Float.isFinite(maxBlood) || maxBlood <= 0 || blood < 0 || blood > maxBlood
                || !zombie && (blood != 0 || disguised)) throw new IllegalArgumentException("Invalid zombie state");
    }
    public static ZombieProgress grant(float maximum) { return new ZombieProgress(true, Math.min(1000, maximum), maximum, false); }
    public ZombieProgress withBlood(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Blood must be finite");
        return zombie ? new ZombieProgress(true, Math.clamp(value, 0, maxBlood), maxBlood, disguised) : this;
    }
    public ZombieProgress withMaximum(float maximum) { return new ZombieProgress(zombie, Math.min(blood, maximum), maximum, disguised); }
    public ZombieProgress withDisguise(boolean value) { return zombie ? new ZombieProgress(true, blood, maxBlood, value) : this; }
    public int bloodLevel(int difficulty) { return !zombie || difficulty == 0 ? -1 : Math.min((int) (blood / maxBlood * 7.5F), 4) + Math.clamp(difficulty, 0, 3); }
    public int maxStaminaFactor(int difficulty) { return Math.max((bloodLevel(difficulty) - 4) * 2, 1); }
    public int staminaRegenFactor(int difficulty) { return Math.max((bloodLevel(difficulty) - 4) * 4, 1); }
    public float leapStrength(int difficulty) { return Math.max(bloodLevel(difficulty), 0) * .2F; }
}
