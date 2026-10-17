# Remaining supported-Stand systems: source audit

Date: 2026-10-18. Baseline: accepted Draft 16.1; original Forge `src/` is read-only. Scope: Star Platinum, The World and their shared infrastructure. Other Stands, non-Stand powers, acquisition/world content and save migration have separate owners. This is a targeted static source audit, not an exhaustive gameplay verification. No tests or production edits were performed for this report.

## Already implemented; do not schedule again

- SP/TW entity ownership, summon/follow/manual-control lifecycle, light/heavy attacks, held barrage/guard, stamina, server phase timing, movement restrictions, automatic finishers, shield disabling and TW armor piercing/kick sweep exist in `fabric/.../StarPlatinumEntity.java`, `StandService.java`, and `core/.../HeldCombat.java`.
- SP Uppercut, Star Finger, Inhale and Zoom exist. TW target-relative Quick move, backshot Time Stop Punch, deferred heavy-impact sound and clock advancement exist. These are partial original implementations, not absent actions.
- Regional entity/player/block-entity freeze, scheduled block/fluid tick retention, multiple distant casters, late join synchronization, immediate frozen-target damage and retained momentum exist in `TimeStopService`, `TimeStopClient`, their mixins and `StandDamage`.
- Per-Stand Resolve levels/training/records, default thresholds, combat/health boosts, decay, resistance, human Time Stop training and Creative bypass exist in `ResolveProgress` and `ProgressionService`.
- Vampire duration and Resolve modifiers have already landed: `ProgressionService.timeStopMaximum()` returns 180 with sufficient blood, otherwise 100; attack/health vampire modifiers are present. `TIME_STOP_PARITY.md`'s final paragraph and older `STATUS.md` paragraphs claiming vampire duration, knives and Hamon are pending are historical and stale.
- Original default HUD, original action selection/held input, per-Stand row editing, cooldowns/learning indicators, independent orbit, and local camera fading exist. Independent orbit and awakening generations are accepted user exceptions.

## Prioritized active gaps

### P0: existing vampire Time Stop packet rejection and countdown truncation

`fabric/src/main/java/com/github/standobyte/jojo/fabric/TimeStopPayload.java:40` rejects received durations above 100 with `DecoderException("Invalid time stop state")`, while `ProgressionService.timeStopMaximum()` supports 180. A trained vampire's valid stop can therefore disconnect receiving clients; this also affects a join-time synchronization payload. Root found this related codec defect during integration review.

Independently, `fabric/src/client/java/com/github/standobyte/jojo/fabric/client/TimeStopClient.java:29` clamps every received stop to `TimeStopService.STOP_TICKS` (100). Once decoding is corrected, an unchanged clamp would make the client countdown reach zero up to four seconds early. Authoritative resume still controls thaw. Original `TimeStopInstance.getTicksLeft()` and instance packets carry the actual duration. Root owns both corrections and a real-client regression.

### P1: Stand visibility, sound audience and damage eligibility

Original anchors:

- `entity/stand/StandEntity.java:isInvisible`, `isInvisibleTo`, `playSound`, `isInvulnerableTo`, `canTakeDamageFrom` (around lines 788, 798, 821 and 1137–1163).
- `power/impl/stand/StandUtil.java:playerCanSeeStands`, `playerCanHearStands` (139–146).
- `client/render/entity/renderer/stand/StandEntityRenderer.java:isBodyVisible` and its existing visibility handling.

Original spiritual Stands are hidden and inaudible to ordinary players without Stand/spirit vision, with spectator exceptions. Stand bodies accept Stand-capable damage, explicitly permitted mod damage and their special fire path; ordinary weapons are not equivalent to Stand attacks.

Fabric SP/TW inherit ordinary mob visibility; their renderers gate only camera opacity. `StarPlatinumEntity.playOriginal()` broadcasts normal player-category sound to everyone. `hurtServer()` forwards almost every non-owner source directly to its owner, without original Stand-damage eligibility. These are active multiplayer gameplay gaps even with only SP/TW installed.

Boundary: shared visibility/audibility predicates, render-state visibility adapter and sound audience; separate explicit Stand damage classification/eligibility. Preserve ordinary hits against the owner. Coordinate damage classification with barrage work so all migrated Stand attacks remain eligible. Do not impose spirit-vision-dependent rules until that effect is registered; its absence is a dependency, not permission to make all Stands visible.

### P1: Time Stop perception, immunity and invasion

Original anchors:

- `capability/world/TimeStopHandler.java:updateEntityTimeStop` (225), `sendPlayerState` (299), `canPlayerSeeInStoppedTime` (343), `canPlayerMoveInStoppedTime` (351), `gamemodeIgnoresTimeStop` (356), `hasTimeStopAbility` (362), and effect-add/expire/remove handlers (464 onward).
- `action/stand/TimeStop.java:checkSpecificConditions` (82), `perform` (115), `getHoldDurationToFire` (163).
- `client/ClientTimeStopHandler.java:setTimeStopClientState`, `tickPauseIrrelevant`, `setConstantPartialTick`, `shouldCancelSound`.

