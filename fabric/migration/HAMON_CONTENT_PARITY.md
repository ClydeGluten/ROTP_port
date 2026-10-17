# Hamon equipment, inheritance and world content

Reference: retained Forge sources under `src/main/java/com/github/standobyte/jojo/`; original sources remain read-only. This document records the Draft 15 implementation. Runtime verification is recorded in the GSD summaries and draft manifest after the integration run.

| Source | Fabric adapter | Preserved behavior |
|---|---|---|
| `item/BreathControlMaskItem`, `ModArmorMaterials` | `BreathControlMaskItem`, `HamonItems` | Head slot, 110 durability, one armor, enchantability 9, iron repair, original hints, Binding Curse recipe. Training and breathing interactions are in `HamonService`. |
| `item/GlovesItem` | `HamonItems`, `HamonGlovesAnvil`, two mixins | One attack damage, enchantability 15, open fingers, original Knockback exception and non-damageable glove anvil merge. |
| `item/BubbleGlovesItem`, `SoapItem`, `HamonBubbleLauncher` | `BubbleGlovesItem`, `SoapItem`, `HamonItems` | 500 ammo; partial final charge; automatic empty reload and shift-use reload; soap inventory reload; glass-bottle returns; poison/nausea drinking effects; original main/offhand priority and creative launcher bottle consumption. |
| `item/OilItem`, `GameplayEventHandler` | `HamonOilItem`, `HamonItems` | Opposite-hand weapon application, 120 uses, 500 energy per physical melee hit, 1.5 base Hamon damage and original strength gain. |
| `item/ClackersItem`, `entity/itemprojectile/ClackersEntity`, `ItemProjectileEntity` | `ClackersItem`, `ClackersEntity` | 20-tick charge, 5/1 charge/upkeep costs, early self-hit, full/partial speed, original Hamon and physical damage, projectile combination, boomerang return, pickup and saved stack. |
| `item/MolotovItem`, `entity/damaging/projectile/MolotovEntity` | `MolotovItem`, `MolotovEntity`, `HamonCharges` | Original nearby fire/fuel search, throwable/dispenser speeds, diamond-shaped fire spread, direct burn damage, owner attribution and two charged Sendo-wave patterns. |
| `entity/mob/HamonMasterEntity` | `HamonMasterEntity` | Persistent stationary teacher, maximum base training/skills, no character technique, original attributes and Hamon/lava immunity. Original skin and extra robe are rendered by the client adapter. |
| `world/gen/structures/HamonTempleStructure`, `HamonTemplePieces` | `HamonTempleStructure`, data-driven structure/set | Original ten NBT templates, center and height gate, 49 terrain samples, nine pieces, all offsets, random draws and original forced unrotated rock quirk. Spacing 20, separation 8, salt 139567129. |
| `util/mc/CustomVillagerTrades`, `CustomTargetIconMap` | `HamonTempleTrades` | Expert cartographer, per-player attempt, one successful unique offer, biome/power odds, 24 emeralds plus compass, one use, 23 villager XP, 0.2 multiplier, radius-100 lookup, original map name/marker/color and first-buy sound. |
| `HamonLifeMagnetism`, `entity/LeavesGliderEntity` | `HamonItems.lifeMagnetism`, `LeavesGliderEntity` | Target-block and carried leaves, leaf type, item-only mounting, four seats, original acceleration/turning/gravity, shared passenger recharge, 200 energy and 4 health, decay/healing and attacker retaliation. |
| `HamonUtil.hamonPerksOnDeath`, `CrimsonBubbleEntity` | `HamonInheritance`, `CrimsonBubbleEntity` | Keep-on-death/hardcore gate, Crimson Bubble priority and held-item retention, 80% inheritance and gradual decay, or full Deep Pass to nearest Hamon player within eight blocks. |
| `DamageUtil.dealHamonDamage`, `LivingUtilCap.hamonSpread` | `VampireHamon`, `HamonCharges` | Charged-target immunity, scarf immunity/reduction, living-target reduction, strength/config multipliers, raw already-scaled charge path, damage through hit cooldown, accumulated spread/Weakness and 0.1/tick decay. |
| `GameplayEventHandler.cheatDeath` | `HamonInheritance` | Registered effect restores half health, clears fire, grants invisibility, teleports and clears nearby targets. Its technique skill remains normally inaccessible, as upstream. |
| `command/HamonStatCommand` | `HamonCommands` | Original `hamonstat set/add/query` grammar, fractional levels, optional training limit and original feedback. Draft grant/clear helpers live under `rotp hamon`. |

