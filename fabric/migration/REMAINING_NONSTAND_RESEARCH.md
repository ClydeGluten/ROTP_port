# Remaining non-Stand powers: source audit

Read-only gameplay audit of original Forge `src/`, 2026-10-18. Fabric source inspected for integration boundaries; no production changes or tests. Scope: zombie-player and Pillar Man. World structures and other Stands are outside this audit.

## Active versus dormant

`JojoCustomRegistries.initCustomRegistries()` explicitly loads `ModZombieActions` and `ModPillarmanActions`; these are active registrations, not abandoned source files. Registry IDs: `jojo:zombie`, `jojo:pillarman`. There are **3 zombie actions and 22 Pillar Man actions**, including shift variations. Every registered Pillar action has an active default/HUD or shift-variation route.

Baseline acquisition found in active callers is administrative `/jojopower give <players> <type>` (`JojoPowerCommand`). No survival caller granting either baseline power was found. Do not invent vampire bites, zombie-player infection, temple rewards, or species selection. `HungryZombieEntity` and existing Fabric `VampireZombieService` concern mobs, not zombie-player powers. `ZombieData` remembering the turning vampire UUID is only a TODO.

Pillar progression is stage 1 initially; existing Stone Mask upgrades 1→2; Aja Stone Mask upgrades any stage <4 directly to 4 and chooses WIND/HEAT/LIGHT uniformly. `BleedingEffect.applyStoneMask()` is the live caller. Aja on non-Pillar players causes 1000 mask damage. Peaceful disallows masks. Stage 3 exists and is accessible through `/pillarman set stage <players> 1..4`, but has no normal progression route. Proposed future prototype masks, stage 5, Ultimate Thing are comments only. Mode commands can set a mode at any stage; mode actions themselves check mode, not stage 4. Preserve this distinction.

Original quirks requiring explicit decisions, not silent redesign: `PillarmanData.setEvolutionStage()` triggers Aja evolution advancement for stage **3**, although mask jumps to **4**. Heavy Punch and Zombie Claw register energy costs but override `consumeEnergy()` with an empty method; runtime checks require sufficient energy, then do not spend it. Evasion afterimages/camera roll are commented out. Light Flash Decoy provides invisibility, not a decoy entity. Existing possession implementation is active but limited spectator-camera possession, not body control.

## State, progression and passives

Original reference root below: `src/main/java/com/github/standobyte/jojo/`.

| System | Exact source/method | Observable contract |
|---|---|---|
| Shared energy | `power/impl/nonstand/NonStandPower`: `BASE_MAX_ENERGY`, `tickEnergy`, `writeNBT`, `keepPower` | Base 1000; clamp energy to [0,max]; persist Type/Energy/AdditionalData/HadPowers plus inherited state. Acquisition adds 1000. Mutually exclusive non-Stand powers; Stand slot remains independent. |
| Zombie resource | `type/zombie/ZombiePowerType`: `getMaxEnergy`, `tickEnergy`, `bloodLevel` | Max=1000×difficulty maxBloodMultiplier. Decay=bloodTickDown[difficulty], suppressed in creative. Blood level=min(floor(energy/max×7.5),4)+difficulty; Peaceful=-1. |
| Zombie passives | `ZombiePowerType.getPassiveEffectLevel`, `tickUser` | Health Boost amplifier=difficulty×2; Night Vision 0. Strength/Speed/Haste/Jump amplifier=bloodLevel−5; disabled when disguised. No passive regeneration. Food forcibly 17 and air full. Hidden passive effects follow shared removal/refresh rules. |
| Zombie lifecycle | `ZombieData.writeNBT/readNBT/toggleDisguise`, `ZombiePowerType.keepOnDeath` | Persist `DisguiseEnabled`; reset disguise when dead. Power lost on actual death, retained on non-death clone. No cure or stages. Nonreplaceable. |
| Pillar attributes | `type/pillarman/PillarmanData.updatePillarmanBuffs` | Attribute multiplier L=2×stage, minus1 at stage3, minus2 at stage>3: L=2/4/5/6. Add attack L, attack speed .15L, movement .01L, swim speed .15L, max health 10L. Thus max-health bonuses +20/+40/+50/+60. Clear removes these modifiers. |
| Pillar resource | `PillarmanPowerType.getMaxEnergy/tickEnergy` | Max=1000×maxBloodMultiplier×stage; decay=bloodTickDown×stage, no creative loss. All food gives nutrition×10 energy (`GameplayEventHandler.onFoodEaten`). |
| Pillar physiology | `PillarmanPowerType.tickUser/getPassiveEffectLevel` | Stage>1: food 17, full air, Night Vision0; Regeneration1 at energy ratio≥.3, Regeneration0 at≥.1, absent below. Stage1 gets none of these. |
| Pillar lifecycle | `PillarmanData.writeNBT/readNBT/tick`, `PillarmanPowerType.keepOnDeath` | Persist `PillarmanStage`, `PillarmanMode`, `StoneForm`; retain power on death. Blades and TNT fuse list are transient; dead player clears stone/blades. Sync stage/mode/stone/blades to tracking clients. Nonreplaceable. |
| Stone form | `PillarmanData.tick` | Refresh STUN20t, Resistance40t amplifier3, Blindness40t, Fire Resistance40t; blocks most Pillar actions. Sun/UV and Hamon immunity checked in `DamageUtil`; entity/first-person stone texture and hidden outer skin layers. |
| Leap | both `*PowerType.getLeapStrength` | Zombie=max(bloodLevel,0)×.2; Pillar=(2+min(stage,2.25)/2)×.6. Both unlocked, free, cooldown20t. |
| Stand interaction | both `*PowerType` stamina/resolve methods | Zombie stamina max factor=max((bloodLevel−4)×2,1), regeneration=max((bloodLevel−4)×4,1); target resolve factor1. Pillar max/regeneration factors=stage; target resolve=stage. Original TimeStop high-life-force predicate=Pillar stage>1; zombie high saturation=energy/max≥.8. |

