package com.github.standobyte.jojo.core;

import java.util.*;

/** Registry metadata and learning rules from Forge ModHamonSkills and its managers. */
public final class HamonSkills {
    public enum Stat { STRENGTH, CONTROL }
    public enum Branch { OVERDRIVE, INFUSION, FLEXIBILITY, HEALING, ATTRACTANT_REPELLENT, BODY_MANIPULATION;
        public String key() { return this == HEALING ? "life" : name().toLowerCase(Locale.ROOT); }
    }
    public enum Reward { ATTACK, ABILITY, PASSIVE, ITEM }
    public record Skill(String id, Stat stat, Branch branch, Reward reward, List<String> prerequisites,
            boolean defaultUnlocked, String technique, boolean perk, List<String> actions,
            List<String> hudActions, int tier, int column) {
        public Skill { prerequisites=List.copyOf(prerequisites); actions=List.copyOf(actions); hudActions=List.copyOf(hudActions); }
        public boolean base() { return stat != null; }
        public boolean requiresTeacher() { return base(); }
        public String translationKey() { return "hamonSkill."+id+".name"; }
    }
    public record Technique(String id, List<String> skills, List<String> perks, Map<Branch,Float> efficiency) {
        public Technique { skills=List.copyOf(skills); perks=List.copyOf(perks); efficiency=Map.copyOf(efficiency); }
        public String translationKey() { return "hamon.technique."+id; }
    }
    private static final Map<String,Skill> SKILLS = new LinkedHashMap<>();
    private static final Map<String,Technique> TECHNIQUES = new LinkedHashMap<>();
    static {
        add(new Skill("overdrive",Stat.STRENGTH,Branch.OVERDRIVE,Reward.ATTACK,List.of(),true,"",false,List.of("hamon_overdrive","hamon_overdrive_beat"),List.of(),0,0));
        add(new Skill("sendo_overdrive",Stat.STRENGTH,Branch.OVERDRIVE,Reward.ATTACK,List.of("overdrive"),false,"",false,List.of("hamon_sendo_overdrive"),List.of(),1,0));
        add(new Skill("turquoise_blue_overdrive",Stat.STRENGTH,Branch.OVERDRIVE,Reward.ATTACK,List.of("overdrive"),false,"",false,List.of("hamon_turquoise_blue_overdrive"),List.of(),1,1));
        add(new Skill("sunlight_yellow_overdrive",Stat.STRENGTH,Branch.OVERDRIVE,Reward.ATTACK,List.of("sendo_overdrive","turquoise_blue_overdrive"),false,"",false,List.of("hamon_sunlight_yellow_overdrive"),List.of(),2,0));
        add(new Skill("throwables_infusion",Stat.STRENGTH,Branch.INFUSION,Reward.PASSIVE,List.of(),false,"",false,List.of(),List.of(),0,0));
        add(new Skill("plant_infusion",Stat.STRENGTH,Branch.INFUSION,Reward.ATTACK,List.of("throwables_infusion"),false,"",false,List.of("hamon_plant_infusion"),List.of(),1,0));
        add(new Skill("plant_item_infusion",Stat.STRENGTH,Branch.INFUSION,Reward.PASSIVE,List.of("throwables_infusion"),false,"",false,List.of(),List.of(),1,1));
        add(new Skill("animal_infusion",Stat.STRENGTH,Branch.INFUSION,Reward.PASSIVE,List.of("plant_infusion"),false,"",false,List.of("hamon_organism_infusion"),List.of(),2,0));
        add(new Skill("arrow_infusion",Stat.STRENGTH,Branch.INFUSION,Reward.PASSIVE,List.of("plant_item_infusion"),false,"",false,List.of(),List.of(),2,1));
        add(new Skill("zoom_punch",Stat.STRENGTH,Branch.FLEXIBILITY,Reward.ATTACK,List.of(),false,"",false,List.of("hamon_zoom_punch"),List.of(),0,0));
        add(new Skill("jump",Stat.STRENGTH,Branch.FLEXIBILITY,Reward.ABILITY,List.of("zoom_punch"),false,"",false,List.of(),List.of(),1,0));
        add(new Skill("speed_boost",Stat.STRENGTH,Branch.FLEXIBILITY,Reward.ABILITY,List.of("zoom_punch"),false,"",false,List.of("hamon_speed_boost"),List.of(),1,1));
        add(new Skill("afterimages",Stat.STRENGTH,Branch.FLEXIBILITY,Reward.PASSIVE,List.of("jump","speed_boost"),false,"",false,List.of(),List.of(),2,0));
        add(new Skill("healing",Stat.CONTROL,Branch.HEALING,Reward.ABILITY,List.of(),true,"",false,List.of("hamon_healing"),List.of(),0,0));
        add(new Skill("plants_growth",Stat.CONTROL,Branch.HEALING,Reward.PASSIVE,List.of("healing"),false,"",false,List.of(),List.of(),1,0));
        add(new Skill("expel_venom",Stat.CONTROL,Branch.HEALING,Reward.PASSIVE,List.of("healing"),false,"",false,List.of(),List.of(),1,1));
        add(new Skill("healing_touch",Stat.CONTROL,Branch.HEALING,Reward.PASSIVE,List.of("plants_growth","expel_venom"),false,"",false,List.of(),List.of(),2,0));
        add(new Skill("wall_climbing",Stat.CONTROL,Branch.ATTRACTANT_REPELLENT,Reward.ABILITY,List.of(),false,"",false,List.of("hamon_wall_climbing"),List.of(),0,0));
        add(new Skill("liquid_walking",Stat.CONTROL,Branch.ATTRACTANT_REPELLENT,Reward.PASSIVE,List.of(),false,"",false,List.of(),List.of(),0,1));
        add(new Skill("life_magnetism",Stat.CONTROL,Branch.ATTRACTANT_REPELLENT,Reward.ABILITY,List.of("wall_climbing"),false,"",false,List.of("hamon_life_magnetism"),List.of(),1,0));
        add(new Skill("projectile_shield",Stat.CONTROL,Branch.ATTRACTANT_REPELLENT,Reward.ABILITY,List.of("liquid_walking"),false,"",false,List.of("hamon_projectile_shield"),List.of(),1,1));
        add(new Skill("protection",Stat.CONTROL,Branch.ATTRACTANT_REPELLENT,Reward.ABILITY,List.of("life_magnetism","projectile_shield"),false,"",false,List.of("hamon_protection"),List.of(),2,0));
        add(new Skill("detector",Stat.CONTROL,Branch.BODY_MANIPULATION,Reward.ABILITY,List.of(),false,"",false,List.of("hamon_detector"),List.of(),0,0));
        add(new Skill("hypnosis",Stat.CONTROL,Branch.BODY_MANIPULATION,Reward.ABILITY,List.of("detector"),false,"",false,List.of("hamon_hypnosis"),List.of(),1,0));
        add(new Skill("hamon_shock",Stat.CONTROL,Branch.BODY_MANIPULATION,Reward.PASSIVE,List.of("detector"),false,"",false,List.of("hamon_shock"),List.of(),1,1));
        add(new Skill("hamon_spread",Stat.CONTROL,Branch.BODY_MANIPULATION,Reward.PASSIVE,List.of("hypnosis","hamon_shock"),false,"",false,List.of(),List.of(),2,0));
        add(new Skill("natural_talent",null,null,Reward.PASSIVE,List.of(),false,"jonathan",true,List.of(),List.of(),0,0));
        add(new Skill("metal_silver_overdrive",null,null,Reward.PASSIVE,List.of("sendo_overdrive","turquoise_blue_overdrive"),false,"jonathan",false,List.of("jonathan_metal_silver_overdrive","jonathan_metal_silver_overdrive_weapon"),List.of(),0,0));
        add(new Skill("scarlet_overdrive",null,null,Reward.ATTACK,List.of("sunlight_yellow_overdrive"),false,"jonathan",false,List.of("jonathan_scarlet_overdrive"),List.of("jonathan_scarlet_overdrive"),0,0));
        add(new Skill("overdrive_barrage",null,null,Reward.ATTACK,List.of("zoom_punch"),false,"jonathan",false,List.of("jonathan_overdrive_barrage"),List.of("jonathan_overdrive_barrage"),0,0));
        add(new Skill("syo_barrage",null,null,Reward.ATTACK,List.of("overdrive_barrage","sunlight_yellow_overdrive"),false,"",false,List.of("jonathan_syo_barrage"),List.of(),0,0));
        add(new Skill("deep_pass",null,null,Reward.PASSIVE,List.of(),false,"zeppeli",true,List.of(),List.of(),0,0));
        add(new Skill("hamon_cutter",null,null,Reward.ATTACK,List.of("throwables_infusion"),false,"zeppeli",false,List.of("zeppeli_hamon_cutter"),List.of("zeppeli_hamon_cutter"),0,0));
        add(new Skill("sendo_wave_kick",null,null,Reward.ATTACK,List.of("jump"),false,"zeppeli",false,List.of("zeppeli_sendo_wave_kick"),List.of("zeppeli_sendo_wave_kick"),0,0));
        add(new Skill("tornado_overdrive",null,null,Reward.ATTACK,List.of("jump","speed_boost"),false,"zeppeli",false,List.of("zeppeli_tornado_overdrive"),List.of("zeppeli_tornado_overdrive"),0,0));
        add(new Skill("rope_trap",null,null,Reward.ITEM,List.of("sendo_overdrive"),false,"joseph",false,List.of(),List.of(),0,0));
        add(new Skill("clacker_volley",null,null,Reward.ITEM,List.of("throwables_infusion"),false,"joseph",false,List.of(),List.of(),0,0));
        add(new Skill("rebuff_overdrive",null,null,Reward.ATTACK,List.of("protection"),false,"joseph",false,List.of("joseph_rebuff_overdrive"),List.of("joseph_rebuff_overdrive"),0,0));
        add(new Skill("cheat_death",null,null,Reward.PASSIVE,List.of(),false,"",false,List.of(),List.of(),0,0));
        add(new Skill("crimson_bubble",null,null,Reward.PASSIVE,List.of(),false,"caesar",true,List.of(),List.of(),0,0));
        add(new Skill("bubble_launcher",null,null,Reward.ATTACK,List.of("throwables_infusion"),false,"caesar",false,List.of("caesar_bubble_launcher"),List.of("caesar_bubble_launcher"),0,0));
        add(new Skill("bubble_cutter",null,null,Reward.ATTACK,List.of("bubble_launcher","arrow_infusion"),false,"caesar",false,List.of("caesar_bubble_cutter","caesar_bubble_cutter_gliding"),List.of("caesar_bubble_cutter"),0,0));
        add(new Skill("bubble_barrier",null,null,Reward.ATTACK,List.of("bubble_launcher","hamon_shock"),false,"caesar",false,List.of("caesar_bubble_barrier"),List.of("caesar_bubble_barrier"),0,0));
        add(new Skill("aja_stone_keeper",null,null,Reward.ITEM,List.of(),false,"lisa_lisa",false,List.of(),List.of(),0,0));
        add(new Skill("satiporoja_scarf",null,null,Reward.ITEM,List.of("animal_infusion"),false,"lisa_lisa",false,List.of(),List.of(),0,0));
        add(new Skill("snake_muffler",null,null,Reward.ITEM,List.of("satiporoja_scarf","detector","jump"),false,"lisa_lisa",false,List.of(),List.of(),0,0));
        add(new Technique("jonathan",List.of("metal_silver_overdrive","scarlet_overdrive","overdrive_barrage"),List.of("natural_talent"),Map.of(Branch.OVERDRIVE,0.2F)));
        add(new Technique("zeppeli",List.of("hamon_cutter","sendo_wave_kick","tornado_overdrive"),List.of("deep_pass"),Map.of(Branch.FLEXIBILITY,0.2F)));
        add(new Technique("joseph",List.of("rope_trap","clacker_volley","rebuff_overdrive"),List.of(),Map.of(Branch.OVERDRIVE,0.1F,Branch.INFUSION,0.1F,Branch.ATTRACTANT_REPELLENT,0.1F)));
        add(new Technique("caesar",List.of("bubble_launcher","bubble_cutter","bubble_barrier"),List.of("crimson_bubble"),Map.of(Branch.INFUSION,0.2F)));
        add(new Technique("lisa_lisa",List.of("aja_stone_keeper","satiporoja_scarf","snake_muffler"),List.of(),Map.of(Branch.INFUSION,0.1F,Branch.FLEXIBILITY,0.1F,Branch.BODY_MANIPULATION,0.1F)));
    }
    private static void add(Skill value) { SKILLS.put(value.id(),value); }
    private static void add(Technique value) { TECHNIQUES.put(value.id(),value); }
    public static String normalize(String id) { return id != null && id.startsWith("jojo:") ? id.substring(5) : id == null ? "" : id; }
    public static Skill skill(String id) { return SKILLS.get(normalize(id)); }
    public static Technique technique(String id) { return TECHNIQUES.get(normalize(id)); }
    public static List<Skill> all() { return List.copyOf(SKILLS.values()); }
    public static List<Technique> techniques() { return List.copyOf(TECHNIQUES.values()); }
    /** Empty result means the server may learn this skill. Teacher values must come from the server world. */
    public static String learningProblem(HamonProgress state,String id,Collection<String> teacherSkills,
            boolean teacherNearby,boolean mix,List<Integer> slots) {
        Skill skill=skill(id);
        if (!state.hamon() || skill==null || state.hasSkill(id)) return "hamon.closed.parents";
        if (skill.base()) {
            if (state.skillPoints(skill.stat())<=0) return "hamon.closed.points";
        } else {
            if (slots.isEmpty()) return "hamon.closed.technique.locked";
            if (skill.technique().isEmpty() || !mix && !skill.technique().equals(state.technique())) return "hamon.closed.technique.bug";
            int used=state.techniqueSkills().size();
            if (used>=slots.size()) return "hamon.closed.technique.max";
            if (state.strengthLevel()<slots.get(used) || state.controlLevel()<slots.get(used)) return "hamon.closed.technique.locked";
        }
        if (skill.prerequisites().stream().anyMatch(parent->!state.hasSkill(parent))) return "hamon.closed.parents";
        if (skill.requiresTeacher()) {
            if (!teacherNearby) return "hamon.closed.teacher.required";
            if (!teacherSkills.contains(skill.id())) return "hamon.closed.teacher.no_skill";
        }
        return "";
    }
    public static float efficiencyBonus(String technique,String skill) {
        Technique t=technique(technique); Skill s=skill(skill);
        return t==null || s==null || s.branch()==null ? 0 : t.efficiency().getOrDefault(s.branch(),0F);
    }
    private HamonSkills() { }
}
