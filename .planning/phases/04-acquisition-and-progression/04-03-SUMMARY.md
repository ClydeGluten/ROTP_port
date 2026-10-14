---
phase: 04-acquisition-and-progression
plan: 03
subsystem: hud
requirements-completed: [HUD-01]
tags: [fabric, hud, action-selection, layouts, draft-8]
---

# Draft 8: action selection and saved layouts

Completed the approved HUD milestone together with The World. Two compact action rows replace the crowded shortcut list. Original icons show locked Resolve levels and authoritative cooldowns; the selected move shows its name and cost/status. Resource bars remain synchronized, and action rows sit above the meters to avoid vanilla chat. The stamina pixel regression remains intact.

Left Alt modifies left/right mouse clicks for the attack/ability row. Alt + wheel selects attacks, Shift + Alt + wheel selects abilities. Vanilla block breaking, item use and hotbar scrolling are intercepted only while these controls are active. Quick clicks are queued so taps between client ticks are retained. Held selections share the existing release/GUI/focus/heartbeat safety behavior. M opens the layout editor; O is reserved by current Minecraft's Friends screen. Direct shortcuts and Minecraft key remapping remain available.

`ActionLayout` validates IDs, removes duplicates/wrong-row entries, preserves ordering and appends new defaults. `ActionLayouts` stores selections/order by stable Stand ID, writes atomically, backs up malformed JSON and falls back to defaults. The editor uses a working copy with keyboard-accessible buttons, reordering, reset, explicit Done save and Escape cancellation. The shared action catalog also supplies progression names and unlock gates.

`StandHudState` publishes transient owner-only snapshots after server simulation. Clients submit action intent only. All costs, ownership, cooldowns, collision and progression remain server-validated.

## Delegation and review

Antigravity's isolated `hud-layout` worktree produced the pure core model and tests. Parent reviewed the files, corrected extreme move arithmetic and invalid-selection fallback, implemented the client editor/input/persistence/HUD and integrated server snapshots. Existing GSD inline execution continues; no worker report substitutes for parent verification.

## Test evidence

Final combined run: `/tmp/rotp-draft8-verification5.log`, BUILD SUCCESSFUL in 2m 7s. Passed 63 core tests, 82 required server GameTests, 29 model checks and all five real-client suites. New client checks cover actual modifier clicks, wheel row changes without vanilla hotbar changes, held release on editor opening, keyboard selection/Done save, saved per-Stand order, malformed-file recovery, server cooldowns and ordinary world reload. Dedicated The World keys exercise the same network/HUD path.

Earlier checks caught wrong texture-blit coordinates and the O/Friends conflict; both were corrected. Intermediate runs also encountered a Vulkan timeout and entity-collection exceptions near world transitions. They did not recur in the final run, and their root cause remains unproven. No engine workaround is included; manual world-switch stress testing remains useful.

## Delivery

Version `0.2.2-fabric.8-dev+upstream.0.2.2.2`; `drafts/latest/` contains the JAR, Fabric API, guide, hashes, test log and screenshots. Mod SHA-256: `608387ffa5b466ead4ef69d77bf7fc153496bdd5afa1776871fd6751048435e0`. The launcher dry run passes. New changes are staged, with no new commit or push in this milestone. Manual playtest is pending. Broader PARITY-02 remains open for acquisition extensions and Resolve presentation/configuration.

## Self-check

Passed. HUD-01 is complete for the approved playable scope. Full build, server/client checks, model verification, bundle generation and launch dry run completed.
