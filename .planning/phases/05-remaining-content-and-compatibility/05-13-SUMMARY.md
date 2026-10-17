---
phase: 05-remaining-content-and-compatibility
plan: 13
status: complete
completed: 2026-10-16
requirements: [HAMON-05]
---
# Hamon equipment, inheritance and world content

Added original mask, gloves/bubble gloves, soap, oil, clackers and Molotov item behavior/recipes, modern anvil/enchantment adapters, stationary Hamon Master, temple templates and terrain placement, cartographer map purchase conditions/marker/sound, four-seat leaf glider, Crimson Bubble/Deep Pass and dormant Cheat Death effect. Inheritance runs after death cancellation resolves but before vanilla drops; actual merchant purchase triggers its deferred visual/sound. Original advancements and logo geometry are restored.

Seven content, two lifecycle and one glove-anvil server checks cover natural learning/vampire memory, ammo, damage/scarf, leaf energy, inherited points/items, original structure loading/layout, infused Molotov waves, canceled death and resource fallback. Source mapping: `fabric/migration/HAMON_CONTENT_PARITY.md`. Legacy template data uses Minecraft data fixing; server-driven glider input replaces client vehicle authority. Source-commented foods/swords remain unregistered.

## Verification and delivery

`/tmp/rotp-draft15-verification8.log`, **BUILD SUCCESSFUL in 53s**. All **197 required server GameTests** passed. The focused Hamon client flow passed in fresh integrated and dedicated worlds. All **91 core tests** pass (unchanged tasks reused); packaging passed **29 model checks**. No unrelated client matrix was repeated.

Bundle: `drafts/0.2.2-fabric.15-dev+upstream.0.2.2.2/`, selected by `drafts/latest`. Mod SHA-256: `f2e40a0d94f4e6846db5cc9624d51a3ab2be534718e901ea65ee19314b56a5e2`. Manual playtest pending. Changes staged locally; HEAD remains `69e4aaa4a` through Draft 11. Broad phase 5 remains open for other powers/content and migration/compatibility.
