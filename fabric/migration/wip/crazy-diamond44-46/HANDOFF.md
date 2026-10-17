# Crazy Diamond 44–46: interrupted implementation snapshot

Status: **WIP; not integrated, compiled, or verified.** Preserved at the user's explicit request to wrap up on 2026-10-18. These Java files deliberately live outside the Fabric source sets. Do not register or move the whole directory into production without completing the remaining audit and integration. Original Forge sources and user saves were not modified. No worker build, commit, or push was run.

## Integrated dependency

Plan43 restoration history is already in the main source set; root reports all three world cases passed. Shared `stand/StandHeavyImpact.java`, `StandBlockShardEntity.java`, and `SourceEraMaterials.java` were copied and staged separately for MR41. Root subsequently fixed modern compilation signatures, added `clear(Entity)`, and integrated impact-state replacement with vampire collisions. The authoritative shared files are the live source files; stale scratch copies are intentionally excluded from this snapshot.

Remaining shared presentation: shard renderer and `StandHeavyImpact.ShockwavePayload` client receiver are **not implemented**. Original `BlockShardRenderer` uses the block particle sprite, two caches of 64 cuboids, UUID low-six-bit dimensions 5–8 (glass thickness1), source UV cache behavior, and UUID-selected static Euler angles. Source `LotsOfBlocksBrokenPacket` randomly caps outgoing visuals at256; client chooses nearest128, plays sounds every eighth block, and respects reduced-particle settings. Root was informed the current server implementation lacked that cap. Check current live root edits before changing it.

## Draft inventory

Plan44: `CrazyDiamondRestorationActions`, `RestorationSelection`, `RecipeReversal`, `CrazyDiamondHealing`, `TntMinecartAccess`, `CreeperPowerAccess`, `CrazyDiamondRestorationGameTests`, and `CRAZY_DIAMOND_RESTORATION.md`.

Plan45: `CrazyDiamondEntity`, `CrazyDiamondCombatActions`, `CDAnchorItem`, `CDBlockBulletEntity` (also package-private common projectile), `CDBloodCutterEntity`, `CDRecoveryModifiers` (also eye/firework flight entities), `AngeloRockEntity`, `FireworkRestorationAccess`, `AngeloMobAccess`, `AngeloLivingAccess`, and `CrazyDiamondCombatGameTests`.

Plan46: `CrazyDiamondModel`, `CrazyDiamondAnimation`, `CrazyDiamondRenderer`, and `generate_cd_presentation.py`. The script extracts exact authored rotation tables and adapts the established segmented model wrapper; the geometry itself uses the existing generated `CrazyDiamondGeometry`. **Renderer references missing `CrazyDiamondEffects` and `CrazyDiamondProjectileRenderer`; this is an unfinished snapshot, not runnable code.** `CrazyDiamondMarkers` is also not written. No presentation manifest, plan45/46 parity ledger, or completion summary has been created.

## Integration contracts

- Root owns shared bootstrap/config/action HUD/input/mixins and the sole Gradle/client lane. Register common actions, entity attributes, lifecycle handlers, payloads and grouped GameTests only after a coherent batch is ready.
- New mixins go in the restoration mixin package: `TntMinecartAccess`, `CreeperPowerAccess`, `FireworkRestorationAccess`, `AngeloMobAccess`, `AngeloLivingAccess`. Root owns the mixin JSON.
- Angelo uses the live shared `PossessionService.ExternalHost`, `beginExternal(ServerPlayer,Entity)` and persisted `asAlive=false` session. Root supplied `isEntrapped`, optional `grunt`, source Shift restriction, logout/rejoin, host-loss death, and respawn/grunt payload intents. No separate possession attachment.
- Root needs Angelo sound/loot hooks: `AngeloRockEntity.cancelPlayerHitSound` consumes PLAYER_ATTACK_STRONG/WEAK; when `mobLootFortune` equals the loot entity, use the maximum of vanilla looting and the attacking pickaxe's Fortune. Check root's current work before adding anything.
- Use the integrated `stand.tracking.StandEffectStore`: `ofType`, `targetsInRange`, `getTargetLookedAt`, and `target` work on server and client. Client entries have public identity/type/target/ticks only. CD anchors are intrinsic stack custom data, not tracked-item identities.
- Root's client possession facade supplies `canExitAlive`, `requestRespawn`, `requestGrunt`, `actualGameMode`. Original Angelo buttons are added to **ChatScreen**, not a standalone death screen: respawn/spectate button at centerX−100,height−40,200×20; silent grunt icon button at centerX−124,height−40,20×20, additional.png UV238,150.

