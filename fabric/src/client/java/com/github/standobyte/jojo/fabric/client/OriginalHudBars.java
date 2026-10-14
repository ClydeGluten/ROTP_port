package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.StandAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** The Stand branches of 1.16 BarsRenderer and HorizontalBarsRenderer, at the default TOP_LEFT anchor. */
final class OriginalHudBars {
    static final int BARS_WIDTH_PX = 28;
    private static final int BAR_LENGTH = 200;
    private static final int BAR_LENGTH_SHORTENED = 150;
    private final OriginalHudFade staminaFade = new OriginalHudFade(40, 10);
    private final OriginalHudFade resolveFade = new OriginalHudFade(40, 10);
    private float previousStamina, previousResolve, previousRecord;
    private float stamina, resolve, record;
    private int redHighlightTick;

    void reset() {
        staminaFade.clear(); resolveFade.clear();
        previousStamina = previousResolve = previousRecord = 0;
        stamina = resolve = record = 0;
        redHighlightTick = 0;
    }

    void reveal() { staminaFade.reset(); resolveFade.reset(); }

    /** Original BarsRenderer.BarEffects.triggerRedHighlight. */
    void triggerRedHighlight(int cycles) {
        if (redHighlightTick % 10 > 0) cycles--;
        redHighlightTick = redHighlightTick % 10 + cycles * 10;
    }

    void tick() {
        previousStamina = stamina;
        previousResolve = resolve;
        previousRecord = record;
        staminaFade.tick(); resolveFade.tick();
        if (redHighlightTick > 0) redHighlightTick--;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (progress.stamina() < progress.maxStamina()) staminaFade.reset();
        if (StandAttachments.resolve(player).resolve() > 0) resolveFade.reset();
        // StandPower.tick keeps this warning pulsing while the stamina debuff applies.
        if (progress.stamina() < progress.maxStamina() * 0.5F
                && !player.isCreative() && !StandAttachments.resolve(player).isBurst()) triggerRedHighlight(999999);
        else if (redHighlightTick > 10) redHighlightTick %= 10;
    }

    void render(GuiGraphicsExtractor g, int x, int y, boolean standMode, int color, float partialTick) {
        var player = Minecraft.getInstance().player;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        var advancement = StandAttachments.resolve(player);
        stamina = progress.stamina();
        resolve = advancement.resolve();
        record = advancement.records().stream().max(Float::compare).orElse(0F);
        if (!player.isCreative()) {
            renderBarWithIcon(g, x, y, true, standMode, 0xFFFFFF, 1,
                    lerp(previousStamina, stamina, partialTick), progress.maxStamina(), 0,
                    advancement.isBurst() ? 0.3F : 1F, staminaFade, partialTick);
            y += standMode ? 12 : 9;
        }
        renderBarWithIcon(g, x, y, false, standMode, color, advancement.level() / 4F,
                lerp(previousResolve, resolve, partialTick), advancement.maxResolve(),
                lerp(previousRecord, record, partialTick), 1, resolveFade, partialTick);
        y += standMode ? 12 : 9;
        if (standMode) {
            float hp = advancement.healthRecord() >= 0 ? Math.min(player.getHealth(), advancement.healthRecord()) : player.getHealth();
            float bonus = advancement.attackBoost() * (1 + (15 - Math.clamp(hp, 5, 15)) * 0.9F);
            if (bonus > 1) {
                Component text = Component.literal("x" + String.format(Locale.ROOT, "%.2f", bonus));
                // HorizontalBarsRenderer.setResolveBonusTextPos uses right-aligned text at x + 245.
                OriginalHudDraw.text(g, text, x + 245 - Minecraft.getInstance().font.width(text), y + 13, color, 1);
            }
        }
    }

    /** HorizontalBarsRenderer.renderBarWithIcon, unchanged atlas coordinates and icon scaling. */
    private void renderBarWithIcon(GuiGraphicsExtractor g, int x, int y, boolean stamina, boolean fullSize,
                                         int color, float iconFill, float value, float maxValue,
                                         float translucentValue, float alpha, OriginalHudFade fade, float partialTick) {
        int length = fullSize ? BAR_LENGTH : BAR_LENGTH_SHORTENED;
        int fill = (int) (length * (value / maxValue));
        fill = Math.min(fill, length);
        int translucentFill = Math.min((int) (length * (translucentValue / maxValue)), length);
        int texY = stamina ? 176 : 160;
        int barX = x + 13;
        if (fullSize) {
            renderBar(g, barX, y, texY, 8, length, fill, color, alpha, 128, 145, translucentFill,
                    stamina ? redHighlightTick : 0, partialTick);
            if (stamina) {
                // BarsRenderer.getIconTex(STAMINA, HORIZONTAL).
                OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x, y - 7, 128, 0, 12, 16,
                        256, OriginalHudDraw.color(0xFFFFFF, alpha));
            } else {
                // The original Resolve arrow is a 34x20 source rendered at 17x10.
                OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x - 4, y, 128, 64, 17, 10, 34, 20,
                        256, OriginalHudDraw.color(0xFFFFFF, alpha));
                if (iconFill > 0) {
                    int width = (int) (17 * iconFill);
                    OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x - 4, y, 168, 64, width, 10, width * 2, 20,
                            256, OriginalHudDraw.color(0xFFFFFF, alpha));
                }
            }
        } else if (fade.shouldRender()) {
            renderBar(g, barX, y, texY + 8, 5, length, fill, color, alpha * fade.alpha(partialTick),
                    136, 153, translucentFill, stamina ? redHighlightTick : 0, partialTick);
        }
    }

    /** BarsRenderer.renderBar plus HorizontalBarsRenderer.barFill/drawBarElement (left alignment). */
    private static void renderBar(GuiGraphicsExtractor g, int x, int y, int fillTexY, int height, int length,
                                  int fill, int color, float alpha, int borderTexY, int scaleTexY, int translucentFill,
                                  int redHighlightTick, float partialTick) {
        if (alpha <= 0) return;
        if (translucentFill > 0) {
            OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x + 1, y + 1, 1, fillTexY + 1,
                    translucentFill, height - 2, 256, OriginalHudDraw.color(color, alpha * 0.4F));
        }
        if (fill > 0) {
            OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x + 1, y + 1, 1, fillTexY + 1,
                    fill, height - 2, 256, OriginalHudDraw.color(color, alpha));
        }
        OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x, y, 0, borderTexY, length + 2, height,
                256, OriginalHudDraw.color(color, alpha));
        // Upstream's cost values are zero: the getActionCost block is commented out in BarsRenderer.render.
        if (redHighlightTick > 0) {
            float tick = redHighlightTick - partialTick;
            float highlight = OriginalHudDraw.highlightAlpha(tick, 10, 8, tick > 5 ? 0.25F : 0, 0.75F);
            OriginalHudDraw.fill(g, x + 1, y + 1, length, height - 2, OriginalHudDraw.color(0xFF0000, highlight));
        }
        OriginalHudDraw.blit(g, ActionHud.OVERLAY_LOCATION, x + 1, y + 1, 1, scaleTexY, length, height - 2,
                256, OriginalHudDraw.color(0xFFFFFF, alpha));
    }

    private static float lerp(float previous, float current, float partialTick) {
        return previous + (current - previous) * partialTick;
    }
}
