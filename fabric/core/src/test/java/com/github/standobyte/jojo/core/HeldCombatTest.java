package com.github.standobyte.jojo.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeldCombatTest {
    @Test void barrageKeepsUpstreamRateAndCosts() {
        var held = new HeldCombat();
        assertTrue(held.start(HeldCombat.Mode.BARRAGE, 100, 5));
        int hits = 0;
        for (int tick = 0; tick < 100; tick++) {
            if (tick % 5 == 0) held.refresh();
            hits += held.tick(StandStatFormulas.getBarrageHitsPerSecond(16));
        }
        assertEquals(540, hits);
        assertEquals(400, HeldCombat.BARRAGE_STAMINA_PER_TICK * 100);
        assertEquals(0.2625F, StandStatFormulas.getBarrageHitDamage(16, 20), 0.00001F);
        held.refresh();
        assertEquals(0, held.tick(108));
        assertEquals(HeldCombat.Mode.NONE, held.mode());
        assertFalse(held.canStart());
        for (int tick = 0; tick < 5; tick++) held.tick(108);
        assertTrue(held.canStart());
    }

    @Test void repeatedStartsCannotResetTheLimit() {
        var held = new HeldCombat();
        held.start(HeldCombat.Mode.BARRAGE, 3, 2);
        for (int tick = 0; tick < 3; tick++) {
            assertFalse(held.start(HeldCombat.Mode.BARRAGE, 100, 2));
            held.refresh();
            held.tick(108);
        }
        held.refresh();
        held.tick(108);
        assertEquals(HeldCombat.Mode.NONE, held.mode());
    }

    @Test void lostInputExpiresAndReleaseIsIdempotent() {
        var held = new HeldCombat();
        held.start(HeldCombat.Mode.BARRAGE, 100, 5);
        for (int tick = 0; tick <= HeldCombat.LEASE_TICKS; tick++) held.tick(108);
        assertEquals(HeldCombat.Mode.NONE, held.mode());
        held.tick(108);
        held.stop();
        assertEquals(4, held.recoveryTicks(), "Repeated STOP must not reset recovery");
        for (int tick = 0; tick < 4; tick++) held.tick(108);
        held.start(HeldCombat.Mode.GUARD, Integer.MAX_VALUE, 0);
        held.stop();
        assertTrue(held.canStart());
    }

    @Test void guardUsesOriginalCostAndBreakRecovery() {
        var full = HeldCombat.guard(10, 100, 16, 16, false);
        assertEquals(50, full.stamina());
        assertEquals(0.4F, full.damage(), 0.00001F);
        assertFalse(full.broken());
        var partial = HeldCombat.guard(10, 25, 16, 16, false);
        assertEquals(0, partial.stamina());
        assertEquals(1.2F, partial.damage(), 0.00001F);
        assertTrue(partial.broken());
        var held = new HeldCombat();
        held.start(HeldCombat.Mode.GUARD, Integer.MAX_VALUE, 0);
        held.breakGuard(80);
        assertFalse(held.canStart());
        for (int tick = 0; tick < 80; tick++) held.tick(0);
        assertTrue(held.canStart());
    }

    @Test void creativeGuardDoesNotSpendStaminaOrBreak() {
        var result = HeldCombat.guard(100, 0, 16, 16, true);
        assertEquals(0, result.stamina());
        assertFalse(result.broken());
        assertThrows(IllegalArgumentException.class, () -> HeldCombat.guard(Float.NaN, 1, 16, 16, false));
    }
}
