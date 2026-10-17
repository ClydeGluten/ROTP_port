---
phase: 05-remaining-content-and-compatibility
plan: 33
subsystem: pillarman-light
status: awaiting_integration
tags: [fabric, pillarman, light, combat, particles]
requires: [05-23]
provides: [authoritative LIGHT actions, source flash and blade combat, LIGHT particles, grouped gameplay checks]
affects: [05-34, 05-36]
tech-stack:
  added: []
  patterns: [tracking attachments, server intent validation, shared damage-immunity helper, source tracking emitters]
key-files:
  created:
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/pillarman/PillarLightActions.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/pillarman/PillarLightCombat.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/pillarman/PillarLightClient.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/PillarLightGameTests.java
    - fabric/migration/PILLAR_LIGHT_PARITY.md
  modified: []
key-decisions:
  - Preserve actual source15F piercing clamp to1 and invert current armor curve.
  - Preserve unlimited grounded dash hold, full continuous dash immunity, and source projectile defense differences.
  - Delegate player clips/blades/first-person aura to concurrent05-34 owner; root owns shared integration.
metrics:
  completed: 2026-10-18
actuals:
  tokens: 13847
  token_measure: staged diff chars divided by4 before this metric line
  tasks: 2
  commits: 0
  commit_policy: user-authorized stage-only shared worktree
---

# Phase05 Plan33: Pillar LIGHT actions

Server-owned Flash/Decoy, timed blade slash, unlimited-charge dash and held barrage preserve original costs, hitboxes, armor compensation, damage defenses and particles.

## Delivered work

Both implementation tasks completed for root integration. Four new Java files provide the authoritative action state and combat, original particle effects, and three grouped GameTests. Original full-invisibility rendering suppression and milk immunity have explicit root hooks. Shared bootstrap/input/species/damage/resource-copy hooks stay root-owned. Player animations/blade layer/first-person rainbow aura stay with05-34 owner. All handoffs were communicated before the combined lane.

Exact original-to-Fabric method pairs, inherited active callers, asset paths and source quirks are recorded in `fabric/migration/PILLAR_LIGHT_PARITY.md`. Read five original action classes completely, `PillarmanAction`, action registration/hold handling, original armor/knockback/immunity utilities, original flash/glint/aura presentation and complete living-render suppression. Original source remains read-only.

## Verification and limits

- Scoped `git diff --check` passes. New files checked for placeholder/TODO markers; none introduced.
- Root reported combined main Java compile passed. Two reported LIGHT client compilation errors were repaired against cached26.3 signatures: Flash now uses ColorParticleOption; force-swing uses a tightly scoped flag for root's SwingState mixin.
- Root subsequently reported main/client compile passed in compile4 after both LIGHT repairs. Test compile and the three real-world GameTests have not yet passed in this handoff. No worker Gradle ran. No TDD red/green pass is claimed; user explicitly requested lean tests and root's single consolidated lane.
- Full rendered invisibility (including held/armor layers), first-person particles, multiplayer/dimension/rejoin lifecycle and Stand-projectile rejection remain integration verification, not completed proof. Root owns the cross-phase unrun-verification register and05-36 delivery.

## Deviations and platform adaptations

Source `addArmorPiercing(15F)` actually clamps to1; preserve this behavior rather than reinterpret the plan shorthand as15 armor points. Source radius16 helper is a centered cube and near-distance threshold is strictly<5. Flash/Decoy auto-fire at maximum hold; Dash overrides maximum toInteger.MAX_VALUE. All recorded in the parity ledger.

26.3 stores swing state privately and Flash particles carry a color option. Root's client SwingState hook replaces only the source barrage forced-swing bypass. Server movement penalties use existing StandMovement; restore its held modifier before computing original dash launch speed. Original action state stays transient and root clears on lifecycle transitions.

Shared-file ownership, no commit/push and root-only build override generic executor defaults. No STATE/ROADMAP updates or worker commits made. No new dependencies or invented LIGHT acquisitions/entities/resources.

## Known stubs

None. Root/05-34 integration and verification remain explicitly pending; registration alone does not establish playable parity.

## Self-Check: PASSED

All four assigned Java files, parity ledger and this summary exist. Assigned source hygiene check passes. No commits expected under stage-only authorization. Runtime status deliberately remains `awaiting_integration`.
