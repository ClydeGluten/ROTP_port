---
phase: 05-remaining-content-and-compatibility
plan: 09
status: complete
completed: 2026-10-15
requirements: [VAMP-02, BUILD-01]
---
# Draft 14: vampire parity follow-up

Restored weak-vampire leap input with the original 0.15 strength multiplier, grounded-mount and positive-blood checks, and stunned-player rejection. Restored universal Stun/Freeze applicability and the original creeper fuse reset. Creative players and NoAI mobs reject Stun; cold-immune targets reject Freeze except vampire players. Original applicability also prevents aura refreshing a stunned NoAI mob.

Restored the coffin-sleep advancement, including the original card, criterion and daytime-to-night condition. Entering a coffin alone or skipping night to night does not award it. Cure sounds now respect silent entities. Eye beams use original translucent culling, camera-pitch lighting, side normals and rotated reverse faces.

## Delegation

Actual AntigravityCLI workers were used. Claude Sonnet 5.5 High hit account quota while auditing lifecycle. Gemini 3.8 Flash High completed a renderer/input audit after a timed-out initial print call and a bounded report follow-up. Two renderer findings were confirmed. The suggested sleeve fix was rejected because modern sleeves are already arm children; automatic firing on screen opening was not established as source-equivalent. A subsequent narrow Antigravity edit task timed out without edits, so root applied the confirmed fixes. Logs: `/tmp/rotp-antigravity-draft14/`.

An available isolated worker restored the coffin advancement and silent cure sounds. It preserved original repeated-apple reset, accelerator-loop and nausea quirks. Its report is `/tmp/rotp-draft14-lifecycle-report.md`.

## Verification and delivery

`/tmp/rotp-draft14-verification5.log`, **BUILD SUCCESSFUL in 1m 25s**. All **175 required server GameTests** passed, alongside the focused vampire client suite in fresh singleplayer and dedicated-server worlds. Existing 80 core checks remained up to date; packaging passed 29 model checks. No unrelated client suites were run.

Two server regression cases cover weak leap strength/stun rejection and common effect applicability/creeper fuse behavior. The existing client flow covers actual held Shift+Space input and coffin advancement conditions in both connection modes. Initial runs exposed test-fixture mistakes: an explicit NoAI flag was missing, a 35-tick assertion exceeded the default 20-tick deadline, shared synthetic players invalidated coffin quorum, and leap checks inherited Creative mode and simulated taps that released before movement ticks. These were corrected; production rules were not loosened to satisfy tests.

A final dedicated screenshot was inspected. The target cow obscures the front-view beam capture, so that screenshot does not establish exact beam shading or cosmetic fidelity. Manual playtest remains pending.

Bundle: `drafts/0.2.2-fabric.14-dev+upstream.0.2.2.2/`, selected by `drafts/latest`. JAR SHA-256: `36ce76be72276520456f6cd3857461f170c385078829773bb69c0c38b2d62e74`. Prior drafts are preserved; `rotptest` launches current sources. Original Forge `src/` and user saves under `fabric/run` are unchanged. Changes are staged locally; HEAD remains `69e4aaa4a` through Draft 11, without a new commit/push.

## Remaining work

This closes confirmed vampire implementation gaps, not full original-mod parity. Full Hamon acquisition/training, Pillar Man evolution, separate zombie-player powers, other Stands/world content, broader Time Stop visuals, Forge save migration and compatibility remain open. Manual lifecycle/respawn/zombie-AI checks and command-driven coffin spawn-setting compatibility remain explicit validation edges. GSD phase 5 now has a verification record with `gaps_found` for the broad content requirement; phases 3/4 also retain their broader parity gaps.
