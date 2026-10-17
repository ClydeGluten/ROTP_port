---
phase: 05-remaining-content-and-compatibility
plan: 07
subsystem: vampirism
requirements: [VAMP-01, WORLD-01, HUD-01, COMBAT-01, BUILD-01, TEST-01]
status: complete
---
# Draft 12: playtest fixes and starter vampirism

User accepted the rest of Draft 11, reported vertical Stand aiming and unclear knife use in `/home/alanno/notes`, and requested vampirism with infection routes and starter abilities. The implementation preserves the original weak/full vampire distinction rather than inventing numbered levels. Original Forge `src/` remains read-only.

## Delivered behavior

Stand manifestations now follow the pitched melee offset from the original entity/action code. The migrated models apply the original limb-aim layer to punch and kick poses. Tests cover both Stands looking above and below in first person and rear F5. Knife tooltips and the held-item prompt explain acquisition, rebound controls, Alt bypass, single throws and The World's remaining-stack volley. Existing original mouse-action priority remains.

The Stone Mask uses the original item and worn textures, armor geometry, 33 durability, one-durability activation and 101-tick active/drop sequence. It adds the original independent 8% jungle-temple chest roll. Wearing it and using exactly one knife transforms a non-Peaceful player into a full vampire; the knife remains and causes the original one self-damage. Blood Gift provides the player-to-player infection route and creates a weak vampire. A mask upgrades weak to full without resetting blood. Full form persists after death; weak form does not. Stand ownership stays independent.

Blood starts at 300 with a 1,000 maximum. Decay, passive tiers, regeneration costs, hunger/air, sunlight damage and healing suppression follow the original difficulty formulas. Blood Drain respects vanilla damage immunity and original target multipliers/debuffs, and stops when the donor is hurt. Claw Lacerate uses its original windup, damage, knockback and seven-block destruction pattern. Its advertised 45 blood is an eligibility gate, not a debit, matching the original override. Blood Gift requires a nearby human at six health or less, donor health above ten, a sixty-tick hold costing five blood per tick and ten donor health on success. Incoming damage alone does not cancel Gift.

J opens the original non-Stand action mode. Vampire layouts use the shared V/B selection, mouse actions, Alt bypass and editor with independent persistence. The original red blood bar, icon, action textures and charge crosshair are used. H and the awakening message explain controls and weak/full behavior. If the previous Time Stop shortcut shares J, vampire mode takes priority; a separate binding or the Stand HUD still provides Time Stop.

## Review and verification

Source review found and fixed milk removing hidden passive health and incoming damage incorrectly canceling Blood Gift. The original `sun_burn` ID and consumable immunity are retained. Actual respawn testing found Fabric's default attachment copy could overwrite weak-form removal; the respawn handler now runs after that copy.

Focused server/client tests passed before the combined run. The first combined server run exposed fixture interference: fresh shelter lighting had not propagated during direct tick-zero calls, so shared daytime sunlight altered five vampire cases. A separate night test environment isolates ordinary vampire tests; the sunlight test still verifies real noon exposure, shelter, rain and night with clock/weather restoration. Client fixture fixes reset the original mask self-hit displacement and use live AI for physical Claw knockback, preserving the gameplay assertions.

Final command: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./fabric/gradlew -p fabric build runClientGameTest -PacceptMinecraftEula=true --console=plain`.

Evidence: `/tmp/rotp-draft12-verification2.log`, **BUILD SUCCESSFUL in 4m 26s**. Passed **80 core tests, 173 required server GameTests, 29 model checks and all twelve real-client suites**. Integrated and dedicated connections cover actual mask activation/models, J/K/H routing, original blood HUD, Drain damage and blood gain, mouse/menu cancellation, physical Claw knockback, interrupted/retried sixty-tick Blood Gift, weak-slot filtering and Stand coexistence. Sixteen first-person/rear-F5 aim scenarios cover both Stands, both pitch directions and both connection types. Existing input, camera, HUD, acquisition, progression, knife and time-action tests remain green. Final mask, shared-resource HUD and upward-aim screenshots were inspected; focused blood, Gift and weak-form screenshots were also inspected.

The packager passed source/model checks. Both bundled JAR checksums and `rotptest --dry-run` pass. Mod SHA-256: `ca69b7c0e6267e805c8d997fb54e5346da75afc6faa5d1f09bd8655e0ccd405c`. Original Forge sources and user worlds/settings under `fabric/run` were untouched.

## Scope and delivery

Version: `0.2.2-fabric.12-dev+upstream.0.2.2.2`. Source mappings: `fabric/migration/PLAYTEST_NOTES_DRAFT12.md` and `VAMPIRISM_PARITY.md`.

This is the starter vampirism slice. Advanced vampire abilities, curing, Hamon, broader bleeding integration, decorative masks, full player action animations and vampire modifiers to Stand stamina/Resolve/Time Stop remain open. Blood Gift resurrection is not included. Broad PARITY requirements remain unchecked.

The verified bundle is `drafts/latest`; earlier drafts remain preserved. Full verification, focused evidence and screenshots accompany the JARs. Changes are staged, with no new commit or push. Run `rotptest` and test vertical attacks, knife guidance, mask awakening, blood use/refill, sunlight and Blood Gift with another player. Await this playtest before starting another milestone.
