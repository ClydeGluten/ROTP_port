---
phase: 05-remaining-content-and-compatibility
plan: 42
subsystem: magicians-red-presentation
status: awaiting-integration-verification
requires: [05-41, 05-55, 05-74]
provides: [original MR body and poses, animated body fire, six dedicated projectile renderers, detector and bind effects]
affects: [05-58, 05-72]
tags: [source-parity, fabric, rendering, animation, multiplayer]
actuals:
  tokens: 13366
  tasks: 2
  commits: 0
  measurement: assigned new-file characters divided by4; staged only per explicit shared-worktree workflow
decisions:
  - Use actual30-part42-cube generated MR geometry and source procedural ankh/bind/detector shapes.
  - Explicit modern atlas entries preserve original animated body-fire sprites.
  - Modern fireball item definition uses original model without a new gameplay item.
---

# Phase05 Plan42: Magician's Red presentation

Dedicated MR renderer preserves original body/beak hierarchy, flame limbs, authored combat poses, seven-piece ankhs, fireball icon, bound kick ribbons and six-axis detector feedback.

## Delivered and source mapping

Five client classes: MagiciansRedRenderer, MagiciansRedModel, MagiciansRedAnimation, MagiciansRedProjectileRenderer and MagiciansRedEffects. `MAGICIANS_RED_PRESENTATION.md` enumerates every active pose family, layer, shape, source method and sound route. `magicians-red-resources.json` pins25 original assets and exact geometry metadata.

Read original MagiciansRedModel/renderer/flame layer, HumanoidStandModel and pose transition classes, FlameModelRenderer, Crossfire normal/special model and renderer, MRRedBindRenderer/RepeatingModel, MRDetector model/renderer/sound, SpriteRenderer/CustomIconItem, controller Stand-hand rendering and all action sound calls/ModSounds aliases. Exact30-part42-cube128×128 body geometry comes from74. Original optional glow is supported without inventing an absent texture.

## Root handoff

- Call `MagiciansRedRenderer.initialize()` in client bootstrap. It registers body and all six projectile renderers, source action replacements/held/alongside predicates and sound ticking.
- Route `MagiciansRedRenderer.submitFirstPerson(PoseStack,SubmitNodeCollector,CameraRenderState,float)` through existing controlled Stand-hand interception.
- Existing74 tasks generate MagiciansRedGeometry. The only converter extension is original item_icon_mr_fireball model plus modern item definition. No new Gradle task/source set is required.
- New `assets/minecraft/atlases/blocks.json` adds original animated MR body fire sprites; original source copy still supplies textures/mcmeta/sounds/language. Existing37 fire models reference original block-fire sprites.
- Shared catalog/layout/input uses41 registered IDs and original icon paths.55 supplies viewer/audio audience;58 owns character-voice settings. Natural-pool enablement remains root72 acceptance.

## Verification and deviations

PASS source checks: model converter6 models/13 outputs; resource converter37 outputs with new fireball;25 original resource digests and13 JoJo sound keys checked. Source aliases resolved: fire abilities share magicians_red_fire_ability, fireball uses vanilla FIRECHARGE_USE, guard uses stand_damage_block. No fake sound registration.

Approved ownership adds modern blocks-atlas resource and74 manifest entry. Modern render snapshots/submission, sprite atlas access, item model resolver and tracked entity fields adapt installed26.3 APIs; original shapes, offsets, textures and interpolation remain. Source first-person active route is used; deprecated separate-arm route stays inactive.

No worker Gradle or runtime screenshot comparison. Root must run actual-input integrated/dedicated scenes with a remote observer for held flame, normal/special Crossfire, fireball, bind/kick, detector, partial summons and control. No standalone helper-only test suite was added. No commits or shared STATE/ROADMAP modifications.

## Self-Check: PASSED (handoff files and source hygiene)

All five client files, asset fragment, atlas addition, converter entry and presentation ledger exist. Source-only validation passed; final runtime status remains pending root lane.
