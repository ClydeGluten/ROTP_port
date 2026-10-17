# Zombie-player parity

Source authority: retained Forge source, commit `72862a5826ed45d1dc26e90b37853097acda35de`. This is the independent player power registered as `jojo:zombie`, not the owned `HungryZombieEntity` mob. Original source is read-only.

Status: module and existing-client integration are implemented. Root's consolidated server lane passed **216 required GameTests**, including the registered zombie test class, and core tests (`/tmp/rotp-rest-server-verification.log`, 2026-10-18 local time). The final public Devour-sound flag was added after that server run; root's client build recompiles it. Focused client gameplay and visual acceptance still belong to 05-26. Diff hygiene is not functional evidence.

## Original method and caller coverage

| Original source / active caller | Fabric implementation | Preserved behavior / evidence |
|---|---|---|
| `init/power/non_stand/zombie/ModZombieActions` registrations | `ZombieActionPayload`, `ZombieClient.move/attacks/abilities/defaultMiddleAction` | Attacks Claw/Devour; ability Disguise; middle default Devour. Admin acquisition only. Shared catalog wiring required. |
| `ZombieData.onPowerGiven`; `NonStandPower.onNewPowerGiven/getMaxEnergy` | `ZombieService.grant/maximum`, `ZombieProgress.grant` | Survival starts with up to 1000 blood; creative gets maximum. Maximum is difficulty multiplier times 1000, clamped to at least one. Other non-Stand powers cannot coexist; Stand attachment is untouched. |
| `ZombieData.writeNBT/readNBT`, `toggleDisguise/setDisguiseEnabled`, `syncWithTrackingOrUser`, `tick` | `ZombieAttachments.CODEC/PROGRESS/VISUAL`, `ZombieService.tick/copyAfterRespawn/onDeath` | Persistent `DisguiseEnabled`, blood and maximum; private blood sync; public skin/eye/action flags. Non-death clone retains state; actual death clears power and disguise. Actual entity save/load and clone/death scenario included in root's passing server lane. |
| `ZombiePowerType.getMaxEnergy/tickEnergy/bloodLevel/isHighSaturation` | `ZombieProgress`, `ZombieService.tick/isHighSaturation` | Shared original config arrays; creative suppresses decay. Tier `min(floor(ratio*7.5),4)+difficulty`, Peaceful -1. Saturated at 80%. |
| `ZombiePowerType.initPassiveEffects/getPassiveEffectLevel/tickUser`; `NonStandPowerType.updatePassiveEffects` | `ZombieService.passiveAmplifier/updatePassives/protectsPassive/tick` | Health Boost amplifier `difficulty*2`, Night Vision zero, Strength/Speed/Haste/Jump `bloodLevel-5` when undisguised. No passive regeneration. Food17, full air. Health adjustment preserves missing health. Disguise removes the source buffs, including active versions when the source would remove them. |
| `ZombiePowerType.getMaxStaminaFactor/getStaminaRegenFactor/getTargetResolveMultiplier` | `ZombieService.maxStaminaFactor/staminaRegenFactor`; ordinary target Resolve multiplier remains 1 | `max((bloodLevel-4)*2,1)` maximum; `max((bloodLevel-4)*4,1)` regeneration. Root must route shared Stand progression. |
| `ZombiePowerType.isLeapUnlocked/getLeapStrength/getLeapCooldownPeriod/getLeapEnergyCost`; common leap packet | `ZombieService.leap`, `ZombieClient.leapInput` | Always unlocked, `max(bloodLevel,0)*0.2`, 20 ticks, no cost. Shared upward aim floor, half vertical velocity, vehicle jump, ground particles/sound; creative has no cooldown. Input is Shift+Space. |
| `ZombieClawLacerate.checkSpecificConditions/perform/consumeEnergy/setCooldownOnUse/Instance.playerTick/getWalkSpeed/onStop` | `ZombieService.claw/tick/updateSpeed`, `ZombieLayer.applyAnimation` | Advertised 60 blood is checked but never consumed. Free main hand includes both original open-finger glove items. Undisguised; sound/swing tick3, impact5, end8; half walking speed. No repeat restart. |
| inherited `VampirismClawLacerate.punchPerform/getDamage/blockDestroy`; `Action` default ranges | `ZombieService.clawHit/destroyBlock` | Attribute attack+4, strength-two knockback and collision impact. Entity range8, block range10; only Devour overrides range2. Zombie destroys one block, not the vampire cross. Respects break gamerule, protection, hardness, tool/harvest scaling and drop policy. |
| `ZombieDevour.checkTarget/checkSpecificConditions/holdTick/getTargetRequirement/getMaxRangeSqEntityTarget/heldAllowsOtherAction/cancelHeldOnGettingAttacked` | `ZombieService.startHeld/canDevour/devour/tick/onDamage` | Server raycast every tick at range2, wall/world checks, no Peaceful, disguised, occupied-hand or stunned/frozen starts. Eligibility uses `canBleed && !undead/vampiric`, not vampire drain override tags. No target can continue holding. Damage cancels Devour. Concurrent claw is permitted without cancelling Devour. |
| inherited `VampirismBloodDrain.drainPerform/drainBlood` | `ZombieService.devour`, existing `VampireZombieService.drainBlood`, `VampireAdvancements.onDrainedKill` | Damage2, normal successful-hit invulnerability interval; accumulated 40-tick debuffs; original difficulty gain, player x5/NPC or illager x4/Hamon x1.5/freeze reduction. Heal gain x0.5 and add actual-healed x configured heal-cost refund. Eligible kill creates `HungryZombieEntity` with **null owner**; original people/animal/created statistics and advancement trigger. |
| `ZombieDisguise.perform/getIconTexturePath/getTranslationKey/greenSelection`; `Action.holdToFire` | `ZombieService.startHeld/tick`, `ZombieClient.move/chargeTicks/greenSelection`, `ZombieHud.name` | Toggle after exactly60 held ticks; early release cancels. No free-hand requirement. Alternate `_on` icon and `.disable` label; enabled selection green. Default hold does not cancel merely on incoming damage. |
| `GameplayEventHandler.onFoodEaten` | `ZombieService.onFoodEaten/isMeat` | Original meat items add nutrition*10 blood and heal nutrition. Non-meat does neither. Explicit original catalog replaces removed `Food.isMeat`; rotten flesh counts, fish does not. Root calls from actual item-finish hook. |
| `VampirismUtil.consumeEnergyOnHeal`; `VampireSunBurnEffect` heal cancellation | `ZombieService.payHealing` returns unchanged; shared `VampireHealMixin` Sun Burn check | Zombies are explicitly absent from original blood-on-heal charging. Devour still grants the original healed-cost refund. Sun Burn still prevents healing. Do not route zombies through vampire charging. |
| `JojoModUtil.playerUndeadAttribute/isUndeadOrVampiric/isAffectedByHamon`; damage/potion/Smite callers | `ZombieService.isUndead`, shared root species hooks | Zombie players count as undead for instant potions, Smite, Hamon, UV and air, independent of disguise. Players remain eligible to bleed. Root owns shared damage/mixin integration. |
| `VampirismUtil.tickSunDamage/incSunBurn`; `DamageUtil.entityTakesUVDamage` | `ZombieService.tickSunDamage` | Sun damage4, original cover/weather/dimension/boat rules, Sun Burn and Weakness accumulation. Player sun sensitivity does **not** depend on undead-mob config. Coffin sleep and Sun Resistance prevent sunlight damage. Root calls once per server tick. |
| `VampirismUtil.makeMobNeutralToVampirePlayers/editMobAiGoals` | root `VampireZombieService.editMobGoals` integration | Monsters remain neutral to zombie players when original aggro config is false. Iron Golem special Jojo-vampiric predicate does not include zombies. |
| `client/render/entity/layerrenderer/ZombieLayer.render/getTexture/renderHandFirstPerson` | `ZombieLayer.extract/submit/submitHand` | Original skin overlay when **not disguised**, including first-person hands and tracking players. Invisible/spectator and arm visibility checks; weak render-state snapshots avoid entity retention. |
| `VampireEyesLayer.render` | `ZombieAttachments.VISUAL`, `ZombieLayer.submit` | Original zombie eyes glow **while disguised**, regardless of blood saturation, honoring existing `VampirePresentation.GLOW_EYES`. Full-bright original texture. |
| `ModPlayerAnimations.zombieClawSwipe`; `KosmXVampireClawSwipeHandler` | `ZombieLayer.applyAnimation/loadClip` | Same original vampire_claws clip, 1.75 speed, handedness, limb bending, OUTCUBIC 17-tick fade starting at zombie tick8. Continued clip time during fade preserves shorter zombie action. Root model hook follows existing vampire pose/reset hook. |

