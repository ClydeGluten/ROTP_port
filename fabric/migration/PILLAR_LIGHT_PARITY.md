# Pillar LIGHT parity

Status: implemented and handed to the shared integration lane; not runtime-verified. Original Forge source remains read-only. The parent owns common bootstrap, payload routing, species/lifecycle hooks, complete render cancellation, milk behavior, resource copying and test registration. Plan05-34 owns player clips, blades and first-person aura.

| Original active method | Fabric implementation | Preserved behavior / evidence status |
|---|---|---|
| `PillarmanAction.isUnlocked/checkConditions`, `ModPillarmanActions` LIGHT registrations | `PillarLightActions.start/eligible` | LIGHT mode works at stage1; stone form/stun/death/spectator reject actions. Entry uses shared `canStart`, continuation `canContinue`; failed start does not replace another hold. Server targets and resource values are never client supplied. |
| `PowerBaseImpl.tickHeldAction`, `Action.getHoldDurationMax` | `tick/release` | Flash auto-fires40; Decoy50. Dash releases only after10 and on ground, continues holding beyond builder40 because source overrides maximum. Stale input clears after20 unpaused ticks; dimension/power/mode invalidation clears transient state. |
| `PillarmanLightFlash.perform` + `MCUtil.entitiesAround` | `PillarLightCombat.flash` | Centered16-block cube, target-to-user line of sight; owner Stand excluded. Strict distance `<5` gets Blindness200, otherwise80; nonplayers/nonStands get STUN60. Cost25, cooldown80. |
| `PillarmanLightFlashDecoy.perform` | `PillarLightActions.release` | Cost40, cooldown120, FULL_INVISIBILITY100, white vanilla flash emission. No decoy entity or ordinary invisibility substitution. |
| `ClientEventHandler.onRenderLiving`, `ModStatusEffects.FULL_INVISIBILITY` | `hidden/uncurable` + root shared mixins | Entire living rendering including carried layers is suppressed; status keeps vanilla invisibility color and survives milk. Source only suppresses rendering; does not alter AI targetability. Shared integration requires verification. |
| `PillarmanBladeSlash.Instance.playerTick/punchPerform` | `PillarLightActions.tick`, `PillarLightCombat.slash` | Cost10; free main hand; movement.5; blades2, swing6, hit8, end20. Damage attack+5; .75 knockback and existing collision-impact service. |
| `DamageUtil.addArmorPiercing`, `VampirismClawLacerate.getDamage` | `PillarLightCombat.bladeHit` | Original15F clamps to1, fully compensating armor. Invert current26.3 armor curve, retain resistance/enchantment processing; do not reinterpret15 as15 armor points. |
| `PillarmanBladeDashAttack.perform/slashHitbox/Instance.playerTick` | `release`, `slashHitbox/dash` | Cost50; pitch clamp[-45,-18], scale1+movementSpeed*20, vertical*.05. Restore held movement modifier before reading speed. Low forward box width*1.5, y inflation.125, expansion height/2. Each UUID attempted once over15 ticks; side knockback60–90 degrees. |
| `PillarmanBladeDashAttack.Instance.cancelIncomingDamage` | `PillarLightCombat.cancelIncomingDamage` + root damage hook | All incoming damage canceled during active DASH, not charging or after end. |
| `PillarmanBladeBarrage.holdTick` + `DamageUtil.hurtThroughInvulTicks` | `PillarLightCombat.barrage`, shared `StandDamage.hurt` | Free main hand; cost1/tick; walk.2; attack*.2 through cooldown while restoring prior cooldown/lastHurt. Server ray respects intervening blocks. Hardness/dig speed/harvest factor10/3 and2.5*sqrt(attack) threshold; protection callback, game rule and build permission preserved. |
| `PillarmanBladeBarrage.onUserAttacked` | `cancelIncomingDamage` | Custom projectile requires canBeEvaded and non-Stand damage. Ordinary projectile path queries alive, nonpickable projectiles within block reach; incoming cone48.75degrees. Source cancels current event if any qualifying nearby projectile matches, without destroying projectile. |
| Flash/Decoy `startedHolding/stoppedHolding`; blade states | shared `PillarmanService.setBlades` | Synced visible blades during charge/barrage/dash; slash enables tick2. Every clear removes blades/movement. |
| `LightGlintParticle`, `LightModeFlashParticle`, `HamonAuraParticle` | `PillarLightClient.Glint/Flash` | Original sprites,8–17 spark and25–34 aura lifetimes, full brightness, alpha envelope; flash lifetime4, sinusoidal7.1 size. Flash charge25 sparks/tick after10; Decoy10 attached rainbow aura/tick outside owner first-person view. |
| `TrPillarmanParticlesPacket.Handler.handle`, `HamonUtil.createHamonSparkParticlesEmitter` | nested `Emitter`, client receiver | Quantity+1 impact sparks at original position/velocity. Intensity2 flash creates20 tracking emitters, lifetimes1,1,2…19. Server sends only to tracking players/self; bounded payload has no clientbound-to-server authority. |
| `PillarmanBladeBarrage.onHoldTickClientEffect` | client tick + root `SwingState` mixin | Force main-hand swing every second held tick.26.3 private swing state replaces old public `swinging=false`; scoped flag bypasses only the original forced swing. |

