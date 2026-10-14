# Migration status

Target: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Java 25. Upstream baseline: Ripples of the Past 0.2.2.2 at `72862a5826ed45d1dc26e90b37853097acda35de`.

**Draft 9 corrects the HUD and camera rejected during the Draft 8 playtest.** The supported Stand HUD now uses original methods/assets and default placement; third person uses independent orbit instead of target-distance-dependent shoulder convergence. Star Platinum and The World remain the playable pool. This is not full original-mod parity. Draft 9.1 fixed the world-entry crash; Draft 9.2 fixes the subsequent mob hit-reaction report. User accepted Draft 9.2. Draft 10 extends original Time Stop combat and instant-time behavior; 76 core tests, 124 server GameTests, 29 model checks and all nine client suites passed. User accepted Draft 10. Draft 11 knives are verified and packaged: 76 core tests, 146 server GameTests, 29 model checks and all ten client suites passed. Its manual playtest remains pending.

| System | Status | Current scope |
| --- | --- | --- |
| Fabric build | Implemented | Java 25, separate common/client sources, embedded reusable core, verified-draft packager |
| Player state | Partial | Persistent Stand ID, stamina, development, previous ownership, owner-only synchronization |
| Manifestations | Playable | Star Platinum/The World summoning, ownership, following, lifecycle cleanup and manual control |
| Melee | Partial | Server phase timing, costs, reach/wall checks, knockback, automatic finishers, shield disable; The World armor piercing/kick sweep |
| Barrage and guard | Partial | Held-input leases, stamina, directional resistance, guard break, movement restrictions |
| Stamina | Playable | Original formulas/regeneration; original textured bar and low-stamina warning; Creative finite bar hidden |
| Rendering | Partial | Original models, textures and supported clips; Star Finger extension; local camera-obstruction fading |
| Acquisition | Playable | Self-piercing arrows, original durability/XP/virus rules, Beetle Arrow chest loot, persistent history |
| Awakening generations | Playable | Successful natural awakenings exhaust SP/TW once per generation; ordinary reload persistence |
| Resolve/training | Playable human slice | Separate per-Stand progression, original combat gains/decay/records, unlocks, development, human Time Stop learning; H overview |
| Original Stand HUD | Implemented for supported actions | TOP_LEFT textured bars, power/name, ornamental folding hotbars, selected names, cooldown/learning indicators, finisher and charge crosshair |
| Original controls/layout | Implemented for supported actions | K mode, V/B focus, mouse actions, Shift variants, Alt bypass, backslash editor, cross-row placement/visibility, per-Stand persistence |
| Third-person camera | Implemented for this slice | Independent 360-degree orbit, eight-direction movement, action aiming, vanilla wall collision and perspective/world lifecycle |
| Star Platinum specials | Playable | Uppercut, Star Finger, inhale, zoom, manual control, human Time Stop and Quick move |
| The World specials | Playable | Kick, manual control, human Time Stop, target-relative Quick move and Time Stop Punch with backshot/recovery cancellation |
| Time Stop simulation | Partial | Regional entity/client freeze, frozen-player guards, paused random/block-entity ticks, retained scheduled block/fluid ticks |
| Knives | Draft 11 | Original item, recipe, stack models, projectile, The World volley, stopped-time flight and stuck-player visuals; see KNIFE_PARITY.md |
| Other content | Pending | Remaining Stands, Hamon/vampirism, most items/blocks/recipes/world generation |
| Migration/compatibility | Pending | Forge-save import, addon compatibility and external-mod compatibility matrix |

## Draft 11 knives

The original `KnifeItem`, `KnifeEntity`, `ItemProjectileEntity` and `TheWorldEntity.onKnivesThrow` provide the source rules. Primary volleys, remaining-stack supplementary volleys, cooldowns, damage through invulnerability timers, five lifetime stopped-time flight ticks, deferred/retraced entity collision, block pickup, save/load and original assets are ported. Recipe, single-knife attributes, tool behavior, dispenser use and stuck-player rendering are included. Unlike melee, knife damage waits for resumed collision. See [source mapping and adaptations](KNIFE_PARITY.md).

## Draft 10 original time actions

Project-wide behavior now explicitly follows the original mod unless the user requested a change. Damage, armor, attribution and death happen immediately during Time Stop. Frozen knockback accumulates without vanilla horizontal damping, then physics resumes normally. Only the original extra The World heavy impact sound waits for thaw; no health damage is queued. Removed entities discard pending callbacks.