## Platform and scope boundaries

- Minecraft 26.3 replaced the `EXTREME_HILLS` biome category. The temple tag uses its direct modern windswept-hills/forest/gravelly-hills descendants. The original altitude gate remains unchanged.
- Structure resources moved from `structures` to `structure`; Minecraft performs the original 1.16 template data conversion. Templates retain their original blocks and teacher entity. The teacher's modern attachment is initialized from its original master defaults.
- Glider physics uses the original formulas, with server-authoritative movement driven by the rider's normal input packets and tracked interpolation. It does not accept arbitrary client vehicle position updates. Forge's dynamic fluid and swim hooks are adapted elsewhere in the Hamon subsystem.
- Modern map decoration registration replaces the old injected decoration-name encoding. A dedicated item model preserves the original gray map marking color after the old map-color component was removed.
- Meteorite and Pillar Man structures/trades are not yet ported. Their original home-biome exclusions remain in the Hamon map odds. Competition with those offers becomes relevant when their own milestones register them.
- Magician's Red's fire-source shortcut for Molotovs depends on that unported Stand. Ordinary nearby fire, lava, lit furnaces/campfires/torches and inventory ignition work.
- Crazy Diamond's block restoration and global item-tracking subsystem depend on an unported Stand. Gliders retain leaf state and thrown Clackers retain their item stack, but those cross-Stand abilities are not available in this milestone.
- `SQUID_INK_PASTA` and Luck/Pluck sword registrations are commented out upstream. They are not newly enabled here. The inaccessible `syo_barrage` and `cheat_death` skill definitions remain inaccessible through normal technique choices.

Joseph’s original nearby chat prediction is implemented in `HamonJosephChat` and mapped in the action ledger.

Playtest must assess vehicle steering/seat presentation and combat feel. Automated checks alone do not establish pixel-perfect UI or animation fidelity.

## Final Draft 15 verification

`/tmp/rotp-draft15-verification8.log`, **BUILD SUCCESSFUL in 53s**. All **197 required server GameTests** passed. The focused Hamon client flow passed in fresh integrated and dedicated worlds. All **91 core tests** pass (unchanged tasks reused); packaging passed **29 model checks**. No unrelated client matrix was repeated.

Bundle `0.2.2-fabric.15-dev+upstream.0.2.2.2`; manual playtest pending. Original source remains unchanged.

## Draft 16 source corrections

The glider now overrides `causeFallDamage` to return false, matching the original and preventing vanilla from forwarding landing damage to passengers. Molotov client use now checks the same nearby-fire/inventory ignition predicates before predicting success. Server use retains fuel priority, creative fire-charge consumption and exactly one projectile/bottle consumption per successful throw. The original clacker view-vector ricochet remains unchanged; an owner-directed suggestion was rejected after tracing the source helper. Focused content regressions and the failed-client-use check run through the shared Draft 16 verification lane.

## Final Draft 16 verification

`/tmp/rotp-draft16-verification3.log`, **BUILD SUCCESSFUL in 1m 1s**. All **205 required server GameTests** and **94 core tests** passed. The focused `HamonClientGameTest` passed in fresh integrated and dedicated worlds; packaging passed **29 model checks**. No unrelated client matrix was repeated.

Root registered the progression cases and integrated all domain changes before this successful run. The earlier worker-only validation notes describe the handoff, not the final delivery status. Bundle `0.2.2-fabric.16-dev+upstream.0.2.2.2`; mod SHA-256 `6e7de829462ebcf9e43decb31437840a70408790ccd9e9853f5d4ddd936e301b`. See `HAMON_SOURCE_REVIEW.md` and `05-DRAFT16-VERIFICATION.md` for correction evidence and limitations. Manual visual/gameplay acceptance remains pending.
