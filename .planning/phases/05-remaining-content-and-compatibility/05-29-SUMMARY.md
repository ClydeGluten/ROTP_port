---
phase: 05-remaining-content-and-compatibility
plan: 29
subsystem: pillarman-body-parts
tags: [fabric, projectile, owner-bound, pillarman]
status: awaiting-integration-verification
requires: [05-23]
provides: [owner-bound-projectile-base, pillar-horn, pillar-ribs, original-body-part-renderer]
affects: [05-31, 05-32, 05-34, 05-36, later-stand-strings]
tech-stack:
  added: []
  patterns: [owner-origin-segment, persistent-entity-attachment, original-model-cubes]
key-files:
  created:
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/stand/OwnedBodyPartEntity.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/pillarman/PillarBodyParts.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/pillarman/PillarHornEntity.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/pillarman/PillarRibsEntity.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/pillarman/PillarBodyPartRenderer.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/PillarBodyPartGameTests.java
    - fabric/migration/PILLAR_BODY_PARTS_PARITY.md
  modified: []
key-decisions:
  - Preserve original predictive sorted multi-entity segment ray and block-fallback quirk.
  - Preserve fixed original textures instead of inventing player-skin texturing.
  - Persist attachment UUID and rib one-hit flag to make required save/load behavior correct.
actuals:
  tokens: 12294
  tasks: 2
  commits: 0
plan_head_before: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
plan_head_after: 69e4aaa4aa2b6d234bac292087fbfd9bea7eeae0
completed: 2026-10-18
---

# Phase 05 Plan 29: Pillar horn and ribs source implementation

Reusable owner-bound segment lifecycle with original horn retraction, one-hit rib attachment/stun, and exact repeating model cubes/textures.

## Work delivered

Task1 implements owner lookup, body/head origin transforms, extension/retraction, entity/block attachment, collision, damage, save/load, owner cleanup and the stage2 horn action. Horn uses cost15/CD60, life40, speed.4, retract1.2, pause4, damage.5 through immunity, zero knockback and hardness≤5.

Task2 implements eight exact rib origins/angles, cost60/technical CD80,21-tick life with20-tick attachment extension, one successful hit, shield/Stand-guard attachment rejection and remaining-lifetime stun. It also supplies the original32×32 model geometry and textures, copied through current render-state submission rather than generic arrows.

Three actual-world GameTests cover repeated horn hits/immunity/velocity/retraction, terrain and owner dimension/death, and rib count/cost/attachment/stun/real save-load/one-hit persistence. **No worker Gradle, GameTest or client run occurred.** Root's prior216 server checks predate these new files and do not verify05-29.

Source is staged and compile-ready. Cached whitespace checks pass. Root briefly encountered `Entity.isInvulnerableTo(ServerLevel,DamageSource)` while the source set was being written; read-only local `javap` established `Entity.isInvulnerableToBase`, and the correction is staged. No successful05-29 compile is claimed here.

## Exact method and asset coverage

Complete method-pair ledger: `fabric/migration/PILLAR_BODY_PARTS_PARITY.md`.

Read the full active `PillarmanHornAttack`, `PillarmanRibsBlades`, `PillarmanHornEntity`, `PillarmanRibEntity`, `OwnerBoundProjectileEntity`, `ModdedProjectileEntity`, inherited `DamagingEntity` hit/filter/save methods, `DamageUtil.hurtThroughInvulTicks`, `JojoModUtil.rayTraceMultipleEntities/isTargetBlocking`, `MathUtil.xRotYRotOffsets`, source registrations and technical/additional cooldown accessors.

Renderer references read in full: `PillarmanHornModel`, `PillarmanRibModel`, `RepeatingModel`, `PillarmanHornRenderer`, `PillarmanRibRenderer`, `ExtendingEntityRenderer`, `SimpleEntityRenderer`. Original texture resources inspected for existence/dimensions: `assets/jojo/textures/entity/projectiles/pm_horn.png` and `pillarman_ribs.png`, both32×32 RGBA. Assets remain unchanged. No visual client verification was performed.

## Root integration entrypoints

- `PillarBodyParts.initialize()` registers public `HORN`/`RIBS` types; no shared event registration occurs in this module.
- Payload routes call `PillarBodyParts.horn(ServerPlayer)` / `ribs(ServerPlayer)`. Both return boolean and enforce all source gates. These concrete entrypoints replace the plan's nonexistent generic `registerAction` hook, as root authorized before implementation.
- Root calls `PillarBodyParts.tick(player)` once per server tick; `clear(entity)` for lifecycle cleanup; `clearAllRuntime()` on server stop. `hornCooldown` and `ribsCooldown` expose authoritative current cooldowns.
- Root's damage-knockback hook uses direct `OwnedBodyPartEntity.knockbackMultiplier()` (zero for both). Main code additionally restores pre-hit server velocity; the root hook prevents a client knockback packet from diverging.
- Client calls `PillarBodyPartRenderer.initialize()` for both registrations.
- Root discovers `PillarBodyPartGameTests` and runs integrated server/client checks only after the full source set is ready.
- Later owner-bound subclasses reuse `origin/bodyOrigin`, motion parameters, attachment methods, `damageSource`, target filtering, save/load and dragging. Current horn/ribs implement the existing Pillar evasion projectile interface and are non-Stand damage. Future Stand subclasses must supply their own special damage/skin rules.

## Deviations and source corrections

1. Original `OwnerBoundProjectileEntity.readAdditionalSaveData` checks `BoundTarget` but writes `AttachedEntity`; adapter reads the actual attachment key. This fixes required real save/rejoin attachment recovery.
2. Original rib `dealtDamage` was transient; adapter persists it to prevent a saved attached rib from regaining its one-hit attack. This is a correctness fix at the save boundary.
3. Plan wording mentions owner skin, but actual horn/rib renderers use fixed projectile textures. Preserved original textures; owner still supplies body rotation, light and visibility.
4. Numeric saved state is finite/bounded; owner/target dimension changes remove stale segments. Client intents never supply targets, owners, damage or resource amounts.
5. As authorized, worker stages only. No commit/push, STATE/ROADMAP edits, original-source changes or dependency installation. Zero commits is intentional, not evidence of a committed delivery.

No intentional gameplay stubs or skipped test declarations were introduced. The empty base hit callback and registration initializer are subclass/registry lifecycle contracts, not unfinished behavior. Future Stand strings, heat veins and their custom damage/rendering remain their own plans.

## Metrics and self-check

Actual tokens:49,176 characters/4 =12,294 over seven realized new files, excluding this metadata. Two tasks implemented; zero commits. All seven source/test/ledger files exist and are staged, with no changes to other workers' files. The initial05-23 swing signature repair was separately authorized and staged before this plan began.

## Self-Check: PASSED (source handoff only)

All assigned files exist; staged whitespace check passes. Three real scenarios and client renderer remain unverified until root's consolidated lane passes. Do not mark full parity or plan verification complete from this source handoff alone.
