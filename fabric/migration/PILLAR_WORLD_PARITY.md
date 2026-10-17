# Pillar world content: source mapping and acceptance ledger

Implementation is staged for root integration. No worker Gradle or client session was run. Source/data checks do not certify gameplay or visual acceptance.

| Original active source | Fabric implementation | Preserved behavior |
|---|---|---|
| `PillarmanBossMultiBlock.getStateForPlacement/setPlacedBy` | `SlumberingPillarBlock` same methods | Two columns, three rows; horizontal facing; clockwise right; bottom-left controller is part 4; all six spaces must be replaceable. Height bound adapts from old 256-world ceiling to the current world's build range. |
| `playerWillDestroy/posByPart/rightDirection` | `playerWillDestroy/posByPart` | Breaking any part removes the other matching part numbers and emits source block-break events. Bedrock hardness/resistance retained; no survival drops invented. |
| `getShape/rotate/mirror` | Corresponding overrides | Four half-block collision shapes; original facing rotation/mirror semantics. Original six block models retain sculpted details beyond collision thickness. |
| `entityInside` and `DamageUtil.dealPillarmanAbsorptionDamage` | `entityInside/absorbContact` | Server only; Peaceful suppressed; living targets take 4 armor-respecting absorption damage with normal hurt immunity. Success applies stun 10 ticks and increments the controller counter. |
| `HamonUtil.preventBlockDamage` | Existing `HamonCharges.protectFromContact` | Hamon energy cost is damage × 0.5, master immunity and charged-entity duration protection retained. Shared helper safely handles empty fluid collision shapes. |
| `PillarmanBossTileEntity.load/save/incAbsorbed` | `SlumberingPillarBlockEntity` | Original `AbsorbedLife` field persists; successful contact marks changed and notifies clients. No original tick behavior exists. Counter values are clamped against invalid negative saved data/overflow. |
| `PillarmanTempleStructure.Start.generatePieces` | `PillarTempleStructure.findGenerationPoint` | Chunk center +7/+7; 7×7 terrain samples from −26 through +22 in steps of 8; minimum WORLD_SURFACE_WG height minus 3. |
| `PillarmanTemplePieces.start/Piece` | `addPieces/Piece` | One random rotation shared by original building (−27,0,−27), stairway (19,−42,−2), corridor (61,−43,−3), bossroom (102,−45,−16). Offsets rotate with the piece. Rotation persists; source has no data-marker effects. |
| `ModStructures` / `pillarManTempleSpawn` | Worldgen JSON and module config | Jungle biome category maps to extensible `#minecraft:is_jungle`; surface structure; no terrain adaptation; spacing 64, separation 20, salt 64023956; generation toggle defaults true. |
| `PillarmanTempleEngravingEntity` | `PillarWorldContent.EngravingEntity` | 48×48-pixel planar artwork; always survives; no drops/placement sound; original `Facing/TexId` and old `TileX/Y/Z` readable; direction/texture synchronize and persist. Invalid texture indices fall back to 0 instead of the source's out-of-range crash. |
| `PillarmanTempleEngravingRenderer.renderPainting` | `PillarWorldClient.EngravingRenderer` | Eight original textures, nine 16-pixel quads, per-block light samples, original orientation/UV, −0.5 pixel Z, RGBA 191. |
| `BoilingBloodFluid` / inherited `LavaFluid` | `BoilingBloodFluid` / current `LavaFluid` | Source/flowing identity, source amount 8, flowing LEVEL, falling fluid, lava speed/spread/ignition/contact/replacement/water reactions and explosion resistance. Both fluids retain the source lava tag. |
| `ModBlocks.BOILING_BLOOD` | `BoilingBloodBlock` | Real LiquidBlock, no collision/drops, random ticks, strength 100 and light 15. Vanilla owns bucket pickup and source-only eligibility. |
| `BoilingBloodFluid.animateTick/getDripParticle` | Same overrides plus client particle provider | Air above required; 1/100 blood pop with source sound and random pitch/volume, 1/200 ambient sound; original vanilla dripping-lava particle preserved. Blood pop uses original texture and LavaParticle motion. |
| `FluidAttributes` and source bucket | World/client registrations and explicit bucket adapter | Original still/flow sprites, fill/empty sounds, fluid identity, luminosity 15. Forge density/viscosity/temperature are metadata without standalone modern equivalents; actual inherited lava behavior remains authoritative. |

