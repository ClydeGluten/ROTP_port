# Gold Experience transformation source ledger

Status: implementation in progress in the isolated worker workspace. No compile or runtime claim.

## Inspected source and required behavior

| Original | Fabric responsibility |
| --- | --- |
| `GoldExperienceCreateLifeform.checkTarget/checkSpecificConditions/perform` | Server validates encountered subtype, living material, loaded ownership and 128-block range; source order is marked item, targeted nonliving entity, offhand item, targeted block. |
| `mobFromEntity/mobFromInventory/mobFromBlock` | Preserve full source entity/item/block and block entity state; consume exactly one inventory item; remove original world material once. Preserve projectile, fish-bucket and named-pigeon delivery branches. |
| `getTicksToCreate/getStaminaCostTicking` | Preserve speed/volume/attack/native biome formula and ongoing cost; at most sixteen currently loaded creations. |
| `GETransformationEntity.tick/turnInto/getDimensions/reverseTransformation/turnEntityBack` | Saved morph entity, source interpolation, gravity/fluid motion, host damage/bleeding, early reversal and completed ten-tick reversal; restore or drop blocked material once. |
| `GETransformationData.writeNbt/readNbt/resolveNbtRead` | Persist original entity, block state, original position, block entity, follow UUID/mode; expose sanitized presentation separately. |
| `GECreatedLifeformEffect.updateTarget/tick/stop` | Transfer effect target from morph to newborn, upkeep/range/delivery removal, neutrality and product effects, reversion during all removal reasons. |
| `GoldExperienceMarkItem.perform/getTargets/getTargetedEffect` | One marked item, source hand swap/split/give behavior, actual plan47 identity, source last-mark and 22.5-degree selection. |
| `GoldExperienceRevertLifeform.perform` | Owned created effect, source range, early-creation cooldown refund, no double restoration. |
| `GameplayEventHandler.onLivingAttack/onMobInteract/usePotionCowProduct/onAnimalOffspring` | Reflect attacks by living attackers except other created life; potion milk/stew and inherited product effects. |
| `SpecificTargetGoal` | Persistent UUID attack target; ordinary follow and delivery remain distinct. |

## Source nuances

- Only loaded created-lifeform targets count toward sixteen. Unloaded targets retain saved identities.
- The active effect cost code uses the transforming entity dimensions while morphing; its unreachable null branch does not substitute newborn dimensions.
- Reverting a block rotates its first directional property toward the lifeform's facing direction.
- Block entity inventory must be suppressed during removal and restored or dropped once on failure.
- Turtle room entry happens before the newborn turtle is added to the world. Plan52 must connect its real room lifecycle before the extra subtype is offered.

## Integration and verification

Exact APIs and world cases will be added with implementation. Shared hooks belong to root. Gradle and integrated/dedicated runtime verification are pending root's consolidated lane.
