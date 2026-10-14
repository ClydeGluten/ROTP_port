# Draft 10: original time-action behavior

Original behavior is the project-wide default by explicit user instruction on 2026-10-14. The original Forge tree is a read-only reference. Explicit custom requirements, including Stand allocation generations and independent third-person camera behavior, remain intentional exceptions.

## Source mapping

| Behavior | Original source | Fabric adapter |
| --- | --- | --- |
| Immediate health damage, attribution and death | `StandEntity.hurtTarget`, `DamageUtil.hurtThroughInvulTicks`, `GameplayEventHandler` | `StandDamage`; ordinary armor, invulnerability and PvP checks still apply |
| Frozen momentum accumulation | `GameplayEventHandler.knockback`, `DamageUtil.applyKnockbackStack` | Time Stop `LivingEntityMixin` replaces only frozen server knockback; resistance applies once, horizontal velocity is not halved, vertical impulse caps at 0.4 even airborne |
| Extra The World heavy impact at thaw | `TheWorldHeavyPunch.TheWorldHeavyPunchInstance.afterAttack`, `EntityUtilCap.queueOnTimeResume` | Entity-local `TimeStopService` callbacks; only the extra impact sound waits, never health damage; removed entities discard callbacks |
| Instant Time Stop for both Stands | `TimeStopInstant.perform`, `calcBlinkPos`, `getMaxImpliedTicks` | `TheWorldActions.blink`: Star Platinum stops in front of its target, The World goes behind the target's facing; block top/face and miss-to-ground rules preserved |
| Cost and cooldown | `StandAction.onPerform`, `TimeStopInstant`, `TimeStop` | Blink debits 180 upfront, then 720 / learned duration per implied tick. The original duration calculation reserves the upfront amount again and permits at least five ticks even with depleted stamina. No Blink learning. Its cooldown is truncated full-stop cooldown / 6; existing full-stop cooldown remains independent |
| Implied time | `TimeStopInstant.skipTicksForStandAndUser`, `TrNoMotionLerpPacket`, `TrDirectEntityPosPacket` | Advance user/Stand clocks without simulating ticks; tracked, dimension/UUID-scoped packets snap relocation and suppress interpolation for three client ticks |
| The World Time Stop Punch | `TheWorldTSHeavyAttack` and `StandEntityTask` | User/manual-Stand aim, Shift backshot, retained target, five real windup ticks, recovery cancellation including barrage recovery, ten implied windup ticks plus twenty when replacing recovery; 90 + implied ticking cost at start and 50 heavy cost at impact; own 50-tick cooldown (Creative zero); learning truncates before the Resolve fourfold multiplier |
| Manual resume | `TimeResume.perform`, `TimeStopInstance.tickSounds` | First click shortens to eleven ticks; next click ends immediately. Resume sound starts with ten ticks remaining |
| HUD variants | `TimeStopInstant`, `TheWorldTSHeavyAttack.getIconTexturePath` | Original SP/TW Blink icons, backshot icon, original names and separate cooldown indicators; Shift on selected Time Stop or direct U |

## Platform adaptations and remaining scope

Travel checks loaded chunks, world border, build height and the entire entity box. Modern minimum Y replaces the old zero-Y ground-search limit. The original Time Stop Punch low-budget distance formula contains an apparent precedence error; the port bounds travel to the affordable implied distance instead of reproducing possible overshoot. Collision shortening does not refund the intended Time Stop Punch's implied time. Target retention never bypasses current range, line-of-sight or PvP checks.

Frozen players receive health updates while their simulation tick is paused. Disconnect cleanup is dispatched to the server thread because Fabric can notify listeners from Netty; tests cover this race and idempotent callback draining. Client and server clocks advance for instant moves; no world ticks, cooldown timers, effects or AI are fast-forwarded. Manual-control anchors move with owner Blink, and an explicit Blink turn updates the independent camera orbit.

This slice does not claim full Time Stop parity. Overlapping regional stops, immunity/perception rules, spectator-specific sound filtering, post-processing shaders, the original time-stop voiceline selection, souls and vampire-specific durations remain unported. The existing safe cancellation on dimension change is retained. Knives, broader combat parity and Hamon remain later milestones.
