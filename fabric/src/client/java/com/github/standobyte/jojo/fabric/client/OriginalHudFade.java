package com.github.standobyte.jojo.fabric.client;

/** Original FadeOut/ElementTransparency arithmetic, including the minimum text alpha. */
final class OriginalHudFade {
    private final int ticksMax;
    private final int ticksStartFadeOut;
    private int ticks;

    OriginalHudFade(int ticksMax, int ticksStartFadeOut) {
        this.ticksMax = ticksMax;
        this.ticksStartFadeOut = ticksStartFadeOut;
    }

    void reset() { ticks = ticksMax; }
    void clear() { ticks = 0; }
    void tick() { if (ticks > 0) ticks--; }
    boolean shouldRender() { return ticks > 0; }
    float value(float partialTick) {
        if (ticks >= ticksStartFadeOut) return 1;
        if (ticks <= 0) return 0;
        return (ticks - partialTick) / ticksStartFadeOut;
    }
    float alpha(float partialTick) { return ticks > 0 ? Math.max(value(partialTick), 1F / 63F) : 0; }
}
