# Magician's Red gameplay source ledger

Implementation handed to root; integrated compilation and gameplay remain unverified until the consolidated lane. Original `src/` remains read-only. Natural acquisition stays disabled until gameplay and42 presentation acceptance.

## Source coverage

| Original active path | Fabric path and preserved behavior |
|---|---|
| ModStandsInit MR registrations | MagiciansRedActions: ten source IDs, left punch/flame/fireball/Crossfire; right block/bind/detector; heavy/kick and special replacements; original parts and Resolve unlocks |
| StandEntityAction.onClick and summon builders | Transactional prepareSummon, MAIN_ARM light, ARMS block, FULL other actions;7/15/2 source mode bits; partial damage/durability penalty and precision zero; automatic partial dismissal |
| StandEntity light/heavy/block task inheritance | MagiciansRedEntity: source windup/perform/recovery formulas, alternating hands, finisher increments/decay, heavy snapshot/subtraction, task-owned costs, guard and linked owner damage |
| MagiciansRedEntity.attackEntity/removeFireUnderPlayer | Ten-second fire around melee damage with failed-hit rollback; active MR clears vanilla/custom fire overlapping user's body |
| MagiciansRedFlameBurst.standTickPerform | One flame each perform tick,3 stamina/tick, ±5-degree cone; reach2.5/5 velocity doubled at Resolve3; client sound per tick |
| MagiciansRedFireball.standPerform | Separate fireball, speed2, source precision-adjusted inaccuracy2,75 cost, three perform ticks; source vanilla fire-charge sound |
| MagiciansRedCrossfireHurricane | Twenty-tick charge,500 cost; one normal ankh or eight small aimed ankhs with original angular offsets; normal max training unlocks special |
| MagiciansRedRedBind.stoppedHolding | Landed ribbon remains after release when finisher is ready and keeps its source per-tick cost |
| MagiciansRedKick | Bound target override, stun conversion, three-block capped slide two ticks before perform, ribbon removal one tick before perform,1.2 knockback multiplier and Hell2U voice |
| MagiciansRedDetector + MRDetectorEntity | User-owned toggle within5 blocks;600-tick lifetime,15-block cube search, source integer nearest comparison, six-axis delta, overlap burn and detection sound |
| DamagingEntity/ModdedProjectileEntity | Exact Stand owner, actual Stand-eye origin, damage/speed factors, bounded collision, block permission, source noSave/noSummon, loaded dead-owner removal and Stand-only projectile damage |
| MRFlameEntity |8-tick flame, base damage1,0.1 knockback,10-second fire, OUTLINE ray, no evasion, active water/rain extinguishing, melt and custom fire |
| MRFireballEntity |100 ticks, base2, hardness5, source fire status, destroyed/adjacent block fire; commented extinguishing remains inactive |
| MRCrossfireHurricaneEntity |100 ticks; normal/small contact6/2 times efficiency; active extinguishing, target steering, original large swept-box burning and six-face scan order |
| CrossfireHurricaneExplosion / CustomExplosion | Radius3/1 times efficiency; original ray candidates, exposure damage, motion, exact-owner exclusion and Resolve4 user safety; small form bypasses immunity;0.03125 normal training per eligible entity |
| OwnerBoundProjectileEntity / MRRedBindEntity | Source whole-ribbon ray, attached target height, speed/distance sync, retraction, range10, lifetime7/100/indefinite kick, drag and fractional suffocation; effect-instance-owned cleanup |

## Shared integration

Root registers `MagiciansRedActions.STAND` attributes from `MagiciansRedEntity.createAttributes()`, StandService factory for `jojo:magicians_red`, and calls `MagiciansRedActions.initialize()`. Root owns player START invocation of `MagiciansRedActions.removeFireUnderPlayer`, common control routing, test registration and natural-pool enablement. Existing shared `MagiciansRedFireBlock` from37 is reused unchanged.

Source-independent detector intent uses `Handler.canRunAlongsideHeld` for FLAME_BURST/BLOCK/BIND. It preserves active registry hold and heartbeat. Charged Crossfire cannot be replaced by detector. Other actions may replace those three source-cancellable holds through `canReplaceHeld`. All client intent still passes shared ownership, parts, training, stamina and lifecycle validation.

`StandHeavyImpact.start(this,target,power,precision)` and `punchBlock(this,hit,power,precision)` are shared inherited heavy behavior owned by45/43. MR owns only its fiery hit and projectile behavior. `StandDamage.canDamageBody` supplies55 source body eligibility; reflected projectile damage explicitly permits self-hits. StandConfig scales actual melee/projectile damage once and separates stamina-free settings from creative-only rules. CustomExplosion's original damage path has no Stand multiplier and remains distinct.

Terrain: actual block destruction uses43 capture with actual drops; original no-drop snow/ice/TNT/flammable removal uses `RestorationHooks.remember`. Solid ice→water follows original replacement branch without an invented explicit destruction record. Mode.NONE Crossfire never bulk-destroys or records all ray candidate positions. Active permission/gamerule checks precede mutations.

## Dormant source stays dormant

Fire-consumption charging, lava conversion, fireball water/rain removal and deprecated standalone first-person arms are inactive in the original. No new player acquisition route, save persistence for noSave entities, or alternative source model is introduced.

## Verification

Two grouped MagiciansRedGameTests cover actual registry flame/detector overlap, stamina/projectile hit, ice/fire owner behavior, water distinction, break gamerule/build permission and restoration capture; normal/special charge and eight projectiles, real explosion training/owner protection, actual punch-built finisher, retained bind/kick/effect cleanup and detector dismissal/expiry. These are authored, not yet run by the worker. Root exclusively runs Gradle and real client acceptance.

Model source validation passes6 models/13 outputs. Resource validation passes37 outputs including the original fireball item conversion. The25-resource fragment pins original assets; all13 referenced JoJo sound keys were checked against original sounds.json, with source aliases resolved correctly.