Both Stands have original Quick move targeting, costs, depleted-stamina behavior, independent cooldowns and implied entity clocks. The World Time Stop Punch supports manual aim, Shift backshot, retained targets and recovery cancellation, including barrage recovery. Its instant component costs 90 plus implied ticking stamina at start; the ordinary 50 stamina is charged at impact. Creative cooldown is zero. First manual resume click leaves eleven ticks; the next ends immediately.

Collision checks, tracking packets and original HUD variants are adapted to modern Minecraft. See [exact source mapping and remaining scope](TIME_STOP_PARITY.md). Overlapping stops, broader immunity/perception, original shaders and vampire durations remain open.

## Draft 9.2 mob hit reactions

The next playtest reported missing knockback and passive-mob reactions across attacks. New live-AI tests confirmed two barrage defects: velocity was restored after each hit, erasing all knockback, and its custom damage type lacked the vanilla panic tag. Rapid light combos also lost the second hit to vanilla immunity. Fresh ordinary hits already passed knockback/panic checks.

Stand damage now ports the original `DamageUtil.hurtThroughInvulTicks` behavior: each accepted action can hit at its own cadence, while previous vanilla cooldown and last-hit amount are restored in `finally`. Entity invulnerability, blocking, PvP permissions, costs, reach and walls remain enforced. This applies to melee, Star Finger, kick sweep and barrage only. Vanilla/environmental damage uses its existing path.

Barrage damage joins `panic_causes` and suppresses automatic knockback through `no_knockback`; an accepted hit then applies the original reduced impulse (`0.4 * clamp(power * 0.0075, 0, 1)`, 0.048 at power 16) through vanilla's resistance-aware method. Active Resolve burst retains zero barrage impulse. Explicit hurt-direction indication preserves player feedback skipped by `no_knockback`. Full Resolve burst gravity/navigation suppression remains unported.

The new short-target Time Stop Punch check exposed a separate aiming error: after relocating, The World reused the player's pitch and punched over a cow. It now faces the acquired target from its new eye position; normal range/wall checks still run when the attack lands. Broader instant-time parity remains pending.

Draft 9.2 passed **76 core tests, 100 required server GameTests, 29 model checks and all seven client suites**. Combined evidence: `/tmp/rotp-draft9-2-verification2.log`, BUILD SUCCESSFUL in 2m 50s, preserved in the versioned bundle. Coverage includes actual AI panic/navigation and physical movement for both Stands, rapid follow-up hits, original barrage strength, resistance, Resolve, rejected hits, existing vanilla cooldown preservation and ordinary hits following Stand damage. A new client suite verified network-visible displacement using real controls in both singleplayer and a dedicated server. The real-client barrage screenshots were inspected. An independent read-only review found no blocker in the damage, impulse or short-target aiming changes.

## Draft 9.1 world-entry crash hotfix

The user's `crash-2026-10-14_15.47.54-client.txt` fails in `InputConstants.isKeyDown` while `KeyMapping.setAll` restores focus after world loading. Draft 9 registered and migrated two unbound actions using the old GLFW `-1` sentinel. Minecraft 26.3 uses SDL scancodes; `InputConstants.UNKNOWN` is 0. Both saved `key.keyboard.-1` values were confirmed in the user's options.

Defaults and migration now use `InputConstants.UNKNOWN`. Startup separately repairs only the invalid heavy/uppercut bindings, even if `original-controls-v1` already exists. Custom bindings remain; a separate `options-before-unbound-key-repair.txt` backup preserves the original settings. In-memory correction occurs before optional disk backup/save, so an unwritable config folder cannot leave the invalid keys active.

`ControlsClientGameTest` fails against Draft 9, then verifies defaults, completed-marker repair, custom bindings, options reload and direct native SDL keyboard-buffer access. Earlier simulated-input tests replaced native polling, and the combat suite rebound both actions before loading a world. The new suite runs first and checks default metadata as well as persisted repair.

A normal `runClient` launch used copies of the user's options/config and newest test world, without the game-test mod. It repaired both bindings, loaded the world, displayed the pause menu after focus loss and shut down cleanly. Evidence: `/tmp/rotp-draft9-1-native.log`; screenshot `/tmp/rotp-draft9-1-native.png`. Original user settings/worlds were not modified by this check. The native log/screenshot and failing pre-fix regression log are preserved in the Draft 9.1 bundle.

