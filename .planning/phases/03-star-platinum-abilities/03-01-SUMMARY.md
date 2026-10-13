---
phase: 03-star-platinum-abilities
plan: 01
subsystem: gameplay
requires: [02-combat-draft]
provides: [uppercut, star-finger, inhale, zoom, manual-control, regional-time-stop, draft-3]
affects: [04-acquisition-and-progression, 05-remaining-content-and-compatibility]
requirements-completed: [BUILD-01, TEST-01]
key-files:
  created:
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/TimeStopService.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/StandControlPayload.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/StarFingerLayer.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/StandView.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/AdvancedAbilityGameTests.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/TimeStopGameTests.java
  modified:
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/StarPlatinumEntity.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/RipplesClient.java
    - fabric/tools/package_draft.py
---

# Draft 3: advanced Star Platinum abilities

The user accepted Draft 2. This plan delivers six additional playable abilities: uppercut, extending Star Finger, inhale, zoom, bounded manual control and regional time stop. Original costs, human time-stop duration, model geometry, animation clips, sounds and textures remain the reference. The new draft exposes abilities directly while progression/unlocks remain deferred.

Manual movement is directional intent with server-owned speed/range and a twelve-tick lease. The owner body remains anchored, attacks originate at the manifestation, and release/menu/lost input restores control. Uppercut launches and disables shields; finger range grows over time and respects walls; inhale/zoom reuse held-input cancellation.

Time stop charges for forty ticks and freezes loaded entities, players, random ticks, block entities and queued block/fluid ticks in the original twelve-chunk square radius. The caster and owned Stand remain active. Resume preserves pending simulation and momentum; costs/cooldowns and death/dismiss/disconnect/dimension invalidation are server-owned. Client state supports multiple independent regions. J is the default key because T conflicts with Minecraft chat.

## Delegation and review

Antigravity CLI worked in isolated `advanced-poses` and `time-stop` worktrees. Claude quota failures produced no accepted output. Gemini Pro completed poses. Time-stop work began with Gemini Flash, then resumed under Gemini Pro after excessive API discovery. Main integration independently corrected packet thread access, per-caster client state, dimension synchronization, cooldown persistence across reconnect, damage-cooldown fields, activation timing and momentum handling. A separate finger layer completes the worker's pose-only finger output. Worker compilation was not treated as runtime verification.

## Validation

Twelve core tests, twenty-nine server GameTests (twenty-eight mod tests plus the framework test), twenty-four model checks, and the real-client suite cover the resulting draft. The time-stop test uses a separate environment batch and advances actual server ticks, including button/fluid queues and furnace state. Client tests cover all new keys, camera/zoom restoration, singleplayer and dedicated-server time stop, frozen local-player input, independent regional payloads and disconnect during an active stop. Original geometry and deterministic generation remain checked.

The manual-range test originally depended on randomized test-chunk readiness. It now explicitly keeps its test chunks loaded and measures entity ticks; gameplay still refuses to load chunks for manual control.

## Scope and remaining parity

The planned six-ability draft is complete. PARITY-01 deliberately remains open: automatic finishers, parry/clashes, afterimages, held movement penalties, time-stop blink/invasion/training/shaders/delayed damage, and other original details remain. `fabric/migration/STATUS.md` records exact coverage and limits. This summary does not claim full Star Platinum or full mod parity. Draft 3 manual playtest remains pending.

Main changes are staged, not committed or published. Draft 2 remains available in its versioned folder; `drafts/latest` selects the verified Draft 3 bundle after packaging.

Follow-up (2026-10-13): the user accepted Draft 3 with a stamina display concern. Draft 4 investigates that report and completes automatic finishers and movement restrictions; see `03-02-SUMMARY.md`.