The Forge bucket's original [drip-mask model](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.16.x/src/main/resources/assets/forge/models/item/bucket_drip.json) uses the vanilla bucket base. [DynamicBucketModel](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.16.x/src/main/java/net/minecraftforge/client/model/DynamicBucketModel.java) defines the liquid face depths and luminosity. The converter retains those depths and uses the original mask's 28 opaque pixels, with UV orientation checked against [ItemTextureQuadConverter](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.16.x/src/main/java/net/minecraftforge/client/model/ItemTextureQuadConverter.java). Modern composite item models combine the base and emissive fluid geometry; generated pixel quads retain the same silhouette without adding side walls. The imported Forge mask is LGPL-2.1-only, copyright Forge Development LLC and contributors, from `src/main/resources/assets/forge/textures/item/mask/bucket_fluid_drip.png` on the 1.16.x branch. Its SHA-256 is `521f9f66e4cf2991af902a3fc52242e6eeae4f79d603cd6454c3b352cf9e533c`.

## Resources actually inspected and mapped

- Original `pillarman_temple/{building,stairway,corridor,bossroom}.nbt`, DataVersion 2586, total 89,587 bytes. StructureTemplateManager performs vanilla data fixing at load; original bytes remain unchanged and copied to singular `data/jojo/structure`. The corridor contains 48 custom engraving entities. Bossroom contains the six sculpture states, controller NBT and existing Stone Masks. Legacy custom entity fields are explicitly handled rather than assumed to be vanilla-fixed.
- Original sculpture blockstate, six `pillarman_<row>_<column>` models, sculpture item model/texture; source `santana`/`santana_face` textures remain in existing original texture copying.
- Original boiling blood blockstate, particle-only block model, particle definition, animated still/flow texture PNG/mcmeta files, particle texture, bucket model and both original lava-tag entries.
- Original temple advancement maps only icon `item`→`id` and the modern location predicate schema; parent, criterion, requirements, titles and display remain source-preserving. Existing `buy_map` parent is required by reference validation.
- Four new worldgen/tag JSON files encode original Java registration settings. No recipe or loot-table path is added: this source content supplies none. Old jigsaw template pools are not loaded by the active direct four-piece structure and are left inactive.

## Public integration contract

Root calls `PillarWorldContent.initialize()` during common registration and `PillarWorldClient.initialize()` on the client. Common initialization also registers `PillarTempleStructure` and loads server-only `config/jojo/pillar_world.json` with `pillarManTempleSpawn`.

HEAT code uses `PillarWorldContent.BOILING_BLOOD`, `FLOWING_BOILING_BLOOD`, `BOILING_BLOOD_BLOCK`, `BOILING_BLOOD_BUCKET`, and `BOILING_BLOOD_POP`. Original self-detonation places the liquid block with `LiquidBlock.LEVEL=7`; veins use level 4. Source placement uses default level 0. Other public registrations are `SLUMBERING_PILLARMAN`, `SLUMBERING_ITEM`, `SLUMBERING_BLOCK_ENTITY`, `ENGRAVING`.

Root adds `PillarWorldGameTests` to the existing test entrypoint list and adds the pinned Forge mask file as an input to the 05-74 converter task. Existing generated-resource source sets already cover every manifest output. Root owns shared bootstrap, metadata, client verification and build execution.

## Acceptance status

- Passed: explicit manifest conversion and missing-reference/duplicate/format guards; 23 resource outputs including the retained Blade Hat tracer. 05-74 model checks still pass. Original source tree diff remains empty.
- Authored, unrun by worker: all four sculpture facings/all six break positions; source contact/stun/Hamon/Peaceful; real block-entity save/deserialize; actual corridor template with 48 saved entities and four-piece layout; engraving save/load; bucket/source/flowing eligibility; actual scheduled falling flow and submerged living damage over a nonfull slab.
- Still required: root compile and registered GameTests; full chunk unload/reload (the authored persistence scenario uses the real block-entity serialization path but does not unload a chunk); actual jungle generation/config toggle; integrated/dedicated tracking and render checks; engraving lighting/UV/transparency, animated fluid and emissive drip-bucket appearance. No visual pass is claimed.
- Source-inactive: sleeping-boss wake logic, sleeping-block player acquisition, any recipe/loot not present in the original. Player Pillar acquisition remains the source command boundary; no new grant route is introduced.
