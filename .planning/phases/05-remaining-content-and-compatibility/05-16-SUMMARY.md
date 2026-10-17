---
phase: 05-remaining-content-and-compatibility
plan: 16
subsystem: hamon-progression
requires: [05-14]
provides: [source-ordered-training-rollover, cached-exercise-bonuses]
requirements: [PARITY-03, PARITY-04, HAMON-02]
---

# Plan 05-16 implementation

Implemented the two source-confirmed progression corrections in the assigned seven files. Exercise processing now precedes the day transition. `HamonProgress` separates raw counters from an effective completion mask and preserves the mask across immutable copies. Exercise completion refreshes only below breathing 100; explicit assignment, persistent decoding and lifecycle sync refresh at the corresponding source sites, while daily reset clears bonuses.

The existing 16-field constructor and persistent schema remain compatible. Stream synchronization adds the authoritative effective mask so synchronization cannot masquerade as a save reload. Swimming capacity, meditation charge time, running/mining attributes, all-four efficiency and daily progression use the source cache where applicable.

## Evidence-driven correction to the plan

All `HamonData.updateExerciseAttributes` call sites were inspected: `tickExercises`, `setExerciseTicks`, `breathingTrainingDay`, and `syncWithUserOnly`; `readNBT` reaches the refresh through `setExerciseTicks`. The original `HamonExercisesPacket` handler also reaches explicit assignment. `getBreathingIncrease` calls cached `getCompleteExercisesCount`, so the original plan's raw-counter daily-forecast assumption was corrected with root authorization. The daily best-incomplete fraction remains raw, as in source.

## Validation handoff

Added three focused core regressions and three server GameTests. Register `com.github.standobyte.jojo.fabric.HamonProgressionParityGameTests` in the shared GameTest entrypoints. Coverage includes the final eligible meditation tick on rollover, login day initialization, a multi-day jump applying one rollover, capped uninterrupted play versus save reload, network state preservation, running modifier restoration/removal and reset cache clearing.

Static `git diff --check` passed. No tests, Gradle tasks, git mutations, dependency changes or original-source edits were performed. Compilation and runtime validation remain pending in the root integration lane. The Fabric client receives the authoritative effective mask; this deliberately replaces the original packet-local cache refresh with unified server-owned attachment state and is documented in the domain ledger.

## Root integration result

`/tmp/rotp-draft16-verification3.log`, **BUILD SUCCESSFUL in 1m 1s**. All **205 required server GameTests** and **94 core tests** passed. The focused `HamonClientGameTest` passed in fresh integrated and dedicated worlds; packaging passed **29 model checks**. No unrelated client matrix was repeated.

Root completed registration and the shared validation after the worker handoff. No separate worker run is claimed. Draft 16 is packaged; manual presentation acceptance remains pending.
