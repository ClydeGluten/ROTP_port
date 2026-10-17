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
**Requirements:** PARITY-03, PARITY-04, WORLD-01, HUD-01, CAMERA-01, VAMP-01, VAMP-02
**Plans:** 78 plans (21 historical deliveries; 57 remaining-port execution plans)

Plans:
- [x] 05-01-PLAN.md — The World original assets, distinct melee/time abilities, separate progression and acquisition; verified alongside the HUD in Draft 8.

- [x] 05-02-PLAN.md — Correct unstable F5 camera and replace custom HUD/controls with original supported Stand UI; verify and package Draft 9 before user playtest.

- [x] 05-03-PLAN.md — Fix Draft 9 unbound-key world-entry crash, repair existing options and verify native input before packaging Draft 9.1.

- [x] 05-04-PLAN.md — Fix mob hit reactions, barrage push/panic and rapid follow-up hits; verify Draft 9.2.

- [x] 05-05-PLAN.md — Original Time Stop combat, both instant moves and Time Stop Punch parity; verified Draft 10.

- [x] 05-06-PLAN.md — Original knives, The World volleys, stopped-time projectile behavior and Draft 11.

- [x] 05-07-PLAN.md — Repair Draft 11 vertical aiming and knife usability; original Stone Mask/Blood Gift vampirism, blood resource and starter actions verified in Draft 12.

- [x] 05-08-PLAN.md — Remaining vampire scope (Draft 13); source behavior, focused client validation.

- [x] 05-09-PLAN.md — Vampire parity follow-up: weak leap, original effect applicability, coffin advancement and confirmed rendering corrections.

- [x] 05-10-PLAN.md — Hamon progression and learning (wave 1).
- [x] 05-11-PLAN.md — Hamon actions and passive effects (wave 2).
- [x] 05-12-PLAN.md — Original Hamon UI and presentation (wave 2).
- [x] 05-13-PLAN.md — Hamon equipment and world content (wave 2).
- [x] 05-14-PLAN.md — Hamon integration and playable draft (wave 3).
- [x] 05-15-PLAN.md — Source review, damage-event omissions and original contact sparks.
- [x] 05-16-PLAN.md — Original training rollover and cached exercise bonus timing.
- [x] 05-17-PLAN.md — Skill-window interactions and handed kick animation.
- [x] 05-18-PLAN.md — Infusion target/cost validation and Life Magnetism hand checks.
- [x] 05-19-PLAN.md — Glider landing immunity and Molotov ignition prediction.
- [x] 05-20-PLAN.md — Combined focused verification and Draft 16 packaging.
- [x] 05-21-PLAN.md — Playtest guidance, source evidence and staged delivery.


Draft 16.1 was accepted on 2026-10-18. The user authorized reverse engineering and adapting the rest of the active original mod. [The remaining execution index](phases/05-remaining-content-and-compatibility/05-REST-PLAN-INDEX.md) tracks plans 05-22–05-78, their dependencies, ownership and complete-scope acceptance. Draft17 packages zombie-player powers, all three Pillar Man modes, equipment, possession, temple content and shared Stand/species foundations after247 server checks plus integrated/dedicated client flows. Silver Chariot, Hierophant Green and terrain restoration are the next implementation wave. Intermediate deliveries do not close the full-port objective.

## Progress

| Phase | Plans Complete | Status | Completed |
| --- | --- | --- | --- |
| 1. Playable Foundation | 1/1 | Complete (automated) | 2026-10-12 |
| 2. Combat Draft | 1/1 | Complete (automated) | 2026-10-12 |
| 3. Star Platinum Abilities | 3/3 | Draft 7 camera correction verified; parity gaps remain | - |
| 4. Acquisition and Progression | 3/3 | Draft 8 action HUD and allocation; broader parity gaps remain | - |
| 5. Remaining Content and Compatibility | 21/78 | Draft 16.1 accepted; executing the complete remaining active original-mod scope | - |

Remaining-port delivery update: Draft18 verifies Silver Chariot/Hierophant Green, terrain-history groundwork and shared config with258 server checks plus fresh integrated/dedicated controls and vampire Time Stop late join. MR/CD/GE next batch remains in production; original-mod parity is incomplete.

2026-10-18 handoff: Codex stopped implementation at the user’s request. Antigravity-only continuation is bounded by available quota/five hours, then a repository/GSD checkpoint and pause. Workspace19 compiles but remains unverified; CD44–46, GE48–50 and President51–52 are saved as inactive WIP. Draft18 remains the latest verified artifact. See `.continue-here.md` in phase5.
