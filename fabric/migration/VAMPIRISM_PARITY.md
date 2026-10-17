# Vampirism (Drafts 12–13)

This milestone ports the original weak/full vampire distinction, acquisition, blood economy and three starter actions. It does not introduce numbered vampire levels. The original Forge sources remain read-only.

## Source mapping

| Original source | Fabric implementation | Supported behavior |
| --- | --- | --- |
| `VampirismData`, `VampirismPowerType` | `VampireProgress`, `VampireAttachments`, `VampireService` | Separate persistent vampire state, 300 initial blood, 1,000 maximum, weak/full action gates, original difficulty formulas and death retention |
| `BleedingEffect.applyStoneMask`, `StoneMaskItem`, `ModArmorMaterials` | `VampireService.activateMask`, `StoneMaskItem`, `VampireItems` | Non-Peaceful transformation, weak-to-full upgrade, 33 durability, one damage per activation, 101-tick active appearance then worn-mask drop |
| `KnifeItem.use` | `KnifeItem.use` | One knife activates a worn mask before throwing; knife remains, one self-damage, no throw cooldown |
| `jungle_temple_stone_mask.json` | `StoneMaskLoot` | Independent 8% bonus roll in jungle-temple chests, including replacement tables; existing vanilla loot preserved |
| `StoneMaskModel`, original item models/textures | `StoneMaskLayer`, `StoneMaskActivationProperty`, modern item JSON | Original 32x32 armor texture, cube geometry/deformation and activated textures |
| `VampirismBloodGift`, `ModVampirismActions` | `VampireService`, `VampireActionPayload` | Empty hand, two-block targeting, donor health above ten, human recipient at six health or less, 60-tick hold, five blood per tick, ten donor health, recipient healing and weak form |
| `VampirismBloodDrain` | `VampireService` | Empty hand, two-block targeting, two damage per accepted hit with vanilla hit immunity, original difficulty/human blood multipliers, cumulative debuffs and no drain knockback (`DamageUtil.getKnockbackFactor`) |
| `VampirismClawLacerate` | `VampireService` | Full form only, 45 blood eligibility without a debit (the original overrides consumption), tick-three swing, tick-five impact, tick-twelve recovery, attack damage plus four, knockback two and seven-block destruction pattern |
| `VampirismUtil`, `VampireSunBurnEffect`, `NonStandPowerType` | `VampireService`, `VampireHealMixin`, `VampirePassiveMixin` | Blood-dependent passive tiers, hunger/air handling, healing costs, four-point sunlight damage, accumulating sunburn/weakness, healing suppression and protection of current hidden passives from effect removal |
| `InputHandler`, `BarsRenderer`, `HorizontalBarsRenderer`, control schemes | `VampireClient`, shared action HUD/layouts | J non-Stand mode, original red blood bar/icon, shared V/B selection and Alt bypass, independent saved vampire layout, original action textures and charge crosshair |

## Playable acquisition and controls

Find the mask in an unopened jungle-temple chest, or use `/give @s jojo:stone_mask`. Equip it in the helmet slot. Hold exactly one knife and use it; hold the rebound hotbar-bypass key (default Alt) when power controls capture right-click. Activation gives full vampirism and drops the reusable mask after approximately five seconds. Activate under shelter or at night: the original sunlight weakness applies immediately.

Blood Gift is the player-to-player route. The recipient must be human and at three hearts or less; Draft 13 also supports dying players before respawn. The donor needs more than five hearts and enough blood to sustain the three-second hold. The recipient becomes a weak vampire with Blood Drain and Blood Gift; using a mask unlocks Claw and full-power passives. Full form survives death under the original default; weak form does not. Vampire and Stand ownership are independent.

Incoming damage interrupts Blood Drain, as in the source. Blood Gift can continue through damage while its donor-health and other conditions remain satisfied. Milk preserves the current hidden vampire passives, including maximum health, and cannot cure the original `sun_burn` effect; ordinary removable effects still clear.

