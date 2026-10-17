# Remaining original-mod port — context

## User authorization (2026-10-18)

The user accepted Draft 16.1 with “work pretty good” and requested: “reverse engineer and adapt the rest of the mod”. Continue through remaining active original content, including gaps in already playable systems. Do not stop after planning or call a partial slice the complete port.

## Locked decisions

- Original Forge 1.16.5 source at commit 72862a5826ed45d1dc26e90b37853097acda35de is authoritative and read-only. Preserve original methods, formulas, assets, acquisition and interaction rules; adapt only platform integration. Dormant/TODO original content is not invented.
- Stand allocation generations and independent third-person orbit remain explicit user exceptions. Extend the natural pool only when a Stand is playable; non-rollable NPC/animal Stands retain original acquisition.
- Keep Fabric Minecraft 26.3 / Loader 0.19.5 / API 0.162.0+26.3 / Java 25. Preserve existing saves, prior bundles and all staged work. Stage delivery; do not automatically commit/push.
- Use GSD and concise chat. AntigravityCLI or built-in agents may handle isolated work when this saves time. Root alone runs the build lane. Minimize tests: meaningful integration checks for new behavior, one consolidated build and focused client flow per substantial deliverable; no duplicated worker Gradle runs.
- Read active callers and inheritance as well as registrations. Draft 16.1's fluid crash demonstrated that a feature inventory and flat-floor tests do not prove API adaptation correctness.
- Keep user playable draft available while expanding the port. Package verified intermediate builds as work proceeds; implementation continues through the full authorized scope.

## Required scope

1. Remaining non-Stand powers: zombie-player and Pillar Man, with active source stages/actions/passives, acquisition, equipment, presentation, commands and coexistence.
2. Remaining active Stand types: Hierophant Green, Silver Chariot, Magician's Red, Crazy Diamond, Gold Experience, Boy II Man and Mr President; shared Stand state/action/render/learning infrastructure must stop assuming only SP/TW.
3. Existing Stand/shared-system gaps: Time Stop perception and interaction, barrage clashes/parry, Resolve progression/configuration, shared UI/settings, active acquisition/projectile/enchantment paths.
4. Remaining standalone items/NPCs/world content, structures/dimensions, recipes/loot/advancements/effects, client presentation and original configuration.
5. Define and implement supported save migration and compatibility, including recovery for allocation persistence. Never silently rewrite original saves or promise support without evidence.

## Acceptance and evidence

Maintain a registry/method coverage ledger distinguishing implemented, source-inactive and unresolved. Every active remaining feature must have working registration, reachable gameplay, server authority, persistence where appropriate, original presentation and traceable original method anchors. Verify actual world/entity/action interactions, not only helper methods. Port completion requires closing active gaps across the ledgers; other Stand names or placeholder buttons are not completion.

Current accepted baseline: Draft 16.1, 206 server checks, focused Hamon client flow, 94 unchanged core checks and 29 model checks. These historical results do not validate new features.
