package com.github.standobyte.jojo.core;

/** Server-ticked held actions. Heartbeats extend the lease, never the action's maximum duration. */
public final class HeldCombat {
    public enum Mode { NONE, BARRAGE, GUARD }
    public static final int LEASE_TICKS = 12;
    public static final float BARRAGE_STAMINA_PER_TICK = 4;
    private Mode mode = Mode.NONE;
    private int elapsed;
    private int limit;
    private int lease;
    private int hitCredit;
    private int barrageRecovery;
    private int recovery;
    private int guardBreak;
    private int guardBreakDuration;

    public boolean start(Mode next, int maximumTicks, int recoveryTicks) {
        if (next == Mode.NONE || maximumTicks < 1 || recoveryTicks < 0) throw new IllegalArgumentException("Invalid held action");
        if (!canStart()) return false;
        mode = next;
        elapsed = 0;
        limit = maximumTicks;
        hitCredit = 0;
        barrageRecovery = recoveryTicks;
        refresh();
        return true;
    }

    public void refresh() { if (mode != Mode.NONE) lease = LEASE_TICKS; }

    /** Returns this tick's number of barrage swings using a deterministic fractional-rate accumulator. */
    public int tick(int hitsPerSecond) {
        if (hitsPerSecond < 0) throw new IllegalArgumentException("Negative hit rate");
        if (guardBreak > 0) { guardBreak--; return 0; }
        if (recovery > 0) { recovery--; return 0; }
        if (mode == Mode.NONE) return 0;
        if (lease == 0 || elapsed >= limit) { stop(); return 0; }
        lease--;
        elapsed++;
        if (mode != Mode.BARRAGE) return 0;
        hitCredit += hitsPerSecond;
        int hits = hitCredit / 20;
        hitCredit %= 20;
        return hits;
    }

    public void stop() {
        if (mode == Mode.BARRAGE) recovery = barrageRecovery;
        mode = Mode.NONE;
        lease = 0;
    }

    public void breakGuard(int duration) {
        if (duration < 1) throw new IllegalArgumentException("Invalid guard break duration");
        stop();
        guardBreak = guardBreakDuration = duration;
    }

    /** An original instant-time punch can replace another action's recovery, but never guard-break stun. */
    public boolean cancelRecovery() {
        if (mode != Mode.NONE || guardBreak > 0 || recovery <= 0) return false;
        recovery = 0;
        return true;
    }

    public Mode mode() { return mode; }
    public int elapsed() { return elapsed; }
    public int limit() { return limit; }
    public int guardBreakTicks() { return guardBreak; }
    public int guardBreakDuration() { return guardBreakDuration; }
    public int recoveryTicks() { return recovery; }
    public boolean canStart() { return mode == Mode.NONE && recovery == 0 && guardBreak == 0; }

    public record GuardResult(float damage, float stamina, boolean broken) { }

    /** Original block stamina and physical-resistance formulas, including a partial block on exhaustion. */
    public static GuardResult guard(float damage, float stamina, double durability, double power, boolean creative) {
        return guard(damage,stamina,durability,power,creative,1);
    }
    public static GuardResult guard(float damage,float stamina,double durability,double power,boolean creative,double resistanceMultiplier){
        if (!Float.isFinite(damage) || damage < 0 || !Float.isFinite(stamina) || stamina < 0) {
            throw new IllegalArgumentException("Invalid guard input");
        }
        float cost = StandStatFormulas.getBlockStaminaCost(damage);
        float ratio = creative || cost == 0 ? 1 : Math.min(stamina / cost, 1);
        float resistance = StandStatFormulas.getPhysicalResistance(durability, power, ratio, damage, resistanceMultiplier);
        return new GuardResult(damage * (1 - resistance), creative ? stamina : Math.max(0, stamina - cost), !creative && stamina < cost);
    }
}
