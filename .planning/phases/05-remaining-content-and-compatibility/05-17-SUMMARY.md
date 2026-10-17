---
phase: 05-remaining-content-and-compatibility
plan: 17
status: complete
completed: 2026-10-17
requirements: [PARITY-03, HUD-01, HAMON-04]
---
# Hamon client source corrections

Implemented HSR-05 through HSR-09 after reading the exact original methods in `HamonSkillsTabGui`, `HamonSkillDescBox`, `HamonTechniqueTabGui`, `HamonTechniqueSlotElement`, `KosmXSendoWaveKickHandler` and `MCUtil.getHand`.

`HamonScreen` now captures scrollable description text and scrollbar drags until release, using the original direction, viewport, thumb geometry, background textures and wheel increment. Skill selection resets the tab's vertical scroll; the horizontal origin is already fixed. Prerequisites switch to their owning stat/technique tab, and occupied technique slots select the skill. Locked slots retain the level requirement; only the next unlocked empty slot has the free hint; later unlocked empty slots have no tooltip. Learned skills are consumed only by unlocked slots, matching the original iterator behavior.

`HamonPlayerAnimation` resolves physical left/right item occupancy through the dominant arm before selecting Sendo Wave Kick's original four clips. Existing assets, timing, fade behavior and sampler remain intact.

Extended the existing cohesive `HamonClientGameTest` before changing production behavior. The same integrated/dedicated script now clicks a below-viewport skill, drags its description and thumb, follows prerequisites into both stat tabs, purchases a technique skill and reselects it through its occupied slot, checks slot hints at levels 20/40, and extracts each kick clip under both dominant-arm settings. Temporary player items, action attachment and hand options are restored in `finally`. One extra screenshot records the scrolled description. No independent suite or production testing API was introduced.

At root's request, the same flow includes a small failed-ignition check for the root-owned Molotov prediction correction. With no nearby fire and a temporarily cleared inventory, direct client use returns `FAIL` and retains the original bottle stack; the inventory is restored in `finally`.

## Verification boundary

- `git diff --check` passes for the changed client and test files.
- Cached Minecraft signatures confirm the hand-option, player-arm, render-state and mouse-release APIs used by the changes.
- No Gradle task, test execution, commit or staging was performed by this worker. Root owns the final build and focused integrated/dedicated flow.
- Manual motion/pixel comparison remains pending. Prior Draft 15 test results do not establish verification for these corrections.

Original `src/` remains read-only. Existing staged changes and disjoint workers' edits were preserved. Source nuance retained: short descriptions do not capture clicks; `HamonSkillDescBox.onClick` gates capture on `hasScrolling`.

## Root integration result

`/tmp/rotp-draft16-verification3.log`, **BUILD SUCCESSFUL in 1m 1s**. All **205 required server GameTests** and **94 core tests** passed. The focused `HamonClientGameTest` passed in fresh integrated and dedicated worlds; packaging passed **29 model checks**. No unrelated client matrix was repeated.

Root completed registration and the shared validation after the worker handoff. No separate worker run is claimed. Draft 16 is packaged; manual presentation acceptance remains pending.
