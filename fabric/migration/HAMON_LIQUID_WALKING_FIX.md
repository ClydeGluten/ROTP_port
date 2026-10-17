# Draft 16.1 liquid-walking crash fix

## Report and cause

The user's `fabric/run/crash-reports/crash-2026-10-17_23.35.49-server.txt` and `crash-2026-10-17_23.36.12-server.txt` show `StackOverflowError: Ticking player`. The earlier `crash-2026-10-17_15.56.48-client.txt` shows the same loop on the client.

The call chain is `HamonMovement.liquidWalking` → `Entity.setOnGround` → `checkSupportingBlock` → fluid collision → `LivingEntity.canStandOnFluid` → `HamonMovement.liquidWalking`.

The original `HamonLiquidWalking.isLiquidWalking` calls `entity.setOnGround(true)` from the original `EntityLiquidWalkingMixin`, after vanilla collision calculation. The port incorrectly called it from `LivingEntity.canStandOnFluid`. Minecraft 26.3's ground setter searches supporting blocks, re-entering that predicate. Local cached Minecraft bytecode confirmed the loop. It also showed that ordinary living entities return an empty `getLiquidCollisionShape`, so merely avoiding recursion does not supply a walking surface.

## Correction and compatibility

Restored the retained original `EntityLiquidWalkingMixin` as `HamonLiquidWalkingMixin`, keeping its four displaced bounding-box corners, fluid-height search, grounded step allowance, touching-fluid rejection and fall reset. Modern `ModifyExpressionValue` adjusts the result of `Entity.collide` inside `move`, the same point as the original local-variable injection. Original attribution and comments are retained.

Removed the side-effecting `canStandOnFluid` injection. Hamon eligibility, energy and lava damage now run from the original fluid-movement algorithm. Ground assignment is conditional to avoid redundant modern support searches. Existing per-tick accounting retains one initial 50-energy payment and a 1-energy consecutive payment without four-corner duplication.

No skill, cost, fire exclusion, escape input, progression or persistence rule changes. The original Forge source remains read-only. Existing draft saves require no reset. Dependency versions remain unchanged.

## Focused regression coverage

One server regression uses real water/lava blocks and player movement. It checks airborne entry, side-effect-free grounded support queries, landing on the fluid surface, initial/consecutive energy costs, lava damage, empty-energy rejection and presentation cleanup. The existing Hamon client flow now lands and remains on water in both integrated and dedicated worlds, including client-side airborne movement.

The previous flat-floor Hamon client fixture never exercised fluid support, so the successful Draft 16 suite missed this recursion. The new checks exercise the actual crash path without a separate broad client matrix.

The first regression run confirmed recursion was gone but failed the real-landing assertion, exposing the missing fluid-movement algorithm. Restoring that original method addresses both crash safety and actual movement behavior. Final validation and package details follow.

## Verified delivery

`/tmp/rotp-draft16-1-verification2.log`: **BUILD SUCCESSFUL in 56s**, **206 required server GameTests passed**, and the focused Hamon client flow passed in fresh integrated and dedicated worlds. The unchanged **94 core tests** remained up to date; packaging passed **29 model checks**.

Version: `0.2.2-fabric.16.1-dev+upstream.0.2.2.2`. Mod SHA-256: `25658c302906c362669b6670f41f72a3d42c5574d838961241792a318e6981fe`. `drafts/latest` selects this bundle; Draft 16 remains preserved. Manifest hashes match and the launcher dry-run passes. A dedicated-client screenshot was inspected; functional assertions establish surface support. Manual gameplay acceptance remains pending.

Original `src/` matches HEAD. All 324 user-save files retain their pre-fix sizes and modification timestamps. Automation uses isolated `fabric/build/run` worlds. Changes are staged; no new commit or push.
