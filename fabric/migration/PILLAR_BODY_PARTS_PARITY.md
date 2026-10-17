# Pillar horn, ribs and owner-bound segments

Source authority: original Forge tree at `72862a5826ed45d1dc26e90b37853097acda35de`, read-only. Scope05-29. New code is staged; this worker ran no Gradle, world tests, or client verification. Registration and source review do not establish runtime parity.

## Method coverage

Original Java paths below are under `src/main/java/com/github/standobyte/jojo/`. Fabric paths are under `fabric/src/main/java/com/github/standobyte/jojo/fabric/` except the client renderer.

| Original methods inspected | Fabric methods | Implemented contract |
|---|---|---|
| `action/non_stand/PillarmanHornAttack.perform`; `ModPillarmanActions.PILLARMAN_HORN_ATTACK` | `pillarman/PillarBodyParts.horn` | Stage2, cost15, cooldown60 (creative skips additional cooldown), allowed in stone/stun, uses original `pm_horn` entity ID. Root wires packet/catalog route. |
| `action/non_stand/PillarmanRibsBlades.perform/addRibProjectile`; `MathUtil.xRotYRotOffsets` | `PillarBodyParts.ribs` | Eight exact body offsets, original shared pitch≈5.710593°/yaw0, life21, cost60. Source `.cooldown(80,0)` is technical80 even creative. Stage2 and no stone/stun bypass. |
| `OwnerBoundProjectileEntity.tick/moveProjectile/moveBoundToOwner/getNextOriginOffset/updateDistance/updateMotionFlags` | `stand/OwnedBodyPartEntity.tick` | Owner lookup, owner death/dimension cleanup, per-tick body-origin movement, extension/full-length pause/retraction, detached travel path, and bounded lifetime. Source computes next distance before switching motion flags; retained. |
| `OwnerBoundProjectileEntity.ownerPosition/getOriginPoint`; `DamagingEntity.getPos/getOwnerRelativeOffset/getXRotOffset` | `origin/bodyOrigin/ownerPosition/ownerRelativeOffset/pitchOffset/originOffset` | Physics origin follows head yaw; body-part rendering origin follows body yaw; eyes plus exact local rotated offsets. Hooks remain reusable for later owner-bound Stand projectiles. |
| `OwnerBoundProjectileEntity.rayTrace`; `JojoModUtil.rayTraceMultipleEntities` | `OwnedBodyPartEntity.hitSegment` | Predictive complete segment from origin through current tip+motion; all intersected entities sorted nearest first. Original quirk: block ray only when no entities intersect, even if an entity is behind terrain. |
| `DamagingEntity.canHitEntity`; `ModdedProjectileEntity.canHitEntity` | `OwnedBodyPartEntity.eligibleTarget` | Living owner attack rules, owner/own Stand exclusions, same-owner projectile exclusion, PvP protection, pickability and spectator filtering. Deflection can permit hitting owner. |
| `DamagingEntity.getDamageSource/hurtTarget`; `OwnerBoundProjectileEntity.hurtTarget`; `DamageUtil.hurtThroughInvulTicks` | `damageSource/hurtTarget/hurtThroughImmunity`, existing `StandDamage.hurt` | Indirect arrow classification with direct projectile and owner; restores other attacks' damage cooldown/last-hurt state. Damage multiplier and overridable source hook support later Stand variants. Current horn/ribs are non-Stand damage. |
| `DamagingEntity.onHitBlock/destroyBlock/canBreakBlock`; `OwnerBoundProjectileEntity.afterBlockHit` | `destroyBlock/afterBlockHit` | Hardness, protection/game rule and player break-event checks; no drops in creative; failed break retracts. |
| `OwnerBoundProjectileEntity.attachToEntity/attachToBlockPos/moveToEntityAttached/moveToBlockAttached/dragTarget` | `attachToEntity/attachToBlock/detachTarget/attachedEntity/tick/dragTarget` | Target midpoint/block-center attachment; stun-compatible dragging; previous dragged velocity reset and removal cleanup. |
| `OwnerBoundProjectileEntity.writeSpawnData/readSpawnData/addAdditionalSaveData/readAdditionalSaveData`; `DamagingEntity` save methods | synced owner/attachment/flags/distance/lifetime/age/speed plus save/load | Original age and live segment state reach late trackers. Owner UUID comes from vanilla Projectile; attached target UUID saved/restored. Reject nonfinite/bounded corrupt saved motion fields. |
| `PillarmanHornEntity` overrides | `PillarHornEntity` | Damage.5 through immunity; zero knockback; hardness≤5; speed.4; retract1.2; four ticks full-length pause;40-tick life; head offset(0,.15,0). |
| `PillarmanRibEntity.hurtTarget/afterEntityHit/ticksLifespan/getOwnerRelativeOffset/originOffset` | `PillarRibsEntity` | Damage.5 through immunity once; zero knockback; hardness≤0. Successful unblocked living hit attaches, adds20 lifetime and stuns for the resulting remaining lifetime. Shields/guarding Stands do not attach. |
| `PillarmanHornModel/PillarmanRibModel`; `RepeatingModel.renderToBuffer`; `ExtendingEntityRenderer`; `SimpleEntityRenderer` | client `pillarman/PillarBodyPartRenderer` | Exact source cubes/UV32×32, inverted Y/Z then yaw/pitch, tip length2 pixels, repeated8-pixel pieces including original full final piece; body-origin extent, owner's packed lighting and invisibility. |