Final combined verification passed 76 core tests, 82 required server GameTests, 29 model checks and all six client suites. Evidence: `/tmp/rotp-draft9-1-verification3.log`, BUILD SUCCESSFUL in 2m 22s. Two test races were corrected by waiting for a perspective transition before direct fixture angle writes and for server disconnect processing before asserting manifestation cleanup; gameplay assertions remain unchanged.

## Draft 9 presentation and input

The previous custom cost panel and Alt-click action scheme are replaced. M summons; O holds manual control; K toggles Stand mode. V/B held with wheel or number keys select each row; ordinary LMB/RMB trigger its selected action. Alt temporarily yields mouse actions to vanilla without preventing V/B selection. Shift converts punch/barrage to heavy or the unlocked finisher. Direct heavy/uppercut bindings start unbound; R/Z/X/C/N/Y/J/U/I remain available.

Backslash opens the original textured layout editor. Moves can be placed across rows, right-clicked to enable/disable, and repositioned with number keys. Done and Escape save. One-time key migration recognizes old defaults, preserves custom bindings and backs up options before writing. Mouse Time Stop now requires holding through its charge; early release cancels, while release after successful activation does not resume the stop.

The HUD restores original full/short stamina and Resolve bars, icons, fades, folding/clipping, selection pulses, names, cooldown wipes with authoritative total durations, learning meter, finisher sprite, charge fill and completed-hold crosshair flash. Source mappings and remaining UI gaps are in [Original HUD and camera](ORIGINAL_HUD_CAMERA.md).

Camera behavior was independently implemented from Better Third Person's advertised behavior. Its all-rights-reserved code was not copied. Vanilla collision replaces the old shoulder offset/convergence correction. First-person Stand/finger fading and manual-body hiding remain. Other camera mods have not been validated.

## Gameplay parity and limits

**Combat.** Light punches and landed barrage hits build finisher charge. At 50% and Resolve 1, heavy attacks use Uppercut/Kick and consume 0.51 charge, preserving the remainder. Barrage/guard/heavy-special movement factors are 0.15/0.3/0.5; sprinting is blocked during slowed actions. Server modifiers clean up on release and lifecycle changes. Parry, clashes, afterimages and automatic guarding remain incomplete. Barrage timing is deterministic rather than the original randomized extra-hit timing; cross-mod movement compatibility is unverified.

**Star Platinum.** Survival gates are Uppercut 1, Inhale 2, Star Finger 3 and Time Stop 4. Star Finger retains cost/range/timing and one hit per reachable target, but not original block breaking or separately damageable projectile parts. Inhale retains pull/air drain with vanilla cloud particles. Manual control anchors the player, has a ten-block sphere and twelve-tick input lease, can phase through blocks and cannot load chunks. Original manual interaction modes and first-person Stand hands remain incomplete.

**The World.** The original model has 59 parts/68 cubes, eight clips and interpolated head-look expressions including the kick spin. Heavy armor piercing uses `power * 0.015`; Kick sweeps adjacent visible targets for half damage. Resolve gates are Kick 1, Time Stop/Time Skip 2 and Time Stop Punch 3. Time Skip costs 180 plus scaled travel. Time Stop Punch costs 140 plus scaled implied time, moves only the Stand, has five-tick windup and a 50-tick ability cooldown. Full-body travel checks precede movement/resource commitment. Draft 10 restores target-relative/ground placement, aggro reset, implied clocks, backshot, the specific deferred heavy sound and independent cooldowns. Draft 11 adds knives; vampire behavior remains open.

**Resolve.** Original default thresholds, attack/health boosts and ten best combat records are implemented. Accepted Stand damage earns full raw points; eligible player attacks while summoned earn half. Passive animals, self-hits and rejected hits give none. Decay starts after 400 ticks, counted twice as fast while dismissed, then removes two points per tick. Expiration records a peak even when the two-tick countdown reaches zero, fixing an upstream edge case. Levels/training persist per Stand; death resets combat Resolve and preserves its peak. Resistance applies after absorption except void damage; vanilla combat-stat accounting still records pre-Resolve damage. Effect shader/particles, souls, configurable progression, extra max-level counters, other-power multipliers and cosmetic chat/remote boosts remain pending. Hamon training is not included.

