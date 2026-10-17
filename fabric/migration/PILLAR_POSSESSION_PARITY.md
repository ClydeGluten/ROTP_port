# Pillar possession: source and integration ledger

Status: implemented in isolated module; **not compiled, executed or integrated yet**. Root owns registration, shared hooks and the consolidated 05-36 verification lane. Original Forge source is unchanged.

## Source mapping

| Original source/method | Fabric owner | Preserved behavior |
|---|---|---|
| `ModPillarmanActions.PILLARMAN_HIDE_IN_ENTITY` | `PossessionService.startHeld/tick/stopHeld` | Hold20, walk0, no energy cost or cooldown; release cancels before auto-fire. |
| `PillarmanHideInEntity.checkTarget/perform`, inherited `PillarmanAction.checkConditions/isUnlocked` | `startHeld/validTarget/tick` | Stage2, living nonself target, stone/stun and spectator gates; possession remains spectator camera, not host control. |
| `Action.checkRangeAndTarget/getMaxRangeSqEntityTarget`, `JojoModUtil.getDistance(Entity,AABB)` | `validTarget` | Eye-to-box clipped surface distance minus player half-width; squared range64, divided by4 without line of sight. No invented strict LOS prohibition or two-block blood-drain range. |
| `IPlayerPossess`, `PlayerEntityMixin.jojoPossessEntity/jojoTickEntityPossession` | `PossessionState`, `tick/stop/host` | Host UUID, previous game mode, asAlive=true, fixed action context; spectator camera; dead/missing host and dead owner restore mode/camera. |
| `PlayerEntityMixin.toNBT/fromNBT/syncToClient/syncToTracking` | Persistent attachment codec, `onJoin/sync/syncTo`, bounded state payload | Versioned host/dimension/mode/context persistence; self and tracking snapshots, UUID checks against recycled entity IDs. |
| `ServerPlayerEntityMixin.jojoCancelEntitySpectate/jojoCancelTeleport` | `cancelCameraChange/cancelTeleport` | Self-camera dismount exits; other spectator-camera selections and arbitrary teleports are blocked while possessed. |
| `GameplayEventHandler.cancelGameModeChange`, `JojoModUtil.getActualGameModeWhilePossessing` | `cancelGameModeChange/actualGameMode` | Admin-requested mode becomes the return mode; player remains spectator until exit. |
| `TrPossessEntityPacket.Handler.handle`, `SpectatorGuiMixin` | `PossessionClient`, root spectator UI hooks | Resolve host camera, clear it on exit, suppress vanilla spectator tooltip/menu input during active possession. |
| `PillarmanHideInEntity.clHeldStartAnim/clHeldStopAnim` | `PossessionClient.heldTicks(Entity)` → 05-34 presentation owner | Exact authoritative start/stop clock exposed for original player animation; animation rendering is not claimed implemented by this module. |

Source files above were read, including both complete possession mixins and active inherited range checks. Source TODOs about ticking the body/showing power HUD/controlling host are not implemented features and remain excluded. Angelo Rock's lethal possession is separate CD scope; this attachment validates the Pillar action context and does not substitute for that path.

## Platform adaptations

- Fabric persistent attachment replaces the Forge player mixin capability NBT. Absent attachment means inactive. Only active possession is saved; an interrupted charge never resumes after login.
- Saved UUID resolution retries for at most20 server ticks; failed resolution restores the recorded mode. Once a host was resolved, its death/removal restores immediately. Client spawn-order resolution is similarly bounded and requests server exit on failure. This is a load-order safeguard, not new host control.
- Static inspection of26.3 `ServerPlayer.setCamera` bytecode confirmed that it invokes the dimension-aware `teleportTo` overload internally. `setHostCamera` grants a scoped exception to the teleport guard only during that server-owned camera transition. External teleports remain blocked; forced lifecycle transfers call `stop` first.
- Hold heartbeat expires after15 ticks, matching existing port action transport safety. No target IDs, game modes, damage or clocks received from client become authoritative without validation.

## Required root wiring

1. Call `PossessionService.initialize()` during main initialization. It registers the persistent attachment and both payload directions exactly once. Call `PossessionClient.initialize()` only from client initialization; it installs its receiver, tick and disconnect callbacks.
2. Tick `PossessionService.tick(ServerPlayer)` for **all players including spectators**, after original base actions. Call `onJoin` after saved attachments load. Call `stop` before death/respawn cloning, disconnect player-save, power clear and forced dimension transfer. On respawn, also clear any copied recovery marker on the new player via `stop(newPlayer)`. Call `clearAllRuntime` after player cleanup on server shutdown.
3. Root mixins at `ServerPlayer.setCamera(Entity)` and `setGameMode(GameType)` cancel if the corresponding `cancelCameraChange`/`cancelGameModeChange` returns true. The latter boolean method returns false when canceled. Guard the dimension-aware `teleportTo(ServerLevel,double,double,double,Set,float,float,boolean)` with `cancelTeleport`; preserve its false return on cancellation. Do not add a broad guard that blocks the scoped `setHostCamera` exception.
4. Tracking-start callback calls `syncTo(possessingOwner,trackingPlayer)`. Main payload stream already sends owner+tracking updates. `actualGameMode` exposes the source logical mode for possession-sensitive visibility rules.
5. Original Pillar catalog action `jojo:pillarman_hide_in_entity`: on press call `PossessionClient.start(selectedEntity)`; every≤15 ticks while held call `keepHeld`; release calls `release`. Dismount/escape calls `exit`. Wire `movementMultiplier()` into existing client movement restriction. Root shared action dispatch must reject competing start intents while `isHolding(player)`; ordinary spectator power gates already reject during possession.
6. Give `PossessionClient.isPossessing()` camera priority over Stand/manual/orbit ownership; use `suppressSpectatorMenu()` for spectator tooltip, scroll, middle-click and hotbar menu. Keep accepted orbit outside possession. 05-34 uses `heldTicks(Entity)` for original possession animation start/stop on local/tracked players.
7. Register `PossessionGameTests` in gametest entrypoints. Incorporate one client segment into the existing focused delivery flow: charge19/20, enter moving host, dismount; repeat on dedicated client, host removal, reconnect, and mode restoration. Include original charge animation and suppressed spectator menu after05-34.

## Authored verification (not run)

Four actual-world GameTests cover20-tick camera transition, original cost/movement, stage/stone/self/range gates, missing heartbeat/release, idempotent dismount, deferred admin mode, actual player save/load host resolution, missing-host recovery, owner/host death, and safe forced dimension transfer. No Gradle, compilation, tests or application launches were performed by the module worker. Root must validate source sets, hooks, packet registration and client flow before claiming gameplay completion.