## Exact shared integration contract

Root common ownership:

- `RipplesFabric.java`: call `ZombieService.initialize()` after existing vampire/Hamon registries; this registers only the new attachments and packet. Connect one server tick to `tick(player)` and `tickSunDamage(player)`, accepted positive damage to `onDamage(entity)`, death to `onDeath(entity)`, after-respawn after attachment copy to `copyAfterRespawn(old,new,alive)`, disconnect to `disconnect(player)`, stop to `stopServer()`. The disconnect helper already queues off-thread callbacks safely.
- `StandCommands.java`: add administrator-only zombie grant and clear/status paths. Grant returns false on an occupied non-Stand slot. No zombie grant packet exists.
- `VampireService.java`, `HamonService.java`, Pillar acquisition and mask/gift paths: respect zombie nonreplaceability. `hasZombie(LivingEntity)` and `progress(LivingEntity)` are public. Devour rejects other undead/vampiric species.
- `StandService.java`/`ProgressionService.java`: use the two zombie stamina factors while retaining independent Stand ownership and target Resolve factor1.
- Existing food/heal/passive/AI/damage/potion mixins: call `onFoodEaten(entity,consumedStack,nutrition)` once; `protectsPassive` extends milk/effect removal protection; retain general Sun Burn healing rejection; expose zombie undead/Hamon/Smite/UV classification; monster neutrality follows the table above. Never call vampire-owned kill conversion for zombie Devour.
- Register `ZombieGameTests` in the existing GameTest entrypoint. Run root's single consolidated build/test lane. Existing vampire-night environment is reused; conversion scenario temporarily selects Hard and restores difficulty in `finally`.

