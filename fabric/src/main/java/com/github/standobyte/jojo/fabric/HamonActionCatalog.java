package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.HamonProgress;
import java.util.*;

/** Immutable registry contract from Forge ModHamonActions; costs are per source action. */
public final class HamonActionCatalog {
    public record Definition(String id,String skill,float energyCost,float holdEnergyCost,int holdTicks,
                             int chargeTicks,int cooldown,float walkSpeed,int freeHands,String shiftOf,boolean holdOnly) {
        public boolean held(){return holdOnly || holdTicks>0;}
    }
    private static final Map<String,Definition> ACTIONS=new LinkedHashMap<>();
    private static void add(String id,String skill,float cost,float tickCost,int hold,int charge,int cooldown,float walk,int hands,String shift,boolean only){
        ACTIONS.put(id,new Definition(id,skill,cost,tickCost,hold,charge,cooldown,walk,hands,shift,only));
    }
    static {
        add("hamon_overdrive","overdrive",600,0,0,0,0,1,1,"",false);
        add("hamon_overdrive_beat","overdrive",1500,0,0,0,0,.5F,2,"hamon_overdrive",false);
        add("hamon_sendo_overdrive","sendo_overdrive",900,0,30,30,0,1,1,"",false);
        add("hamon_turquoise_blue_overdrive","turquoise_blue_overdrive",1000,0,0,0,10,1,1,"",false);
        add("hamon_sunlight_yellow_overdrive","sunlight_yellow_overdrive",0,0,10,40,0,0,1,"",false);
        add("hamon_zoom_punch","zoom_punch",450,0,0,0,14,1,1,"",false);
        add("hamon_speed_boost","speed_boost",600,0,0,0,0,1,0,"",false);
        add("hamon_plant_infusion","plant_infusion",200,0,0,0,0,1,1,"",false);
        add("hamon_organism_infusion","animal_infusion",1000,0,0,0,0,1,1,"",false);
        add("hamon_breath","",0,0,0,0,0,0,0,"",true);
        add("hamon_healing","healing",0,5,0,0,0,.9999F,1,"",true);
        add("hamon_wall_climbing","wall_climbing",0,10,0,0,0,1,3,"",false);
        add("hamon_detector","detector",0,5,0,0,0,.5F,0,"",true);
        add("hamon_life_magnetism","life_magnetism",200,0,0,0,0,1,1,"",false);
        add("hamon_projectile_shield","projectile_shield",0,15,0,0,0,.3F,0,"",true);
        add("hamon_protection","protection",0,0,0,0,0,1,0,"",false);
        add("hamon_hypnosis","hypnosis",0,15,60,60,0,1,0,"",false);
        add("hamon_shock","hamon_shock",0,0,0,0,0,.25F,0,"",false);
        add("jonathan_scarlet_overdrive","scarlet_overdrive",0,0,8,32,0,0,2,"",false);
        add("jonathan_metal_silver_overdrive","metal_silver_overdrive",1000,0,0,0,0,1,1,"",false);
        add("jonathan_metal_silver_overdrive_weapon","metal_silver_overdrive",750,0,0,0,0,1,0,"",false);
        add("jonathan_overdrive_barrage","overdrive_barrage",0,70,0,0,0,.5F,3,"",true);
        add("jonathan_syo_barrage","syo_barrage",0,0,60,60,0,0,3,"jonathan_overdrive_barrage",false);
        add("zeppeli_hamon_cutter","hamon_cutter",400,0,0,0,0,1,0,"",false);
        add("zeppeli_sendo_wave_kick","sendo_wave_kick",1000,0,0,0,0,1,0,"",false);
        add("zeppeli_tornado_overdrive","tornado_overdrive",0,75,0,0,0,1,0,"",true);
        add("joseph_rebuff_overdrive","rebuff_overdrive",1200,0,0,0,100,0,3,"",false);
        add("caesar_bubble_launcher","bubble_launcher",0,50,0,0,0,.3F,0,"",true);
        add("caesar_bubble_barrier","bubble_barrier",0,50,20,20,0,.3F,0,"",false);
        add("caesar_bubble_cutter","bubble_cutter",500,0,0,0,10,1,0,"",false);
        add("caesar_bubble_cutter_gliding","bubble_cutter",600,0,0,0,10,1,0,"caesar_bubble_cutter",false);
    }
    public static Definition get(String id){return ACTIONS.get(normalize(id));}
    public static Collection<Definition> all(){return Collections.unmodifiableCollection(ACTIONS.values());}
    public static String normalize(String id){return id!=null && id.startsWith("jojo:")?id.substring(5):id;}
    public static boolean unlocked(HamonProgress state,String id){var a=get(id);return a!=null && state.hamon() && (a.skill().isEmpty() || state.hasSkill(a.skill()));}
    public static String shift(String id){
        id=normalize(id);
        if("jonathan_metal_silver_overdrive".equals(id))return "hamon_overdrive_beat";
        for(var a:ACTIONS.values())if(a.shiftOf().equals(id))return a.id();
        return id;
    }
    private static final List<String> ATTACKS=List.of("hamon_overdrive","hamon_sendo_overdrive","hamon_sunlight_yellow_overdrive","hamon_plant_infusion","hamon_zoom_punch","hamon_turquoise_blue_overdrive");
    private static final List<String> ABILITIES=List.of("hamon_breath","hamon_healing","hamon_speed_boost","hamon_wall_climbing","hamon_life_magnetism","hamon_projectile_shield","hamon_protection","hamon_detector","hamon_hypnosis","hamon_shock");
    public static List<String> attacks(HamonProgress state){
        var out=new ArrayList<>(ATTACKS);
        for(String id:List.of("jonathan_scarlet_overdrive","jonathan_overdrive_barrage","zeppeli_hamon_cutter","zeppeli_sendo_wave_kick","zeppeli_tornado_overdrive","joseph_rebuff_overdrive","caesar_bubble_launcher","caesar_bubble_cutter","caesar_bubble_barrier"))if(unlocked(state,id))out.add(id);
        return List.copyOf(out);
    }
    public static List<String> abilities(HamonProgress state){return ABILITIES;}
    private HamonActionCatalog(){}
}
