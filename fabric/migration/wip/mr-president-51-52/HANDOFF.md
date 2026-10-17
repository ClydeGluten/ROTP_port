# Mr President51/52 paused WIP

User requested wrap-up and stop on2026-10-18. These files are a reviewable snapshot outside active source sets. Plans51/52 are incomplete; no runtime feature is registered and no completed SUMMARY is claimed.

## Files and status

- `MrPresidentWorldData.java`: draft persistent level attachment preserving original Rooms/Turtle/Pos/TurtleDim/TurtlePos layout, UUID→section allocation and last turtle return position. Not compiled. Section currently validates y0..15; boundary tracking needs a separate unconstrained section or explicit handling outside room height before reuse.
- `MrPresidentTeleporter.java`: draft original loaded-turtle-first/remembered-location fallback, room entry and return using modern TeleportTransition. Not compiled; references unfinished Rooms/RoomAccess classes.
- `SOURCE-NOTES.md`: inspected source behavior and incomplete work. No original source was modified.

No room generation/service, effect lifecycle, access/boundary, exit block, Coco entity, items/key, Stand adapter, renderer/model/UI, modern dimension/template conversion or world tests have been written. No Gradle, runtime test, cross-dimension entry, restart or client verification was run for51/52. Only pinned26.3 signatures were inspected via local javap.

## Source inspected

Original MrPresidentStandType; WorldData and inside/back teleporters; EnteredRoomEffect; CocoJumboTurtleEntity; key item; GemBlock; RoomFeature; original registrations/dimension JSON; EntityUtilCap boundary; StandEffectsTracker and inherited StandEffectInstance NBT; source offhand-swap/carry/offhand-availability/HUD/wall-advancement callers; Coco model/renderer and embedded mesh; StandVirus mob grant algorithm. Original model mesh has been read but not converted.

Important source distinctions: one16³ room per turtle UUID; saved fallback return position only serialized for allocated rooms; source allocation never releases room identity. Entry lands at corner+(8,6,8), boundary snap at+(8.5,6,8.5). Unassigned turtle is unlocked; assigned key removed means locked. Lock transition expels living non-armor-stands. Loaded turtle removal destroys breakable contents and returns all entities/items; unload skips destruction. Entered-room effect's roomId/entered UUIDs/previous lock have no NBT override, so do not invent persistent membership. Master key bypasses missing Stand/identity. Removed key is named/bound; death/reversion drops a plain unbound key. Animal Stand never enters ordinary Arrow allocation pool.

## Agreed shared integration contracts (not implemented)

Root accepted proposed `MrPresidentStand.has(Entity)`, `grant(Entity)`, `tickUser(LivingEntity)`; `CocoJumboTurtleEntity.releaseCarried(ServerPlayer):boolean`, `carryOffset(float,LivingEntity)`, `carriedBy(Entity,Entity)`, `repositionCarriedPassenger(Entity,Entity):Vec3`; `MrPresidentRoomAccess.tickBoundary(Entity)`; natural companion spawn `onRegularTurtleSpawn(Turtle,ServerLevelAccessor,EntitySpawnReason)`. Root owns exact shared mixin/bootstrap/input hooks and59 `spawnCocoJumboTurtle` setting, default true. APIs remain proposals until implementation.

Source hook targets: server player swap-offhand action consumes packet only when releasing carried Coco; passenger positioning applies carried offset; offhand availability counts carried turtle; body pose and HUD draw carried turtle; vanilla turtle natural/chunk-generation/spawner checks add source probabilistic extra turtle; entity server tick enforces room boundary; owner death/removal/logout/Stand change applies effect cleanup; real Stand Arrow hit applies animal virus/grant path. Virus grant chance is1−0.15×amplifier each10 ticks. Original natural chance table and advancements are in SOURCE-NOTES/original Coco source.

GE worker `/root/equipment_finish` supplied stable `GoldExperienceTransformation.TurtleBridge` (package `com.github.standobyte.jojo.fabric.stand.goldexperience`): `isTurtle(Entity)`, `created(Entity turtle,LivingEntity owner,GETransformationEntity morph,Entity formerItemHolder)`, `reverseUnspawned(Entity)`, `beforeRevert(Entity)`, registered with `registerTurtleBridge`.52 must implement it: remember position and teleport nearby eligible entities before newborn turtle world-add; exclude morph/user/manifestation; include source remote former item holder; reverse unspawned stops entered effects; finished reversion drops key before discard.47 `LifeformSubtypes.registerCocoJumbo(grant,has)` is live; register real `jojo:coco_jumbo_turtle#stand` subtype after entity/power initialization. No GE shared file was edited.

## Previous completed handoff

41/42 MR source/client/resources/tests/docs are staged in active source sets, awaiting root's current integrated lane; see their SUMMARY and parity ledgers. Root already fixed MR heavy block path to call shared StandHeavyImpact exclusively. No further MR production edits are pending from this worker.37/38 summaries record root draft18 accepted server and integrated/dedicated client evidence.
