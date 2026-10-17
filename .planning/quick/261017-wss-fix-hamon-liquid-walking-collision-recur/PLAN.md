---
type: quick
status: complete
requirements: [HAMON-03, BUILD-01]
---
# Fix liquid-walking collision recursion

User reports the test build crashes. Latest reports at 2026-10-17 23:35:49 and 23:36:12, plus an earlier client report, contain StackOverflowError through HamonMovement.liquidWalking -> Entity.setOnGround -> supporting-block collision -> canStandOnFluid -> liquidWalking.

1. Compare the original liquid-walking method and modern ground/support implementation. Apply the smallest adaptation preserving eligibility, costs, fire restrictions and grounded behavior.
2. Add one focused server regression covering real fluid support/collision, grounded/airborne entry, per-tick energy costs and recovery. Extend the existing Hamon client flow to stand on actual water in both integrated and dedicated worlds, exercising client collision as well.
3. Run one combined build/server and focused Hamon client validation, fix only failures, and package Draft 16.1. Preserve Draft 16, original Forge src, existing staged work and user saves. Update GSD state, source ledger, playtest guidance and stage the fix. No commit/push.

Execute inline: this small shared-file fix does not benefit from another agent or CLI startup. Reuse the existing Java 25 toolchain and dependency versions. The task retains the project's staging-only delivery policy rather than the skill's default commits.

## Evidence-driven scope refinement

A recursion-only guard passed the support-query checks but failed real landing. Cached 26.3 bytecode exposes an empty default liquid shape. The original mod uses `EntityLiquidWalkingMixin` after collision, not a vanilla fluid predicate. Restore that exact four-corner movement method and remove the misplaced predicate hook before final verification.
