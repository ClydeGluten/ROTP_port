package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.github.standobyte.jojo.core.PillarmanProgress.Mode;
import com.github.standobyte.jojo.fabric.client.ActionCatalog;
import java.util.*;

/** All22 active ModPillarmanActions entries, including the four source shift links. */
public final class PillarmanActionCatalog {
    public enum Route { BASE, WIND, POSSESSION }
    public record Action(String id,String packet,Route route,int stage,Mode mode,boolean held,int charge,
                         float energy,float perTick,int cooldown,boolean stone,boolean ignoreStun,boolean freeHand,String shift) {
        public boolean unlocked(PillarmanProgress state){return state.pillarman()&&state.stage()>=stage&&(mode==Mode.NONE||state.mode()==mode);}
        public float cost(int ticks){
            float ticking=(id.equals("pillarman_divine_sandstorm")||id.equals("pillarman_atmospheric_rift"))&&ticks>=40?3:perTick;
            return energy+ticking*(held?Math.max(1,charge-ticks):0);
        }
        public ActionCatalog.Move move(){return new ActionCatalog.Move(id,id,packet,stage,Math.round(cost(0)),held?1:0);}
    }
    private static final Map<String,Action> ACTIONS=new LinkedHashMap<>();
    private static void add(String suffix,String packet,Route route,int stage,Mode mode,boolean held,int charge,float cost,float tick,int cd,boolean stone,boolean stun,boolean hand,String shift){
        String id="pillarman_"+suffix;ACTIONS.put(id,new Action(id,packet,route,stage,mode,held,charge,cost,tick,cd,stone,stun,hand,shift.isEmpty()?"":"pillarman_"+shift));
    }
    static {
        add("heavy_punch","HEAVY_PUNCH",Route.BASE,1,Mode.NONE,false,0,10,0,0,false,false,true,"blade_slash");
        add("absorption","START_ABSORPTION",Route.BASE,2,Mode.NONE,true,0,0,0,0,true,true,true,"");
        add("horn_attack","HORN",Route.BASE,2,Mode.NONE,false,0,15,0,60,true,true,false,"");
        add("ribs_blades","RIBS",Route.BASE,2,Mode.NONE,false,0,60,0,80,false,false,false,"");
        add("hide_in_entity","START",Route.POSSESSION,2,Mode.NONE,true,20,0,0,0,false,false,false,"");
        add("stone_form","START_STONE_FORM",Route.BASE,1,Mode.NONE,true,40,0,0,0,true,true,false,"");
        add("regeneration","REGENERATION",Route.BASE,2,Mode.NONE,false,0,40,0,20,true,true,false,"");
        add("enhanced_senses","START_SENSES",Route.BASE,2,Mode.NONE,true,0,0,.05F,0,true,true,false,"");
        add("evasion","START_EVASION",Route.BASE,2,Mode.NONE,true,0,0,1.5F,50,false,false,false,"unnatural_agility");
        add("unnatural_agility","START_AGILITY",Route.BASE,2,Mode.NONE,true,0,0,1,0,false,false,false,"");
        add("small_sandstorm","SMALL_SANDSTORM",Route.WIND,1,Mode.WIND,false,0,20,0,10,false,false,false,"divine_sandstorm");
        add("divine_sandstorm","START_DIVINE",Route.WIND,1,Mode.WIND,true,40,0,0,0,false,false,false,"");
        add("wind_cloak","START_CLOAK",Route.WIND,1,Mode.WIND,true,0,0,2,100,false,false,false,"");
        add("atmospheric_rift","START_RIFT",Route.WIND,1,Mode.WIND,true,40,0,0,0,false,false,false,"");
        add("erratic_blaze_king","ERRATIC_BLAZE",Route.BASE,1,Mode.HEAT,false,0,20,0,0,false,false,false,"");
        add("giant_carthwheel_prison","START_GIANT_HEAT",Route.BASE,1,Mode.HEAT,true,30,125,0,100,false,false,false,"");
        add("self_detonation","START_DETONATION",Route.BASE,1,Mode.HEAT,true,60,150,0,0,false,false,false,"");
        add("light_flash","START_LIGHT_FLASH",Route.BASE,1,Mode.LIGHT,true,40,25,0,80,false,false,false,"light_flash_decoy");
        add("light_flash_decoy","START_LIGHT_DECOY",Route.BASE,1,Mode.LIGHT,true,50,40,0,120,false,false,false,"");
        add("blade_slash","BLADE_SLASH",Route.BASE,1,Mode.LIGHT,false,0,10,0,0,false,false,true,"");
        add("blade_dash_attack","START_BLADE_DASH",Route.BASE,1,Mode.LIGHT,true,10,50,0,0,false,false,false,"");
        add("blade_barrage","START_BLADE_BARRAGE",Route.BASE,1,Mode.LIGHT,true,0,0,1,0,false,false,true,"");
    }
    public static Action get(String id){return ACTIONS.get(id);}
    public static List<Action> all(){return List.copyOf(ACTIONS.values());}
    public static Action resolve(String id,PillarmanProgress state,boolean shift){
        var action=get(id);if(action!=null&&shift&&!action.shift.isEmpty()){
            var alternate=get(action.shift);if(alternate!=null&&alternate.unlocked(state))return alternate;
        }return action;
    }
    public static List<String> attacks(PillarmanProgress state){
        var ids=new ArrayList<String>();ids.add("pillarman_heavy_punch");
        if(state.stage()>1)ids.addAll(List.of("pillarman_absorption","pillarman_horn_attack","pillarman_ribs_blades"));
        switch(state.mode()){
            case WIND -> ids.addAll(List.of("pillarman_small_sandstorm","pillarman_atmospheric_rift"));
            case HEAT -> ids.addAll(List.of("pillarman_erratic_blaze_king","pillarman_giant_carthwheel_prison","pillarman_self_detonation"));
            case LIGHT -> ids.addAll(List.of("pillarman_blade_dash_attack","pillarman_blade_barrage"));
            default -> {}
        }return List.copyOf(ids);
    }
    public static List<String> abilities(PillarmanProgress state){
        var ids=new ArrayList<String>();ids.add("pillarman_stone_form");
        if(state.stage()>1)ids.addAll(List.of("pillarman_regeneration","pillarman_enhanced_senses","pillarman_hide_in_entity","pillarman_evasion"));
        if(state.mode()==Mode.WIND)ids.add("pillarman_wind_cloak");
        if(state.mode()==Mode.LIGHT)ids.add("pillarman_light_flash");
        return List.copyOf(ids);
    }
    public static String defaultMiddleAction(){return "pillarman_absorption";}
    private PillarmanActionCatalog(){}
}
