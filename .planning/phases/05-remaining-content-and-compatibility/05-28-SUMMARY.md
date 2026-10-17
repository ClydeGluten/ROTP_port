---
phase: 05-remaining-content-and-compatibility
plan: 28
status: awaiting-integration-verification
requires: [05-75, 05-76, 05-27]
provides: [per-ID client action rows, registered intent routing, generic manifestation view]
affects: [05-37, 05-39, 05-41, 05-44, 05-48, 05-51, 05-53]
---

Implemented generic client routing; prerequisite delivery/runtime validation is still pending. This summary publishes the current API so independent Stand modules can prepare without enabling unfinished content.

## Combined contract

- `StandDefinitions` contains all nine source IDs and source stats/weight/color/acquisition. `playable` remains false for unfinished entries. Root enables only after actual integration/presentation acceptance. Nonmanifesting stands use `Kind.NO_SUMMON` and actions with `requiresManifestation(false)`.
- Entity modules implement `StandManifestation`; root registers `StandService.registerFactory(id, factory)`. Shared lifecycle and action handlers use `currentManifestation`. Existing concrete `current/ownStand` are compatibility views for specialized SP/TW callers. `StandView.ownManifestation` drives generic controlled camera and anchor state; possession keeps camera priority.
- `StandActionRegistry.register(Definition,Handler)` owns source action IDs, LEFT/RIGHT/HIDDEN row/slot, required parts, shift link, hold/fire timing, costs, cooldown, training and movement. See05-76-SUMMARY for exact Builder/Handler semantics, delegation and rejected-by-default extras. Runtime authority never comes from a client HUD value.
- `StandActionTraining` retains finite per-action learning; `StandParts` holds source body/arms/legs; `StandEffects` persists registered continuous effects and unresolved target UUIDs. See05-75-SUMMARY for lifecycle APIs and concrete-effect dependencies.
- `ActionCatalog.attacks/abilities(String)` derive Stand rows from registered definitions. SP/TW map source IDs to the existing saved short aliases; new modules use canonical IDs. All nine Stand IDs are eligible for local layout loading. Unfinished empty definitions do not acquire SP defaults.
- `ActionCatalog.Move` derives original action translation/icon from canonical ID, looks up owner-only `StandHudState.REGISTERED` for unlock/parts/availability/training/cooldown, and uses source shift definitions. The shared original overlay draws registered cooldown/training/charge data, Stand color/name/portrait by definition; nonmanifesting power icons do not depend on a humanoid summon.
- Source-dependent substitutions register `StandActionRegistry.registerReplacement(standId, BiFunction<ServerPlayer,Identifier,Identifier>)` and matching `ActionCatalog.registerReplacement(standId, BiFunction<Minecraft,Identifier,Identifier>)`. Substitution follows Shift and precedes required-part/training validation; cross-Stand/unknown IDs are rejected.
- `StandControlPayload` routes the shared `StandManifestation.control(sideways,forward,vertical,yaw,pitch)` contract. Shared camera opacity and finisher display accept the interface.
- `StandSelection.sendRegistered(move,phase[,extra])` sends version1 PRESS/KEEP/RELEASE through the new bounded channel. Generic held actions refresh every5 ticks and release on the mapped mouse edge. Legacy SP/TW keys and enum packet ordinals remain compatible. Item/entity-choice extras require explicit server handler validation; forthcoming chooser modules supply their own local UI before sending.

## Source mapping and verification boundary

Inspected original `ModStandsInit` action rows/shift variants, `ActionsOverlayGui` icon/training/cooldown/charge renderer and existing original-derived port render helpers. Rendering reuses original assets and offsets; only ID selection/generalization changed. Accepted independent orbit behavior remains. Pillar non-Stand UI connects separately through05-34/36.

Current source sets are being compiled in root's single lane. No current integrated/dedicated client pass claimed yet. Required checks include retained SP/TW editor/reload/aim and registered-action UI input/display in a real client. Downstream gameplay can implement against this contract but must not treat this summary as completed verification or enable natural rolls early.
