---
phase: 05-remaining-content-and-compatibility
plan: 34
status: awaiting-integrated-verification
completed: false
subsystem: pillar-presentation
tags: [fabric, client, pillarman, rendering, original-animation]
requires: [05-23, 05-29, 05-30, 05-31, 05-32, 05-33]
provides: [pillar-action-catalog, pillar-input-hud, pillar-player-animation, pillar-stone-blades-senses]
affects: [05-36, 05-56]
tech-stack:
  added: []
  patterns: [tracking-attachment-render-extraction, original-emote-sampling, bounded-client-intent]
key-files:
  created: [PillarmanClient.java, PillarmanHud.java, PillarmanActionCatalog.java, PillarmanPresentation.java, PillarmanPlayerAnimation.java, PillarmanLayers.java, PillarPresentationGameTests.java, PILLAR_PRESENTATION_PARITY.md]
  modified: []
key-decisions:
  - All22 source actions and four shifts use existing authoritative module handlers and source mode gates independent of stage4.
  - Reuse unchanged source emote/texture/sound files and modernize render submission only.
  - Preserve source-inactive first-person blades and uncalled stone-form pose handler, confirmed by root.
actuals:
  tokens: 14636
  tasks: 2
  commits: 0
plan_head_before: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
plan_head_after: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
---

# Phase 05 Plan 34: Original Pillar Man presentation

All22 active Pillar actions now have source catalog metadata, shift selection, held input routing and HUD state. Original active player emotes, stone skin, animated-arm blades, senses outlines, absorption sound and hand aura particles use synchronized server state. Root has connected shared input/HUD/render hooks; full integrated verification remains pending.

## Tasks and source coverage

1. **Catalog and controls implemented.** `ModPillarmanActions`, `PillarmanPowerType.clAddMissingActions/isActionLegalInHud`, inherited `PillarmanAction`/`NonStandAction` cost and condition methods → `PillarmanActionCatalog`, `PillarmanClient`, `PillarmanHud`. Source initial Heavy Punch/Stone Form/Absorption quick action, stage2 additions, mode additions independent of stage4, all four shifts, source icons/language/color, fractional costs, cooldown/charge state and release versus cancellation.
2. **Render presentation implemented.** `PillarmanLayer`, `PillarmanBladesLayer`, `PillarmanBladesModel`, `ClientEventHandler` stone outer-skin hiding, `PillarmanEnhancedSenses` and `MCUtil.entitiesAround`, `PillarmanAbsorption.onHoldTickClientEffect`, all active Pillar KosmX handlers and inherited timing/modifier classes → `PillarmanLayers`, `PillarmanPresentation`, `PillarmanPlayerAnimation`. Original14 active clip assets, speed/mirror/aim/loop/fade behavior, blade arm bend anchor, modern immutable render-state extraction and source particle quad motion.

Full action-by-action method/asset mapping and dormant-source distinctions are in `fabric/migration/PILLAR_PRESENTATION_PARITY.md`. No original source or assets changed. Existing build resource copying supplies original icons, sounds, all Pillar animation JSON, stone/blade/cloak textures and aura frames.

## Public integration contracts

`PillarmanClient.POWER_ID = "jojo:pillarman"`. Shared UI uses `state`, `hud`, `ownsPower`, `playable`, `isMode`/`active`, `toggle`, `close`, `suspendInput`, `attacks`, `abilities`, `defaultMiddleAction`, `move`, `displayMove`, `unlocked`, `available`, `heldAction`, `chargeTicks/chargeDuration`, `cooldown/cooldownTotal`, `greenSelection`.

Input entrypoints: `interceptClick(boolean attack)`, `interceptMiddle()`, `capturesAttackHeld()`, `releaseMappedKey(InputConstants.Key)`, `tick(Minecraft, boolean active)` and `leapInput(Input)`. Empty selected slots return control safely. Root routes shared J/mouse/mapped release and leap input here. Starts select the current source Shift replacement once; ongoing input retains that action until release/cancel. Base, HEAT and LIGHT use appended `PillarmanActionPayload` enum intents; WIND and possession use their existing dedicated channels. No client data grants stage/mode, energy, targets, damage or action clocks.