## Source decisions already reflected

- History capture always occurs; the default-false saveDestroyedBlocks setting gates SAVE/LOAD only. No history TTL/cap. Creative XP affordability and material consumption are preserved; XP debit is skipped. Actual loot is recorded without rerolling; source exclusions and improper-position mutation bug remain.
- Direct-block previous-state reversal, map reversal and brewing reversal are inactive source paths and remain excluded. Runtime recipe reversal implements active smith/cook/stone/craft behavior, including blasting-output restrictions.
- Heal death-window threshold15, rewind2, minimum revived health .001; no direct user heal, but linked Stand healing works. Terrain12 Manhattan/cone and per-placement stamina are source based.
- Heavy recovery item use, misshapen effects, block bullet material effects, dried blood, anchor custom data and Angelo formation are drafted from original active callers. Anchor-make recovery was corrected to5.
- There is **no active visual misshape model or body-deformation layer** in the original. Active source reverses directional input and swaps jump/sneak for legs; reflects mouse sensitivity around −1/3 for face; swaps and later restores attack/use key bindings for arms. Plan46 wording must not be interpreted as authority to invent geometry.
- CD's authored heavy head angles deliberately bypass degree wrapping while body angles wrap. Negative idle easing and exact transition-breakpoint arithmetic are preserved. Blood-cutter pose is not implemented in the original model, so idle fallback is retained. Shared shard's reversed save/load-method bug is preserved.
- Source-era materials were extracted from Mojang's official1.16.5 server registry (763 blocks). Named dirt_path/short_grass/water_cauldron aliases were added. Modern-only blocks remain UNKNOWN. Mod blocks need explicit original-material adapters; no sound/tag approximation should replace them.

## Remaining audit and implementation work

1. Complete45 behavior review: source target-relative heavy/heal offsets, linked Stand knockback ownership, Angelo cross-owner uniqueness, sound voice cooldown policy, source-specific eye-shard damage type, projectile tracking intervals and hit-order behavior. Check empty-drop anchor custom-data handling. Source shard traces before restored movement; current shared shard ordering merits audit.
2. Compile modern APIs through the root lane. Drafts contain unverified signatures. `AngeloRockEntity.publishRestored` currently takes `Set<BlockPos>`; verify actual43 result collection type. Verify inventory test expectation includes offhand in `countItem`. The newly added model/renderer generic and snapshot APIs are uncompiled.
3. Complete46: restoration particle provider/hand-relative particle, loop lifecycle/reset-death receiver, misshape control hooks, owner-scoped dried-blood and anchor markers, source projectile geometries/textures, authored Angelo formation geometry/shadow, source ChatScreen widgets, shared shard renderer/shockwave receiver. Renderer currently calls missing helpers.
4. Read exact original normal-particle defaults; avoid silently substituting modern defaults. Preserve source block-bullet NORTH-face first-quad texture selection (glass fallback), while shard uses particle sprite. Modern renderer class/package changes need local jar inspection; old BlockRenderDispatcher/BlockModelShaper names were not found in26.3.
5. Source Angelo model uses original cuboid iteration order for formation despite constructing an unused shuffled list. Port all authored rock/shadow cubes rather than a surrogate two-block mesh.
6. Complete source pairing/resource ledgers and44/45/46 summaries with honest awaiting-verification status. Root owns global GSD state. Run the supplied six grouped real-world tests only in the consolidated lane, then focused two-player client scenes. No draft feature is verified or eligible for the rollable Stand pool yet.

## Checks performed

Source reading, official registry extraction, local `javap` API inspection and draft generation only. Parent reports43's existing world cases passed. No44–46 tests or compilation ran. All Java files here are deliberately non-production WIP retained for continuation.
