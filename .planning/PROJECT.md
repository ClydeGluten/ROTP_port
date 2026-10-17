# Ripples of the Past Fabric Port

## What This Is

A staged migration of Ripples of the Past 0.2.2.2 from Forge 1.16.5 to Fabric and Minecraft 26.3. The original source and assets remain the reference; a separate Fabric project replaces platform integration while retaining reusable gameplay logic.

## Core Value

Deliver playable, tested drafts that progressively restore the original Stand experience.

## Requirements

### Validated

- Star Platinum summon/dismiss, melee, held combat, advanced abilities, stamina, camera comfort and persistence — iterative automated checks and user playtests.
- Java 25 / Fabric 26.3 build and real-client singleplayer/dedicated-server checks.
- Draft 2 barrage/guard, held-input lifecycle, poses, and local packaging — automated verification passed; user playtest accepted.

### Active

- User accepted Draft 16.1 on 2026-10-18 and authorized reverse-engineering/adapting the rest of the original mod.
- Complete all remaining active Stand/non-Stand powers, shared combat/progression/UI, items/NPC/world content, migration and compatibility, preserving original methods and assets.
- Keep verified local drafts available while work continues. Partial deliveries do not close the full-port objective.

### Boundaries

- Dormant registrations and original TODO content remain dormant; do not invent behavior.
- Public release is outside the current task. Private GitHub exists; local staging remains the current handoff policy.

## Context and Constraints

- Project-wide user directive (2026-10-14): always prefer preserving original mod behavior. Consult the original source before changing gameplay, controls or presentation. Explicit user-requested changes, such as Stand allocation generations, remain intentional exceptions. Time Stop applies damage immediately; frozen motion and the original deferred impact sound resume afterward.

- Worktree: `/home/alanno/Documents/Soft/ROTP_port`; Fabric build: `fabric/`.
- User tested Draft 8 and requested a camera/HUD correction before another content milestone.
- Preserve the original Downloads JAR and original Forge code.
- Use concise communication and delegate to Antigravity only when parallel work saves time.
- Dependencies may be installed. Minecraft EULA acceptance is authorized for the local test server.
- GSD Core 1.16.0 minimal profile is installed locally under `.codex/`, excluded from Git. Planning files are tracked.
- This imports an existing, authorized project into GSD; configuration and scope derive from the conversation and verified repository, rather than a new project questionnaire.

## Key Decisions

| Decision | Rationale | Outcome |
| --- | --- | --- |
| Separate Fabric foundation | Forge lifecycle/render/network APIs cannot be mechanically replaced | First draft passed |
| Reuse formulas and assets | Preserve original behavior and identity | Ongoing |
| Star Platinum first | User-selected first playable Stand | Playable |
| The World second plus action HUD | Explicit user-selected milestone | Draft 8; presentation rejected in playtest |
| Original Stand HUD and independent orbit | User requested original UI and Better Third Person behavior | Draft 9; native key crash corrected in Draft 9.1 |
| GSD planning with inline execution and isolated Antigravity tasks | Keep work resumable without duplicating current research | Active |
| Stage rather than commit | Initial Git instruction | Superseded by the 2026-10-13 request to commit and push privately |
| Commit progress to private GitHub | User's explicit 2026-10-13 request | Active: ClydeGluten/ROTP_port, branch port/fabric-26.3 |
