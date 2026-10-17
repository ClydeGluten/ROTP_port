---
phase: 05-remaining-content-and-compatibility
plan: 14
status: complete
completed: 2026-10-16
requirements: [HAMON-01, HAMON-02, HAMON-03, HAMON-04, HAMON-05, BUILD-01]
---
# Draft 15 integration and delivery

Integrated progression, actions, equipment/world content, client presentation, Stand stamina and genuine prior-Hamon vampire memory. The feature census covers 46 skill definitions, five techniques and 31 registered actions. Dormant source definitions stay dormant; cross-links requiring unported Stands/powers are explicitly tracked rather than substituted.

Actual AntigravityCLI Gemini 3.8 Flash Low completed a bounded read-only plan review (`/tmp/rotp-hamon-plan-review/review.log`). Its lifecycle/persistence concerns informed the contract. Three available workers implemented independent progression, action and client domains; root implemented content/integration and owned the single Gradle lane. Do not attribute those code edits to Antigravity.

Initial integration corrected obsolete advancement/map components, atlas wiring, mouse-button numbering and clipped GUI state. One Shock test incorrectly treated `spawnWithNoFreeWill` as permanent NoAI; its fixture now sets the actual flag. Focused screenshots clear chat/toasts and remove the teacher before the front-camera mask view. No production rule was weakened for a test.

Manual playtest guide: `fabric/migration/HAMON_PLAYTEST.md`. Launch with `rotptest`; naturally learn from a temple master, or summon `jojo:hamon_master` in a test world. Original Forge `src/` and user saves under `fabric/run` are untouched. Launcher dry-run passes. Prior staged work is preserved; no new commit/push was requested.

## Verification and delivery

`/tmp/rotp-draft15-verification8.log`, **BUILD SUCCESSFUL in 53s**. All **197 required server GameTests** passed. The focused Hamon client flow passed in fresh integrated and dedicated worlds. All **91 core tests** pass (unchanged tasks reused); packaging passed **29 model checks**. No unrelated client matrix was repeated.

Bundle: `drafts/0.2.2-fabric.15-dev+upstream.0.2.2.2/`, selected by `drafts/latest`. Mod SHA-256: `f2e40a0d94f4e6846db5cc9624d51a3ab2be534718e901ea65ee19314b56a5e2`. Manual playtest pending. Changes staged locally; HEAD remains `69e4aaa4a` through Draft 11. Broad phase 5 remains open for other powers/content and migration/compatibility.

After final verification, one whitespace-only line in HamonProjectileEntity.java was cleaned. compileJava passed; both resulting projectile class files were byte-identical to the tested packaged JAR. No runtime behavior changed and the client/server suites were not repeated for formatting.
