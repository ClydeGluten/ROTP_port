# Ripples of the Past Fabric Port

## What This Is

A staged migration of Ripples of the Past 0.2.2.2 from Forge 1.16.5 to Fabric and Minecraft 26.3. The original source and assets remain the reference; a separate Fabric project replaces platform integration while retaining reusable gameplay logic.

## Core Value

Deliver playable, tested drafts that progressively restore the original Stand experience.

## Requirements

### Validated

- Star Platinum summon/dismiss, light and heavy melee, stamina, persistence, original model and texture — first draft automated tests passed; user playtest pending.
- Java 25 / Fabric 26.3 build and real-client singleplayer/dedicated-server checks.
- Draft 2 barrage/guard, held-input lifecycle, poses, and local packaging — automated verification passed; user playtest accepted.

### Active

- User playtest and feedback on the packaged combat draft.
- Remaining Star Platinum combat and advanced abilities, followed by acquisition/progression and broader content parity.

### Out of Scope for the Immediate Draft

- Time stop, additional Stands, acquisition, full progression, and Forge save migration — later phases, not abandoned requirements.
- Public releases — the user has authorized a private GitHub repository for source progress; playable drafts remain local.

## Context and Constraints

- Worktree: `/home/alanno/Documents/Soft/ROTP_port`; Fabric build: `fabric/`.
- User will test the latest build after getting home; prioritize a usable draft.
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
| Star Platinum first | User-selected first playable Stand | Ongoing |
| GSD planning with inline execution and isolated Antigravity tasks | Keep work resumable without duplicating current research | Active |
| Stage rather than commit | Initial Git instruction | Superseded by the 2026-10-13 request to commit and push privately |
| Commit progress to private GitHub | User's explicit 2026-10-13 request | Active: ClydeGluten/ROTP_port, branch port/fabric-26.3 |
