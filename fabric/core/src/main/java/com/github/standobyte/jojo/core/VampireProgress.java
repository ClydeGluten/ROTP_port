package com.github.standobyte.jojo.core;

/** Original vampire blood and full/weak transformation state, independent of Stand ownership. */
public record VampireProgress(boolean vampire, boolean fullPower, float blood, float maxBlood) {
    public static final float MAX_BLOOD = 1000;
    public static final VampireProgress EMPTY = new VampireProgress(false, false, 0);
    private static final float[] DECAY = {0.13889F, 0.00278F, 0.00278F, 0};
    private static final float[] DRAIN = {0, 1, 1.75F, 2.5F};
    private static final float[] HEAL_COST = {10, 4, 2, 1};
    public VampireProgress(boolean vampire, boolean fullPower, float blood) { this(vampire, fullPower, blood, MAX_BLOOD); }
    public VampireProgress {
        if (!Float.isFinite(blood) || blood < 0 || blood > maxBlood || !Float.isFinite(maxBlood) || maxBlood <= 0 || !vampire && (fullPower || blood != 0))
            throw new IllegalArgumentException("Invalid vampire state");
    }
    public static VampireProgress transform(boolean full) { return new VampireProgress(true, full, 300); }
    public VampireProgress withBlood(float amount) {
        if (!Float.isFinite(amount)) throw new IllegalArgumentException("Blood must be finite");
        return vampire ? new VampireProgress(true, fullPower, Math.clamp(amount, 0, maxBlood), maxBlood) : this;
    }
    public VampireProgress withMaximum(float maximum) { return new VampireProgress(vampire, fullPower, Math.min(blood, maximum), maximum); }
    public VampireProgress withFullPower() { return vampire ? new VampireProgress(true, true, blood, maxBlood) : transform(true); }
    public VampireProgress tick(int difficulty, boolean creative) { return creative ? this : withBlood(blood - DECAY[Math.clamp(difficulty, 0, 3)]); }
    public VampireProgress afterDeath() { return fullPower ? this : EMPTY; }
    public int bloodLevel(int difficulty) {
        if (!vampire || difficulty == 0) return -1;
        int level = Math.min((int) (blood / maxBlood * 5), 4) + difficulty;
        return fullPower ? level : Math.max(level - 2, 1);
    }
    public static float drainGain(int difficulty) { return DRAIN[Math.clamp(difficulty, 0, 3)]; }
    public static float healCost(int difficulty) { return HEAL_COST[Math.clamp(difficulty, 0, 3)]; }
}
