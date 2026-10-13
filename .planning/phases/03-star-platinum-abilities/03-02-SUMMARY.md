---
phase: 03-star-platinum-abilities
plan: 02
subsystem: gameplay
requires: [03-01]
provides: [stamina-mode-feedback, automatic-finishers, combat-movement, draft-4]
affects: [04-acquisition-and-progression, 05-remaining-content-and-compatibility]
requirements-completed: [BUILD-01, TEST-01]
key-files:
  created:
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/StandMovement.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/mixin/StandSprintMixin.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/MovementGameTests.java
  modified:
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/StarPlatinumEntity.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/RipplesClient.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/StandClientGameTest.java
    - fabric/tools/package_draft.py
---

# Draft 4: stamina feedback and combat behavior

The user accepted Draft 3 and reported that the stamina bar did not visually change. New real-client regressions passed against the previous HUD: full, half and empty Survival values draw correctly, attacks visibly drain the bar, and resting refills it. Creative intentionally bypasses costs. It now displays “Stamina: Unlimited (Creative)” and omits the misleading finite bar. Mode switches and dedicated-server half/quarter values are covered. The user's test mode was not confirmed; no Survival drawing or synchronization defect was reproduced.

The next combat slice adds a synchronized finisher meter and the original automatic uppercut selection at 0.5 charge. Heavy attacks consume 0.51 and preserve the remainder; launch uses charge captured before consumption. Light attacks and landed barrage hits retain their existing charge gains. Resolve/unlocks remain deferred, so finishers are available directly in this draft.

Original action movement factors now apply through a transient, vanilla-synchronized movement-speed modifier: barrage 0.15, guard 0.3, heavy/special 0.5, light/recovery/idle 1. Sprinting is disabled while slowed. Only the mod's modifier is changed; other speed modifiers survive. Release, expiry, dismissal, death, disconnect and dimension changes restore movement. Existing manual/time-stop body locks remain active.

## Delegation and review

Antigravity CLI, using `claude-sonnet-5-5-high`, completed the isolated `../ROTP_port-worktrees/combat-movement` task. The conversation and worker result are recorded in `/tmp/rotp-movement-result.json`. Its five scoped files were inspected and integrated without overwriting parent finisher work. Parent integration added original sprint restrictions and real-client synchronization/input checks. The worker's 42-test report was independently superseded by the integrated 45-test run. Main branch remains staged and uncommitted; the isolated snapshot did not change main HEAD.

## Validation

Passed 12 core tests, 45 server GameTests (44 mod tests plus framework), 24 model checks and real-client singleplayer/dedicated-server tests. Final release evidence: `/tmp/rotp-draft4-release-verification.log`, copied into the versioned bundle. Inspected Creative unlimited and finisher-ready screenshots and the Survival half-bar baseline.

The first new client finisher check observed the preceding light-attack recovery before the heavy packet arrived. It now waits for the heavy action before asserting uppercut selection. Runtime finisher boundary tests passed independently. No failed run is used as release evidence.

## Remaining scope

PARITY-01 remains open for parry/clashes, afterimages, automatic guarding and remaining time-stop variants/presentation. Phase 4 acquisition/progression also remains open. This draft does not claim complete Star Platinum, original mod, migration or third-party compatibility. Draft 4 manual playtest is pending; Draft 3 remains available in its versioned folder.

## Self-check

Passed: `fabric/src/main/java/com/github/standobyte/jojo/fabric/StandMovement.java`, `fabric/src/client/java/com/github/standobyte/jojo/fabric/mixin/StandSprintMixin.java` and `fabric/src/gametest/java/com/github/standobyte/jojo/fabric/MovementGameTests.java` exist. Final integrated build and runtime checks passed. The verified Draft 4 bundle is packaged; Draft 3 remains available. Git changes are staged under the user's stage-only workflow.