## Assets inspected and handoff

Original particle definitions `assets/jojo/particles/{light_spark,light_mode_flash,hamon_aura_rainbow}.json` retain their original references. `light_mode_flash` uses vanilla `minecraft:flash`; LIGHT sprites use `textures/particle/pillarman/light/light_spark_*.png`; rainbow uses `textures/particle/hamon/rainbow/*`. Root copies the three definitions through existing resource processing; existing texture copying remains unchanged.

Read original `PillarmanBladesModel`/`PillarmanBladesLayer`:16x16 texture, four cuboids per blade, arm-local pivots(-.9,9,5.9)/(2.3,9,5.9), y rotation3.1416, full light and foil. These are implemented by05-34 owner, not duplicated here. Source first-person blade method is inactive FIXME, not a new feature requirement. Read/handed off clip IDs `pillarman/light_flash`, `light_flash_decoy`, `blade_slash`, `blade_dash`, `blade_barrage`; first-person Decoy aura is two sprites per hand. `PillarLightClient.firstPersonAuraCount` and `RAINBOW_AURA` expose this to05-34.

## Shared integration APIs

- Common: `PillarLightActions.initialize`, `tick(ServerPlayer)`, `clear(ServerPlayer)`, `clearAllRuntime`; `active` joins shared busy checks. Ephemeral action state is deliberately not persisted/replayed on rejoin.
- Input: `flash`, `decoy`, `slash`, `dashCharge`, `barrage`; `keepHeld`; `release(player,true)` for release and false for cancellation. All starts return authoritative acceptance. Nested Emitter is server-to-client only; root owns common action-intent payload.
- Presentation: `state(LivingEntity)` returns Phase/ticks; `hidden` cancels complete living render; `uncurable` joins milk filtering. Public FLASH/DECOY IDs query shared AbilityCooldowns. Client initialize registers particles/emitter receiver. `animation` returns original clip IDs.
- Damage: call `PillarLightCombat.cancelIncomingDamage` before actual damage processing. Cooldown cleanup remains shared lifecycle responsibility.

## Verification

Authored three grouped real-world GameTests: Flash near/far/blocked LOS and charge/resource/CD/Decoy timing; slash hit timing and grounded/unlimited dash/real damage hook/once-per-target sweep; barrage repeated hits under immunity/projectile cone and custom immunity/terrain rules/stale hold/mode cleanup. Root registers/runs these. They do not claim save-rejoin, cross-dimension world transfer, explicit Stand-projectile rejection, full render invisibility or first-person sprite verification.

Worker scoped whitespace checks pass. Root reported main/client compile passed in compile4 after worker fixed the two reported client API errors against cached26.3 bytecode. Test compile, GameTests, multiplayer/rejoin and rendered effect/layer checks remain unrun at this handoff. No worker Gradle or TDD red/green evidence is claimed. Deferred integration evidence belongs to05-36; no shipped parity claim is made here.
