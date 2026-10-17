---
phase: 05-remaining-content-and-compatibility
plan: 35
status: awaiting_integration
subsystem: pillar-world
tags: [pillarman, temple, fluid, multiblock, engraving]
requires: [05-23, 05-74 conversion pipeline, original Forge source]
provides: [sleeping sculpture and persistence, temple worldgen and original templates, saved engraving entity and renderer, boiling blood fluid/block/bucket, three grouped world scenarios]
affects: [05-32, 05-36, 05-66]
tech-stack:
  added: []
  patterns: [original resource manifest, modern fluid rendering registry, original custom-entity NBT compatibility]
key-files:
  created: [PillarWorldContent.java, SlumberingPillarBlock.java, SlumberingPillarBlockEntity.java, PillarTempleStructure.java, BoilingBloodFluid.java, BoilingBloodBlock.java, PillarWorldClient.java, PillarWorldGameTests.java, PILLAR_WORLD_PARITY.md, pillar temple worldgen and biome/structure tag JSON, original Forge bucket drip mask]
  modified: [fabric/tools/convert_remaining_resources.py, fabric/migration/remaining-resource-schema.json]
key-decisions:
  - Preserve source command-only player Pillar acquisition; sculpture only damages, stuns and counts absorbed life.
  - Keep original four template byte streams and explicitly read legacy custom engraving save fields.
  - Use actual LavaFluid/LiquidBlock inheritance and source lava tags rather than approximating heat with unrelated blocks.
  - Convert source Forge bucket mask into modern emissive item geometry; preserve original fluid sprite and silhouette.
actuals:
  tokens: 17789
  tasks: 2
  commits: 0
  files: 17
plan_head_before: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
plan_head_after: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
duration: not-measured
completed: 2026-10-18
---

# Phase 05 Plan 35: Pillar world implementation handoff

Implemented the original six-part sleeping sculpture and persistent absorption counter, four-piece jungle temple, saved engraving entity/presentation, and actual boiling blood fluid/block/bucket. Required templates, models, particles, tags, advancement and worldgen data are supplied now, not deferred to the data audit. This is a staged implementation handoff, **not completed functional acceptance**.

## Tasks and source evidence

The sculpture preserves part numbering, facing-dependent shapes, six-space placement, all-part break behavior, 4 absorption damage, ordinary hit immunity, 10-tick stun, Hamon protection and Peaceful suppression. Only part 4 stores `AbsorbedLife`. Source has no boss-wake tick or player-grant behavior; none was invented.

Temple generation preserves original terrain samples, offsets and common random rotation. Jungle eligibility, spacing 64, separation 20, salt 64023956 and default-enabled `pillarManTempleSpawn` are supplied through modern data/module configuration. All four original templates remain read-only; the corridor actually contains 48 saved engraving entities, and the bossroom contains the sculpture plus existing Stone Masks. Engraving fields `Facing`, `TexId`, `TileX/Y/Z` have explicit legacy compatibility; modern tracking persists direction/texture.

Boiling blood uses actual source/flowing fluid types and LiquidBlock, retaining inherited lava rules and original lava tag membership. Its sprites, particle, ambient/pop/fill/empty sounds, light 15, fluid levels and source-only pickup are wired. The Forge drip bucket is converted from its exact original mask into a modern composite model with emissive fluid faces; source geometry depths and UV mapping are retained.

Detailed original/Fabric method pairs, active callers, inspected resources and unresolved acceptance live in [PILLAR_WORLD_PARITY.md](../../../fabric/migration/PILLAR_WORLD_PARITY.md). That ledger also records the upstream Forge mask/model sources and license attribution.

## Root integration

- Common: `PillarWorldContent.initialize()`; client: `PillarWorldClient.initialize()`.
- Register test entrypoint `com.github.standobyte.jojo.fabric.PillarWorldGameTests` in shared metadata.
- HEAT API: `PillarWorldContent.BOILING_BLOOD`, `FLOWING_BOILING_BLOOD`, `BOILING_BLOOD_BLOCK`, `BOILING_BLOOD_BUCKET`, `BOILING_BLOOD_POP`. Original self-detonation liquid level is 7; vein liquid level is 4.
- Existing 05-74 generated resource roots cover the expanded manifest. Add `fabric/src/main/resources/assets/jojo/textures/item/bucket_fluid_drip_mask.png` as a converter task input. No new dependency is installed.
- Module config is `config/jojo/pillar_world.json` with boolean `pillarManTempleSpawn`, default true. Root retains shared configuration/bootstrap ownership; this file affects server generation only.

## Validation status

Passed: `python3 fabric/tools/convert_remaining_resources.py --check` validates 23 outputs including prior Blade Hat resources. Both existing 05-74 model/fixture checks and generated output checks passed before the final source-backed north-face bucket UV correction; final source conversion check passed afterwards. Root generation will refresh that derived file. Source models, templates and original `src/` are unchanged. Scoped whitespace/source hygiene checks pass.

Three grouped GameTests are authored: sculpture facing/break/contact/Hamon/Peaceful plus real block-entity save/load; actual corridor template loading with 48 engravings and four-piece layout; bucket collection, flowing rejection, scheduled falling fluid and submerged damage above a nonfull slab. The corridor uses its actual original structure as its GameTest template. No additional unit-test scaffolding was introduced.

No Gradle, javac, game launch, commit or push was run by this worker. Root must compile and execute the registered scenarios, test actual chunk unload/reload, and complete integrated/dedicated visual acceptance. The save/load scenario invokes real block-entity serialization but is not falsely labelled a chunk-unload test. Visual acceptance still covers sculpture models, engraving lighting/transparency/tracking, fluid animation and emissive bucket shape. Root owns shared cross-phase verification/WINDOWS tracking.

## Deviations

- Root explicitly delegated necessary new resources and extension of the 05-74 allowlist. Added four worldgen/tag JSON files and one pinned original Forge mask; extended the converter with a strict Forge boiling-blood bucket adapter and support for the source particle-only fluid block model. Existing manifest entries and fail-fast checks are preserved.
- Engraving entity and renderer are nested in the owned WorldContent/WorldClient files, avoiding edits to another worker's production files.
- Fixed source out-of-bounds engraving texture handling and bounded invalid saved absorption counts. Adapted the source placement ceiling to modern world heights.
- Build, shared registration, state/roadmap updates and runtime RED/GREEN execution remain root-owned. Formal TDD execution is not claimed. Zero commits is intentional staged delivery.
- Empty source behaviors remain intentionally empty: template data-marker callback, engraving item drop/placement sound, and no sleeping-controller tick. These are source behavior, not missing runtime implementations.

## Self-Check: PASSED (worker scope)

All assigned production/client/test/ledger files and required new resources exist. Converter validates references and rejects duplicate/missing resources. Original source diff is empty. Scoped staged whitespace verification passes. Functional acceptance remains explicitly pending.
