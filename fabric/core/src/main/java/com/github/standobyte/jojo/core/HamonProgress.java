package com.github.standobyte.jojo.core;

import java.util.*;
import java.util.function.Consumer;

/** Immutable Hamon save state; formulae preserve HamonData's shared fractional XP and training rules. */
public record HamonProgress(boolean hamon, int strengthPoints, int controlPoints, float pointsFraction,
        float breathingLevel, float energy, float breathStability, List<String> learnedSkills, String technique,
        List<Integer> exercises, float trainingBonus, int skipDays, int energyTicks, int maskTicks,
        int noRecoveryTicks, boolean tcsa, int effectiveExerciseMask) {
    public static final int MAX_STAT_LEVEL=60;
    public static final float MAX_BREATHING_LEVEL=100;
    private static final int[] POINTS_AT_LEVEL=new int[MAX_STAT_LEVEL+1];
    static {
        POINTS_AT_LEVEL[1]=2;
        for (int i=2,diff=0;i<POINTS_AT_LEVEL.length;i++) {
            diff+=3+(i-1)/20;
            POINTS_AT_LEVEL[i]=POINTS_AT_LEVEL[i-1]+2+diff;
        }
    }
    public static final int MAX_HAMON_POINTS=POINTS_AT_LEVEL[MAX_STAT_LEVEL];
    public static final HamonProgress EMPTY=new HamonProgress(false,0,0,0,0,0,0,List.of(),"",List.of(0,0,0,0),0,0,0,0,0,true);
    public enum Exercise { MINING(1500), RUNNING(1350), SWIMMING(1200), MEDITATION(750);
        private final int baseTicks;
        Exercise(int ticks) { baseTicks=ticks; }
        public int maximum(float breathing) { return (int)Math.floor(baseTicks*((100-breathing)/100*.75F+.25F)); }
    }
    /** Save/legacy construction refreshes the source's runtime exercise cache, including at level 100. */
    public HamonProgress(boolean hamon,int strengthPoints,int controlPoints,float pointsFraction,float breathingLevel,
            float energy,float breathStability,List<String> learnedSkills,String technique,List<Integer> exercises,
            float trainingBonus,int skipDays,int energyTicks,int maskTicks,int noRecoveryTicks,boolean tcsa) {
        this(hamon,strengthPoints,controlPoints,pointsFraction,breathingLevel,energy,breathStability,learnedSkills,technique,
                exercises,trainingBonus,skipDays,energyTicks,maskTicks,noRecoveryTicks,tcsa,completionMask(exercises,breathingLevel));
    }
    private static int completionMask(List<Integer> exercises,float breathing) {
        if(exercises.size()!=4)throw new IllegalArgumentException("Four exercise counters required");
        int mask=0;
        for(var exercise:Exercise.values())if(exercises.get(exercise.ordinal())>=exercise.maximum(breathing))mask|=1<<exercise.ordinal();
        return mask;
    }
    public HamonProgress {
        if (strengthPoints<0 || strengthPoints>MAX_HAMON_POINTS || controlPoints<0 || controlPoints>MAX_HAMON_POINTS
                || !Float.isFinite(pointsFraction) || pointsFraction<0 || pointsFraction>=1.001F
                || !Float.isFinite(breathingLevel) || breathingLevel<0 || breathingLevel>100
                || !Float.isFinite(energy) || energy<0 || !Float.isFinite(breathStability) || breathStability<0
                || !Float.isFinite(trainingBonus) || trainingBonus<0 || skipDays<0 || energyTicks<0 || maskTicks<0 || noRecoveryTicks<0
                || effectiveExerciseMask<0 || effectiveExerciseMask>15)
            throw new IllegalArgumentException("Invalid Hamon progress");
        technique=HamonSkills.normalize(Objects.requireNonNull(technique));
        if (!technique.isEmpty() && HamonSkills.technique(technique)==null) throw new IllegalArgumentException("Unknown Hamon technique");
        learnedSkills=List.copyOf(new LinkedHashSet<>(learnedSkills.stream().map(HamonSkills::normalize).filter(id->HamonSkills.skill(id)!=null).toList()));
        exercises=List.copyOf(exercises);
        if (exercises.size()!=4 || exercises.stream().anyMatch(v->v<0)) throw new IllegalArgumentException("Four nonnegative exercise counters required");
    }
    public static HamonProgress initial() { return EMPTY.edit(b->{b.hamon=true;b.breathStability=1000;b.learnedSkills=List.of("overdrive","healing");}); }
    public static int pointsAtLevel(int level) { return POINTS_AT_LEVEL[Math.clamp(level,0,60)]; }
    public static int levelFromPoints(int points) {
        int i=Arrays.binarySearch(POINTS_AT_LEVEL,Math.clamp(points,0,MAX_HAMON_POINTS));
        return i>=0?i:-i-2;
    }
    public static int pointsAtLevelFraction(float level) {
        level=Math.clamp(level,0,60); int floor=(int)level;
        return pointsAtLevel(floor)+(int)((pointsAtLevel(floor+1)-pointsAtLevel(floor))*(level-floor));
    }
    public static float levelFractionFromPoints(int points) {
        int level=levelFromPoints(points);
        return level==60?60:level+(float)(points-pointsAtLevel(level))/(pointsAtLevel(level+1)-pointsAtLevel(level));
    }
    public int strengthLevel() { return levelFromPoints(strengthPoints); }
    public int controlLevel() { return levelFromPoints(controlPoints); }
    public int statLevel(HamonSkills.Stat stat) { return stat==HamonSkills.Stat.STRENGTH?strengthLevel():controlLevel(); }
    public int skillPoints(HamonSkills.Stat stat) {
        return statLevel(stat)/5-(int)learnedSkills.stream().map(HamonSkills::skill).filter(s->s.base() && s.stat()==stat && !s.defaultUnlocked()).count();
    }
    public boolean hasSkill(String id) {
        id=HamonSkills.normalize(id);
        if (learnedSkills.contains(id)) return true;
        var t=HamonSkills.technique(technique);
        return t!=null && t.perks().contains(id);
    }
    public List<String> techniqueSkills() { return learnedSkills.stream().filter(id->!HamonSkills.skill(id).base()).toList(); }
    public int exerciseMaxTicks(Exercise exercise) { return exercise.maximum(breathingLevel); }
    public int exerciseTicks(Exercise exercise) { return Math.min(exercises.get(exercise.ordinal()),exerciseMaxTicks(exercise)); }
    public boolean exerciseComplete(Exercise exercise) { return exerciseTicks(exercise)>=exerciseMaxTicks(exercise); }
    public int completedExercises() { return (int)Arrays.stream(Exercise.values()).filter(this::exerciseComplete).count(); }
    public boolean exerciseBonusActive(Exercise exercise) { return (effectiveExerciseMask & 1<<exercise.ordinal())!=0; }
    public int effectiveCompletedExercises() { return Integer.bitCount(effectiveExerciseMask); }
    public HamonProgress refreshExerciseBonuses() { return edit(b->b.effectiveExerciseMask=completionMask(exercises,breathingLevel)); }
    public HamonProgress withExercises(List<Integer> values) {
        return edit(b->{b.exercises=List.copyOf(values);b.effectiveExerciseMask=completionMask(b.exercises,breathingLevel);});
    }
    public float maxIncompleteExercise() {
        float largest=0;
        for (var ex:Exercise.values()) if (!exerciseComplete(ex)) largest=Math.max(largest,(float)exerciseTicks(ex)/exerciseMaxTicks(ex));
        return largest;
    }
    public float maxBreathStability() { return 1000*(1+controlLevel()*.1F)*(exerciseBonusActive(Exercise.SWIMMING)?1.1F:1); }
    public float maxEnergy() { return breathStability; }
    public float damageMultiplier() { return 1+strengthLevel()*.1F; }
    public float fullEnergyTicks() { return 80-40*breathingLevel/100-(exerciseBonusActive(Exercise.MEDITATION)?20:0); }
    public float fullStabilityTicks() { return 1200-600*breathingLevel/100; }
    public float energyEfficiency(float cost,boolean mask) {
        if (!hamon) return 0;
        if (cost<=0 || energy>=cost) return 1;
        if (energy>0) return .5F+.5F*energy/cost;
        return .5F*Math.min(breathStability*(mask?1:2.5F)/cost,1);
    }
    public HamonProgress consume(float cost,boolean mask) {
        if (cost<=0) return this;
        if (energy>=cost) return withEnergy(energy-cost);
        if (energy>0) return withEnergy(0);
        float available=breathStability*(mask?1:2.5F);
        // Forge divides by 2.5 even when the mask disables the multiplier above.
        return withBreathStability(available<cost?0:(available-cost)/2.5F,80);
    }
    public float actionEfficiency(float cost,boolean mask,String skill,float bloodstream) {
        float result=energyEfficiency(cost,mask)*bloodstream;
        if (result>0) {
            result*=1+(effectiveCompletedExercises()>=4?.05F:0)+HamonSkills.efficiencyBonus(technique,skill);
            if (breathStability<maxBreathStability()) result*=.5F+.5F*breathStability/maxBreathStability();
        }
        return result;
    }
    public HamonProgress withEnergy(float value) { return edit(b->b.energy=Math.clamp(value,0,breathStability)); }
    public HamonProgress withBreathStability(float value,int pause) {
        return edit(b->{b.breathStability=Math.clamp(value,0,maxBreathStability());b.noRecoveryTicks=Math.max(pause,noRecoveryTicks);b.energy=Math.min(energy,b.breathStability);});
    }
    public HamonProgress withBreathing(float value) { return edit(b->b.breathingLevel=Math.clamp(value,0,100)); }
    public HamonProgress withTutorial(boolean value) { return edit(b->b.tcsa=value); }
    public HamonProgress withStats(int strength,int control) {
        int c=Math.clamp(control,0,MAX_HAMON_POINTS); float ratio=(1+levelFromPoints(c)*.1F)/(1+controlLevel()*.1F);
        return edit(b->{b.strengthPoints=Math.clamp(strength,0,MAX_HAMON_POINTS);b.controlPoints=c;b.energy*=ratio;b.breathStability*=ratio;});
    }
    public HamonProgress gainPoints(HamonSkills.Stat stat,float cost,float multiplier,int trainingGap) {
        if (!Float.isFinite(cost) || cost<=0 || multiplier<=0) return this;
        cost*=multiplier*(hasSkill("natural_talent")?2:1);
        int points=(int)(cost/750); float remainder=pointsFraction+(cost%750)/750;
        if (remainder>=1) { points++; remainder--; }
        int old=stat==HamonSkills.Stat.STRENGTH?strengthPoints:controlPoints;
        int value=Math.min(MAX_HAMON_POINTS,old+points);
        int limit=(int)breathingLevel+trainingGap;
        if (trainingGap>=0 && levelFromPoints(value)>limit) value=Math.max(old,pointsAtLevel(limit+1)-1);
        HamonProgress result=stat==HamonSkills.Stat.STRENGTH?withStats(value,controlPoints):withStats(strengthPoints,value);
        final float fraction=remainder;
        return result.edit(b->b.pointsFraction=fraction);
    }
    public HamonProgress learn(String id) {
        var skill=HamonSkills.skill(id); if (skill==null || hasSkill(skill.id())) return this;
        return edit(b->{var list=new ArrayList<>(learnedSkills);list.add(skill.id());b.learnedSkills=list;});
    }
    public HamonProgress withTechnique(String value) { return edit(b->{b.technique=HamonSkills.normalize(value);b.tcsa=false;}); }
    public HamonProgress resetSkills(String tab) {
        boolean techniqueTab=tab.equalsIgnoreCase("technique");
        return edit(b->{b.learnedSkills=learnedSkills.stream().filter(id->{var s=HamonSkills.skill(id);return s.defaultUnlocked()
                || !(techniqueTab?!s.base():s.base() && s.stat().name().equalsIgnoreCase(tab));}).toList();if (techniqueTab)b.technique="";});
    }
    public HamonProgress exercise(Exercise exercise,int amount) {
        if (amount<=0 || exerciseComplete(exercise)) return this;
        var next=edit(b->{var list=new ArrayList<>(exercises);list.set(exercise.ordinal(),Math.min(exerciseTicks(exercise)+amount,exerciseMaxTicks(exercise)));b.exercises=list;});
        // HamonData.tickExercises refreshes only on a completion below the breathing cap.
        return breathingLevel<100 && next.exerciseComplete(exercise)?next.refreshExerciseBonuses():next;
    }
    public float dayIncrease(boolean mask,boolean deterioration,float multiplier) {
        float raw=Math.clamp(effectiveCompletedExercises()+maxIncompleteExercise()-2,-1,1);
        float increase;
        if (raw<=0) increase=!deterioration || breathingLevel>=100 || skipDays>0?0:raw*.25F;
        else increase=(raw+(mask?trainingBonus:0))*(hasSkill("natural_talent")?2:1)*multiplier;
        return Math.clamp(increase,-breathingLevel,100-breathingLevel);
    }
    public HamonProgress nextDay(boolean mask,boolean deterioration,float multiplier) {
        float raw=Math.clamp(effectiveCompletedExercises()+maxIncompleteExercise()-2,-1,1);
        float increase=dayIncrease(mask,deterioration,multiplier);
        // Bonus decisions use the pre-cap increase, as in the original getBreathingIncrease.
        float beforeCap=raw<=0?(!deterioration || breathingLevel>=100 || skipDays>0?0:raw*.25F)
                :(raw+(mask?trainingBonus:0))*(hasSkill("natural_talent")?2:1)*multiplier;
        return edit(b->{b.breathingLevel=Math.clamp(breathingLevel+increase,0,100);
            if (beforeCap<=0 && skipDays==0) b.trainingBonus=0;
            else if (mask) b.trainingBonus+=Math.max(raw,0)*.25F;
            b.skipDays=Math.max(skipDays-1,0); if (effectiveCompletedExercises()>=4)b.skipDays=Math.max(b.skipDays,2);
            b.exercises=List.of(0,0,0,0);
            b.effectiveExerciseMask=0;
        });
    }
    public HamonProgress tickBreath(boolean holding,boolean canBreathe,boolean mask,boolean meditating,
            int recoveryMeditationTicks,boolean decay,boolean dying,float random) {
        if (dying) return edit(b->{b.energy=0;b.breathStability=0;});
        return edit(b->{
            if (holding && canBreathe) {
                b.maskTicks=0;
                float grace=150*breathingLevel/100;
                b.energyTicks=50+(int)grace+(random<grace-(int)grace?1:0);
                b.energy+=maxBreathStability()/fullEnergyTicks();
            } else {
                b.maskTicks=mask && !meditating?Math.min(maskTicks+1,Integer.MAX_VALUE):0;
                if (energyTicks>0)b.energyTicks--;else if (decay)b.energy=Math.max(0,b.energy-20);
            }
            float increment;
            if (mask) {
                float ticks=400+breathingLevel*16;
                float handicap=breathingLevel<100?Math.clamp((ticks-b.maskTicks)/(ticks/2),-1,1):0;
                increment=handicap>=0?maxBreathStability()/fullStabilityTicks()*handicap:maxBreathStability()/1200*handicap;
                if (handicap<0 && (breathStability+increment)/maxBreathStability()<.2F)
                    increment=Math.clamp(increment,.2F*maxBreathStability()-breathStability,0);
            } else increment=maxBreathStability()/fullStabilityTicks();
            if (increment>=0 && meditating && recoveryMeditationTicks>0)increment*=(float)Math.sqrt(Math.min(recoveryMeditationTicks,100));
            if (!canBreathe)increment=Math.min(increment,0);
            if (increment>0 && noRecoveryTicks>0) { b.noRecoveryTicks--;increment=0; }
            b.breathStability=Math.clamp(breathStability+increment,0,maxBreathStability());
            b.energy=Math.clamp(b.energy,0,b.breathStability);
        });
    }
    private HamonProgress edit(Consumer<Builder> edit) { var b=new Builder(this);edit.accept(b);return b.build(); }
    private static final class Builder {
        boolean hamon,tcsa; int strengthPoints,controlPoints,skipDays,energyTicks,maskTicks,noRecoveryTicks,effectiveExerciseMask;
        float pointsFraction,breathingLevel,energy,breathStability,trainingBonus;
        List<String> learnedSkills;String technique;List<Integer> exercises;
        Builder(HamonProgress s) {hamon=s.hamon;strengthPoints=s.strengthPoints;controlPoints=s.controlPoints;pointsFraction=s.pointsFraction;
            breathingLevel=s.breathingLevel;energy=s.energy;breathStability=s.breathStability;learnedSkills=s.learnedSkills;technique=s.technique;
            exercises=s.exercises;trainingBonus=s.trainingBonus;skipDays=s.skipDays;energyTicks=s.energyTicks;maskTicks=s.maskTicks;noRecoveryTicks=s.noRecoveryTicks;tcsa=s.tcsa;effectiveExerciseMask=s.effectiveExerciseMask;}
        HamonProgress build() {return new HamonProgress(hamon,strengthPoints,controlPoints,pointsFraction,breathingLevel,energy,breathStability,
                learnedSkills,technique,exercises,trainingBonus,skipDays,energyTicks,maskTicks,noRecoveryTicks,tcsa,effectiveExerciseMask);}
    }
}
