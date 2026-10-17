# Ripples of the Past: Fabric development port

**Current checkpoint:** Draft18 is the latest verified bundle in `drafts/latest`, adding Silver Chariot, Hierophant Green, zombie-player/Pillar powers and equipment to prior content. Workspace19 compiles but is an unfinished Magician’s Red/shared-integration candidate. `rotptest` builds workspace19. Crazy Diamond, Gold Experience and Mr President drafts remain inactive under `migration/wip/`. See [handoff](../.planning/phases/05-remaining-content-and-compatibility/.continue-here.md) for exact verification and remaining work. Historical draft details below remain scoped to their versions.

Draft 16.1 targets **Minecraft 26.3** with playable **Star Platinum**, **The World**, **vampirism**, and the registered original **Hamon progression and ability system**. Vampires gain Freeze, Space Ripper Stingy Eyes, zombies, Evil Atmosphere, leaps, curing/coffins, Stand bonuses and original blood/visual integrations. The supported original 1.16 HUD and independent third-person orbit remain. This is an incomplete port of StandoByte's Ripples of the Past 0.2.2.2; the original Forge source is preserved at the repository root.

Draft 9.2 restores mob hit reactions: rapid Stand attacks follow their own timing, barrage applies a small push and triggers passive-mob panic, and The World's Time Stop Punch aims correctly at short targets after moving. Vanilla attacks retain their own damage immunity; barrage respects knockback resistance. Full regression verification passed.

Draft 9.1 fixes the world-entry crash caused by invalid unbound heavy/uppercut keys. Existing Draft 9 settings repair automatically on launch; custom bindings remain. Previous options are saved to `config/jojo/options-before-unbound-key-repair.txt`.

## Build and launch

Requirements: Java 25, Minecraft 26.3, Fabric Loader 0.19.5 or later, and Fabric API 0.162.0+26.3.

Run from this `fabric/` directory:

```sh
./gradlew build
./gradlew runClient
```

On this machine:

```sh
JAVA_HOME=/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1 ./gradlew build
```

Run **`rotptest`** in fish from any directory, or `../play-draft.sh` here, to launch the development client. The Draft 16.1 JAR is `build/libs/RipplesOfThePast-Fabric-0.2.2-fabric.16.1-dev+upstream.0.2.2.2.jar`. Install it and Fabric API on both client and server. Forge world/player import is not implemented; use a test world.

Verified bundles go under `../drafts/latest/`, with installation instructions in `START-HERE.txt` and both JARs in `mods/`. Earlier drafts remain in their versioned directories; Draft 1 is under `../drafts/archive/draft-1/`.

## Controls and HUD

