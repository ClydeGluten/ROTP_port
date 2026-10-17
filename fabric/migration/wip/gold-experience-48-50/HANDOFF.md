# Gold Experience plans 48–50: inactive WIP handoff

Saved 2026-10-18 at the user's request to wrap up. **Plans 48, 49 and 50 are incomplete.** This directory is a reviewable snapshot of `/tmp/rotp-ge48-work`; its nested `fabric/` paths show intended destinations. None of these files is in an active source set. Do not enable Gold Experience or copy this batch into production unchanged.

## Snapshot and verification

Seven Java drafts, one damage-type JSON and one source ledger are preserved. No GE compiler, GameTest, integrated-client or dedicated-client verification has run. The drafts intentionally refer to missing `GoldExperienceEntity`/`GoldExperienceActions` classes and require shared hooks below. Only snapshot integrity, JSON syntax and staged whitespace checks were run during wrap-up. No Gradle, commit, push, reset, shared bootstrap change or original-source edit was performed by this worker.

Plan47 tracking was previously copied to active sources and staged; root reported successful main/client/GameTest compilation after its shared hooks. Its runtime scenarios remain pending. Root's earlier draft18 verification (258 server tests, SC/HG integrated and dedicated clients, late join) predates the plan47/GE additions and must not be cited as GE or tracking runtime proof. Older equipment, Pillar presentation and HG slices remain in their existing staged files; no stale scratch copies were restored.

## Draft contents

| Draft | Implemented in scratch; not yet verified |
| --- | --- |
| `GETransformationData` | Saved original entity/block/block entity, source position, follow UUID/mode, product effects, item presentation. |
| `GETransformationEntity` | Forward/reverse saved morph, dimensions, motion, embedded host damage, material restoration/fallback drops, newborn effect transfer. |
| `GoldExperienceMorphPayload` | Bounded public entity views from synced data, owner/dimension/entity identity checks and pending-client spawn cache; avoids sending full private entity/block-entity NBT. |
| `GoldExperienceTransformation` | Source duration/upkeep/caps, encountered subtype authority, offhand/targeted/marked material origins, source removal, created-lifeform effect, follow/neutrality, reflection draft and turtle bridge. |
| `GoldExperienceItemActions` | Source one-item hand split/swap, plan47 marked identity, actual holder resolution, range and look selection. |
| `GEAnimalProducts` | Persistent product-effects component, milk/stew decoration, post-consumption effects and offspring inheritance. |
| `GELifeshotState` | Sensory effect, 100-tick duration and repeated-hit resistance (40/20 increments, 1200-tick delay, .125 decay), undead villager conversion, delayed consciousness impulse. |
| `arrow_lifeform.json` | Source embedded-lifeform damage type; shared armor-bypass tag still needs wiring. |

## Original source inspected

Read active `GoldExperienceCreateLifeform` target/condition/perform and all material-origin branches; `GoldExperienceChooseLifeform`, mark and revert actions; `GETransformationEntity`, source-data persistence and `GECreatedLifeformEffect`; `GameplayEventHandler` reflection, cow-product consumption and offspring callbacks; `SpecificTargetGoal`; `ModStandsInit` GE definitions; relevant inherited `Action`, `StandAction`, `StandEntityAction`, light/heavy/barrage and modifier behavior. Read GE lifeshot, heavy/tooth, life detector, bone meal, self/other healing, healing-item, `GEHealingEffect`, body-tissue and the entire original `ObjectEntity` implementation. The chooser screens, consciousness client implementation and actual GE model/animation assets still need their full plan50 audit.

Source nuances to retain on resumption:

- There are 17 action definitions/variations: punch, barrage, lifeshot modifier, lifeshot punch, tooth-lifeform modifier, heavy punch, block, choose, create, revert, mark, bone meal, life detector, heal, healing item, heal other, plus the source-registered action mapping must be recounted directly from `ModStandsInit` before claiming coverage. The current scratch batch registers none of the GE action table.
- Ordinary GE actions and entity-owned tasks have different summon/phase behavior. Inspect actual constructor `super(builder...)` calls; do not infer summon modes from required parts. Recovery-followup lookup lives in `StandAction` as well as the deprecated heavy override; this audit was the next unfinished step.
- Only loaded creations count toward sixteen; preserve unloaded identities. The active original upkeep code uses morph dimensions while transforming. Source creation's native-habitat overload consults the created entity's player capability, so ordinary mobs do not receive the hypothetical owner-native discount.
- Burning block reversal invokes the fire hook instead of placing the block. Block-container transfer removes the block entity then `removeBlock(pos,false)` without generating loot or restoration history. Terrain worker confirmed this requires no history-suppression API; pre-existing history is not erased.
- Marked item frames begin the morph half a block in front of the frame. Turtle room entry occurs before the newborn is added. Early reversal refunds creation cooldown; completed reversal takes ten ticks.
- Lifeshot treats vampire/zombie players as undead through `SpeciesHooks.undeadPlayer`; Pillarmen are excluded from that specific rule. Source sensory removal resets Mob NoAI to false. The resistance interval is sixty seconds, not twenty minutes.
- Healing source distinctions remain unimplemented: ordinary living exclusions; undead-player regeneration; arrow/knife material before offhand material; bleed reduction; saved previous regeneration; body-tissue creator ownership; dead-target soul resurrection and dying-body branches.

## Exact pending shared integration

Root owns these hooks and shared files. They must remain disconnected until the feature batch is coherent.

