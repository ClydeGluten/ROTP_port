# Pillar Man parity ledger

Authoritative source: original Forge tree at `72862a5826ed45d1dc26e90b37853097acda35de`, read-only. This ledger covers the bounded 05-23 implementation; it is **not a full Pillar parity claim**. Status on 2026-10-18: source implemented, pending root registration/build/world checks. None of the new world cases has run in the worker lane.

## State, physiology and integration

Original paths below are relative to `src/main/java/com/github/standobyte/jojo/`; Fabric classes are under `fabric/src/main/java/com/github/standobyte/jojo/fabric/` unless noted.

| Original method inspected | Fabric implementation | Evidence/status |
|---|---|---|
| `power/impl/nonstand/type/pillarman/PillarmanData.updatePillarmanBuffs`, `onClear`, `onPowerGiven` | `PillarmanService.grant/updateAttributes/clear`, core `PillarmanProgress.attributeLevel` | L=2/4/5/6; +L attack, +.15L attack speed, +.01L movement, +10L health. Removed Forge swim attribute adapted through `swimmingAcceleration` (+.15L multiplier). Root wires swim mixin. World assertions supplied, unrun. |
| `PillarmanData.writeNBT/readNBT`, `NonStandPower.writeNBT/readNBT/keepPower` | `PillarmanAttachments.CODEC/PROGRESS`, `PillarmanProgress` | Persistent stage/mode/stone/energy; default no power for absent old attachments. Reject invalid stages/nonfinite resource. Actual entity save/load and respawn test supplied. Does not claim automatic Forge-save conversion. |
| `PillarmanData.tick/setBladesVisible/addEatenTntFuse` | `PillarmanService.tick/resetTransient/addEatenTntFuse`, `BLADES` | Stone STUN20, Resistance40 amp3, Blindness40, Fire Resistance40. Blades and fuse queue transient; fuses make quiet explosion sound only. Death clears stone/blades. Root must call lifecycle hooks. |
| `PillarmanData.setEvolutionStage/setMode` | `PillarmanService.setStage/setMode` | Stage3 command accessible; evolution and Aja criteria fire at stage3, matching the original quirk. Modes permitted below4. Original `pillarman_evolve`, `pillarman_evolve_aja`, mode criteria registered. Root's acquisition trigger/command integration remains external. |
| `potion/BleedingEffect.applyStoneMask`; `command/JojoPowerCommand.givePower`, `PillarmanModeCommand.setStage/setMode` | `PillarmanService.grant/applyMask/setStage/setMode` | Baseline admin grant only; normal mask1→2; Aja<4→4, uniform WIND/HEAT/LIGHT; Peaceful denied. Root owns mask item activation and non-Pillar Aja lethality. |
| `PillarmanPowerType.getMaxEnergy/tickEnergy/tickUser/getPassiveEffectLevel` | `PillarmanService.maximumEnergy/tick`, `PillarmanProgress.regenerationAmplifier` | Stage×1000×difficulty capacity; stage×configured decay, creative exempt. Stage>1 food17/full air/Night Vision0; regen1 at30%, regen0 at10%. Passive refresh follows energy/stage changes, including source removal behavior. |
| `PillarmanPowerType.keepOnDeath/isHighLifeForce/getMaxStaminaFactor/getStaminaRegenFactor/getTargetResolveMultiplier` | `copyAfterRespawn/highLifeForce/staminaFactor/targetResolveMultiplier` | Power retained on death; stage factors; stage>1 life force. Root supplies shared Stand/Time Stop hooks. |
| `PillarmanPowerType.getLeapStrength/getLeapCooldownPeriod/getLeapEnergyCost` and active `VampireCombatService.leap` platform adapter | `PillarmanBaseActions.leap` | `(2+min(stage,2.25)/2)*.6`, free,20t cooldown, original grounded/upward aim mechanics. Leap landing/presentation uses later presentation work. |
| `VampirismUtil.consumeEnergyOnHeal`; food caller in `GameplayEventHandler.onFoodEaten` | `PillarmanService.consumeEnergyOnHeal/onFoodEaten` | Stage>1 healing pays configured energy/HP; food adds nutrition×10. Sun Burn blocks healing. Root supplies shared mixins. |

## All 22 active registered actions

All registrations and builder settings in `init/power/non_stand/pillarman/ModPillarmanActions` inspected. `action/non_stand/PillarmanAction.checkConditions/isUnlocked` establishes stage/mode and stone gates. “Implemented” means source implementation exists, not that integrated runtime verification passed.

