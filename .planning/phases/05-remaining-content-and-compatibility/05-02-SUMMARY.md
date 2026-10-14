---
phase: 05-remaining-content-and-compatibility
plan: 02
subsystem: presentation
requirements-completed: [CAMERA-01, HUD-01, BUILD-01, TEST-01]
tags: [fabric, camera, original-hud, controls, draft-9, gap-closure]
---

# Draft 9: camera and original Stand HUD correction

The user rejected Draft 8's custom HUD and jumping F5 camera. This correction replaces both and stops for user playtest before another content milestone.

## Camera

`OrbitRotation` and `StandCamera` independently implement 360-degree orbit and eight-direction camera-relative movement. Idle mouse look does not rotate the body; movement faces travel, while attacks/use synchronize player facing before the server action. Vanilla wall collision and view distance remain. Target distance no longer feeds camera rotation, eliminating the old shoulder convergence jumps. First-person Stand/finger fading and hidden manual-control body remain. Perspective/world/manual transitions reset orbit ownership; returning to first person also resets interpolation history.

Better Third Person's author page describes the requested behavior but labels its code all rights reserved. No code from that mod was copied.

## Original presentation and controls

Supported Stand UI now ports the original `ActionsOverlayGui`, `BarsRenderer`, `HorizontalBarsRenderer`, `HotbarFold`, `HotbarRenderer`, fade helpers and editor behavior. Original assets, top-left coordinates, textured gradient stamina/Resolve bars, ornaments, folding/clipping, names, cooldown wipes, learning, finisher and hold crosshair indicators replace the custom panel. Cooldown totals are synchronized authoritatively for correct wipe proportions. Exact source mappings and remaining adapters are in `fabric/migration/ORIGINAL_HUD_CAMERA.md`.

Original controls: M summon, O held manual control, K mode, V/B held row focus with numbers/wheel, LMB/RMB actions, Shift variants, Alt temporary mouse bypass, backslash editor and H progression. Editor pick/place, cross-row movement, enable/disable and save-on-close preserve per-Stand layouts. One-time default-key migration backs up options and retains custom rebindings. Vanilla's global Friends shortcut required a scoped O-conflict hook.

Mouse Time Stop now requires holding through charge. Release cancels only an unfinished charge; vanilla repeated use cannot immediately resume a newly activated stop. Mapped release events cover release/repress between client ticks. Direct J retains toggle behavior.

The supported Stand overlay is restored, not every original screen: non-Stand powers, arbitrary action-keybind slots, alternate anchors, queue-state brightness and other unported gameplay widgets remain open. The original baked radial atlas adapts the old dynamic quad rendering to current extraction APIs.

## Delegation and verification

Antigravity CLI passed readiness and scratch-write checks, then both task requests hit account quota before edits. Available coding agents completed isolated HUD/control scopes and disjoint camera/HUD tests. Parent reviewed, integrated, corrected and tested the results. No worker completion report substituted for integrated checks.

Final combined command: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./fabric/gradlew -p fabric build runClientGameTest -PacceptMinecraftEula=true --console=plain`. Evidence: `/tmp/rotp-draft9-verification2.log`, **BUILD SUCCESSFUL in 2m 22s**. Passed **76 core tests, 82 required server tests, 29 model checks and five real-client suites**. Coverage includes orbit/target changes, all movement directions, walls, server hit/use/mining, F5/manual/world lifecycle, original stamina pixel widths/drain/refill, row routing, Shift variants, editor persistence, corrupted settings, separate learning and dedicated-server actions. Rendered camera, folded/expanded HUD and editor screenshots were inspected; HUD scales 2/3 passed.

Intermediate failures: runtime window resizing stalled Vulkan, including on the title screen; tests now start at 1280×720. A concurrent compile briefly invalidated class reads, so subsequent builds/tests were serialized. A vanilla notification covered the stamina pixel region; clearing this test notification exposed correct 100px/50px half/quarter bars. No runtime renderer patch or general engine stability fix is claimed.

## Delivery

Version `0.2.2-fabric.9-dev+upstream.0.2.2.2` is packaged at `drafts/latest/`, with dependency, guide, verification log, screenshots, manifest and hashes. Mod SHA-256: `a5b003491a4c1d1c9b3dfdf4695f3e2e1798a9a0bcf6f9cd727c989fb7f1b4b5`. Earlier drafts remain. Launcher dry run and bundle checksums pass. Source/planning changes are staged; no new commit/push.

Original Forge sources and user worlds under `fabric/run` were untouched. `fabric/build/run` is again an ordinary directory containing final evidence; previous test data is preserved separately. GSD plan structure and consistency checks pass. Automated correction complete; **manual playtest pending**. Broad parity requirements remain open.

## Self-Check: PASSED

Implementation, full regression evidence, source mappings, draft package and launcher checks are present. User acceptance remains pending.
