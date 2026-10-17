---
phase: 05-remaining-content-and-compatibility
plan: 24
status: awaiting_integrated_verification
completed: false
subsystem: equipment
tags: [fabric, blade-hat, tommy-gun, paintings, source-parity]
requires: [existing-hamon-service, original-resource-copy, root-client-hooks]
provides: [blade-hat-gameplay, tommy-gun-gameplay, equipment-renderers, original-paintings]
affects: [05-27, 05-47]
tech-stack:
  added: []
  patterns: [server-authoritative-projectiles, item-components, modern-special-model-renderer]
key-files:
  created:
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/equipment/EquipmentContent.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/equipment/EquipmentClient.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/EquipmentGameTests.java
    - fabric/migration/EQUIPMENT_PARITY.md
  modified: []
key-decisions:
  - Keep original constant-velocity Tommy bullets despite the source's inactive gravity override.
  - Preserve normal Blade Hat immunity and gun bullet immunity bypass as separate inherited behaviors.
  - Preserve full item components and record Crazy Diamond tracking integration explicitly under 05-47.
commits: 0
actuals:
  tokens: 49548
  tasks: 2
  commits: 0
plan_head_before: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
plan_head_after: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
---

# Phase 05 Plan 24: Equipment implementation handoff

Blade Hat throw/equip/return with component-preserving recovery, source Tommy gun magazine/fire behavior with original 3D mesh and lifecycle audio, and two placeable original paintings are implemented for integrated verification.

## Work completed

Both implementation tasks have code and focused world checks. The inherited partial main files were audited against source item/projectile parents and corrected. Client rendering, worn armor, audio, item resources, geometry conversion, recipe/advancement, painting variants and seven GameTests were added. Registration, mixins and test metadata remain the root integrator's ownership.

The full original/Fabric method mapping, source-inactive behavior, resource inventory, and exact integration hooks live in `fabric/migration/EQUIPMENT_PARITY.md`. That ledger distinguishes implemented behavior from pending runtime verification.

## Deviations and corrections

- [Rule 1] Fixed pinned-platform API mismatches: moved painting package, color-collection wool access, void `Item.hurtEnemy`, direct arrow DamageSource construction for a non-AbstractArrow projectile, and modern swing accessors.
- [Rule 1] Audited inheritance and corrected the partial implementation's first-flight collision order, shooter-velocity inheritance, Blade Hat damage-immunity bypass and failed-hit deletion behavior. Restored successful-hit-only Joseph Hamon reward.
- [Rule 2] Added missing original client models/sounds, generic worn armor layer for players/mobs/armor stands, enchantment tags, original acquisition resources, and placeable painting tag.
- [User instruction] No task commits or worker Gradle runs. Delivery is staged only; root owns compilation and the integrated build/test lane. Commit metrics stay zero rather than inventing history.

## Verification

The mesh generator completed with 322 original body quads and 36 muzzle-flash quads. Cached Minecraft classes were inspected with `javap` for platform-specific APIs. Root `/tmp/rotp-rest-foundation-compile3.log` reports successful main, client and GameTest compilation. That build precedes the final generic armor-layer extension, which still requires the root integration compile. Assigned-file staged whitespace validation passed. World checks and client visual/audio verification remain pending.

## Pending acceptance and cross-plan work

- Run the seven EquipmentGameTests in the root batch and the equipment segment of the focused client flow: all held/worn/projectile models, gun muzzle/idle/swing transforms, remote firing/release, audio stop on release/disconnect, and both paintings.
- Plan **05-47** must connect the source `TrackerItemStack.setAtEntity` callback to Fabric Crazy Diamond item tracking. No Fabric tracking infrastructure currently exists. Preserved stack components alone do not close that original interaction.

## Self-Check: PASSED

All 25 assigned files exist and are staged, with no unstaged changes in those paths. `git diff --cached --check` passes. HEAD remains `69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0`; no commits were made, as required. The realized token figure measures staged diff characters divided by four, including the generated original mesh. This file-existence/staging check does not claim world/client acceptance.
