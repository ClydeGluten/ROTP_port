# Remaining Stand source audit

Read-only audit of original `src/` and current `fabric/`, 2026-10-18. Scope: Stands beyond Star Platinum/The World. This document records inspected registrations and methods, not implementation or test results. Original `src/` remains unchanged.

## Active scope

`src/main/java/com/github/standobyte/jojo/init/power/stand/ModStandsInit.java` registers five additional entity Stands plus two non-manifesting Stands. `ModStands.java` exposes all seven entity Stands, including SP/TW. Boy II Man and Mr President are real registered gameplay; they are not regular Arrow-pool entities. `KILLER_QUEEN` at the end of `ModStandsInit` is only `Supplier -> null`; do not treat that placeholder or commented Requiem references as playable source behavior.

Stats below follow registry builder semantics: power/speed/durability/precision base→developed; range is effective/max range, not development.

| Stand ID | Power | Speed | Durability | Precision | Range | Source weight/pool |
|---|---|---|---|---|---|---|
| `hierophant_green` | 8→9 | 10→12 | 8→10 | 8→10 | 50/100 | weight 2 |
| `silver_chariot` | 8→9 | 14→17.5 | 11→12 | 11.5→16 | 10/10 | weight 2; armor 20, toughness 12 |
| `magicians_red` | 11→13 | 11→12 | 12→13 | 8→9 | 5/10 | weight 2 |
| `crazy_diamond` | 14→17 | 14→16.5 | 11→13 | 11→12 | 2/4 | weight 2 |
| `gold_experience` | 9→10 | 14→15 | 7 | 10 | 2/4 | builder default weight 2 |
| `boy_ii_man` | 0 | 0 | 0 | 0 | 0 | `NPC_ENCOUNTER` |
| `mr_president` | 0 | 0 | 14 | 0 | 0 | `ANIMAL` |

Keep the user's intentional allocation-generation behavior when enlarging the regular playable pool; source pool membership does not authorize adding the two encounter/animal Stands to Arrow allocation.

## Actions and supporting systems

All action source classes below are under `src/main/java/com/github/standobyte/jojo/action/stand/`; exact costs, durations, unlock levels and hotbar order are in each Stand section of `ModStandsInit`.

### Hierophant Green

- Registry actions: `hierophant_green_attack`, `attack_binding`, `emerald_splash`, `es_concentrated`, `block`, `grapple`, `grapple_entity`, `barrier` (all suffixes prefixed `hierophant_green_`). Left row: attack, splash. Right: block, grapple, barrier. Binding, concentrated splash and entity grapple are shift variations.
- `HierophantGreenStringAttack.standPerform`: eight extending strings normally, four binding strings shifted. Lifetime scales with attack speed. `HierophantGreenEmeraldSplash.standTickPerform`: projectile rate scaling; concentrated mode fires 2.5× rate with faster/tighter projectiles and block breaking. Max training unlocks concentrated mode. The normal splash also fires from placed barriers at the aimed target.
- `HierophantGreenGrapple.standPerform`: owner-bound block grapple or entity bind. `HierophantGreenEntity.setManualControl/createUserString/attachBarrier`: remote-control tether, anchor placement, connected strings, accelerated retraction. `HierophantGreenBarrier.getMaxBarriersPlaceable`: 15 barriers, 100 at resolve 4.
- Entities registered in `ModEntityTypes`: `hg_string`, `hg_emerald`, `hg_grappling_string`, `hg_barrier`. Behavior in `HGStringEntity`, `HGEmeraldEntity`, `HGGrapplingStringEntity`, `HGBarrierEntity`; shared owner-bound superclass and `util/mod/HGBarriersNet` matter. `HGBarriersNet.shootEmeraldsFromBarriers` selects separated shooting points, spends stamina per projectile and tracks ripped barriers. Presentation includes `HierophantGreenBarrierDetectionMarker`, extending-string renderers, emerald model and glow texture.

### Silver Chariot

- Registry actions: `silver_chariot_no_rapier_attack`, `attack`, `barrage`, `sweeping_attack`, `dash_attack`, `rapier_launch`, `block`, `take_off_armor`. Left: attack, barrage, launch. Right: block, armor removal. Dash is shifted attack/barrage; sweeping attack is its finisher; no-rapier attack is a live fallback.
- `SilverChariotEntity.setRapier/setArmor` carry indispensable state. Without rapier: damage −25%, attack speed −50%, reach −1. Armor removal: movement +150% base, attack speed +50% base, armor/toughness/durability removed. Removal creates afterimages. Rapier block breaking is limited to hardness ≤1 and harvest level ≤0. Resolve 4 enables projectile deflection and reduced user movement penalties.
- `attackEntity/attackTarget/deflectProjectile`: deflect incoming projectiles; deflecting a fiery damaging projectile lights rapier for 300 ticks. Fiery rapier attacks burn targets. `SilverChariotRapierLaunch.standPerform`: projectile speed 2; noncreative user loses rapier until recovered.
- `sc_rapier`/`SCRapierEntity`: persistent projectile, up to 100 ricochets, base damage 1.5× Stand attack damage plus 0.5 per ricochet, pickup back into the Stand. This is not equivalent to a disposable arrow.
- Presentation: separate base/armor models and texture, conditional rapier visibility, rapier flame layer, one-handed barrage animation, afterimages. Original model's `rapierAnim` map differs from unarmed animations.

