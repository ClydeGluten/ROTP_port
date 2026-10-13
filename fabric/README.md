# Ripples of the Past: Fabric development port

This is the seventh playable Star Platinum draft for Minecraft **26.3**, the latest stable release verified on October 12, 2026. It is an incomplete development port of StandoByte's Ripples of the Past 0.2.2.2. The original Forge source remains at the repository root.

## Requirements and build

- Java 25
- Minecraft 26.3
- Fabric Loader 0.19.5 or later
- Fabric API 0.162.0+26.3

Run Gradle from this directory, not the original Forge project root:

```sh
./gradlew build
./gradlew runClient
```

Java 25 was installed locally for this project. On this machine, use:

```sh
JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./gradlew build
```

The installable JAR is `build/libs/RipplesOfThePast-Fabric-0.2.2-fabric.7-dev+upstream.0.2.2.2.jar`. Install it and Fabric API in the `mods` directory on both client and server. Use a new test world; importing old Forge world or player data is not implemented.

For this machine, run `rotptest` in fish from any directory, or `../play-draft.sh` from this directory to launch the development client using the installed Java 25. The packaged draft and Fabric API are under `../drafts/latest/mods/`; `../drafts/latest/START-HERE.txt` contains playtest instructions. `latest` points to a versioned directory, Drafts 2 through 6 remain in their versioned directories, and draft 1 is retained under `../drafts/archive/draft-1/`.

## Play the draft

Draft 7 addresses the camera obstruction reported during the Draft 6 playtest. In first-person, your own Stand and its extended finger render at 16% opacity; the player camera stays at the normal eye position. Rear third-person moves 1.15 blocks toward the opposite shoulder and 0.35 blocks up, constrained by nearby walls. It visually aims at the block or entity on the player's eye ray without rotating the player or extending combat reach. A Stand crossing the centre view still fades when walls retract the camera. Front third-person keeps its normal camera position.

Manual control hides the local Stand body while looking through its eyes. Dismissal, switching views and leaving the world restore the normal camera. Other players' Stands retain their normal opacity. Camera changes are currently automatic; an adjustable camera settings screen and interoperability with external camera mods have not been tested.

Random Stand allocation is [planned](../.planning/phases/04-acquisition-and-progression/04-STAND-ALLOCATION.md): tentative `1..n` candidates, permanent reservation when awakening succeeds, each playable Stand once per generation before repeats. This rule is not implemented in Draft 7.

Survival acquisition now works without commands. Find a **Beetle Stand Arrow** in an unopened desert-pyramid chest: the original independent 2% bonus chance per chest, alongside normal loot. Both arrow variants use their original textures and have 25/250 durability. Each successful Survival piercing uses one durability; the final use still works when the arrow breaks.

Gather **30 XP levels**, then use/right-click the arrow. The virus takes one level every ten ticks and deals 0.75 damage while paying, protecting very low health using the original rule. Without XP it deals 7.5 damage every ten ticks and can kill you. Milk cannot cure the virus, but still cures other effects. Paying the required levels awakens Star Platinum after about 15 seconds; surviving the full 31-second initial virus duration also awakens it, as upstream. Afterward, healing restores 0.25 health each tick until full.

Each previous arrow awakening adds five levels to the next cost. Infection and history persist across saving and reconnecting. Death cancels the unfinished attempt but retains acquisition history. Duplicate use and already-owned Stands reject the arrow without spending durability. Administrator Stand replacement or effect removal cancels pending acquisition. Creative use awakens instantly without XP or durability cost; both items appear in the Combat tab.

Only Star Platinum is in the migrated pool. Normal-arrow crafting awaits meteorite materials/world generation; the Beetle Arrow loot route is the available Survival path. Bow/crossbow/dispenser shooting and arrow enchantments remain unported. The loot addition also applies to datapack replacements of the same desert-pyramid table, matching the original global modifier.

For quick controlled testing with commands enabled, use `/rotp clear @s`, `/give @s jojo:stand_arrow_beetle`, `/experience set @s 30 levels`, then `/gamemode survival` and right-click the arrow. Use a fresh player if you need the initial 30-level cost.

The operator shortcut remains available:

```text
/rotp grant @s star_platinum
```

| Default key | Action |
| --- | --- |
| G | Summon or dismiss Star Platinum |
| R | Light punch |
| V | Heavy punch |
| Hold Z | Barrage |
| Hold X | Frontal guard |
| B | Uppercut |
| C | Star Finger |
| Hold N | Inhale |
| Hold Y | Progressive zoom |
| Hold H | Manual control; move with WASD, Space and Shift |
| J | Charge time stop; press again to resume |
| K | Stand progression; scroll or use Up/Down/Home/End, Esc to close |

