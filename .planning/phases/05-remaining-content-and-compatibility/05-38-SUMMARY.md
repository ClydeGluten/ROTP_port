---
phase: 05-remaining-content-and-compatibility
plan: 38
subsystem: silver-chariot-presentation
status: complete
tags: [fabric, silver-chariot, rendering, source-parity, multiplayer]
requires: [05-28, 05-37, 05-43, 05-59, 05-74]
provides:
  - Original Chariot body/armor geometry and all active legacy action pose families
  - Rapier/flame/afterimage renderers and remote-control first-person submission
  - Source partial-arm summon lifecycle, synced pose state and config-aware damage boundaries
affects: [05-55, 05-59, 05-67, 05-71]
tech-stack:
  added: []
  patterns: [generated original model geometry, immutable render snapshots, entity-owned action lifecycle]
key-files:
  created:
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/stand/SilverChariotRenderer.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/stand/SilverChariotLayers.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/stand/SilverChariotAnimation.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/model/SilverChariotModel.java
    - fabric/migration/silver-chariot-resources.json
    - fabric/migration/SILVER_CHARIOT_PRESENTATION.md
  modified:
    - fabric/tools/generate_remaining_stand_models.py
    - fabric/migration/remaining-model-manifest.json
    - fabric/migration/remaining-resource-schema.json
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/stand/silverchariot/SilverChariotEntity.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/stand/silverchariot/SilverChariotActions.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/stand/silverchariot/SilverChariotRapierEntity.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/stand/silverchariot/SilverChariotFlameSwingEntity.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/SilverChariotGameTests.java
decisions:
  - Preserve original blade-only removal, separate armor and layer-specific barrage suppression
  - Preserve active ControllerStand first-person route instead of deprecated separate-arm route
  - Keep natural pool unchanged until root delivery and actual client evidence
completed: 2026-10-18
actuals:
  tokens: 20742
  tasks: 2
  commits: 0
  measurement: chars/4 over new feature files and worker integration diff before summary; no harness token count
commit_policy: Explicit root stage-only workflow; commits, STATE and ROADMAP reserved to root
---

# Phase 05 Plan 38: Original Silver Chariot presentation

Original Chariot and independent armor geometry now drive source rapier/unarmed poses, one-handed barrage arms, flame, delayed-entity afterimages and controlled first-person rendering.

## Delivered work

1. Dedicated modern render-state renderer, independent armor mesh/layer, original body and armor textures, optional resource-pack glow, thrown blade and animated atlas flame geometry, original one-tick particle trail, and shared real afterimage renderer. Strict generator now handles the literal armor constructor and retains fail-fast rejection of unsupported source syntax.
2. Original summon/idle/armed/unarmed/light/dash/sweep/launch/guard/barrage pose data, interpolation, secondary pitch and lifecycle-driven state. Root-authorized main-side extension closes inherited provisional summon/arms-only behavior and exposes exact state for all tracking clients. Existing grouped37 tests cover source summon-lock timing and partial→full transition penalties.

Detailed original/Fabric method pairs, audited assets, lifecycle choices and integration limits are recorded in `fabric/migration/SILVER_CHARIOT_PRESENTATION.md`; exact texture/icon/particle mappings and digests are in `fabric/migration/silver-chariot-resources.json`.

## Root integration contract

- Call `SilverChariotRenderer.initialize()` once from client bootstrap after common registrations.
- Call `SilverChariotRenderer.submitFirstPerson(PoseStack, SubmitNodeCollector, CameraRenderState, float)` from the shared hand-submission interception alongside HG; true means the controlled first-person Chariot handled rendering.
- Existing74 tasks automatically generate `SilverChariotArmorGeometry` and original `jojo:flame` particle JSON. No Gradle task/source-set additions required.
- 37's common factory/entity/attributes/actions/shared afterimage/fire bootstrap remains required; source projectile owner and lifecycle validation remain server authoritative.
- New `summonMode`, `summonVariant`, `summonAnimation`, `visualAge`, `leftPunch`, `visualAttackSpeed`, `visualPrecision`, `renderAlpha` state drives renderer. Client reads state only.
- The natural acquisition pool stays unchanged. Root71 enables complete Stand delivery only after integrated gameplay/presentation acceptance.

## Source decisions and deviations

**[Rule2: missing critical inherited behavior]** Source builders specify MAIN_ARM for light/unarmed, ARMS for barrage/block and FULL otherwise. Root authorized updating37 main classes during38. Transactional provisional summons, original7/15/2 mode bits, arm addition/full upgrade, −25% damage/durability and zero precision, shortened light recovery, summon lock/weakness, automatic partial-arm unsummon and synced side/age were added. Existing full summons stay full. No invented new acquisition path.

**[Rule2: active presentation dependencies]** Root approved shared `StandAfterimageEntity` in37/38 ownership. The class was copied with37 because armor removal creates real source afterimages, including mob distraction.38 supplies its actual origin renderer. Root also approved extending74 generator/manifest for independent armor and the original flame particle.

**[Rule1: source subtlety]** Launch hides only the blade; handle/guard remain. Armor's original `isLayerModel` suppresses duplicate barrage armor while body/glow/flame preserve active source behavior. Original Java integer division in armed light easing and negative idle interpolation are retained rather than silently corrected.

**[Rule2: config integration]** Root59 settings are applied at melee/rapier/flame damage boundaries once, with separate stamina-free and creative-only rules. SC's resistance override calls the zero-stat formula; it does not bypass its internal config multiplier. Blockable Stand-body damage receives the zero-stat/config reduction even outside guard; ordinary direct-owner damage receives no new passive reduction. Linked transfer cannot double-scale.

**[Rule3: pinned API]** END_LEVEL_TICK replaces unavailable END_WORLD_TICK. Client implementation uses modern render-state extraction/submission, atlas manager, particle and model APIs inspected from the installed26.3 jar and established repository examples. Original Forge source remains unchanged.

## Checks and evidence

- Root reports draft18 packaged: main/client compilation, SC37/38 server cases and integrated/dedicated actual-input client acceptance passed. Root inspected original models, rapier, armor, barrage and control screenshots. Earlier developed-stat fixture errors and projectile origins were corrected before the accepted run.

- PASS: model converter `--check`:6 source models,13 validated outputs.
- PASS: resource converter `--check`:35 validated outputs, including original fire resources from37 and appended flame particle.
- PASS: scoped whitespace/source hygiene and no production TODO/FIXME/placeholder markers.
- NOT RUN BY WORKER: Gradle and runtime checks. Root exclusively ran the shared lane; the pass above is coordinator-reported evidence.
- No new lightweight client/unit suite was added. Existing grouped world tests were extended only for meaningful inherited lifecycle assertions.
- No commits or STATE/ROADMAP modifications: explicit shared-worktree ownership instruction. All assigned source/docs staged individually; other workers' edits preserved.

## Known verification limits

Root's draft18 acceptance includes inspected models, rapier/armor/barrage/control scenes and both client world modes. Generic55 visibility/skin/audio expansion remains its separate assigned work; this delivery does not claim it complete. Root unified the source shared afterimage ID with existing HamonAfterimageEntity; StandAfterimageEntity is an allocation facade and the shared renderer delegates to the real original renderer. No duplicate registration remains.

## Self-Check: PASSED

All six new feature/client/manifest/ledger files and this summary exist. Assigned modern API changes, original geometry inputs and resource targets were checked. Source-only checks passed; no unrun runtime check is represented as passed.
