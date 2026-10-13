# Roadmap

## Phases

- [x] **Phase 1: Playable Foundation** — Star Platinum basic summon, melee, persistence, and rendering.
- [x] **Phase 2: Combat Draft** — Held barrage, directional guard, input lifecycle, and repeatable draft packaging.
- [ ] **Phase 3: Star Platinum Abilities** — Advanced melee, Star Finger, inhale, zoom, manual control, and time stop.
- [ ] **Phase 4: Acquisition and Progression** — Survival acquisition, unlocks, resolve, and UI.
- [ ] **Phase 5: Remaining Content and Compatibility** — Other powers, content, migration, and compatibility.

## Phase Details

### Phase 1: Playable Foundation
**Goal:** Establish a tested Fabric foundation using Star Platinum.
**Requirements:** BASE-01, BASE-02, BASE-03
**Status:** Automated verification complete; user accepted Draft 2.

### Phase 2: Combat Draft
**Goal:** Make Star Platinum's core combat loop usable in the next local draft.
**Depends on:** Phase 1
**Requirements:** COMBAT-01, COMBAT-02, COMBAT-03, COMBAT-04, BUILD-01, TEST-01
**Success Criteria:**
1. Barrage is server-authoritative, drains stamina, respects walls, and ends on release or timeout.
2. Guard reduces frontal physical damage, preserves rear/environmental damage, and breaks on insufficient stamina.
3. Both actions render distinctly and respond to actual client input in integration tests.
4. A tested latest JAR, dependency, checksums, and playtest guide are available locally.
**Plans:** 1 plan

Plans:
- [x] 02-01-PLAN.md — Implement combat behavior, integrate isolated pose work, verify, and package draft.

### Phase 3: Star Platinum Abilities
**Goal:** Restore remaining Star Platinum abilities.
**Depends on:** Phase 2
**Requirements:** PARITY-01, CAMERA-01
**Draft 4:** Stamina feedback, automatic finishers and combat movement verified after the accepted six-ability draft. Full original parity remains open; see the migration ledger.
**Draft 7:** First-person obstruction fade, collision-aware rear shoulder camera, aim alignment and camera lifecycle verified after the Draft 6 playtest report.
**Plans:** 3 plans

Plans:
- [x] 03-01-PLAN.md — Uppercut, Star Finger, inhale, zoom, manual control and regional time stop; verified local draft.
- [x] 03-02-PLAN.md — Stamina feedback and pixel regression checks, automatic finishers, movement restrictions; verified Draft 4.
- [x] 03-03-PLAN.md — Local Stand camera obstruction fixed in both views; verified and packaged Draft 7.

### Phase 4: Acquisition and Progression
**Goal:** Replace operator-only acquisition with playable progression.
**Depends on:** Phase 2
**Requirements:** PARITY-02, ASSIGN-01
**Draft 6:** Stand Resolve, original unlocks, human Time Stop training and progression overview verified. The customizable action UI and broader progression/acquisition parity remain open.
**Plans:** 2 plans

Plans:
- [x] 04-01-PLAN.md — Stand Arrow items, original loot, persistent XP/virus acquisition and verified Draft 5.
- [x] 04-02-PLAN.md — Combat Resolve, unlocks, Time Stop training, progression screen and verified Draft 6.

Planned extension (not implemented): random Stand allocation in server-wide generations. Players receive tentative integers `1..n`; collisions are resolved from the remaining pool when awakening succeeds. Successful awakening reserves ownership, before first summon. No duplicates within a generation; exhausting all playable Stands opens the next generation. See [allocation rules and acceptance checks](phases/04-acquisition-and-progression/04-STAND-ALLOCATION.md). With four playable Stands, the first four successful awakenings are unique and the fifth starts the next generation.

### Phase 5: Remaining Content and Compatibility
**Goal:** Extend the validated foundation across original content and define migration support.
**Depends on:** Phase 3, Phase 4
**Requirements:** PARITY-03, PARITY-04

## Progress

| Phase | Plans Complete | Status | Completed |
| --- | --- | --- | --- |
| 1. Playable Foundation | 1/1 | Complete (automated) | 2026-10-12 |
| 2. Combat Draft | 1/1 | Complete (automated) | 2026-10-12 |
| 3. Star Platinum Abilities | 3/3 | Draft 7 camera correction verified; parity gaps remain | - |
| 4. Acquisition and Progression | 2/2 | Draft 6 progression; broader parity gaps remain | - |
| 5. Remaining Content and Compatibility | 0/TBD | Not started | - |
