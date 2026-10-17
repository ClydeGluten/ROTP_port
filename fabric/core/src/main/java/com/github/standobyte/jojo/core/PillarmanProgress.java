package com.github.standobyte.jojo.core;

/** Original Pillar Man state. Runtime actions, blades and swallowed TNT are deliberately not saved. */
public record PillarmanProgress(boolean pillarman, int stage, Mode mode, boolean stoneForm, float energy, float maximum) {
    public enum Mode { NONE, WIND, HEAT, LIGHT }
    public static final PillarmanProgress EMPTY = new PillarmanProgress(false, 1, Mode.NONE, false, 0, 1000);
    public PillarmanProgress {
        if (stage < 1 || stage > 4 || mode == null || !Float.isFinite(energy) || !Float.isFinite(maximum)
                || maximum <= 0 || energy < 0 || energy > maximum || !pillarman && (stoneForm || energy != 0))
            throw new IllegalArgumentException("Invalid Pillar Man state");
    }
    public static PillarmanProgress acquired(float maximum) { return new PillarmanProgress(true, 1, Mode.NONE, false, Math.min(1000, maximum), maximum); }
    public PillarmanProgress withEnergy(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite Pillar energy");
        return pillarman ? new PillarmanProgress(true, stage, mode, stoneForm, Math.clamp(value, 0, maximum), maximum) : this;
    }
    public PillarmanProgress withMaximum(float value) { return new PillarmanProgress(pillarman, stage, mode, stoneForm, Math.min(energy, value), value); }
    public PillarmanProgress withStage(int value, float maximum) { return new PillarmanProgress(pillarman, value, mode, stoneForm, Math.min(energy, maximum), maximum); }
    public PillarmanProgress withMode(Mode value) { return new PillarmanProgress(pillarman, stage, value, stoneForm, energy, maximum); }
    public PillarmanProgress withStoneForm(boolean value) { return new PillarmanProgress(pillarman, stage, mode, value, energy, maximum); }
    public PillarmanProgress afterDeath() { return withStoneForm(false); }
    public int attributeLevel() { return stage == 3 ? 5 : stage == 4 ? 6 : 2 * stage; }
    public int regenerationAmplifier() { return stage == 1 || energy / maximum < .1F ? -1 : energy / maximum >= .3F ? 1 : 0; }
    public float leapStrength() { return (2 + Math.min(stage, 2.25F) / 2) * .6F; }
}
