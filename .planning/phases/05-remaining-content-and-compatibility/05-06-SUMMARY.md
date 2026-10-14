---
phase: 05-remaining-content-and-compatibility
plan: 06
subsystem: knives
requirements: [PARITY-03, WORLD-01, BUILD-01, TEST-01]
status: complete
---
# Draft 11: original knives

User accepted Draft 10 and authorized the next milestone. The implementation follows original KnifeItem, KnifeEntity, ItemProjectileEntity, TheWorldEntity.onKnivesThrow, LivingStuckObjects and the original render/assets. Forge sources remain read-only. Original behavior remains the default for the whole project.

## Delivered behavior

Knives craft four at a time from an iron ingot above a wooden rod and stack to sixteen. Ordinary use throws up to eight; Shift throws one. The primary count sets a three-ticks-per-knife cooldown. The World throws up to eight additional knives from the remaining stack; Creative preserves ammunition and therefore retains the original extra-volley edge cases. No invented stamina or Resolve gate was added.

Launch speed, Gaussian spread, first/subsequent knife inaccuracy, fixed 0.3 target margin, fractional speed-scaled damage, original invulnerability-timer preservation, ownership/PvP, nearest contact, rejected-hit rebound, fire restoration, pickup and projectile persistence are ported. Dispensers retain the original position and slight upward launch direction. Singleton melee attributes, cobweb harvesting, plant-family mining speeds and log stripping are included.

Thrown knives retain five lifetime stopped-time flight ticks. Entity contact halts a knife without damage; thaw retraces collision and can hit a replacement target or miss a target that moved away. Block collisions still happen during grace. Frozen client positions synchronize with the server, and knife render states use the final endpoint/rotation. Ordinary Stand melee damage remains immediate during stopped time.

Original stack models, textures, projectile geometry and sounds are retained. Persistent synchronized stuck-knife counts use the original transient decay timer; the original player-only layer preserves seeded placement and follows the current player model. Stone Mask/vampirism interaction, unported item tracking and broader voice/Stand-interception systems remain documented dependencies rather than invented replacements. See `fabric/migration/KNIFE_PARITY.md`.

## Review and verification

Independent source review found changed vanilla projectile rules. Three added regressions failed before the fix: a rear target took damage after a rejected front hit, newborn knives missed the old expanded hitbox, and launch spread differed from original Gaussian behavior. The fixes restore original rules; all 146 server tests then passed. Review also caught stale render interpolation after the final flight tick; client tests now assert rendered endpoint/rotation at partial ticks 0, 0.5 and 1.

Final command: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./fabric/gradlew -p fabric build runClientGameTest -PacceptMinecraftEula=true --console=plain`.

Evidence: `/tmp/rotp-draft11-verification2.log`, **BUILD SUCCESSFUL in 3m 28s**. Passed **76 core tests, 146 required server GameTests, 29 model checks and all ten real-client suites**. Knife client checks use actual mouse/modifier input in integrated and dedicated servers, verify count-dependent item models, original selected-action priority, primary/Stand/Shift consumption, five-tick flight, synchronized frozen endpoints and rotations, deferred/retraced hits, thaw movement, player stuck-count/render layers and cleanup. Existing camera/HUD/controls/acquisition/progression/time-action checks remain green. Inventory, frozen-projectile and player stuck-knife screenshots were inspected.

The packager passed model/source checks. Mod SHA-256: `6f2c3ae2b8bea5355dcee7b526779908060a717a1d5c199778286e6cdc408a8b`. The bundle preserves final verification plus before/after physics regression evidence. The launcher dry run passed. No original Forge sources or user worlds/settings under `fabric/run` were modified.

## Delivery

Version: `0.2.2-fabric.11-dev+upstream.0.2.2.2`. The verified bundle is `drafts/latest`; earlier versions remain preserved. `fabric/build/run` is restored to an ordinary directory with final evidence; the previous directory remains at `fabric/build/run.disk-before-draft11`. Progress is staged, with no new commit/push. Run `rotptest` after delivery; test knife volleys with and without The World, Shift, Alt bypass, stopped-time flight/resume and block pickup. Await user playtest before another milestone. Hamon remains later.
