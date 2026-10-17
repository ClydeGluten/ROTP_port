# Crazy Diamond restoration parity

Status: source methods ported; integrated build, world tests and client presentation pending. Plans 45/46 supply the actual manifestation and presentation. The Stand remains outside the playable pool until the delivery lane verifies those dependencies.

| Original active method | Fabric implementation | Preserved behavior |
|---|---|---|
| `CrazyDiamondRepairItem.repairTick`, `canBeRepaired`, `itemTransformationTick` | `CrazyDiamondRestorationActions.repairTick`, `canBeRepaired`, `itemTransformationTick` | Offhand durability, repair-cost reset, all enchantments removed, rarity/tier training, anvil/cobble/cracked conversion and source creative shrink order |
| `CrazyDiamondRepairItem.dropExperience` | `CrazyDiamondRestorationActions.dropExperience` | Physical XP orbs, noncurse minimum-cost sum, random half-to-full return, source hand offset; curses removed without XP |
| `CrazyDiamondPreviousState.convertTo`, `getIngredients`, `revertBookPagesNBT` | `RecipeReversal` | Actual server recipes; smithing/cooking/stonecutting/crafting priority; random recipe and independent ingredient alternatives; repeated ingredients retained; output count consumed; plain written-book pages |
| `CrazyDiamondPreviousState.checkTarget`, `checkStandTarget`, `standTickPerform` | `entityReversible`, action handler and `reverseEntity` | Resolve 3 TNT/cart/snow-golem/charged-creeper, resolve 4 iron-golem/forming-Wither; healing precedes probabilistic reversals; current fuse +2 and source first-tick behavior |
| `CrazyDiamondPreviousState.canReplaceBlock`, `replaceOrDropBlock` | Same action helpers | Replaceable or strictly softer nonnegative-hardness blocks; destroy with drops then place, otherwise drop block item |
| `CrazyDiamondRestoreTerrain.checkSpecificConditions`, `standTickPerform`, `blocksPerTick` | Action handler, `performTick`, `RestorationSelection` | Camera-relative Manhattan 12 selection, aim/resolve selection, source fractional rate and stamina limit, up to 64 resolve placements, 2 stamina per placement, empty-selection continue-hold |
| `CrazyDiamondRestoreTerrain.restoreBlocks`, source/drop/XP ordering | Plan 43 `RestorationHooks` | Actual captured loot, no reroll, XP affordability even creative, item consumption before creative bypass, source falling-block ordering and improper reconstruction quirks |
| `CrazyDiamondHeal.healLivingEntity`, `healSpeedWithConfig`, `MCUtil.onLivingResurrect` | `CrazyDiamondHealing` | Owner-through-manifestation, death window through tick 15, two death ticks reversed, source health/bleeding interval, boats/health-bearing entities, death reset payload and source player XP reset |
| `StandEntityAction` target/camera/summon methods; `PowerBaseImpl.tickHeldAction` | Registry authority plus `RestorationSelection.Stand` | Server ray target, owning player/dimension checks, real attack-speed input, source off-arm summon adapter, held lease/release cleanup |

## Source-inactive paths

Targeted block reversal, filled-map reversal and brewing reversal remain unavailable. No new recipe or enchantment mechanics are introduced. Dynamic recipes without a declared output/ingredient list remain nonreversible.

## Platform adaptations

- Minecraft 26.3 exposes static recipe outputs through recipe displays and ingredients through placement information. The implementation reads those from the actual server recipe manager; it does not invent a reverse recipe table.
- Written-book NBT pages become plain-text writable-book components. Unrelated repaired-item components are retained.
- `TieredItem` no longer stores the original mining level. Original vanilla tool tiers and the mod sledgehammer are registered explicitly; `registerToolTier` supports further original item adapters. New vanilla tools without an original counterpart receive no invented tier bonus.
- Two narrow mixin accessors replace old access-transformer fields: TNT minecart fuse and Creeper powered entity data.
- Tracked server selection contains only actual restored positions and bounded target/action state. Plan 46 supplies source particles, loops, poses and markers. The resurrection packet retains the original health/death ordering correction.

## Integration contract

Root calls `CrazyDiamondRestorationActions.initialize()` and registers `restoration.TntMinecartAccess` and `restoration.CreeperPowerAccess`. The plan 45 entity implements `RestorationSelection.Stand.restorationAttackSpeed()` and `prepareRestorationSummon(Mode, newlySummoned)` using its actual source attributes, range efficiency and arm state. The root registry provides ownership, part/training, hold and fractional-cost checks; terrain debits honor `StandConfig.staminaFree`.

`setUseOtherPlayersInventories` exposes the source action configuration (default false). Plan 46 reads `RestorationSelection.DATA`, registers `CrazyDiamondHealing.ResetDeath` reception and uses original client-visible restoration behavior. No client supplies recipe outputs, restoration history, XP or item counts.

## Verification supplied

Three grouped real-world tests cover repair/XP/components/training and real recipe outputs; resolve gates and actual TNT/cart mutation; and player break, explosion and MR fire followed by held terrain restoration and actual completion synchronization. Plan 43's grouped chunk serialization case covers the same history save/reload path without duplicating a second serialization test here. All world tests await root integration of plans 45/46. No worker Gradle or game client was run.

## Assets inspected

Original `textures/action/crazy_diamond_repair.png`, `crazy_diamond_previous_state.png`, `crazy_diamond_restore_terrain.png` and improper icon variant; `sounds.json` restoration start/loop/end definitions; original restoration particle definitions and client action loop/phase call sites. Binary assets are reused by plan 46; this plan does not create substitutes.
