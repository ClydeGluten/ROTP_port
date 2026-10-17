---
phase: 05-remaining-content-and-compatibility
plan: 22
status: awaiting_integration
subsystem: zombie-player
tags: [zombie, non-stand, blood, disguise, client]
requires: [05-REST-CONTEXT, original Forge source, existing vampire and Hamon infrastructure]
provides: [zombie state and server actions, tracking presentation, client API, four server-world scenarios]
affects: [05-26, species integration, action routing, Stand stamina]
tech-stack:
  added: []
  patterns: [persistent private resource attachment, public visual attachment, intent-only packets]
key-files:
  created: [ZombieProgress.java, ZombieAttachments.java, ZombieService.java, ZombieActionPayload.java, ZombieGameTests.java, ZombieClient.java, ZombieHud.java, ZombieLayer.java, ZOMBIE_PARITY.md]
  modified: [ActionCatalog.java, ActionHud.java, ActionLayouts.java, HamonClient.java, ProgressionScreen.java, RipplesClient.java, StandSelection.java, VampireClient.java, FrozenHandMixin.java, StandInputMixin.java, StandSelectionInputMixin.java, VampireAvatarRendererMixin.java, VampirePlayerModelMixin.java]
key-decisions:
  - Zombie healing does not spend blood; inherited Devour still refunds healed cost.
  - Original zombie eye glow occurs while disguised, independent of saturation.
  - Original open-finger gloves satisfy free-main-hand conditions.
  - Shared event registration remains root-owned and must run exactly once.
actuals:
  tokens: 30138
  tasks: 2
  commits: 0
  files: 22
plan_head_before: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
plan_head_after: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
completed: 2026-10-18
---

# Phase 05 Plan 22: Zombie-player implementation handoff

Implemented the independent zombie-player blood economy, authoritative Claw/Devour/Disguise/leap, persistent disguise, death loss, private HUD/public tracking state, original skin/eyes/claw presentation, and executable world scenarios. This summary records an implementation handoff, **not completed functional acceptance**. Root owns the single build lane and 05-26 integration.

## Tasks and evidence

1. Server module implemented: immutable validated state, intent-only networking, server raycasts and action validation, original resource/passive formulas, source-permitted concurrent Devour/claw, untamed kill conversion and original stats, food and species/Stand hooks, disconnect/time-freeze/heartbeat handling.
2. Client module implemented: original action catalog API, held-control lifecycle, leap input, green disguise selection and alternate label/icon, blood bar, first-/third-person skin and eye rendering hooks, shared original clip with zombie eight-tick action timing.

Four GameTests are authored and registered for actual world/player behavior. Root's consolidated lane passed **all 216 required server GameTests and core tests**, with `BUILD SUCCESSFUL in 25s` in `/tmp/rotp-rest-server-verification.log`. The final Devour-sound tracking boolean was added after this server run; root's subsequent client build recompiles it. **No Gradle, test execution or client launch was run by this worker.** User explicitly reserved those for root. Client and dedicated-world acceptance remain pending in 05-26.

Original method anchors include `ZombiePowerType`, `ZombieData`, all three `Zombie*` actions, inherited `VampirismClawLacerate.punchPerform`, `VampirismBloodDrain.drainPerform`, `NonStandPowerType.updatePassiveEffects`, `GameplayEventHandler.onFoodEaten`, `VampirismUtil.consumeEnergyOnHeal/editMobAiGoals`, `DamageUtil.entityTakesUVDamage`, `ZombieLayer`, `VampireEyesLayer`, and `KosmXVampireClawSwipeHandler`. Full method/caller/API/asset mapping lives in the parity ledger.

## Decisions and deviations

- Followed original callers over broad plan shorthand: healing is free for zombies; eye glow occurs while disguised; default claw range is 8 entities/10 blocks while Devour is 2; gloves count as free hands; zombie Claw destroys one block.
- No commits, push, STATE or ROADMAP edits, per explicit user/root ownership instructions. Existing staged work is preserved. `commits: 0` is intentional staged delivery, not an invented commit result.
- TDD scenario was written before implementation, but RED/GREEN execution was intentionally deferred to the shared root build lane. No TDD pass is claimed.
- Root subsequently authorized this worker to wire existing client catalogs/layouts/input/render hooks. That follow-up is implemented and staged: J mode, H overview, editable rows, mapped mouse release, middle-button Devour, Shift+Space leap, blood/disguise HUD, looping Devour audio, and first-/third-person model/skin/eye hooks. Common bootstrap/commands/species integration remain root-owned.
- Actual token estimate is characters/4 over new-file diffs plus incremental existing-client diffs measured before final staging. It is not a harness usage count. No task commits were created, so HEAD remains unchanged.

## Remaining acceptance

Root owns final common lifecycle/acquisition/species/stamina acceptance and focused integrated/dedicated client flow. Server registration and the aggregate world-test gate have passed. Exact sound cancellation, first-person hand overlay, other-player skin, eye setting, invisibility/spectator suppression, and animation fade are not visually verified by this worker. The original turning-vampire ownership TODO remains source-inactive rather than implemented fiction.

No runtime stub is intentionally introduced. The empty attachment `initialize` body triggers the class's declared attachment registrations; shared server callbacks remain root-owned. Client `initialize` registers tracked Devour audio.

## Self-Check: PASSED (static scope)

All 22 owned production/test/ledger files exist. Scoped staged `git diff --check` passed. `git diff HEAD --name-only -- src` is empty: original source remains unchanged. No commit exists or is claimed; staged delivery was explicitly required. Parent-owned `TimeStopClient.java` changes were neither edited nor staged by this worker. Static self-check does not replace the pending client acceptance gate.
