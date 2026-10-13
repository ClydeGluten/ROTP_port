---
phase: 02-combat-draft
plan: 01
subsystem: gameplay
tags: [fabric, barrage, guard, gsd, antigravity]
requirements-completed: [COMBAT-01, COMBAT-02, COMBAT-03, COMBAT-04, BUILD-01, TEST-01]
completed: 2026-10-12
---

# Combat Draft Summary

Draft `0.2.2-fabric.2-dev+upstream.0.2.2.2` adds held barrage (Z), frontal guard (X), guard break, key hints, and a local launcher. It preserves the first draft's summon, melee, stamina, persistence, and original assets.

## Changes

- `HeldCombat` owns duration, deterministic hit-rate accumulation, heartbeat expiry, recovery, and original guard/stamina calculations without Minecraft dependencies.
- Server gameplay owns hit selection, reach/wall checks, costs and damage. Barrage uses a dedicated damage type with the vanilla cooldown-bypass tag instead of changing other entities' damage timers.
- A narrowly scoped Player mixin applies guard after armor/enchantments and before absorption. Transferred Stand damage passes through the same accepted-hit path once; rejected cooldown hits do not consume guard stamina.
- Client input sends start/stop and five-tick heartbeats. The server lease expires after twelve ticks without a refresh. Menus and loss of focus cancel held input. Guard-break locking cannot be bypassed by normal summon toggling.
- Antigravity CLI converted original barrage/block tracks in an isolated worktree. Its seven changed files were reviewed and independently verified before integration. No typed GSD agent execution is claimed.
- `play-draft.sh` launches the development client. `package_draft.py` checks verification evidence, preserves versioned bundles, and updates `drafts/latest` atomically. Draft 1 is retained.
- GSD Core 1.16.0 minimal profile was installed locally and the existing project imported into requirements, roadmap, plans, summaries and verification reports. User instruction to stage rather than commit takes precedence over workflow commit defaults.

## Verification

- `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./gradlew build runClientGameTest -PacceptMinecraftEula=true`: passed; log copied to `fabric/build/draft2-verification.log` and the draft bundle.
- 12 core unit tests, 18 server GameTests (17 mod plus the framework check), 18 model checks.
- Real-client tests cover held barrage beyond the heartbeat lease, release, guard pose/state, dedicated-server menu cancellation, original summon/heavy input, save/reload, and disconnect cleanup.
- Barrage and guard screenshots inspected. Focus-loss behavior is code-reviewed; menu cancellation exercises the shared cancellation path. Manual feel, real window focus switching, and arbitrary mod compatibility remain user/manual checks.
- `bash -n play-draft.sh`, deterministic model regeneration checks, JAR metadata/content inspection, and bundle hashes passed.

## Fixes Found During Verification

Test players needed a client-loaded acknowledgement before Minecraft would accept damage; test actors also needed placement in the loaded GameTest region. A new regression test found and prevented stamina consumption for rejected damage transferred through the Stand.

## Deviations and Remaining Work

Barrage uses deterministic fractional timing rather than randomized extra swings. Parry/clashes, uppercut, fist afterimages, original held-action movement penalties, automatic guarding, and advanced abilities remain unfinished. Time stop, other powers, acquisition, progression, and Forge save migration are later phases. User playtest is pending.

Local bundle: `drafts/latest/`. Main working branch: `port/fabric-26.3`; staged, uncommitted, unpublished.
