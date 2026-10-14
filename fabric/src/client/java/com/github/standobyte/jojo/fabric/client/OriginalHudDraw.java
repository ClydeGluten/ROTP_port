package com.github.standobyte.jojo.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Float blits and text helpers replacing 1.16 BlitFloat and ClientUtil's immediate-mode drawing. */
final class OriginalHudDraw {
    private static final int SUBPIXELS = 1024;

    static int color(int rgb, float alpha) {
        return (Math.round(Math.clamp(alpha, 0F, 1F) * 255F) << 24) | (rgb & 0xFFFFFF);
    }

    static void blit(GuiGraphicsExtractor g, Identifier texture, float x, float y, float u, float v,
                     float width, float height, int textureSize, int color) {
        blit(g, texture, x, y, u, v, width, height, width, height, textureSize, color);
    }

    static void blit(GuiGraphicsExtractor g, Identifier texture, float x, float y, float u, float v,
                     float width, float height, float sourceWidth, float sourceHeight, int textureSize, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        // The new extractor accepts integer rectangles. A subpixel pose retains the original
        // floating UV/geometry cutoffs while the ornamental hotbar folds between its slots.
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1F / SUBPIXELS, 1F / SUBPIXELS);
        g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, u * SUBPIXELS, v * SUBPIXELS,
                Math.round(width * SUBPIXELS), Math.round(height * SUBPIXELS),
                Math.round(sourceWidth * SUBPIXELS), Math.round(sourceHeight * SUBPIXELS),
                textureSize * SUBPIXELS, textureSize * SUBPIXELS, color);
        g.pose().popMatrix();
    }

    static void fill(GuiGraphicsExtractor g, float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1F / SUBPIXELS, 1F / SUBPIXELS);
        g.fill(0, 0, Math.round(width * SUBPIXELS), Math.round(height * SUBPIXELS), color);
        g.pose().popMatrix();
    }

    /** ActionsOverlayGui.drawString/drawBackdrop and ClientUtil.drawBackdrop, left alignment. */
    static void text(GuiGraphicsExtractor g, Component text, int x, int y, int rgb, float alpha) {
        if (alpha <= 0) return;
        var client = Minecraft.getInstance();
        int background = client.options.getBackgroundColor(0F);
        if (background != 0) {
            int backdrop = color(background, (background >>> 24) / 255F * alpha);
            g.fill(x - 2, y - 2, x + client.font.width(text) + 2, y + client.font.lineHeight + 2, backdrop);
        }
        g.text(client.font, text, x, y, color(rgb, alpha), true);
    }

    /** Unchanged triangular pulse from ClientUtil.getHighlightAlpha. */
    static float highlightAlpha(float ticks, float cycleTicks, float maxAlphaTicks, float minAlpha, float maxAlpha) {
        ticks %= cycleTicks;
        float coefficient = maxAlpha / maxAlphaTicks;
        float alpha = ticks <= cycleTicks / 2 ? coefficient * ticks : coefficient * (cycleTicks - ticks);
        return Math.min(alpha, maxAlpha - minAlpha) + minAlpha;
    }

    private OriginalHudDraw() { }
}