### Magician's Red

- Registry actions: `magicians_red_punch`, `kick`, `heavy_punch`, `flame_burst`, `fireball`, `crossfire_hurricane`, `ch_special`, `block`, `red_bind`, `detector`. Left: punch, flame burst, fireball, crossfire. Right: block, bind, detector. Shift heavy punch has kick finisher; special crossfire unlocks through max training.
- `MagiciansRedFlameBurst.standTickPerform`: sustained cone of flames, reach-derived velocity doubled at resolve 3. `MagiciansRedCrossfireHurricane.standPerform`: normal large ankh or eight targeted special ankhs. Fire-block consumption inside `standTickButtonHold` is `if (false)`/FIXME and is dormant; do not invent its charging bonus.
- `MagiciansRedRedBind.stoppedHolding` can preserve a landed bind for a ready finisher; `MagiciansRedKick.targetBeforePerform/setAction/standTickWindup` slides to and kicks that bound target. Detector toggles `MRDetectorEntity`, which points at nearest eligible living entity, has sound feedback, and burns overlapping targets.
- Registered entities: `mr_flame`, `mr_fireball`, `mr_crossfire_hurricane`, `mr_crossfire_hurricane_special`, `mr_red_bind`, `mr_detector`. Their source classes, `MagiciansRedEntity`, `MagiciansRedFireBlock` and custom crossfire explosion are required. Flames melt snow/ice and place special fire; large crossfire burns flammable terrain, records broken blocks for CD restoration, and creates fire. `MagiciansRedEntity.attackEntity` adds fire to melee; `removeFireUnderPlayer` prevents its owner's fire obstruction.
- Presentation: flame particles/projectiles, big/small ankh renderers, bind ribbon, detector, animated Stand flame layer and Avdol voices. Fireball water/rain handling in its source is commented out; distinguish this from active crossfire extinguishing.

### Crazy Diamond

- Registry actions: `crazy_diamond_punch`, `barrage`, `misshape_face`, `misshape_arms`, `misshape_legs`, `misshaping_punch`, `leave_object`, `heavy_punch`, `block_bullet`, `blood_cutter`, `block`, `repair`, `previous_state`, `heal`, `angelo_rock`, `restore_terrain`, `anchor_move`, `anchor_make`.
- Left: punch, barrage, block bullet, blood cutter. Right: block, item repair, heal, restore terrain, anchor move. Shift repair is previous state (training unlock); shift anchor move makes an anchor. Heavy/recovery modifiers support leave-object, body-part misshaping and Angelo Rock; these cannot be represented as unrelated hotbar attacks.
- `CrazyDiamondRepairItem.repairTick`: offhand durability repair, repair-cost reset, enchantment removal/XP return; anvils, cobblestone and cracked blocks transform. `CrazyDiamondPreviousState.standTickPerform/convertTo`: reverses recipes and selected entities (TNT/minecart, snow golem, charged creeper; iron golem and forming Wither at higher resolve). Direct targeted block reversal is commented out.
- `CrazyDiamondHeal.checkTarget/healLivingEntity`: disallows self, heals Stand users through manifestation, bleeding, boats and source `IHasHealth` entities. The source permits reversing up to 15 death ticks; do not silently simplify to ordinary living-target heal. Misshape modifiers apply only to player Stand users; mob variants remain TODO. They heal back half the prior punch damage while applying part-specific debuffs.
- `CrazyDiamondRestoreTerrain.restoreBlocks/rememberBrokenBlock`: chunk history of state, drops and dropped XP; restoration consumes corresponding items/XP, prioritizes placements, obeys placement rules, supports intentional improper reconstruction and syncs restored blocks. `capability/chunk/ChunkCap.saveBrokenBlock` explicitly excludes inventory block entities and the Stone Mask block entity. Its history persistence is configurable. World break/explosion capture must exist before the restoration action can be faithful.
- `CrazyDiamondBlockBullet.standPerform`: consumes offhand hard block, uses `cd_block_bullet`, homes toward `DriedBloodDrops` unless shift disables homing. `CrazyDiamondBloodCutter` uses `cd_blood_cutter`. Anchor item stores source block position/state; make/move methods handle creating a fragment and drawing its holder back. `CrazyDiamondLeaveObject.standTickRecovery`, `CDTurnIntoAngeloRockEffect` and `AngeloRockEntity` add inventory/embedded-object and persistent entrapment behavior.
- Presentation: repair loops/particles, body-part render effects, blood/anchor world markers, block-textured projectile, Angelo rock and dedicated Java action poses.