`PillarmanHud.COLOR = 0xFFAA00`; `render(GuiGraphicsExtractor, x, y, partial)` returns shared bar height. `name`, `powerName`, `costText` and `rejected` support shared labels/feedback. `PillarmanActionCatalog.all/get/resolve/attacks/abilities` expose all22 definitions without hard-coded shared enum edits. Dynamic WIND cost becomes3/tick after40; other fractional costs stay exact in costText.

Render entrypoints:

- `PillarmanPresentation.initialize()` installs only local presentation ticking; `extract(Avatar, AvatarRenderState, float)` reads original active animation phases; `apply(PlayerModel, AvatarRenderState)` applies source poses and stone outer-skin suppression after vanilla setup.
- `PillarmanLayers<S extends HumanoidRenderState, M extends HumanoidModel<S>>(RenderLayerParent<S,M>)` contains stone/blades only. `extract(LivingEntity, HumanoidRenderState)` captures data for player and other humanoid renderers. Root installs this layer once globally.
- `PillarmanPresentation.glowing(Entity)` and `applyOutline(Entity, EntityRenderState)` supply source senses eligibility and orange color to root's Minecraft/EntityRenderer hooks. Outlines retain80 ticks and clear on client world change.
- `PillarmanPresentation.submitHand(PoseStack, SubmitNodeCollector, ModelPart arm, int light)` renders active stone arm overlay and source pseudo-particles. The overload with `ModelPart outer` before light also renders the source outer-arm stone overlay. Root's existing hand hook can supply both parts.
- WIND remains separate: existing `PillarWindClient.CloakLayer`, `extract`, `submitHand` are hooked once by root. Body-part/possession/HEAT/LIGHT renderer/particle ownership remains with those modules.

The renderer uses all tracked players' attachments, not just the local selection. Full LIGHT invisibility is a renderer-wide root hook; ordinary invisibility retains source blade/cloak behavior. Time Stop freezes pose interpolation/hand particles and mutes the absorption loop.

## Source-inactive paths

Root confirmed preserving original `PillarmanBladesLayer.renderHandFirstPerson`'s empty callback. This is not an implementation placeholder: the original explicitly leaves it unfinished. Likewise, `KosmXStoneFormHandler` has no active caller despite its registered layer and three clip files, so no stone idle pose was invented. Evasion afterimages/camera roll remain commented source experiments. These do not block the active-source scope.

## Verification evidence and remaining boundary

The first four client files (catalog/input/HUD/animation) passed root's fourth main/client compilation. Root then integrated final layer/outline/hand-particle files and corrected the pinned PoseStack API to `rotate(Axis.XP, radians)`; the final combined build is root-owned and running at handoff. `git diff --check` passes for assigned files. No TODO/FIXME/placeholder implementation remains in them.

`PillarPresentationGameTests.modeTransitionsPublishActualVisualStateAndClearIt` supplies one real-player sequence: actual40-tick stone activation, rejection of LIGHT while stone, accepted stage1 LIGHT flash with tracked blades/clock, mode change to WIND below stage4 and power cleanup clearing presentation. This checks renderer inputs and lifecycle; it cannot prove actual pixels or remote-client drawing. Root registers and runs it with the existing module tests and focused integrated/dedicated client sequence. No runtime pass is claimed here.

Required focused client observations remain: source stone hands/hidden outer skin, both arm blades following the actual animation, WIND cloak and hand effects, HEAT charge emote/veins, LIGHT slash/flash/dash release, possession entry/exit, and senses' expanding orange outlines. Root owns current-build local/remote captures and final verification status.

## Deviations and trust boundary

The explicit root/plan contract requires stage-only delivery and one shared build lane. No commits, Gradle runs, user-save edits or global planning state changes were performed by this worker. Root owns shared input, visibility, bootstrap, mixins and test registration. Root also published shared HUD helper visibility and moved horn/rib clocks to synchronized identifier cooldowns to support accurate modular UI.

T-05-234 is enforced by reading server-owned attachments and forwarding bounded existing intents. Selections do not authorize server effects; stage/mode, stone, item, cooldown, target and resource checks remain in existing services. Render maps/outline/particle lists clear on client world changes. No dependencies or version pins changed.

## Self-Check: PASSED

All eight assigned implementation/test/ledger files exist. Source hygiene passed and file ownership is bounded. Verification is explicitly pending root's current integrated results; no commit or full-port completion is claimed.
