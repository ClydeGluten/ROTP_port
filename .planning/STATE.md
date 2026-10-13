# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-12)

**Core value:** Playable, tested Fabric drafts with progressive original feature parity.
**Current focus:** Draft 7 camera comfort playtest; remaining action UI, Star Platinum parity and planned Stand allocation generations.

## Current Position

Phase: 3 of 5 (Star Platinum Abilities; camera feedback follow-up)
Plan: 3 of 3 in current phase
Status: Draft 7 camera fix verified; PARITY-01 and PARITY-02 retain broader gaps; ASSIGN-01 planned
Last activity: 2026-10-13 — Draft 7 verified; user authorized committing current progress and pushing it to a private GitHub repository.

## Decisions and Constraints

- Work branch remains `port/fabric-26.3`. The user authorized committing and pushing current progress on 2026-10-13. Destination: private `ClydeGluten/ROTP_port`; preserve the original project as the `upstream` remote. This supersedes the earlier stage-only instruction.
- Original Forge source remains the read-only reference.
- GSD uses the existing-project inline adaptation; no typed GSD agent orchestration is claimed.
- Latest Antigravity scopes: original progression audit and one-file progression screen, separate worktrees, Claude Sonnet 5.5 Medium. Parent independently reviewed, corrected and tested integration.
- Survival unlocks Uppercut/Inhale/Star Finger/Time Stop at Resolve 1/2/3/4. Existing Fabric ownership/development is retained, but Survival gates apply to prior draft users. Creative temporarily bypasses gates and trained duration.
- Levels and learning survive death, clear/regrant and reload; death clears combat meter but preserves its peak. Operator-only explicit reset clears progression without removing ownership.
- Time Stop learns from actual stopped ticks, starts at five ticks and reaches 100 at 95 points. Original cost scaling and cooldown bonuses are restored.
- Only Star Platinum progression is in this milestone. Hamon remains separate and unported.
- Full action selection, souls/Resolve presentation/configuration, acquisition extensions and broader combat parity remain open.
- User confirmed obstruction in both first-person and third-person. Draft 7 keeps player rays/reach intact, fades the local body/finger, clips a rear shoulder offset and hides the body during first-person manual control.
- Stand allocation is roadmap-only: tentative random `1..n` candidates reserve nothing. Successful awakening permanently reserves an unused Stand in the current generation. Exhaust the pool before starting the next generation. The user's successful-awakening clarification overrides their earlier first-summon wording.

## Verification and Playtest

Passed: 24 core tests, 67 server GameTests, 24 model checks and four real-client suites, including shoulder collision/aim and camera lifecycle. Eight camera and seven progression screenshots accompany Draft 7. Drafts 2 and 3 were accepted; the user playtested Draft 6 and reported camera obstruction. Draft 7 manual playtest is pending. Latest bundle is `drafts/latest/`; previous drafts remain available. Verification used temporary RAM storage to avoid the earlier NVMe test-world save issue, then restored an ordinary `fabric/build/run` directory. User playtest worlds were untouched.

## Session Continuity

Read `03-03-SUMMARY.md`, `04-STAND-ALLOCATION.md` and `fabric/migration/STATUS.md`. Remaining Phase 4 work includes the original customizable ability UI, acquisition extensions and ASSIGN-01. Phase 3 retains clashes/parry, afterimages, time-stop blink and presentation gaps. Do not equate this milestone with full parity or implemented allocation. The private source checkpoint is tracked on `origin/port/fabric-26.3`; inspect Git history for its commit. Historical milestone reports describe the earlier stage-only workflow. Release log: `/tmp/rotp-draft7-verification4.log`, copied into Draft 7. Draft 7 JAR SHA-256 is recorded in its manifest and summary. The fish command `rotptest` starts the updated development client from any directory.
