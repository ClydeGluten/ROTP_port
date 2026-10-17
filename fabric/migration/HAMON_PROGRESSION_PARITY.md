# Hamon progression, learning, and advancement parity

Original `src/` is read-only. The authoritative references below are Forge methods, not a newly designed progression system.

## Implemented domain and source mapping

| Original source | Fabric implementation | Preserved rule |
| --- | --- | --- |
| `HamonData` static point table, `setHamonStatPoints`, `hamonPointsFromAction` | core `HamonProgress` | Original cumulative XP table (max 6460), strength/control level 60, shared fractional action XP, 750 energy per point, natural talent doubling, optional breathing/stat gap, resource rescaling on control growth |
| `HamonData.tickEnergy`, `tickHamonBreath`, `tickBreathStability`, `getHamonEnergyUsageEfficiency` | `HamonProgress.tickBreath`, `consume`, `HamonService` | Initial zero energy/full stability; control-sized stability pool; delayed 20/tick energy decay; exact breathing recovery and mask handicap; 80-tick stability recovery pause; partial-energy efficiency; empty-energy stability conversion including original mask asymmetry |
| `HamonData.getActionEfficiency`, `getBloodstreamEfficiency`, `reduceEnergyConsumed` | `HamonProgress.actionEfficiency`, `HamonService.efficiency/consumeEnergy/bloodstreamEfficiency` | Strength damage factor, original branch bonuses, four-exercise bonus, current-stability penalty, scarf 60% energy cost, bleeding/freeze efficiency and optional Mowzie/Vampirism/Twilight freeze effect IDs |
| `ModHamonSkills`, `BaseHamonSkillTree`, `MainHamonSkillsManager`, `BaseHamonSkillSet`, `HamonTechniqueManager` | core `HamonSkills` | All 46 registry skills, 26 base skills/two defaults, complete six-branch prerequisite graph and original HUD action metadata; five techniques; automatic perks without slot costs; `[20,30,40]` slots requiring both stats; optional mixed techniques/empty disabled slots |
| `HamonData.setBreathingTrainingAttributes`, `updateExerciseAttributes` | `HamonService.updateAttributes`, `HamonProgressionMixin` | Integer breathing buffs: damage .03, attack speed .015, movement .0005, swim speed .01 per level; running +10%, mining +5%, swimming max stability +10%, meditation regeneration time -20 ticks |
| `HamonData.tickExercises`, `incExerciseTicks` | `HamonService.tickExercises`, `HamonMiningAccess` | Actual server mining/delayed destruction and six-tick tail; sprint/swim movement eligibility; meditation starts at tick 40; exact exercise durations and breathing scaling; original completion messages |
| `HamonData.setIsMeditating`, bounding-box helpers | `HamonService.setMeditating/meditationDimensions`, `HamonProgressionMixin` | Grounded activation, 35-tick shrinking pose/hitbox and eye height, server and tracking-client refresh, hunger exhaustion reduction and natural-regeneration healing |
| `HamonData.getBreathingIncrease`, `breathingTrainingDay`; `PowerBaseImpl.newDayCheck` | `HamonProgress.dayIncrease/nextDay`, `HamonService.trainingDay/tick` | Best partial exercise plus completed exercises; original positive/negative formulas; worn-mask streak bonus; natural-talent/config multipliers; exactly two skipped-day allowances; daily reset; runtime-only day marker and no offline-day penalties |
| `HamonUtil.nearbyTeachersSkills/interactWithHamonTeacher/startLearningHamon`; `HamonData` learner methods | `HamonService.nearbyTeachers/interactTeacher/teach` | Base-skill teacher union within inflated radius 3; NPC immediate acquisition; player learner-request/teacher-accept handshake; radius-8/death request expiry; creative mastery; Zeppeli nonlethal force-breath 1% failure; original messages/hint |
| Original Hamon client request packets | `HamonPayload` | Bounded operation/ID/target intent only. Server derives teachers, prerequisites, costs, ownership, technique/config gates, and creative-only resets; range/line-of-sight validation prevents remote forged teacher requests |
| `HamonData.addHamonSkill`, `LivingUtilCap.onScarfPerk` | `HamonService.addSkill`, utility attachments | Scarf reward once per retained player utility state, including abandon/relearn; cheat-death refresh when administratively granted and at new training day |
| `HamonData.writeNBT/readNBT`; `NonStandPower.keepPower`; `HamonPowerType.keepOnDeath/onClear` | `HamonAttachments`, `HamonService` lifecycle | All original stat/fraction/breathing/skill/technique/exercise/day-bonus/skip/resource/timer/TCSA fields; immutable save values; copied state after Fabric attachment-copy phase then original keep-on-death config; runtime cleared on death/disconnect; clear removes modifiers |
| `JojoModConfig.Common` Hamon entries | `HamonConfig` | Exact defaults and bounds; server-authoritative JSON, synchronized at join; temple-spawn option consumed by root worldgen adapter |
| Hamon stats/last-Hamon/charge-kill/killed-power-user triggers | `HamonAdvancements` | Stats bounds; optional source entity/loot conditions; attacker/victim power checks; killing-blow/entity predicates; charged entity versus block/state/location distinction |
| `GetPowerTrigger`, abandon packet, `GameplayEventHandler` AFK condition | `VampireAdvancements.onHamonAcquired`, `HamonAdvancements` | Existing get-power criterion reused; original abandon criterion; original >30-second idle/input condition for root card |
| Original Hamon/map advancement JSON | `data/jojo/advancement/jojo/` | Original titles, descriptions, parents, frame/hidden flags, criteria groups and XP rewards. Map predicates retain `JojoStructure` values, including `hamon_map` |
| `CustomIconItem`, `ParseGeckoModel`, original logo geometry | `HamonLogoRenderer`, `HamonLogoModelMixin`, `generate_hamon_logo.py` | 141 exact source quads including bone pivots/rotations, UV coordinates, per-face omissions, inflation, original custom transforms and lighting-normal rotation; original textures remain byte-identical |
| `GlovesItem` enchantability and `combineInAnvil/combineEnchantments` | `HamonGlovesAnvil`, glove anvil/enchanting mixins | Knockback added to superclass eligibility without broad sword/fire/durability enchants; same-glove nondamageable merging; original equal-level upgrade, max levels, incompatibility penalties, rarity/anvil costs, prior-work growth, rename-only 39-level cap, creative exception and second-glove consumption |
| `BreathControlMaskItem.appendHoverText` | Fabric `BreathControlMaskItem` | Original two localized mask explanations and blank spacing |
| `HamonPowerType.consumeEnergy`, `ClackersItem`, `ItemProjectileEntity.onHitEntity` | `HamonService.consumeResourceEnergy`, `ClackersItem`, `ClackersEntity` | Direct power consumers retain partial-energy/stability fallback independently of bloodstream; clacker charge/upkeep/melee use this path; burning clackers ignite for five seconds, skip Endermen, and restore prior fire duration after failed hits |
| `HamonUtil.hamonPerksOnDeath`, `GameplayEventHandler.cheatDeath/sendMemeDeathMessage` | `HamonInheritance`, living/player inheritance mixins | Inheritance runs after death cancellation resolves and before vanilla drops; Crimson captures the held item; Deep Pass uses the exact centered eight-block query; cheat-death messages respect team visibility |
| `MerchantResultSlotMixin`, `CustomVillagerTrades.MapTrade.onTrade`, `TrEntitySpecialEffectPacket` | `HamonMapPurchaseMixin`, `HamonTempleTrades`, `HamonMapPurchasePayload`, `HamonMapClient` | A successful first purchase schedules the effect until the buyer closes their menu; only the buyer hears unattenuated RECORDS music; tracking clients see the merchant's original 200-tick menacing emitter and glyph motion/fade |

