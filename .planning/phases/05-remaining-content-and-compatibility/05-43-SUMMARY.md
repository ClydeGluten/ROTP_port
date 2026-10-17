---
phase: 05-remaining-content-and-compatibility
plan: 43
status: awaiting-integrated-verification
completed: false
subsystem: block-history-restoration
tags: [fabric, crazy-diamond, chunk-attachments, restoration, source-parity]
requires: [05-28]
provides: [per-chunk-broken-block-history, actual-loot-capture-api, source-restoration-payment, config-gated-chunk-persistence]
affects: [05-41, 05-44, 05-61, 05-71, 05-72]
tech-stack:
  added: []
  patterns: [server-chunk-attachment, explicit-chunk-dirty-marking, actual-generated-loot-capture]
key-files:
  created: [BlockHistory.java, BrokenBlockRecord.java, RestorationHooks.java, BlockHistorySavedData.java, BlockRestorationGameTests.java, BLOCK_RESTORATION_PARITY.md]
  modified: []
key-decisions:
  - "Capture always runs; source saveDestroyedBlocks defaults false and gates only save/load."
  - "Use actual chunk attachments, not a level-global cache; retain unlimited source map lifetime."
  - "Preserve creative XP affordability/material consumption, immediate improper-position assignment, and no placement rollback."
requirements-completed: []
actuals:
  tokens: 14774
  tasks: 2
  commits: 0
plan_head_before: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
plan_head_after: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
coverage:
  - id: block-history-restoration
    description: Actual break state/drop/XP capture and source restoration rules
    requirement: PARITY-03
    verification:
      - kind: integration
        ref: fabric/src/gametest/java/com/github/standobyte/jojo/fabric/BlockRestorationGameTests.java
        status: unknown
    human_judgment: false
duration: 10min
---

# Phase 05 Plan 43: Broken-block history and restoration Summary

Per-chunk state/drop/XP history, source restoration ordering and payment, and optional chunk persistence are implemented for root integration.

## Tasks delivered

1. Capture and restore slice: `BlockHistory`, `BrokenBlockRecord`, `RestorationHooks`, and three grouped world tests. Capture uses actual generated loot, rejects original inventory/Stone Mask/fire cases, keeps pending positive XP, supports source weak entity callbacks, and restores using source placement/priority/material/XP semantics.
2. Persistence and caller contracts: `BlockHistorySavedData` uses actual chunk attachments, source field names, registry-aware item codecs, required-field validation, missing-registry tolerance, first-load replacement pruning, and `saveDestroyedBlocks` save/load gating. Direct and scoped capture APIs are supplied for root/MR/HG/CD/shared combat integration.

No task commit was made: the locked user/plan instruction requires stage-only delivery. Both implementation tasks are delivered, but runtime acceptance and PARITY-03 completion remain pending root's integrated tests. No Gradle, build, push, original-source edit, user-save edit, shared bootstrap edit, or root planning-state update was performed by this worker.

## Original/Fabric method and asset coverage

- `ChunkCap` save/read/remove/reset/query/XP methods map to `BlockHistory`; `PrevBlockInfo` copies, persistent fields, `withEntities`, and `onRestore` map to `BrokenBlockRecord` and the saved-data codec.
- `CrazyDiamondRestoreTerrain` remember/forget, restore/filter/priority/placement, item matching/consumption, source inventory ordering, rounded eye position, range and aim checks map to `RestorationHooks`. The explicit `SourceType` / `ItemsSource` overload supports `CDTurnIntoAngeloRockEffect` target-first inventory ordering.
- Active callers audited: `LootTableMixin`, `FireBlockMixin`, `GameplayEventHandler.onBlockBreak`, `MCUtil.destroyBlocksInBulk` / `destroyBlock` / `blockCatchFire`, `MRFlameEntity.meltIceAndSnow`, `MRCrossfireHurricaneEntity`, `StandEntityHeavyAttack`, `HamonLifeMagnetism`, `CDTurnIntoAngeloRockEffect`, and `AngeloRockEntity`.
- Inheritance/callbacks audited: `StandEntityAction extends StandAction`, `StandEntityTask` perform/ticking-cost dispatch, `EntityMadeFromBlock`, `BlockShardEntity.crazyDRestore`, and `LeavesGliderEntity.crazyDRestore`. Source action ticking/stamina/camera/presentation remains plan 44's ownership, not silently replaced by these helpers.
- Assets inspected: original `particles/cd_restoration.json` spark frame references; sound JSON start/loop/ended restoration entries; metadata of the normal and improper 32×32 action textures. No asset file was changed. Original render/audio delivery belongs to plan 44/root.

