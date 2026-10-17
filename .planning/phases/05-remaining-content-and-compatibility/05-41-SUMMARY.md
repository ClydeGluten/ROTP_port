---
phase: 05-remaining-content-and-compatibility
plan: 41
subsystem: magicians-red
status: completed
completed: true
requires: [05-28, 05-29, 05-37, 05-43, 05-55, 05-59]
provides: [Magicians Red manifestation, ten original actions, six projectile types, source fire/bind/detector combat]
affects: [05-42, 05-45, 05-55, 05-58, 05-72]
tags: [source-parity, fabric, stand, fire, projectiles]
actuals:
  tokens: 25200
  tasks: 3
  commits: 0
  measurement: assigned new-file characters divided by4; staged only per explicit shared-worktree workflow
decisions:
  - Reuse37 custom fire and43 history; Mode.NONE explosions never record bulk candidate positions.
  - Detector uses independent alongside-held intent; entity task owns original costs and timing.
  - Keep source noSave/noSummon and inactive fireball extinguishing/fire-consumption branches.
---

# Phase05 Plan41: Magician's Red gameplay

Source MR combat includes sustained flame, distinct fireball and both ankh forms, retained bind/finisher kick, independent detector and inherited attack/block/control lifecycle.

## Delivered and source mapping

Ten main classes plus two grouped world scenarios are in the feature source sets. Existing shared MagiciansRedFireBlock from37 is unchanged. `fabric/migration/MAGICIANS_RED_PARITY.md` records exact original/Fabric method pairs, original constants, active inherited methods, mutation ownership and dormant branches.

Read ModStandsInit, MagiciansRedEntity, all MR action classes, StandEntityAction/Light/Heavy/Block/task/punch inheritance, DamagingEntity, ModdedProjectileEntity, OwnerBoundProjectileEntity, MRFlame/Fireball/Crossfire/RedBind/Detector, CustomExplosion/Crossfire explosion, DamageUtil fire/suffocation, source sound aliases and active callers. Implementation uses actual source formulas and geometry dependency, not registration placeholders.

## Root handoff

- Call `MagiciansRedActions.initialize()`. Register `STAND` attributes using `MagiciansRedEntity.createAttributes()` and StandService factory for `jojo:magicians_red`.
- Entity registrations: `STAND`, `FLAME`, `FIREBALL_ENTITY`, `CROSSFIRE_ENTITY`, `CROSSFIRE_SPECIAL_ENTITY`, `BIND_ENTITY`, `DETECTOR_ENTITY`. Six projectiles preserve source noSave/noSummon and tracking intervals.
- Call `MagiciansRedActions.removeFireUnderPlayer(ServerPlayer)` from source player START tick. Preserve existing37 fire registration/private-age adapter.
- Register MagiciansRedGameTests. Two grouped cases cover real combat, block permissions/history, training, bind/effect lifecycle and independent detector hold.
- Shared dependencies:28 provisional summons/held replacement/alongside-held;43 actual-drop/no-drop capture;45 StandHeavyImpact start/punchBlock;55 StandDamage and StandPerception;59 StandConfig. Runtime entity owns per-tick/perform costs; registry owns client intent eligibility, parts/training and lease validation.
-42 supplies renderer, model, effects and exact visual state. Natural acquisition remains disabled until root72 acceptance.

## Deviations

Approved feature-local MRProjectileEntity ports common inherited projectile behavior instead of duplicating it six times. Approved shared heavy impact avoids duplicating Crazy Diamond's inherited heavy foundation. Shared37 fire dependency is reused, with no duplicate ID. Detector required the source alongside-held registry/client hook; ordinary replacement would incorrectly cancel sustained attacks. Bind speed/distance and starting positions use explicit modern tracked fields for late observers.

Source dictates omitted dormant behavior: fire-consumption charging and lava conversion stay absent; commented fireball water/rain extinguishing stays inactive. Solid ice replacement remains distinct from source no-drop removal history. Original noSave types do not gain invented restart/rejoin persistence.

## Verification and limitations

Worker ran source/resource checks only: resource converter accepts37 outputs; model converter accepts6 models/13 outputs;13 actual sound keys exist. Grouped tests and runtime remain unrun by worker. Root exclusively owns compilation, server/client lane, shared registrations and packaging. No commit/push, STATE or ROADMAP changes. Character-voice configuration is58 shared work; audience filtering already uses55.

## Self-Check: PASSED (handoff files and source hygiene)

Assigned production/test/ledger files exist; no TODO/FIXME/placeholder was left in feature Java. Source remains read-only. Runtime success is deliberately not asserted until root's consolidated lane.