1. Bootstrap `GoldExperienceTransformation.initialize()` (registers its morph payload, marked-item effect and animal-product component) and `GELifeshotState.initialize()` once. Register completed GE manifestation, attribute factory, all action definitions, entity renderers and item renderer only after missing files exist. GE remains outside the natural pool/playable definitions until delivery verification.
2. Implement `GoldExperienceTransformation.EquipmentDrop.jojo$dropGEEquipment(ServerLevel)` by invoking protected `LivingEntity.dropEquipment`; implement `TargetGoals.jojo$geTargetSelector()` for `Mob.targetSelector`. Route natural targeting through `allowNaturalTarget(Mob,LivingEntity)` and living-attack reflection through `reflect(LivingEntity,DamageSource,float)` once the incomplete reflection behavior below is fixed.
3. Animal products: decorate the actual milk/stew result with `GEAnimalProducts.decorate(animal,player,stack)`; run `consume(user,usedStack)` after vanilla consumable effects but before the stack is consumed (milk otherwise clears these effects); call the draft offspring inheritance method at actual breeding. Avoid applying the custom effects a second time through vanilla potion contents.
4. Sensory overload: call `GELifeshotState.tick(LivingEntity)` once per real server living tick; gate effect application with `applicable`; call `removed` on every sensory-effect removal path; preserve source uncurable behavior. `CreeperSwell.jojo$setGECreeperSwell(int)` writes private `Creeper.swell`; `ZombieConversion.jojo$startGEConversion(UUID,int)` invokes private `ZombieVillager.startConverting`. The effect itself schedules the two-tick impulse from `onEffectStarted`.
5. Client receivers: morph payload receive/clientTick/clear lifecycle and `GELifeshotState.Impulse` receive/takeImpulse; send current morph presentation on tracking start. Root plan47 `TrackingClient` receivers already exist and must not be duplicated. Plan50 must send `ItemTrackingPayload.Encounter` from original encounter-on-look/toast flow.
6. Add `jojo:arrow_lifeform` to the shared damage-type armor-bypass tag. Embedded host bleeding needs the original particle-position offset; the current draft ignores that offset and only applies the effect.
7. Plan52 bridge is agreed with `zombie_player`: `GoldExperienceTransformation.TurtleBridge` methods `isTurtle(Entity)`, `created(Entity,LivingEntity,GETransformationEntity,Entity formerHolder)`, `reverseUnspawned(Entity)`, `beforeRevert(Entity)`, installed through `registerTurtleBridge`. Register `LifeformSubtypes.registerCocoJumbo(Consumer<Entity> grant, Predicate<Entity> hasStand)` only when the real Mr President actor/room exists. `created` remembers the turtle and teleports eligible nearby/remote-holder entities before world add; early reversal clears unspawned actor effects; completed reversal drops its key before removal. No fake room/subtype fallback.
8. Plan60 soul bridge is still pending from root: actual soul lookup/alive/time-left, resurrection and dying-body status/timer must connect to healing and detector. Reflection requires a real owner/target Resolve helper (root proposed `ProgressionService.addResolve(ServerPlayer,LivingEntity,float)`); do not fake attacker attribution. Shared `StandDamage.marked(...).allowSelf()` exists and still needs use in the reflection draft. Shared plan55 defenses/clash integration remains pending.
9. Shared heavy-impact implementation is already live under `stand/StandHeavyImpact`: `start(StandManifestation,LivingEntity,double power,double precision)` after applying punch knockback, and `punchBlock(StandManifestation,BlockHitResult,double,double)`. Do not duplicate it. Root confirms the start radius matches inherited source exactly.

## Incomplete files and known draft issues

- Missing plan48 `GoldExperienceEntity` and transformation GameTests. Implement an independent GE manifestation with original stats, arms/full summon, control, task phases, damage transfer and lifecycle; no SP fallback.
- Missing plan49 `GoldExperienceActions`, `GoldExperienceCombatActions`, `GoldExperienceHealing`, `GEBodyTissueItem`, combat GameTests and final source ledger. Missing source-required tooth `ObjectEntity` adaptation (`util_object`, .25×.25, saved, noSummon) and its rendering. Tooth recovery must retain the actual ejected object and source target identity.
- Every plan50 class is missing: real GE model/animation/renderer, morph/object renderers, all three chooser flows and persistent selection/search, markers/glint/outlines, sensory consciousness/camera and encounter toast. Use the actual BB geometry and authored tracks through plan74 conversion; the original near-empty Java model is not geometry.
- Transformation targeting currently filters eligible nonliving materials before ray selection; audit/fix the original living-target rejection so a living entity cannot be ignored in favor of a block behind it. Reconcile source target checks with offhand precedence.
- Reflection needs the allow-self damage marker and successful-hit Resolve credit. Created-lifeform marker presentation still needs sanitized owner-specific original-material/name snapshots for completed creatures; the private effect NBT is intentionally not broadcast.
- Audit all current cached Minecraft API assumptions with javap and compile in root's lane. Review rare failed newborn world-add behavior to avoid losing material. Restore source slime-size branch if a valid custom subtype can enter it. Review additional spawn presentation for tooth and other source entities; synced vanilla data alone may be insufficient.
- Review saved-source/follow-mode behavior: the draft preserves followMode on copy, unlike the source copy omission. Document this as an identity-preservation correction only after verification. Block-placement event differences, container scan bounds and other platform adaptations need final ledger entries.
- No GE tests exist yet. Required real-world cases: item and block entity create/revert with components and no duplication; marked transfer/embedded projectiles; death, unload and save/rejoin; real Coco room; repeated lifeshot/actual tooth; plants/healing/bleeding/tissue; malicious subtype/UUID rejection; morph and sensory clients on integrated/dedicated servers.

## Resumption

Keep this snapshot inactive. Resume from the original plans/context and current shared API files, not stale scratch versions of already delivered features. Complete missing implementation and source audit, then copy one coherent module batch after root releases its build lane. Only root may run Gradle/shared integration. Do not create completion summaries or mark requirements done until actual acceptance evidence exists.
