# Requirements

## First Playable Foundation

- [x] BASE-01: Build and run on Fabric / Minecraft 26.3 with Java 25.
- [x] BASE-02: Grant, summon, dismiss, attack, and preserve Star Platinum ownership across saves.
- [x] BASE-03: Render original geometry/textures and synchronize gameplay between client and server.

## Combat Draft

- [x] COMBAT-01: Hold barrage with upstream rate, damage, maximum duration, recovery, and stamina cost.
- [x] COMBAT-02: Release, focus loss, GUI opening, disconnect, or stale input cannot leave a held action active indefinitely.
- [x] COMBAT-03: Guard protects against physical damage from the front, spends stamina, and breaks when exhausted.
- [x] COMBAT-04: Barrage/guard have distinct poses and visible control hints.
- [x] BUILD-01: Preserve the previous tested draft and provide a stable latest-draft path with version and checksums.
- [x] TEST-01: Core, server, model, and real-client checks cover new behavior; manual playtest remains explicit.

## Later Milestones

- [x] CAMERA-01: Keep the local Stand from obstructing play; provide stable independent third-person orbit, camera-relative movement, correct aim and wall collision.
- [x] HUD-01: Port the supported original Stand overlay, textured bars, folding rows, selection/control methods and editor; retain server cooldown/resource feedback and saved per-Stand layouts.
- [x] WORLD-01: Make The World playable with original model, textures, animation, distinct combat and human Time Stop progression.
- [x] ASSIGN-01: Assign tentative random `1..n` Stand candidates and reserve only on successful awakening; award each playable Stand once per server generation before repeating.
- [x] VAMP-02: Complete the remaining vampire action, lifecycle, Stand modifier, visual, blood and configuration scope; keep separate Hamon/Pillar Man/zombie-player dependencies explicit.
- [x] VAMP-01: Provide original weak/full vampirism through Stone Mask and Blood Gift, with blood, sunlight, starter Drain/Claw/Gift actions and non-Stand HUD; verify alongside the Draft 11 playtest fixes.

- [ ] PARITY-01: Complete Star Platinum abilities, including time stop and advanced combat.
- [ ] PARITY-02: Restore survival acquisition, progression, and power UI.
- [ ] PARITY-03: Migrate other Stands, non-Stand powers, and world content.
- [ ] PARITY-04: Define Forge save migration and compatibility support.

## Traceability

| Requirements | Phase |
| --- | --- |
| BASE-01, BASE-02, BASE-03 | 1 |
| COMBAT-01, COMBAT-02, COMBAT-03, COMBAT-04, BUILD-01, TEST-01 | 2 |
| PARITY-01, CAMERA-01 | 3 |
| PARITY-02, ASSIGN-01, HUD-01 | 4 |
| PARITY-03, PARITY-04, WORLD-01, VAMP-01, VAMP-02 | 5 |

## Complete Hamon milestone (2026-10-16)

- [x] HAMON-01: Original natural learning, teacher/player interactions, abandon, persistence/death and vampire transitions.
- [x] HAMON-02: Complete original energy/breathing, stats, exercises/day progression, skills, five character techniques and configuration.
- [x] HAMON-03: Every registered Hamon action and passive skill effect, preserving costs/timing/requirements.
- [x] HAMON-04: Original Hamon progression UI/HUD/input and complete supported presentation.
- [x] HAMON-05: Original Hamon equipment, entities, teacher/temple acquisition and advancements.

All HAMON requirements map to Phase 5 plans 10–14, with source-correction follow-up in plans 15–21.

Draft 16 source corrections and focused verification complete; manual visual/gameplay acceptance remains pending. Cross-content dependencies are listed in the Hamon parity ledgers.