| Default input | Action |
| --- | --- |
| M | Summon or dismiss your Stand |
| K | Toggle Stand action mode |
| Left / right mouse | Use the selected attack / ability while Stand, Hamon or vampire mode is enabled |
| Hold V + wheel or number key | Select an attack |
| Hold B + wheel or number key | Select an ability |
| Shift + selected punch or barrage | Heavy punch; uses the unlocked finisher when charge permits |
| Hold Left Alt | Temporarily use vanilla mouse actions; V/B selection still works |
| Hold O | Manual control; move with WASD, Space and Shift |
| Backslash (`\`) | Edit the action layout |
| H | Open original Hamon screen when learned; otherwise power progression overview |
| Hold middle mouse | Hamon breathing in Hamon mode |
| R / hold Z / hold X | Direct light punch / barrage / guard |
| C / hold N / hold Y | Direct Star Finger / inhale / zoom |
| J | Toggle Hamon/vampire mode when acquired; otherwise direct Time Stop |
| U / I | Either Stand Quick move / The World Time Stop Punch |

Direct heavy-punch and uppercut/kick bindings remain available in Controls but start unbound. All keys can be rebound under Controls → Ripples of the Past. A one-time migration changes recognized old defaults while preserving custom bindings, and backs up `options.txt` before saving.

The original overlay sits at the **top left**. It uses the original power icon, ornamental hotbars, selected-action names, folding/fading, textured stamina/Resolve bars, cooldown wipes and crosshair finisher. Holding V or B unfolds that row; wheel selection includes an empty slot that lets the mouse button retain its vanilla behavior. The old Alt-click selection system and permanent cost-text panel are gone. Creative has unlimited stamina and no finite stamina bar, matching the original overlay.

The layout editor uses the original texture and slot interaction. Left-click to pick and place a move, including across rows; right-click to enable or disable it. Number keys reposition the picked, hovered or keyboard-focused move. **Done and Escape both save.** Layouts persist separately for each Stand, Hamon and vampirism in `config/jojo/action-layouts.json`.

Select Time Stop and **hold its mouse button through charging**: 40 ticks for Star Platinum, 30 for The World. Releasing early cancels the charge. Finishing the charge starts the stop; releasing that completed press does not resume it. Release and press again on Resume Time to leave eleven ticks; another press resumes immediately. The direct Time Stop shortcut uses the same resume sequence; vampires need a separate binding if it shares J with vampire mode.

Source mappings and presentation limits are recorded in [Original HUD and camera](migration/ORIGINAL_HUD_CAMERA.md).

## Third-person camera

F5 now uses independent 360-degree orbit and camera-relative movement in eight directions. Looking around does not turn the player until movement or an action needs a facing direction. Attack/use aligns the player with the visible aim before sending the action. Vanilla handles camera distance and wall collision; entering or leaving the aiming ray no longer changes the camera's orbit.

This behavior was implemented independently after reviewing Better Third Person's advertised behavior. No code from that all-rights-reserved mod was copied. Your own Stand/finger still fades in first person and when it crosses the third-person view; manual first-person control hides the local Stand body. Other players' Stands keep normal opacity. Compatibility with other camera mods remains unverified.

## Hamon (Draft 16.1)

Find a Hamon Temple in newly generated windswept hills, or buy its map from an expert cartographer when the original offer conditions succeed. Right-click the Hamon Master to learn. Player teachers also work through the original request/accept flow. Existing vampires cannot learn Hamon; later transformation remembers genuine prior Hamon training.

Press **H** for the original Hamon window: introduction, stats/exercises, Strength, Control and character techniques. Press **J** for Hamon action mode. Hold middle mouse to breathe; left/right mouse use selected actions, V/B select rows, Shift selects variations, and backslash edits the layout. Hold Alt for ordinary item interactions while action mode is active.

Train Strength/Control through actions, and complete mining, running, swimming and meditation exercises for breathing progression. The original daily rollover, training bonus, skipped-day deterioration, mask effects, energy/stability distinction and server configuration are preserved. Buy skills with earned points near a qualified teacher where required. Choose Jonathan, Zeppeli, Joseph, Caesar or Lisa Lisa, with original technique slots, prerequisites and perks.

Equipment includes the Breath Control Mask, gloves, bubble gloves/soap, oil, clackers and Molotovs for Throwables Infusion. Abilities include original melee/overdrives, healing and body manipulation, movement/leaf glider, infused objects/projectiles/plants, projectile shield, detector/hypnosis/shock, bubbles/cutter/barrier and technique-specific combat. Deep Pass and Crimson Bubble preserve their original inheritance rules. Original settings live in `config/jojo/hamon.json`.

Draft 16.1 fixes the water/lava-walking crash by restoring the original fluid-displacement method; existing draft saves need no reset. See [crash details](migration/HAMON_LIQUID_WALKING_FIX.md).

Draft 16 corrects source damage/contact hooks, training rollover/cache timing, skill-window interactions, handed kick selection, infusion eligibility, glider fall handling and Molotov prediction. See [source review](migration/HAMON_SOURCE_REVIEW.md).

See [Draft 16.1 playtest](migration/HAMON_PLAYTEST.md), [progression source mapping](migration/HAMON_PROGRESSION_PARITY.md), and [equipment/world source mapping](migration/HAMON_CONTENT_PARITY.md). Manual visual/combat acceptance remains pending; integrations requiring unported Stands and other power milestones remain dependencies.

## Vampirism (Draft 13)

Find a **Stone Mask** in an unopened jungle-temple chest (original 8% bonus chance), or use `/give @s jojo:stone_mask`. Equip it on your head. Hold **exactly one knife**, then right-click; hold Alt if a power hotbar captures right-click. Activation grants full vampirism, costs one mask durability, and drops the mask after 101 ticks. Use shelter or nighttime: sunlight deals damage and prevents healing. Peaceful difficulty prevents transformation.

**J** opens the original vampire HUD. Empty your main hand. Hold **V** and select **Claw Lacerate** or **Blood Drain**; left-click uses Claw, holding left-click drains a nearby living target. Blood fuels healing and stronger passive effects. **Blood Gift** occupies the right row: hold right-click for three seconds on a human player with three hearts or less. Donor needs more than five hearts and spends five blood per tick plus five hearts on success. The recipient becomes a weak vampire and heals; a Stone Mask upgrades them to full power. Weak vampires have Drain/Gift only and lose vampirism on death. Full vampires retain it.

Vampirism coexists with either Stand. **K** opens Stand mode; **J** opens vampire mode. With default shared J bindings, use Time Stop from the Stand HUD or rebind its direct shortcut. Vampire layouts save independently in the same editor. This follows the original weak/full distinction rather than adding a level system.

Full vampires also select Freeze and Space Ripper Stingy Eyes from the attack row; zombie summons and Evil Atmosphere join the ability row. **Hold eyes for at least one second, then release to fire.** **Shift + Space** in vampire mode leaps. Claw, Drain, Gift and Freeze need an empty main hand. At least 80% blood raises the Time Stop cap to nine seconds; blood tiers also improve Stand stamina and regeneration.

Craft any of sixteen oak coffin colors. Coffins permit daytime sleep and protect vampire respawns. Begin curing with an **enchanted golden apple under Weakness V**. Progress requires zero blood, takes 40 minutes by default, and finishes after waking; nearby beds/iron bars may accelerate it. Server settings live in `config/jojo/vampirism.json`; glowing-eye preference lives in `config/jojo/vampire-client.json`. Both are created automatically.

Masks now place as original blocks, react to blood and preserve item state. Aja stones drop from ordinary redstone ore under the original Fortune rules. Aja masks keep the original lethal non-Pillar-Man behavior. Sledgehammers can destroy placed masks. Satiporoja scarf and retained-Hamon vampire interactions are present; natural Hamon learning is available in Draft 15; Pillar Man evolution and the separate zombie-player power remain separate milestones. Operator `/rotp vampire hamon_memory <strength>` exposes the retained-Hamon adapter for playtesting.

Draft 12 also repairs vertical Stand attack positioning/poses and adds rebound-key knife instructions in tooltips and the held-item HUD. Check attacking targets above and below you, including F5. Knives remain in the Creative Combat tab and craft four per iron ingot above a stick. See [vampirism source mapping and remaining scope](migration/VAMPIRISM_PARITY.md).

## Knives (Draft 11)

Craft four knives with one iron ingot above one stick; stacks hold sixteen. Right-click throws up to eight and Shift throws one. Hold Alt when the selected Stand action captures right-click, or close Stand mode with K. Summoned The World throws up to eight additional knives from the remaining stack; in Survival, sixteen becomes two volleys of eight, while eight becomes one volley. The primary count sets the cooldown at three ticks per knife. There is no extra stamina or Resolve gate.

Thrown knives retain five total ticks of flight during stopped time, then freeze. Entity collisions during that window halt the knife without damage. Resume checks the collision again, allowing a moving target to escape. Ordinary Stand damage still applies immediately during Time Stop. Knives embedded in blocks can be recovered; Creative projectiles remain Creative-only. Single held knives give +2 melee damage and cut cobweb quickly. Any stack strips logs without consumption. Original stack textures, projectile geometry, sounds and player stuck-knife appearance are retained.

Use `/give @s jojo:knife 16` for a controlled playtest. See [knife source mapping and limits](migration/KNIFE_PARITY.md).

## Quick playtest

With commands enabled:

```text
/rotp grant @s star_platinum
```

Or use `/rotp grant @s the_world`. Press M to summon, then use V/B to select actions. Creative unlocks all migrated moves. Switch to Survival to check stamina drain, regeneration, Resolve gates and the red low-stamina warning.

Both Stands share melee, barrage, guard, manual control and human Time Stop. Light punches and landed barrage hits build finisher charge. At 50% charge and Resolve level 1, a heavy punch selects Uppercut or The World's Roundhouse Kick; it consumes 51 percentage points and preserves any remainder.

| Stand | Survival unlocks | Time Stop training |
| --- | --- | --- |
| Star Platinum | Uppercut 1; Inhale 2; Star Finger 3; Time Stop / Quick move 4 | 0.25 points per actual stopped tick |
| The World | Kick 1; Time Stop/Time Skip 2; Time Stop Punch 3 | 0.1 points per actual stopped tick |

Time Stop starts at five ticks, grows with training, and caps at 100 ticks/five seconds for humans. Resolve bursts quadruple training. A completed stop costs 225 upfront plus `900 / durationTicks` per active tick. Survival cooldown is three times elapsed ticks, reduced during Resolve. Creative bypasses stamina costs, training duration and active-stop cooldown.

The World uses its original model, eight clips, sounds, stats and five-block effective reach. Its heavy punches pierce part of armor; Kick sweeps nearby visible targets. Time Skip costs 180 plus scaled travel and checks the player's full body against walls, unloaded chunks and the world border. Time Stop Punch costs 140 plus scaled implied time, moves only the Stand, winds up for five ticks and has a 50-tick ability cooldown. Draft 10 restores original target placement, implied clocks, backshot, recovery cancellation and the deferred frozen-heavy impact sound. Draft 11 adds original knives; Draft 12 adds the original weak/full vampirism foundation. Draft 13 adds original vampire Time Stop, stamina and Resolve modifiers.

The server validates ownership, unlocks, reach, walls, stamina and timing. Held actions end on release, menu opening, focus loss or stale input. Barrage, guard and heavy/special actions restrict movement. Manual control anchors the player and limits the Stand to ten blocks; release O to return. Full combat and simulation limits are in [Migration status](migration/STATUS.md).

Draft 10 restores original time-action behavior: immediate frozen damage, stacked momentum, SP/TW Quick move, and The World backshot/recovery-cancelling Time Stop Punch. Use U or Shift with Time Stop selected for Quick move; I with Shift selects backshot. First resume click leaves eleven ticks; another resumes immediately. See [source mapping and remaining scope](migration/TIME_STOP_PARITY.md).

## Awakening, Resolve and training

Find a **Beetle Stand Arrow** in an unopened desert-pyramid chest: the original independent 2% bonus chance per chest. Gather **30 XP levels**, then right-click the arrow. Infection consumes one level every ten ticks; paying the full cost or surviving the maximum infection duration awakens a Stand. Without XP, infection can kill you. Milk cannot cure it. Each previous successful Arrow awakening adds five levels to the next cost. Normal/Beetle arrows have 25/250 durability; each successful Survival piercing costs one durability. Creative awakens instantly without cost.

The natural pool contains **Star Platinum and The World**. Each successful awakening reserves one unused Stand in the current server-wide generation; after both are used, a new generation begins. Joining assigns a provisional random candidate, so an unawakened player does not reserve a duplicate. Operator grants bypass this pool. Normal save/reload persists allocation; crash recovery between separate world/player saves is still pending. See [allocation rules](../.planning/phases/04-acquisition-and-progression/04-STAND-ALLOCATION.md).

Press H for progression; Hamon users can open the Stand overview from their Hamon screen. Earn Resolve through qualifying combat; passive animals, misses and rejected hits give none. Original thresholds, combat/low-health boosts, saved combat records and decay are implemented. Each Stand keeps separate levels and training through death, replacement and reload. Creative access does not award earned levels. Hamon has its separate original training system described above.

Operator test commands:

```text
/rotp progression @s level 4
/rotp progression @s training 95
/rotp progression @s reset
/rotp status
/rotp clear @s
```

Changing or resetting progression dismisses the Stand; summon again with M. Reset keeps ownership but clears that Stand's levels, combat records and training. Grant, clear and progression edits require operator privileges.

## Verification and project layout

- `core/`: platform-independent formulas, state, timelines, layouts and orbit math.
- `src/main/`: registries, attachments, commands, networking and server gameplay.
- `src/client/`: controls, HUD, camera, models and animation.
- `src/gametest/`: server and real-client integration tests, excluded from the release JAR.
- `tools/`: model conversion, parity checks and draft packaging.

```sh
./gradlew build runClientGameTest -PacceptMinecraftEula=true
python3 tools/verify_star_platinum_model.py
python3 tools/verify_the_world_model.py
```

Only pass `-PacceptMinecraftEula=true` if you accept the [Minecraft EULA](https://www.minecraft.net/eula). The user's acceptance covers this machine's local test server. `build` runs core tests and server GameTests. **Draft 13 passed 80 core tests, 173 required server GameTests, 29 model checks and the focused vampire client suite in singleplayer and a dedicated connection.** Add `-PclientGameTest=VampireClientGameTest` for the focused flow; omit it for the full client matrix. Original effect tracking was restored after the focused test exposed invisible frozen-mob effects. Full lifecycle/AI/cosmetic manual playtest remains pending. Reports/screenshots are under `build/` and excluded from Git.

After successful combined verification, package with:

```sh
python3 tools/package_draft.py --verification-log /path/to/successful-verification.log
```

The packager checks evidence and source timestamps, verifies models, and records hashes. GSD plans and reports live under `.planning/`. Source progress is maintained in the private [ClydeGluten/ROTP_port repository](https://github.com/ClydeGluten/ROTP_port), branch `port/fabric-26.3`.

## Source and credits

Original author: **StandoByte**. Original code, assets and contributor credits are preserved; see root `LICENSE` and `src/main/resources/UPSTREAM_CREDITS.txt`. The port retains GPL-3.0-only.

- [Upstream source](https://github.com/StandoByte/Ripples-of-the-Past), commit `72862a5826ed45d1dc26e90b37853097acda35de`.
- Input JAR: `JJBA-RipplesOfThePast-1.16.5-0.2.2.2.jar`.
- Input SHA-256: `92af778c18737bf4bf01386af86c0e75db7e89ba0f6dc0ec7f88895b92ff2226`.

Delegated changes are reviewed and tested before release. Remaining Stands, non-Stand powers, most world content, Forge-save import and addon compatibility are still pending.
