---
phase: 05-remaining-content-and-compatibility
plan: 04
subsystem: combat
requirements-completed: [COMBAT-01, BUILD-01, TEST-01]
tags: [fabric, knockback, panic-ai, damage, draft-9.2, gap-closure]
---

# Draft 9.2: mob hit reactions

The user liked Draft 9.1's presentation but reported missing knockback and passive-mob reactions across attacks. Actual AI-enabled cows exposed barrage's erased impulse and missing panic classification. Rapid light combinations also lost the second hit to vanilla damage immunity. Fresh ordinary hits already passed movement/panic checks; the fix does not assume every fresh attack was broken.

## Changes

`fabric/src/main/java/com/github/standobyte/jojo/fabric/StandDamage.java` ports original `DamageUtil.hurtThroughInvulTicks`: temporarily clear damage cooldown for the independently timed Stand hit, then restore the previous cooldown and last-hit amount in `finally`. A narrow LivingEntity accessor exposes the protected amount. Melee, kick sweep, Star Finger and barrage use this path; normal/environmental damage keeps its existing path. Entity invulnerability, blocking, reach, PvP, costs and walls remain enforced.

Barrage now belongs to vanilla panic causes. Its automatic impulse is suppressed through the damage tag, then one original reduced impulse is applied through vanilla knockback: `0.4 * clamp(power * 0.0075, 0, 1)`, or 0.048 at power 16. Active Resolve burst retains zero impulse; resistance is applied by vanilla. Player hurt-direction feedback is sent explicitly because the no-knockback tag also skips that vanilla step. Original Resolve burst gravity/navigation suppression remains a separate parity gap.

A new Time Stop Punch check exposed a short-mob miss: relocating changed the Stand eye position, but copied player pitch sent its ray over a cow. The relocated Stand now faces the acquired target's center. Impact-time ray/reach/wall validation remains; target movement can still cause a legitimate miss.

## Evidence

`fabric/src/gametest/java/com/github/standobyte/jojo/fabric/HitReactionGameTests.java` adds 18 server checks. They cover live physics/panic for light/heavy/uppercut/finger/barrage/Time Punch across both Stands, rapid combos, exact reduced impulse, resistance, active Resolve, invulnerable rejection, preserved vanilla cooldown/last-hit amount and a first ordinary hit after Stand damage.

Pre-fix logs `/tmp/rotp-hitreactions-before.log` and `/tmp/rotp-hitreactions-before2.log` reproduce erased barrage movement and swallowed rapid light hits. One early heavy test sampled velocity after wall collision had consumed it; the corrected fixture disables only random walking until impact and accepts measured displacement or remaining impulse, then restores movement speed and verifies real panic AI. The initial full run also exposed the short-target Time Punch miss; its regression passes after the aiming correction.

`fabric/src/gametest/java/com/github/standobyte/jojo/fabric/HitReactionClientGameTest.java` drives real light/heavy/barrage keys for both Stands in singleplayer and on a dedicated server. It measures synchronized client displacement while the target's walking speed is zero, then restores speed and waits for native panic behavior. Four hit-reaction screenshots are packaged; singleplayer Star Platinum and dedicated The World screenshots were visually inspected.

Final combined command: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./fabric/gradlew -p fabric build runClientGameTest -PacceptMinecraftEula=true --console=plain`. Evidence: `/tmp/rotp-draft9-2-verification2.log`, **BUILD SUCCESSFUL in 2m 50s**. Passed **76 core tests, 100 required server GameTests, 29 model checks and all seven client suites**. Independent read-only source reviews found no blockers. Antigravity was not retried after its prior quota failures; an existing agent handled the isolated audit while the parent implemented and verified changes.

## Delivery

Version `0.2.2-fabric.9.2-dev+upstream.0.2.2.2` is packaged at `drafts/latest/`; mod SHA-256: `9cfb11dd71c73e6b662a09d1bae415ef98770b4641109f3f585dfcdea3d97f4f`. Guide, verification/model logs, regression evidence, screenshots and checksums are included. Launcher dry run passes. Source/planning changes are staged; no new commit/push.

Original Forge sources and user settings/worlds under `fabric/run` were untouched. `fabric/build/run` is restored to an ordinary directory with final evidence; previous data remains in `fabric/build/run.disk-before-hitreactions`. Draft 9.1 and earlier bundles remain preserved. Run `rotptest` for the corrected build. User playtest remains pending; no new content milestone started, and broad parity requirements remain open.

## Self-Check: PASSED

Implementation, red/green regression evidence, full suite, native mob AI, synchronized movement, package and launcher checks are present.
