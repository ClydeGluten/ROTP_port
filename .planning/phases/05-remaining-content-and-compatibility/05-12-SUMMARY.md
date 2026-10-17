---
phase: 05-remaining-content-and-compatibility
plan: 12
status: complete
completed: 2026-10-16
requirements: [HAMON-04]
---
# Original Hamon UI and presentation

Implemented the original 230×227 Hamon window, introduction, Stats/exercises, both skill trees, five technique choices, teacher feedback, original sprites and descriptions, scrolling, confirmation/reset controls, and separate Stand overview. H opens skills, J switches non-Stand mode, middle mouse breathes, and existing original action rows/editor support Hamon. Added original entities/equipment, aura families, wall-climbing cycle/contact methods, barrage arm trails, protection geometry, Sendo planes/sounds and breathing/mask animation through modern renderer adapters.

Runtime inspection fixed modern left-button numbering, a nested-scissor leak, missing item-atlas sprites and the original broken breath-file reference. Exact legacy egg/map sprites and breath audio were recovered from official 1.16.5 resources. The focused real-client flow learns from a teacher, buys Zoom Punch, picks Jonathan, holds/releases breathing, cancels on menu entry, renders entities/mask and switches Stand modes on both connection types. Screenshots were inspected; manual motion/pixel/feel acceptance remains pending. Ledger: `fabric/migration/HAMON_CLIENT_PARITY.md`.

## Verification and delivery

`/tmp/rotp-draft15-verification8.log`, **BUILD SUCCESSFUL in 53s**. All **197 required server GameTests** passed. The focused Hamon client flow passed in fresh integrated and dedicated worlds. All **91 core tests** pass (unchanged tasks reused); packaging passed **29 model checks**. No unrelated client matrix was repeated.

Bundle: `drafts/0.2.2-fabric.15-dev+upstream.0.2.2.2/`, selected by `drafts/latest`. Mod SHA-256: `f2e40a0d94f4e6846db5cc9624d51a3ab2be534718e901ea65ee19314b56a5e2`. Manual playtest pending. Changes staged locally; HEAD remains `69e4aaa4a` through Draft 11. Broad phase 5 remains open for other powers/content and migration/compatibility.
