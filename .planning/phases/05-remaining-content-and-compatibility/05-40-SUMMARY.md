---
phase: 05-remaining-content-and-compatibility
plan: 40
status: awaiting-integrated-verification
completed: false
subsystem: hierophant-presentation
tags: [fabric, original-model, original-poses, extending-strings, barrier-markers]
requires: [05-39]
provides: [hierophant-body-and-glow, original-legacy-poses, extending-string-renderers, rip-location-markers]
affects: [05-55, 05-57, 05-58, 05-71]
tech-stack:
  added: []
  patterns: [immutable-render-state, original-cuboid-conversion, camera-projected-marker]
key-files:
  created: [HierophantGreenModel.java, HierophantGreenRenderer.java, HierophantAnimation.java, HierophantProjectileRenderer.java, HierophantBarrierMarkers.java, hierophant-green-resources.json, HIEROPHANT_PRESENTATION.md]
  modified: []
key-decisions:
  - Original Java legacy poses remain authoritative because no HG animation JSON exists.
  - Preserve source body/string fullbright overlays; original emerald has no glow layer.
  - Read synchronized barrier rip positions to repair the source marker's unsynchronized-net access.
actuals:
  tokens: 12488
  tasks: 2
  commits: 0
---

# Phase05 Plan40: Hierophant Green geometry, strings and detection

Dedicated Hierophant rendering now uses its literal segmented body, original legacy poses, separate glow texture, repeating owner-bound string geometry, emerald mesh and synchronized rip markers. Client batch is prepared for shared compilation; runtime/visual verification remains pending.

## Implemented tasks

1. Original `HierophantGreenModel` geometry and active pose constructors, inherited `StandEntityModel`/`HumanoidStandModel`, `LegacyStandAnimator`, pose interpolation/phase fillers, `StandEntityRenderer`/glow and held-item layers → dedicated model/animation/renderer with23 bones and30 cuboids. Original `HGEmeraldModel` and extending/repeating string renderers → actual yaw/pitch emeralds and endpoint-driven strings with original lighting/glow/final-piece behavior.
2. `HGBarrierEntity.isGlowing/getTeamColor`, `HierophantGreenBarrierDetectionMarker` and inherited `MarkerRenderer` → owner red rip outline and through-block projected icon/frame at real synchronized rip locations. `ControllerStand.renderStandHands` and inherited first-person rotations → actual animated remote Stand arms. ActionCatalog gets the same held-action replacement rules as39 server authority.

Exact method pairs, preserved quirks and24 original asset hashes are in `HIEROPHANT_PRESENTATION.md` and `hierophant-green-resources.json`. No SP model, pose or projectile fallback is used. Original source/assets remain unchanged.

## Root contracts

- `HierophantGreenRenderer.initialize()` registers the body model/renderer and four dedicated projectile renderers plus HG client held-replacement rule. Call once.
- `HierophantBarrierMarkers.render(GuiGraphicsExtractor,float)` is a HUD hook independent of action menu visibility. `positions(Minecraft)` exposes actual owner-only synchronized points for71 assertions.
- `HierophantProjectileRenderer.outline(Entity)` joins the shared Minecraft glowing predicate; the renderer extracts red outline color.
- `HierophantGreenRenderer.submitFirstPerson(PoseStack,SubmitNodeCollector,CameraRenderState,float)` returns true when it handled remote HG hands; root then suppresses vanilla player hands.
- `setTextureResolver(BiFunction<Entity,Identifier,Identifier>)` and `setVisibilityPredicate(Predicate<Entity>)` accept58/55 shared source policy. Defaults use unchanged assets and entity visibility.57 shader integration remains shared.

## Verification and status

23 original bones/30 body cuboids mechanically converted; original emerald three cuboids and repeating one cuboid preserved.24 manifest paths/hashes checked. Pinned cached jars inspected via javap for current model, item-layer, submission, camera and GUI APIs. Root owns compilation and71 integrated/dedicated scenes; no worker Gradle/runtime claim. HG remains disabled for playability/natural allocation until the complete delivery is accepted.

## Deviations and source corrections

- Source HG has no animation JSON. Converted its active Java legacy definitions despite the stale model comment about a future Gecko migration.
- Only Guard auto-summons arms; source required body parts do not determine summon mode.39 corrected after inspecting actual builders and constructors.
- Emerald has no source glow layer. Body and string overlays are preserved instead.
- Modern HUD projection preserves original marker placement/sizing/through-block behavior using synced tracked-barrier rip state, fixing source's documented unsynchronized-net path.

## Self-Check

All assigned model/renderer/animation/marker files, manifest and source ledger exist. Cached `git diff --check` passed and all24 asset hashes matched original files. Compilation and world/visual acceptance remain explicitly pending with root. Assigned files only; no commit/push or shared planning-state edits.
