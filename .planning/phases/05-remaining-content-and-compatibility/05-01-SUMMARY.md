---
phase: 05-remaining-content-and-compatibility
plan: 01
subsystem: stands
requirements-completed: [WORLD-01, ASSIGN-01]
tags: [fabric, the-world, progression, awakening, draft-8]
---

# Draft 8: The World and awakening generations

The World is playable alongside Star Platinum with its own entity registration, original texture, geometry, animation clips, sounds, icons and stats. Its 59-part/68-cube model is generated from the read-only original Java model; eight original clips drive idle, melee, kick, barrage, guard and time abilities. Keyframed head look expressions and the kick spin are preserved through interpolated constant/pitch/yaw channels. Both renderers share the tested camera comfort rules.

Shared gameplay now selects stats and progression by owned Stand. The World has five-block effective melee reach, stronger heavy knockback and original partial armor piercing. Its Resolve-1 Kick sweeps visible adjacent targets, disables shields and rotates knockback. Resolve 2 unlocks the 30-tick Time Stop charge and Time Skip; Resolve 3 unlocks Time Stop Punch. Human duration starts at five ticks, trains at 0.1 points per actually stopped tick and caps at 100 ticks. Separate persistent Resolve attachments retain each Stand's learning through replacement and reload.

Time Skip uses server-owned aim, learned duration, speed, stamina and swept full-body collision. Time Stop Punch moves only the Stand, has a five-tick windup, scoped reach checks and a 50-tick ability cooldown. Both reject cross-Stand misuse and invalid state, and validate resource costs before committing movement. Original target-relative/ground blink placement, mob aggro reset, complete tick-skipping/backshot behavior, queued impacts, knives and vampire mechanics remain parity work.

## Awakening allocation

Successful Arrow awakenings now draw from Star Platinum and The World once each per generation, then start the next generation. Joining assigns a tentative random integer but consumes no slot. A colliding preference rerolls from unused IDs on successful awakening. The Overworld stores generation and used stable IDs globally across dimensions. Per-player markers never expire, so death/relogin cannot re-seed old ownership. Existing owners seed at first post-update join; operator grants bypass allocation. New catalog entries fill an unfinished generation. Ordinary save/reload and infection lifecycle are covered; crash recovery between separate world/player saves, offline legacy-owner scanning and configurable pools remain future work.

## Delegation and review

Isolated Antigravity worktrees handled original model generation, server gameplay and pure/server allocation. The first model worker reached its Claude quota after writing partial assets; verified Gemini workers completed the other scopes. Parent inspected all changed files and independently corrected head expression binding, body-swept travel, scaled stamina, shared cooldowns, time-punch costs/bounds, non-expiring migration markers, command-grant behavior, test batching and assertions. Worker-added tests were explicitly registered and run in the integrated project.

## Test evidence

Final command: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./gradlew build runClientGameTest -PacceptMinecraftEula=true --console=plain` from `fabric/`. Evidence: `/tmp/rotp-draft8-verification5.log`, BUILD SUCCESSFUL in 2m 7s. Passed 63 core tests, 82 required server GameTests (81 mod plus framework), 24 Star Platinum model checks, five The World model checks and five real-client suites. Original numeric geometry/keyframes and deterministic generation are checked independently. Real clients exercise The World renderer, charging/cooldown, separate learning/reload, dedicated Kick/Time Skip/Time Stop Punch keys and old camera/combat/acquisition regressions.

Earlier test runs encountered intermittent engine/harness errors around world transitions and a Vulkan GUI timeout. Focused new-client and final full runs completed. Their cause is not conclusively established; manual world-switch stress testing remains pending. Thread ownership checks guard Stand removal, and no vanilla engine workaround is shipped.

## Delivery

Combined HUD/The World version: `0.2.2-fabric.8-dev+upstream.0.2.2.2`. Stable bundle path: `drafts/latest/`. JAR SHA-256: `608387ffa5b466ead4ef69d77bf7fc153496bdd5afa1776871fd6751048435e0`; dependency SHA-256: `8626c189b702f466800b1c8ddac6203cf8a1e0bdaee75a5f9a33b18c2baa81b1`. Previous drafts and user playtest worlds remain preserved. `rotptest --dry-run` passes. Source/planning changes are staged; this milestone has no new commit/push. Manual playtest is pending. Broad PARITY-03 remains open.

## Self-check

Passed. WORLD-01 and the normal-play ASSIGN-01 distribution rule are implemented and verified. The approved HUD and second Stand are packaged together; broader original-content and crash-recovery work remains tracked separately.
