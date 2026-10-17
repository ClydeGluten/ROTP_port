---
phase: 05-remaining-content-and-compatibility
plan: 47
status: awaiting-integrated-verification
completed: false
subsystem: shared-stand-linked-state
tags: [fabric, item-components, actual-holders, persistent-effects, lifeform-subtypes]
requires: [05-28]
provides: [stable-tracked-items, real-holder-extraction, linked-effect-queries, encountered-lifeforms]
affects: [05-24, 05-45, 05-48, 05-50, 05-51, 05-52, 05-63, 05-73]
tech-stack:
  added: []
  patterns: [persistent-data-component, authoritative-slot-resolution, owner-only-snapshot, existing-effect-lifecycle]
key-files:
  created: [TrackedItemIdentity.java, ItemTrackingService.java, ItemTrackingPayload.java, StandEffectStore.java, LifeformsEncountered.java, LifeformSubtypes.java, TrackingHooks.java, StandTrackingGameTests.java, STAND_LINKED_STATE_PARITY.md]
  modified: []
key-decisions:
  - Track only source single-item stacks and resolve actual holders before extraction.
  - Keep StandEffects as the sole persistent effect owner and server tick lifecycle.
  - Preserve original subtype syntax and its sole extra Coco Jumbo with Stand variant.
  - Keep private effect payloads server-side while synchronizing public target identities.
actuals:
  tokens: 22577
  tasks: 2
  commits: 0
---

# Phase05 Plan47: Persistent linked effects, item identities and lifeform encounters

The isolated batch implements stable tracked stacks, durable actual-holder locations, exactly-once source extraction, reusable effect queries and the original discovered/native lifeform catalog. Shared root hooks and the consolidated build/runtime lane remain pending. No new Stand is enabled by this infrastructure.

## Implemented tasks

1. Ported `TrackerItemStack` and `SidedItemTrackerMap` identity, owner registration, transfer, disappearance and marker contracts to components/attachments. Real inventory/equipment/container/projectile slots remain authoritative. Owner-only snapshots omit unresolved holders. Added vanilla transfer callbacks, embedded arrow/knife handling, traded-item representation, source villager refusal/piglin retaliation, marked ammo priority and the original extra mob pickup pass.
2. Added `StandEffectStore` on top of root28 `StandEffects`: owner/target queries, source max-dot selection, optional expiry delegation, public tracking/self target snapshots, inverse queries and no duplicate server ticking. Added exact base/subtype encounter storage, source twelve-block validation, supported-ID checks, original lifeform predicate, source biome/structure eligibility and zero-bit random spawn checks. Root owns shared duplicate-effect UUID canonicalization and the backward-compatible Extra subtype field.

Exact method pairs and shared callback signatures are in `fabric/migration/STAND_LINKED_STATE_PARITY.md`. Original sources, assets and saves are untouched; no assets are introduced. The plan's nine implementation/test/ledger files are prepared in `/tmp/rotp-tracking47-work` until root releases the active draft18 lane.

## Source and inheritance coverage

Read `TrackerItemStack`, `SidedItemTrackerMap`, all nineteen itemtracking mixins, the separate marked-pickup `mixin/MobEntityMixin`, `ItemProjectileEntity` tracking/pickup and `ItemNbtProjectileEntity` inherited component storage. Inspected active Knife/Clackers/Blade Hat throw/embedded callers, `MerchantData` and trade-refusal interaction. Read `StandEffectsTracker`, `StandEffectInstance`, `GEItemMarkEffect`, GE mark/create relevant holder branches, CD anchor make/move, registered effect policies and type registry. Read `LifeformsMetMobs`, entity subtype/key/prototype classes, original encounter packet/client callback and complete GE lifeform eligibility predicate.

Source inactive: the commented itemtracking event subscriber, piglin scam-counter TODO and GE's rejected `BLOCK_IS_ITEM` transformation branch. Original extra subtype registration contains only `jojo:coco_jumbo_turtle#stand`; no vanilla color variants are invented.

## Integration handoff

- `TrackingHooks.initialize()` registers common components/attachments/payloads. Root wires actual transfer/load, player/living lifecycle, successful embedding, pickup, merchant and marked-mob-pickup callbacks. `livingTick` handles presentation only; root28 keeps the one server effect tick.
- `ItemTrackingService.mark/resolve/extract/unmark/restoreRegistration` are the shared48 action API. Actual-source extraction requires matching owner and current dimension; the concrete action enforces its source range and eligibility. A raw Blade Hat thrown-stack adapter closes24's pending tracking handoff.
- `StandEffectStore.getTargetLookedAt(owner,type,range)` and `target(owner,entry)` support CD's original64-block dried-blood homing. `getOrCreate` preserves existing age. Root calls `ownerChanged` after effect mutation and `syncTo` when an observer starts tracking.
- Client receives `ItemTrackingPayload` and its nested `Effects`; original encounter intent is nested `Encounter`.50 supplies original GE marker/glint/toast/selection presentation.
- `LifeformSubtypes.registerCocoJumbo(grantMrPresident,hasMrPresident)` is52's concrete turtle hook. Until52 exists, that subtype is unavailable. Root Extra now accepts optional bounded subtype separately from base Identifier;48 validates the pair against this catalog.

## Verification and actual status

Pinned cached26.3 jars were inspected with javap for components, entity/item slots, pickup origins, codec/network APIs, entity package moves, biome spawning, gossip and protected piglin behavior. Three meaningful GameTests are prepared: actual item pickup/container save and dimension transfer/extraction; actual fired arrow embedding; NPC effect reload/unresolved target/duplicate-load/expiry plus real lifeform encounter persistence. Worker source hygiene only; no Gradle, compilation, runtime or client acceptance claim.

Root must connect the full callback batch and run the cases before47 can be marked complete. Concrete GE effects/actions, Coco Jumbo acquisition and GE client presentation belong to48/50/52 and are not placeholders in this batch. No commit/push or shared planning-state edit was made.

## Deviations and corrections

- Modern immutable item identity and versioned owner records replace Forge capability-tag rewriting. Holder UUIDs survive dimension transfer/rejoin; unloaded holders remain unresolved without forcing chunks.
- ItemStack copies retain identity but cannot transfer authority until observed in a real slot. Duplicate persisted effect UUIDs are deduplicated by root to prevent repeated ticks after load.
- Active marked-loot pickup and merchant refusal were found outside the planned itemtracking folder and included as essential source behavior.
- Original subtype strings cannot fit an Identifier because of `#stand`; root added a bounded optional subtype while retaining existing constructors and version1 decode.

## Self-Check

All seven main classes, three-case GameTest class and source ledger exist in the isolated handoff. `git diff --no-index --check` passed for all ten files, including this summary. Shared callback wiring and root build/runtime validation remain open; this summary does not mark the plan complete.