Shared difficulty config can reuse Fabric `VampireConfig`: original defaults max multipliers 1/1/1/1, drain 0/1/1.75/2.5, decay .13889/.00278/.00278/0, healing costs 10/4/2/1.

## Zombie action map

Registry: `init/power/non_stand/zombie/ModZombieActions.java`. Classes: `action/non_stand/Zombie*.java`.

| ID suffix | Methods / contract |
|---|---|
| `zombie_claw_lacerate` | Free main hand, nominal cost60 (checked but not consumed), blocked by disguise. `Instance.playerTick`: swing/sound3, hit5, stop8; walk×.5. Calls `VampirismClawLacerate.punchPerform`: damage=attack attribute+4, knockback2, collision impact, block destruction. |
| `zombie_devour` | Held, free main hand, range2, Peaceful disabled, blocked by disguise; only living canBleed and non-undead/vampiric targets. Calls `VampirismBloodDrain.drainPerform(...,.5,false)`. Damage2 per successful damage interval; difficulty drain multiplier ×5 player / ×4 NPC or illager / ×1.5 Hamon user, reduced by Freeze. Adds that energy, heals half that amount, then refunds healed HP×configured healing cost as energy. Zombie healing itself has no blood charge. Victims get stacking Slowness/Fatigue/Weakness/Nausea duration40t per hit and amplifier difficulty−1. On kill, eligible targets become **untamed** hungry zombies; original shared drained/created stats still increment. Other action allowed during hold; incoming damage cancels hold. |
| `zombie_disguise` | Hold60t then toggle. No energy cost. Disables combat buffs and Claw/Devour. Alternate enabled icon/label and green selection. Undisguised body uses `sated_zombie.png`; disguised zombie uses vampire glowing eyes subject to client eyes setting. |

Meat consumption additionally gives nutrition×10 energy and heals nutrition (`GameplayEventHandler.onFoodEaten`). All food is not equivalent here.

## Pillar action map

Registry: `init/power/non_stand/pillarman/ModPillarmanActions.java`; classes `action/non_stand/Pillarman<Suffix>.java`. All timings below are ticks. Stage/base action unlocks are in constructors and `PillarmanAction.isUnlocked/checkConditions`; HUD routing is `PillarmanPowerType.clAddMissingActions`. Initial attack Heavy Punch, ability Stone Form, quick-access Absorption (locked at stage1). Stage2 adds base suite. Shift links: Evasion→Unnatural Agility, Small Sandstorm→Divine Sandstorm, Light Flash→Decoy, Heavy Punch→Blade Slash.