Detailed method-by-method status, inactive-source exclusions, and integration examples are in `fabric/migration/BLOCK_RESTORATION_PARITY.md`.

## Public integration contracts

Root calls `BlockHistorySavedData.initialize()` once before loading chunks, and binds the common configuration to `setSaveDestroyedBlocks(boolean)`. Default is false. Attachment ID: `jojo:broken_blocks`. Capture is independent of this option; chunk unload owns runtime release. Changed mutable history explicitly marks its owning chunk unsaved.

Global hooks call `RestorationHooks.captureDrops(ServerLevel, BlockPos, BlockState, BlockEntity, List<ItemStack>)` for already-generated loot and `setDroppedXp(ServerLevel, BlockPos, int)` for positive player-break XP. `captureDrops` applies `GameRules.BLOCK_DROPS`. Direct `remember(...)` keeps source pre-mutation fire/melt/no-drop behavior; its nullable block entity is sampled before destruction. These direct calls do not require successful later mutation, matching their original source routes.

Optional success-owned custom mutations use `try (var capture = RestorationHooks.begin(...))`, actual `captureDrops` / `captureXp`, and `commit(success)`. Cancelled or repeated commits do not record; this API never rolls back world changes. Direct loot hooks share an active capture when explicitly scoped.

Restoration callers use `restoreBlocks(...)`, `getBlocksInRange(...)`, `sourceItemStacks(...)`, and the resulting original-position sets for source stamina and particles. `BrokenBlockRecord.withEntities(...)` accepts weak `RestorableBlockEntity` callbacks for owning shard/glider plans. Capture API was sent to root and the MR/fire worker before handoff.

## Verification status

`git diff --check` source hygiene passes. Runtime verification is pending. No TDD RED/GREEN or world-test pass is claimed: the locked plan explicitly reserves the single integrated build/client lane for root.

The three GameTests cover actual world mutation/full state/actual drop and XP consumption plus exclusions; cancellation/commit idempotence/creative affordability and payment/falling priority/replacement conflict; and actual full chunk serialization/deserialization with config gates, Keep/TickCount/XP/drop fields, missing registries, and first-load pruning. Serialization reconstructs a detached chunk through the pinned `SerializableChunkData` pipeline without touching user saves.

Pinned API inspection found default block states use string encoding in modern `BlockState.CODEC`. Persistence therefore uses `FULL_CODEC` to preserve the source compound shape. Local Minecraft/Fabric signatures and Fabric's attachment serializer mixin were inspected, and official attachment documentation was consulted. No dependencies or version pins changed.

## Source-inactive and remaining integration

The original history-forgetting loop and player chunk-resync body are commented out; neither was activated. There is no invented time-to-live or history cap. Client block-history synchronization and restored-block presentation remain root/plan 44 work; this worker does not claim Crazy Diamond gameplay or client acceptance from helper registration.

Root still owns registration of global loot/break/XP/fire hooks and these tests, active mutation call sites in shared combat, common-config binding, and the current-source server/client verification results. These are explicit ownership boundaries, not implementation placeholders.

## Deviations

None from the locked plan. Stage-only delivery, temporary drafting while root's build lane was active, shared-file exclusion, and root-only integration verification follow explicit instructions. The optional capture scope supplements direct source routes only where the caller owns a cancellable mutation; it does not change direct source recording behavior.

## Trust boundaries

Server-level APIs, dimension checks, loaded-chunk checks, per-chunk/build-height load validation, inventory exclusions, and strict required saved fields cover T-05-243. There is no new network endpoint, dependency, or automatic legacy-save migration. Item and block registry failures normalize individual saved entries instead of rerolling loot.

## Self-Check: PASSED

All six assigned implementation/test/ledger files and this summary exist in the repository; source hygiene passes. Shared integration and world-test verification remain explicitly pending. Commit count is zero by the user's stage-only instruction.
