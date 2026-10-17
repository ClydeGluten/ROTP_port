package com.github.standobyte.jojo.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResolveProgressTest {
    @Test void thresholdsAndUnlocksMatchStarPlatinum() {
        float[] expected = {2500, 10000, 25000, 50000, 32500};
        for (int level = 0; level <= 4; level++) {
            var state = ResolveProgress.EMPTY.withLevel(level);
            assertEquals(expected[level], state.maxResolve());
            assertEquals(level / 4F, state.development());
            for (int required = 0; required <= 4; required++) {
                assertEquals(level >= required, state.unlocked(required, false));
                assertTrue(state.unlocked(required, true));
            }
        }
    }
    @Test void attackBoostAppliesAfterAwardAndCapsAtFive() {
        var state = ResolveProgress.EMPTY.attack(10, 20);
        assertEquals(10, state.resolve()); assertEquals(1.5F, state.attackBoost());
        state = state.attack(10, 20);
        assertEquals(25, state.resolve()); assertEquals(2, state.attackBoost());
        assertEquals(5, state.attack(100, 20).attackBoost());
    }
    @Test void missingHealthUsesLowestRecordedHealth() {
        assertEquals(100, ResolveProgress.EMPTY.attack(10, 5).resolve());
        assertEquals(55, ResolveProgress.EMPTY.attack(10, 10).resolve());
        var state = ResolveProgress.EMPTY.attacked(15, 20, 20);
        assertEquals(5, state.healthRecord()); assertEquals(15, state.resolve());
        assertEquals(115, state.attack(10, 20).resolve());
    }
    @Test void damageRecordSurvivesBeforeFirstResolveGainUntilFullRecovery() {
        var state = ResolveProgress.EMPTY.attacked(7, 20, 20).tick(false, false);
        assertEquals(0, state.resolve()); assertEquals(13, state.healthRecord());
        assertEquals(28, state.attack(10, 14).resolve(), 0.0001F);
        assertEquals(-1, state.tick(false, true).healthRecord());
    }
    @Test void recordsRestoreFasterAndStoreTenBestPeaks() {
        var state = ResolveProgress.EMPTY.attack(100, 20).resetCombat(true);
        assertEquals(List.of(100F), state.records());
        assertEquals(30, state.attack(10, 20).resolve());
        for (int i = 0; i < 12; i++) state = state.attack(50 + i, 20).resetCombat(true);
        assertEquals(10, state.records().size());
        var source = new ArrayList<>(List.of(100F));
        var immutable = new ResolveProgress(0, 0, 0, 1, -1, source, true, 0, 0, 0);
        source.clear(); assertEquals(List.of(100F), immutable.records());
        assertThrows(UnsupportedOperationException.class, () -> immutable.records().clear());
    }
    @Test void decayWaitsTwentySecondsSummonedOrTenDismissed() {
        var state = ResolveProgress.EMPTY.attack(10, 20);
        for (int i = 0; i < 399; i++) state = state.tick(true, true);
        assertEquals(10, state.resolve()); assertEquals(1, state.noDecayTicks());
        state = state.tick(true, true); assertEquals(List.of(10F), state.records());
        state = state.tick(true, true); assertEquals(8, state.resolve()); assertEquals(1, state.attackBoost());
        state = ResolveProgress.EMPTY.attack(10, 20);
        for (int i = 0; i < 200; i++) state = state.tick(false, true);
        assertEquals(0, state.noDecayTicks()); assertEquals(List.of(10F), state.records());
        assertEquals(8, state.tick(false, true).resolve());
    }
    @Test void thresholdStartsOneBurstAndAdvancesOneLevel() {
        var state = ResolveProgress.EMPTY.attack(2500, 20);
        assertEquals(1, state.level()); assertEquals(10000, state.resolve());
        assertEquals(600, state.burstTicks()); assertEquals(0, state.burstLevel());
        state = state.attack(100000, 20); assertEquals(1, state.level());
        assertEquals(0.6667F, state.damageReduction());
        for (int i = 0; i < 301; i++) state = state.tick(true, true);
        assertFalse(state.isBurst()); assertEquals(0, state.resolve()); assertEquals(1, state.level());
        assertTrue(state.records().isEmpty());
    }
    @Test void maxLevelCanTriggerAnotherBurstWithoutOverflow() {
        var state = ResolveProgress.EMPTY.withLevel(4).attack(32500, 20);
        assertEquals(4, state.level()); assertEquals(2400, state.burstTicks());
        assertEquals(4, state.burstLevel()); assertTrue(state.isBurst());
    }
    @Test void damageReductionScalesWithMeter() {
        assertEquals(0, ResolveProgress.EMPTY.damageReduction());
        assertEquals(0.33335F, ResolveProgress.EMPTY.attack(1250, 20).damageReduction(), 0.00001F);
    }
    @Test void timeStopTrainsOnlyActualTicksAndClampsAtFiveSeconds() {
        var state = ResolveProgress.EMPTY.withLevel(4);
        assertEquals(5, state.timeStopTicks());
        state = state.trainTimeStop(5); assertEquals(1.25F, state.trainingPoints()); assertEquals(6, state.timeStopTicks());
        assertEquals(state, state.trainTimeStop(0));
        assertEquals(0, ResolveProgress.EMPTY.trainTimeStop(100).trainingPoints());
        state = state.trainTimeStop(10000); assertEquals(95, state.trainingPoints()); assertEquals(100, state.timeStopTicks());
        var vampire = state.trainTimeStop(400, 0.25F, 175);
        assertEquals(175, vampire.trainingPoints()); assertEquals(180, vampire.timeStopTicks());
        var lowBlood = vampire.trainTimeStop(20, 0.25F, 95);
        assertEquals(180, lowBlood.trainingPoints()); assertEquals(180, lowBlood.timeStopTicks());
        assertEquals(185, lowBlood.trainTimeStop(20).trainingPoints());
    }
    @Test void resolveBurstQuadruplesTrainingAndDeathKeepsIt() {
        var state = ResolveProgress.EMPTY.withLevel(4).attack(32500, 20).trainTimeStop(5);
        assertEquals(5, state.trainingPoints());
        var dead = state.resetCombat(true);
        assertEquals(4, dead.level()); assertEquals(5, dead.trainingPoints());
        assertFalse(dead.isBurst()); assertEquals(0, dead.resolve()); assertEquals(1, dead.attackBoost());
        assertEquals(List.of(32500F), dead.records());
    }
    @Test void invalidPersistentValuesCannotCreateProgress() {
        assertThrows(IllegalArgumentException.class, () -> ResolveProgress.EMPTY.withLevel(5));
        assertThrows(IllegalArgumentException.class, () -> ResolveProgress.EMPTY.withTraining(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ResolveProgress(0, 2501, 0, 1, -1, List.of(), true, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResolveProgress(0, 0, 0, 1, -1, List.of(Float.NaN), true, 0, 0, 0));
        assertEquals(ResolveProgress.EMPTY, ResolveProgress.EMPTY.attack(Float.NaN, 20));
    }
}
