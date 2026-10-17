---
phase: 05-remaining-content-and-compatibility
plan: 25
status: awaiting_integrated_client_verification
completed: false
---

# Stand definition and manifestation foundation

Implemented the shared definition and lifecycle boundary while preserving the shipped SP/TW identifiers and constructors. Original `ModStandsInit` builders supply all nine active definitions, source stats, precision development, Chariot armor/toughness, colors, acquisition categories and weights. Dormant Killer Queen remains absent. The original source is unchanged.

`StandDefinitions.Definition` distinguishes known source content from integrated playable content. Only SP/TW are currently playable and naturally assignable. `StandAllocation.DEFAULT_CATALOG` derives from the enabled Arrow definitions, preserving the user's uniform integer/generation rule. Encounter/animal definitions cannot enter that pool. Unknown IDs no longer acquire SP stats; invalid administrative grants validate before modifying existing ownership.

`StandManifestation` exposes the actual Mob, owner, definition, action/pose state, control, guard and lifecycle methods. `StarPlatinumEntity` implements it and derives stats/name from its registered entity type. SP-only specials now explicitly require SP. `StandService` owns a common manifestation map and factories; its old `current()` method remains a compatibility view until plan 28 converts the remaining consumers. Existing source-specific SP/TW action behavior is preserved.

Public contracts:

- `StandDefinitions.definition(String)` returns a known definition or throws; `find(String)` handles unknown saved IDs safely; `all()` and `naturalPool()` expose immutable catalogs.
- `StandService.definition(String)`, `registerFactory(String, Function<ServerLevel, ? extends StandManifestation>)`, and `currentManifestation(ServerPlayer)` are the module registration/lifecycle boundary.
- `StandService.current(ServerPlayer)` remains the old SP-compatible view. New modules may extend the shared entity but must provide their own original actions and presentation before enabling their definition.
- `StandAttachments.resolveType(String)` and `resolveType(Player)` provide independent per-ID Resolve attachments. Existing `stand_resolve` and `the_world_resolve` keys are retained unchanged.
- `StandProgress.canStopTime()` and all actual time-action entrypoints reject unrelated Stands, including creative players. A zero duration is returned for non-time-stoppers.

Verification: `/tmp/rotp-rest-server-verification.log` passed all 216 required world tests and core tests. The new scenario loads an old SP attachment, summons its real entity, switches progression without losing either legacy record, rejects an unknown grant without mutation, and checks nonmanifesting/creative time-action rejection. Existing SP/TW world regressions also passed. The combined client flow is still pending; plan acceptance is not claimed yet.

Root also corrected the shared 180-tick packet/countdown defect under plan 26. Generic action IDs, training, part/effect ownership, client catalogs and remaining concrete view consumers are plan 28, rather than falsely claimed complete here. No assets were converted by this foundation plan. No commit or push was made.
