#!/usr/bin/env python3
"""Package a verified local draft, preserve versions, and atomically update drafts/latest."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
FABRIC = ROOT / "fabric"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verification-log", type=Path, required=True)
    args = parser.parse_args()
    log = args.verification_log.resolve()
    evidence = log.read_text()
    if "BUILD SUCCESSFUL" not in evidence or "BUILD FAILED" in evidence or "> Task :runClientGameTest" not in evidence:
        raise SystemExit("A successful build + runClientGameTest log is required.")
    match = re.search(r"All (\d+) required tests passed", evidence)
    if not match:
        raise SystemExit("The log must include a successful server GameTest run.")
    runtime_inputs = [FABRIC / "build.gradle", FABRIC / "gradle.properties", FABRIC / "core/build.gradle"]
    for directory in (FABRIC / "src", FABRIC / "core/src"):
        runtime_inputs.extend(p for p in directory.rglob("*") if p.is_file())
    if any(p.stat().st_mtime > log.stat().st_mtime for p in runtime_inputs):
        raise SystemExit("Source or build inputs changed after verification; rerun the checks.")
    properties = dict(line.split("=", 1) for line in (FABRIC / "gradle.properties").read_text().splitlines() if "=" in line)
    version = properties["mod_version"]
    jar = FABRIC / "build/libs" / f"RipplesOfThePast-Fabric-{version}.jar"
    with ZipFile(jar) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        assert metadata["version"] == version
        assert metadata["depends"]["minecraft"] == properties["minecraft_version"]
        assert archive.testzip() is None
        assert not any("GameTest" in name or name.endswith("mods.toml") for name in archive.namelist())
    model_check = subprocess.run([sys.executable, str(FABRIC / "tools/verify_star_platinum_model.py")],
                                 capture_output=True, text=True, check=True)
    world_model_check = subprocess.run([sys.executable, str(FABRIC / "tools/verify_the_world_model.py")],
                                       capture_output=True, text=True, check=True)
    api_candidates = list((Path.home() / ".gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api"
                           / properties["fabric_api_version"]).glob("*/*.jar"))
    api_candidates = [p for p in api_candidates if not p.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if len(api_candidates) != 1:
        raise SystemExit("Build first: expected exactly one cached Fabric API release JAR.")
    drafts = ROOT / "drafts"
    destination = drafts / version
    mods = destination / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar, mods / "RipplesOfThePast-Fabric-latest.jar")
    shutil.copy2(api_candidates[0], mods / api_candidates[0].name)
    shutil.copy2(log, destination / "verification.log")
    (destination / "model-verification.txt").write_text(model_check.stdout + model_check.stderr + world_model_check.stdout + world_model_check.stderr)
    (destination / "START-HERE.txt").write_text(f"""Ripples of the Past — Fabric draft {version}

Fastest local launch:
  {ROOT / 'play-draft.sh'}

Or use your Minecraft launcher:
  Minecraft {properties['minecraft_version']}, Java 25, Fabric Loader {properties['loader_version']} or later.
  Copy BOTH JARs from mods/ into that instance's mods/ directory.
  Remove any older Ripples of the Past JAR from that instance first.
  Create a fresh test world. Old Forge saves are not migrated.

Draft 11: knives and The World volley
  Craft four knives with an iron ingot above a stick. Maximum stack: sixteen.
  Right-click throws up to eight; Shift + right-click throws one. Hold Alt while Stand mode captures right-click.
  Summoned The World throws up to eight more from the remaining stack. Survival stack16 throws16; stack8 throws8.
  Cooldown is three ticks per primary knife. No added stamina or Resolve requirement.
  During Time Stop, thrown knives fly for five ticks, then freeze. Entity hits wait until time resumes.
  Resume traces collision again: moving out of the knife's path can avoid the hit. Melee damage remains immediate.
  Recover knives embedded in blocks. Creative knives cannot be collected in Survival.
  One held knife gives +2 melee damage and cuts cobweb quickly. Any stack strips logs without being consumed.
  Knives stuck in players use the original appearance and decay timer. Stack models use the original textures.
  Test ordinary throws, Shift throws and frozen volleys against mobs and another player, then save/reload.
  Commands: /give @s jojo:knife 16; /rotp grant @s the_world; M to summon.

