package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.github.standobyte.jojo.fabric.StandAttachments;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Read-only progression overview. All values come from client-side attachment data; nothing is sent to the server. */
public final class ProgressionScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFD45C;
    private static final int LILAC = 0xFFE1D3FF;
    private static final int GREEN = 0xFF7DFF9A;
    private static final int GRAY = 0xFFB8B8B8;
    private static final int CYAN = 0xFFBCEAFF;
    private static final int RED = 0xFFFF8080;
    private static final int PANEL = 0xD0101018;
    private static final int PANEL_EDGE = 0xFF8E45FF;
    private static final int PAD = 6;
    private static final int TOP = 24;
    private static final int FOOTER = 28;

    /** One drawable row: either wrapped text or a progress bar. */
    private record Row(FormattedCharSequence text, int color, float bar, int barColor) {
        boolean isBar() { return text == null; }
    }

    private int scroll;
    private int maxScroll;
    private int contentHeight;
    private int left;
    private int right;

    public ProgressionScreen() {
        super(Component.translatable("jojo.fabric.progress.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int w = 100;
        addRenderableWidget(Button.builder(Component.translatable("jojo.fabric.progress.close"), b -> onClose())
                .bounds((width - w) / 2, height - FOOTER + 4, w, 20).build());
    }

    private ResolveProgress resolve() {
        var player = minecraft == null ? null : minecraft.player;
        return player == null ? null : StandAttachments.resolve(player);
    }

    private boolean hasStand() {
        var player = minecraft == null ? null : minecraft.player;
        return player != null && player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand();
    }

    private boolean creative() {
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }

    @Override
    public Component getNarrationMessage() {
        var r = resolve();
        if (r == null || !hasStand()) return Component.translatable("jojo.fabric.progress.narration.none");
        return Component.translatable(creative() ? "jojo.fabric.progress.narration.creative" : "jojo.fabric.progress.narration",
                r.level(), Math.round(r.resolve()), Math.round(r.maxResolve()),
                String.format(java.util.Locale.ROOT, "%.2f", r.timeStopTicks() / 20F), Math.round(r.trainingPoints()));
    }

    private static Component key(String name) {
        return Component.keybind("key.jojo.fabric." + name);
    }

    private void text(List<Row> rows, int w, Component c, int color) {
        for (FormattedCharSequence line : font.split(c, w)) rows.add(new Row(line, color, 0, 0));
    }

    private void gap(List<Row> rows) {
        rows.add(new Row(FormattedCharSequence.EMPTY, WHITE, 0, 0));
    }

    private void move(List<Row> rows, int w, Component name, int level, int current) {
        boolean open = level <= current;
        boolean bypass = !open && creative();
        Component status = level == 0 || open ? Component.translatable("jojo.fabric.progress.unlocked")
                : bypass ? Component.translatable("jojo.fabric.progress.creative_access", level)
                : Component.translatable("jojo.fabric.progress.requires_level", level);
        text(rows, w, Component.translatable("jojo.fabric.progress.move_line", name, status),
                level == 0 || open ? GREEN : bypass ? CYAN : GRAY);
    }

    private List<Row> buildRows(int w) {
        List<Row> rows = new ArrayList<>();
        ResolveProgress r = resolve();
        if (r == null) return rows;
        if (!hasStand()) {
            text(rows, w, Component.translatable("jojo.fabric.progress.no_stand"), GOLD);
            return rows;
        }
        boolean creative = creative();
        if (creative) text(rows, w, Component.translatable("jojo.fabric.progress.creative_notice"), CYAN);

        text(rows, w, Component.translatable("jojo.fabric.progress.level", r.level()), GOLD);
        float max = Math.max(r.maxResolve(), 0.0001F);
        text(rows, w, Component.translatable("jojo.fabric.progress.resolve", Math.round(r.resolve()), Math.round(r.maxResolve())), WHITE);
        rows.add(new Row(null, 0, Math.clamp(r.resolve() / max, 0F, 1F), r.isBurst() ? 0xFFFFD45C : 0xFF8E45FF));
        if (r.isBurst()) {
            text(rows, w, Component.translatable("jojo.fabric.progress.burst", String.format(java.util.Locale.ROOT, "%.1f", r.burstRemainingTicks() / 20F)), GOLD);
        }
        gap(rows);

        text(rows, w, Component.translatable("jojo.fabric.progress.gain_title"), GOLD);
        text(rows, w, Component.translatable("jojo.fabric.progress.gain_attacks"), LILAC);
        text(rows, w, Component.translatable("jojo.fabric.progress.gain_boost"), LILAC);
        text(rows, w, Component.translatable("jojo.fabric.progress.decay"), LILAC);
        gap(rows);

        text(rows, w, Component.translatable("jojo.fabric.progress.moves_title"), GOLD);
        int lv = r.level();
        for (String id : ActionCatalog.attacks(ActionCatalog.world())) {
            var action = ActionCatalog.move(id); move(rows, w, action.name(), action.level(), lv);
        }
        for (String id : ActionCatalog.abilities(ActionCatalog.world())) {
            var action = ActionCatalog.move(id); move(rows, w, action.name(), action.level(), lv);
        }
        // Blink is a Shift variation, so it has no separate default hotbar slot.
        var blink = ActionCatalog.move("blink");
        move(rows, w, blink.name(), blink.level(), lv);
        text(rows, w, Component.translatable("jojo.fabric.progress.blink_input",
                Component.keybind(minecraft.options.keyShift.getName())), LILAC);
        gap(rows);

        text(rows, w, Component.translatable("jojo.fabric.progress.time_title"), GOLD);
        text(rows, w, Component.translatable("jojo.fabric.progress.time_duration",
                String.format(java.util.Locale.ROOT, "%.2f", r.timeStopTicks() / 20F), "5.0"), WHITE);
        text(rows, w, Component.translatable("jojo.fabric.progress.time_points", Math.round(r.trainingPoints() * 10) / 10F, 95), WHITE);
        rows.add(new Row(null, 0, Math.clamp(r.trainingPoints() / 95F, 0F, 1F), 0xFF5CC8FF));
        text(rows, w, Component.translatable(ActionCatalog.world() ? "jojo.fabric.progress.world_training" : "jojo.fabric.progress.time_training"), LILAC);
        if (creative) text(rows, w, Component.translatable("jojo.fabric.progress.time_creative"), CYAN);
        return rows;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_END) scroll = maxScroll;
        else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_HOME) scroll = 0;
        else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN) scroll = Math.min(maxScroll, scroll + 24);
        else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_UP) scroll = Math.max(0, scroll - 24);
        else return super.keyPressed(event);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY) * 12, 0, maxScroll);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (minecraft == null || minecraft.player == null) return;
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        int panelW = Math.min(width - 12, 340);
        left = (width - panelW) / 2;
        right = left + panelW;
        int top = TOP;
        int bottom = height - FOOTER;
        g.fill(left, top - 2, right, bottom, PANEL);
        g.outline(left, top - 2, panelW, bottom - top + 2, PANEL_EDGE);
        g.centeredText(font, title, width / 2, 8, WHITE);

        int textW = panelW - PAD * 2 - 6;
        List<Row> rows = buildRows(textW);
        int lh = font.lineHeight + 1;
        contentHeight = 0;
        for (Row row : rows) contentHeight += row.isBar() ? 11 : lh;
        int viewH = bottom - top - PAD;
        maxScroll = Math.max(0, contentHeight - viewH);
        scroll = Math.clamp(scroll, 0, maxScroll);

        g.enableScissor(left + 1, top, right - 1, bottom - 1);
        int y = top + PAD / 2 - scroll;
        int x = left + PAD;
        for (Row row : rows) {
            if (row.isBar()) {
                g.fill(x, y + 1, x + textW, y + 9, 0xFF000000);
                g.outline(x, y + 1, textW, 8, 0xFF606060);
                int filled = Math.round((textW - 2) * row.bar());
                if (filled > 0) g.fill(x + 1, y + 2, x + 1 + filled, y + 8, row.barColor());
                y += 11;
            } else {
                g.text(font, row.text(), x, y, row.color());
                y += lh;
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = bottom - top - 2;
            int thumbH = Math.max(12, trackH * viewH / contentHeight);
            int thumbY = top + (trackH - thumbH) * scroll / maxScroll;
            g.fill(right - 4, top, right - 2, bottom - 2, 0xFF303030);
            g.fill(right - 4, thumbY, right - 2, thumbY + thumbH, 0xFFC8B0FF);
        }
    }
}
