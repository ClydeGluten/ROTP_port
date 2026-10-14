package com.github.standobyte.jojo.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable snapshot of an action-bar layout for one Stand.
 * Each row (attacks / abilities) holds an ordered list of action IDs and
 * remembers which ID is currently selected.
 *
 * <p>All mutation methods return a fresh {@code ActionLayout}; the receiver is
 * never modified.
 */
public record ActionLayout(
        List<String> attacks,
        List<String> abilities,
        String selectedAttack,
        String selectedAbility,
        List<String> disabled) {

    /** Backward-compatible construction for layouts without disabled actions. */
    public ActionLayout(List<String> attacks, List<String> abilities, String selectedAttack, String selectedAbility) {
        this(attacks, abilities, selectedAttack, selectedAbility, List.of());
    }

    /* -----------------------------------------------------------------------
     * Canonical constructor – defensive copies & non-null validation
     * --------------------------------------------------------------------- */

    public ActionLayout {
        Objects.requireNonNull(attacks,        "attacks must not be null");
        Objects.requireNonNull(abilities,      "abilities must not be null");
        Objects.requireNonNull(selectedAttack, "selectedAttack must not be null");
        Objects.requireNonNull(selectedAbility,"selectedAbility must not be null");
        attacks   = List.copyOf(attacks);
        abilities = List.copyOf(abilities);
        disabled = List.copyOf(disabled);
    }

    /* -----------------------------------------------------------------------
     * Factory – tolerant restoration from potentially corrupted saved data
     * --------------------------------------------------------------------- */

    /**
     * Restores (or initialises) a layout from saved data.
     *
     * <p>Rules applied in order:
     * <ol>
     *   <li>A {@code null} saved list is treated as empty.</li>
     *   <li>Each element of the saved list is checked: {@code null} elements,
     *       non-strings (already typed here, so just null), blank strings, or
     *       IDs that belong to the <em>other</em> row's defaults are silently
     *       dropped ("wrong-row" rejection).</li>
     *   <li>IDs not present in either default list are unknown and dropped.</li>
     *   <li>Duplicate IDs are removed, preserving first occurrence.</li>
     *   <li>Any default IDs that are missing from the resulting list are
     *       appended at the end in their original default order.</li>
     *   <li>If the saved selection is blank/null or no longer present in the
     *       restored list, the selection is fixed to the first ID in the list;
     *       if the list is empty the selection becomes {@code ""}.</li>
     * </ol>
     *
     * @param attacks         saved attack row (may be {@code null}/malformed)
     * @param abilities       saved ability row (may be {@code null}/malformed)
     * @param selectedAttack  saved attack selection (may be {@code null})
     * @param selectedAbility saved ability selection (may be {@code null})
     * @param defaultAttacks  canonical attack IDs for this Stand (non-null)
     * @param defaultAbilities canonical ability IDs for this Stand (non-null)
     */
    public static ActionLayout restore(
            List<String> attacks,
            List<String> abilities,
            String selectedAttack,
            String selectedAbility,
            List<String> defaultAttacks,
            List<String> defaultAbilities) {

        Objects.requireNonNull(defaultAttacks,   "defaultAttacks must not be null");
        Objects.requireNonNull(defaultAbilities, "defaultAbilities must not be null");

        List<String> restoredAttacks   = restoreRow(attacks,   defaultAttacks, defaultAbilities);
        List<String> restoredAbilities = restoreRow(abilities, defaultAbilities, defaultAttacks);

        String selAtk = fixSelection(selectedAttack,  restoredAttacks);
        String selAbl = fixSelection(selectedAbility, restoredAbilities);

        return new ActionLayout(restoredAttacks, restoredAbilities, selAtk, selAbl);
    }

    /** Convenience overload: default layout (null saved data). */
    public static ActionLayout restore(
            List<String> defaultAttacks,
            List<String> defaultAbilities) {
        return restore(null, null, null, null, defaultAttacks, defaultAbilities);
    }

    /* -----------------------------------------------------------------------
     * Mutation API – each returns a new ActionLayout
     * --------------------------------------------------------------------- */

    /**
     * Changes the selection in the given row.
     * No-op if {@code id} is not present in that row.
     */
    public ActionLayout select(boolean attackRow, String id) {
        if (id == null) return this;
        List<String> row = row(attackRow);
        if (!row.contains(id)) return this;
        return withSelection(attackRow, id);
    }

    /**
     * Moves {@code id} within the given row by {@code delta} positions
     * (positive = toward end, negative = toward start), clamped to the list
     * bounds. The selection follows the ID after the move.
     * No-op if {@code id} is not present in that row or {@code delta == 0}.
     */
    public ActionLayout move(boolean attackRow, String id, int delta) {
        if (id == null || delta == 0) return this;
        List<String> row = row(attackRow);
        int from = row.indexOf(id);
        if (from < 0) return this;

        int to = (int) Math.clamp((long) from + delta, 0, row.size() - 1);
        if (to == from) return this;

        List<String> newRow = new ArrayList<>(row);
        newRow.remove(from);
        newRow.add(to, id);

        // Selection sticks to the moved ID in the affected row.
        String newSel = id;
        return attackRow
                ? new ActionLayout(newRow, abilities, newSel, selectedAbility, disabled)
                : new ActionLayout(attacks, newRow, selectedAttack, newSel, disabled);
    }

    /**
     * Cycles the selection in the given row by {@code direction} steps
     * ({@code +1} = forward, {@code -1} = backward), skipping IDs for which
     * {@code unlocked} returns {@code false}. Wraps around.
     *
     * <p>If the row is empty or all IDs are locked, returns {@code this}
     * unchanged.
     *
     * @param direction  typically {@code +1} or {@code -1}; sign determines
     *                   direction, magnitude is normalised to 1 step
     * @param unlocked   predicate that returns {@code true} for usable IDs
     */
    public ActionLayout cycle(boolean attackRow, int direction, Predicate<String> unlocked) {
        if (direction == 0) return this;
        List<String> row = row(attackRow);
        if (row.isEmpty()) return this;

        int step     = direction > 0 ? 1 : -1;
        int size     = row.size();
        String sel   = currentSelection(attackRow);
        int startIdx = row.indexOf(sel);
        if (startIdx < 0) startIdx = 0;

        int idx = startIdx;
        for (int i = 0; i < size; i++) {
            idx = Math.floorMod(idx + step, size);
            if (unlocked.test(row.get(idx))) {
                return withSelection(attackRow, row.get(idx));
            }
        }
        // All locked – no-op.
        return this;
    }

    /**
     * Returns the selected ID in the given row if it is unlocked, otherwise
     * returns the first unlocked ID, or {@code ""} if none are unlocked.
     */
    public String selected(boolean attackRow, Predicate<String> unlocked) {
        List<String> row = row(attackRow);
        String sel = currentSelection(attackRow);
        if (row.contains(sel) && unlocked.test(sel)) return sel;
        return row.stream().filter(unlocked).findFirst().orElse("");
    }

    /** Original HUD layouts allow any supported action in either row, once only. */
    public static ActionLayout restoreFlexible(List<String> attacks, List<String> abilities,
            String selectedAttack, String selectedAbility, List<String> disabled,
            List<String> defaultAttacks, List<String> defaultAbilities) {
        var legal = new LinkedHashSet<>(defaultAttacks);
        legal.addAll(defaultAbilities);
        var seen = new LinkedHashSet<String>();
        List<String> left = flexibleRow(attacks, legal, seen);
        List<String> right = flexibleRow(abilities, legal, seen);
        for (String id : defaultAttacks) if (seen.add(id)) left.add(id);
        for (String id : defaultAbilities) if (seen.add(id)) right.add(id);
        var hidden = new LinkedHashSet<String>();
        if (disabled != null) for (Object id : disabled) {
            if (id instanceof String value && legal.contains(value)) hidden.add(value);
        }
        return new ActionLayout(left, right, flexibleSelection(selectedAttack, left, hidden),
                flexibleSelection(selectedAbility, right, hidden), List.copyOf(hidden));
    }

    private static List<String> flexibleRow(List<String> saved, java.util.Set<String> legal,
            java.util.Set<String> seen) {
        List<String> result = new ArrayList<>();
        if (saved != null) for (Object raw : saved) {
            if (raw instanceof String id && legal.contains(id) && seen.add(id)) result.add(id);
        }
        return result;
    }

    private static String flexibleSelection(String selected, List<String> row, java.util.Set<String> hidden) {
        if ("".equals(selected)) return "";
        if (selected != null && row.contains(selected) && !hidden.contains(selected)) return selected;
        return row.stream().filter(id -> !hidden.contains(id)).findFirst().orElse("");
    }

    public List<String> enabledActions(boolean attackRow) {
        return row(attackRow).stream().filter(id -> !disabled.contains(id)).toList();
    }

    /** Selecting beyond the visible row deselects it, restoring vanilla mouse input. */
    public ActionLayout selectSlot(boolean attackRow, int slot, Predicate<String> visible) {
        var enabled = enabledActions(attackRow);
        String id = slot >= 0 && slot < enabled.size() && visible.test(enabled.get(slot)) ? enabled.get(slot) : "";
        return withSelection(attackRow, id);
    }

    /** Original wheel cycling visits the empty slot between the last and first action. */
    public ActionLayout cycleOriginal(boolean attackRow, int direction, Predicate<String> visible) {
        if (direction == 0) return this;
        var enabled = enabledActions(attackRow);
        if (enabled.isEmpty()) return withSelection(attackRow, "");
        int index = enabled.indexOf(currentSelection(attackRow));
        for (int i = 0; i <= enabled.size(); i++) {
            index = Math.floorMod(index + (direction > 0 ? 1 : -1) + 1, enabled.size() + 1) - 1;
            if (index == -1 || visible.test(enabled.get(index))) return selectSlot(attackRow, index, visible);
        }
        return this;
    }

    public ActionLayout toggleEnabled(String id) {
        if (!attacks.contains(id) && !abilities.contains(id)) return this;
        List<String> hidden = new ArrayList<>(disabled);
        boolean enable = hidden.remove(id);
        if (!enable) hidden.add(id);
        return new ActionLayout(attacks, abilities,
                attacks.contains(id) ? enable ? id : selectedAttack.equals(id) ? "" : selectedAttack : selectedAttack,
                abilities.contains(id) ? enable ? id : selectedAbility.equals(id) ? "" : selectedAbility : selectedAbility,
                hidden);
    }

    /** Moves an action between the original left/right click bars. */
    public ActionLayout transfer(boolean fromAttack, String id, boolean toAttack, int index) {
        if (!row(fromAttack).contains(id)) return this;
        if (fromAttack == toAttack) return move(fromAttack, id, index - row(fromAttack).indexOf(id));
        List<String> left = new ArrayList<>(attacks), right = new ArrayList<>(abilities);
        List<String> from = fromAttack ? left : right, to = toAttack ? left : right;
        from.remove(id);
        to.add(Math.clamp(index, 0, to.size()), id);
        String leftSelected = selectedAttack.equals(id) ? "" : selectedAttack;
        String rightSelected = selectedAbility.equals(id) ? "" : selectedAbility;
        if (!disabled.contains(id)) {
            if (toAttack) leftSelected = id; else rightSelected = id;
        }
        return new ActionLayout(left, right, leftSelected, rightSelected, disabled);
    }

    /* -----------------------------------------------------------------------
     * Private helpers
     * --------------------------------------------------------------------- */

    private List<String> row(boolean attackRow) {
        return attackRow ? attacks : abilities;
    }

    private String currentSelection(boolean attackRow) {
        return attackRow ? selectedAttack : selectedAbility;
    }

    private ActionLayout withSelection(boolean attackRow, String id) {
        return attackRow
                ? new ActionLayout(attacks,  abilities, id,               selectedAbility, disabled)
                : new ActionLayout(attacks,  abilities, selectedAttack,   id, disabled);
    }

    /**
     * Rebuilds one row from saved data, applying all validation rules.
     *
     * @param saved        raw saved list (may be null)
     * @param ownDefaults  canonical IDs for this row
     * @param otherDefaults canonical IDs for the other row (wrong-row check)
     */
    private static List<String> restoreRow(
            List<String> saved,
            List<String> ownDefaults,
            List<String> otherDefaults) {

        // Use a set for fast membership tests.
        var ownSet   = new LinkedHashSet<>(ownDefaults);
        var otherSet = new LinkedHashSet<>(otherDefaults);

        // Seen-set to deduplicate while preserving order.
        var seen   = new LinkedHashSet<String>();

        if (saved != null) {
            for (Object raw : saved) {
                // Accept only non-null, non-blank strings that belong to this
                // row's defaults and are not from the other row.
                if (!(raw instanceof String id)) continue;
                if (id.isBlank())               continue;
                if (otherSet.contains(id))      continue; // wrong-row
                if (!ownSet.contains(id))       continue; // unknown
                seen.add(id);                             // deduplicates
            }
        }

        // Append any defaults that are still missing.
        seen.addAll(ownDefaults);

        return new ArrayList<>(seen);
    }

    private static String fixSelection(String saved, List<String> row) {
        if (row.isEmpty()) return "";
        if (saved != null && !saved.isBlank() && row.contains(saved)) return saved;
        return row.get(0);
    }
}
