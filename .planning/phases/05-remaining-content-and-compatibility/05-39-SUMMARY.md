---
phase: 05-remaining-content-and-compatibility
plan: 39
status: awaiting-integrated-verification
completed: false
subsystem: hierophant-gameplay
tags: [fabric, hierophant-green, owner-bound-projectiles, server-actions]
requires: [05-28, 05-29, 05-75, 05-76]
provides: [hierophant-eight-actions, strings-and-grapples, trained-emerald-splash, barrier-network]
affects: [05-40, 05-55, 05-59, 05-71]
tech-stack:
  added: []
  patterns: [entity-owned-action-phases, provisional-action-summon, analytic-barrier-points]
key-files:
  created: [HierophantGreenEntity.java, HierophantGreenActions.java, HGStringEntity.java, HGEmeraldEntity.java, HGGrapplingStringEntity.java, HGBarrierEntity.java, HGBarriersNet.java, HierophantGreenGameTests.java, HIEROPHANT_GREEN_PARITY.md]
  modified: []
key-decisions:
  - Original action tasks own costs and phase timing while generic registry retains authority and lease checks.
  - Barrier stamina is charged per selected firing point before its emerald batch, following active source rather than compressed plan wording.
  - Preserve all source firing points with analytic segment intervals instead of quadratic distance tables.
actuals:
  tokens: 24269
  tasks: 3
  commits: 0
---

# Phase 05 Plan39: Hierophant Green gameplay

Eight source actions now drive real HG manifestations, extending attack/binding bodies, trained concentrated emeralds, held block/entity grapples and connected stamina-funded barrier segments. Implementation is ready for root integration; no compilation or world pass is claimed.

## Tasks and original method coverage

1. `HierophantGreenStringAttack.standPerform/getStandActionTicks`, `HGStringEntity`, inherited `OwnerBoundProjectileEntity` movement/whole-body collision/attachment, `MathUtil.xRotYRotOffsets`, `StandEntity.addProjectile` → `HierophantGreenEntity.fireStrings/addBodyProjectile`, `HGStringEntity`, shared `OwnedBodyPartEntity`. Exact eight/four geometry, source lifetimes, hit damage/binding/counterknockback, owner tracking, walls and cleanup.
2. `HierophantGreenEmeraldSplash.standTickPerform/getStandWindupTicks/getStandRecoveryTicks`, `HGEmeraldEntity.afterEntityHit`, `HierophantGreenGrapple`, `HGGrapplingStringEntity`, inherited `DamagingEntity`/`ModdedProjectileEntity` → HG task lifecycle, source fire rates/velocities/spread, accepted-hit training/concentrated unlock, source costs/cooldowns, held grapple attachment/pull/release. `StandEntityAction.onClick/afterClick`, `EntityStandType` provisional summon and `StandEntity` arms/full modifiers/fade are also mapped into registry preparation and synchronized HG mode.
3. `HierophantGreenEntity.attachBarrier/createUserString/getMaxBarriersPlaceable`, `HGBarrierEntity.tick/hurtTarget`, `HGBarriersNet.shootEmeraldsFromBarriers` → connected anchors, manual owner tether, source fifteen/hundred limits, original point selection and per-point resource debit, crossing/rip burst and deferred stopped-time effects, owner damage and dismissal cleanup.

Full inherited-method/asset coverage, source-inactive distinctions and platform adaptations are in `fabric/migration/HIEROPHANT_GREEN_PARITY.md`. `HierophantGreen20mEmeraldSplash` is empty and its special shout branch is commented; neither becomes an invented ninth action.

## Integration contracts

- Main bootstrap: `HierophantGreenActions.initialize()` once. Public registered types: `TYPE`, `STRING`, `EMERALD`, `GRAPPLING_STRING`, `BARRIER_ENTITY`. Eight public action IDs match the original registry. Generic action UI uses metadata and source-specific task state without SP/TW routing or time-stop training reuse.
- `StandActionRegistry.Handler` uses `ownsTaskLifecycle()`, `autoSummons()`, `prepare(Context)`/`Preparation`, `canUseWithoutManifestation(Context)`. `StandService.prepareSummon` supplies provisional ownership; only Guard selects ARMS. All other HG actions retain the inherited builder's FULL default, independently of their required parts. Existing full summons never demote; existing arms promote before later requirement rejection.
- Mode/render getters: `isArmsOnlyMode`, `wasSummonedAsArms`, `showMainArm`, `showOffArm`, `summonLockTicks`, `gradualSummonTicks`, `unsummonTicks`, `getUnsummonDuration`. State setters: `setArmsOnlyMode()` or `(boolean, boolean)`, `fullSummonFromArms()`.
- Root frozen-entity interception calls `HGBarrierEntity.tickFrozen()`; shared bound-body freeze follows living owner. Root Resolve handling recognizes `EvasionProjectile.standDamage()`, including HG bodies/emeralds. Shared accepted-hit body ignition follows inherited `DamagingEntity`.
- Root installs40 dedicated model/projectile/marker presentation and registers `HierophantGreenGameTests`. Playability/natural-pool enablement remains deferred until complete71 delivery acceptance.

## Verification and remaining work

Three production GameTests cover registered strings/binding, owner/wall/dismissal lifecycle, provisional summon rejection and arm fade; emerald hit/training/concentration and actual player save/load; connected barrier funding/ripping and actual grapple release. Tests manually call both `Entity.commonTick()` and `tick()` on the pinned26.3 API. They are supplied for root's consolidated lane and are currently unrun. Dedicated server/client scenarios, source sound audience and40 rendering are pending; no result from an earlier build is reused as evidence.

No worker Gradle, commit or push. Only assigned paths are staged. Source tree and shared planning state are unchanged.

## Deviations

- Source-corrected barrier cost wording: per firing point, not per emerald projectile.
- Analytic point/exclusion representation preserves long-segment candidate positions without source quadratic allocation.
- Manifestation runtime lifecycle is session-scoped through existing Fabric service; persistent owner Stand data, training and cooldowns use the existing saved attachments. Source projectile no-save/no-summon flags remain exact.
- Root59 config integration uses `StandConfig.staminaFree` for resources/stat efficiency, configured physical resistance and a single emerald power multiplier at the actual damage boundary; shared owner-bound bodies apply their multiplier centrally. Creative-only cooldown behavior remains separate.

## Self-Check

Assigned implementation, fixture and ledger files exist. Cached `git diff --check` passed on all assigned paths. Compilation and real-world verification remain pending with root.
