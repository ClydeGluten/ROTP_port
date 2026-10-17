# Broken-block history and terrain restoration parity

Status: implementation staged for root integration; the three world cases have not run yet. This module does not make Crazy Diamond playable or extend the natural Stand pool.

Authoritative source: Forge commit `72862a5826ed45d1dc26e90b37853097acda35de`. Original `src/` remains read-only. Target remains Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Java 25.

## Active method mapping

| Original method / active caller | Fabric implementation | Status |
| --- | --- | --- |
| `ChunkCap.saveBrokenBlock`, `getBrokenBlockAt`, `getBrokenBlocks`, `wasBlockBroken`, `removeBrokenBlock`, `reset`, `setDroppedXp` | `BlockHistory` with one map per actual chunk and positive pending XP | Implemented; integrated tests pending |
| `PrevBlockInfo` construction, drops copy, XP, `withEntities`, `onRestore` | `BrokenBlockRecord`, weak `RestorableBlockEntity` references; first live callback decides restoration | Implemented; shard/glider callers belong to their feature plans |
| `ChunkCap.save/load`, `PrevBlockInfo.toNBT/fromNBT`, first loaded-NBT `tick` | `BlockHistorySavedData` chunk attachment codec and `onChunkLoad`, `BlockHistory.bind` | Implemented; full chunk serializer test pending |
| `CrazyDiamondRestoreTerrain.rememberBrokenBlock` | `RestorationHooks.remember` | Implemented direct pre-mutation capture |
| `LootTableMixin.jojoRememberBlockLoot` | Root's actual-loot hook calls `captureDrops` | Root integration required; no loot reroll in module |
| `GameplayEventHandler.onBlockBreak` | Root's accepted player break/XP hooks call `setDroppedXp`; creative no-loot capture uses `remember` | Root integration required |
| `FireBlockMixin` before replacement/removal | Root calls `remember` with old state, old block entity, empty drops | Root integration required |
| `MCUtil.destroyBlocksInBulk`, `destroyBlock`, `blockCatchFire`; `MRFlameEntity.meltIceAndSnow`; `MRCrossfireHurricaneEntity` explosion callback | Direct `remember` for source no-drop paths; actual-loot hook for generated drops; optional scoped custom-mutation API | API supplied to root and MR/fire worker |
| `CrazyDiamondRestoreTerrain.restoreBlocks`, `restorationExclude`, `restorationPriority`, `tryPlaceBlock`, `blockCanBePlaced`, `forgetBrokenBlocks` | `RestorationHooks.restoreBlocks`, placement helpers, `forget` | Implemented; source ordering/cost rules preserved |
| `consumeNeededItems`, `consumeSingleItem`, `sortItem`, `stacksMatch` | `consumeNeededItems` and modern item-component matching | Implemented; original duplicate-cost allocation and multi-stack collection omission preserved |
| `SourceType`, `ItemsSource`, `sourceItemStacks`; `CDTurnIntoAngeloRockEffect.itemsSource` | Matching enum, explicit ordered source overload, terrain convenience overload | Implemented; configurable other-player inventory inclusion remains caller-owned |
| `getBlocksInRange`, `eyePos`, `blockPosSelectedForRestoration` | Loaded-chunk query, rounded eye coordinates, Manhattan range 12, 24-block aim ray, 0.7071 / 0 facing thresholds | Implemented; no forced chunk loading |
| `StandEntityHeavyAttack` shard linking and `HamonLifeMagnetism` glider linking | `getBrokenBlockAt(...).withEntities(...)` | Interface supplied; owning feature plans wire entities |
| `BlockShardEntity.crazyDRestore` and `LeavesGliderEntity.crazyDRestore` | Boolean callback contract preserves immediate shard approval versus delayed glider approval | Contract implemented; entities remain in their feature plans |
| `CDTurnIntoAngeloRockEffect.restoreBrokenBlocks` and `AngeloRockEntity` re-recording | Ordered source overload, explicit record streams, optional XP player, direct remember | Shared support implemented; gameplay integration belongs to plan 44 |

Inheritance checked: `CrazyDiamondRestoreTerrain extends StandEntityAction extends StandAction`; `StandEntityTask` invokes the active perform tick and then the action ticking cost. Terrain overrides generic ticking cost to zero and charges per placed block itself. Plan 44 must retain the registered two-stamina-per-block cost, source `min(blocksPerTick, (int)(staminaPerBlock * stamina))` formula, Resolve limit 64, selected camera, and held action controls. These action-level formulas are not silently replaced by this lower-level history module.

## Preserved details