### Gold Experience

- Registry actions: `gold_experience_punch`, `barrage`, `lifeshot`, `lifeshot_punch`, `tooth_lifeform`, `heavy_punch`, `block`, `choose_lifeform`, `create_lifeform`, `revert_lifeform`, `mark_item`, `bone_meal`, `life_detector`, `heal`, `healing_item`, `heal_other`. Choose-lifeform has default C binding; left row punch/barrage; right block, choose, create, mark, bonemeal, detector, heal.
- `GoldExperienceCreateLifeform.checkSpecificConditions/perform`: maximum 16 created lifeforms; selected subtype must have been encountered and pass server validation. Sources include offhand material, targeted inanimate entities/blocks, marked items in inventories/block inventories and embedded arrows/knives. Creates `ge_lifeform` (`GETransformationEntity`) with serialized source, duration, ownership, optional host/follow/aggro target and continuous `GECreatedLifeformEffect`; shift reverts the transformation. Source data must survive save/load and restore items/entities correctly.
- Dependencies: `LifeformsMetMobs`, `LifeformsUIState`, entity subtype registry, `GETransformationData`, `itemtracking/*`, `mixin/itemtracking/*`, `GEItemMarkEffect`, three choose-lifeform screens and marker renderers. Marked items can move between holders; a position-only marker is insufficient. `perform` explicitly supports creating a Mr President turtle and teleporting affected nearby entities/foreign item holder into its room.
- `GoldExperienceHeavyPunch` knocks out teeth/objects; recovery followup turns them into lifeforms. Lifeshot applies sensory overload with resistance accumulation (`GoldExperienceEntityLifeshot.giveEffectWithResist`), backed by `GELifeshotEffect`/consciousness packets. `GoldExperienceLifeDetector.standTickPerform` expands detection to 32 blocks, highlights eligible living entities and souls for 80 ticks. `GoldExperienceBoneMeal` grows eligible blocks. Heal/self/other and crafted body tissue use `GoldExperienceHeal.canHeal/spendAndHeal/giveGEHealEffect` plus `GEHealingEffect`, bleeding and regeneration rules.
- Geometry is **not** in the nearly empty `GoldExperienceModel.java`: it is `assets/jojo/bb/gold_experience.bbmodel`, with `animations/gold_experience.animation.json`. Lifeform morph rendering, selection screens, marked-item glint, soul/sensory presentation and body-tissue item are additional assets/runtime code.

### Boy II Man and Mr President

- Boy II Man: `RockPaperScissorsKidEntity.finalizeSpawn` grants the registered Stand; `RockPaperScissorsGame.boy2Man` takes arms after first winning round, legs after second, remaining Stand at the third. Winning back a match removes the corresponding `BoyIIManStandPartTakenEffect`; its `stop()` returns stolen parts or the whole Stand. Requires part ownership/gating in shared Stand state, continuous effect persistence, RPS game/session packets and screen, kid renderer/AI/spawn path and advancements. TODO comments surround working branches; the theft/return code is active.
- Mr President: `MrPresidentStandType.tickUser/findTargets/teleportEntities/teleportFromRoom` sends falling entities onto an unlocked turtle into a per-turtle room. `CocoJumboTurtleEntity`, key/master-key items, `MrPresidentWorldData`, inside/back teleporters, `MrPresidentEnteredRoomEffect`, dimension data and exit blocks form the unit. Room locking depends on assigned key vs inserted key. This also supports GE's turtle subtype. Presentation includes turtle/key renderer, exit block assets and room structure resources. No humanoid manifestation or ordinary action hotbar exists.

## Existing Fabric seams and required generalization

