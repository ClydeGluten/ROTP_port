package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.core.HamonProgress;
import com.github.standobyte.jojo.core.HamonSkills;
import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** HamonScreen and its original five tabs, using the modern extracted GUI renderer. */
public final class HamonScreen extends Screen {
    public static final int WINDOW_WIDTH = 230, WINDOW_HEIGHT = 227;
    private static final Identifier WINDOW = RipplesFabric.id("textures/gui/hamon_window.png");
    private static final Identifier SKILLS = RipplesFabric.id("textures/gui/hamon_window_2.png");
    private static final Identifier TABS = RipplesFabric.id("textures/gui/screen_tabs.png");
    private static final Identifier BACKGROUND = RipplesFabric.id("textures/gui/advancements/jojo.png");
    private static final String[] TITLES = {"hamon.intro.tab", "hamon.stats.tab", "hamon.strength_skills.tab", "hamon.control_skills.tab", "hamon.techniques.tab", "hamon.abandon.tab"};
    private static final String[][] TREES = {{"overdrive", "infusion", "flexibility"}, {"life", "attractant_repellent", "body_manipulation"}};
    private static final HamonSkills.Branch[][] BRANCHES = {{HamonSkills.Branch.OVERDRIVE, HamonSkills.Branch.INFUSION, HamonSkills.Branch.FLEXIBILITY},
            {HamonSkills.Branch.HEALING, HamonSkills.Branch.ATTRACTANT_REPELLENT, HamonSkills.Branch.BODY_MANIPULATION}};
    private record Area(int x, int y, int w, int h, Runnable click, List<Component> tooltip) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Description(List<FormattedCharSequence> lines, int height) {
        boolean scrolling() { return height > 44; }
        int maxScroll() { return Math.max(0, height - 44); }
    }
    private record Scrollbar(double x, double y, double height) {
        boolean contains(double mx, double my) { return mx >= x && mx <= x + 3 && my >= y && my <= y + height; }
    }
    private final List<Area> areas = new ArrayList<>();
    private final int[] scroll = new int[6];
    private int tab, ticks, contentHeight;
    private double descriptionScroll;
    private boolean descriptionDragged, descriptionScrollbarDragged;
    private String selected = "";
    private String lastClicked = "";
    private int lastClickTick = -100;
    private boolean extraBreath;
    private boolean initialized;
    private Button learn, reset, abandon, yes, no, layout, stand;
    private final Map<String, Button> techniqueButtons = new LinkedHashMap<>();