## Exact notable edge cases

- Source registers **five**, not six, character techniques: Jonathan, Zeppeli, Joseph, Caesar, Lisa Lisa.
- `syo_barrage` and `cheat_death` are registered but have no owning technique in source. They remain inaccessible through ordinary skill purchase; admin integration can grant them. No invented unlock path was added.
- The source exercise enum has two unused placeholder counters. Fabric exposes the four functioning original exercises; no fabricated exercises were added.
- The source movement-change predicate tests X/Y and omits Z. This literal behavior is preserved.
- The source breath mask at breathing level 100 uses zero handicap, including zero passive recovery while worn. This literal behavior is preserved.
- Source `LastDay` serialization is commented out. Joining/reloading initializes the current day without applying missed offline days. A clock jump triggers one training rollover, not one per skipped calendar day.
- `PowerBaseImpl.tick` invokes `HamonPowerType.tickUser`/`HamonData.tickExercises` before `newDayCheck`. Fabric now processes the final exercise tick before rollover, so a fourth completion on that tick grants the two-day allowance before counters reset.
- `HamonData.updateExerciseAttributes` maintains a cache distinct from raw exercise counters. Refresh sites are a newly completed exercise **below** breathing 100 (`tickExercises`), explicit `setExerciseTicks` (including `readNBT` and the original client exercise packet), daily reset, and `syncWithUserOnly`. Fabric preserves that cache in immutable copies, refreshes on save decoding/join/respawn or explicit assignment, and clears it on daily reset. Continuously completing meditation at 100 leaves charging at 40 ticks; reloading the completed counters refreshes it to 20. Running/mining modifiers, swimming capacity and the all-four action bonus follow the same cache.
- Source `getBreathingIncrease` also uses cached `getCompleteExercisesCount()`, plus the raw best incomplete fraction. Consequently capped-session completions with a stale empty cache do not grant skipped days until a source refresh occurs. This source evidence corrects plan 05-16's initial assumption that the entire daily forecast should use raw completion. Raw counters/completion percentages remain available separately.
- Default overdrive/healing skills remain free and survive base-skill resets. Technique perks do not spend technique slots. Empty configured slot lists disable technique choice/learning.
- The shared fractional XP remainder crosses strength/control actions, matching original source.
- Original level-fraction calculation divides by zero at maximum level. The UI helper returns exactly 60 at cap, a numerical repair with no progression change.