Keys can be changed under Controls → Ripples of the Past. Aim at a nearby living target. The server checks reach, walls, ownership, stamina, and attack timing. Survival players start with empty stamina, which regenerates; Creative players have unlimited stamina: the HUD says “Unlimited (Creative)” and omits the finite bar. In Survival, the bar and numbers show actual synchronized stamina. After granting a Stand, use `/gamemode survival` to test drain and regeneration.

The finisher HUD shows charge and marks the original 50% threshold. Light punches build charge (successful hits add more), and landed barrage hits also build it. At 50% or above and Resolve level 1, V automatically selects an uppercut. Heavy attacks consume 51 percentage points and preserve any remaining charge; B still selects uppercut directly. Survival unlocks Uppercut at Resolve level 1, Inhale at 2, Star Finger at 3, and Time Stop at 4. Creative bypasses these gates.

Barrage allows 15% walking speed, guard 30%, and heavy attacks and specials 50%. Light attacks, recovery, release, and dismissal restore normal walking speed. Slowed actions also disable sprinting. These transient server-owned modifiers preserve other speed modifiers. Manual control and time-stop charge retain their body locks.

Barrage consumes four stamina per tick and lasts up to five seconds at base durability. Release and repress after the maximum duration or an interrupted start. Guard reduces frontal physical damage using the original resistance and stamina formulas; it does not block rear hits, falls, or other environmental damage. Exhaustion breaks guard and locks attacks/summon toggling for four seconds at base durability. Menu opening, focus loss, or stale input ends held actions. A short barrage recovery prevents immediate action switching.

Uppercut costs 50 stamina, launches the target and disables a blocking shield. Star Finger costs 375 stamina and extends up to 3.6 blocks from the Stand over 12 ticks, holds for four ticks, and retracts over four ticks. It deals 4.5 damage per target and has an 80-tick cooldown starting at activation, reduced to 20 during a Resolve burst. It respects walls.

Inhale pulls visible entities in a 12-block forward cone, drains two stamina per tick, and lasts up to 80 ticks. Releasing it applies a cooldown based on time held. Zoom progressively narrows the camera field of view and restores it on release. Manual control anchors the player body, uses the Stand camera, and limits the Stand to ten blocks from the body. Attacks then originate at the Stand. Release H or open a menu to return; missing input expires after 12 server ticks. Manual control can pass through blocks; it does not load new chunks.

Time Stop unlocks at Resolve level 4, charges for 40 ticks and starts at five stopped ticks (0.25 seconds). Actual stopped ticks earn 0.25 training points each, or four times that during a Resolve burst. Duration is five plus floored training points, capped at 100 ticks (five seconds). Each use costs 225 stamina plus `900 / durationTicks` per active tick. A completed stop costs 1125 stamina at any training level. Press J again to resume early. Survival cooldown is three times actual stopped ticks, divided by three during Resolve; cancelling the charge has a 20-tick safety cooldown. Creative has full duration, no stamina cost and no active-stop cooldown. Detailed freeze coverage and remaining parity limits are listed in [the migration ledger](migration/STATUS.md).

`/rotp summon`, `/rotp punch`, `/rotp heavy`, `/rotp uppercut`, and `/rotp finger` also trigger these actions. `/rotp status` displays current state. `/rotp clear <players>` removes a Stand. Grant, clear and progression editing require operator privileges.

## Stand Resolve and training

Press K for your level, meter, unlocks and Time Stop training. Earn Resolve by landing Stand hits on hostile mobs, aggressive mobs and other players; normal player attacks count at half strength while your Stand is summoned. Passive animals, misses, self-hits and rejected immunity hits give none. Sustained combat raises the attack multiplier from 1 to 5; low health raises the health multiplier from 1 at 15 HP to 10 at 5 HP. Saved combat peaks help regain lost Resolve.

The original meter thresholds are 2500, 10000, 25000, 50000 and 32500 for levels 0 through 4. Filling the meter advances one level, grants a temporary Resolve effect, and increases Stand stat development by one quarter. During Resolve, the meter drains, training is quadrupled, and damage resistance reaches 66.67%. Outside the effect, resistance scales with the meter. Void damage bypasses resistance. Resolve starts decaying by two points per tick after 20 seconds without a hit while summoned, or 10 seconds while dismissed.

Levels and training survive death, save/reload, reconnect, and clearing/reacquiring Star Platinum. Death resets the current combat meter and retains its peak. Existing Fabric draft ownership and saved development are preserved; Survival now follows the unlock gates, so previously available moves may be locked. Creative access does not fabricate earned levels. Forge saves are still not imported.

