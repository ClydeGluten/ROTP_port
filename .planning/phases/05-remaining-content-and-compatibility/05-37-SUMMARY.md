---
phase: 05-remaining-content-and-compatibility
plan: 37
subsystem: silver-chariot
status: complete
requires: [05-28, 05-29, 05-43, 05-74, 05-76]
provides: [Silver Chariot manifestation, eight authoritative actions, recoverable rapier, fiery sweep, shared Magicians Red fire]
affects: [05-38, 05-41, 05-43, 05-71]
tags: [source-parity, stand, silver-chariot, projectiles]
actuals:
  tokens: 21638
  tasks: 2
  commits: 0
  measurement: owned new-file characters divided by4 plus11-entry resource fragment; excludes38 afterimage dependency
key-decisions:
  - Preserve exact source noSave/noSummon projectile lifecycle and original Stand UUID ownership.
  - Keep dormant ricochet damage formula dormant; active damage is1.5 times base attack.
  - Entity owns perform costs and phases; registry owns intent, eligibility, parts, unlock and held replacement.
  - Active fiery-sweep dependency includes MagiciansRedFireBlock now and uses43 terrain capture directly.
---

# Phase05 Plan37: Silver Chariot gameplay

Independent Silver Chariot manifestation implements source rapier and armor stat changes, all eight action registrations, deflection/fire, and recoverable100-ricochet projectiles.

## Source mapping

| Original methods/classes inspected | Fabric implementation |
|---|---|
| ModStandsInit SC registrations and StandEntityType | SilverChariotActions registers source IDs, parts, rows, shifts, unlock levels and entity types |
| SilverChariotEntity.setRapier/setArmor/getAttackDamage/getPhysicalResistance/attackTarget/deflectProjectile/tick | SilverChariotEntity stat modifiers, source stamina scaling, zero passive physical resistance, guard, projectile cone,300-tick burning and exact-owner recovery |
| StandEntityLightAttack and inherited StandEntityPunch | Light/unarmed damage/timing, two finisher additions, guard counter and additional knockback |
| SilverChariotMeleeBarrage + StandEntityMeleeBarrage | Held barrage, stochastic residual hit schedule, source skeleton reduction, finisher per hit,4/tick consumption, armor/Resolve duration override, Resolve target gravity restoration |
| SilverChariotDashAttack + StandEntityHeavyAttack | Finisher snapshot/subtraction, initial inherited punch, forward first half, multiple targets, source speed24 knockback branch, recovery |
| SilverChariotSweepingAttack |3-tick cone sweep, outer half damage, extra knockback and active fiery projectile |
| SilverChariotTakeOffArmor and LivingUtilCap.addAfterimages | Armor transition and synchronized elapsed timer;38 owns source10-afterimage realization |
| StandEntity.actuallyHurt + StandLinkDamageSource | Stand armor/magic calculation, five-tick idle auto-guard, owner transfer without applying player armor/magic twice |
| SCRapierEntity, ModdedProjectileEntity, DamagingEntity | Indefinite active-world rapier,1.5*baseAttack,100 bounded reflections, settled face, immunity-independent damage, exact living Stand owner recovery, loaded-owner removal |
| SCFlameSwingEntity + MRFlameEntity + MagiciansRedFireBlock |20-tick flame, damage/knockback/fire, water/rain extinguishing, snow/ice/TNT/fire world effects, source fire age identity |
| MCUtil.blockCatchFire/destroyBlock and CrazyDiamondRestoreTerrain.rememberBrokenBlock |43 RestorationHooks.remember captures original no-drop snow/ice/TNT/destruction paths |

Read inherited active methods and callers, including StandEntityTask's perform-before-cost order. The source `getDamageFinalCalc` ricochet bonus has no caller and is intentionally not activated. Original noSave/noSummon means active-world indefinite lifespan is not persistence across restart; helper NBT retains original UUID/ricochets but is not a chunk-save promise. No player-owner fallback or fresh-summon weapon-loss state is invented.

## Root integration

- Register attributes with `SilverChariotActions.STAND` / `SilverChariotEntity.createAttributes()` and factory `jojo:silver_chariot` through StandService. Call `SilverChariotActions.initialize()`, `MagiciansRedFireBlock.initialize()` and approved38 shared `StandAfterimageEntity.initialize()` once. The action initializer registers only its Resolve gravity cleanup ticks; common player input/lifecycle remains shared.
- Root private FireBlock age adapter calls `MagiciansRedFireBlock.stateWithAge(LevelReader,BlockPos,int)` for this subclass. `getStateForPlacement(BlockGetter,BlockPos)` is public. The block copies default Fabric flammability entries once at bootstrap.
- Requires shared Handler.ownsTaskLifecycle and canReplaceHeld. SC approves only DASH/SWEEP replacing active BARRAGE. Client38 mirrors registered replacement and held replacement.
- Register `SilverChariotGameTests`. Two grouped cases cover launch, ricochet, real owner recovery/removal, noSave registration, armor and rapier penalties, light/barrage state, fiery deflection/sweep, melt and extinguishing.
-11 original fire blockstate/model entries extend74 allowlist; existing textures remain original copied assets.38 supplies geometry, armor/glow/flame/projectile renderers and action poses.
- Public visual state: `hasRapier`, `hasArmor`, `isRapierOnFire`, `getTicksAfterArmorRemoval`, `attackKind`, action phase/ticks/duration, ownerId. Phases0idle/1windup/2perform/3recovery/4barrage/5guard/6guard-broken. Rapier exposes ricochetCount/leftOwner and exact living getOwner; flame exposes double-precision synchronized getStartingPos.

## Deviations and verification

Source overrides plan shorthand: noSave/noSummon, exact owner UUID and dormant bonus corrected. Added approved standalone SC flame and shared MR fire classes instead of omitting an active dependency. Terrain capture uses43 shared API, creating an explicit batch dependency. Presentation and enablement remain38/root-owned.

No worker Gradle, commit or push. Root reports draft18 packaged after SC37/38 server tests and integrated/dedicated actual-input client acceptance passed. Root inspected original models, rapier, armor, barrage and control screenshots. Initial developed-stat fixture errors and actual-Stand projectile origins were corrected before that accepted run. Natural acquisition remains governed by root delivery policy.

## Self-check: PASSED (files and source hygiene only)

All six main Java files, the grouped GameTest, resource manifest, parity ledger and summary exist. Scoped staged `git diff --check` passed. Converter `--check` passed34 outputs including11 original fire additions. Stub scan found no TODO/FIXME/placeholder in37 Java. No intentional gameplay stub or substituted SP model. Root owns integrated verification and final completion status. Changes are staged, uncommitted by explicit coordinator/user workflow; zero commits is not a claim of completed delivery.
