package com.github.standobyte.jojo.core;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Original Stand Resolve rules, including vampire bonuses, independent of the Minecraft runtime. */
public record ResolveProgress(int level, float resolve, int noDecayTicks, float attackBoost,
        float healthRecord, List<Float> records, boolean saveNextRecord, int burstTicks,
        int burstLevel, float trainingPoints) {
    private static volatile float[] THRESHOLDS = {2500, 10000, 25000, 50000, 32500};
    private static final int[] BURST_MIN = {300, 400, 500, 600, 600};
    private static final int[] BURST_MAX = {600, 1200, 1500, 1800, 2400};
    public static final ResolveProgress EMPTY = new ResolveProgress(0, 0, 0, 1, -1, List.of(), true, 0, 0, 0);

    public ResolveProgress {
        records = List.copyOf(records);
        if (level < 0 || level > 4 || !Float.isFinite(resolve) || resolve < 0 || resolve > threshold(level)
                || noDecayTicks < 0 || noDecayTicks > 400 || !Float.isFinite(attackBoost) || attackBoost < 1 || attackBoost > 5
                || !Float.isFinite(healthRecord) || healthRecord < -1 || healthRecord > Float.MAX_VALUE
                || records.size() > 10 || records.stream().anyMatch(v -> !Float.isFinite(v) || v <= 0)
                || burstLevel < 0 || burstLevel > 4 || burstTicks < 0 || burstTicks > BURST_MAX[burstLevel]
                || !Float.isFinite(trainingPoints) || trainingPoints < 0) {
            throw new IllegalArgumentException("Invalid Resolve progress");
        }
    }

    /** Source GeneralUtil.getOrLast: threshold count does not change a Stand type's maximum level. */
    private static float threshold(int level) { var values=THRESHOLDS; return values[Math.min(level,values.length-1)]; }
    public static void configureThresholds(List<Float> values) {
        if(values.isEmpty()||values.size()>1024||values.stream().anyMatch(v->v==null||!Float.isFinite(v)||v<=0))throw new IllegalArgumentException("Invalid Resolve thresholds");
        float[] next=new float[values.size()];for(int i=0;i<next.length;i++)next[i]=values.get(i);THRESHOLDS=next;
    }
    /** Saved points can exceed a threshold lowered by an operator; earned records and training remain intact. */
    public static ResolveProgress loaded(int level,float value,int delay,float attack,float health,List<Float> records,
            boolean saveRecord,int burstTicks,int burstLevel,float training) {
        if(level<0||level>4||!Float.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid saved Resolve progress");
        return new ResolveProgress(level,Math.min(value,threshold(level)),delay,attack,health,records,saveRecord,burstTicks,burstLevel,training);
    }
    public ResolveProgress normalized() { return loaded(level,resolve,noDecayTicks,attackBoost,healthRecord,records,saveNextRecord,burstTicks,burstLevel,trainingPoints); }
    public float maxResolve() { return threshold(level); }
    public float development() { return level / 4F; }
    public boolean isBurst() { return burstTicks > 0; }
    public int burstRemainingTicks() { return isBurst() ? Math.min(burstTicks, (int) Math.ceil(resolve * BURST_MIN[burstLevel] / maxResolve())) : 0; }
    public int timeStopTicks() { return 5 + (int) Math.floor(Math.min(trainingPoints, 175)); }
    public float damageReduction() { return (isBurst() ? 1 : resolve / maxResolve()) * 0.6667F; }
    public boolean unlocked(int requiredLevel, boolean creative) { return creative || level >= requiredLevel; }

    public ResolveProgress withLevel(int newLevel) {
        return new ResolveProgress(newLevel, 0, 0, 1, -1, List.of(), true, 0, 0, trainingPoints);
    }

    public ResolveProgress withTraining(float points) {
        return new ResolveProgress(level, resolve, noDecayTicks, attackBoost, healthRecord, records,
                saveNextRecord, burstTicks, burstLevel, points);
    }

    public ResolveProgress trainTimeStop(int elapsedTicks) {
        if (elapsedTicks <= 0 || level < 4) return this;
        return trainTimeStop(elapsedTicks, 0.25F, 95);
    }

    /** StandPower.addLearningProgressPoints only clamps values which were within the current cap. */
    public ResolveProgress trainTimeStop(int elapsedTicks, float rate, int currentMaximum) {
        if (elapsedTicks <= 0) return this;
        float points = trainingPoints + elapsedTicks * rate * (isBurst() ? 4 : 1);
        if (trainingPoints <= currentMaximum) points = Math.min(points, currentMaximum);
        return withTraining(points);
    }

    /** Target eligibility and Stand-versus-user damage weighting are checked by the server adapter. */
    public ResolveProgress attack(float damage, float health) {
        return attack(damage, health, false);
    }
    public ResolveProgress attack(float damage, float health, boolean vampire) {
        if (!Float.isFinite(damage) || damage <= 0) return this;
        ResolveProgress gained = gain(damage, health, vampire);
        return new ResolveProgress(gained.level, gained.resolve, gained.noDecayTicks,
                Math.min(5, attackBoost + damage * 0.05F), gained.healthRecord, gained.records,
                gained.saveNextRecord, gained.burstTicks, gained.burstLevel, gained.trainingPoints);
    }

    /** Called before applying incoming health damage from another entity. */
    public ResolveProgress attacked(float damage, float health, float maxHealth) {
        return attacked(damage, health, maxHealth, false);
    }
    public ResolveProgress attacked(float damage, float health, float maxHealth, boolean vampire) {
        if (!Float.isFinite(damage) || damage <= 0) return this;
        float hp = Math.max(health - damage, 0);
        if (healthRecord >= 0) hp = Math.min(hp, healthRecord);
        ResolveProgress next = new ResolveProgress(level, resolve, noDecayTicks, attackBoost, hp, records,
                saveNextRecord, burstTicks, burstLevel, trainingPoints);
        return damage >= maxHealth * 0.4F ? next.gain(damage * 0.1F, health, vampire) : next;
    }

    private ResolveProgress gain(float points, float health, boolean vampire) {
        float hp = healthRecord >= 0 ? Math.min(health, healthRecord) : health;
        float hpBoost = vampire ? 5 : 1 + (15 - Math.clamp(hp, 5, 15)) * 0.9F;
        float base = points * attackBoost * hpBoost;
        // Preserve the original multiset formula, including its 1 + count multiplier per distinct record.
        var counts = new TreeMap<Float, Integer>();
        for (float record : records) if (record > resolve) counts.merge(record, 1, Integer::sum);
        float boosted = base;
        for (var entry : counts.entrySet()) boosted += Math.min(base, entry.getKey() - resolve) * (1 + entry.getValue());
        float value = Math.min(maxResolve(), resolve + boosted);
        if (!isBurst() && value >= maxResolve()) {
            int nextLevel = Math.min(4, level + 1);
            return new ResolveProgress(nextLevel, threshold(nextLevel), 0, attackBoost, healthRecord,
                    records, saveNextRecord, BURST_MAX[level], level, trainingPoints);
        }
        if (isBurst()) value = Math.max(maxResolve() * 0.5F, value);
        return new ResolveProgress(level, value, isBurst() ? 0 : 400, attackBoost, healthRecord,
                records, saveNextRecord, burstTicks, burstLevel, trainingPoints);
    }

    public ResolveProgress tick(boolean summoned, boolean fullHealth) {
        if (isBurst()) {
            float next = Math.max(0, resolve - maxResolve() / BURST_MIN[burstLevel]);
            if (next == 0 || burstTicks == 1) return resetCombat(false);
            return new ResolveProgress(level, next, 0, attackBoost, healthRecord, records,
                    saveNextRecord, burstTicks - 1, burstLevel, trainingPoints);
        }
        if (noDecayTicks > 0) {
            int next = Math.max(0, noDecayTicks - (summoned ? 1 : 2));
            List<Float> nextRecords = records;
            if (next == 0 && resolve > 0) {
                var updated = new ArrayList<>(records);
                if (!saveNextRecord && !updated.isEmpty()) updated.remove(updated.indexOf(java.util.Collections.min(updated)));
                nextRecords = addRecord(updated, resolve);
            }
            return new ResolveProgress(level, resolve, next, attackBoost, healthRecord, nextRecords,
                    next > 0 && saveNextRecord, 0, burstLevel, trainingPoints);
        }
        float next = Math.max(0, resolve - 2);
        float hp = resolve > 0 && next == 0 || fullHealth ? -1 : healthRecord;
        if (next == resolve && attackBoost == 1 && healthRecord == hp) return this;
        return new ResolveProgress(level, next, 0, 1, hp, records,
                next == 0 || saveNextRecord, 0, burstLevel, trainingPoints);
    }

    /** Death preserves levels, training and the ten best combat records; ending a burst clears records. */
    public ResolveProgress resetCombat(boolean death) {
        List<Float> saved = death ? addRecord(records, resolve) : List.of();
        return new ResolveProgress(level, 0, 0, 1, -1, saved, true, 0, 0, trainingPoints);
    }

    private static List<Float> addRecord(List<Float> previous, float value) {
        if (value <= 0) return List.copyOf(previous);
        var next = new ArrayList<>(previous);
        next.add(value);
        next.sort(Float::compare);
        if (next.size() > 10) next.removeFirst();
        return List.copyOf(next);
    }
}
