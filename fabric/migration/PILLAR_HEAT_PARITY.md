# Pillar Man HEAT source adaptation

Implementation is awaiting the consolidated root world/client verification. Original source is unchanged. No runtime parity pass is claimed here.

| Original methods inspected | Fabric methods | Behavior retained |
|---|---|---|
| `ModPillarmanActions`, inherited `PillarmanAction`, `Action.AbstractBuilder.holdType/holdToFire` | `PillarHeatActions.start/release/tick` | Mode independent of evolution stage4; resource, stone, stun and concurrent-action gates. Charge waits for release after threshold. |
| `PillarmanErraticBlaze`, its continuous action tick | `erratic/tick` | Cost20, walk0.1, two five-vein fans at tick10, end40. |
| `PillarmanGiantCarthwheelPrison.perform` | `giant/release` | Original misspelled registry ID; hold30, cost125, four eight-vein fans, invisible fire resistance600, cooldown100 (creative0). |
| `PillarmanSelfDetonation.perform` | `detonation/release`, `PillarHeatExplosion` | Hold60, cost150, radius3 blast, separate self damage40 and fire resistance200; creative exemption preserved. |
| `PillarmanVeinEntity` and inherited `OwnerBoundProjectileEntity` / `ModdedProjectileEntity` collision | `PillarVeinEntity`, `OwnedBodyPartEntity` | Lifespan25, range16, damage0.35 through immunity, zero normal knockback, burning10seconds, retract after successful hit, no damage while retracting. Original hardness0, fluid level4, face placement and snow/ice melting. |
| `MathUtil.xRotYRotOffsets` | `PillarHeatActions.rotation` | Exact source trigonometry and signed rotation convention, original emitter offsets. |
| `CustomExplosion.calculateBlocksToBlowUp/hurtEntities/finalizeExplosion`, `PillarmanExplosion`, `MCUtil.canHarm` | `PillarHeatExplosion.traceBlocks/explode` | Sixteen-edge ray grid, source resistance decrement, radius/impact damage formula; living targets and team friendly-fire rules; source's undamped impact velocity, merged drops capped16, conditional fire/boiling blood finish. |
| `GameplayEventHandler` HEAT right-click TNT branch and `PillarmanData` swallowed-fuse ticking | `eatExplosive`, `PillarmanService.addEatenTntFuse/tick` | Main-hand consumes primed TNT or primed TNT minecart, retains cart identity/state/passengers with normal minecart replacement; remaining fuse gives quiet sound, no damage. Zero fuse accepted. |
| `PillarmanVeinModel/Renderer`, HEAT action particle callbacks | `PillarBodyPartRenderer` texture/geometry overload, `PillarHeatClient` | Original vein/rib geometry and vein texture, charge aura and blood. Original player action animation is tracked separately in05-34. |

Platform changes: modern server explosion block callbacks preserve block-specific drops, modern dimension environment attribute replaces ultraWarm, velocity synchronization replaces the old packet behavior. Modern client attachment state contains phase and elapsed ticks; client has no damage/energy authority. Root owns payload/bootstrap/lifecycle and shared knockback hooks.

Remaining integration dependencies:05-34 active player animation/first-person aura,05-43 restoration history for damaged terrain, consolidated actual-client visual acceptance. These are not marked delivered by this module. Eaten minecart identity/passenger conversion needs the real-world fixture before acceptance.

Three grouped GameTests exercise actual action time/cost/release, owned projectile immunity/fire/retraction/fluid placement and original explosion/TNT effects. They are authored, not yet passed. No stand-in tests or source-string checks count as gameplay verification.
