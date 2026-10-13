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

- [x] CAMERA-01: Keep the local Stand from obstructing first-person and third-person play while preserving aim and collision.
- [ ] ASSIGN-01: Assign tentative random `1..n` Stand candidates and reserve only on successful awakening; award each playable Stand once per server generation before repeating.

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
| PARITY-02, ASSIGN-01 | 4 |
| PARITY-03, PARITY-04 | 5 |