## Platform adaptations

- Fabric attachments replace Forge capabilities/NBT wrappers and original incremental packets. They preserve original state categories; progress is server-authoritative and tracking-visible, while teacher UI availability is recomputed from server entities.
- Persistent Hamon field names and meanings are unchanged; runtime exercise bonuses are recomputed from counters on decode, matching original reload. The attachment stream carries the effective mask explicitly so unrelated synchronization cannot refresh stale server bonuses. Original client exercise packets explicitly refreshed their local cache; the unified Fabric attachment instead synchronizes the authoritative effective value while retaining raw counters for display.
- `getOverworldClockTime()` replaces removed world day-time access. Forge's global day cycle is preserved across dimensions.
- Forge `SWIM_SPEED` does not exist in 26.3. The exact breathing multiplier is applied to water acceleration, avoiding vanilla water-efficiency/depth-strider substitution.
- Original entity-size events become a dimensions mixin; mining reads server game-mode state through an accessor.
- Modern criteria codecs use modern entity/loot predicate schemas and singular `advancement` paths. Original field meanings and condition logic are retained. Map NBT predicates become custom-data component predicates. Old `Icon` and `CustomModel` icon stacks become `minecraft:item_model` components.
- 26.3 exposes no public special-item-renderer codec registry. A narrowly scoped bootstrap mixin registers the source logo renderer.
- Modern anvil code rejects nondamageable same-item merges and preserves the right item on rename-only operations. The glove-specific override restores both original behaviors without changing other items.
- Modern enchantment definitions supply anvil costs, equivalent to the original vanilla rarity multipliers; custom data-pack enchantments retain their configured costs and compatibility predicates.
- Modern Fabric `AFTER_DEATH` occurs after inventory drops. A living-entity killing-blow hook and the player `die` override preserve the original pre-drop held-item capture while allowing all `ALLOW_DEATH` vetoes to resolve first.
- The removed map-color component is represented by constant tints in item models. The purchase advancement retains the original purple `7171001`; the temple map retains its original gray.
- Minecraft 26.3 separates item and block atlases. The three original Hamon/vampire advancement-icon sprites and the original 1.16.5 spawn-egg/map layers are explicitly included in the item atlas. The removed map-markings layer and base are copied byte-for-byte from the cached official 1.16.5 client; no source texture pixels are edited.

