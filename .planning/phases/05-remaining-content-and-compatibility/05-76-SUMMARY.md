---
phase: 05-remaining-content-and-compatibility
plan: 76
status: awaiting-integrated-verification
subsystem: stand-action-authority
tags: [fabric, server-authority, networking, persistence, stand-actions]
requires: [05-75]
provides: [registered-stand-action-intents, source-action-metadata, legacy-dispatch-bridge, registered-owner-hud]
affects: [05-39, 05-47, 05-53, 05-56]
tech-stack:
  added: []
  patterns: [bounded-versioned-intents, server-owned-registered-handlers, persistent-resource-id-cooldowns]
key-files:
  created: [StandActionRegistry.java, StandActionsGameTests.java]
  modified: [StandActionPayload.java, StandHudState.java, AbilityCooldowns.java]
key-decisions:
  - Preserve original enum channel and constructor; add a separate versioned registered-intent channel.
  - Delegate existing SP/TW gameplay to its existing services without a second debit or training/cooldown owner.
  - Default-reject all client extra fields unless a concrete handler explicitly validates their ownership and allowed values.
completed: false
actuals:
  tokens: 12863
  tasks: 1
  commits: 0
plan_head_before: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
plan_head_after: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
---

# Phase 05 Plan 76: Registered Stand action authority

Registered resource-ID intents and legacy SP/TW intents now share server ownership and part checks. Generic handlers use persistent learning/cooldowns, source hold/release timing and movement factors; existing SP/TW combat retains its shipped execution services. Implementation is ready for root's integrated compilation and live-world verification; no runtime pass is claimed.

## Source pairs actually inspected

| Original source | Fabric implementation and retained behavior |
| --- | --- |
| `ModStandsInit` SP/TW registrations, default left/right hotbars, shift and finisher variations | `StandActionRegistry.initialize`: original action IDs, hotbar positions, required parts, costs, unlock levels, charge thresholds and shift metadata. Existing SP/TW handlers call `StandService`, `TheWorldActions` and `TimeStopService`. |
| `Action` constructor/builders, hold duration getters, cooldown technical/additional calculation; `PowerBaseImpl.tickHeldAction/stopHeldAction`; input release caller | Definition/Builder and dispatch/tick/finish. Maximum is max(explicit duration, charge threshold); maximum stops before the final ticking callback. Release runs stopped-holding callback before performing a charged action. A fire threshold alone does not invent automatic firing while a longer hold remains. |
| `StandAction.checkConditions/isUnlocked/canBeUnlocked/getMaxTrainingPoints/resolveLearningMultiplier/getCooldownAdditional/onPerform` | Required parts, learned-zero versus absent state, resolve unlock, default maximum1, learning multiplier4, default Resolve additional-cooldown multiplier0, technical cooldown retained in Creative. Handler overrides cover source-specific maxima/gains/durations. |
| `StandAction` inherited cost methods, `StandPower.consumeStamina/isStaminaInfinite`, `StandUtil.standIgnoresStaminaDebuff` | Generic Creative bypass, initial/per-tick costs, clamp at zero, Resolve burst continues through insufficient stamina. Existing actor-task costs remain delegated because `StandEntityAction.consumeStamina` intentionally does not debit a second time. The future original config adapter owns the disabled-stamina configuration; this plan adds no new configuration. |
| `StandEntityAction`, light/heavy/barrage/block builders and `StandEntityHeavyAttack.getFinisherVariationIfPresent` | Source base costs and movement factors; required ARMS/LEGS/MAIN_BODY. The World heavy resolves to kick before part gates when arms are missing but legs remain. Root owns the complementary actor finisher part checks. |
| `StandActionLearningProgress`, `ActionCooldownTracker` and plan75 contracts | Persistent resource-ID cooldowns retain unknown IDs and remaining/total clocks; integer phase wrappers still map original IDs. Registry uses plan75 learning, parts and linked-effect services. |
| Existing Fabric `StandService`, `StarPlatinumEntity` action entrypoints, `TheWorldActions`, `TimeStopService`, `ProgressionService` and old payload/HUD | Legacy ordinal values and old HUD record/codec unchanged. Original targeting, costs, hit timing, Time Stop charge/training and cooldowns continue through these handlers. |

No assets are created or changed in this plan. No inactive remaining-Stand action IDs are promoted to playable handlers merely because they appear in source.

## Stable registration API

`StandActionRegistry.register(Definition, Handler)` accepts definitions built through `Definition.builder(Identifier action, String standId)` and these builder methods:

- `hotbar(Hotbar.LEFT/RIGHT/HIDDEN, slot)`, `parts(StandParts.Part...)`, `shift(Identifier)`.
- `hold(maxTicks, fireAfter, continueHolding)`, `cost(initial, perTick)`, `cooldown(technical, additional)`, `resolveCooldownMultiplier(float)`.
- `training(resolveLevel, trained, maximum)`, `movement(factor)`, `requiresManifestation(boolean)`.
- `recoveryFollowupOf(Identifier...)`, `requiresEffect(Identifier...)`, `delegated()`, `build()`.

