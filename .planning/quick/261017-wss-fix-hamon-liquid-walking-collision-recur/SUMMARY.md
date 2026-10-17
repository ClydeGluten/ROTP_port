---
status: complete
completed: 2026-10-17
requirements: [HAMON-03, BUILD-01]
---
# Liquid-walking crash corrected

Reported client/server StackOverflowError came from the side-effecting Hamon `canStandOnFluid` hook: setting grounded state re-entered fluid collision. A guard stopped recursion, but the new real-landing regression exposed a second issue: ordinary 26.3 living entities supply no vanilla fluid collision shape. The original mod used a separate displacement algorithm.

Adapted the original `EntityLiquidWalkingMixin` into `HamonLiquidWalkingMixin`, preserving its four corner searches, fluid heights, step allowance, touching-fluid rejection and fall reset. `ModifyExpressionValue` intercepts the `collide` result inside `move`, replacing the original local-variable hook without relying on obsolete local slots. Removed the unsafe vanilla predicate injection. Retained source attribution/comments, existing cost accounting and lava damage. Original `src/` is unchanged.

`/tmp/rotp-draft16-1-verification2.log`: **BUILD SUCCESSFUL in 56s**, **206 required server GameTests passed**, and the focused Hamon client flow passed in fresh integrated and dedicated worlds. The unchanged **94 core tests** remained up to date; packaging passed **29 model checks**.

The first regression run failed real landing after the recursion-only correction; final run verifies the restored movement algorithm. One server regression and a small extension of the existing Hamon client flow cover the actual crash path. No unrelated client matrix. A dedicated-client water screenshot was inspected.

Bundle `0.2.2-fabric.16.1-dev+upstream.0.2.2.2`, mod SHA-256 `25658c302906c362669b6670f41f72a3d42c5574d838961241792a318e6981fe`. Package hashes, preserved Draft 16, launcher dry-run and unchanged metadata for all 324 user-save files are confirmed. No world reset or dependency change. `rotptest` launches the fixed sources.

Execution stayed inline because this bounded shared-file correction would not benefit from CLI/agent startup. GSD quick state and source/playtest guidance are updated. The existing staged Draft 12–16 work is preserved; this fix is staged without a commit or push, following the project's standing delivery policy. Manual playtest remains pending.
