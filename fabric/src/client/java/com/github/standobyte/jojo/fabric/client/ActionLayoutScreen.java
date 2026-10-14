package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.core.ActionLayout;
import com.github.standobyte.jojo.fabric.RipplesFabric;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;

/** Ports HudLayoutEditingScreen's texture, slot geometry, pick/place, number move and visibility switch. */
public final class ActionLayoutScreen extends Screen {
    private static final Identifier WINDOW = RipplesFabric.id("textures/gui/layout_editing.png");
    private static final int WINDOW_WIDTH = 230, WINDOW_HEIGHT = 180;
    private final Screen parent;
    private final String stand;
    private ActionLayout draft;
    private Slot picked, hovered;
    private String status = "";
    private record Slot(boolean attack, String id, int index) { }

    public ActionLayoutScreen(Screen parent) {
        super(Component.translatable("jojo.screen.edit_hud_layout"));
        this.parent = parent;
        stand = ActionCatalog.standId();
        draft = ActionLayouts.get(stand);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        if (ActionLayouts.set(stand, draft)) minecraft.gui.setScreen(parent);
        else status = "jojo.fabric.layout.save_error";
    }
    private int windowX() { return (width - WINDOW_WIDTH) / 2; }
    private int windowY() { return (height - WINDOW_HEIGHT) / 2; }
    private List<String> row(boolean attack) { return attack ? draft.attacks() : draft.abilities(); }
    private void refresh() { clearWidgets(); init(); }
    @Override protected void init() {
        addRow(true); addRow(false);
        addRenderableWidget(new TextureButton(windowX() - 26, windowY() + 146, 24, 24,
                Component.translatable("jojo.screen.edit_hud_layout.reset"), 0, 208, () -> {
                    draft = ActionCatalog.defaults(stand.equals("jojo:the_world")); picked = null; refresh();
                }));
        addRenderableWidget(new TextureButton(windowX() - 24, windowY() + 60, 22, 22,
                Component.translatable("jojo.screen.edit_hud_layout.mc_controls"), 164, 212,
                () -> minecraft.gui.setScreen(new ControlsScreen(this, minecraft.options))));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(windowX() + 66, windowY() + 151, 98, 20).build());
    }
    private void addRow(boolean attack) {
        List<String> row = row(attack);
        for (int i = 0; i < row.size(); i++) {
            Slot slot = new Slot(attack, row.get(i), i);
            addRenderableWidget(new ActionButton(slot));
        }
        if (picked != null && picked.attack != attack && row.size() < 9) {
            addRenderableWidget(new TextureButton(windowX() + 20 + row.size() * 18,
                    windowY() + (attack ? 10 : 36), 18, 18,
                    Component.translatable("jojo.fabric.layout.place"), 64, 220,
                    () -> place(attack, row.size())));
        }
    }
    private void pickOrPlace(Slot slot) {
        if (picked != null) place(slot.attack, slot.index);
        else { picked = slot; refresh(); }
    }
    private void place(boolean attack, int index) {
        if (picked == null) return;
        draft = draft.transfer(picked.attack, picked.id, attack, index);
        picked = null; refresh();
    }
    private Slot slotAt(double mx, double my) {
        int x = (int) mx - windowX() - 20, y = (int) my - windowY();
        if (x < 0) return null;
        boolean attack;
        if (y >= 10 && y < 28) attack = true;
        else if (y >= 36 && y < 54) attack = false;
        else return null;
        int i = x / 18;
        return i < row(attack).size() ? new Slot(attack, row(attack).get(i), i) : null;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Slot slot = slotAt(event.x(), event.y());
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && slot != null) {
            draft = draft.toggleEnabled(slot.id); refresh(); return true;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled && event.button() == InputConstants.MOUSE_BUTTON_LEFT && picked != null) {
            picked = null; refresh(); return true;
        }
        return handled;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (StandSelection.editorMatches(event)) { onClose(); return true; }
        for (int i = 0; i < minecraft.options.keyHotbarSlots.length; i++) {
            if (!minecraft.options.keyHotbarSlots[i].matches(event)) continue;
            Slot moving = picked != null ? picked : hovered;
            if (moving == null && getFocused() instanceof ActionButton button) moving = button.slot;
            if (moving != null && i < row(moving.attack).size()) {
                draft = draft.transfer(moving.attack, moving.id, moving.attack, i);
                picked = null; refresh(); return true;
            }
        }
        return super.keyPressed(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        Slot slot = slotAt(x, y);
        if (slot != null && vertical != 0) {
            draft = draft.cycleOriginal(slot.attack, vertical > 0 ? -1 : 1, id -> ActionCatalog.move(id).unlocked());
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hovered = slotAt(mx, my);
        g.blit(RenderPipelines.GUI_TEXTURED, WINDOW, windowX(), windowY(), 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, 256, 256);
        g.blit(RenderPipelines.GUI_TEXTURED, WINDOW, windowX() + 7, windowY() + 10, 232, 3, 9, 16, 256, 256);
        g.blit(RenderPipelines.GUI_TEXTURED, WINDOW, windowX() + 7, windowY() + 36, 232, 39, 9, 16, 256, 256);
        g.centeredText(font, title, width / 2, windowY() - 15, 0xFFFFFFFF);
        super.extractRenderState(g, mx, my, delta);
        int textY = windowY() + 69;
        for (String key : List.of("jojo.screen.edit_hud_layout.hint.lmb", "jojo.screen.edit_hud_layout.hint.rmb", "jojo.fabric.layout.number_move")) {
            for (var line : font.split(Component.translatable(key), 205)) {
                g.text(font, line, windowX() + 12, textY, 0xFF282828, false); textY += 10;
            }
            textY += 4;
        }
        if (!status.isEmpty()) g.centeredText(font, Component.translatable(status), width / 2, windowY() + 138, 0xFFAA0000);
        if (picked != null) drawIcon(g, picked.id, mx - 8, my - 8, true);
        else if (hovered != null) {
            g.setTooltipForNextFrame(font, List.of(ActionCatalog.displayMove(hovered.id).name(),
                    Component.translatable("jojo.screen.edit_hud_layout.hint.lmb"),
                    Component.translatable("jojo.screen.edit_hud_layout.hint.rmb")), java.util.Optional.empty(), mx, my);
        }
    }
    private void drawIcon(GuiGraphicsExtractor g, String id, int x, int y, boolean enabled) {
        var move = ActionCatalog.displayMove(id);
        int color = !enabled ? 0x33000000 : move.unlocked() ? 0xFFFFFFFF : 0x99000000;
        g.blit(RenderPipelines.GUI_TEXTURED, move.texture(), x, y, 0, 0, 16, 16, 32, 32, 32, 32, color);
    }
    private class ActionButton extends AbstractWidget {
        final Slot slot;
        ActionButton(Slot slot) {
            super(windowX() + 20 + slot.index * 18, windowY() + (slot.attack ? 10 : 36), 18, 18,
                    ActionCatalog.move(slot.id).name());
            this.slot = slot;
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            int texX = isHoveredOrFocused() ? 82 : 64;
            if (picked != null) texX += 36;
            g.blit(RenderPipelines.GUI_TEXTURED, WINDOW, getX(), getY(), texX, 238, 18, 18, 256, 256);
            if (picked == null || !picked.id.equals(slot.id)) drawIcon(g, slot.id, getX() + 1, getY() + 1, !draft.disabled().contains(slot.id));
        }
        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { pickOrPlace(slot); }
        @Override public boolean keyPressed(KeyEvent event) {
            if (isFocused() && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) {
                pickOrPlace(slot); return true;
            }
            return super.keyPressed(event);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
    private static class TextureButton extends AbstractWidget {
        private final int u, v;
        private final Runnable action;
        TextureButton(int x, int y, int w, int h, Component name, int u, int v, Runnable action) {
            super(x, y, w, h, name); this.u = u; this.v = v; this.action = action;
            setTooltip(Tooltip.create(name));
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            int hoverOffset = isHoveredOrFocused() && width != 18 ? height : 0;
            g.blit(RenderPipelines.GUI_TEXTURED, WINDOW, getX(), getY(), u, v + hoverOffset, width, height, 256, 256);
        }
        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { action.run(); }
        @Override public boolean keyPressed(KeyEvent event) {
            if (isFocused() && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) {
                action.run(); return true;
            }
            return super.keyPressed(event);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
