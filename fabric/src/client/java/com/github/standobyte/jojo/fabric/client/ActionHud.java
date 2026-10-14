package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StandArrowItem;
import com.github.standobyte.jojo.fabric.StandAttachments;
import com.github.standobyte.jojo.fabric.StandHudState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.List;

/**
 * Stand-only port of 1.16 ActionsOverlayGui, using its default ClientModSettings:
 * TOP_LEFT bars/hotbars, FADE_OUT text, and folding hotbars. Rendering adapters
 * replace MatrixStack/RenderSystem with 26.3's GuiGraphicsExtractor.
 */
public final class ActionHud {
    static final Identifier OVERLAY_LOCATION = RipplesFabric.id("textures/gui/overlay.png");
    private static final Identifier RADIAL_INDICATOR = RipplesFabric.id("textures/gui/radial_indicator.png");
    private static final OriginalHudBars BARS = new OriginalHudBars();
    private static final OriginalHudFade POWER_NAME = new OriginalHudFade(40, 10);
    private static final OriginalHudFade[] NAMES = {new OriginalHudFade(40, 10), new OriginalHudFade(40, 10)};
    private static final OriginalHudFade[] FOLD = {new OriginalHudFade(40, 10), new OriginalHudFade(40, 10)};
    private static final String[] LAST_SELECTED = {"", ""};
    private static final Component[] LAST_NAMES = {Component.empty(), Component.empty()};
    private static Object session;
    private static String lastStand = "";
    private static int lastTick;
    private static boolean previousMode;
    private static int previousCharge, crosshairFlashTicks;

    /** Input adapter may reuse the original one-cycle resource-warning effect on a rejected action. */
    public static void staminaRejected() { BARS.triggerRedHighlight(1); }

