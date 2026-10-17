---
phase: 05-remaining-content-and-compatibility
plan: 74
status: awaiting_integration
subsystem: source-resource-conversion
tags: [assets, models, blockbench, generation, source-parity]
requires: [original Forge source, pinned local Minecraft 26.3 client jar]
provides: [explicit resource manifest conversion, five original model factories, normalized GE animation tracks, focused asset checks]
affects: [05-27, 05-38, 05-40, 05-42, 05-46, 05-50, future world resources]
tech-stack:
  added: []
  patterns: [Python standard-library generation, source allowlists, fail-closed adapters, dedicated generated output roots]
key-files:
  created: [fabric/tools/convert_remaining_resources.py, fabric/tools/generate_remaining_stand_models.py, fabric/tools/verify_remaining_assets.py, fabric/migration/remaining-resource-schema.json, fabric/migration/remaining-model-manifest.json, fabric/migration/fixtures/remaining-resource-cases.json, fabric/migration/fixtures/remaining-model-cases.json]
  modified: []
key-decisions:
  - Generate Blade Hat model and modern item definition only; existing sound/texture/language copying stays intact.
  - GE conversion includes actual per-face UV cubes and both meshes, not the empty Java shell.
  - Generate direct ModelPart factories to retain arbitrary mesh polygons without runtime mixins.
  - Future version-sensitive data/NBT needs explicit source-digest-pinned review; copying does not imply schema or runtime parity.
actuals:
  tokens: 16187
  tasks: 2
  commits: 0
  files: 8
plan_head_before: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
plan_head_after: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
duration: not-measured
completed: 2026-10-18
---

# Phase 05 Plan 74: Original resource and model conversion handoff

Implemented deterministic allowlist conversion for the original Blade Hat resources, four original Java Stand geometries and actual Gold Experience Blockbench geometry/animation data. This is an implementation handoff with passing Python source/output checks, **not completed game or visual acceptance**. Root owns build.gradle integration and the sole build lane.

## Implemented tasks and source coverage

1. `convert_remaining_resources.py` reads `remaining-resource-schema.json`, validates duplicate keys/IDs/output paths, missing source files, missing model/texture references, model overrides requiring explicit adaptation, and collisions with handwritten resources. It emits the exact original `assets/jojo/models/item/blade_hat.json` content plus the modern `assets/jojo/items/blade_hat.json` wrapper. Both use the original `jojo:item/blade_hat` texture; no texture, sound or language is copied by this tool.
2. `generate_remaining_stand_models.py` parses Java geometry constructor statements, including `convertLimb`, and the real GE BB source. It preserves named hierarchy, pivots, rotations, dimensions, deformation, UVs and mirror behavior. Unsupported constructor/BB/animation constructs fail explicitly. Generated factories create fresh `ModelPart` trees, including custom GE polygons. Normalized GE tracks retain clip names, timing, vectors, easing, query expressions and phase/barrage/loop-back events.

| Original source | Converted parts | Cubes | Meshes / mesh faces |
|---|---:|---:|---:|
| HierophantGreenModel constructor | 23 | 30 | 0 |
| SilverChariotModel constructor | 22 | 50 | 0 |
| MagiciansRedModel delegated constructor | 30 | 42 | 0 |
| CrazyDiamondModel constructor | 63 | 117 | 0 |
| gold_experience.bbmodel | 52 | 80 | 2 / 38 |

All five use their own original textures. Logical UV canvases are 128×128; original 256×256 texture variants remain correctly normalized. GE produces all 16 authored animation clips, including the source-labelled unused clip as retained data, without declaring that clip gameplay-active.

## Exact method and asset mapping

- Original `HumanoidStandModel.convertLimb` returns a new `XRotationModelRenderer`; Java parser retains this as `x_rotation_limb` metadata and generated `X_ROTATION_LIMBS`. Original `XRotationModelRenderer.translateAndRotate` applies secondary pose rotation. The later feature animator must implement that runtime behavior; factory generation does not claim animation playback.
- Original constructors' `setPos`, `setRotationAngle`, `addChild`, `texOffs/addBox` map to generated `ModelPart` hierarchy, initial `PartPose`, and `ModelPart.Cube`. Original named root is retained below an outer container, including HG/SC/MR root Y=24 and CD root Y=0.
- Original `ParseGenericModel.GroupParsed.makeModelPart` maps to BB group-relative X/Y reflection, root Y=24 and degree-to-radian bind rotations. `Element.convertRotated` maps to source-compatible `<parent>_rN` child bones.
- Original `ParseGenericModel.ElementCube.makeCube` maps to explicit six-face polygons and the source's opposite-X/Y face selection and Y-face UV ordering. Actual per-face UVs carry mirroring; no additional mirror operation is applied twice.
- Original `ParseGenericModel.ElementMesh.makeCube`, `MeshModelBox.MeshFaceBuilder.createFace` and `MeshVerticesHelper.sortVertices` map to reflected vertices, duplicate-ID removal, original quad sorting, triangle padding and computed normals. Both original GE meshes are retained.
- Original `ParseGeckoAnims.parseKeyframes/parseKeyframe` and `GeckoStandAnimator.animateSecs/animFromJson` map to normalized tracks/events. The source runtime coordinate contract is recorded: rotation XYZ degrees to radians; translation `(x,-y,z)`, additive to bind pose. Head query expressions remain explicit data for later runtime evaluation.
- Inspected assets: original Blade Hat and Tommy Gun item models; original Blade Hat texture reference; all five Stand texture PNG headers; GE BB/animation JSON; existing Fabric Clackers item/model resources. Tommy Gun's legacy predicate override is deliberately rejected until a dedicated modern dispatch mapping is reviewed.
- Target API/schema evidence came from the pinned local 26.3 game: `ModelPart`, `ModelPart.Cube/Polygon/Vertex`, `CubeDefinition`, vanilla Iron Sword item/recipe, Stone loot table and Crit particle definitions. Existing Star Platinum parser/generator/verifier provided repository conventions only; none of its geometry substitutes for another Stand.