| Suffix / registry tail | Gate; configured cost/hold/cooldown | Active implementation |
|---|---|---|
| HeavyPunch / `heavy_punch` | any; cost10 check only | Same punch helper as zombie; swing3/hit5/stop8, walk×.5. Stage>1 extends original block punch footprint. |
| Absorption / `absorption` | stage2, stone allowed; held/free | Range2, non-Peaceful; any living target, unlike blood-drain filter. `absorb`: Hamon contact protection may block; damage2, four drain debuffs40t amplifier1 per hit; successful hit grants35×difficulty drain multiplier. Other action allowed while held. |
| HornAttack / `horn_attack` | stage2, stone allowed;15/CD60 | Spawn owner-bound `pm_horn`, lifespan40; damage.5 through invulnerability, zero knockback, speed.4, retract×3, hardness≤5. |
| RibsBlades / `ribs_blades` | stage2;60/CD80 | Spawn eight `pillarman_ribs`, lifespan21, damage.5 through invulnerability; first successful nonblocked hit attaches and stuns remaining lifespan; attachment extends lifespan20. |
| HideInEntity / `hide_in_entity` | stage2;hold20/walk0 | Living target other than self; `IPlayerPossess.jojoPossessEntity(target,true,this)`. Save previous game mode; switch spectator+target camera; restore on dismount/target death. Powers unavailable while spectator. Possession state has separate persisted entity resolver/context. |
| StoneForm / `stone_form` | stage1, stone allowed;hold40/walk.5 | Toggle stone; hide blades; passives above. |
| Regeneration / `regeneration` | stage2, stone allowed;40/CD20 | Regeneration amplifier4 for60t, uses `HamonHealing.updateRegenEffect` merging; blood is also charged for actual healing. |
| EnhancedSenses / `enhanced_senses` | stage2, stone allowed;.05/t/walk.5 | Client-only orange outlines80t, expanding radius=min(heldTicks×.5,36), initial scan each tick then each20 after160. |
| Evasion / `evasion` | stage2;max50/1.5 per t/walk5/CD50 scaled with hold | `PillarmanUnnaturalAgility.onUserAttacked` cancels eligible nonexplosion entity/projectile attacks. Cannot evade unseen Stands or nonevadable custom projectiles. Animations active; planned afterimages dormant. |
| UnnaturalAgility / `unnatural_agility` | stage2;1/t/walk0 | Same evasion; ordinary living melee attacker has30% chance to receive .75×barehand attack counter. |
| SmallSandstorm / `small_sandstorm` | WIND;20/CD10 | Projectile radius1.5/damage6/life40/speed1.5. |
| DivineSandstorm / `divine_sandstorm` | WIND;windup40/walk.2, then3/t | Every2 ticks after charge emits radius1.5/damage2/life10/speed.9 projectile. |
| WindCloak / `wind_cloak` | WIND;max200/2 per t/CD100 | Refresh Invisibility and Sun Resistance5t; cloak layer and start/stop particles. |
| AtmosphericRift / `atmospheric_rift` | WIND;windup40/walk0, then3/t | Every2 ticks emits atmospheric radius.5/damage2/life60/speed1.75 projectile; hurts self1 armor-bypassing unless creative. |
| ErraticBlazeKing / `erratic_blaze_king` | HEAT;20/walk.1 | Continuous action: tick10 emits10 veins, each life25; stop40. |
| GiantCarthwheelPrison / `giant_carthwheel_prison` | HEAT;125/hold30/walk0/CD100 | Emits32 veins; Fire Resistance600t. Preserve misspelled registry `carthwheel`. |
| SelfDetonation / `self_detonation` | HEAT;150/hold60/walk0 | Custom explosion radius3, filters self/allies, damages/breaks/ignites; noncreative self damage40 and Fire Resistance200t. Places boiling-blood fluid and melts ice/snow; respects original griefing behavior. |
| LightFlash / `light_flash` | LIGHT;25/hold40/walk0/CD80 | Visible targets within original radius16 query get Blindness200t if distance<5, else80t; nonplayer/nonStand also STUN60t. Blades visible during charge. |
| LightFlashDecoy / `light_flash_decoy` | LIGHT;40/hold50/walk0/CD120 | FULL_INVISIBILITY100t, flash emitter, no decoy spawn. Original renderer cancels complete living rendering for this effect. |
| BladeSlash / `blade_slash` | LIGHT;free hand/10 | Blades2, swing6, hit8, end20; damage=(attack+4)+1 with15 armor piercing; knockback.75 and collision impact. |
| BladeDashAttack / `blade_dash_attack` | LIGHT;50/windup10/walk0 | Ground-only; original overrides maximum hold to unlimited despite builder40. Leap based on movement speed;15t sweeping hitbox, one hit per target, armor-piercing slash damage, complete incoming-damage cancellation during action. |
| BladeBarrage / `blade_barrage` | LIGHT;free main hand/1 per t/walk.2 | Per-tick target damage=.2×barehand attack through invulnerability, block breaking, blades/sparks. `onUserAttacked` handles projectile defense; unlike evasion, does not permit Stand-damage custom projectiles. |

