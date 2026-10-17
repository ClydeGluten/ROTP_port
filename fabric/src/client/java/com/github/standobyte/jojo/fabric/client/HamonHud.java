package com.github.standobyte.jojo.fabric.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Original energy fill and translucent breath-stability capacity from HorizontalBarsRenderer. */
public final class HamonHud {
    public static final int COLOR = 0xFFFF00;
    private static int warningTicks;
    private static int lastTick;
    public static void rejected() { warningTicks = 10; }
    static void render(GuiGraphicsExtractor g, int x, int y, float partialTick) {
        if (!HamonClient.playable()) return;
        var p = HamonClient.state();
        boolean full = HamonClient.isMode();
        int tick = net.minecraft.client.Minecraft.getInstance().gui.hud.getGuiTicks();
        warningTicks = Math.max(0, warningTicks - Math.clamp(tick-lastTick,0,40)); lastTick = tick;
        renderEnergyBar(g,x+13,y,full ? 200 : 150,p.breathStability()/Math.max(1,p.maxBreathStability()),p.energy()/Math.max(1,p.maxBreathStability()),full ? 1 : .75F);
        if (full) OriginalHudDraw.blit(g,ActionHud.OVERLAY_LOCATION,x,y-3,144,0,12,16,256,-1);
        if (warningTicks > 0) OriginalHudDraw.fill(g,x+14,y+1,full ? 200 : 150,6,OriginalHudDraw.color(0xFF0000,warningTicks/20F));
    }
    static void renderEnergyBar(GuiGraphicsExtractor g, int x,int y,int length,float stability,float energy,float alpha) {
        OriginalHudDraw.blit(g,ActionHud.OVERLAY_LOCATION,x,y,0,128,length+2,8,256,OriginalHudDraw.color(COLOR,alpha));
        OriginalHudDraw.blit(g,ActionHud.OVERLAY_LOCATION,x+1,y+1,1,161,length*Math.clamp(stability,0,1),6,256,OriginalHudDraw.color(COLOR,alpha*.4F));
        OriginalHudDraw.blit(g,ActionHud.OVERLAY_LOCATION,x+1,y+1,1,161,length*Math.clamp(energy,0,1),6,256,OriginalHudDraw.color(COLOR,alpha));
        OriginalHudDraw.blit(g,ActionHud.OVERLAY_LOCATION,x+1,y+1,1,145,length,6,256,OriginalHudDraw.color(0xFFFFFF,alpha));
    }
    private HamonHud() { }
}
