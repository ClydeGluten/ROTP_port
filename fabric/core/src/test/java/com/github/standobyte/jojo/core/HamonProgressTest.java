package com.github.standobyte.jojo.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HamonProgressTest {
    private static final List<Integer> SLOTS=List.of(20,30,40);
    private static HamonProgress leveled(int strength,int control) {
        return HamonProgress.initial().withStats(HamonProgress.pointsAtLevel(strength),HamonProgress.pointsAtLevel(control));
    }
    private static HamonProgress complete(HamonProgress state,HamonProgress.Exercise... exercises) {
        for(var exercise:exercises)state=state.exercise(exercise,Integer.MAX_VALUE/2);
        return state;
    }
    @Test void originalXpCurveAndFractionStayFiniteAtCap() {
        assertEquals(40,HamonProgress.pointsAtLevel(5));assertEquals(610,HamonProgress.pointsAtLevel(20));
        assertEquals(673,HamonProgress.pointsAtLevel(21));assertEquals(2630,HamonProgress.pointsAtLevel(40));
        assertEquals(6460,HamonProgress.MAX_HAMON_POINTS);
        assertEquals(19,HamonProgress.levelFromPoints(609));assertEquals(20,HamonProgress.levelFromPoints(610));
        assertEquals(60,HamonProgress.levelFractionFromPoints(HamonProgress.MAX_HAMON_POINTS));
    }
    @Test void actionXpRemainderIsSharedBetweenStrengthAndControlAndRespectsTrainingGap() {
        var state=HamonProgress.initial().gainPoints(HamonSkills.Stat.STRENGTH,375,1,-1);
        assertEquals(0,state.strengthPoints());assertEquals(.5F,state.pointsFraction());
        state=state.gainPoints(HamonSkills.Stat.CONTROL,375,1,-1);
        assertEquals(1,state.controlPoints());assertEquals(0,state.pointsFraction());
        state=state.gainPoints(HamonSkills.Stat.STRENGTH,75000,1,0);
        assertEquals(1,state.strengthPoints());assertEquals(0,state.strengthLevel());
        var jonathan=HamonProgress.initial().withTechnique("jonathan").gainPoints(HamonSkills.Stat.STRENGTH,375,1,-1);
        assertEquals(1,jonathan.strengthPoints());
    }
    @Test void controlLevelsScaleBothResourcePoolsAndPartialEnergyNeverChargesStability() {
        var state=HamonProgress.initial().withEnergy(100).withStats(0,HamonProgress.pointsAtLevel(10));
        assertEquals(2000,state.breathStability());assertEquals(200,state.energy());
        assertEquals(.75F,state.energyEfficiency(400,false));
        var spent=state.consume(400,false);
        assertEquals(0,spent.energy());assertEquals(2000,spent.breathStability());
        assertEquals(.5F,spent.energyEfficiency(400,false));
        assertEquals(1840,spent.consume(400,false).breathStability());
        assertEquals(640,spent.consume(400,true).breathStability());
    }
    @Test void emptyEnergyStabilityRecoveryUsesOriginalPauseAndAirRules() {
        var spent=HamonProgress.initial().consume(2500,false);
        assertEquals(0,spent.breathStability());assertEquals(80,spent.noRecoveryTicks());
        var noAir=spent.tickBreath(false,false,false,false,0,true,false,0);
        assertEquals(80,noAir.noRecoveryTicks());assertEquals(0,noAir.breathStability());
        var firstAir=noAir.tickBreath(false,true,false,false,0,true,false,0);
        assertEquals(79,firstAir.noRecoveryTicks());assertEquals(0,firstAir.breathStability());
    }
    @Test void exercisesUseOriginalDurationsAndDailyTrainingAccumulatesMaskBonus() {
        var state=HamonProgress.initial();
        assertEquals(1500,state.exerciseMaxTicks(HamonProgress.Exercise.MINING));
        assertEquals(1350,state.exerciseMaxTicks(HamonProgress.Exercise.RUNNING));
        assertEquals(1200,state.exerciseMaxTicks(HamonProgress.Exercise.SWIMMING));
        assertEquals(750,state.exerciseMaxTicks(HamonProgress.Exercise.MEDITATION));
        state=complete(state,HamonProgress.Exercise.values());
        assertEquals(1100,state.maxBreathStability());assertEquals(60,state.fullEnergyTicks());
        var day=state.nextDay(true,true,1);
        assertEquals(1,day.breathingLevel());assertEquals(.25F,day.trainingBonus());assertEquals(2,day.skipDays());
        assertEquals(0,day.completedExercises());
        day=complete(day,HamonProgress.Exercise.MINING,HamonProgress.Exercise.RUNNING,HamonProgress.Exercise.SWIMMING).nextDay(true,true,1);
        assertEquals(2.25F,day.breathingLevel());assertEquals(.5F,day.trainingBonus());assertEquals(1,day.skipDays());
    }
    @Test void fourExercisesProtectExactlyTwoMissedDaysAndLevel100NeverDeteriorates() {
        var state=complete(HamonProgress.initial().withBreathing(10),HamonProgress.Exercise.values()).nextDay(false,true,1);
        assertEquals(11,state.breathingLevel());
        state=state.nextDay(false,true,1);assertEquals(11,state.breathingLevel());assertEquals(1,state.skipDays());
        state=state.nextDay(false,true,1);assertEquals(11,state.breathingLevel());assertEquals(0,state.skipDays());
        state=state.nextDay(false,true,1);assertEquals(10.75F,state.breathingLevel());
        assertEquals(100,HamonProgress.initial().withBreathing(100).nextDay(false,true,1).breathingLevel());
        assertEquals(375,HamonProgress.initial().withBreathing(100).exerciseMaxTicks(HamonProgress.Exercise.MINING));
    }
    @Test void mostAdvancedPartialExerciseAffectsDailyChangeWithoutSummingPartials() {
        var state=HamonProgress.initial().withBreathing(10);
        state=complete(state,HamonProgress.Exercise.MINING,HamonProgress.Exercise.RUNNING);
        state=state.exercise(HamonProgress.Exercise.SWIMMING,state.exerciseMaxTicks(HamonProgress.Exercise.SWIMMING)/2)
                .exercise(HamonProgress.Exercise.MEDITATION,state.exerciseMaxTicks(HamonProgress.Exercise.MEDITATION)/4);
        assertEquals(.5F,state.dayIncrease(false,true,1),.002F);
    }
    @Test void cappedSessionCountersDoNotRefreshBonusesButExplicitAssignmentDoes() {
        var state=complete(HamonProgress.initial().withBreathing(100),HamonProgress.Exercise.values());
        assertEquals(4,state.completedExercises());assertEquals(0,state.effectiveCompletedExercises());
        assertEquals(40,state.fullEnergyTicks());assertEquals(1000,state.maxBreathStability());
        assertEquals(1,state.actionEfficiency(0,false,"overdrive",1));
        assertEquals(0,state.nextDay(false,true,1).skipDays());
        var refreshed=state.withExercises(state.exercises());
        assertEquals(4,refreshed.effectiveCompletedExercises());assertEquals(20,refreshed.fullEnergyTicks());
        assertEquals(1100,refreshed.maxBreathStability());
        assertEquals(1.05F,refreshed.withBreathStability(1100,0).actionEfficiency(0,false,"overdrive",1),.0001F);
        var reset=refreshed.nextDay(false,true,1);
        assertEquals(0,reset.effectiveCompletedExercises());assertEquals(40,reset.fullEnergyTicks());
        assertEquals(2,reset.skipDays());
    }
    @Test void bonusCacheSurvivesOrdinaryEditsAndBreathingThresholdChanges() {
        var active=complete(HamonProgress.initial().withBreathing(99),HamonProgress.Exercise.MEDITATION).withBreathing(100);
        assertEquals(20,active.fullEnergyTicks());
        var stale=complete(HamonProgress.initial().withBreathing(100),HamonProgress.Exercise.MEDITATION);
        stale=stale.withEnergy(20).withStats(40,40).learn("zoom_punch").withTutorial(false);
        assertEquals(40,stale.fullEnergyTicks());assertEquals(0,stale.effectiveCompletedExercises());
        var partial=HamonProgress.initial().exercise(HamonProgress.Exercise.MEDITATION,749).withBreathing(1);
        assertTrue(partial.exerciseComplete(HamonProgress.Exercise.MEDITATION));
        assertFalse(partial.exerciseBonusActive(HamonProgress.Exercise.MEDITATION));
        assertEquals(-.25F,partial.dayIncrease(false,true,1));
        partial=complete(partial,HamonProgress.Exercise.RUNNING);
        assertTrue(partial.exerciseBonusActive(HamonProgress.Exercise.MEDITATION));
    }
    @Test void legacyConstructorRefreshesSavedCountersAtLevel100() {
        var original=complete(HamonProgress.initial().withBreathing(100).learn("zoom_punch").withEnergy(25),HamonProgress.Exercise.values());
        var loaded=new HamonProgress(original.hamon(),original.strengthPoints(),original.controlPoints(),original.pointsFraction(),
                original.breathingLevel(),original.energy(),original.breathStability(),original.learnedSkills(),original.technique(),
                original.exercises(),original.trainingBonus(),original.skipDays(),original.energyTicks(),original.maskTicks(),original.noRecoveryTicks(),original.tcsa());
        assertEquals(0,original.effectiveCompletedExercises());assertEquals(4,loaded.effectiveCompletedExercises());
        assertEquals(20,loaded.fullEnergyTicks());assertEquals(25,loaded.energy());
        assertEquals(original.learnedSkills(),loaded.learnedSkills());assertEquals(original.exercises(),loaded.exercises());
    }
    @Test void learningRequiresAvailablePointsParentsAndServerTeacherSkillUnion() {
        var state=leveled(5,0);
        assertEquals("hamon.closed.teacher.required",HamonSkills.learningProblem(state,"zoom_punch",List.of(),false,false,SLOTS));
        assertEquals("hamon.closed.teacher.no_skill",HamonSkills.learningProblem(state,"zoom_punch",List.of("overdrive"),true,false,SLOTS));
        assertEquals("",HamonSkills.learningProblem(state,"zoom_punch",List.of("zoom_punch"),true,false,SLOTS));
        assertEquals("hamon.closed.parents",HamonSkills.learningProblem(state,"jump",List.of("jump"),true,false,SLOTS));
        state=state.learn("zoom_punch");assertEquals(0,state.skillPoints(HamonSkills.Stat.STRENGTH));
        assertEquals("hamon.closed.points",HamonSkills.learningProblem(state,"jump",List.of("jump"),true,false,SLOTS));
        assertTrue(state.resetSkills("strength").hasSkill("overdrive"));assertTrue(state.resetSkills("strength").hasSkill("healing"));
    }
    @Test void techniquePerksDoNotSpendSlotsAndEachSlotRequiresBothStats() {
        var state=leveled(20,19).withTechnique("jonathan").learn("zoom_punch");
        assertTrue(state.hasSkill("natural_talent"));assertTrue(state.techniqueSkills().isEmpty());
        assertEquals("hamon.closed.technique.locked",HamonSkills.learningProblem(state,"overdrive_barrage",List.of(),false,false,SLOTS));
        state=state.withStats(HamonProgress.pointsAtLevel(20),HamonProgress.pointsAtLevel(20));
        assertEquals("",HamonSkills.learningProblem(state,"overdrive_barrage",List.of(),false,false,SLOTS));
        assertEquals("hamon.closed.technique.bug",HamonSkills.learningProblem(state,"aja_stone_keeper",List.of(),false,false,SLOTS));
        assertEquals("",HamonSkills.learningProblem(state,"aja_stone_keeper",List.of(),false,true,SLOTS));
        assertEquals("hamon.closed.technique.bug",HamonSkills.learningProblem(state,"cheat_death",List.of(),false,true,SLOTS));
        assertEquals("hamon.closed.technique.locked",HamonSkills.learningProblem(state,"overdrive_barrage",List.of(),false,false,List.of()));
        var reset=state.learn("overdrive_barrage").resetSkills("technique");
        assertFalse(reset.hasSkill("natural_talent"));assertFalse(reset.hasSkill("overdrive_barrage"));assertTrue(reset.hasSkill("zoom_punch"));
    }
    @Test void registeredSkillGraphRetainsAllOriginalDefinitionsAndNoUnknownDependencies() {
        assertEquals(46,HamonSkills.all().size());assertEquals(26,HamonSkills.all().stream().filter(HamonSkills.Skill::base).count());
        assertEquals(5,HamonSkills.techniques().size());assertEquals(3,HamonSkills.all().stream().filter(HamonSkills.Skill::perk).count());
        for(var skill:HamonSkills.all())for(var prerequisite:skill.prerequisites())assertNotNull(HamonSkills.skill(prerequisite),skill.id());
        assertEquals(List.of("bubble_launcher","arrow_infusion"),HamonSkills.skill("bubble_cutter").prerequisites());
        assertEquals("",HamonSkills.skill("syo_barrage").technique());
        assertEquals(.2F,HamonSkills.efficiencyBonus("jonathan","sendo_overdrive"));
        assertEquals(0,HamonSkills.efficiencyBonus("jonathan","scarlet_overdrive"));
    }
    @Test void stateCopiesPreventMutationThroughInputCollectionsAndRejectNonfiniteSaveValues() {
        var initial=HamonProgress.initial();
        assertThrows(UnsupportedOperationException.class,()->initial.exercises().set(0,100));
        assertThrows(UnsupportedOperationException.class,()->initial.learnedSkills().add("jump"));
        assertThrows(IllegalArgumentException.class,()->initial.withEnergy(Float.NaN));
        assertEquals(0,HamonProgress.EMPTY.energyEfficiency(0,false));
    }
}
