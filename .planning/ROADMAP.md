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
**Requirements:** PARITY-02, ASSIGN-01, HUD-01
**Draft 6:** Stand Resolve, original unlocks, human Time Stop training and progression overview verified. Draft 8 delivered an action UI; its playtest requested original presentation and controls, addressed by Phase 5 plan 02. Broader progression/acquisition parity remains open.
**Plans:** 3 plans

Plans:
- [x] 04-01-PLAN.md — Stand Arrow items, original loot, persistent XP/virus acquisition and verified Draft 5.
- [x] 04-02-PLAN.md — Combat Resolve, unlocks, Time Stop training, progression screen and verified Draft 6.
- [x] 04-03-PLAN.md — Compact action HUD, keyboard-accessible editor, saved layouts and authoritative cooldowns; verified Draft 8.

Draft 8 implements natural awakening generations for two playable Stands. Provisional candidates reserve nothing; successful Arrow awakening chooses an unused Stand, then starts a new generation after exhaustion. Normal world reload, death/relogin and command-bypass rules are covered. Crash recovery across partial world/player saves remains open. See [allocation rules and remaining acceptance targets](phases/04-acquisition-and-progression/04-STAND-ALLOCATION.md).

### Phase 5: Remaining Content and Compatibility
**Goal:** Extend the validated foundation across original content and define migration support.
**Depends on:** Phase 3, Phase 4
**Requirements:** PARITY-03, PARITY-04, WORLD-01, HUD-01, CAMERA-01
**Plans:** 6 plans

Plans:
- [x] 05-01-PLAN.md — The World original assets, distinct melee/time abilities, separate progression and acquisition; verified alongside the HUD in Draft 8.

- [x] 05-02-PLAN.md — Correct unstable F5 camera and replace custom HUD/controls with original supported Stand UI; verify and package Draft 9 before user playtest.

- [x] 05-03-PLAN.md — Fix Draft 9 unbound-key world-entry crash, repair existing options and verify native input before packaging Draft 9.1.

- [x] 05-04-PLAN.md — Fix mob hit reactions, barrage push/panic and rapid follow-up hits; verify Draft 9.2.

- [x] 05-05-PLAN.md — Original Time Stop combat, both instant moves and Time Stop Punch parity; verified Draft 10.

- [x] 05-06-PLAN.md — Original knives, The World volleys, stopped-time projectile behavior and Draft 11.

The World and knives are playable; Draft 11 awaits user playtest. Vampirism, broader Time Stop perception and original content remain open.

## Progress

| Phase | Plans Complete | Status | Completed |
| --- | --- | --- | --- |
| 1. Playable Foundation | 1/1 | Complete (automated) | 2026-10-12 |
| 2. Combat Draft | 1/1 | Complete (automated) | 2026-10-12 |
| 3. Star Platinum Abilities | 3/3 | Draft 7 camera correction verified; parity gaps remain | - |
| 4. Acquisition and Progression | 3/3 | Draft 8 action HUD and allocation; broader parity gaps remain | - |
| 5. Remaining Content and Compatibility | 6/6 | Draft 11 knives verified; broad parity remains open | - |