Original distinguishes seeing stopped time from moving in it. Unlocked time-stop users can see; immunity effect, Creative/spectator and the original singleplayer-owner exception control movement. Stand and soul movement derive from their owners. Optional Enderman immunity comes from common config. A qualified user can invade another stop immediately: `getHoldDurationToFire()` becomes zero, a separate instance and personal immunity lifetime are created, and overlapping instances retain independent resume semantics.

Fabric `TimeStopService.toggle()` rejects every frozen user and overlapping area; `isFrozen()` exempts only the caster/its Stand and existing projectile exceptions. `TimeStopClient.Stop.immune()` has the same limited model. Thus other Creative/spectator players freeze, SP/TW cannot invade, and unqualified observers still see the changing scene. Current client input mixins freeze all affected players uniformly.

Boundary: first define shared authoritative movement/perception decisions and a dedicated observer-state payload; then implement invasion and independent immunity expiration; then adapt frozen input/partial ticks/sound. Keep existing distant-stop maps, immediate damage and scheduled-tick retention. Do not merely remove overlap rejection: the current per-caster immunity test would freeze each caster under the other instance.

### P1: barrage clash, parry and supported combat responses

Original anchors:

- `entity/stand/BarrageHandler.java:addParryCount`, `tick`, delayed-hit state.
- `entity/stand/StandEntity.java:barrageClashParryPunches` (874), `setBarrageClashOpponent` (928), `actuallyHurt` (948), `setBarrageHitsThisTick` (around 1790).
- `action/stand/StandEntityMeleeBarrage.java:standTickPerform`, `isCancelable`, `onTaskStopped`, `cancelHeldOnGettingAttacked`, `isFreeRecovery`, `BarrageEntityPunch.doHit` (325).

Fabric `findTarget()` explicitly excludes every `StarPlatinumEntity`; `tickHeld()` emits a plain aggregated damage source with no per-hit barrage count/parry budget/opponent state. No SP-versus-TW barrage clash can occur. Existing guard is not clash support. Original parry consumes incoming punch counts, gives finisher/Resolve credit even on fully parried damage, tracks the opponent and allows particular recovery/cancellation behavior.

Other active differences in this same boundary:

- Original idle, non-manual Stand can begin a five-tick automatic guard when its body is attacked (`StandEntity.actuallyHurt`). Fabric only handles explicitly held guard.
- Original Resolve barrage grants its target three ticks of gravity suppression and stops mob navigation. Fabric restores zero impulse only; target gravity/navigation still run.
- Original Resolve halves finisher decay in `StandEntity.tick`; Fabric's tick uses fixed decay. Original Resolve and clash can also grant free barrage recovery; Fabric builds fixed recovery in `beginHeld`.
- Original barrage fractional extra hits use delayed random timing. Fabric `HeldCombat.tick()` uses deterministic accumulation; already documented adaptation, still a parity difference.

Boundary: one combat owner must coordinate `StarPlatinumEntity`, `StandDamage`, `HeldCombat`, synced clash state and damage metadata. Target selection must include other owners' Stands while preserving self/PvP checks. Gravity suppression needs a short-lived shared entity adapter and cleanup, not a permanent gravity toggle.

### P2: original block interaction, Star Finger body and manual interactions

Original `action/stand/punch/StandBlockPunch` handles block targets; `StandEntity.punch()` dispatches entity/block/miss behavior. Fabric regular attacks use living-target raycasts only. `SPStarFingerEntity` is an owner-bound, damageable body-part projectile with block hardness rules; Fabric `tickFinger()` is an extended ray hit and visual layer. Root `STATUS.md` already identifies missing Star Finger breaking and damageable parts.

Manual control movement/camera exists, but original manual block/entity/item interaction and first-person Stand hands remain incomplete. Sources: `StandEntity` manual-control methods, `StandUtil.setManualControl`, `client/ControllerStand`, related input handling. Preserve the modern camera exception.

Boundary: block-target/punch and gamerule-aware destruction adapter first; separate owner-bound projectile implementation for Star Finger; then manual interaction authority and client hands. Reuse existing `JojoGameRules.BREAK_BLOCKS` rather than adding a second rule.

### P2: common Stand settings and broader progression

Original common settings are live in `JojoModConfig.Common` and synchronized by `Common.SyncedValues`: `skipStandProgression`, `resolveLvlPoints`, `standStamina`, damage/resistance multipliers, `soulAscension`, `timeStopChunkRange`, `timeStopDamageMultiplier`, `endermenBeyondTimeSpace`, and Stand retention on death. Default range 12 is implemented; config value zero means whole dimension, not zero-radius freeze.

