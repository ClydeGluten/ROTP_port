package com.github.standobyte.jojo.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VampireProgressTest {
    @Test void originalWeakAndMaskStateKeepIndependentRules() {
        var weak = VampireProgress.transform(false);
        assertEquals(300, weak.blood());
        assertFalse(weak.fullPower());
        assertEquals(VampireProgress.EMPTY, weak.afterDeath());
        var full = weak.withBlood(671).withFullPower();
        assertEquals(671, full.blood(), "Mask upgrading a weak vampire preserves blood");
        assertEquals(full, full.afterDeath());
        assertEquals(VampireProgress.transform(true), VampireProgress.EMPTY.withFullPower());
    }
    @Test void passiveTiersUseOriginalFloorAndWeakPenalty() {
        for (int difficulty = 1; difficulty <= 3; difficulty++) {
            assertEquals(difficulty + 4, VampireProgress.transform(true).withBlood(1000).bloodLevel(difficulty));
            assertEquals(Math.max(difficulty + 2, 1), VampireProgress.transform(false).withBlood(1000).bloodLevel(difficulty));
            assertEquals(difficulty, VampireProgress.transform(true).withBlood(199.99F).bloodLevel(difficulty));
            assertEquals(difficulty + 1, VampireProgress.transform(true).withBlood(200).bloodLevel(difficulty));
        }
        assertEquals(-1, VampireProgress.transform(true).bloodLevel(0));
    }
    @Test void difficultyDefaultsMatchOriginalBloodEconomy() {
        var full = VampireProgress.transform(true);
        assertEquals(300 - .13889F, full.tick(0, false).blood());
        assertEquals(300 - .00278F, full.tick(1, false).blood());
        assertEquals(300 - .00278F, full.tick(2, false).blood());
        assertEquals(300, full.tick(3, false).blood());
        assertEquals(full, full.tick(0, true));
        assertEquals(1.75F, VampireProgress.drainGain(2));
        assertEquals(2, VampireProgress.healCost(2));
        assertEquals(0, full.withBlood(0).tick(0, false).blood());
    }
    @Test void malformedPersistentStateCannotCreateInfiniteResources() {
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1, 1001})
            assertThrows(IllegalArgumentException.class, () -> new VampireProgress(true, true, value));
        assertThrows(IllegalArgumentException.class, () -> new VampireProgress(false, true, 0));
        assertThrows(IllegalArgumentException.class, () -> new VampireProgress(false, false, 1));
        assertThrows(IllegalArgumentException.class, () -> VampireProgress.transform(true).withBlood(Float.NaN));
    }
}