| Shared files | Concrete constraint |
|---|---|
| `core/StandProgress.java`, `StandStats.java`, `StandAllocation.java` | `hasStand()` recognizes only SP/TW; stats fallback is SP; grant rejects other IDs; allocation lists two IDs. Stats currently cannot express developed precision or SC armor/toughness. `timeStopUnlock()` must not grant time stop to unrelated Stands. |
| `fabric/StarPlatinumEntity.java`, `StandService.java`, `RipplesFabric.java` | The World shares the SP entity class. Summon map, constructor, service return type, owner/phase APIs and type checks are concrete SP/TW assumptions. Extract reusable manifestation/action behavior or add a common contract before independent Stand implementations. Preserve existing SP/TW behavior. |
| `StandAttachments.java`, `ProgressionService.java`, `core/ResolveProgress.java` | Resolve attachment selection is TW-vs-SP. Other Stands need independent progression plus per-action training (splash, crossfire, repair), not time-stop training reused for everything. Parts and persistent linked effects are currently missing from this model. |
| `StandActionPayload.java`, `StandHudState.java`, `AbilityCooldowns.java`, `StandMovement.java` | Fixed SP/TW intent enum, fixed five cooldown fields and integer phase IDs. New actions need validated action IDs/targets/extra data, per-action cooldown/training and held-movement factors. GE selected subtype/item tracker and CD recovery modifiers need extra intent data. |
| Client `ActionCatalog.java`, `ActionLayouts.java`, `ActionHud.java`, `HamonScreen.java`, `ProgressionScreen.java` and input/view/camera classes using SP types | Catalog defaults use a boolean world switch; layouts enumerate two Stands; HUD uses two portrait/icon names. Root should own generalization so feature workers do not race on these files. Audit direct `StarPlatinumEntity` casts in control/render/input paths. |
| `client/RipplesClient.java`, model generators/templates/render state | Only two model-layer/renderer registrations. Existing Java geometry parser `tools/star_platinum_source.py` recognizes direct `new ModelRenderer`, not old `convertLimb(new ModelRenderer(...))`; HG/SC/MR/CD require parser adaptation plus Java pose conversion. GE requires BB model parsing. Existing TW generator imports SP helper code and is not an all-Stand converter. |
| `fabric/build.gradle`, `JojoSounds.java` | All original JoJo textures/sounds/languages already copied; `JojoSounds` registers every original sound definition. No need to manually duplicate them. Original BB/animations, particle definitions, model/blockstate/data assets are not generally copied; geometry can be generated, but particle/item/block/dimension resources require deliberate conversion. |

Reusable **patterns**, not drop-in behavior: `HamonEntities` for independent entity registration; `HamonProjectileEntity`/`KnifeEntity` for 26.3 projectile sync, owner IDs, collision and persistence; `HamonAfterimageEntity` for an existing afterimage implementation; `TrackedEffects` for distributing custom mob effects; `VampireBlocks`/`StoneMaskBlockEntity` for block registration and persistence. A general persistent Stand-effect/item tracker/chunk restoration store is still needed. MR and CD share broken-block capture; GE and Mr President share transformation/turtle lifecycle. `TrackedEffects`' allowlist needs the additional actual effects.

## Smallest faithful order and parallel ownership

1. **Root foundation first:** Stand definitions/stats/pools; common manifestation/service contract; action intent/catalog + per-action training/cooldown; progress migration; root-controlled registration hooks; explicit non-time-stopper gating. Add persistent linked-effect and missing-part representation before CD/GE/Boy II Man integration.
2. **Parallel isolated lanes after contracts settle:** (A) Silver Chariot including rapier/armor/actions; (B) Hierophant Green including projectiles/net/grapple/barriers; (C) presentation generator/runtime conversion. Give each gameplay lane new namespaced feature files and their dedicated tests; presentation lane owns new generic generator/parser plus new Stand models/renderers, not shared registration/HUD files. Root integrates registrations/catalog and runs the complete build lane.
3. **World-history foundation, then MR/CD lanes:** shared block-history/event capture belongs to one owner. MR owns its fire block/projectiles/explosion/detector. CD owns restoration/reversal/heal/anchors/body modifiers/Angelo behavior and markers. Do not let both edit global damage or world-event mixins independently.
4. **Persistent-effect/item/subtype foundations, then GE:** one owner for shared item tracking and encounter catalogs; GE owns transformation, actions, UI and morph rendering. Mr President can proceed in parallel after persistent effect contract: owns `mrpresident/*`, room dimension/blocks/turtle/keys. Integrate their explicit turtle-transformation case together.
5. **Boy II Man:** can run alongside GE/Mr President once missing-part state and linked effects work; owns RPS game/NPC/session UI/packets. Preserve original part-return semantics and source TODO boundaries.

This ordering follows actual shared dependencies. SC is the smallest complete next Stand; registering all five as SP recolors would not meet source parity. Broad completion requires source-driven scenario checks for action variants, saved linked state, inventory movement and world restoration, plus visual checks of dedicated poses/layers—not only successful compilation.

## Remaining investigation boundaries

This audit is a dependency/registration handoff, not a completed method-by-method parity specification. Before implementing each lane, trace projectile collision/damage constants, action-specific recipe/target corner cases, continuous-effect cleanup on death/logout/dimension changes, NPC spawn rules, Mr President room destruction/locking, GE damage interactions and sensory-camera behavior. The cited Java pose constructors and BB geometry have not been converted or visually compared. No builds or tests were run for this documentation-only audit.
