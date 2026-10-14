package com.github.standobyte.jojo.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Behavioral regression coverage for the original two-button HUD editor and input contract. */
class OriginalActionLayoutTest {
    private static final List<String> ATTACKS = List.of("light", "barrage", "finger");
    private static final List<String> ABILITIES = List.of("guard", "zoom", "inhale", "time_stop");
    private static ActionLayout defaults() { return ActionLayout.restore(ATTACKS, ABILITIES); }
    private static ActionLayout reload(ActionLayout layout) {
        return ActionLayout.restoreFlexible(layout.attacks(), layout.abilities(), layout.selectedAttack(),
                layout.selectedAbility(), layout.disabled(), ATTACKS, ABILITIES);
    }
    @Test void wheelVisitsEmptySlotBetweenEnds() {
        var first = defaults();
        var empty = first.cycleOriginal(true, -1, id -> true);
        assertEquals("", empty.selectedAttack());
        assertEquals("finger", empty.cycleOriginal(true, -1, id -> true).selectedAttack());
        assertEquals("light", empty.cycleOriginal(true, 1, id -> true).selectedAttack());
        assertEquals("", first.select(true, "finger").cycleOriginal(true, 1, id -> true).selectedAttack());
    }
    @Test void wheelSkipsLockedActionsButStillReachesVanillaSlot() {
        var layout = defaults();
        assertEquals("finger", layout.cycleOriginal(true, 1, id -> !id.equals("barrage")).selectedAttack());
        assertEquals("", layout.cycleOriginal(true, 1, id -> false).selectedAttack());
    }
    @Test void numberBeyondRowAndLockedNumberDeselect() {
        assertEquals("", defaults().selectSlot(true, 8, id -> true).selectedAttack());
        assertEquals("", defaults().selectSlot(true, 1, id -> false).selectedAttack());
    }
    @Test void disablingSelectedActionRestoresVanillaAndReenablingSelectsIt() {
        var hidden = defaults().toggleEnabled("light");
        assertEquals("", hidden.selectedAttack());
        assertEquals(List.of("barrage", "finger"), hidden.enabledActions(true));
        assertEquals("barrage", hidden.selectSlot(true, 0, id -> true).selectedAttack());
        var enabled = hidden.toggleEnabled("light");
        assertEquals("light", enabled.selectedAttack());
        assertEquals(ATTACKS, enabled.enabledActions(true));
    }
    @Test void crossRowTransferSurvivesRestoreWithoutDuplication() {
        var moved = reload(defaults().transfer(true, "light", false, 1));
        assertEquals(List.of("barrage", "finger"), moved.attacks());
        assertEquals(List.of("guard", "light", "zoom", "inhale", "time_stop"), moved.abilities());
        assertEquals("", moved.selectedAttack());
        assertEquals("light", moved.selectedAbility());
    }
    @Test void disabledStateAndEmptySelectionSurviveRestore() {
        var hidden = reload(defaults().toggleEnabled("light").transfer(true, "light", false, 0));
        assertEquals(List.of("light"), hidden.disabled());
        assertFalse(hidden.enabledActions(false).contains("light"));
        assertEquals("", hidden.selectedAttack());
    }
    @Test void flexibleRestoreRejectsUnknownIdsAndCrossRowDuplicates() {
        var layout = ActionLayout.restoreFlexible(List.of("guard", "light", "unknown", "light"),
                List.of("guard", "finger", "barrage"), "unknown", "finger", List.of("unknown", "guard", "guard"), ATTACKS, ABILITIES);
        assertEquals(List.of("guard", "light"), layout.attacks());
        assertEquals(List.of("finger", "barrage", "zoom", "inhale", "time_stop"), layout.abilities());
        assertEquals(List.of("guard"), layout.disabled());
        assertEquals("light", layout.selectedAttack());
    }
    @Test void newlyAddedActionsJoinCanonicalRowWithoutUndoingCustomization() {
        var layout = ActionLayout.restoreFlexible(List.of("guard", "barrage"), List.of("light", "finger"),
                "guard", "light", List.of(), ATTACKS, ABILITIES);
        assertEquals(List.of("guard", "barrage"), layout.attacks());
        assertEquals(List.of("light", "finger", "zoom", "inhale", "time_stop"), layout.abilities());
    }
    @Test void originalCyclingHandlesAllDisabledAndEmptyBars() {
        var layout = defaults().toggleEnabled("light").toggleEnabled("barrage").toggleEnabled("finger");
        assertEquals("", layout.cycleOriginal(true, 1, id -> true).selectedAttack());
        assertEquals(List.of(), layout.enabledActions(true));
        assertEquals("", ActionLayout.restore(List.of(), List.of()).cycleOriginal(true, -1, id -> true).selectedAttack());
    }
    @Test void editsPreserveDisabledState() {
        var layout = defaults().toggleEnabled("barrage").move(true, "finger", -2).select(true, "light");
        assertEquals(List.of("barrage"), layout.disabled());
        assertEquals(List.of("finger", "light"), layout.enabledActions(true));
        assertThrows(UnsupportedOperationException.class, () -> layout.disabled().add("guard"));
    }
}
