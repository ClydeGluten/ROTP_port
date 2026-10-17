---
phase: 05-remaining-content-and-compatibility
plan: 08
status: complete
requirements-completed: [VAMP-02, BUILD-01]
---
# Draft 13 — remaining vampire scope

Delivered remaining vampire actions, zombie ownership/AI, curing/coffins, Stand modifiers, decorative masks/Aja acquisition, blood/visuals, configuration and vampire-facing Hamon. Original Forge sources remain read-only. Full Hamon learning, Pillar Man evolution and separate zombie-player powers are explicit later dependencies.

Mappings: `VAMPIRISM_PARITY.md` and `VAMPIRE_SUPPORT_PARITY.md`. Claw uses original keyframes with native hinge bends; armor remains rigid. Modern terrain layers derive from texture transparency.

Source audit corrected eyes to charge for twenty ticks and fire on release. Client validation found server Freeze damage worked but observers lacked effect data. Original add/update/remove/start-tracking packets now restore visual synchronization.

Final command: `JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./fabric/gradlew -p fabric build runClientGameTest -PclientGameTest=VampireClientGameTest -PacceptMinecraftEula=true --console=plain`.

Evidence: `/tmp/rotp-draft13-verification2.log`, **BUILD SUCCESSFUL in 1m 17s**. Passed **80 core tests, 173 required server GameTests, 29 model checks and the focused vampire client suite in singleplayer and a dedicated connection**. The previous twelve-suite client matrix was not repeated. Existing coverage was extended without a new test suite. A model/blockstate dependency scan validated 106 models. The first client run caught missing tracked mob effects; original packet hooks fixed this. Screenshots confirm frozen target/HUD and beam rendering. Some front-view screenshots are occluded by the test cow and do not prove cosmetic fidelity. Manual testing remains pending, especially curing/respawn, zombie AI and the complete cosmetic sequence.

Package checksums and launcher dry run pass. Original Forge sources and user saves under `fabric/run` remain untouched. Test worlds use `fabric/build/run`.

Bundle `0.2.2-fabric.13-dev+upstream.0.2.2.2`; SHA-256 `3b50ff1c2f8fb2cde7afa9700ebbc22379e14ff539efe1012892f3f08865fbc2`. Staged locally, not committed or pushed.