## Resource adapter scope

Supported explicit kinds are item/block model, blockstate, particle definition, shaped/shapeless crafting recipe, tag, reviewed JSON and reviewed structure NBT. Crafting converts legacy item/tag ingredient objects to modern strings/tag strings and result `item` to `id`; tags and data use singular registry directories.

Loot tables, advancements, dimensions and worldgen JSON require `reviewed_json` with an exact original SHA-256, a review note, and explicit JSON-pointer replacements/removals with reasons. Structure copies require the same digest/review gate and singular `structure` directory. These contracts allow later plans to supply reviewed mappings while retaining original content. They **do not automatically upgrade arbitrary legacy schemas or NBT DataVersion**, and no unreviewed world/data resource is enabled by this manifest. Only Blade Hat is presently active in the resource allowlist. Existing copied assets remain root-owned.

## Root integration contract

- Resource generation command, from `fabric/`: `python3 tools/convert_remaining_resources.py`; output root `build/generated/remainingResources`.
- Model generation command: `python3 tools/generate_remaining_stand_models.py`; output root `build/generated/remainingStandModels` with `java/` and `resources/` children.
- Add both generated resource roots to `sourceSets.main.resources`; add `generated/remainingStandModels/java` to `sourceSets.client.java`.
- `processResources` depends on both generation tasks; `compileClientJava` depends on model generation. Task inputs include scripts, manifests, fixtures, original selected assets/textures, source model/base Java and GE animation/BB files. Root may use broad original-source input trees for correct invalidation. `verify_remaining_assets.py` is the focused verification entrypoint.
- Generated public API: `com.github.standobyte.jojo.fabric.client.model.generated.{HierophantGreen,SilverChariot,MagiciansRed,CrazyDiamond,GoldExperience}Geometry.createRoot()`. Returned outer container has child `root`; every original named part remains addressable. Constants expose logical texture dimensions and X-rotation limb names.
- Geometry metadata: `assets/jojo/converted_models/<stand>.json`. GE tracks: `assets/jojo/converted_animations/gold_experience.json`. Runtime registration/renderers/animation consumers remain feature-owned.
- Reserve generated Blade Hat model/item-definition paths from handwritten equipment assets. Converter rejects duplicates rather than choosing an arbitrary resource winner. Original textures are still included by the existing processResources copy.

## Verification and acceptance

Passed locally:

- `python3 fabric/tools/convert_remaining_resources.py --check`.
- `python3 fabric/tools/verify_remaining_assets.py --check`.
- Both generators wrote their dedicated build/generated outputs; `python3 fabric/tools/verify_remaining_assets.py --check-output` then passed exact-byte and stale-file-set comparison.
- Fixtures compare real counts, pivots, coordinates, UVs, parents, mirrors, GE mesh faces and animation values; reject empty GE Java geometry, unsupported statements/formats/expressions, missing files/textures, duplicate mappings, unsafe paths and unmapped Tommy predicates.
- Deterministic in-memory generation is checked by regenerating and comparing every output byte. Original `src/` diff is empty. Scoped whitespace check passes.

No Gradle, javac, game launch, commit or push was run by this worker. Root must compile generated Java, validate resource loading without duplicates, and exercise actual Blade Hat equipment through task 05-27. Runtime poses, model appearance, meshes/UVs and animation playback still require per-feature visual checks in 05-38/40/42/46/50. Parent owns cross-phase acceptance tracking and any WINDOWS entry; this worker did not edit shared planning state.

## Deviations and known limits

- User/root explicitly require staged delivery and root-owned build.gradle/STATE/ROADMAP. Zero commits is intentional. Build integration is handed off instead of edited here.
- GE contains two meshes beyond basic BB cubes. Added source-faithful mesh conversion, avoiding geometry omission. Direct ModelPart factories replace a LayerDefinition-only design because vanilla cube builders cannot express these per-face mesh UVs.
- Focused fixtures were added alongside implementation; formal RED/GREEN runtime TDD was not executed or claimed. No extra gameplay tests were authored.
- No placeholder runtime implementation exists in this tooling. Future reviewed data mappings and feature animation playback are explicitly outside the active manifest and are not reported as verified functionality.

## Self-Check: PASSED (worker scope)

Seven assigned tooling/manifest/fixture files plus this summary exist. Both planned Python checks and exact generated output checks pass. All work is staged without modifying original source or another worker's files. No functional acceptance is inferred from static generation.
