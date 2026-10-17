package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Original Pillar orange accent and energy bar, composed into the shared action overlay. */
public final class PillarmanHud {
    public static final int COLOR=0xFFAA00;
    private static final OriginalHudFade FADE=new OriginalHudFade(40,10);
    private static Object session;private static int lastTick,warning;private static boolean previousMode;
    public static void rejected(){warning=10;}
    public static Component name(String id){return Component.translatable("action.jojo."+id);}
    public static Component powerName(){return Component.translatable("non_stand.jojo.pillarman");}
    public static Component costText(String id){var action=PillarmanActionCatalog.get(id);float cost=action==null?0:action.cost(PillarmanClient.chargeTicks(id));return Component.literal(cost==Math.floor(cost)?Integer.toString((int)cost):Float.toString(cost));}
    public static int render(GuiGraphicsExtractor g,int x,int y,float partial){
        var client=Minecraft.getInstance();if(!PillarmanClient.playable())return 0;
        int tick=client.gui.hud.getGuiTicks();
        if(session!=client.level){session=client.level;lastTick=tick;FADE.clear();previousMode=false;warning=0;}
        int elapsed=Math.clamp(tick-lastTick,0,40);lastTick=tick;
        if(!client.isPaused())for(int i=0;i<elapsed;i++){FADE.tick();warning=Math.max(0,warning-1);}
        var state=PillarmanClient.state();boolean full=PillarmanClient.isMode();
        if(state.energy()<state.maximum()||full!=previousMode)FADE.reset();previousMode=full;
        int length=full?200:150,height=full?8:5;
        if(full||FADE.shouldRender()){
            float alpha=full?1:FADE.alpha(partial);int tint=OriginalHudDraw.color(COLOR,alpha);
            OriginalHudDraw.blit(g,ActionHud.OVERLAY_LOCATION,x+14,y+1,1,full?161:169,length*state.energy()/Math.max(1,state.maximum()),height-2,256,tint);
            OriginalHudDraw.blit(g,ActionHud.OVERLAY_LOCATION,x+13,y,0,full?128:136,length+2,height,256,tint);
            if(warning>0){float phase=warning-partial;OriginalHudDraw.fill(g,x+14,y+1,length,height-2,OriginalHudDraw.color(0xFF0000,OriginalHudDraw.highlightAlpha(phase,10,8,phase>5?.25F:0,.75F)));}
            OriginalHudDraw.blit(g,ActionHud.OVERLAY_LOCATION,x+14,y+1,1,full?145:153,length,height-2,256,OriginalHudDraw.color(0xFFFFFF,alpha));
            if(full)OriginalHudDraw.blit(g,ActionHud.OVERLAY_LOCATION,x,y-3,240,52,12,16,256,-1);
        }return full?12:9;
    }
    private PillarmanHud(){}
}