    public static void render(GuiGraphicsExtractor g, float partialTick, boolean standMode,
                              boolean attackFocused, boolean abilityFocused, boolean hotbarsDisabled) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden() || !client.player.isAlive() || client.player.isSpectator()) return;
        update(client, standMode, attackFocused, abilityFocused);
        var progress = client.player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (!progress.hasStand()) { renderArrowFeedback(g); return; }
        int color = progress.hasTheWorld() ? 0xFFD800 : 0x8E45FF; // ModStandsInit registry colors.
        int x = 4, y = standMode ? 4 + OriginalHudBars.BARS_WIDTH_PX + 4 : 4;
        BARS.render(g, 4, 4, standMode, color, partialTick);
        renderPowerIcon(g, x, y);
        if (standMode) {
            var snapshot = client.player.getAttachedOrCreate(StandAttachments.HUD);
            var layout = ActionLayouts.current();
            List<String> attacks = layout.enabledActions(true), abilities = layout.enabledActions(false);
            if (!attacks.isEmpty()) renderActionsHotbar(g, x, y, true, attacks, StandSelection.selectedAction(true),
                    snapshot, attackFocused, hotbarsDisabled, color, partialTick);
            if (!abilities.isEmpty()) renderActionsHotbar(g, x, y + (attacks.isEmpty() ? 0 : getHotbarsYDiff()), false,
                    abilities, StandSelection.selectedAction(false), snapshot, abilityFocused, hotbarsDisabled, color, partialTick);
            drawPowerName(g, x, y, color, partialTick);
            renderIconBarAtCrosshair(g);
            renderCrosshair(g, snapshot, partialTick);
        }
        // Retain the port's regional Time Stop visual/countdown for frozen observers.
        if (TimeStopClient.isActive()) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x187C63B7);
            g.text(client.font, Component.translatable("jojo.fabric.time_active", TimeStopClient.ticksRemaining() / 20F),
                    4, g.guiHeight() - 37, 0xFFFFD45C);
        }
    }

    /** ActionsOverlayGui.tick/setPowerMode/scrollAction, driven by the client GUI tick counter. */
    private static void update(Minecraft client, boolean mode, boolean attackFocused, boolean abilityFocused) {
        String stand = client.player.getAttachedOrCreate(StandAttachments.PROGRESS).standId();
        int tick = client.gui.hud.getGuiTicks();
        if (session != client.level || !stand.equals(lastStand)) {
            session = client.level; lastStand = stand; lastTick = tick; previousMode = false;
            BARS.reset(); POWER_NAME.clear();
            previousCharge = crosshairFlashTicks = 0;
            for (int row = 0; row < 2; row++) {
                NAMES[row].clear(); FOLD[row].clear(); LAST_SELECTED[row] = ""; LAST_NAMES[row] = Component.empty();
            }
        }
        int elapsed = Math.clamp(tick - lastTick, 0, 40);
        if (!client.isPaused()) for (int i = 0; i < elapsed; i++) {
            BARS.tick(); POWER_NAME.tick();
            if (crosshairFlashTicks > 0) crosshairFlashTicks--;
            for (int row = 0; row < 2; row++) { NAMES[row].tick(); FOLD[row].tick(); }
        }
        lastTick = tick;
        var snapshot = client.player.getAttachedOrCreate(StandAttachments.HUD);
        if (previousCharge > 0 && snapshot.charge() == 0 && snapshot.activeStop() > 0) crosshairFlashTicks = 8;
        previousCharge = snapshot.charge();
        if (mode != previousMode) {
            if (mode) {
                POWER_NAME.reset();
                for (int row = 0; row < 2; row++) { NAMES[row].reset(); FOLD[row].reset(); }
            } else BARS.reveal();
            previousMode = mode;
        }
        if (mode) {
            for (int row = 0; row < 2; row++) {
                String selected = StandSelection.selectedAction(row == 0);
                if (!selected.equals(LAST_SELECTED[row])) { LAST_SELECTED[row] = selected; FOLD[row].reset(); }
            }
            if (attackFocused) FOLD[0].reset();
            if (abilityFocused) FOLD[1].reset();
        }
    }

    private static int getHotbarsYDiff() { return 2 * 2 + 22 + Minecraft.getInstance().font.lineHeight; }

    /** ActionsOverlayGui.renderActionsHotbar: original row offsets, fold clipping and selection pulse. */
    private static void renderActionsHotbar(GuiGraphicsExtractor g, int positionX, int positionY,
                                            boolean attack, List<String> ids, String selectedId,
                                            StandHudState snapshot, boolean focused, boolean disabled,
                                            int color, float partialTick) {
        int row = attack ? 0 : 1, selected = ids.indexOf(selectedId);
        float alpha = selected < 0 || disabled ? 0.25F : 1F;
        int x = positionX, y = positionY + getHotbarsYDiff() - 6;
        OriginalHudDraw.blit(g, OVERLAY_LOCATION, x, y + 2, attack ? 216 : 226, 128, 9, 16,
                256, OriginalHudDraw.color(0xFFFFFF, alpha));
        x += 12;
        OriginalHotbarFold fold = OriginalHotbarFold.makeHotbarFold(ids.size(), selected,
                1 - FOLD[row].value(partialTick), OriginalHotbarRenderer.Alignment.LEFT);
        OriginalHotbarRenderer.renderFoldingHotbar(g, x, y, fold, alpha);
        int iconX = x + 3, iconY = y + 3;
        fold.renderSlots(slot -> {
            if (slot.getFrameRenderedWidth() <= 0) return;
            float leftIconEdge = slot.pos + 2 + OriginalHotbarRenderer.EDGE_EXTRA_WIDTH;
            float rightIconEdge = leftIconEdge + 16;
            float leftBorder = Math.max(slot.getFrameRenderedLeftEdge(), leftIconEdge);
            float rightBorder = Math.min(slot.getFrameRenderedLeftEdge() + slot.getFrameRenderedWidth(), rightIconEdge);
            ActionCatalog.Move move = ActionCatalog.displayMove(ids.get(slot.slotIndex));
            renderActionIcon(g, move, snapshot, iconX + slot.pos, iconY, slot.slotIndex == selected, alpha,
                    leftBorder - leftIconEdge, rightBorder - leftBorder, partialTick);
        });
        if (focused) {
            float highlight = OriginalHudDraw.highlightAlpha(lastTick + partialTick, 40, 40, 0.25F, 0.5F);
            if (selected >= 0) OriginalHudDraw.fill(g, iconX + fold.getSlotWithIndex(selected).pos - 4, iconY - 4,
                    24, 23, OriginalHudDraw.color(0xFFFFFF, highlight));
            else OriginalHudDraw.fill(g, iconX - 3, iconY - 3, ids.size() * 20 + 2, 22,
                    OriginalHudDraw.color(0xFFFFFF, highlight));
        }
        if (selected >= 0) renderActionHoldProgress(g, ActionCatalog.displayMove(selectedId), snapshot,
                iconX + fold.getSlotWithIndex(selected).pos + 10, iconY + 9, disabled, partialTick);
        drawHotbarText(g, positionX, positionY, row, selectedId, color, disabled, partialTick);
    }

    /** ActionsOverlayGui.renderActionIcon: dim unavailable icons, white cooldown fill, learning and ornament. */
    private static void renderActionIcon(GuiGraphicsExtractor g, ActionCatalog.Move move, StandHudState snapshot,
                                         float x, float y, boolean selected, float hotbarAlpha,
                                         float leftCut, float cutWidth, float partialTick) {
        var client = Minecraft.getInstance();
        var progress = client.player.getAttachedOrCreate(StandAttachments.PROGRESS);
        int cooldown = move.cooldown(snapshot);
        boolean available = move.unlocked() && cooldown <= 0 && (!move.requiresManifestation() || StandView.ownStand(client) != null)
                && (!move.requiresStaminaUpfront() || client.player.isCreative() || progress.stamina() >= move.cost())
                && (move.canReplaceRecovery() || snapshot.recovery() <= 0)
                && (snapshot.charge() <= 0 || move.id().equals("resume"));
        int iconColor = available ? OriginalHudDraw.color(0xFFFFFF, hotbarAlpha)
                : OriginalHudDraw.color(0x333333, 0.5F * hotbarAlpha);
        if (cutWidth > 0) {
            // Action.renderActionIcon normalizes the entire texture to 16x16; source art is 32x32.
            OriginalHudDraw.blit(g, move.texture(), x + leftCut, y, leftCut * 2, 0, cutWidth, 16,
                    cutWidth * 2, 32, 32, iconColor);
            if (!available && cooldown > 0) {
                int duration = move.cooldownTotal(snapshot);
                float ratio = duration > 0 ? Math.clamp((cooldown - partialTick) / duration, 0F, 1F) : 0;
                OriginalHudDraw.fill(g, x + leftCut, y + 16 * (1 - ratio), cutWidth, 16 * ratio, 0x7FFFFFFF);
            }
        }
        if (move.id().equals("time_stop")) {
            float training = client.player.isCreative() ? 1 : StandAttachments.resolve(client.player).trainingPoints() / 95F;
            if (training >= 0 && training < 1) {
                OriginalHudDraw.fill(g, x + 2, y + 13, 13, 2, 0xFF000000);
                OriginalHudDraw.fill(g, x + 2, y + 13, training * 13, 1, 0xFF00FF00);
            }
        }
        if (selected) {
            boolean finisher = move.id().equals("uppercut") || move.id().equals("kick");
            OriginalHotbarRenderer.renderSlotSelection(g, x, y, hotbarAlpha, finisher && available);
        }
    }

    /** ActionsOverlayGui.drawHotbarText/actionName: original name offsets and 40/10-tick fade. */
    private static void drawHotbarText(GuiGraphicsExtractor g, int x, int y, int row, String id,
                                       int color, boolean disabled, float partialTick) {
        if (id.isEmpty()) { LAST_NAMES[row] = Component.empty(); return; }
        var action = ActionCatalog.displayMove(id);
        Component baseName = ActionCatalog.move(id).name();
        if (!baseName.equals(LAST_NAMES[row])) { LAST_NAMES[row] = baseName; NAMES[row].reset(); }
        Component name = action.name();
        if (action.held() != 0 || action.id().equals("time_stop")) name = Component.translatable("jojo.overlay.hold", name);
        if (action.id().equals(id)) {
            ActionCatalog.Move shift = switch (id) {
                case "light", "barrage" -> ActionCatalog.move("heavy");
                case "time_stop" -> ActionCatalog.move("blink");
                default -> null;
            };
            if (shift != null && shift.unlocked()) name = Component.translatable("jojo.overlay.shift", name,
                    Component.keybind(Minecraft.getInstance().options.keyShift.getName()), shift.name());
        }
        float alpha = NAMES[row].alpha(partialTick);
        if (disabled && alpha > 0) alpha = Math.max(alpha * 0.25F, 1F / 63F);
        OriginalHudDraw.text(g, name, x + 12, y + 16 + 3, color, alpha);
    }

    private static void renderPowerIcon(GuiGraphicsExtractor g, int x, int y) {
        if (StandView.ownStand(Minecraft.getInstance()) == null) return;
        String stand = ActionCatalog.world() ? "the_world" : "star_platinum";
        OriginalHudDraw.blit(g, RipplesFabric.id("textures/power/" + stand + ".png"), x, y,
                0, 0, 16, 16, 16, 0xFFFFFFFF);
    }
    private static void drawPowerName(GuiGraphicsExtractor g, int x, int y, int color, float partialTick) {
        String stand = ActionCatalog.world() ? "the_world" : "star_platinum";
        OriginalHudDraw.text(g, Component.translatable("stand.jojo." + stand), x + 19,
                y + (16 - Minecraft.getInstance().font.lineHeight) / 2, color, POWER_NAME.alpha(partialTick));
    }

    /** ActionsOverlayGui.renderIconBarAtCrosshair/renderFilledIcon, original 18px finisher sprite. */
    private static void renderIconBarAtCrosshair(GuiGraphicsExtractor g) {
        var client = Minecraft.getInstance();
        var stand = StandView.ownStand(client);
        if (stand == null || !StandAttachments.resolve(client.player).unlocked(1, client.player.isCreative())) return;
        float fill = stand.finisherCharge();
        if (fill <= 0) return;
        int x = g.guiWidth() / 2 - 25, y = g.guiHeight() / 2 - 9;
        OriginalHudDraw.blit(g, OVERLAY_LOCATION, x, y, 96, 216, 18, 18, 256, 0xFFFFFFFF);
        float pixels = 18 * Math.clamp(fill, 0F, 1F);
        OriginalHudDraw.blit(g, OVERLAY_LOCATION, x, y + 18 - pixels, fill >= 0.5F ? 132 : 114,
                216 + 18 - pixels, 18, pixels, 256, 0xFFFFFFFF);
    }

    private static void renderActionHoldProgress(GuiGraphicsExtractor g, ActionCatalog.Move move, StandHudState snapshot,
                                                 float x, float y, boolean disabled, float partialTick) {
        if (!move.id().equals("time_stop")) return;
        int duration = ActionCatalog.world() ? 30 : 40;
        float ratio = snapshot.charge() > 0 ? Math.clamp((duration - snapshot.charge() + partialTick) / duration, 0F, 1F) : 0;
        float alpha = disabled ? 0.25F : 1F;
        if (snapshot.charge() <= 0) alpha = Math.max(alpha * 0.75F, 1F / 63F);
        // Original renderRadialIndicator retains this baked-atlas path in its source.
        // These are the original 360 one-degree sprites, not a replacement progress graphic.
        int degrees = Math.clamp((int) (ratio * 360), 0, 360);
        OriginalHudDraw.blit(g, RADIAL_INDICATOR, x, y, degrees % 19 * 13, degrees / 19 * 13,
                13, 13, 256, OriginalHudDraw.color(0xFFFFFF, alpha));
    }

    /** Charging fill from ActionsOverlayGui.renderCrosshair, layered over the vanilla crosshair. */
    private static void renderCrosshair(GuiGraphicsExtractor g, StandHudState snapshot, float partialTick) {
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        int duration = ActionCatalog.world() ? 30 : 40;
        float ratio = Math.clamp((duration - snapshot.charge() + partialTick) / duration, 0F, 1F);
        float x = (g.guiWidth() - 15) / 2, y = (g.guiHeight() - 15) / 2;
        if (snapshot.charge() > 0 && ratio > 0) {
            float height = ratio == 1 ? 15 : 3 + 9 * ratio;
            OriginalHudDraw.blit(g, OVERLAY_LOCATION, x, y + 15 - height, 88, 30 - height, 15, height, 256, 0xFFFFFFFF);
        }
        // Original ElementTransparency(8, 6): two-tick fade-in, then six-tick fade-out and a 25% expansion.
        if (crosshairFlashTicks > 0) {
            float age = 8 - crosshairFlashTicks + partialTick;
            float alpha = Math.clamp(age <= 2 ? age / 2 : (crosshairFlashTicks - partialTick) / 6, 0, 1);
            float size = 15 * (1 + alpha * 0.25F);
            OriginalHudDraw.blit(g, OVERLAY_LOCATION, x + (15 - size) / 2, y + (15 - size) / 2,
                    88, 0, size, size, 15, 15, 256, OriginalHudDraw.color(0xFFFFFF, alpha));
        }
    }

    private static void renderArrowFeedback(GuiGraphicsExtractor g) {
        var client = Minecraft.getInstance();
        var arrow = client.player.getAttachedOrCreate(StandAttachments.ARROW);
        int x = 10, y = g.guiHeight() - 105;
        if (arrow.infected()) {
            g.text(client.font, Component.translatable("jojo.fabric.arrow.progress", arrow.paidLevels(), arrow.requiredLevels()), x, y - 11, 0xFFFF7777);
            g.text(client.font, Component.translatable("jojo.fabric.arrow.remaining", (arrow.remainingTicks() + 19) / 20), x, y + 1, 0xFFFFFFFF);
        } else if (client.player.getMainHandItem().getItem() instanceof StandArrowItem
                || client.player.getOffhandItem().getItem() instanceof StandArrowItem) {
            g.text(client.font, Component.translatable(client.player.isCreative() ? "jojo.fabric.arrow.creative" : "jojo.fabric.arrow.requirement", arrow.nextCost()), x, y - 11, 0xFFFFD45C);
            g.text(client.font, Component.translatable("jojo.fabric.arrow.use"), x, y + 1, 0xFFFFFFFF);
        }
    }
    private ActionHud() { }
}