Draft 10: original time actions
  Time Stop damage and kill credit apply immediately. Frozen knockback stacks and moves targets on resume.
  The World's extra frozen-heavy impact sound waits for thaw.
  U or Shift + selected Time Stop uses Quick move with either Stand, including while unsummoned.
  Star Platinum stops in front of a target; The World moves behind its facing.
  Blink has its own cooldown and gives no Time Stop learning; depleted stamina still permits the original short move.
  I: The World Time Stop Punch; hold Shift for backshot. Works from manual control and cancels attack recovery.
  First resume click shortens Time Stop to eleven ticks; another resumes immediately.
  Check these actions in first person and F5, against mobs and another player.

Draft 9.2 hotfix: mob hit reactions
  Rapid Stand hits now use the original independent timing instead of losing hits to vanilla immunity.
  Barrage applies the original small push and triggers passive-mob panic.
  The World's Time Stop Punch re-aims after moving so it can hit short mobs.
  Check repeated punches, heavy hits and barrage against cows and hostile mobs with either Stand.

Draft 9.1 hotfix: world-entry crash
  Fixes invalid unbound heavy/uppercut keys that crashed native keyboard polling after world loading.
  Existing Draft 9 settings repair automatically on launch; custom bindings remain.
  Previous options are saved in config/jojo/options-before-unbound-key-repair.txt.

Draft 9: original Stand HUD and independent third-person camera
  F5: orbit freely around your player; WASD moves relative to the visible camera.
  Targets crossing your aim no longer turn or reposition the camera.
  Vanilla wall clipping remains. Attacks and item use follow the crosshair.
  First-person Stand/finger fading and manual-control body hiding remain.
  Original 1.16 Stand bars, ornaments, folding rows, icons, names and cooldown wipes replace Draft 8's custom UI.

Controls (remappable in Minecraft Controls)
  M: summon/dismiss. K: toggle Stand HUD mode.
  Left/right mouse: selected attack/ability while Stand mode is active.
  Hold V/B + wheel or number key: select attack/ability; an empty slot yields vanilla input.
  Hold Alt: temporarily use normal Minecraft mouse actions.
  Shift + selected punch/barrage: heavy attack; at half finisher charge, uppercut/The World Kick.
  Backslash: original layout editor. Left-click pick/place, right-click enable/disable.
  Move actions between rows, or use number keys to reorder. Done, Escape and backslash SAVE.
  O held: manual control; WASD moves the Stand, Space/Shift moves vertically. Release to return.
  H: progression screen; scroll or use Up/Down/Home/End, Escape closes.
  Direct shortcuts: R punch; hold Z barrage, X guard, N inhale, Y zoom; C Star Finger.
  J: charge Time Stop, press again to resume. U: either Stand Quick move. I: The World Time Stop Punch.
  Heavy/uppercut direct shortcuts start unbound; bind them in Controls if wanted.
  Mouse-selected Time Stop must be held until charged. Releasing early cancels charging.
  With either Stand, Shift + selected Time Stop uses Quick move. Shift + Time Stop Punch uses backshot.
  Draft 8 default keys migrate once; custom rebindings remain. Previous options are backed up in config/jojo/.

Quick controlled playtest (commands enabled)
  /rotp grant @s star_platinum
  M summons and opens Stand mode. Test the two mouse rows, Shift variants and V/B selection.
  /rotp grant @s the_world
  M summons The World. Check its Kick, Time Skip and Time Stop Punch.
  Creative unlocks all moves and has unlimited stamina. For stamina drain/refill, use Survival.
  /rotp progression @s level 4
  /rotp progression @s training 95
  These give full Survival unlocks and human Time Stop training. Level changes dismiss the Stand; summon again.
  /rotp progression @s reset clears earned progress but keeps ownership.

