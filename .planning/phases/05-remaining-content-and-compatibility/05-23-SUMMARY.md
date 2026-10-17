---
phase: 05-remaining-content-and-compatibility
plan: 23
subsystem: pillarman-base
tags: [fabric, nonstand, pillarman, source-parity]
status: awaiting-integration-verification
requires: [existing-vampire-config-and-effects, shared-nonstand-integration]
provides: [persistent-pillar-stages-and-modes, base-action-dispatch, pillar-world-scenarios]
affects: [05-26, 05-27, 05-29, 05-30, 05-31, 05-32, 05-33, 05-34, 05-35]
tech-stack:
  added: []
  patterns: [immutable-persistent-attachment, server-owned-action-clock, bounded-intent-payload]
key-files:
  created:
    - fabric/core/src/main/java/com/github/standobyte/jojo/core/PillarmanProgress.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/PillarmanAttachments.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/PillarmanService.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/PillarmanActionPayload.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/PillarmanBaseActions.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/PillarmanStateGameTests.java
    - fabric/migration/PILLARMAN_PARITY.md
  modified: []
key-decisions:
  - Preserve source stage3 advancement quirk and stage-independent command modes.
  - Root alone registers shared lifecycle callbacks and runs Gradle/world verification.
  - No survival baseline acquisition, future modes, or source-inactive features invented.
actuals:
  tokens: 16128
  tasks: 2
  commits: 0
plan_head_before: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
plan_head_after: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
completed: 2026-10-18
---

# Phase 05 Plan 23: Pillar Man base source implementation

Persistent Pillar stages/modes/stone with original attributes and energy rules, plus seven base actions with server-owned targeting and clocks.

## Tasks and verification

1. Persistent grant/evolution/passives implemented. Includes actual entity save/load and death-respawn scenarios.
2. Base nonprojectile actions implemented. Includes actual damage/cooldown/overlap/stone/absorption/evasion scenarios.

Both tasks are implemented, **not integration verified**. `git diff --cached --check` passed for the seven assigned files. No Gradle, GameTest, client, or runtime verification was run in this worker, per the locked single root build lane. Four methods in `PillarmanStateGameTests` await root test registration and execution. Source-only inspection is not a pass; no TDD RED/GREEN execution is claimed.

All seven files were staged individually. No commit or push, no STATE/ROADMAP edits, and no original `src/` modification. The zero commit count is intentional under the user's staged-delivery instruction, not an assertion that changes were committed. Token actual is rounded-up characters/4 of the seven new staged files (64,509 characters), excluding this metadata file.

## Exact source coverage

`fabric/migration/PILLARMAN_PARITY.md` records method pairs and all22 registered actions. Inspected: `PillarmanData`, `PillarmanPowerType`, `NonStandPower`, `NonStandPowerType.updatePassiveEffects/afterClear`, `PillarmanAction`, `ModPillarmanActions`, `BleedingEffect.applyStoneMask`, acquisition/mode commands, all seven base action classes, inherited `VampirismClawLacerate` punch/block behavior, `HamonHealing.updateEffect`, `Action.cooldownFromHoldDuration`, `DamageUtil.dealPillarmanAbsorptionDamage/getDamageWithoutHeldItem`, and `VampirismUtil.consumeEnergyOnHeal`.

Implemented source quirks: L=2/4/5/6; stage3 Aja advancement despite Aja mask jumping to4; command modes below4; Heavy Punch checks10 energy but spends none; absorption targets all original living types rather than vampire feeding whitelist; stage>1 physiology and regen thresholds; persistent stone cleared on actual death; transient blades/fuses; absorption permits overlapping instant actions; original regeneration application-phase merge.

Assets inspected at definition/copy-rule level: `ModSounds` original Pillar IDs and Fabric original asset-copy rules. Referenced original awakening/mode/swing/punch/strong-regen/evasion/heavy-punch events. No image/animation/waveform runtime verification was performed. Original orange senses outlines, layers, hand/third-person animations and absorption loop remain05-34; world assets remain05-35.

## Root integration contract

- `PillarmanService.initialize()` registers attachments, criterion triggers and payload only.
- Root callbacks: `tick(ServerPlayer)`, `onDeath(LivingEntity)`, `copyAfterRespawn(old,new,alive)` after Fabric attachment copy, `disconnect(ServerPlayer)`, `clearAllRuntime()`, `updateAttributes(LivingEntity)` on join/load. Root `ALLOW_DAMAGE` invokes `!PillarmanBaseActions.evade(entity,source)`.
- Commands/masks: `hasPillarman`, `state`, `grant`, `clear`, `setStage`, `setMode`, `setStoneForm`, `applyMask(player,aja)`. Item durability/activation stays with root. Grant guards Hamon/vampire/zombie conflicts.
- Shared hooks: `onFoodEaten(entity,nutrition)`, `consumeEnergyOnHeal(entity,amount)`, `swimmingAcceleration`, `protectsPassive`, `highLifeForce`, `staminaFactor`, `targetResolveMultiplier`; stone UV/Hamon immunity and nonstone species classification stay with root shared hooks.
- Root supplied dependencies: `HamonCharges.protectFromContact` and `jojo:pillarman_absorption` damage type. Absorption actual damage has no attacker, matching the source generic damage route; Hamon contact checks receive an attacker-bearing source.
- Payload enum: `HEAVY_PUNCH`, `START_ABSORPTION`, `START_STONE_FORM`, `REGENERATION`, `START_SENSES`, `START_EVASION`, `START_AGILITY`, `KEEP_HELD`, `STOP_HELD`, `RELEASE_HELD`, `LEAP`. No client target/resource values.
- `PillarmanAttachments.ACTIONS` synchronizes held enum ordinal/ticks, punch ticks, regeneration/evasion/leap cooldowns. `PROGRESS` synchronizes state; `BLADES` synchronizes transient appearance; owner-only `SENSES_TICKS` supplies exact scan cadence/radius through `sensesScanDue/sensesRadius`.
- Later action modules can call `PillarmanBaseActions.canStart/stopHeld`, energy APIs, `setBlades` and `addEatenTntFuse`. Custom projectile classes implement `PillarmanBaseActions.EvasionProjectile` to preserve original non-evadable/Stand-damage restrictions.

## Deviations and limits

- User-locked stage-only delivery overrides executor commit/state boilerplate. Root owns all shared existing files and build commands.
- Forge SWIM_SPEED is absent on the pinned platform. Original multiplier is exposed as a water-acceleration hook; root wires it alongside existing Hamon adaptation.
- Source-inactive Ultimate Thing/stage5/prototype progression/evasion afterimages are not invented. Mode/projectile actions remain explicitly assigned to05-29–33; presentation05-34 and world05-35 remain separate. These are intentional future-plan boundaries, not completed parity.
- Runtime recursion guard bounds counters between two agility users. Packet leases and dimension cleanup protect server authority without allowing clients to supply action clocks.
- No auth gate, dependency install, or source placeholder was introduced. Build/API correctness and actual shared event integration still require root validation.

## Self-Check: PASSED (source handoff only)

All seven assigned new files exist and are staged. Cached whitespace check passes. No commits expected or claimed. World cases remain explicitly unrun; the plan must not be promoted to verified until root executes them.
