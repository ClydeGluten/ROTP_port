---
phase: 05-remaining-content-and-compatibility
plan: 32
status: awaiting-integration-verification
requires: [05-23, 05-29, 05-35]
provides: [HEAT vein fans, charged detonation, eaten explosives]
affects: [05-34, 05-36, 05-43]
---

HEAT implementation and exact source/method mapping are recorded in `fabric/migration/PILLAR_HEAT_PARITY.md`.

`PillarHeatActions.initialize/tick/clear/clearAllRuntime` own lifecycle. `erratic/giant/detonation`, `keepHeld`, `release(player,fire)` expose original actions. Synced `state(LivingEntity)` contains phase/ticks. Root appended payload intents without changing old ordinals, installed UseEntityCallback for eating explosives, and integrated shared lifecycle/knockback/renderer hooks. `PillarHeatClient.initialize()` owns render registration and particle ticks. `PillarHeatGameTests` is registered in the consolidated server suite.

No completed-runtime claim: current compile caught/fixed modern Level random access, TNT package and riding signature; broader combined compilation and world/client scenarios remain pending. Original player action animation is05-34; terrain restoration capture is05-43. No new commit or push; no original-source edits or user-save edits.