Please check
  F5 orbit while standing, walking, attacking and near walls; switch front/rear/first-person views.
  Summon/dismiss and hold/release O. Camera should stay stable when a mob crosses the crosshair.
  HUD at your GUI scale, original row folding, cooldowns, stamina drain and regeneration.
  Backslash editor: reorder, cross-row moves, enable/disable, close/save, then reload the world.
  Hold/release barrage, guard and Time Stop; open menus while holding an action.

Existing Survival progression
  Find a Beetle Stand Arrow in an unopened desert-pyramid chest (2% bonus chance per chest).
  Gather 30 XP levels for the first awakening; each previous arrow awakening adds 5 levels.
  Use the arrow to pierce yourself. The virus takes one XP level every half-second.
  Without enough XP it can kill you; milk cannot cure it. Successful awakening heals afterward.
  Arrow awakenings award Star Platinum and The World once each per generation, then repeat.
  Provisional choices reserve nothing until successful awakening; command grants bypass allocation.
  Infection, ownership, Resolve and separate Stand learning survive ordinary save/reload.
  Land Stand hits on hostile mobs/players to gain Resolve; low health and sustained combat boost gains.
  Star Platinum unlocks: Uppercut 1, Inhale 2, Star Finger 3, Time Stop / Quick move 4.
  The World unlocks: Kick 1, Time Stop / Time Skip 2, Time Stop Punch 3.
  Survival Time Stop begins at 0.25 seconds and trains through use up to five seconds.
  Hamon training is separate and remains unported.

This is an incomplete development port. The supported Stand HUD uses original source methods/assets;
non-Stand UI, custom action-keybind slots and alternative HUD anchors remain pending.
Barrage clashes, Stone Mask knife interaction, vampirism, further Stands/content, overlapping Time Stop/immunity, original
Time Stop shaders and crash recovery between separate allocation saves remain pending.
See fabric/migration/STATUS.md, KNIFE_PARITY.md, TIME_STOP_PARITY.md and ORIGINAL_HUD_CAMERA.md for scope and source mappings.
Original assets and GPL-3.0 license retained; full source remains in the project.
""")
    screenshots = FABRIC / "build/run/clientGameTest/screenshots"
    playtest_screenshots = sorted({*screenshots.glob("*progression*.png"), *screenshots.glob("*camera*.png"),
                                  *screenshots.glob("*hud*.png"), *screenshots.glob("*stamina*.png"),
                                  *screenshots.glob("*hit-reaction*.png"), *screenshots.glob("*time-actions*.png"),
                                  *screenshots.glob("*time-skip*.png"), *screenshots.glob("*time-stop-player*.png"),
                                  *screenshots.glob("*time-stop-frozen*.png"), *screenshots.glob("*knives*.png")})
    if playtest_screenshots:
        (destination / "screenshots").mkdir(exist_ok=True)
        for screenshot in playtest_screenshots:
            shutil.copy2(screenshot, destination / "screenshots" / screenshot.name)
    hashes = {str(path.relative_to(destination)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(mods.glob("*.jar"))}
    manifest = {"version": version, "minecraft": properties["minecraft_version"], "fabric_loader": properties["loader_version"],
                "fabric_api": properties["fabric_api_version"], "created_utc": datetime.now(timezone.utc).isoformat(),
                "server_gametests_passed": int(match.group(1)), "client_gametest": "passed",
                "manual_playtest": "pending", "sha256": hashes}
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (destination / "SHA256SUMS").write_text("".join(f"{digest}  {name}\n" for name, digest in hashes.items()))
    latest = drafts / "latest"
    if latest.exists() and not latest.is_symlink():
        raise SystemExit("drafts/latest is not a symlink; refusing to replace user files.")
    temporary = drafts / ".latest-next"
    temporary.unlink(missing_ok=True)
    temporary.symlink_to(destination.name, target_is_directory=True)
    temporary.replace(latest)
    print(latest)


if __name__ == "__main__":
    main()
