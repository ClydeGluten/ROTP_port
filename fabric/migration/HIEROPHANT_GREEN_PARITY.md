# Hierophant Green gameplay source ledger

Gameplay implementation prepared for the shared integration lane; world behavior and presentation remain unverified.

Authoritative source: original Forge tree at `72862a5826ed45d1dc26e90b37853097acda35de`, kept read-only. Target: pinned Minecraft26.3/Fabric/Java25.

## Inspected active behavior

- `HierophantGreenStringAttack.standPerform/addProjectile/getStandActionTicks` and `MathUtil.xRotYRotOffsets`: eight attack strings or four binding strings, unit-radius ring at depth10 plus center, lifespan scaled by attack speed divided by8 with minimum0.125.
- `HGStringEntity`: damage1 or0.5 once per string; bypass damage immunity; zero ordinary knockback; binding requires an unblocked living target and adds ten lifespan ticks; zero-hardness block breaking and retraction inherited from owner-bound base.
- `HierophantGreenEmeraldSplash.standTickPerform`: source fire-rate scaling, concentrated rate2.5, velocities2/3, spread8/1, concentrated block breaking; both variants trigger barrier emission. Normal splash maximum training unlocks concentration.
- `HGGrapplingStringEntity`: four-block-per-tick extension, held-action lifetime, forty ticks before attachment; block pull2, entity pull1, stop within2 blocks, block grapple attaches a barrier only if a previous barrier anchor exists.
- `HierophantGreenEntity`: remote-control user tether, connected anchors, fifteen barriers or one hundred at resolve4, triple return speed for an unattached user tether.
- `HGBarriersNet`: points every8 blocks (one midpoint for segments up to16), choose nearest target points separated by8; pay stamina per selected point before source-scaled emerald batch; barrier emerald velocity1.5, inaccuracy2, damage factor0.75, lowered knockback. Remove dead/ripped barriers each tick.
- `HGBarrierEntity.tick/hurtTarget/wasRippedAt`: attached-segment damage2 without bypassing hit immunity, victim velocity reset, rip point synchronization, forty-tick rip lifetime, owner damage0.2 once, counterburst20×stamina condition with1.5 stamina per point and2-block exclusion. Stopped-time crossing records only the rip; damage/lifetime resume with time.
- `StandEntityAction.onClick/afterClick` and `EntityStandType.summon/finalizeStandSummonFromAction`: provisional action summon before requirements, successful world insertion, failed provisional rollback, existing arms-to-full promotion retained on later rejection. Registry preparation hook preserves this ordering.
- `StandEntityAction.Builder.autoSummonMode` defaults FULL. Only `StandEntityBlock` changes it to ARMS; splash/grapple required-part metadata does not alter this default. All seven other HG actions summon/promote full body.
- `StandEntity.setArmsOnlyMode/fullSummonFromArms/onArmsOnlyModeUpdated/clearTask`, `StandEntityUnsummon.standTickPerform`, and `StandStatFormulas.getSummonLockTicks`: synced original arm flags, arm-only damage/durability×0.75 and precision0, initial full summon lock, gradual full-summon modifier removal, source7/3-tick arm fade lengthened by low stamina. Arms use offset(0,0,0.15); full uses source idle/front offsets.
- `StandEntityTask.tick`, inherited action conditions/cancellation and `PowerBaseImpl` resource order: finite tasks own windup/perform/recovery, source task queue, binding-to-splash cancellation, source concentrated unlock, separate generic held lease, per-PERFORM payment and no duplicate registry debit.
- `DamagingEntity.onHitEntity/hurtTarget/canHitEntity`, `ModdedProjectileEntity.tick`, `OwnerBoundProjectileEntity.tick/moveBoundToOwner/rayTrace`: owner damage credit, source immunity bypass where appropriate, constant emerald motion, Gaussian1.16 spread, accepted-hit ignition, whole-body segment tracing, inherited attachment/retraction and source hardness limits. Modern block protection callbacks are preserved.
- `StandEntity` statistics/control/guard/damage inheritance: source stamina and range efficiency, development-scaled stats, projectile power/speed factors, physical resistance, guard counter/guard break, Stand-only damage acceptance, owner transfer, remote user tether and accelerated return cleanup.
- `ModEntityTypes` registration flags: all four projectiles preserve noSave/noSummon and update intervals; barrier tracking omits velocity updates. Stand manifestation is session-scoped through the shared Fabric lifecycle; source Stand entity serialization is replaced by persistent owner progress and action training/cooldowns.

## Integration and bounded computation

`HierophantGreenActions.initialize()` registers eight ordinary action definitions, the real entity factory, all four projectile entities and concentrated unlock reconciliation. `Handler.ownsTaskLifecycle` leaves entity phases/costs under HG authority while the registry retains ownership, learned-action, body-part, cooldown, initial affordability and held-lease gates. No client-supplied target, damage, stamina or training is accepted.

Root connects `HGBarrierEntity.tickFrozen()` at the existing frozen entity interception; bound bodies follow their owner's frozen state. Root's Resolve classification includes the shared Stand-damage marker for projectiles. Root registers `HierophantGreenGameTests` and installs40 renderers before the complete71 acceptance enables the playable/natural pool. This plan does not enable that pool.

The network stores analytic equally spaced segments and interval exclusions instead of source quadratic point-distance tables. Every original shooting point remains eligible, including long segments; each shot chooses the nearest nonexcluded point with the original strict minimum separation. Per-tick point budget is bounded by the source maximum20. Anchor/world ownership checks and source fifteen/hundred segment limits remain authoritative.

## Source-corrected plan wording

Plan39 says per-projectile barrier spending. Active `HGBarriersNet.shootEmeraldsFromBarriers` spends once per selected point before that point's rate-scaled emerald batch. Implementation follows the original active method. Firing-point geometry is captured when the barrier is placed, before its newly attached end moves next tick, matching the source ordering.

## Assets inspected

Gameplay registration and referenced entries in `assets/jojo/sounds.json`: HG summon/unsummon, tentacles, emerald splash, barrier placement/rip, Kakyoin emerald-splash shout. Original eight action icons and HG body/projectile textures are supplied unchanged by the existing resource copy. Geometry, glow, poses, detection icon and client sound audience are tracked by40/55.

## Source-inactive behavior

- `HierophantGreen20mEmeraldSplash` is an empty class; no ninth gameplay action is invented.
- The commented special20m shout override in `HierophantGreenEmeraldSplash` is inactive.

## Verification

Three actual-world GameTests are supplied: (1) registered four-string binding and eight-string attack, collision/immobilization, moving owner origin, wall retraction, dismissal cleanup, rejected provisional summon and automatic arms fade; (2) emerald hit immunity,0.002 learning/max-trained concentration, source concentrated projectile/cost behavior and actual player save/load of training/cooldown; (3) connected barrier point funding at zero/full stamina, real crossing/rip/owner damage/pruning, block grapple pull/release and no-save flags. They have not been run on this implementation. No worker Gradle. Root owns compilation and integrated/client world acceptance.
