---
phase: 05-remaining-content-and-compatibility
plan: 75
status: awaiting-integration-verification
completed: false
requires: [05-25]
provides: [stand-action-learning, stand-parts, persistent-effect-lifecycle, action-movement-factors]
---

# Persistent Stand contracts

Implemented and compiled with the new equipment/model source set in `/tmp/rotp-rest-foundation-compile3.log`. No new runtime pass is claimed. Plan76 supplies the focused live-world persistence/gating scenarios; root owns lifecycle integration and remaining source parity.

## Source pairs actually read

- `StandActionLearningProgress` complete class and nested entry/map → `StandActionTraining`: per-Stand/action entries; absent=-1, learned zero retained, unknown identifiers preserved; finite/bounded versioned persistent codec; owner sync. `StandPower.unlockAction/getLearningProgressPoints/setLearningProgressPoints/addLearningProgressPoints` and `StandAction` training/cooldown methods → explicit maximum/Resolve multiplier APIs. Nontrained/Creative initial maximum, trained initial zero; positive burst multiplier; source above-current-cap retention.
- `StandInstance` complete class and `StandAction.checkConditions` → `StandParts`: MAIN_BODY/ARMS/LEGS, idempotent remove/add, complete legacy defaults, per-instance Stand identity and synced persistent mask. Custom names/skins stay in plan58. `EntityStandType.tickUser` → original319-tick amplifier1 Weakness/Mining Fatigue for missing arms and Slowness for missing legs. No invented raw speed penalty.
- `StandEffectsTracker`, `StandEffectInstance`, `BoyIIManStandPartTakenEffect` complete classes → `StandEffects`: persistent UUID/type/target/age/payload, source start/tick/stop and death/logout/change policies, same-world lazy target lookup, unloaded target UUID retention, idempotent callback cleanup, unknown saved type retention. Concrete GE/Boy effects and client effect presentation/sync are plan47/53; this contract alone does not deliver those features.
- `StandEntityAction.getUserWalkSpeed` existing adapter → `StandMovement.currentManifestation` and action-ID movement overrides. Existing Time Stop charge and combat values remain.

## Public handoff

`StandActionTraining.points(user, Identifier, maximum)`, `rawPoints(user, standId, Identifier)`, `unlocked(user, Identifier)`, `unlock(ServerPlayer, Identifier, trained, maximum)`, `set(LivingEntity, Identifier, points)`, `add(ServerPlayer, Identifier, amount, maximum, resolveMultiplier)`, `clear(user)`. DATA/CODEC and immutable State/Entry exposed. Existing SP/TW Time Stop training stays in its shipped Resolve attachments; future action training must not overwrite or merge those values.

`StandParts.has(user, Part)`, `mask(user)`, `remove/add`, `set(user, mask)`, `reset(user)`, `tick(user)`. Server action dispatch must enforce required parts; root76 wires that route. `reset` is only for newly granted/cleared instances; returning stolen parts must use idempotent add/restore.

`StandEffects.register(type, Handler)`, `add(user,type,target,CompoundTag)`, `entries/find/target`, `remove(user,UUID)`, `tick(user)`, `cleanup(user, Removal)`, `clearRuntime()`. Handler requires start/tick/stop/needsTarget and may override target-death/lifecycle policy. Tick returns updated same-identity Entry or null to remove. Data is copied; persistence limits128 effects and64KiB per payload. Concrete types remain unregistered until feature modules exist. Unknown saved entries stay inert and preserved. Client transport belongs47 rather than syncing full mutable NBT every tick.

`StandMovement.actionFactor(player, actionId, factor)`, `releaseAction`, `releaseAllActions`, `clearRuntime`. Factors compose by minimum with manifestation/charge penalty. Input still is server authoritative.

Root already hooked attachment initialization, normal power ticks, source death/logout/change policies, grant/clear parts reset and server-stop runtime cleanup. These shared-file changes are root-owned, outside this bounded plan's five files. Source imports with unknown IDs are not promoted into playable catalog entries.

## Verification boundary

Main/client/gametest compilation passed. Runtime save/rejoin, missing-part action gates and actual generic actions remain unverified until76 integration. No tests were run solely for these data objects. No original source/assets or user saves were modified. Stage only, no commit/push. No broad parity claim.