J opens the vampire rows. Hold V and select Claw or Blood Drain; left-click uses Claw and holding left-click drains. Blood Gift is the default right-row action: hold right-click on an eligible player with an empty main hand. K returns to Stand mode; summoning also opens Stand mode. Both resource sets remain visible in the original bar arrangement. H explains your weak/full form, blood and action requirements. The earlier development shortcut also used J for Time Stop: when both bindings share J, vampire mode takes priority. Select Time Stop in the Stand HUD or give its direct shortcut a separate binding.

Claw block damage obeys the namespaced game rule `/gamerule jojo:abilities_break_blocks false`, adapted from the original `jojoAbilitiesBreakBlocks`, along with world/player permissions and Fabric block-break cancellation.

## Platform adaptations and remaining scope

Fabric attachments replace capabilities and separate packets synchronize owner-only state. Server-held actions use an expiring input lease and authoritative targeting. The current renderer replaces the original armor callback with a player render layer; modern item model dispatch replaces item predicates. Original textures and sounds are packaged from the retained source assets.

Draft 13 expands this foundation with the remaining vampire action, lifecycle, Stand modifier, visual, configuration and blood scopes below. Full Hamon learning/acquisition, Pillar Man evolution and separate zombie-player powers remain outside this vampire milestone. Aja mask misuse retains the original lethal result; the Pillar Man-only evolution branch requires that later power subsystem. Retained-Hamon behavior has an explicit adapter and operator setup command; it is not a substitute Hamon acquisition path.

Validation results and bundle information are recorded in `05-07-SUMMARY.md` and `STATUS.md` after the combined verification run.

## Advanced combat and rendering (Draft 13)

`VampireCombatService`, `SpaceRipperStingyEyesEntity` and `VampireCollisionImpact` now port the original Freeze, eye beams, Evil Atmosphere, leap and Claw collision formulas. Freeze consumes 0.45 blood per fulfilled tick, inflicts difficulty-scaled cold damage, stacks the original slowing effect, counters direct living melee attackers and creates temporary frosted ice. Its original skeleton-to-stray conversion and fire/ultrawarm restrictions remain. Modern `WATER_EVAPORATES` dimension attributes replace the removed `ultraWarm` flag.

Space Ripper Stingy Eyes requires twenty authoritative charge ticks, then fires on button release. Keeping the button down holds the completed charge. Two beams consume twenty blood upfront and twenty per continuous-action tick, detach after the original firing window or exhausted blood, and retain the original difficulty speed/damage, invulnerability bypass, no knockback and hardness-three block limit. The original multiple-entity ray helper checks blocks only when no entities intersect the segment; the port preserves that behavior, including hits through intervening blocks when an entity intersects. Modern server-controlled input leases cancel abandoned charge states without trusting client-supplied durations.

Evil Atmosphere uses the original cubic 8/24/40-block ranges, passive-mob stun, difficulty-scaled debuffs, Hard-only owned-zombie buffs and poppy-to-wither-rose conversion. Leap keeps the original blood-tier and weak-form multipliers, direction, vertical factor, cooldown, ground particles and sound. Claw impacts apply the original nonexplosive wall/entity damage; the commented-out source explosion is not enabled. Collision broad-phase traversal uses current vanilla collision boxes rather than Forge's removed collision collection types.

Action validation follows `PowerBaseImpl` and `Action`: Drain and Freeze permit switching, but a successful new action replaces the preceding hold. Gift, eye charging and Hamon concentration reject other starts until released. Claw and firing eye beams block other vampire actions for their continuous-action duration. Failed starts leave a current hold intact. Hamon-trained targets gain the source 1.5 blood-drain multiplier and cannot receive Blood Gift through the future Hamon-power adapter.

`FrozenLayer` uses the original biped/non-biped textures and four intensity stages on all living models and first-person hands. Original model texture dimensions are captured from baked cubes because the current model API no longer exposes them. Beam rendering retains the original texture, full brightness, 0.15 width and four translucent sides.

`VampireClawAnimation` reads the unmodified original `vampire/vampire_claws.json`, including positions, rotations, bend channels, handedness mirroring, 1.75 playback speed and the 17-tick cubic fade. `VampireBendGeometry` clips original limb/outer-skin faces at elbow, knee and torso pivots and preserves interpolated UVs while rotating the resulting segments. This is a two-segment hinge adaptation of KosmX's deformable rig, not its smooth skin deformation. Vanilla armor remains rigid and follows the animated parent pose. These mesh differences are explicit platform adaptations; the animation data is not replaced by invented poses.