Fabric has no equivalent shared Stand config. `ResolveProgress.THRESHOLDS`, level bounds/record validation, `TimeStopService.CHUNK_RANGE`, damage multipliers and most unlock/stamina paths use fixed defaults. Existing `HamonConfig`/`VampireConfig` are useful adapters but do not implement Stand settings.

Active progression additions:

- `ResolveCounter.onResolveEffectStarted` and `ResolveLevelsMap.incExtraLevel` preserve extra max-level activation counts; Fabric record has no such field. Extra count is persistence parity, not an additional combat stat in this baseline.
- `StandPower.tickSoulCheck`/death path (around 484–522), `SoulEntity.tick`, `ControllerSoul`, and `ResolveCounter.soulAddResolveLook/Teammate` provide soul ascension and team/look Resolve. No Fabric soul entity/controller exists.
- `ResolveCounter.tickBoostRemoteControl`, `onChatMessage`, `getBoostVisible` and `GameplayEventHandler.onChatMessage` track remote/chat display multipliers. **They do not multiply actual gains in this source**: `boostAddedValue()` uses only attack/health/records. Port them as presentation state, not a new reward mechanic.

Boundary: config/schema synchronization first, with runtime-safe normalization for saved Resolve values; progression persistence and soul lifecycle second. Soul needs client camera/death-screen/network work and should be its own implementation plan.

### P2: shared client settings, original Stand screens and effects

Original anchors: `client/ClientModSettings.java:Settings`, `client/ui/screen/ClientModSettingsScreen`, `client/ui/screen/standskin/StandSkinsScreen`, `client/standskin/StandSkinsManager`, `capability/entity/player/PlayerClientBroadcastedSettings`, `client/render/world/shader/ShaderEffectApplier`, `TimeStopShader`, `client/resources/ResolveShadersListManager`, `ClientEventHandler` Resolve callbacks and world/game renderer mixins.

Missing active options include alternate bar/hotbar anchors and orientation, text/fold modes, locked-slot display, held/toggled row and bypass controls, Stand side synchronization, motion tilt/outline, character voices, menacing particles, Resolve shaders and Time Stop animation selection. Existing action layout persistence must be retained. `ProgressionScreen` is a port-specific text overview, not the original Stand stats/skin/settings screens. SP/TW renderers use fixed default textures and have no selected-skin state.

Time Stop currently uses a HUD tint/countdown. Original applies a selected Stand-specific postprocess, optional expansion animation and precedence over Resolve shaders; ordinary frozen observer sound/perception rules are separate. Copied shader assets alone are not a render-pipeline implementation. Original weather/particle world-render hooks and sound cancellation also need modern equivalents where active.

Boundary: a shared persisted client settings service/screen can proceed independently of server combat. Coordinate only Stand-side broadcast fields. Skin registry/resource reload/persistence is separate. Shader/postprocess implementation can follow the observer-state contract, without owning server Time Stop logic.

## Source-inactive or misleading candidates

- `ClientModSettings.Settings.autoResolveActivation` is declared but has no consumer in the original Java tree. Do not invent a manual-activation feature.
- Both registered SP/TW time stops set `timeStopDecayPerDay(0F)` in `ModStandsInit` (198/295). The generic daily decay hook exists, but default SP/TW training decay is inactive.
- `ClientTimeStopHandler.setWeatherStopped` and its old weather-handler storage are commented out. Active world-render rain/cloud/particle hooks are distinct; do not port the commented subsystem as required behavior.
- `TimeStopHandler.cancelBlockNeighborUpdate` is commented out. A full deferred world snapshot is not original active parity.
- Original HUD bar-cost preview code is commented out, as already documented in `ORIGINAL_HUD_CAMERA.md`.
- `GameplayEventHandler` has `TODO unsummoned stand auto-block`; that TODO is not implemented behavior. The summoned idle-Stand automatic block above is active.
- Light attacks add finisher on swing and again through the hit punch (`StandEntityLightAttack.standPerform` and `punchEntity`). Fabric's two additions on a landed light punch match those two paths; they are not a duplicate-credit bug.
- Remote/chat Resolve multipliers affect the visible multiplier, not original points. Treating them as missing combat multipliers would change the mod.

## Suggested implementation order and ownership

1. Root: vampire Time Stop codec/countdown correction and ledger reconciliation.
2. Shared Stand combat owner: damage eligibility, targeting, parry/clashes, automatic guard, Resolve barrage/finisher/recovery effects.
3. Time Stop owner: authority/perception payload, immunity/invasion/overlap, then frozen observers and sound.
4. Shared configuration/progression owner: common settings/schema, extra-level persistence; separate soul plan.
5. Client owner: persisted settings, original Stand stats/skins, then shader pipeline using agreed observer state.
6. Interaction owner: block punch/Star Finger body/manual interactions.

The combat and Time Stop owners both touch `StarPlatinumEntity` unless logic is extracted. Assign one owner to this existing file or serialize its integration. Client settings and configuration can research independently but must not silently change accepted orbit/allocation exceptions.
