package com.github.standobyte.jojo.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StandLogicTest {
    @Test void starPlatinumBaseCombatMatchesUpstream() {
        assertEquals(4F, StandStatFormulas.getLightAttackDamage(16));
        assertEquals(16F, StandStatFormulas.getHeavyAttackDamage(16));
        assertEquals(14, StandStatFormulas.getHeavyAttackWindup(16, 0));
        assertEquals(7, StandStatFormulas.getHeavyAttackWindup(16, 1));
        assertEquals(15, StandStatFormulas.getHeavyAttackRecovery(16, 0));
        assertEquals(7, StandStatFormulas.getHeavyAttackRecovery(16, 1));
        assertEquals(108, StandStatFormulas.getBarrageHitsPerSecond(16));
        assertEquals(100, StandStatFormulas.getBarrageMaxDuration(16));
    }

    @Test void randomWindupRetainsProbabilisticRounding() {
        assertEquals(1, StandStatFormulas.getLightAttackWindup(18, 0, 0, false, () -> 0.75));
        assertEquals(2, StandStatFormulas.getLightAttackWindup(18, 0, 0, false, () -> 0.25));
        assertEquals(1, StandStatFormulas.getLightAttackWindup(16, 0, 0, true, () -> 0.5));
        assertEquals(0, StandStatFormulas.getLightAttackWindup(24, 0, 0, false, () -> 0));
    }

    @Test void rangeAndBlockBreakingRetainBoundaryRules() {
        assertEquals(1F, StandStatFormulas.rangeStrengthFactor(2, 10, 2));
        assertEquals(0.25F, StandStatFormulas.rangeStrengthFactor(2, 10, 100));
        assertFalse(StandStatFormulas.isBlockBreakable(16, -1, 0));
        assertTrue(StandStatFormulas.isBlockBreakable(16, 50, 3));
        assertFalse(StandStatFormulas.isBlockBreakable(16, 50, 4));
        assertEquals(1.2F, StandStatFormulas.getStandBreakBlockHardness(2, false));
    }

    @Test void acquisitionAndStaminaMatchBaseHumanRules() {
        StandProgress survival = StandProgress.grantStarPlatinum(false);
        assertEquals(0, survival.stamina());
        assertEquals(2000, survival.maxStamina());
        assertEquals(7.5F, survival.regenerate(false, true, 20, false).stamina());
        assertEquals(3F, survival.regenerate(true, true, 17, false).stamina());
        assertEquals(0, survival.regenerate(true, false, 20, false).stamina());
        assertEquals(2000, StandProgress.grantStarPlatinum(true).stamina());
        assertEquals(2250, StandStats.STAR_PLATINUM.maxStamina(1));
        assertEquals(2000, survival.withStamina(1999).regenerate(false, true, 20, false).stamina());
    }

    @Test void invalidStateCannotPoisonSavesOrStamina() {
        assertThrows(IllegalArgumentException.class, () -> new StandProgress("jojo:star_platinum", Float.NaN, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new StandProgress("jojo:star_platinum", 0, 2, true));
        assertThrows(IllegalArgumentException.class, () -> StandProgress.EMPTY.withStamina(Float.POSITIVE_INFINITY));
        assertEquals(StandProgress.EMPTY, StandProgress.EMPTY.regenerate(false, true, 20, false));
    }

    @Test void attackStrikesOnceAndPreventsWindupSpam() {
        AttackTimeline action = new AttackTimeline();
        assertTrue(action.start(false, 2, 4, 16));
        assertFalse(action.start(false, 2, 4, 16));
        assertFalse(action.tick());
        assertTrue(action.tick());
        for (int i = 0; i < 4; i++) assertFalse(action.tick());
        assertEquals(AttackTimeline.Phase.RECOVERY, action.phase());
        assertTrue(action.start(false, 1, 4, 16));
        action.cancel();
        assertFalse(action.tick());
    }

    @Test void heavyRecoveryCannotBeCancelledByAnotherAttack() {
        AttackTimeline action = new AttackTimeline();
        assertTrue(action.start(true, 0, 1, 2));
        assertTrue(action.tick());
        assertFalse(action.tick());
        assertFalse(action.canStart());
        assertFalse(action.start(false, 1, 1, 0));
        assertFalse(action.tick());
        assertFalse(action.tick());
        assertTrue(action.canStart());
    }
}
