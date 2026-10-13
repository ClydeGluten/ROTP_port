---
phase: 02-combat-draft
verified: 2026-10-12T14:21:08Z
status: passed
score: 4/4 must-haves verified
behavior_unverified: 0
---

# Combat Draft Verification

Scope: automated correctness and local draft delivery. User playtest remains pending.

| Required behavior | Evidence | Result |
| --- | --- | --- |
| Barrage hits and stamina are server-owned | Core rate/cost/duration tests; server consecutive damage, stamina, exhaustion, spam and wall tests | Passed |
| Stale input expires and cannot reset maximum duration | Core lease/max-duration tests; server heartbeat expiry; real-client sustained input/release and dedicated-server menu cancellation | Passed |
| Guard protects only against frontal blockable damage and breaks on exhaustion | Real player-damage tests for front/rear/fall, partial block, break lock, and transferred-hit cooldown | Passed |
| Latest draft is verified and reviewable | Successful full build/client test log, model checks, inspected screenshots, versioned bundle, checksum manifest and launcher | Passed |

Requirements covered: COMBAT-01 through COMBAT-04, BUILD-01, TEST-01. Automated counts: 12 core, 18 server GameTests, 18 model checks. Real-client suite includes singleplayer and dedicated server.

Artifacts: `fabric/core/src/main/java/com/github/standobyte/jojo/core/HeldCombat.java`, `fabric/src/main/java/com/github/standobyte/jojo/fabric/StarPlatinumEntity.java`, the Player guard mixin, original-derived client animation files, and `drafts/latest/mods/RipplesOfThePast-Fabric-latest.jar`.

Human follow-up: actual combat feel, focus switching on the user's desktop, rebind preferences, and unrelated mod compatibility. These are outside the four automated acceptance truths; no manual acceptance is claimed.
