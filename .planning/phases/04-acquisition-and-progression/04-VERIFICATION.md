---
phase: 04-acquisition-and-progression
status: gaps_found
verified: 2026-10-13
---

# Verification

Draft 6 passes 24 core tests, 67 server GameTests, 24 model checks and three real-client suites. Final command, from `fabric/`: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./gradlew build runClientGameTest -PacceptMinecraftEula=true`. Evidence: `/tmp/rotp-draft6-verification5.log`, copied into the versioned draft; BUILD SUCCESSFUL in 1m 46s.

Draft 5 acquisition coverage remains passing: XP/damage timing, healing, surviving the full virus, lethal unpaid infection, real respawn, Creative bypass, rejection, escalating XP, milk, durability, administrator cancellation, deterministic chest loot and real item-use packets across reload/reconnect.

Draft 6 adds accepted damage eligibility, original gain/boost/decay/record formulas, natural level-up and stat growth, server unlock gates, rejection without resource loss, post-absorption resistance and void exclusion, death/respawn persistence, effect removal, serialization, trained time-stop duration/cost/gain, Creative bypass and operator command permissions/reset. Real client keys cover K navigation, locked action feedback, combat level-up and J training; reload and dedicated reconnect preserve fractional learning and unlock state. Progression screenshots show the meter, requirements and trained duration without missing translations or models.

The generated test-world save encountered slow disk I/O in an intermediate run. Final verification used RAM-backed disposable run directories, retaining real file save/reload semantics. Project files and playable worlds stayed in place.

`gaps_found` reflects the larger PARITY-02 requirement, not failing draft tests: customizable action selection, souls/full Resolve presentation/configuration, other-power interactions and projectile/enchantment/material acquisition extensions remain pending. Hamon is separate and unported. Plans 04-01 and 04-02 are complete. Drafts 4, 5 and 6 manual playtests remain pending; prior bundles are preserved.
