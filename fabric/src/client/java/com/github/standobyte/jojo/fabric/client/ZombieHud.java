package com.github.standobyte.jojo.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Original red blood bar and green zombie action accents. Root renders this above Stand bars. */
public final class ZombieHud {
    public static final int COLOR = 0x99BB00;
    private static final OriginalHudFade FADE = new OriginalHudFade(40, 10);
    private static int lastTick, warning;
    private static Object session;
    private static boolean previousMode;
    public static void rejected() { warning = 10; }
    public static Component name(String id) {
        return Component.translatable("action.jojo." + id + (id.equals("zombie_disguise") && ZombieClient.state().disguised() ? ".disable" : ""));
    }
    public static Component powerName() { return Component.translatable("non_stand.jojo.zombie"); }
    /** Returns consumed vertical space, including faded bars, for the shared HUD layout. */
    public static int render(GuiGraphicsExtractor g, int x, int y, float partialTick) {
        var client = Minecraft.getInstance();
        if (!ZombieClient.playable()) return 0;
        int tick = client.gui.hud.getGuiTicks();
        if (session != client.level) { session = client.level; lastTick = tick; FADE.clear(); previousMode = false; warning = 0; }
        int elapsed = Math.clamp(tick - lastTick, 0, 40); lastTick = tick;
        if (!client.isPaused()) for (int i = 0; i < elapsed; i++) { FADE.tick(); warning = Math.max(0, warning - 1); }
        var state = ZombieClient.state(); boolean full = ZombieClient.isMode();
        if (state.blood() < state.maxBlood() || full != previousMode) FADE.reset();
        previousMode = full;
        int length = full ? 200 : 150, height = full ? 8 : 5;
        if (full || FADE.shouldRender()) {
            float alpha = full ? 1 : FADE.alpha(partialTick);
            int tint = OriginalHudDraw.color(0xFF0000, alpha);
            OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x + 14, y + 1, 1, full ? 161 : 169,
                    length * state.blood() / state.maxBlood(), height - 2, 256, tint);
            OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x + 13, y, 0, full ? 128 : 136, length + 2, height, 256, tint);
            if (warning > 0) {
                float phase = warning - partialTick;
                float alphaWarning = OriginalHudDraw.highlightAlpha(phase, 10, 8, phase > 5 ? .25F : 0, .75F);
                OriginalHudDraw.fill(g, x + 14, y + 1, length, height - 2, OriginalHudDraw.color(0xFF0000, alphaWarning));
            }
            OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x + 14, y + 1, 1, full ? 145 : 153, length, height - 2, 256,
                    OriginalHudDraw.color(0xFFFFFF, alpha));
            if (full) OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x, y - 3, 240, 0, 12, 16, 256, -1);
        }
        return full ? 12 : 9;
    }
    private ZombieHud() { }
}