Projectile dependencies: `PillarmanDivineSandstormEntity` survives entity hits, fades damage to floor50%, hardness≤5, cannot be deflected/evaded. `PillarmanVeinEntity` damage.35 through invulnerability, burns10s, retracts on successful hit, speed16/lifespan; block hit melts ice/snow or places level4 boiling blood. Original owner-bound projectile infrastructure supplies retract/attachment/body-part behavior and damage filtering; a generic thrown projectile is not faithful.

## Shared hooks that must change

1. `JojoModUtil.playerUndeadAttribute`: zombie counts as vanilla undead; Pillar does **not**. `isUndeadOrVampiric` nevertheless includes both through separate predicates. Zombie therefore shares undead potion/Smite behavior; do not mark Pillar undead indiscriminately.
2. `VampirismUtil.tickSunDamage` / `DamageUtil.entityTakesUVDamage`: both suffer sunlight4 and accumulating Sun Burn/Weakness; Pillar stone immune, Wind Cloak Sun Resistance protects sun, coffin sleeping protected. Sun Burn prevents healing. Hamon affects both; Pillar damage×.5, stone immune (`DamageUtil.dealHamonDamage`).
3. `VampirismUtil.consumeEnergyOnHeal`: Pillar stage>1 pays configured blood/HP; stage1 and zombie do not. This differs from existing vampire helper.
4. `VampirismUtil.editMobAiGoals`: monsters neutral to zombies and to Pillar stone or stage>1 when config says so. Iron golems target Pillar through vampiric predicate; original extra golem predicate does not include zombie-player.
5. `GameplayEventHandler.onEntityInteract`: HEAT may eat primed TNT or primed TNT-minecart explosive; replace minecart with normal one preserving data; play eating sound then quiet explosion sound when saved transient fuse expires, without explosion damage.
6. Existing Fabric `VampireService.activateMask` currently kills every Aja wearer; route existing Pillar progression before this branch. `grant`/mask/blood gift and `HamonService.grant/learn` need shared exclusivity checks. Add remaining types to `/jojopower` and original `/pillarman` command.
7. Extend shared damage/passive/food/air/AI hooks and `StandService` stamina integration through small predicates. Existing `VampireService.drain`/private claw routines are vampire-specific; extract shared mechanics with explicit species parameters or implement faithful wrappers. Do not make zombie use the current tamed-drained-kill callback. `VampireBlood`, collision impacts, Hamon contact protection, `VampireCombatService.STUN`, sounds, mask items, scarf and config are reusable.
8. Existing Fabric has no possession service found. New possession module must restore game mode/camera on escape, death, disconnect and invalid target, with tracking/persistence adapted from `PlayerEntityMixin`/`ServerPlayerEntityMixin` and `TrPossessEntityPacket`.

## Smallest faithful implementation boundary and ownership

Suggested isolated modules; integration owner controls shared existing files to avoid concurrent edits:

- **Zombie owner:** new `ZombieProgress`, `ZombieAttachments`, `ZombieService`, `ZombieActionPayload`, client HUD/actions/skin layer. Reuse original assets and vampire animation machinery with 8t action duration. Approximately1–2 execution plans; no new gameplay entity required.
- **Pillar core owner:** `PillarmanProgress`, attachments/service/commands/catalog/payload; stages/masks/stone/base actions/evasion/senses; separate possession service. Approximately2–3 plans.
- **Pillar projectile/mode owner:** owner-bound horn/rib/vein entities and renderer adapters, sandstorm entity, mode action service, boiling blood block/fluid bridge, full invisibility/sun resistance. Approximately3–4 plans. Heat fluid and possession are the largest dependencies.
- **Presentation owner or later phase:** original animations, stone/blade/cloak/sandstorm layers, local senses outlines, particle providers, action HUD/shift variations, first-person hand layers, sound loops, advancement parity. Approximately1–2 plans; copy/translate original assets rather than invent placeholders.
- **Integration owner:** existing `RipplesFabric`, commands/client bootstrap, `HamonService`, `VampireService`, `VampireHamon`, `VampireZombieService`, `StandService`, shared mixin hooks and asset registration. Add centralized non-Stand species query to prevent independent attachments allowing combinations. Approximately1 plan including targeted smoke verification.

Estimate: **8–12 focused execution plans** for full gameplay and presentation parity, roughly35–55 new Java files depending consolidation, plus shared integration edits/assets. This is a sizing estimate, not a measured implementation duration. Zombie alone is considerably smaller. Existing-project entity/animation utilities may reduce count; blindly cloning services increases it. Minimum lean checks: compile once per integrated slice; one targeted lifecycle/action smoke each for zombie, Pillar base and three modes; manual client pass for controls/layers/possession. No broad test suite was run for this audit.
