---
phase: 04-acquisition-and-progression
plan: 02
subsystem: progression
requirements-completed: []
tags: [fabric, star-platinum, resolve, training, progression, draft-6]
---

# Draft 6: Stand Resolve, unlocks and training

Implemented the next human Star Platinum progression slice. Survival combat earns Resolve with the original thresholds, attack/health boosts, record multipliers, decay, level-up effect and damage resistance. Levels unlock Uppercut/Inhale/Star Finger/Time Stop at 1/2/3/4 and develop stats by one quarter each. Time Stop starts at five ticks, trains from actual stopped ticks and reaches 100 ticks at 95 points. Creative access remains immediate and does not fabricate earned levels. Hamon remains separate and unported.

## Implementation

- `fabric/core/src/main/java/com/github/standobyte/jojo/core/ResolveProgress.java` contains the immutable, validated progression rules and ten-record history. `fabric/src/main/java/com/github/standobyte/jojo/fabric/ProgressionService.java` owns server mutation, accepted-hit eligibility, post-absorption resistance, level development, effect lifecycle and death/respawn behavior.
- `StandAttachments.RESOLVE` persists and synchronizes owner state independently of the existing Stand and arrow schemas. Old Fabric development initializes the nearest original level when the attachment is absent; ownership is retained. Existing Survival draft players now follow the original unlock gates. Death keeps levels, training and the current peak while clearing transient combat state. Clear/regrant retains learned progression; explicit operator reset clears it.
- Direct entity, service, command and packet entry points enforce gates. A rejected move cannot consume stamina or start its cooldown. Automatic finisher Uppercut also requires level 1. Time Stop uses captured trained duration, scaled per-tick cost, actual elapsed training and original cooldown bonuses. Creative active stops have zero cooldown; cancelling charge retains the existing 20-tick safety cooldown.
- K opens a scrollable, narrated progression screen with remappable key labels and mouse/keyboard navigation. The HUD adds a Resolve meter; failed locked inputs explain the requirement. Operator-only progression commands set levels/training or reset while preserving ownership.

## Delegation and review

Used GSD's existing-project inline adaptation and Antigravity CLI with Claude Sonnet 5.5 Medium. The read-only original-source audit ran in `../ROTP_port-worktrees/progression-reference`; result `/tmp/rotp-progression-audit-result.json`. A separate worker wrote only `ProgressionScreen.java` in `../ROTP_port-worktrees/progression-ui`; result `/tmp/rotp-progression-ui-result.json`. Parent independently inspected both scopes and original references, corrected the reported screen-opening API, added keyboard scrolling/precise duration/missing translation, integrated, compiled and tested. No worker self-report was treated as verification. Main branch has no new commits.

Primary original references: `ResolveCounter.java`, `StandPower.java`, `StandAction.java`, `TimeStop.java`, `TimeStopInstance.java`, `ResolveEffect.java`, `GameplayEventHandler.java` and the Star Platinum section of `ModStandsInit.java` under the original source tree.

## Verification

Release command from `fabric/`: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./gradlew build runClientGameTest -PacceptMinecraftEula=true`.

Final evidence: `/tmp/rotp-draft6-verification5.log`; BUILD SUCCESSFUL in 1m 46s. Passed 24 core unit tests, 67 server GameTests (66 mod plus one framework), 24 model checks, and all three real-client suites. Existing combat and acquisition suites pass with explicit unlocked fixtures where those tests isolate combat mechanics. Old cooldown expectations were updated to the restored original Creative and short-duration behavior.

New tests cover raw accepted hostile damage, rejection/passive exclusion, half-rate user attacks, overkill, natural combat level-up, stat development, denial without resource costs, post-absorption resistance/void exclusion, actual death/respawn, Creative bypass, milk, codec persistence, fresh five-tick Time Stop training/scaled stamina, and permission-checked command execution/reset. Actual client keys exercise denied abilities, K/End/Esc navigation, combat level-up and J training. World reload and dedicated reconnect preserve fractional learning and gates. Screenshots 0031, 0033 and 0034 were inspected; all seven progression screenshots accompany the draft.

One generated server run passed tests but stalled on NVMe region-file writes during shutdown. A thread dump confirmed chunk-save I/O, not gameplay deadlock. Only that disposable test JVM was stopped; final server/client verification ran in temporary RAM storage and completed normally. Real save/reload and reconnect still execute file persistence. Project and user worlds were not moved.

## Artifact and remaining scope

Version: `0.2.2-fabric.6-dev+upstream.0.2.2.2`.
JAR SHA-256: `9853fbf68a7100c79a6e7ede74c7b620738ea6b2a66c3afd0d69539c96afb5e5`.
Bundle: `drafts/latest/`, including mod, Fabric API, checksums, verification log, model report, screenshots and playtest guide. Draft 5 remains available and both bundles' hashes were verified. Manual Draft 6 playtest is pending.

Full PARITY-02 remains open: the original customizable action interface, souls, full Resolve presentation/configuration, other-power interactions and acquisition extensions are not complete. Vanilla combat-stat accounting still uses pre-Resolve damage, though health reduction is correct. The original unsummoned countdown edge case now records a peak when the two-tick decrement lands on zero. Creative progression bypass is temporary rather than permanently awarding levels. Other Star Platinum combat/time-stop parity remains tracked separately in Phase 3.

## Self-check

Passed: `fabric/core/src/main/java/com/github/standobyte/jojo/core/ResolveProgress.java`, `fabric/src/main/java/com/github/standobyte/jojo/fabric/ProgressionService.java`, `fabric/src/client/java/com/github/standobyte/jojo/fabric/client/ProgressionScreen.java`, `fabric/src/gametest/java/com/github/standobyte/jojo/fabric/ProgressionGameTests.java` and `drafts/latest/manifest.json` exist. Main work is staged only; no main-branch commit was requested.
