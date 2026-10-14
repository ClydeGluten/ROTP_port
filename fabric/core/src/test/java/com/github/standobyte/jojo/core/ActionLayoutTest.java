package com.github.standobyte.jojo.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ActionLayoutTest {

    // Shared "Stand" defaults used throughout the tests.
    private static final List<String> DEF_ATK = List.of("ora", "star_finger", "rush");
    private static final List<String> DEF_ABL = List.of("time_stop", "punch", "throw");

    /* -----------------------------------------------------------------------
     * 1. Default restore (null saved data)
     * --------------------------------------------------------------------- */

    @Test void defaultRestoreProducesDefaultOrder() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        assertEquals(DEF_ATK, layout.attacks());
        assertEquals(DEF_ABL, layout.abilities());
        assertEquals("ora",       layout.selectedAttack());
        assertEquals("time_stop", layout.selectedAbility());
    }

    /* -----------------------------------------------------------------------
     * 2. Saved re-order is faithfully restored
     * --------------------------------------------------------------------- */

    @Test void savedReorderIsRestored() {
        var savedAtk = List.of("rush", "ora", "star_finger");
        var layout = ActionLayout.restore(savedAtk, null, "rush", null, DEF_ATK, DEF_ABL);
        assertEquals(savedAtk, layout.attacks());
        assertEquals("rush", layout.selectedAttack());
        // Abilities fall back to defaults when null saved.
        assertEquals(DEF_ABL, layout.abilities());
    }

    /* -----------------------------------------------------------------------
     * 3. Corrupt / null entries in saved list are dropped
     * --------------------------------------------------------------------- */

    @Test void corruptEntriesDropped() {
        // Build a raw list with nulls and blank strings via reflection-free workaround.
        var corrupt = new ArrayList<String>();
        corrupt.add(null);
        corrupt.add("");
        corrupt.add("   ");
        corrupt.add("ora");
        corrupt.add("rush");
        var layout = ActionLayout.restore(corrupt, null, null, null, DEF_ATK, DEF_ABL);
        // Remaining: ora, rush; missing default star_finger appended.
        assertEquals(List.of("ora", "rush", "star_finger"), layout.attacks());
    }

    /* -----------------------------------------------------------------------
     * 4. Duplicate IDs are removed (first occurrence wins)
     * --------------------------------------------------------------------- */

    @Test void duplicatesRemovedPreservingOrder() {
        var saved = List.of("rush", "ora", "rush", "star_finger", "ora");
        var layout = ActionLayout.restore(saved, null, null, null, DEF_ATK, DEF_ABL);
        assertEquals(List.of("rush", "ora", "star_finger"), layout.attacks());
    }

    /* -----------------------------------------------------------------------
     * 5. Missing default IDs are appended at the end
     * --------------------------------------------------------------------- */

    @Test void missingDefaultsAreAppended() {
        var partial = List.of("rush");
        var layout = ActionLayout.restore(partial, null, null, null, DEF_ATK, DEF_ABL);
        // ora and star_finger come after rush in original default order.
        assertEquals(List.of("rush", "ora", "star_finger"), layout.attacks());
    }

    /* -----------------------------------------------------------------------
     * 6. Wrong-row IDs are rejected
     * --------------------------------------------------------------------- */

    @Test void wrongRowIdsRejected() {
        // "time_stop" and "punch" belong to abilities, not attacks.
        var mixed = List.of("ora", "time_stop", "punch", "star_finger");
        var layout = ActionLayout.restore(mixed, null, null, null, DEF_ATK, DEF_ABL);
        // Only the attack IDs survive; rush is missing so it's appended.
        assertEquals(List.of("ora", "star_finger", "rush"), layout.attacks());
    }

    /* -----------------------------------------------------------------------
     * 7. Unknown IDs are rejected
     * --------------------------------------------------------------------- */

    @Test void unknownIdsRejected() {
        var withUnknown = List.of("ora", "UNKNOWN_ACTION", "rush", "???");
        var layout = ActionLayout.restore(withUnknown, null, null, null, DEF_ATK, DEF_ABL);
        assertEquals(List.of("ora", "rush", "star_finger"), layout.attacks());
    }

    /* -----------------------------------------------------------------------
     * 8. Invalid selection is fixed to first ID
     * --------------------------------------------------------------------- */

    @Test void invalidSelectionFixedToFirst() {
        var layout = ActionLayout.restore(null, null, "GHOST", null, DEF_ATK, DEF_ABL);
        assertEquals("ora", layout.selectedAttack());
    }

    /* -----------------------------------------------------------------------
     * 9. Empty row selects empty string
     * --------------------------------------------------------------------- */

    @Test void emptyRowSelectsEmptyString() {
        var layout = ActionLayout.restore(
                List.of(), List.of(), null, null,
                List.of(), List.of());
        assertEquals("", layout.selectedAttack());
        assertEquals("", layout.selectedAbility());
    }

    /* -----------------------------------------------------------------------
     * 10. Both rows are independent
     * --------------------------------------------------------------------- */

    @Test void rowsAreIndependent() {
        var savedAtk = List.of("rush", "ora", "star_finger");
        var savedAbl = List.of("punch", "throw", "time_stop");
        var layout = ActionLayout.restore(savedAtk, savedAbl, "rush", "punch", DEF_ATK, DEF_ABL);
        assertEquals(savedAtk, layout.attacks());
        assertEquals(savedAbl, layout.abilities());
        assertEquals("rush",  layout.selectedAttack());
        assertEquals("punch", layout.selectedAbility());
    }

    /* -----------------------------------------------------------------------
     * 11. select() – valid ID changes selection; invalid is no-op
     * --------------------------------------------------------------------- */

    @Test void selectValidId() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        var updated = layout.select(true, "star_finger");
        assertEquals("star_finger", updated.selectedAttack());
        // Abilities unchanged.
        assertEquals(layout.selectedAbility(), updated.selectedAbility());
    }

    @Test void selectUnknownIdIsNoOp() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        assertSame(layout, layout.select(true, "NOPE"));
        assertSame(layout, layout.select(true, null));
    }

    /* -----------------------------------------------------------------------
     * 12. move() – clamped reordering; selection sticks to moved ID
     * --------------------------------------------------------------------- */

    @Test void moveForwardClampsAndSticks() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        // ora is at index 0; move +100 → clamps to last index (2).
        var moved = layout.move(true, "ora", 100);
        assertEquals(List.of("star_finger", "rush", "ora"), moved.attacks());
        assertEquals("ora", moved.selectedAttack());
    }

    @Test void moveBackwardClampsAndSticks() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        // rush is at index 2; move -100 → index 0.
        var moved = layout.move(true, "rush", -100);
        assertEquals(List.of("rush", "ora", "star_finger"), moved.attacks());
        assertEquals("rush", moved.selectedAttack());
    }

    @Test void moveNonExistentIdIsNoOp() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        assertSame(layout, layout.move(true, "GHOST", 1));
    }

    @Test void moveZeroDeltaIsNoOp() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        assertSame(layout, layout.move(true, "ora", 0));
    }

    @Test void moveSelectedIdSelectionFollows() {
        // Start with "rush" selected; move it one step back.
        var layout = ActionLayout.restore(null, null, "rush", null, DEF_ATK, DEF_ABL);
        assertEquals("rush", layout.selectedAttack());
        var moved = layout.move(true, "rush", -1);
        assertEquals("rush", moved.selectedAttack());
        assertEquals(1, moved.attacks().indexOf("rush"));
    }

    /* -----------------------------------------------------------------------
     * 13. cycle() – wraps and skips locked IDs
     * --------------------------------------------------------------------- */

    @Test void cycleForwardWrapsAround() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL)
                .select(true, "rush"); // rush is last
        var cycled = layout.cycle(true, +1, id -> true);
        assertEquals("ora", cycled.selectedAttack()); // wraps to first
    }

    @Test void cycleBackwardWrapsAround() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL); // ora is first
        var cycled = layout.cycle(true, -1, id -> true);
        assertEquals("rush", cycled.selectedAttack()); // wraps to last
    }

    @Test void cycleSkipsLockedIDs() {
        // Unlock only "rush"; cycling from "ora" forward should jump over
        // "star_finger" and land on "rush".
        Set<String> unlocked = Set.of("ora", "rush");
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL); // selected: ora
        var cycled = layout.cycle(true, +1, unlocked::contains);
        // star_finger is skipped; rush is the next unlocked.
        assertEquals("rush", cycled.selectedAttack());
    }

    @Test void cycleAllLockedIsNoOp() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        var result = layout.cycle(true, +1, id -> false);
        assertSame(layout, result);
    }

    @Test void cycleEmptyRowIsNoOp() {
        var layout = ActionLayout.restore(List.of(), List.of(), null, null,
                List.of(), List.of());
        assertSame(layout, layout.cycle(true, +1, id -> true));
    }

    @Test void cycleZeroDirectionIsNoOp() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        assertSame(layout, layout.cycle(true, 0, id -> true));
    }

    /* -----------------------------------------------------------------------
     * 14. selected(row, unlocked) – fallback to first unlocked
     * --------------------------------------------------------------------- */

    @Test void selectedReturnsCurrentIfUnlocked() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL); // selectedAttack = "ora"
        assertEquals("ora", layout.selected(true, id -> true));
    }

    @Test void selectedFallsBackToFirstUnlocked() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL); // selectedAttack = "ora"
        // "ora" is locked; first unlocked is "star_finger".
        Set<String> unlocked = Set.of("star_finger", "rush");
        assertEquals("star_finger", layout.selected(true, unlocked::contains));
    }

    @Test void selectedReturnsEmptyIfAllLocked() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        assertEquals("", layout.selected(true, id -> false));
    }

    /* -----------------------------------------------------------------------
     * 15. Immutability – returned lists cannot be mutated externally
     * --------------------------------------------------------------------- */

    @Test void listsAreImmutableCopies() {
        var layout = ActionLayout.restore(DEF_ATK, DEF_ABL);
        assertThrows(UnsupportedOperationException.class,
                () -> layout.attacks().add("mutated"));
        assertThrows(UnsupportedOperationException.class,
                () -> layout.abilities().remove(0));
    }

    @Test void mutatingSourceListDoesNotAffectRecord() {
        var source = new ArrayList<>(DEF_ATK);
        var layout = new ActionLayout(source, DEF_ABL, "ora", "time_stop");
        source.add("mutant");
        assertEquals(3, layout.attacks().size());
    }
}