For controlled testing, operators can use:

```text
/rotp progression @s level 4
/rotp progression @s training 95
/rotp progression @s reset
```

Setting a level or resetting progression dismisses the Stand. Summon again with G. Reset keeps Stand ownership but clears Resolve levels, combat records and training. Only Time Stop is trainable among the currently migrated moves; Hamon training is separate and remains unported.

## What works

- Original Star Platinum stat formulas, stamina costs and regeneration, attack phase timing, and basic finisher accumulation.
- Server-owned summoning, light/heavy melee, cooldowns, line-of-sight checks, and basic damage transfer.
- Persistent player attachments with owner-only state synchronization and copy-on-death configuration.
- Original model geometry and texture: 74 parts, 107 cubes, and 43 hair segments.
- Original idle, first light-punch, and heavy-punch animation tracks adapted to the new renderer.
- Server-authoritative held barrage, directional guard, exhaustion/guard break, and original barrage/block poses.
- Uppercut, extending Star Finger, inhale, zoom, bounded manual control, and time-stop lifecycle.
- Original arrow items, desert-pyramid loot, XP/virus acquisition, owner feedback and persistent arrow history.
- Persistent combat Resolve, original unlock levels, stat development, human Time Stop training, effect bonuses and a synchronized progression screen.
- Original textures, sounds, translations, licensing, and contributor credits.

Parry/clashes, time-stop blink, the original customizable action-selection interface, other Stands, non-Stand powers, most items, blocks, and world generation remain unported. Barrage fist afterimages, automatic guarding, and full animation-engine behavior remain incomplete. This draft uses deterministic fractional barrage timing rather than the original randomized extra-hit timing. See [the parity ledger](migration/STATUS.md).

## Layout and verification

- `core/`: reusable Java formulas, persistent state, and attack timelines without Minecraft dependencies.
- `src/main/`: Fabric registries, attachments, commands, networking, and server gameplay.
- `src/client/`: controls, HUD, renderer, model, and animation sampling.
- `src/gametest/`: server and real-client integration tests, excluded from the release JAR.
- `tools/`: deterministic model conversion and structural parity checks.

```sh
./gradlew build
./gradlew runClientGameTest -PacceptMinecraftEula=true
python3 tools/verify_star_platinum_model.py
```

`build` runs core unit tests and server GameTests. The four client suites exercise real key input and packets, entity and attachment synchronization, rendering, save/reload, dedicated-server connections, camera collision and aim alignment. Use `-PacceptMinecraftEula=true` only if you agree to the [Minecraft EULA](https://www.minecraft.net/eula); dedicated-server testing requires it. Test screenshots are written under `build/run/clientGameTest/screenshots/`.

To regenerate the model and animation data from the original resources:

```sh
python3 tools/generate_star_platinum_model.py
python3 tools/verify_star_platinum_model.py
```

After a successful `build runClientGameTest` run with output saved to a log, package a draft with:

```sh
python3 tools/package_draft.py --verification-log /path/to/successful-verification.log
```

The packager requires server/client verification evidence, refuses runtime sources newer than the log, runs model checks, and records hashes. Draft bundles and local GSD installation files are ignored by Git. GSD Core 1.16.0 uses `.planning/PROJECT.md`, `REQUIREMENTS.md`, `ROADMAP.md`, and phase plans/reports to track this existing port. Its minimal Codex skills are installed under `.codex/skills/` and become discoverable in a subsequent session opened at the project root. Source progress is versioned in the private [ClydeGluten/ROTP_port repository](https://github.com/ClydeGluten/ROTP_port) on branch `port/fabric-26.3`.

## Source and credits

Original author: **StandoByte**. Original code, assets, and contributor credits are preserved; see the root `LICENSE` and `src/main/resources/UPSTREAM_CREDITS.txt`. The port retains the original GPL-3.0-only license.

- [Upstream source](https://github.com/StandoByte/Ripples-of-the-Past), commit `72862a5826ed45d1dc26e90b37853097acda35de`.
- Input JAR: `JJBA-RipplesOfThePast-1.16.5-0.2.2.2.jar`.
- Input SHA-256: `92af778c18737bf4bf01386af86c0e75db7e89ba0f6dc0ec7f88895b92ff2226`.
- [Fabric 26.3 migration notes](https://fabricmc.net/2026/09/15/263.html) and [automatic testing documentation](https://docs.fabricmc.net/develop/automatic-testing).

Antigravity CLI handles scoped model, time-stop, acquisition and progression tasks alongside main gameplay development. Delegated output is reviewed, corrected, compiled, and tested before integration; completion reports alone are not verification.
