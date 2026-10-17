---
phase: 05-remaining-content-and-compatibility
plan: 11
status: complete
completed: 2026-10-16
requirements: [HAMON-03]
---
# Hamon actions and passive effects

Implemented all 31 registered action handlers, original costs/charge/release/cooldowns, overdrives, healing/body manipulation, movement, bubbles, shield, infusion, hypnosis/shock and technique combat. Persistent charged entities, drops, plants and projectiles retain attribution and decay. Added original drop/pickup hooks, liquid-walk synchronization, leap geometry, last-used aura state and Joseph chat prediction. Milk immunity, AI restoration and server held-action leases are integrated.

Twelve focused action regressions cover risky control, cost, effect and charge boundaries. Ledger: `fabric/migration/HAMON_ACTIONS_PARITY.md`. Molotov infusion now uses a real source item/entity and two Sendo wave groups. Hermit Purple countering and other unavailable Stand integrations remain named cross-content dependencies.

## Verification and delivery

`/tmp/rotp-draft15-verification8.log`, **BUILD SUCCESSFUL in 53s**. All **197 required server GameTests** passed. The focused Hamon client flow passed in fresh integrated and dedicated worlds. All **91 core tests** pass (unchanged tasks reused); packaging passed **29 model checks**. No unrelated client matrix was repeated.

Bundle: `drafts/0.2.2-fabric.15-dev+upstream.0.2.2.2/`, selected by `drafts/latest`. Mod SHA-256: `f2e40a0d94f4e6846db5cc9624d51a3ab2be534718e901ea65ee19314b56a5e2`. Manual playtest pending. Changes staged locally; HEAD remains `69e4aaa4a` through Draft 11. Broad phase 5 remains open for other powers/content and migration/compatibility.
