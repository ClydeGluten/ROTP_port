# Project State

## Project Reference

See `.planning/PROJECT.md`, root `AGENTS.md`, and `05-06-SUMMARY.md`.

**Core value:** Playable, tested Fabric drafts with progressive original feature parity.
**Current focus:** User playtest of verified Draft 11 knives and The World volley. User accepted Draft 10.

## Current Position

Phase: 5 of 5 (Remaining Content and Compatibility)
Plan: 6 of 6 in current phase
Status: Draft 11 implemented, verified and packaged. Broad PARITY requirements remain open.
Last activity: 2026-10-14 — Draft 11 final combined build passed 146 server tests and ten client suites; model checks passed.

## Decisions and Constraints

- Project-wide user directive: always prefer preserving original mod behavior. Original Forge sources/assets remain read-only; explicit custom requests, such as awakening generations and independent third-person orbit, remain intentional exceptions.
- Branch `port/fabric-26.3`; private `ClydeGluten/ROTP_port`. User requested GitHub synchronization of progress through Draft 11 on 2026-10-14.
- GSD uses existing-project inline execution. Chat remains concise using caveman. Dependencies and local-server EULA acceptance are authorized.
- Original damage is immediate during Time Stop, including armor, attribution and death. Frozen momentum stacks and begins moving targets on thaw; only The World's specific extra heavy impact sound is deferred.
- Both Stands now have Quick move, including original unsummoned/depleted-stamina use, front/behind targeting, ground/block placement, independent cooldowns, implied clocks and no Blink learning.
- Time Stop Punch supports manual aim, Shift backshot, retained targets and recovery cancellation including barrage. Instant stamina debit precedes ordinary heavy cost at impact; its own cooldown is zero in Creative. Resolve multiplies truncated learning by four.
- First manual-resume click leaves eleven ticks; another resumes immediately. The cue starts at ten ticks remaining.
- M summon; O held manual; K mode; V/B held row focus with numbers/wheel; LMB/RMB actions; Alt bypass; backslash editor; H progression. Shift selects heavy/finisher, either Stand Quick move, or Time Stop Punch backshot. U/I remain direct shortcuts.
- Independent F5 orbit, camera-relative movement, original supported HUD/editor, saved per-Stand layouts, settings migration and native unbound-key repair remain.
- Network relocation retains vanilla packet baselines. Frozen corrections preserve player velocity. Disconnect cleanup is marshaled to the server thread; deterministic Netty-style and real-client tests cover the race.
- Antigravity was not retried after prior quota failures. Existing coding agents handled isolated audits/tests/client work; parent integrated and verified.
- Scope/source mapping: `fabric/migration/KNIFE_PARITY.md`, `TIME_STOP_PARITY.md`, `ORIGINAL_HUD_CAMERA.md`, `STATUS.md`. Overlapping stops, broader immunity/perception, shaders, vampire durations, full Resolve combat effects, Stone Mask/item tracking, Hamon, other content and allocation crash recovery remain open.

- Knives preserve primary/remaining-stack World volleys, Shift single throws, original Gaussian spread/fixed hit margin/closest collision, cooldowns, fractional damage, recovery, recipe, singleton attributes, tool/dispenser behavior and original assets. Five lifetime Time Stop flight ticks; entity impacts wait for collision on thaw. Frozen knife rendering uses the current endpoint. Player stuck knives preserve count/decay and seeded placement.

## Verification and Playtest

Final command: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./fabric/gradlew -p fabric build runClientGameTest -PacceptMinecraftEula=true --console=plain`.

Evidence: `/tmp/rotp-draft11-verification2.log`, **BUILD SUCCESSFUL in 3m 28s**. Passed **76 core tests, 146 required server GameTests, 29 model checks and ten real-client suites**. Actual singleplayer/dedicated knife input, item models, volleys, stopped-time flight, synchronized/rendered endpoints, deferred hits, thaw motion and stuck-player rendering passed alongside prior camera/HUD/controls/progression/time-action regressions. Three source-identified physics regressions failed before the fixes and pass afterward; evidence is preserved in the bundle. Inventory, frozen-projectile and stuck-player screenshots were inspected.

`rotptest --dry-run` passes. Draft 11 manual playtest remains pending. Original Forge sources and user data under `fabric/run` were untouched.

## Session Continuity

Latest bundle: `drafts/latest/`, version `0.2.2-fabric.11-dev+upstream.0.2.2.2`. Mod SHA-256: `6f2c3ae2b8bea5355dcee7b526779908060a717a1d5c199778286e6cdc408a8b`. Earlier drafts remain preserved. `fabric/build/run` is restored to an ordinary directory containing final logs/screenshots; previous test data remains in `fabric/build/run.disk-before-draft11`. Final temporary evidence remains in `/tmp/rotp-draft11-tests-pgzwathw`. Fish command `rotptest` launches the current development sources. Await this milestone's playtest before starting the next slice. Hamon remains later.