`Handler.execute(Context)` returns whether the actual server action started. Optional methods are `validateExtra(Context, Extra)`, `canUse(Context)`, `startHolding(Context)`, `tracksHold(Context)`, `tick(Context, ticks)`, `keep(Context)`, `release(Context, ticks, cancelled)`, `maxTraining(Context)`, `trainingGain(Context)` and `cooldown(Context, ticks)`. Context exposes player, definition, current owned manifestation and extra. All target discovery, UUID ownership and selection allowlists belong to the concrete server handler. Effect eligibility checks presence of required registered effect types; concrete target relationships still belong to the handler.

Use ordinary definitions for registry-owned resource/training/cooldown lifecycle. `delegated()` is the compatibility bridge for an existing complete lifecycle, as with SP/TW; it skips generic learning, cost, cooldown and recovery handling while preserving ownership/part checks. Future modules must explicitly choose a single owner for each lifecycle rather than treating delegated registration alone as parity proof.

`dispatch(ServerPlayer, Identifier, Phase.PRESS/KEEP/RELEASE, shift, Extra)` is the authority entrypoint. `definition`, `actions(standId)`, `held`, `heldTicks`, `partsAvailable`, `maximumTraining`, `available` and `unlockAvailable` provide query/learning integration. Generic `available` reflects registry requirements and handler `canUse`; existing delegated services still make the final action-specific acceptance decision. Do not equate a registry preview with guaranteed execution.

`Extra(Optional<Identifier> selection, Optional<UUID> item, boolean recoveryModifier)` defaults to `Extra.EMPTY`; default handlers reject every nonempty extra. `StandActionPayload.Registered` provides separate TYPE `jojo:registered_stand_action` and CODEC version1: IDs bounded to256 characters, phase byte, shift boolean, only three recognized extra flags. No target entity, damage or training value can be submitted. The existing `stand_action` ordinal channel and `StandActionPayload(Action)` remain unchanged.

`StandHudState.REGISTERED` is a separate transient owner-only attachment. Its `RegisteredState` carries Stand ID, up to512 ActionViews, current held ID and held ticks. Each ActionView contains action ID, cooldown remaining/total, training/maximum, unlocked, parts and available. `captureRegistered(player)` derives server values. The old record constructor, fields, EMPTY and STREAM_CODEC remain compatible. `AbilityCooldowns.DATA/CODEC`, identifier overloads and all integer wrappers remain available.

## Shared integration handoff

- Existing `StandActionPayload.initialize()` initializes the registry and both receiver channels. Existing `StandHudState.initialize()` ticks held registry state once before capturing old/new owner HUD snapshots.
- `StandService.dismiss(player)` must call `StandActionRegistry.cancel(player)` before removing the manifestation. Root owns that hook and complementary entity heavy-finisher part selection. Grant/clear/death/dimension paths already use dismiss.
- Disconnect cancels on the server executor; server stop clears runtime holds/last-action state. Persistent cooldowns are still ticked once by the existing `StandService` loop, not a second registry tick.
- Concrete remaining Stand modules register their own handlers; no shared enum edits are needed. A handler's `validateExtra` must explicitly reject unrecognized GE subtype/item UUID or CD modifier combinations. Unknown saved cooldown/training IDs remain preserved and inactive.

## Verification and pending work

`git diff --check` passed for the five assigned Java files. The source/stub scan found no TODO/FIXME/placeholder implementations. Root owns all Gradle/client/server verification; this worker did not run them.

Three focused `StandActionsGameTests` cases are supplied:

1. `registeredAndLegacyPunchHaveIdenticalAuthoritativeEffects`: two actual SP manifestations attack actual cows; one uses the old enum route, one round-trips the new bounded payload. Checks exact10 stamina debit once and matching original4 damage after the real windup.
2. `registeredHoldPersistsTrainingCooldownPartsAndRejectsStaleRelease`: a registered test handler starts actual Stand guard, charges initial/per-tick cost, rejects a mismatched release, then restores identifier cooldown/training/stolen arms through actual player save/load. Both legacy and registered punches reject missing arms and owner HUD reflects restored state.
3. `unknownForgedAndWrongOwnerIntentsDoNotMutateWorld`: rejects unknown ID, another Stand's ID, unapproved selection/item/modifier and malformed payload version without attack or stamina mutation.

These tests are unrun until root's integrated lane reports their results. Neither registration nor compilation alone establishes source gameplay parity. No broad parity completion is claimed.

## Deviations and threat boundary

The explicit plan/root contract requires staging only and shared verification, so no commits, global STATE/ROADMAP edits or independent Gradle execution were performed. Plan75's persistent cooldown implementation was preserved and tightened only with identifier/runtime entry bounds.

T-05-276: new network entrypoint accepts bounded intent, verifies same player/Stand/world/manifestation, part availability, source eligibility and allowed extra data. Held input has a12-tick lease and captured manifestation/dimension identity; another action's release cannot cancel it. Server handlers retain target authority. T-05-SC: no dependency or pinned-stack changes.

## Self-Check: PASSED

The five implementation/test files exist; ownership is limited to those paths and this summary. Source hygiene passed. Runtime verification remains explicitly pending; stage-only delivery contains no commit claim.
