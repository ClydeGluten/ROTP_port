# Pillar Man presentation coverage

Original Forge source is read-only. Fabric targets Minecraft26.3/Java25. This ledger separates active presentation from dormant source behavior and records verification independently of implementation.

## Action catalog and controls

`PillarmanActionCatalog` follows `ModPillarmanActions`, `PillarmanAction.isUnlocked/checkSpecificConditions`, inherited `NonStandAction` costs and `PillarmanPowerType.clAddMissingActions/isActionLegalInHud`.

| Active source action | Source route and presentation |
| --- | --- |
| heavy_punch | Initial left slot; free main hand,10 energy eligibility; continuous original `pillar_man_punch` clip at2x. |
| absorption | Initial quick/middle action, stage2 left addition; held free-hand target intent, source absorption loop. |
| horn_attack | Stage2 left addition, usable in stone/stun;15 cost,60 cooldown. Existing body-part renderer. |
| ribs_blades | Stage2 left addition;60 cost,80 technical cooldown. Existing repeated segment renderer. |
| hide_in_entity | Stage2 right addition;20 charge, separate validated possession intent, original possession clip. |
| stone_form | Initial right slot;40 charge, green selected state while stone, original skin overlay and hidden outer skin. |
| regeneration | Stage2 right addition;40 cost,20 cooldown, permitted in stone/stun. |
| enhanced_senses | Stage2 right addition;.05 per tick, stone/stun permitted; expanding orange outline scan. |
| evasion | Stage2 right addition;1.5 per tick,50 maximum/cooldown; original clip at2x. |
| unnatural_agility | Shift evasion;1 per tick, original clip at1.75x. |
| small_sandstorm | WIND left addition at any stage;20 cost,10 cooldown; existing WIND particle-only projectile renderer. |
| divine_sandstorm | Shift small sandstorm;40 charge then continuous WIND handler; source emote with head/arms aim modifiers. |
| wind_cloak | WIND right addition at any stage;2 per tick,200 maximum,100 cooldown; existing animated cloak body/hand layer. |
| atmospheric_rift | WIND left addition at any stage;40 charge then continuous WIND handler; source head-aim emote and existing particles. |
| erratic_blaze_king | HEAT left addition at any stage;20 cost, original head-aim emote and existing veins. |
| giant_carthwheel_prison | HEAT left addition;125 cost,30 threshold, release to fire,100 cooldown; original emote and existing aura/veins. Original registry spelling retained. |
| self_detonation | HEAT left addition;150 cost,60 threshold, release to fire; original emote and existing heat effects. |
| light_flash | LIGHT right addition at any stage;25 cost,40 charge,80 cooldown, original mirrored/head-aim emote. |
| light_flash_decoy | Shift light flash;40 cost,50 charge,120 cooldown, original mirrored emote and existing rainbow aura. |
| blade_slash | Shift heavy punch in LIGHT; free hand,10 cost; original clip at1.5x and synchronized blades. |
| blade_dash_attack | LIGHT left addition;50 cost,10 threshold and unlimited hold override, original charged clip stops at return tick10 then advances from11 after release. |
| blade_barrage | LIGHT left addition; free hand,1 per tick; original3.25x aimed-arm clip and synchronized blades. |

These22 entries use original `textures/action/pillarman_*.png` icons and `action.jojo.pillarman_*` language keys. The unused `light_flash2` and `light_flash_decoy2` icon files have no active source texture-selection override and are not substituted. Four shift links remain independent of stage4: punch→slash, evasion→agility, small→divine, flash→decoy. Default quick action remains absorption even before stage2 unlock.

`PillarmanClient` exposes shared selection/input callbacks; it sends original base/HEAT/LIGHT payload intents, existing WIND intents and existing possession intents. Starts resolve current shift once, heartbeats every5 ticks, release/cancel follows the originating handler. Closing mode, changing world and opening a screen clear held input. LEAP uses mapped jump+shift. Client availability is advisory; server handlers remain authoritative.

The shared HUD uses original accent `0xFFAA00`, energy data from synchronized PillarmanProgress and original overlay icon at(240,52). Cost display preserves fractional hold costs. Cooldowns come from synchronized base/WIND snapshots and shared resource-ID cooldown attachments. Root owns shared catalog/layout/HUD and input integration.

## Active animation source