Advanced common and client sources compile together. Runtime checks and playtest results, when performed, are recorded separately by the packaging milestone.


## Draft 13 lifecycle, Stand modifiers and supporting content

`VampireLifecycle` ports four-stage curing, enchanted-golden-apple/Weakness V entry, zero-blood progress, default 48,000-tick duration, accelerator blocks and waking completion. Sixteen oak coffin colors preserve recipes, day/night sleep, vampire-only respawn, closed states and delayed waking on nighttime respawn. Typed resurrection packets reset death animation and the local death screen after successful Blood Gift.

`StandProgress` keeps independent maximum-stamina and regeneration multipliers from original vampire blood tiers. `ProgressionService` gives vampires the source fixed Resolve health boost, vampire-target difficulty multiplier, no Resolve damage reduction, and nine-second Time Stop cap at 80% blood. Learned duration is retained when blood falls; the active duration is capped until blood returns.

`VampireBlood` ports bleeding health reduction and eligible entity types, ordinary physical-hit blood splashes, and mask activation. `VampirePresentation` synchronizes glowing eyes and original Claw animation state without exposing private blood. `VampireHamon` ports retained-strength self-destruction, Hamon Spread, scarf mitigation and training-preservation hooks. Natural Hamon progression remains separate; the operator-only retained-memory command exists for development checks.

Server settings retain original difficulty defaults, blood maximum/drain/decay/healing, full-vampire death retention, vampire mob aggression, optional undead sunlight damage and curing duration. Original client glowing-eye preference is local JSON and sent on join. Minecraft26.3 terrain transparency selects render layers automatically; old explicit cutout/cutout-mipped setters no longer exist.

See `VAMPIRE_SUPPORT_PARITY.md` for zombies, original blood particles, advancements and sledgehammer mappings. Vanilla player render layers replace Forge armor callbacks; original models/textures remain the source of truth. Full compatibility with other animation/renderer mods is not claimed.


`TrackedEffects` preserves the original effect add/update/remove and start-tracking packet hooks. Vanilla does not normally send complete mob effects to observers; the explicit packets are required for frozen mobs, remote vampire smoke and late-tracking clients. A focused client check exposed and verified this integration requirement.

## Draft 14 source-fidelity corrections

Weak vampires can now send the same leap input as full vampires, retaining the source's 0.15 strength multiplier. Input checks blood-tier strength, grounded mounts, curing and immobilization; the server also rejects stunned leap packets. `VampirismPowerType.isLeapUnlocked/getLeapStrength` and `InputHandler` supply these rules.

The common effect-applicability hook now matches original `GameplayEventHandler`, `ImmobilizeEffect`, `StunEffect` and `FreezeEffect`: creative players and existing NoAI mobs reject Stun; cold-immune entities reject Freeze unless they are vampire players. This applies to effects from commands and other powers as well as vampire attacks. Existing stunned mobs do not accept aura refreshes while their AI is disabled, matching the source applicability check. Stun resets an ignited creeper's fuse, using an accessor in place of original reflection.

Eye beams now use the original culled translucent pipeline, camera-pitch normal matrix, side normals and rotated reverse faces. The first-person frozen sleeve audit required no change: modern sleeves are children of arm model parts, so the existing arm submission already includes them. Opening a screen or losing focus still cancels the port's input lease; the suggested automatic discharge was not applied because the source skips input handling while such screens are open and the change was not established as equivalent.

Coffin sleep restores the original `jojo:coffin_sleep` criterion and advancement card. The award requires a sleeping coffin occupant and a daytime-to-night skip; entry alone and night-to-night skipping do not award it. Cure start/end sounds respect silent entities. Original repeated-apple reset, accelerator-loop behavior and integer-division nausea behavior remain intact. Command-driven coffin spawn setting remains an unverified compatibility edge; normal sleep/respawn rules remain implemented.

Verification and delegation evidence are recorded in `05-09-SUMMARY.md`. Full Hamon progression, Pillar Man evolution, separate zombie-player powers and external renderer compatibility remain outside this correction draft.