    public HamonScreen() { super(Component.translatable("non_stand.jojo.hamon")); }
    private int wx() { return (width - WINDOW_WIDTH) / 2; }
    private int wy() { return (height - WINDOW_HEIGHT) / 2; }
    private HamonProgress state() { return HamonClient.state(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        if (!initialized) { tab = HamonClient.introRead() ? 1 : 0; initialized = true; HamonClient.request(HamonPayload.Operation.OPEN, ""); }
        learn = button("hamon.learnButton", 150, 92, 64, () -> learnSelected());
        reset = button("hamon.resetButton", 16, 92, 64, () -> HamonClient.request(HamonPayload.Operation.RESET, tab == 2 ? "strength" : tab == 3 ? "control" : "technique"));
        abandon = button("hamon.abandon.tab", 13, 999, 204, () -> selectTab(5));
        yes = button("gui.yes", 13, 96, 100, () -> { HamonClient.request(HamonPayload.Operation.ABANDON, ""); onClose(); });
        no = button("gui.no", 117, 96, 100, () -> selectTab(1));
        layout = button("jojo.screen.edit_hud_layout", 230, 28, 28, () -> minecraft.gui.setScreen(new ActionLayoutScreen(this)));
        stand = button("jojo.fabric.progress.title", 32, -28, 28, () -> minecraft.gui.setScreen(new ProgressionScreen()));
        techniqueButtons.clear();
        for (var technique : HamonSkills.techniques()) techniqueButtons.put(technique.id(), button("hamon.pick_technique", 16, 999, 80,
                () -> HamonClient.request(HamonPayload.Operation.TECHNIQUE, technique.id())));
        updateButtons();
    }
    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.translatable(label), b -> action.run()).bounds(wx() + x, wy() + y, w, 20).build());
    }
    private void selectTab(int value) {
        if (value != 0 && !HamonClient.introRead()) HamonClient.request(HamonPayload.Operation.READ_INTRO, "");
        tab = value; selected = ""; descriptionScroll = 0;
        descriptionDragged = descriptionScrollbarDragged = false; updateButtons();
    }
    private void selectSkill(String id) {
        var skill = HamonSkills.skill(id);
        if (skill == null) return;
        int owner = skill.base() ? skill.stat() == HamonSkills.Stat.STRENGTH ? 2 : 3 : 4;
        if (tab != owner) selectTab(owner);
        selected = id; scroll[tab] = 0; descriptionScroll = 0;
        descriptionDragged = descriptionScrollbarDragged = false; updateButtons();
    }
    private void learnSelected() {
        if (!selected.isEmpty() && HamonClient.learnReason(selected).isEmpty()) HamonClient.request(HamonPayload.Operation.LEARN, selected);
    }
    private void updateButtons() {
        boolean skills = tab >= 2 && tab <= 4;
        learn.visible = skills && !selected.isEmpty() && !state().hasSkill(selected);
        learn.active = learn.visible && HamonClient.learnReason(selected).isEmpty();
        reset.visible = skills && minecraft.player != null && minecraft.player.isCreative();
        abandon.visible = tab == 1;
        yes.visible = no.visible = tab == 5;
        stand.visible = minecraft.player != null && minecraft.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand();
        layout.visible = true;
        techniqueButtons.values().forEach(b -> b.visible = false);
        learn.setY(wy() + 92 - scroll[tab]); reset.setY(wy() + 92 - scroll[tab]);
    }
    @Override public void tick() {
        ticks++;
        if (!HamonClient.playable()) { onClose(); return; }
        if (ticks % 20 == 0) HamonClient.request(HamonPayload.Operation.OPEN, "");
        updateButtons();
    }
    @Override public Component getNarrationMessage() {
        return Component.translatable(TITLES[tab]).append(". ").append(Component.translatable("hamon.strength_level", state().strengthLevel(), 60))
                .append(". ").append(Component.translatable("hamon.control_level", state().controlLevel(), 60));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.player == null) return;
        updateButtons(); areas.clear();
        int x = wx() + 9, y = wy() + 18;
        g.enableScissor(x, y, x + 212, y + 200);
        for (int tx = 0; tx < 14; tx++) for (int ty = -1; ty < 14; ty++) blit(g, BACKGROUND, x + tx * 16, y + ty * 16 - scroll[tab] % 16, 0, 0, 16, 16, 16);
        int oy = y - scroll[tab];
        contentHeight = switch (tab) {
            case 0 -> intro(g, x, oy, partialTick);
            case 1 -> stats(g, x, oy);
            case 2, 3 -> skills(g, x, oy, mouseX, mouseY);
            case 4 -> techniques(g, x, oy, mouseX, mouseY);
            default -> abandon(g, x, oy);
        };
        scroll[tab] = Math.clamp(scroll[tab], 0, Math.max(0, contentHeight - 200));
        // Widgets are clipped to the exact original content window; side category controls are drawn separately.
        for (var widget : List.of(learn, reset, abandon, yes, no)) if (widget.visible) widget.extractRenderState(g, mouseX, mouseY, partialTick);
        for (var widget : techniqueButtons.values()) if (widget.visible) widget.extractRenderState(g, mouseX, mouseY, partialTick);
        g.disableScissor();
        blit(g, WINDOW, wx(), wy(), 0, 0, 230, 227, 256);
        int count = HamonClient.techniqueSlots() > 0 ? 5 : 4;
        for (int i = 0; i < count; i++) {
            blit(g, TABS, wx() - 28, wy() + i * 28, i == 0 ? 0 : i == 4 ? 64 : 32, tab == i ? 92 : 64, 32, 28, 256);
            final int target = i;
            areas.add(new Area(wx() - 28, wy() + i * 28, 28, 28, () -> selectTab(target), List.of(Component.translatable(TITLES[i]))));
            int ix = wx() - 18, iy = wy() + i * 28 + 5;
            if (i == 1) OriginalHudDraw.blit(g, SKILLS, ix, iy, 192, 0, 16, 16, 32, 32, 256, -1);
            if (i == 2 || i == 3) {
                OriginalHudDraw.blit(g, SKILLS, ix, iy, 128, i == 2 ? 0 : 64, 16, 16, 64, 64, 256, -1);
                if (state().skillPoints(i == 2 ? HamonSkills.Stat.STRENGTH : HamonSkills.Stat.CONTROL) > 0)
                    blit(g, WINDOW, ix - 6, iy - 3, HamonClient.teacherNearby() ? 248 : 239, 206, 8, 8, 256);
            }
        }
        g.text(font, Component.translatable(TITLES[tab]), wx() + 8, wy() + 6, 0xFF404040, false);
        blit(g,TABS,wx()+226,wy(),96,92,32,28,256);
        blit(g,TABS,wx()+226,wy()+28,128,64,32,28,256);
        OriginalHudDraw.blit(g,RipplesFabric.id("textures/power/hamon.png"),wx()+232,wy()+6,0,0,16,16,16,-1);
        blit(g,TABS,wx()+232,wy()+34,240,240,16,16,256);
        if (layout.isMouseOver(mouseX,mouseY)) g.setTooltipForNextFrame(font,key("jojo.key.edit_hud"),mouseX,mouseY);
        if (stand.visible) {
            blit(g,TABS,wx()+32,wy()-28,32,2,28,32,256);
            String power=ActionCatalog.world()?"the_world":"star_platinum";
            OriginalHudDraw.blit(g,RipplesFabric.id("textures/power/"+power+".png"),wx()+38,wy()-19,0,0,16,16,16,-1);
            if(stand.isMouseOver(mouseX,mouseY))g.setTooltipForNextFrame(font,key("jojo.stand_ui.name"),mouseX,mouseY);
        }
        for (var area : areas) if (area.contains(mouseX, mouseY) && !area.tooltip().isEmpty() && (inside(mouseX, mouseY) || mouseX < wx())) {
            g.setTooltipForNextFrame(font, area.tooltip(), Optional.empty(), mouseX, mouseY); break;
        }
        if (learn.visible && !learn.active && learn.isMouseOver(mouseX, mouseY)) {
            String reason = HamonClient.learnReason(selected);
            if (!reason.isEmpty()) g.setTooltipForNextFrame(font, Component.translatable(reason), mouseX, mouseY);
        }
    }
    private int paragraph(GuiGraphicsExtractor g, Component text, int x, int y, int width) {
        for (var line : font.split(text, width)) { g.text(font, line, x, y, -1, false); y += 9; }
        return y;
    }
    private Component key(String key, Object... args) { return Component.translatable(key, args); }
    private Component underlined(String key) { return Component.translatable(key).withStyle(ChatFormatting.UNDERLINE); }
    private int intro(GuiGraphicsExtractor g, int x, int y, float partial) {
        int cy = y + 6;
        cy = paragraph(g, key("hamon.intro.about.name"), x + 2, cy, 200) + 2;
        cy = paragraph(g, key("hamon.intro.about.text"), x + 5, cy, 200) + 15;
        cy = paragraph(g, key("hamon.intro.breath.name"), x + 2, cy, 200) + 2;
        cy = paragraph(g, key("hamon.intro.breath.text1", underlined("hamon.intro.breath.text1.underlined")), x + 5, cy, 200) + 2;
        HamonHud.renderEnergyBar(g, x + 5, cy, 200, 1, 0, 1); cy += 22;
        cy = paragraph(g, key("hamon.intro.breath.text2", underlined("hamon.intro.breath.text2.underlined")), x + 5, cy, 200);
        cy = paragraph(g, key("hamon.intro.breath.text3", underlined("hamon.intro.breath.text3.underlined")), x + 5, cy, 200) + 8;
        OriginalHudDraw.blit(g, RipplesFabric.id("textures/action/hamon_breath.png"), x + 98, cy, 0, 0, 16, 16, 32, 32, 32, -1);
        cy += 24;
        HamonHud.renderEnergyBar(g, x + 5, cy, 200, 1, Math.clamp(((ticks + partial) % 100 - 20) / 60, 0, 1), 1); cy += 22;
        g.text(font, Component.literal(extraBreath ? "▼" : "▶"), x + 5, cy, -1, false);
        int toggleY = cy;
        cy = paragraph(g, key("hamon.intro.breath.stability_hidden").copy().withStyle(ChatFormatting.ITALIC), x + 19, cy, 186);
        areas.add(new Area(x + 3, toggleY, 204, cy - toggleY + 3, () -> extraBreath = !extraBreath, List.of()));
        if (extraBreath) {
            cy = paragraph(g, key("hamon.intro.breath.text4", underlined("hamon.intro.breath.text4.underlined")), x + 5, cy + 3, 200) + 3;
            float t = (ticks + partial) % 750, stability = Math.min(1, .4F + .6F * t / 720);
            HamonHud.renderEnergyBar(g, x + 5, cy, 200, stability, Math.clamp((t - 20) / 60, 0, stability), 1); cy += 17;
            cy = paragraph(g, key("hamon.intro.breath.text5"), x + 5, cy, 200);
        }
        return paragraph(g, key("hamon.intro.stats_transition"), x + 5, cy + 15, 200) - y + 15;
    }
    private int stats(GuiGraphicsExtractor g, int x, int y) {
        var p = state(); int cy = y + 6;
        cy = stat(g, x, cy, "strength", p.strengthLevel(), p.strengthPoints(), 234);
        cy = stat(g, x, cy + 15, "control", p.controlLevel(), p.controlPoints(), 239) + 15;
        g.text(font, key("hamon.breathing_level", (int)p.breathingLevel(), 100), x + 2, cy, -1, false);
        statBar(g, x + 153, cy, p.breathingLevel() >= 100 ? 1 : p.breathingLevel() % 1, p.breathingLevel() >= 100 ? 249 : 244);
        cy = paragraph(g, key("hamon.breathing_stat.desc").copy().append(key("hamon.breathing_stat.desc.gap")), x + 5, cy + 11, 200) + 11;
        int completed = 0;
        for (var exercise : HamonProgress.Exercise.values()) {
            int col = exercise.ordinal() % 2, row = exercise.ordinal() / 2;
            int ex = x + 15 + col * 96, ey = cy + row * 9;
            float ratio = Math.clamp((float)p.exerciseTicks(exercise) / Math.max(1, p.exerciseMaxTicks(exercise)), 0, 1);
            if (ratio >= 1) completed++;
            blit(g, WINDOW, ex + 1, ey + 1, 93, 250, (int)(90 * ratio), 5, 256);
            blit(g, WINDOW, ex, ey, 0, 249, 92, 7, 256);
            OriginalHudDraw.blit(g, WINDOW, ex - 3, ey - 1, 230, 92 + exercise.ordinal() * 16, 8, 8, 16, 16, 256, -1);
            OriginalHudDraw.blit(g, WINDOW, ex + 85, ey - 1, 230, 188, 8, 8, 16, 16, 256, ratio >= 1 ? -1 : 0xFF000000);
            g.centeredText(font, key("hamon." + exercise.name().toLowerCase(Locale.ROOT) + "_exercise"), ex + 45, ey, -1);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(key("hamon." + exercise.name().toLowerCase(Locale.ROOT) + "_exercise"));
            tooltip.add(Component.literal(p.exerciseTicks(exercise) / 20 + " / " + p.exerciseMaxTicks(exercise) / 20 + " s"));
            if(ratio<1)tooltip.add(key("hamon.exercise.completion_buff_hint"));
            tooltip.add(key("hamon.exercise."+exercise.name().toLowerCase(Locale.ROOT)+".completion_buff", exercise==HamonProgress.Exercise.MEDITATION?"1":exercise==HamonProgress.Exercise.MINING?"5":"10", key("hamon.exercise.completion_buff_hint2")));
            if (exercise == HamonProgress.Exercise.MEDITATION) { tooltip.add(key("hamon.meditation_button")); tooltip.add(key("hamon.meditation_button.stability_hint")); }
            areas.add(new Area(ex, ey - 1, 92, 10, () -> { if (exercise == HamonProgress.Exercise.MEDITATION) { HamonClient.request(HamonPayload.Operation.MEDITATE, ""); onClose(); } }, tooltip));
        }
        cy += 20;
        int fill=48*completed, incomplete=(int)(48*p.maxIncompleteExercise());
        blit(g, WINDOW, x + 13, cy + 1, 1, 234, fill, 5, 256);
        blit(g, WINDOW, x + 13+fill, cy + 1, 1+fill, 239, incomplete, 5, 256);
        blit(g, WINDOW, x + 13+fill+incomplete, cy + 1, 1+fill+incomplete, 244, 192-fill-incomplete, 5, 256);
        blit(g, WINDOW, x + 12, cy, 0, 227, 194, 7, 256);
        OriginalHudDraw.blit(g, WINDOW, x+198,cy-1,230,188,8,8,16,16,256,completed==4?-1:0xFF000000);
        float increase = HamonClient.dailyIncrease();
        g.centeredText(font, Component.literal(String.format(Locale.ROOT, "%+.2f", increase)), x + 106, cy + 9, increase >= 0 ? 0xFF55FF55 : 0xFFFF5555);
        var dailyTips=new ArrayList<Component>();dailyTips.add(key("hamon.exercise.all.count",completed));
        if(increase!=0)dailyTips.add(key(increase>0?"hamon.exercise.all.day_end_increase":"hamon.exercise.all.day_end_decrease",String.format(Locale.ROOT,"%.2f",Math.abs(increase))));
        else if(p.skipDays()>0)dailyTips.add(key("hamon.exercise.can_skip",p.skipDays()));
        if(p.trainingBonus()>0){blit(g,WINDOW,x+3,cy-1,increase>0?230:239,216,8,8,256);dailyTips.add(key("hamon.training_bonus",p.trainingBonus()));}
        dailyTips.add(key("hamon.exercise.full_completion_buff",5,key("hamon.exercise.completion_buff_hint2")));
        dailyTips.add(key("hamon.exercise.full_completion_hint3"));
        areas.add(new Area(x + 3, cy, 203, 8, () -> {}, dailyTips));
        cy = paragraph(g, key("hamon.breathing_stat.desc2", underlined("hamon.breathing_stat.desc2.mask")), x + 5, cy + 25, 200);
        if(HamonConfig.current(true).breathingTrainingDeterioration())cy = paragraph(g, key("hamon.breathing_stat.desc3"), x + 5, cy + 4, 200);
        if(HamonClient.statGap()>=0)cy = paragraph(g, key("hamon.breathing_stat.desc4", HamonClient.statGap()), x + 5, cy + 4, 200);
        abandon.setY(cy + 12); return cy - y + 43;
    }
    private int stat(GuiGraphicsExtractor g, int x, int y, String name, int level, int points, int v) {
        g.text(font, key("hamon." + name + "_level", level, 60), x + 2, y, -1, false);
        float fraction = HamonClient.levelFraction(points, level);
        statBar(g, x + 153, y, fraction, v);
        areas.add(new Area(x + 153, y, 52, 8, () -> {}, List.of(Component.literal(Integer.toString(points)))));
        return paragraph(g, key("hamon." + name + "_stat.desc"), x + 5, y + 11, 200);
    }
    private void statBar(GuiGraphicsExtractor g, int x, int y, float fraction, int v) {
        blit(g, WINDOW, x + 1, y + 1, 203, v, Math.round(50 * fraction), 5, 256);
        blit(g, WINDOW, x, y, 202, 227, 52, 7, 256);
    }
    private int skills(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        var stat = tab == 2 ? HamonSkills.Stat.STRENGTH : HamonSkills.Stat.CONTROL;
        description(g, x, y, stat, mouseX, mouseY);
        for (int tree = 0; tree < 3; tree++) {
            int center = x + 38 + tree * 68;
            int titleY = y + 96;
            for (var line : font.split(key("hamon.skills." + TREES[tab - 2][tree]), 75)) { g.centeredText(font, line, center, titleY, -1); titleY += 9; }
            var branch = BRANCHES[tab - 2][tree];
            for (var skill : HamonSkills.all()) if (skill.base() && skill.branch() == branch) {
                int tier = skill.tier();
                var peers = HamonSkills.all().stream().filter(s -> s.base() && s.branch() == branch && s.tier() == tier).toList();
                int index = peers.indexOf(skill), grid = peers.size() == 1 ? 1 : index * (int)Math.ceil(3F / peers.size());
                skill(g, skill, x + 12 + tree * 68 + grid * 13, y + 114 + tier * (tier < 2 ? 26 : 27), tier == 2);
            }
        }
        return 200;
    }
    private Description description() {
        var text = key("hamonSkill." + selected + ".desc");
        var lines = font.split(text, 192);
        int height = 3 + lines.size() * font.lineHeight;
        if (height > 44) {
            lines = font.split(text, 186);
            height = 6 + lines.size() * font.lineHeight;
        }
        return new Description(lines, height);
    }
    private Scrollbar descriptionScrollbar(Description description) {
        double height = 41.0 / description.height() * 38;
        return new Scrollbar(wx() + 208, wy() + 46 - scroll[tab] + descriptionScroll / description.maxScroll() * (38 - height), height);
    }
    private boolean overDescription(double x, double y) {
        return !selected.isEmpty() && inside(x, y) && x > wx() + 16 && x <= wx() + 214
                && y > wy() + 43 - scroll[tab] && y <= wy() + 87 - scroll[tab];
    }
    private void scrollDescription(double movement) {
        descriptionScroll = Math.clamp(descriptionScroll + movement, 0, description().maxScroll());
    }
    private void description(GuiGraphicsExtractor g, int x, int y, HamonSkills.Stat stat, int mouseX, int mouseY) {
        var chosen = HamonSkills.skill(selected);
        if (chosen == null) {
            if (stat != null) {
                g.text(font, key(stat == HamonSkills.Stat.STRENGTH ? "hamon.strength_level" : "hamon.control_level", stat == HamonSkills.Stat.STRENGTH ? state().strengthLevel() : state().controlLevel(), 60), x + 6, y + 5, -1, false);
                Component pts = key("hamon.skill_points", state().skillPoints(stat));
                g.text(font, pts, x + 206 - font.width(pts), y + 5, -1, false);
                paragraph(g, key(TITLES[tab] + ".desc"), x + 6, y + 22, 200);
            } else paragraph(g, key("hamon.techniques.tab.desc", HamonClient.mixTechniques() ? "" : key("hamon.techniques.tab.desc.only_one")), x + 6, y + 5, 200);
            return;
        }
        icon(g, chosen.id(), x + 4, y + 4, 16, -1);
        paragraph(g, skillName(chosen), x + 24, y + 5, 180);
        var description = description();
        descriptionScroll = Math.clamp(descriptionScroll, 0, description.maxScroll());
        blit(g, SKILLS, x + 4, y + 22, 52, 206, 204, 50, 256);
        blit(g, SKILLS, x + 4, y + 22, 52, 156, 204, 50, 256);
        if (description.scrolling()) {
            var bar = descriptionScrollbar(description);
            int brightness = descriptionScrollbarDragged ? 255 : bar.contains(mouseX, mouseY) ? 191 : 127;
            g.fill((int)bar.x(), (int)bar.y(), (int)bar.x() + 3, (int)Math.ceil(bar.y() + bar.height()),
                    0x7F000000 | brightness << 16 | brightness << 8 | brightness);
        }
        g.enableScissor(Math.max(wx()+9,x + 7), Math.max(wy()+18,y + 25), Math.min(wx()+221,x + 205), Math.min(wy()+218,y + 69));
        int lineY = y + 28 - (int)descriptionScroll;
        for (var line : description.lines()) { g.text(font, line, x + 10, lineY, -1, false); lineY += font.lineHeight; }
        g.disableScissor(); // Popping the description clip restores the enclosing content clip.
        int px = x + 135;
        for (String parent : chosen.prerequisites()) {
            px -= 18; icon(g, parent, px, y + 77, 16, state().hasSkill(parent) ? 0x66FFFFFF : 0xFF000000);
            var required = HamonSkills.skill(parent);
            areas.add(new Area(px, y + 77, 16, 16, () -> selectSkill(parent), List.of(required == null ? Component.literal(parent) : skillName(required))));
        }
    }
    private int techniques(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        if (state().technique().isEmpty() && !HamonClient.canPickTechnique()) {
            paragraph(g, key("hamon.techniques_locked", HamonClient.techniqueRequirement(0)), x + 6, y + 5, 200);
            return 200;
        }
        description(g, x, y, null, mouseX, mouseY);
        int count = HamonClient.techniqueSlots();
        List<String> learned = state().learnedSkills().stream().filter(id -> { var s = HamonSkills.skill(id); return s != null && !s.base() && !s.perk(); }).toList();
        var learnedSkills = learned.iterator();
        boolean freeSlot = true;
        for (int i = 0; i < count; i++) {
            int row = i / 7, rowSize = Math.min(7, count - row * 7);
            int sx = x + (int)((i % 7 + .5F) * 212 / rowSize) - 14, sy = y + 97 + row * 32;
            boolean unlocked = Math.min(state().strengthLevel(),state().controlLevel()) >= HamonClient.techniqueRequirement(i);
            String slotSkill = unlocked && learnedSkills.hasNext() ? learnedSkills.next() : "";
            boolean nextFree = unlocked && slotSkill.isEmpty() && freeSlot;
            if (unlocked && slotSkill.isEmpty()) freeSlot = false;
            blit(g, SKILLS, sx, sy, 0, 156, 28, 28, 256);
            blit(g, SKILLS, sx+1, sy+1, unlocked ? 0 : 52, !slotSkill.isEmpty() ? 52 : nextFree ? 26 : 0, 26, 26, 256);
            if (!slotSkill.isEmpty()) icon(g, slotSkill, sx + 6, sy + 6, 16, -1);
            List<Component> tooltip = !unlocked ? List.of(key("hamon.technique_slot.locked", HamonClient.techniqueRequirement(i)))
                    : !slotSkill.isEmpty() ? List.of(skillName(HamonSkills.skill(slotSkill)))
                    : nextFree ? List.of(key("hamon.technique_slot.free")) : List.of();
            areas.add(new Area(sx + 1, sy + 1, 26, 26, () -> { if (!slotSkill.isEmpty()) selectSkill(slotSkill); }, tooltip));
        }
        int cy = y + 135 + Math.max(0, (count - 1) / 7) * 32;
        for (var technique : HamonSkills.techniques()) {
            if (!state().technique().isEmpty() && !HamonClient.mixTechniques() && !technique.id().equals(state().technique())) continue;
            var names = font.split(key("hamon.technique." + technique.id()), 192);
            int boxHeight = Math.max(names.size() * 9 + 26, 36);
            boolean chosen = technique.id().equals(state().technique());
            g.fill(x + 8, cy, x + 204, cy + boxHeight, chosen ? 0x80101000 : 0x80100010);
            g.outline(x + 8, cy, 196, boxHeight, chosen ? 0x5050FF00 : 0x505000FF);
            int perkX = x + 8;
            for (var perk : technique.perks()) {
                icon(g, perk, perkX, cy, 16, chosen ? 0x66FFFFFF : 0xFF000000);
                var s = HamonSkills.skill(perk);
                areas.add(new Area(perkX, cy, 16, 16, () -> {}, List.of(skillName(s), key("hamonSkill." + perk + ".desc")))); perkX += 18;
            }
            paragraph(g, key("hamon.technique." + technique.id()), perkX + 3, cy + 1, 204 - (perkX - x));
            int i = 0;
            for (String skill : technique.skills()) {
                var definition = HamonSkills.skill(skill);
                if (definition != null) skill(g, definition, x + 209 - (technique.skills().size() - i) * 28, cy + names.size() * 9 + 4, false);
                i++;
            }
            var button = techniqueButtons.get(technique.id());
            button.visible = state().technique().isEmpty(); button.active = HamonClient.canPickTechnique(); button.setY(cy + boxHeight - 19);
            cy += boxHeight + 10;
        }
        return cy - y;
    }
    private void skill(GuiGraphicsExtractor g, HamonSkills.Skill skill, int x, int y, boolean last) {
        boolean learned = state().hasSkill(skill.id()), available = HamonClient.learnReason(skill.id()).isEmpty();
        int v = (last ? 78 : 0) + (learned ? 52 : available ? 26 : 0);
        blit(g, SKILLS, x, y, 0, v, 26, 26, 256);
        if (selected.equals(skill.id())) blit(g, SKILLS, x, y, 26, last ? 78 : 0, 26, 26, 256);
        var selectedDefinition = HamonSkills.skill(selected);
        if (selectedDefinition != null && selectedDefinition.prerequisites().contains(skill.id()) && !learned) blit(g, SKILLS, x, y, 26, last ? 104 : 26, 26, 26, 256);
        icon(g, skill.id(), x + 5, y + 5, 16, -1);
        List<Component> tips = new ArrayList<>(); tips.add(skillName(skill));
        for (String parent : skill.prerequisites()) if (!state().hasSkill(parent)) tips.add(skillName(HamonSkills.skill(parent)).copy().withStyle(ChatFormatting.RED));
        areas.add(new Area(x, y, 26, 26, () -> {
            selectSkill(skill.id());
            if (lastClicked.equals(selected) && ticks - lastClickTick <= 7) learnSelected();
            lastClicked = selected; lastClickTick = ticks; updateButtons();
        }, tips));
    }
    private Component skillName(HamonSkills.Skill skill) { return skill == null ? Component.empty() : key("hamonSkill." + skill.id() + ".name"); }
    private void icon(GuiGraphicsExtractor g, String id, int x, int y, int size, int color) {
        OriginalHudDraw.blit(g, RipplesFabric.id("textures/hamon/" + id + ".png"), x, y, 0, 0, size, size, 16, 16, 16, color);
    }
    private int abandon(GuiGraphicsExtractor g, int x, int y) {
        int cy = paragraph(g, key("hamon.abandon.tab.desc"), x + 5, y + 11, 200);
        paragraph(g, key("hamon.abandon.tab.desc2"), x + 5, cy + 18, 200); return 200;
    }
    private static void blit(GuiGraphicsExtractor g, Identifier texture, int x, int y, int u, int v, int w, int h, int size) { OriginalHudDraw.blit(g, texture, x, y, u, v, w, h, size, -1); }
    private boolean inside(double x, double y) { return x >= wx()+9 && x < wx()+221 && y >= wy()+18 && y < wy()+218; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        descriptionDragged = descriptionScrollbarDragged = false;
        if ((inside(event.x(), event.y()) || event.x() >= wx()+230 || stand.visible && stand.isMouseOver(event.x(),event.y())) && super.mouseClicked(event, doubleClick)) return true;
        if (overDescription(event.x(), event.y())) {
            var description = description();
            if (description.scrolling()) {
                descriptionScrollbarDragged = descriptionScrollbar(description).contains(event.x(), event.y());
                descriptionDragged = !descriptionScrollbarDragged;
                return true;
            }
        }
        for (var area : areas) if (area.contains(event.x(), event.y()) && (inside(event.x(), event.y()) || event.x() < wx())) { area.click().run(); return true; }
        if (inside(event.x(), event.y())) { selected = ""; descriptionScroll = 0; updateButtons(); return true; }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!inside(x,y)) return false;
        if (overDescription(x, y)) scrollDescription(-vertical * 2.5);
        else scroll[tab] = Math.clamp(scroll[tab] - (int)(vertical * 16), 0, Math.max(0, contentHeight-200));
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && (descriptionDragged || descriptionScrollbarDragged)) {
            scrollDescription(descriptionScrollbarDragged ? dy : -dy); return true;
        }
        if (inside(event.x(), event.y())) { scroll[tab] = Math.clamp(scroll[tab] - (int)dy, 0, Math.max(0, contentHeight-200)); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && (descriptionDragged || descriptionScrollbarDragged)) {
            descriptionDragged = descriptionScrollbarDragged = false; return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (HamonClient.screenKeyMatches(event)) { onClose(); return true; }
        if (event.key() == InputConstants.KEY_DOWN) { scroll[tab] = Math.min(Math.max(0,contentHeight-200), scroll[tab]+16); return true; }
        if (event.key() == InputConstants.KEY_UP) { scroll[tab] = Math.max(0,scroll[tab]-16); return true; }
        return super.keyPressed(event);
    }
}
