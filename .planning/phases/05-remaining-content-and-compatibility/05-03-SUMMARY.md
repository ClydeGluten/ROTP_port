---
phase: 05-remaining-content-and-compatibility
plan: 03
subsystem: input
requirements-completed: [HUD-01, BUILD-01, TEST-01]
tags: [fabric, sdl, controls, crash-fix, draft-9.1, gap-closure]
---

# Draft 9.1: world-entry crash hotfix

The user's `crash-2026-10-14_15.47.54-client.txt` failed when world entry restored mouse focus and polled keyboard state. Draft 9 incorrectly used GLFW's old `-1` sentinel for unbound heavy/uppercut keys. Minecraft 26.3 uses SDL; its `InputConstants.UNKNOWN` is 0. Both invalid saved bindings were confirmed in the user's options. This was a port bug, unrelated to earlier Vulkan test failures.

## Implementation

Default registration and the original-controls migration now use `InputConstants.UNKNOWN`. A separate startup repair runs before the completed migration marker check, correcting only invalid heavy/uppercut bindings and retaining custom bindings. Original options are backed up to the game directory's config/jojo/options-before-unbound-key-repair.txt before saving. In-memory key mapping resets before disk operations, keeping the runtime repair effective even when the config directory is unwritable. No global input bounds workaround was added.

## Regression evidence

`ControlsClientGameTest` fails against Draft 9 with the explicit invalid-default assertion, then passes with this repair. It checks default metadata, completed-marker repair, preservation of a custom F7 binding, options persistence/reload, backup existence and direct access to SDL's real keyboard buffer. Earlier client tests mocked native input and rebound both actions before loading a world; this new first suite covers that blind spot.

A normal `runClient` launch, without the game-test mod, used copies of the user's settings/config and newest world. It automatically repaired both bindings, entered the world, rendered normally and exited cleanly. Original user settings/worlds were untouched. Evidence: `/tmp/rotp-draft9-1-native.log`, `/tmp/rotp-draft9-1-native.png`; copies and the pre-fix failing test log are preserved in the versioned bundle.

Final combined command: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./fabric/gradlew -p fabric build runClientGameTest -PacceptMinecraftEula=true --console=plain`. Evidence: `/tmp/rotp-draft9-1-verification3.log`, **BUILD SUCCESSFUL in 2m 22s**. Passed **76 core tests, 82 required server GameTests, 29 model checks and all six client suites**. The two model verifiers passed during packaging.

Intermediate full runs exposed two fixture timing races. Camera checks now wait one tick for the perspective transition before directly assigning fixture angles. Dedicated cleanup checks wait for server disconnect processing before asserting that manifestations are gone. Existing gameplay assertions remain unchanged. An independent read-only review found no concrete regression in the repair or test changes.

Implementation files: `fabric/src/client/java/com/github/standobyte/jojo/fabric/client/OriginalControlsMigration.java`, `fabric/src/client/java/com/github/standobyte/jojo/fabric/client/RipplesClient.java` and `fabric/src/gametest/java/com/github/standobyte/jojo/fabric/ControlsClientGameTest.java`.

## Delivery

Version `0.2.2-fabric.9.1-dev+upstream.0.2.2.2` is packaged at `drafts/latest/`; mod SHA-256: `7282711a5272bb16da6af2c4c961214cb07dd713ad9331e34e51e6ee3a35ed46`. Earlier drafts remain preserved. Bundle checksums and `rotptest --dry-run` pass. `fabric/build/run` is restored to an ordinary directory containing final test evidence; the previous directory remains at `fabric/build/run.disk-before-keyfix`.

Original Forge sources and user worlds under `fabric/run` were untouched. Source/planning changes are staged; no new commit/push. The next ordinary `rotptest` launch repairs the user's existing bindings automatically. Stop for the corrected camera/HUD playtest; no new content milestone started. Broad parity requirements remain open.

## Self-Check: PASSED

Repair, red/green regression coverage, normal native launch evidence, full regression, package and launcher checks are present. User acceptance remains pending.
