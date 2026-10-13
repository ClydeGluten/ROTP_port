---
phase: 03-star-platinum-abilities
status: gaps_found
verified: 2026-10-13
---

# Verification

## Draft 7 camera follow-up

Draft 7 passes 24 core checks, 67 server GameTests, 24 model checks and four real-client suites. Final evidence is `/tmp/rotp-draft7-verification4.log` and `drafts/latest/verification.log`. The new suite checks actual camera displacement and ray alignment, near-wall clipping, first-person render opacity and eye position, a nearby entity hit, manual body hiding, front-view/dismissal reset and a fresh world after disconnect. Eight camera captures accompany the bundle; first-person, rear, wall, entity-aim and manual-control screenshots were inspected. The old progression fixture now uses a synchronized player teleport.

CAMERA-01 is satisfied by automated checks; the user's Draft 7 playtest is pending. Draft 6 was playtested and prompted this correction. Broader PARITY-01 remains incomplete. ASSIGN-01 was added as a future custom feature, with reservation explicitly at successful awakening, and is not implemented here.

## Previous Draft 4 evidence

Draft 4 passes 12 core tests, 45 server GameTests (44 mod tests plus one framework test), 24 model checks and real-client singleplayer/dedicated-server checks. The final command was `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./gradlew build runClientGameTest -PacceptMinecraftEula=true` from `fabric/`, recorded in `/tmp/rotp-draft4-release-verification.log`. The verified bundle contains a copy and hashes.

Survival tests inspect screenshot pixel widths at full, half and empty stamina, during real barrage drain and resting regeneration. Dedicated-server checks measure half and quarter bars. These integrated-server regressions passed before the UI change; no Survival defect was reproduced. Creative deliberately skips stamina costs and now explicitly says Unlimited instead of showing a stationary finite bar.

Server tests cover automatic finisher selection below and at the original half-charge threshold, launch damage and preservation of remaining charge after consuming 0.51. Movement tests cover actual speed attributes, coexistence with foreign modifiers, leases, recovery, dismiss/clear, death, disconnect, dimension changes and body locking. Real-client checks verify attribute synchronization, sprint rejection, release restoration and heavy-key finisher selection. Screenshots of the Creative label, Survival bars and finisher readiness were inspected.

`gaps_found` applies to the broader PARITY-01 requirement, not a failing draft test. Full original combat and time-stop variants remain open. See `fabric/migration/STATUS.md`. Drafts 2 and 3 were accepted by the user, with the Draft 3 stamina concern handled as above. Draft 4 manual playtest remains pending.
