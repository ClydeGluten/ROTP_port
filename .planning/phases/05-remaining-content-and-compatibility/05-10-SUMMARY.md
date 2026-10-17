---
phase: 05-remaining-content-and-compatibility
plan: 10
status: complete
completed: 2026-10-16
requirements: [HAMON-01, HAMON-02]
---
# Hamon progression and learning

Implemented original persistent Strength/Control points, shared fractional gains, breathing level, energy/stability, air and mask behavior, daily exercise progression/bonuses/deterioration, and configuration. All 26 base and 20 character skill definitions and five registered techniques preserve source requirements, slots, perks, resets and dormant definitions. NPC and player teaching, abandonment, original retention/respawn and vampire memory transitions are integrated. Original stat commands and advancements are available.

Eleven focused core regressions exercise formulas, boundaries, learning and immutable state. Source/API mapping: `fabric/migration/HAMON_PROGRESSION_PARITY.md`. Modern attachments and bounded intent packets replace capabilities; server state determines learning, cost and ownership. Source offline-day and mask-at-cap quirks remain intentional.

## Verification and delivery

`/tmp/rotp-draft15-verification8.log`, **BUILD SUCCESSFUL in 53s**. All **197 required server GameTests** passed. The focused Hamon client flow passed in fresh integrated and dedicated worlds. All **91 core tests** pass (unchanged tasks reused); packaging passed **29 model checks**. No unrelated client matrix was repeated.

Bundle: `drafts/0.2.2-fabric.15-dev+upstream.0.2.2.2/`, selected by `drafts/latest`. Mod SHA-256: `f2e40a0d94f4e6846db5cc9624d51a3ab2be534718e901ea65ee19314b56a5e2`. Manual playtest pending. Changes staged locally; HEAD remains `69e4aaa4a` through Draft 11. Broad phase 5 remains open for other powers/content and migration/compatibility.
