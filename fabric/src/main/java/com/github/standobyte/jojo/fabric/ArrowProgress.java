package com.github.standobyte.jojo.fabric;

/** Separate attachment preserves compatibility with existing Stand ownership saves. */
public record ArrowProgress(int acquired, int remainingTicks, int paidLevels, int requiredLevels, boolean healing) {
    public static final ArrowProgress EMPTY = new ArrowProgress(0, 0, 0, 0, false);
    public ArrowProgress {
        if (acquired < 0 || acquired > 1_000_000 || remainingTicks < 0 || requiredLevels < 0
                || requiredLevels > 5_000_030 || paidLevels < 0 || paidLevels > requiredLevels
                || remainingTicks > (requiredLevels + 1L) * 20 || (remainingTicks > 0 && requiredLevels == 0)) {
            throw new IllegalArgumentException("Invalid Stand Arrow progression");
        }
    }
    public int nextCost() { return 30 + acquired * 5; }
    public boolean infected() { return remainingTicks > 0; }
    public ArrowProgress begin() { return new ArrowProgress(acquired, (nextCost() + 1) * 20, 0, nextCost(), false); }
    public ArrowProgress cancel() { return new ArrowProgress(acquired, 0, 0, 0, false); }
    public ArrowProgress acquiredStand() { return new ArrowProgress(Math.min(1_000_000, acquired + 1), 0, 0, 0, true); }
}