Client integration completed in the root-authorized follow-up by the same worker:

- `RipplesClient`: `ZombieClient.initialize()`, `tick(client,active)`; route J to `toggle`, close on other mode activation, route mapped press/release and suspend on menu/focus loss. Keep existing H overview and Shift+Space integration.
- `ActionCatalog`: zombie IDs route to `ZombieClient.move/unlocked/available/chargeTicks/chargeDuration/cooldown/cooldownTotal`; alternate name comes from `ZombieHud.name(id)`. `greenSelection(id)` supplies enabled disguise selection. Attacks/abilities/default middle action come from ZombieClient.
- `ActionLayouts`/`StandSelection`: select `ZombieClient.POWER_ID` for active zombie mode and apply the existing editable rows and held-control semantics.
- `ActionHud`: zombie accent `ZombieHud.COLOR`; `ZombieHud.render(g,x,y,partial)` returns blood-row vertical space. Reuse original action rows, overview, icons and charge presentation. Blood remains red.
- Existing `VampireAvatarRendererMixin`: add `new ZombieLayer(this)` and `ZombieLayer.extract(entity,state,partial)`. `VampirePlayerModelMixin`: call `ZombieLayer.applyAnimation(model,state)` after existing vampire animation. `FrozenHandMixin`: call `ZombieLayer.submitHand(pose,collector,arm,light)`. First-person rendering does not invent eye overlays on arms.

These calls are now present in existing production client files. `StandSelection.nonStandPlayable`, `isNonStandMode`, and `closeNonStandModes` provide explicit extension points for subsequent species without inventing their action catalogs. `StandSelectionInputMixin` routes the mapped middle button to Devour; `StandInputMixin` routes grounded Shift+Space. `ProgressionScreen` includes synchronized zombie blood, original translated action names and concise control/food/lifecycle help. No new client test was added by this worker; root owns the shared focused flow.

## Assets

All assets are already included from authoritative source by `fabric/build.gradle`; no copied replacements are required:

- `assets/jojo/textures/power/zombie.png`
- `assets/jojo/textures/action/zombie_claw_lacerate.png`
- `assets/jojo/textures/action/zombie_devour.png`
- `assets/jojo/textures/action/zombie_disguise.png` and `zombie_disguise_on.png`
- `assets/jojo/textures/entity/biped/sated_zombie.png` and `vampire_eyes.png`
- `assets/jojo/player_animation/vampire/vampire_claws.json`
- Existing `zombie_swipe`, `zombie_claw_lacerate`, `zombie_devour`, `heavy_punch` sound events and original translations.

`ZombieClient.DevourSound` ports the original `StoppableEntityTickableSound` loop with volume/pitch1, position tracking and immediate cancellation on hold/eligible-target loss, death or world change. A public boolean in the visual attachment broadcasts eligibility without exposing blood or target identity. Root's focused flow must validate the audible result; no listening or visual evidence is claimed here.

## Evidence and source-inactive content

Four registered `ZombieGameTests` scenarios cover actual grant/drain/untamed conversion and stats; tick5/end8 claw, disguise60 and input cleanup; entity save/load, non-death clone and death; wall reach, actual food consumption, gloves, concurrent actions, leap20 and disconnect. Root's server log reports all 216 required tests passed. The final sound-only tracking flag postdates that run and is covered by the subsequent root build/client lane. Source checks confirm the original assets exist and remain unchanged.

The original TODO remembering the vampire that turned a zombie player is source-inactive. There is no source-active automatic player-zombie infection acquisition or zombie ownership memory to invent. Saturation is exposed as an API but is not substituted for the original disguised-eye condition.

Root's focused client flow must still record J selection, H overview, mapped held Devour/release, disguise icon/green selection, first-/third-person skin and eye settings, claw pose, and save/rejoin in fresh integrated and dedicated worlds. Worker did not launch Gradle or a client. No claim of full port completion follows from this ledger.
