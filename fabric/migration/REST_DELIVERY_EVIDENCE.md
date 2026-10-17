# Remaining-content delivery evidence

## Draft18

Version: `0.2.2-fabric.18-dev+upstream.0.2.2.2`; packaged at `drafts/latest` before the next batch. Mod SHA-256: `3b76052b729af7bbfb16a99c87ed519f4e799307bbb642ffe692908d975b115c`.

`/tmp/rotp-draft18-verification6.log`: build and258 required server GameTests passed; StandExpansionClientGameTest passed fresh integrated and dedicated connections. Actual V/B mouse controls exercise Silver Chariot light/barrage/armor removal/rapier, Hierophant Emerald Splash, dedicated models and remote first-person hands. A real client joins an already-active180-tick vampire Time Stop and receives the frozen state. The four playable Stands now participate in nonduplicate awakening generations.

Also delivered: source shared afterimages (one original entity registration), terrain history/reconstruction groundwork, configurable Stand progression/stamina/damage/range and Resolve display/persistence. StandConfig tests pass changed settings against real actions; restoration tests exercise loot/XP/materials/creative breaks and chunk save/load. Manual visual review and full-mod parity remain open; shader/perception/clash and later content are separate plans. Original src/ and user saves were untouched by this lane. Screenshots and log are copied into the draft; prior17 remains available.

## Draft17

Version: `0.2.2-fabric.17-dev+upstream.0.2.2.2`. Packaged before subsequent Silver Chariot, Hierophant Green and terrain-history integration. This immutable draft remains available; source launch command `rotptest` builds current sources.

- Mod SHA-256: `5285f96ca2dca945e53755c05fa0fb3071f597608d780c7f8b293132a4b2a72b`.
- Verification: `/tmp/rotp-draft17-verification2.log`, copied into the draft.247 required server GameTests passed; build/core checks passed;29 SP/TW model checks passed. Focused client flow passed fresh integrated and dedicated connections. BUILD SUCCESSFUL in1m30s.
- Delivered source slices: zombie-player progression/actions; Pillar base, body parts, possession, WIND/HEAT/LIGHT; original equipment and temple/fluid/engravings; generalized Stand definitions, registered actions and persistent training/parts/effects/cooldowns; strict original resource/model conversions.
- Actual client input: SP/TW time abilities and frozen vampire victim; zombie devour/disguise; Blade Hat Shift-use and Tommy Gun held fire; Pillar stone, wind cloak, heat charge, light slash and possession with Shift exit. Held modifier fixtures use held keys across ticks.
- Runtime fixes exposed by integration: immutable-set null recovery lookup, legacy engraving TileXYZ decoding and modern recipe-unlock JSON; fixture corrections distinguish engine commonTick aging from overridden tick calls.

## Limits and next work

No claim that the entire mod is ported. SC/HG/restoration and the180-tick late-join client case were subsequently delivered in18. New natural temple placement/config toggle/reload and human presentation review remain open. Generic Stand presentation acceptance awaits new Stand deliveries. Draft18 enables four tested Stands; remaining Stands stay outside the natural pool until their delivery checks pass.

Original Forge source remains read-only. Automation uses `fabric/build/run`; user worlds in `fabric/run` are preserved. Changes are staged without commits or pushes. Previous Draft16.1 remains available. Plan summaries retain their specific incomplete acceptance items instead of treating this successful integration as full-port closure.
