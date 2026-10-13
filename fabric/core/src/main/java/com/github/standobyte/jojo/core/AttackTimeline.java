package com.github.standobyte.jojo.core;

/** Server-ticked action phases. A strike is emitted once when windup ends. */
public final class AttackTimeline {
    public enum Phase { IDLE, WINDUP, PERFORM, RECOVERY }
    private Phase phase = Phase.IDLE;
    private int ticks;
    private int perform;
    private int recovery;
    private boolean heavy;
    private int phaseDuration;

    public boolean start(boolean heavy, int windup, int perform, int recovery) {
        if (windup < 0 || perform < 1 || recovery < 0) throw new IllegalArgumentException("Invalid phase duration");
        if (phase != Phase.IDLE && !(phase == Phase.RECOVERY && !this.heavy)) return false;
        this.heavy = heavy;
        this.perform = perform;
        this.recovery = recovery;
        this.ticks = windup;
        this.phaseDuration = windup;
        phase = Phase.WINDUP;
        return true;
    }

    public boolean tick() {
        if (phase == Phase.IDLE) return false;
        if (ticks > 0) ticks--;
        if (ticks > 0) return false;
        switch (phase) {
            case WINDUP -> { phase = Phase.PERFORM; ticks = perform; phaseDuration = perform; return true; }
            case PERFORM -> { phase = recovery == 0 ? Phase.IDLE : Phase.RECOVERY; ticks = recovery; phaseDuration = recovery; }
            case RECOVERY -> phase = Phase.IDLE;
            default -> { }
        }
        return false;
    }

    public Phase phase() { return phase; }
    public boolean heavy() { return heavy; }
    public int ticksRemaining() { return ticks; }
    public int phaseDuration() { return phaseDuration; }
    public boolean canStart() { return phase == Phase.IDLE || (phase == Phase.RECOVERY && !heavy); }
    public void cancel() { phase = Phase.IDLE; ticks = 0; }
}
