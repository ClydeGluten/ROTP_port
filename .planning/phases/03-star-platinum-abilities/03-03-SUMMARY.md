---
phase: 03-star-platinum-abilities
plan: 03
subsystem: camera
requirements-completed: [CAMERA-01]
tags: [fabric, star-platinum, camera, draft-7]
---

# Draft 7: Stand camera comfort

Fixed the local Stand obstruction reported during the user's Draft 6 playtest. First-person keeps the normal eye position and fades the owned Stand and Star Finger to 16% opacity. First-person manual control hides the Stand body and its finger layer. Rear third-person shifts toward the shoulder opposite the idle Stand, with block clipping and visual aim convergence. Player rotation, physical Stand placement, attack origin and combat reach are unchanged.

The shoulder offset is 1.15 blocks sideways and 0.35 blocks upward from the vanilla camera. Eight corner rays sweep the offset and the player-to-camera path, with a small collision margin. Camera rotation converges on the block or entity along the player's eye ray, excluding the player's own Stand. A local Stand crossing the central view fades even when a wall retracts the camera. Front third-person keeps vanilla camera placement. Sleeping, riding, panoramic and non-player cameras do not receive the shoulder shift. State is derived each frame, so dismissal, perspective changes, manual control and disconnect leave no accumulated offset. Other Stands retain their normal opacity.

## Test evidence

Final command from `fabric/`: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./gradlew build runClientGameTest -PacceptMinecraftEula=true --console=plain`.

Evidence: `/tmp/rotp-draft7-verification4.log`, copied into the versioned bundle. BUILD SUCCESSFUL in 1m 51s. Passed 24 core tests, 67 server GameTests (66 mod plus the framework test), 24 model checks and four real-client suites. Existing core checks were unchanged and Gradle reused their successful results.

The camera suite verifies extracted render opacity and tint, non-owned Stand visibility, unchanged first-person eye position, actual shoulder displacement, wall clipping, block-ray convergence without player rotation, camera-ray intersection with a nearby hostile entity and a real key-driven hit, front-view reset, manual-control body hiding, dismissal and a new world after disconnect. Eight camera screenshots accompany seven progression screenshots. Inspected first-person guard, rear guard, wall clipping, entity aim and manual-control captures.

Intermediate runs exposed an old progression fixture that moved only the server player; it now teleports through the connection and waits for client arrival. The new test fixture also needed an entity ID before renderer extraction. The shoulder test caught a reversed camera-axis assumption, corrected before final verification. No gameplay server code changed. Disposable GameTest storage used RAM to avoid the previously diagnosed NVMe save stall; `fabric/build/run` is restored to an ordinary directory with verified logs and screenshots. User playtest worlds were untouched.

## Roadmap clarification

Added ASSIGN-01 and `04-STAND-ALLOCATION.md`: tentative random integers `1..n`, atomic reservation and permanent ownership when awakening succeeds, no repeated Stand within a generation, and a fresh pool only after exhaustion. Joining, starting an awakening and first summon reserve nothing. Includes the four-Stands/five-players example, persistence, failed/replayed/concurrent awakenings, pool changes and migration acceptance checks. This custom rule remains planned, not implemented in Draft 7.

## Delivery and limits

Version: `0.2.2-fabric.7-dev+upstream.0.2.2.2`.
JAR SHA-256: `17579a29b82615355cd24143a910d14c0d2ea205e67886fa53a0d2840aac47dd`.
Bundle: `drafts/latest/`, with mod, Fabric API, checksums, verification evidence, 15 screenshots and playtest instructions. Draft 6 remains available; both bundles' checksums pass. `rotptest` launches the updated development source through the previously installed fish function and `/etc/bash_scripts/rotptest.sh`.

GSD used the established inline workflow. The camera, rendering and integration tests were tightly coupled, so a new external worker would not shorten this patch. Work is staged without a main-branch commit. Draft 7 user playtest is pending. Adjustable camera settings and compatibility with other camera mods remain untested. Broader PARITY-01/PARITY-02 gaps remain open.

## Self-check

Passed: `fabric/src/client/java/com/github/standobyte/jojo/fabric/client/StandCamera.java`, `fabric/src/gametest/java/com/github/standobyte/jojo/fabric/CameraClientGameTest.java`, `.planning/phases/04-acquisition-and-progression/04-STAND-ALLOCATION.md`, `drafts/latest/manifest.json` and `drafts/latest/verification.log` exist. GSD plan/summary validation, bundle hashes and staged whitespace checks pass.
