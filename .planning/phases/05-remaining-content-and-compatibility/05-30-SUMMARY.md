# 05-30 — Pillar possession module

Status: implementation complete in assigned files; integration/build/runtime verification pending root. No commit/push. Original source untouched.

## Delivered

- `PossessionState`: version1 persistent attachment preserving host UUID, dimension, previous mode, `PossessAsAlive=true` and fixed original action context. Invalid versions/context fail codec validation.
- `PossessionService`: original20-tick/free/walk0 charge; stage2/stone/stun/target/range validation; spectator host camera; idempotent restoration; bounded saved-host resolution; source camera/teleport/admin-mode hooks.
- `PossessionPayload`: server-authoritative state snapshots and bounded START/KEEP_HELD/RELEASE/EXIT intent. No shared enum edits.
- `PossessionClient`: self/tracking state, host UUID/ID resolution, bounded retry, exit/reset hooks, charge-animation clock and spectator-menu/camera-priority predicates.
- Four `PossessionGameTests` using real players, hosts, game modes, cameras and player serialization. Tests authored, not executed.

Exact source/method mapping, platform adaptations and root wiring: `fabric/migration/PILLAR_POSSESSION_PARITY.md`.

## Public integration contract

Main service, package `com.github.standobyte.jojo.fabric.pillarman`:

- `initialize()`; `tick(ServerPlayer)` including spectators; `onJoin(ServerPlayer)` after attachment load.
- `boolean startHeld(ServerPlayer, Entity)`; `keepHeld(ServerPlayer)`; `stopHeld(ServerPlayer)`; `int heldTicks(ServerPlayer)`; `boolean isHolding(ServerPlayer)`.
- `stop(ServerPlayer)` before death/respawn/disconnect-save/power clear/forced transfer; `clearAllRuntime()` after shutdown player cleanup.
- `boolean isPossessing(ServerPlayer)`; `Entity host(ServerPlayer)`; `PossessionState state(ServerPlayer)`.
- `boolean cancelCameraChange(ServerPlayer, Entity)`; `boolean cancelTeleport(ServerPlayer)`; `boolean cancelGameModeChange(ServerPlayer, GameType)`; `GameType actualGameMode(ServerPlayer)`.
- `syncTo(ServerPlayer owner, ServerPlayer recipient)` for start-tracking; `sync(ServerPlayer)`; `PossessionPayload snapshot(ServerPlayer)`.

Client, package `com.github.standobyte.jojo.fabric.client.pillarman`:

- `initialize()` installs owned receiver/tick/disconnect callbacks.
- `start(Entity)` / `keepHeld()` / `release()` / `exit()` for original HUD input.
- `isPossessing()` for camera priority; `suppressSpectatorMenu()` for spectator menu interception; `movementMultiplier()` for held walk0.
- `heldTicks(Entity)` for05-34 original possession animation; `reset(Minecraft)` for lifecycle reset.

Root alone edits bootstrap, game-mode/camera/teleport mixins, shared input/camera/HUD, spectator UI, tracking events and test entrypoints. This isolated module does not count as playable until those hooks and the05-36 integrated flow pass. Charge animation is owned by05-34; no surrogate animation is claimed here.

## Validation

Read complete original HideInEntity, IPlayerPossess, player/server mixins, packet and spectator GUI hooks; traced inherited range and game-mode event behavior. Static26.3 bytecode inspection verified modern setCamera's internal dimension-aware teleport and the service scopes its exception accordingly. Scoped whitespace check passed. No Gradle/test/build execution. Root must run the one consolidated lane and report actual discovery/results.
