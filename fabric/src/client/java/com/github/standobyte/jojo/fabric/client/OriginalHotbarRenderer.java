package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Port of 1.16 HotbarRenderer's atlas table, folding frames, and ornamental selection. */
final class OriginalHotbarRenderer {
    static final Identifier HOTBAR_LOCATION = RipplesFabric.id("textures/gui/overlay_hotbar.png");
    public static final int EDGE_EXTRA_WIDTH = 15;
    enum Alignment { LEFT, RIGHT }
    private static final int[][] TEX_POSITIONS = {
            {405, 65}, {385, 115}, {365, 165}, {345, 215}, {325, 265},
            {305, 315}, {285, 365}, {265, 415}, {245, 465}, {15, 465},
            {15, 415}, {15, 365}, {15, 315}, {15, 265}, {15, 215},
            {15, 165}, {15, 115}, {15, 65}, {15, 15}
    };

    static void renderFoldingHotbar(GuiGraphicsExtractor g, float x, float y, OriginalHotbarFold fold, float alpha) {
        if (fold.getSlotsCount() <= 0) return;
        int[] tex = TEX_POSITIONS[Math.clamp(fold.getSlotsCount() - 1, 0, TEX_POSITIONS.length - 1)];
        fold.renderSlots(slot -> {
            float width = slot.getFrameRenderedWidth();
            if (width > 0) {
                OriginalHudDraw.blit(g, HOTBAR_LOCATION,
                        x + slot.getFrameRenderedLeftEdge() - EDGE_EXTRA_WIDTH + 1, y - EDGE_EXTRA_WIDTH + 1,
                        tex[0] + slot.getFrameRenderedTexX() - EDGE_EXTRA_WIDTH, tex[1] - EDGE_EXTRA_WIDTH,
                        width, 20 + EDGE_EXTRA_WIDTH * 2, 512, OriginalHudDraw.color(0xFFFFFF, alpha));
            }
        });
    }

    static void renderSlotSelection(GuiGraphicsExtractor g, float x, float y, float alpha, boolean green) {
        OriginalHudDraw.blit(g, HOTBAR_LOCATION, x - 28, y - 28, 440, 0, 72, 72,
                512, OriginalHudDraw.color(green ? 0x00FF00 : 0xFFFFFF, alpha));
    }

    private OriginalHotbarRenderer() { }
}