**Time Stop.** Human duration starts at five ticks and caps at 100. Star Platinum charges for 40 ticks and trains at 0.25 points per actual stopped tick; The World charges for 30 and trains at 0.1. Resolve quadruples training. Cost is 225 upfront plus `900 / durationTicks` per active tick. Survival cooldown is three times elapsed ticks, reduced during Resolve; charge cancellation retains a 20-tick safety cooldown. Creative bypasses costs, learned duration and active-stop cooldown without awarding earned levels. Separate per-Stand learning persists through replacement/reload.

The stopped region is the original square radius of twelve chunks. Overlapping regions are rejected. Scheduled block/fluid ticks wait until resume; random ticks and block entities pause. Immediate neighbor updates and external events are not deferred into a full world snapshot. Damage applies immediately, and normal damage cooldowns advance while frozen. Frozen impacts stack momentum; The World normal heavy queues its extra resume sound. Original shaders, invasion, vampire duration and broader visibility rules remain unported. Both Stands have instant Quick move. Client state is tracked per caster and synchronized on join/dimension changes. Cooldowns survive death, dismissal, regrant and reconnect within a server session, but not server restart.

**Acquisition.** Normal/Beetle Arrow durability is 25/250. The Beetle Arrow adds an independent 2% desert-pyramid chest roll, including datapack replacements; already-opened chests are not refilled. Infection uses 30 levels plus five per previous awakening, one payment per ten ticks, 0.75 paid damage or 7.5 unpaid damage, and a maximum `(requiredLevels + 1)` seconds. Paying the full cost or surviving that duration awakens the assigned Stand; afterward, healing restores 0.25 health each tick. Milk preserves infection. Death/admin effect removal cancels an unfinished attempt; history remains. Creative bypasses costs. Meteorite materials, recipes, projectile acquisition, enchantments, mob awakening and configurable pools/costs remain pending.

**Allocation.** The migrated natural pool is Star Platinum and The World. A joining player receives a provisional candidate; only successful awakening reserves a slot. Generation/used IDs persist on the Overworld. Existing owners seed once on their first post-update join; operator grants bypass natural allocation. A permanent player marker prevents re-seeding on death/relogin. Ordinary save/reload is covered. Atomic recovery after a crash between separate world/player saves, configurable pools and offline legacy-owner scanning remain open.

## Verification status

Draft 8 completed 63 core tests, 82 required server tests, 29 model checks and five real-client suites. Earlier intermediate runs had intermittent world-transition/renderer failures without a proven common cause; the final Draft 8 combined run passed. No engine workaround or general stability fix is claimed.

Draft 9 passed **76 core tests, 82 required server GameTests, 29 model checks and all five real-client suites**. Combined evidence: `/tmp/rotp-draft9-verification2.log`, BUILD SUCCESSFUL in 2m 22s; the same log is preserved in the versioned Draft 9 bundle. Relevant evidence covers camera stability when targets change, orbit/movement/action aim, walls and lifecycle, real mouse/key routing, original HUD pixels, layout persistence, save/reload and dedicated connections. Both model verifiers passed during packaging.

Three intermediate HUD runs failed in the Vulkan renderer when the test resized its window, including one on the title screen. Tests now start at 1280×720 and change only GUI scale; no runtime renderer patch is shipped. A dedicated-server pixel check initially counted a yellow chat-notification icon covering the bar; clearing that test notification restored measured widths of 100px/50px. The final suite passed with real stamina drain/regeneration and both GUI scales. General renderer/world-transition stability is not claimed.

Run from `fabric/`:

```sh
./gradlew build runClientGameTest -PacceptMinecraftEula=true
python3 tools/verify_star_platinum_model.py
python3 tools/verify_the_world_model.py
```

The user's EULA acceptance applies to this local test server. Reports/screenshots are under `fabric/build/` and excluded from Git. Versioned draft bundles include verification logs, screenshots, manifest and checksums. Check `drafts/latest/manifest.json` for the actual packaged version.

## After the playtest

The user accepted the corrected presentation and reported combat hit reactions. Wait for the Draft 9.2 combat playtest before starting another content milestone. Remaining work includes combat/animation parity, Star Platinum blink, additional UI settings, acquisition materials/projectiles, crash-safe allocation recovery, more Stands and non-Stand powers. Forge-save migration and addon compatibility need stable schemas/registries first. GSD tracks plans and acceptance under `.planning/`.

Draft 9.2 mod SHA-256: `9cfb11dd71c73e6b662a09d1bae415ef98770b4641109f3f585dfcdea3d97f4f`. `rotptest --dry-run` passes; source changes are staged. `fabric/build/run` is an ordinary directory with final evidence, and user worlds under `fabric/run` were not modified.