| Registry suffix (`pillarman_`) | Original method(s) | Fabric / owner | Status |
|---|---|---|---|
| heavy_punch | `PillarmanHeavyPunch.Instance.playerTick`, `consumeEnergy`; `VampirismClawLacerate.punchPerform/getDamage/blockDestroy` | `PillarmanBaseActions.heavyPunch/tick/punchHit/destroyBlock` | Implemented: eligibility10/no spend, swing3/hit5/end8, walk.5, attack+4, knockback2/collision, stage>1 seven-block footprint. World case unrun. |
| absorption | `PillarmanAbsorption.checkTarget/checkSpecificConditions/holdTick/absorb/heldAllowsOtherAction`; `DamageUtil.dealPillarmanAbsorptionDamage` | `PillarmanBaseActions.absorb/startHeld/tick` | Implemented: stage2, free hand, stone/stun allowed, living targets including undead, range2, normal immunity, generic armor-applying damage2, cumulative40t amp1 debuffs, +35×difficulty, Hamon contact defense, allows another instant action. Root supplies damage resource/contact wrapper. |
| stone_form | `PillarmanStoneForm.perform` | `PillarmanBaseActions.startHeld/tick` | Implemented: stage1, charge40, walk.5, stone/stun allowed, toggle and hide blades. Root shared Hamon/UV immunity required. |
| regeneration | `PillarmanRegeneration.perform`; `HamonHealing.updateRegenEffect/updateEffect` | `PillarmanBaseActions.regenerate` | Implemented: stage2, stone/stun allowed, cost40, cooldown20, amp4/60 with original effect-phase merge (not additive duration). |
| enhanced_senses | `PillarmanEnhancedSenses.holdTick` | `PillarmanBaseActions.sensesRadius/sensesScanDue`; `SENSES_TICKS` | Server cost.05/t and walk.5 implemented. Exact scan cadence/radius exposed for05-34; orange80t client outlines remain05-34. |
| evasion | `PillarmanEvasion.getCooldownAdditional`; `PillarmanUnnaturalAgility.onUserAttacked`; `Action.cooldownFromHoldDuration` | `PillarmanBaseActions.evade/tick/stopHeld` | Implemented: stage2,1.5/t,max50,walk5,hold-scaled CD50; nonexplosion entity/projectile filtering and unseen Stand rejection. Animation05-34. |
| unnatural_agility | `PillarmanUnnaturalAgility.onUserAttacked` | `PillarmanBaseActions.evade` | Implemented: stage2,1/t,walk0;30% ordinary-living melee counter for.75×barehand damage, equipment modifiers excluded. Recursion guard bounds two simultaneous counters. Animation05-34. |
| horn_attack | registration | 05-29 | Active, not implemented by05-23. |
| ribs_blades | registration | 05-29 | Active, not implemented by05-23. |
| hide_in_entity | registration | 05-30 | Active possession, not implemented by05-23. |
| small_sandstorm | registration | 05-31 | Active WIND, not implemented by05-23. |
| divine_sandstorm | registration | 05-31 | Active WIND shift variation, not implemented by05-23. |
| wind_cloak | registration | 05-31 | Active WIND, not implemented by05-23. |
| atmospheric_rift | registration | 05-31 | Active WIND, not implemented by05-23. |
| erratic_blaze_king | registration | 05-32 | Active HEAT, not implemented by05-23. |
| giant_carthwheel_prison | registration | 05-32 | Active HEAT, preserve original misspelling, not implemented by05-23. |
| self_detonation | registration | 05-32 | Active HEAT, not implemented by05-23. |
| light_flash | registration | 05-33 | Active LIGHT, not implemented by05-23. |
| light_flash_decoy | registration | 05-33 | Active LIGHT invisibility, not a decoy entity; not implemented by05-23. |
| blade_slash | registration | 05-33 | Active LIGHT shift variation, not implemented by05-23. |
| blade_dash_attack | registration | 05-33 | Active LIGHT, not implemented by05-23. |
| blade_barrage | registration | 05-33 | Active LIGHT, not implemented by05-23. |

Future custom projectiles implement `PillarmanBaseActions.EvasionProjectile` for source `canBeEvaded`/`standDamage` restrictions. Existing vanilla-derived knife, Hamon, and vampire eye projectiles remain evadable. Do not classify mode projectiles as generic vanilla projectiles without reviewing their source overrides.

## Assets and inactive source

Inspected `init/ModSounds.java` exact IDs and `fabric/build.gradle` original asset-copy rules; original sound events used by this slice: `pillar_man_awakening`, `pillar_man_wind_mode`, `pillar_man_heat_mode`, `pillar_man_light_mode`, `pillar_man_swing`, `pillar_man_punch`, `pillar_man_strong_regen`, `pillar_man_evasion`, `heavy_punch`. Swallowed TNT uses vanilla `GENERIC_EXPLODE`. Source absorption loop ID `pillar_man_absorption` was inspected; client loop is05-34. No images, geometry, animation JSON, or sound waveforms were visually/audibly verified in this worker. Original layers, blades, orange senses outlines, loops, and animations belong to05-34; temple/block/world assets belong to05-35.

Source-inactive: planned stage5/Ultimate Thing, prototype progression, evasion afterimages/camera roll, expanded possession control. These are not implemented. Stage3 itself is active through commands and is implemented.

## Trust boundary and checks

`PillarmanActionPayload` is a bounded enum-only intent; target raycasts and dimension, living, stage, stone, hand, resource, cooldown and time-stop checks stay server-side. Held leases expire after15 missed ticks, dimension changes clear action state, and duplicate/failed starts cannot steal a valid hold. `PROGRESS`, `BLADES`, and `ACTIONS` synchronize with tracking clients; senses clock is owner-only. No client supplies a resource amount, target UUID, stage, mode, or damage value.

Four `PillarmanStateGameTests` use actual server players/entities and actual respawn/save hooks. They cover stages/masks/attributes/save/death/old defaults, punch timing/energy/overlaps/stone, absorption/Hamon/regen costs, and evade/senses/passive thresholds. They are supplied but **unrun**. Root alone registers and runs the consolidated build/integration lane; a failed or unrun case cannot be recorded as parity verified.
