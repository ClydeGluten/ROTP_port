package com.github.standobyte.jojo.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class StandAllocationTest {
    private static final String SP = StandProgress.STAR_PLATINUM;
    private static final String TW = StandProgress.THE_WORLD;

    @Test
    void coreSequence_preferenceHonoredWhenUnused() {
        var cycle = StandAllocation.StandCycle.INITIAL;
        var result = StandAllocation.allocate(cycle, 1, List.of(SP,TW), new Random(1));
        assertEquals(SP, result.standId(), "Provisional 1 should receive Star Platinum when unused");
        assertEquals(1, result.newCycle().generation());
        assertEquals(List.of(SP), result.newCycle().reserved());
        assertFalse(result.newGenerationStarted());
    }

    @Test
    void coreSequence_fallbackToAvailableWhenPreferenceUsed() {
        var cycle = new StandAllocation.StandCycle(1, List.of(SP));
        var result = StandAllocation.allocate(cycle, 1, List.of(SP,TW), new Random(1));
        assertEquals(TW, result.standId(), "Should fallback to remaining unused Stand (The World) when preference is used");
        assertEquals(1, result.newCycle().generation());
        assertEquals(List.of(SP, TW), result.newCycle().reserved());
        assertFalse(result.newGenerationStarted());
    }

    @Test
    void coreSequence_advancesGenerationWhenAllReserved() {
        var cycle = new StandAllocation.StandCycle(1, List.of(SP, TW));
        var result = StandAllocation.allocate(cycle, 1, List.of(SP,TW), new Random(1));
        assertEquals(SP, result.standId(), "Preference should be honored in new generation");
        assertEquals(2, result.newCycle().generation(), "Generation should increment when pool was exhausted");
        assertEquals(List.of(SP), result.newCycle().reserved());
        assertTrue(result.newGenerationStarted(), "New generation flag should be true");
    }

    @Test
    void coreSequence_fullGenerationsCycleWithoutDuplicates() {
        var cycle = StandAllocation.StandCycle.INITIAL;
        var catalog=StandAllocation.DEFAULT_CATALOG;
        for (int gen=1;gen<=5;gen++) {
            var received=new java.util.HashSet<String>();
            for(int i=0;i<catalog.size();i++) {
                var next=StandAllocation.allocate(cycle,1,catalog,new Random(i));
                assertTrue(received.add(next.standId()),"No duplicate before every playable Stand awakens");
                assertEquals(gen,next.newCycle().generation());
                cycle=next.newCycle();
            }
            assertEquals(new java.util.HashSet<>(catalog),received);
        }
    }

    @Test
    void invalidMetadata_nonPositiveGenerationSanitized() {
        var invalidCycle = new StandAllocation.StandCycle(0, List.of());
        assertEquals(1, invalidCycle.generation(), "Generation <= 0 should clamp to 1");

        var negativeCycle = new StandAllocation.StandCycle(-10, List.of());
        assertEquals(1, negativeCycle.generation(), "Negative generation should clamp to 1");

        var result = StandAllocation.allocate(negativeCycle, 2, List.of(SP,TW), new Random(1));
        assertEquals(TW, result.standId());
        assertEquals(1, result.newCycle().generation());
    }

    @Test
    void invalidMetadata_outOfRangeProvisionalIndex() {
        var cycle = StandAllocation.StandCycle.INITIAL;
        var fixedRandom = new Random(42);

        // Provisional index 0 (out of 1..n range)
        var resultZero = StandAllocation.allocate(cycle, 0, StandAllocation.DEFAULT_CATALOG, fixedRandom);
        assertNotNull(resultZero.standId());
        assertTrue(StandAllocation.DEFAULT_CATALOG.contains(resultZero.standId()));

        // Provisional index -5
        var resultNeg = StandAllocation.allocate(cycle, -5, StandAllocation.DEFAULT_CATALOG, fixedRandom);
        assertNotNull(resultNeg.standId());
        assertTrue(StandAllocation.DEFAULT_CATALOG.contains(resultNeg.standId()));

        // Provisional index 100
        var resultHigh = StandAllocation.allocate(cycle, 100, StandAllocation.DEFAULT_CATALOG, fixedRandom);
        assertNotNull(resultHigh.standId());
        assertTrue(StandAllocation.DEFAULT_CATALOG.contains(resultHigh.standId()));
    }

    @Test
    void invalidMetadata_corruptedOrUnknownStandsInReserved() {
        // Reserved list contains unknown stands that do not exist in the current catalog
        var corrupted = new StandAllocation.StandCycle(1, List.of("jojo:silver_chariot", "jojo:magicians_red", SP));
        // Available should only see SP as used; TW should still be available in this generation
        var result = StandAllocation.allocate(corrupted, 1, List.of(SP,TW), new Random(1));
        assertEquals(TW, result.standId(), "Unknown reserved stands must not prevent remaining catalog stand from being allocated");
        assertEquals(1, result.newCycle().generation(), "Generation should not advance prematurely due to invalid reserved IDs");
        assertEquals(List.of(SP, TW), result.newCycle().reserved(), "Reserved list should only retain valid catalog stands");
    }

    @Test
    void catalogGrowth_preservesCycleUntilExpandedPoolExhausted() {
        String cd = "jojo:crazy_diamond";
        var expandedCatalog = List.of(SP, TW, cd);

        // Previous cycle had both SP and TW reserved under the old 2-stand catalog
        var previousCycle = new StandAllocation.StandCycle(1, List.of(SP, TW));

        // When allocating with the expanded 3-stand catalog:
        var result = StandAllocation.allocate(previousCycle, 1, expandedCatalog, new Random(1));
        assertEquals(cd, result.standId(), "Newly added Stand should be allocated to complete the generation");
        assertEquals(1, result.newCycle().generation(), "Should remain in generation 1 until expanded pool is full");
        assertEquals(List.of(SP, TW, cd), result.newCycle().reserved());
        assertFalse(result.newGenerationStarted());

        // Now that all 3 are reserved, next allocation must advance to generation 2
        var nextResult = StandAllocation.allocate(result.newCycle(), 1, expandedCatalog, new Random(1));
        assertEquals(SP, nextResult.standId());
        assertEquals(2, nextResult.newCycle().generation(), "Generation should increment to 2");
        assertEquals(List.of(SP), nextResult.newCycle().reserved());
        assertTrue(nextResult.newGenerationStarted());
    }

    @Test
    void seeding_existingOwnerPreventsDuplicateOnNextArrow() {
        var cycle = StandAllocation.StandCycle.INITIAL;
        // Old owner with Star Platinum joins
        var seeded = StandAllocation.seed(cycle, SP);
        assertEquals(List.of(SP), seeded.reserved(), "Seeding should reserve old owner's Stand in current generation");

        // First new arrow awakening requests provisional 1 (SP)
        var arrowResult = StandAllocation.allocate(seeded, 1, List.of(SP,TW), new Random(1));
        assertEquals(TW, arrowResult.standId(), "First new arrow awakening should receive The World, avoiding duplicate Star Platinum");
        assertEquals(List.of(SP, TW), arrowResult.newCycle().reserved());
    }

    @Test
    void seeding_isIdempotentAcrossRelogin() {
        var cycle = StandAllocation.StandCycle.INITIAL;
        var seededOnce = StandAllocation.seed(cycle, SP);
        var seededTwice = StandAllocation.seed(seededOnce, SP);
        assertEquals(seededOnce, seededTwice, "Re-seeding the same Stand should not duplicate or modify the cycle");
    }

    @Test
    void seeding_unknownStandIgnored() {
        var cycle = StandAllocation.StandCycle.INITIAL;
        var seeded = StandAllocation.seed(cycle, "jojo:unknown");
        assertEquals(cycle, seeded, "Unknown Stand ID should not be seeded");
    }
}