## Assets actually inspected

Original model and renderer source files were read completely. Texture files were inspected for existence and PNG dimensions:

- `src/main/resources/assets/jojo/textures/entity/projectiles/pm_horn.png`:32×32 RGBA.
- `src/main/resources/assets/jojo/textures/entity/projectiles/pillarman_ribs.png`:32×32 RGBA.

The renderer references those exact unchanged assets through Fabric's existing original-texture copy rule. Original horn/ribs use fixed projectile textures, **not the player's skin texture**. The plan's “owner skin/body origin” wording is implemented according to actual source: owner body rotation, lighting and visibility with original fixed textures. No texture was repainted and no generic arrow model was substituted. Visual output remains unverified.

## Integration contract

- Common registration: `PillarBodyParts.initialize()`. Public `HORN` and `RIBS` entity types preserve original sizes.125 and.35, and tracking update interval20.
- Server intents: `PillarBodyParts.horn(ServerPlayer)` and `ribs(ServerPlayer)` return success. They validate source stage/stone/stun/action-overlap/resource gates before spawning.
- Lifecycle: root calls `tick(ServerPlayer)` once per tick for cooldowns, `clear(LivingEntity)` at death/disconnect/power removal, and `clearAllRuntime()` on server stop. `clear` removes only that owner's owner-bound entities. Entity ticks independently reject absent/dead/removed/cross-dimension owners.
- Damage integration: root's knockback hook multiplies by `OwnedBodyPartEntity.knockbackMultiplier()` when the direct damage entity is a body part. Both implementations return0. Server velocity restoration also preserves pre-hit motion; the shared hook avoids erroneous client knockback packets.
- Client registration: `PillarBodyPartRenderer.initialize()` registers both renderers. Root owns action payload/HUD/client selection integration and test discovery.
- Later modules subclass `OwnedBodyPartEntity`, overriding damage source/classification, body offsets, speed/retraction/full-length time, hardness, damage, attachment hit behavior and optional dragging. `EvasionProjectile` is already implemented for the common Pillar evasion filter.

## Explicit adaptations and source bugs

The source saves `AttachedEntity` but accidentally checks `BoundTarget` while loading; the adapter reads the actual saved attachment key. The source's rib `dealtDamage` flag was not saved; this adapter persists it so reload cannot turn an already attached rib into a fresh attack. Both changes preserve intended one-hit/attachment behavior across the required real save/rejoin boundary.

Lifecycle cleanup includes dimension transfer and invalid target rejection. Source numeric fields lacked defensive bounds; the modern persistence codec bounds age/lifetime/motion to avoid invalid metadata. No arbitrary client-provided target, damage or owner ID is accepted by an action packet.

No currently active horn/rib behavior is deliberately stubbed. The reusable base is not a claim that later strings, veins, Stand skins or their special damage rules are already ported; those subclasses retain their own future plans. Source-inactive `PillarmanRibEntity.addKnockback` field is not applied because original `knockbackMultiplier()` unconditionally returns0.

## Verification status

`PillarBodyPartGameTests` supplies three actual entity scenarios: horn repeated damage/immunity/retraction/zero knockback; terrain and owner dimension/death cleanup; ribs spawn count/cost/attachment/stun/real save-load/one-hit lifetime. Root must register and run these in the shared lane. Worker check: staged diff whitespace passes; modern Entity/Projectile/renderer APIs were inspected with read-only `javap`. No worker build, GameTest, or client run occurred.