The active `KosmX*Handler` classes and inherited `KosmXWindupAttackHandler`, `KosmXKeyframeAnimPlayer`, head/arm/hand-side modifiers and `KosmXFixedFadeModifier` were inspected. `PillarmanPlayerAnimation` reads the unchanged upstream `player_animation/pillarman/*.json` files, including sparse position/rotation/bend channels, source loop ranges, speed multipliers, dominant-arm mirroring, aiming modifiers and cubic release fades. Tracked player actions use the same synchronized state as the local player. Time Stop freezes partial interpolation.

Active clips: pillar_man_punch, pillar_man_possession, evasion, unnatural_agility, divine_sandstorm, atmospheric_rift, erratic_blaze_king, giant_cartwheel_prison, self_detonation, light_flash, light_flash_decoy, blade_slash, blade_dash and blade_barrage. Blade dash uses the actual charge/action phase transition; it is not represented by a static pose. Existing modern bend geometry adapts the original player-animation bend transform.

## Layers, particles and audio

`PillarmanLayers` ports `PillarmanLayer.render/getTexture`, `PillarmanBladesLayer.render/renderBlade` and all eight boxes of `PillarmanBladesModel`. Stone uses `textures/entity/biped/pillarman_layer.png`; blades use `textures/entity/layer/pillarman_blades.png`, the original arm-relative pivots, full light and armor glint. `ClientEventHandler`'s stone outer-skin suppression is applied after vanilla model setup. The generic layer/extraction contract supports humanoid renderer models; the shared root hook installs it once.

`PillarmanPresentation.submitHand` adapts `IFirstPersonHandLayer.defaultRender` for active stone arm/outer-arm texture. The overload with an outer part preserves the source sleeve overlay. First-person aura pseudo-particles follow `CustomParticlesHelper.summonHamonAuraParticlesFirstPerson` and `FirstPersonHamonAura.HamonAuraPseudoParticle`: original per-hand randomized positions, rotations, velocity/friction,25–34 lifetime, growing size, triangular alpha and fullbright additive quads. Green uses11 original frames, red11 and rainbow22. Divine produces1 per hand, Decoy2, Giant1 and Detonation2; Rift's integer intensity/5 produces0. These render only on free hands and leave the inactive first-person blade callback empty.

Enhanced senses ports `PillarmanEnhancedSenses.holdTick` and the exact cuboid `MCUtil.entitiesAround` query: radius min(ticks*.5,36), scans each tick before160 then every20, orange0xFFAA00 outlines retained80 ticks. Client world changes clear previous scans; outline eligibility/color are exposed to root's Minecraft and EntityRenderer hooks. No server Glowing effect is fabricated.

Absorption ports the active held-effect loop using original `pillar_man_absorption`, volume1.25/pitch.8 and client-visible valid-target geometry. Time Stop mutes the loop. The existing dedicated body-part, WIND, HEAT and LIGHT clients still own their projectiles, third-person particles and flash effects. WIND's existing CloakLayer/extraction/hand path is installed separately once; this layer does not duplicate it.

Root-owned `PillarPresentationGameTests.modeTransitionsPublishActualVisualStateAndClearIt` registration runs one actual action-state sequence: stage1 stone charge, rejected LIGHT while stone, accepted LIGHT flash at stage1 with tracked blade/clock state, mode change to WIND and final power cleanup. It checks inputs feeding the renderer; visual drawing remains a separate client check.

## Source-inactive behavior

- `PillarmanBladesLayer.renderHandFirstPerson` is an empty FIXME in original source. Root explicitly confirmed preservation; no new first-person blade geometry is invented.
- `KosmXStoneFormHandler` and the three stone_form clips are registered but have no active caller of `ModPlayerAnimations.stoneForm` in original source. Stone skin and outer-layer hiding are active; the unused pose selector is not activated.
- Evasion's commented afterimage/camera-roll experiments remain dormant.
- The original Flash source still uses Aja beam sound and records replacing it as a TODO. The active sound is preserved by LIGHT gameplay, not redesigned.

## Verification boundary

This ledger records source inspection and implementation. Root owns build, GameTests and first/third-person client captures. The initial catalog/input/HUD/animation batch compiled in root's fourth shared compile. The final layer/outline/hand-particle batch is in the next shared compile; root fixed the pinned PoseStack API to `rotate(Axis.XP, radians)` while integrating it. Existing body-part, possession, WIND, HEAT and LIGHT modules retain their separate ledgers; their registration alone is not proof that the combined presentation works. Current full build/runtime results must be recorded by root before marking this plan verified.