- Runtime capture always runs. The original `saveDestroyedBlocks` option defaults to false and gates serialization and deserialization only.
- There is no active history expiry or capacity limit. `Keep` and `TickCount` are stored unchanged except normalization of invalid negative counts. Pending XP is runtime-only, as in `ChunkCap`.
- Breaks replace the record at their position. Drops are copied from the actual generated list; inventory block entities, Stone Mask block entities, and the original `FireBlock` class are excluded. Ordinary noninventory block-entity NBT is not invented.
- Save/load stores `Blocks`, `Pos`, `State`, `Drops`, `Xp`, `Keep`, and `TickCount`. Unknown block entries are skipped, unknown item entries are skipped, malformed required fields are rejected, negative XP is normalized, and first load removes records outside their chunk/build height or under nonreplaceable blocks.
- Modern `BlockState.CODEC` can emit a string for default states. Persistence deliberately uses `BlockState.FULL_CODEC` to retain the source compound shape. Item codecs use the supplied registry context.
- Falling blocks wait when missing support can itself be restored. Supported falling blocks sort ahead of ordinary blocks when a comparator is supplied. The limit applies before restoration callbacks and payment attempts.
- XP affordability is checked even in creative. Available materials are consumed before the creative fallback. Creative skips only the XP debit and allows missing materials. Survival consumes materials and XP before neighbor updates/placement, with no invented refund or transaction rollback.
- Improper reconstruction immediately chooses the random offset, including the original ineffective history guard's result. Failure at that offset does not restore the original position. History forgets the original position on reported success.
- `sourceItemStacks` keeps live item references so consuming materials changes actual nearby drops, mob hands, and selected inventories. Explicit source order supports Angelo Rock's target-first behavior.
- Weak shard/glider callback references and pending XP are not persisted. Successful restores do not spawn new drops or XP.

## Root integration contract

Call `BlockHistorySavedData.initialize()` once before world loading. Bind the existing/common config to `setSaveDestroyedBlocks(boolean)` before any chunk read; default false is source-correct. No separate configuration file is invented by this worker. The attachment ID is `jojo:broken_blocks`. It is server-only; mutable per-chunk changes explicitly call `LevelChunk.markUnsaved()`. Unload naturally releases runtime data with the chunk.

Root owns global break, loot, XP and fire mixins, their registration, and any shared protection callbacks. Direct calls:

```java
RestorationHooks.remember(level, pos, oldState, oldBlockEntity, actualDrops);
RestorationHooks.captureDrops(level, pos, state, blockEntity, generatedLoot);
RestorationHooks.setDroppedXp(level, pos, positiveXp);
```

`captureDrops` applies `GameRules.BLOCK_DROPS`; it does not generate loot. Without a scope it records immediately, matching the original loot hook even if some later caller does not remove the block. `remember` always records immediately, matching original fire and melt callers. Root must not wrap all source direct routes in success-only logic.

For explicit success-owned custom mutations, use a scope:

```java
try (var capture = RestorationHooks.begin(level, pos, oldState, oldBlockEntity)) {
    // Actual loot hooks route to this capture automatically, or supply the existing list here.
    capture.captureDrops(actualDrops);
    capture.captureXp(actualXp);
    capture.commit(successfulMutation);
}
```

The scope copies before mutation, cancels without recording, commits at most once, and checks that the old state actually changed. It never rolls back a world mutation. Direct `remember` is intentionally independent of scopes. Plan 41/44/61 can call these adapters without depending on a client packet or renderer. A returned `RestoreResult.blocksPlaced` is the source-position set for plan 44's original restoration particles/packet and stamina cost.

## Source-inactive and future-owned presentation

`ChunkCap`'s `forget()` eviction loop and `onChunkLoad(ServerPlayerEntity)` resync body are commented out. They were not activated. Source `blocksToSync` sends and `CDBlocksRestoredPacket` are presentation concerns that plan 44/root must connect; this module does not claim client ghost-block rendering or particle verification.

Assets inspected: `particles/cd_restoration.json` (eight `spark_0` through `spark_7` frames), sound JSON entries `crazy_diamond_fix_started`, `crazy_diamond_fix_loop`, `crazy_diamond_fix_ended` (restoration start/loop/over sound files), and both 32×32 action texture metadata files `crazy_diamond_restore_terrain.png` / `_improper.png`. No assets were generated, replaced, or copied by plan 43. The exact textures/sounds/particles and visibility controls belong to plan 44's reachable action presentation.

## Verification and adaptation evidence

`BlockRestorationGameTests` supplies three grouped actual-world cases: actual block break/full state/drop/XP restoration and exclusions; cancelled capture, creative affordability/consumption and falling-block priority/conflicts; and actual `SerializableChunkData.copyOf/write/parse/read` attachment serialization, config off/on, old-save ignoring, missing registry entries, and first-load conflicts. The serializer test reconstructs a detached chunk in memory without unloading or writing the user's world.

No Gradle/build/test process ran in this worker. Root registers the test class and runs the shared current-source lane. Source hygiene passes; runtime and client status remain unverified until root records results. Modern signatures were checked against pinned local Minecraft/Fabric jars, including the attachment serializer mixin. [Fabric's attachment documentation](https://docs.fabricmc.net/develop/serialization/data-attachments) supplies the registration/persistence contract; explicit chunk dirty marking handles this mutable source-compatible map.

No new network endpoint or user-save rewrite is introduced. Server-level typed APIs, dimension checks, loaded chunk checks, and decode bounds enforce the planned trust boundaries. Original Forge save migration is not claimed by this new Fabric attachment format.