## Validation

- Eleven focused JUnit tests passed using the existing cached JUnit dependencies and `javac --release 25`, without running Gradle from this worker.
- Tests cover literal XP boundaries/cap, cross-stat fractional XP and training gap, control-pool rescaling, partial/empty-energy consumption and mask asymmetry, air/recovery pause, exercise duration/completion bonuses, mask streak and missed-day retention, maximum-breathing protection, teacher/prerequisite/point checks, technique slots/mixing/reset/perks, all registry definitions/dependencies, and immutable/finite state.
- The shared root build lane performs Minecraft compilation, runtime mixin verification, resource loading, and integrated/dedicated flows. Those results are recorded in the root delivery notes; they are not implied by the domain tests above.
- Added focused real-menu `HamonGlovesGameTests` for equal-level merging, XP/prior-work cost, retained soap charge, and sacrifice consumption including rename-only merges. Added `HamonLifecycleGameTests` for late death cancellation, accepted player/mob pre-drop inheritance, and clacker stability fallback. Their execution belongs to the shared root build lane; this worker did not run Gradle.
- Plan 05-16 adds three core cases for capped stale/explicit-refresh behavior, preservation across immutable edits and threshold changes, and legacy-constructor reload. Three `HamonProgressionParityGameTests` cover final-tick rollover, first-login/single-clock-jump behavior, and actual capped meditation completion with persistence/network/attribute/reset assertions. Static diff checks passed; these additions have **not** been compiled or executed by this worker. Root must register `com.github.standobyte.jojo.fabric.HamonProgressionParityGameTests` and run the shared validation lane.

## Integration ownership

`HamonService.initialize()` registers progression attachments, config and UI payload; call once. `HamonAdvancements.initialize()` registers callbacks; call once. Common mixins: `HamonMiningAccess`, `HamonProgressionMixin`, `HamonGlovesAnvilMixin`, `HamonGlovesEnchantingMixin`, `HamonInheritanceMixin`, `HamonPlayerInheritanceMixin`, `HamonMapPurchaseMixin`. Client mixin: `HamonLogoModelMixin`. `HamonTempleTrades.initialize()` registers the purchase payload/particle; `HamonMapClient.initialize()` registers its receiver and presentation. Living-entity tick covers NPC Hamon users; do not tick masters a second time.

Actions, passive combat, inherited Hamon, item acquisition, teacher entity/worldgen, Stand stamina integration, vampire capture/clear, and client presentation are owned by the accompanying action/client/root adapters. Their source mapping is maintained separately.

## Final Draft 15 verification

`/tmp/rotp-draft15-verification8.log`, **BUILD SUCCESSFUL in 53s**. All **197 required server GameTests** passed. The focused Hamon client flow passed in fresh integrated and dedicated worlds. All **91 core tests** pass (unchanged tasks reused); packaging passed **29 model checks**. No unrelated client matrix was repeated.

Bundle `0.2.2-fabric.15-dev+upstream.0.2.2.2`; manual playtest pending. Original source remains unchanged.

## Final Draft 16 verification

`/tmp/rotp-draft16-verification3.log`, **BUILD SUCCESSFUL in 1m 1s**. All **205 required server GameTests** and **94 core tests** passed. The focused `HamonClientGameTest` passed in fresh integrated and dedicated worlds; packaging passed **29 model checks**. No unrelated client matrix was repeated.

Root registered the progression cases and integrated all domain changes before this successful run. The earlier worker-only validation notes describe the handoff, not the final delivery status. Bundle `0.2.2-fabric.16-dev+upstream.0.2.2.2`; mod SHA-256 `6e7de829462ebcf9e43decb31437840a70408790ccd9e9853f5d4ddd936e301b`. See `HAMON_SOURCE_REVIEW.md` and `05-DRAFT16-VERIFICATION.md` for correction evidence and limitations. Manual visual/gameplay acceptance remains pending.
