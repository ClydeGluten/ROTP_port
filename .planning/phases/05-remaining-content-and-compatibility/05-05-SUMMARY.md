---
phase: 05-remaining-content-and-compatibility
plan: 05
subsystem: time-stop-combat
requirements-completed: [BUILD-01, TEST-01]
tags: [fabric, time-stop, time-skip, the-world, multiplayer, draft-10]
---

# Draft 10: original time actions

The user accepted Draft 9.2 and authorized the next Time Stop milestone. Source inspection corrected the earlier delayed-damage proposal: the original applies damage immediately. The user explicitly made original behavior the project-wide default; this now persists in root `AGENTS.md` and `.planning/PROJECT.md`. Explicit requested exceptions such as Stand allocation generations remain.

## Implementation

Frozen knockback now follows `DamageUtil.applyKnockbackStack`: resistance once, no horizontal damping, capped vertical impulse even while airborne. Health, armor, kill credit and death remain immediate, and thaw never replays damage or adds a second impulse. Entity-local callbacks defer only The World's extra normal-heavy impact sound and discard removed targets. Frozen players still receive immediate health updates; rejected movement corrections preserve velocity on both ends.

Both Stands support original Quick move, including unsummoned/depleted-stamina use. Star Platinum stops in front of an acquired target; The World goes behind its facing. Original block top/face, ground projection, aggro clearing, cost ordering, independent cooldown and no-learning behavior are retained. User/Stand clocks advance without simulating world ticks. Network relocation preserves vanilla's relative-position baseline and suppresses interpolation briefly; explicit turns update the independent camera, and manual-control anchors move with Blink.

The World Time Stop Punch now supports manual aim, Shift backshot, retained targets and cancellation of another action's recovery, including barrage recovery. It uses five real windup ticks, separate implied travel/windup, an instant 90-plus-ticking debit and ordinary 50 stamina at impact. Its own cooldown is 50 ticks, zero in Creative. Learning truncates before Resolve's fourfold multiplier. First manual resume click leaves eleven ticks; a second resumes immediately, with the original resume cue at ten ticks remaining. HUD variants and separate cooldown indicators use original assets/names.

Safe full-body collision, loaded-chunk, height, border and current range/PvP checks remain. The original low-budget Time Punch distance precedence bug is bounded rather than reproduced. Exact mapping and remaining scope are in `fabric/migration/TIME_STOP_PARITY.md`. Broad parity requirements remain open, including overlapping stops, broader immunity/perception, shaders, knives and Hamon.

## Regression findings

- First combined run passed 122 server tests but exposed a real disconnect race. Fabric can fire DISCONNECT from Netty; removing the Stand map entry before a cross-thread discard guard left an orphan. Both server listeners now schedule cleanup on the server thread, and client maps reset on the client thread. A deterministic worker-thread event test verifies physical removal, cancellation, callback thread and idempotence. The existing real disconnect assertion remains.
- Review caught manual-control anchoring being skipped during Time Punch. Only Stand navigation is now suppressed; an airborne-owner regression covers anchoring.
- Review found custom relocation incorrectly resetting the client packet codec while the server kept its old baseline. Removing that reset prevents double displacement on short relative updates. Real Blink and Time Punch position comparisons cover integrated and dedicated servers.
- The old HUD fixture expected a Creative Time Punch cooldown; it now asserts the original zero cooldown and observes the action directly.
- The new real frozen-player fixture reproduced erased momentum when rejecting a movement packet. Coordinate-only teleport zeroed velocity; the correction now sends the stored position and velocity explicitly. Both ends are checked before thaw, then actual movement is verified afterward.

Original Forge sources and user worlds/settings in `fabric/run` remain untouched. Independent agents handled source audits and disjoint tests/client changes; Antigravity was not retried after prior account-quota failures.

## Verification and delivery

Final combined command: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./fabric/gradlew -p fabric build runClientGameTest -PacceptMinecraftEula=true --console=plain`. Evidence: `/tmp/rotp-draft10-verification5.log`, **BUILD SUCCESSFUL in 2m 59s**. Passed **76 core tests, 124 required server GameTests, 29 model checks and all nine real-client suites**. Both integrated and dedicated paths pass. HUD/backshot and dedicated frozen-player screenshots were visually inspected.

Version `0.2.2-fabric.10-dev+upstream.0.2.2.2` is packaged at `drafts/latest/`; mod SHA-256: `b797671ba3760bc674c61ce05d241d4851b09f73513fdc0556e51642cacfa959`. Guide, final verification/model logs, pre-fix failure evidence, screenshots and checksums are included. Launcher dry run passes. Source/planning changes are staged; no new commit/push. Earlier drafts remain preserved.

`fabric/build/run` is restored to an ordinary directory with final evidence; its previous data remains at `fabric/build/run.disk-before-draft10`. User worlds/settings in `fabric/run` and original Forge sources were untouched. Run `rotptest`. User subsequently accepted the manual playtest and authorized Draft 11 knives. Hamon remains later.

## Self-Check: PASSED

Implementation, source mapping, regression evidence, server and real-client checks, original models, packaged artifact and launcher verification are complete.
