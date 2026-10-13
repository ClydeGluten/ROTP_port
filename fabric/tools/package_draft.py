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
    (destination / "model-verification.txt").write_text(model_check.stdout + model_check.stderr)
    (destination / "START-HERE.txt").write_text(f"""Ripples of the Past — Fabric draft {version}

Fastest local launch:
  {ROOT / 'play-draft.sh'}

Or use your Minecraft launcher:
  Minecraft {properties['minecraft_version']}, Java 25, Fabric Loader {properties['loader_version']} or later.
  Copy BOTH JARs from mods/ into that instance's mods/ directory.
  Remove any older Ripples of the Past JAR from that instance first.
  Create a fresh test world. Old Forge saves are not migrated.

New in Draft 7: camera comfort
  First-person: your own Stand and Star Finger fade so attacks do not block your view.
  Rear third-person: camera moves to the opposite shoulder and clips against walls.
  Camera aim converges on your player aim ray; attacks retain their normal origin and reach.
  Manual control hides the Stand body while viewing through its eyes.
  Dismissal and perspective changes restore the normal camera automatically.
  Test near a wall, while guarding/barraging, and while holding/releasing H.
  Random Stand allocation generations are on the roadmap; they are not implemented yet.

Draft 6: Stand Resolve and Time Stop training
  K opens Stand progression. Scroll or use Up/Down/Home/End; Esc closes it.
  Land Stand hits on hostile mobs/players to gain Resolve. Low health and sustained combat boost gains.
  Survival unlocks: Uppercut at level 1, Inhale 2, Star Finger 3, Time Stop 4.
  Existing Fabric Stands keep ownership/development but now follow these Survival gates.
  Time Stop starts at 0.25 seconds. Actual use trains duration up to 5 seconds.
  Resolve bursts give damage resistance, 4x training, and shorter ability cooldowns.
  Levels/training survive death and reload; death resets the current combat meter.
  Creative retains all moves and full Time Stop duration.
  Operators: /rotp progression @s level 4 and /rotp progression @s training 95 for full testing.
  /rotp progression @s reset clears earned progress while keeping the Stand.
  Setting a level or resetting dismisses the Stand; summon again with G.

Survival Stand Arrow acquisition
  Find a Beetle Stand Arrow in an unopened desert-pyramid chest (2% bonus chance per chest).
  Gather 30 XP levels for the first awakening; each prior arrow awakening adds 5 levels.
  Use/right-click the arrow to pierce yourself. The virus takes 1 XP level every half-second.
  Without enough XP it can kill you; milk cannot cure it. Survive to receive Star Platinum.
  Paid acquisition takes about 15 seconds at the initial cost; surviving the full virus also awakens it.
  Infection resumes after save/reload or reconnect. Successful acquisition heals you afterward.
  Only Star Platinum is available. Shooting arrows and meteorite crafting remain pending.

Quick controlled test (commands enabled, preferably a new player/world):
  /rotp clear @s
  /give @s jojo:stand_arrow_beetle
  /experience set @s 30 levels
  /gamemode survival
  Then hold the arrow and use/right-click it. G summons after awakening.
  Both arrow variants also appear in Creative's Combat tab; Creative use awakens instantly.

Existing combat shortcut:
  /rotp grant @s star_platinum
  G: summon / dismiss. R: light punch. V: heavy punch.
  Hold Z: barrage (up to five seconds at base stats). Release and repress after it ends.
  Hold X: frontal guard. Guard consumes stamina when hit and breaks when exhausted.
  B: uppercut. C: Star Finger. Hold N: inhale. Hold Y: progressive zoom.
  Hold H: manual control; WASD moves the Stand, Space/Shift moves vertically. Release to return.
  J: charge time stop (two seconds); press again to resume. Maximum stop: five seconds.
  Controls can be rebound. Survival stamina starts empty and regenerates.
  Creative has unlimited stamina; its HUD says Unlimited instead of showing a finite bar.
  To check drain and refill after granting: /gamemode survival, then hold Z and release.
  Finisher charge builds through punches and landed barrage hits. At 50% and Resolve level 1, V becomes an uppercut.
  Barrage allows 15% walking speed, guard 30%, heavy/special actions 50%; recovery restores speed.

Playtest: summon, aim at a nearby mob, try all attacks, hold/release guard,
open a menu while holding an action, save/reload, and check Controls rebinding.

This is an incomplete development port. Barrage clashes, time-stop blink,
other powers (including Hamon) and the original customizable ability-selection UI are not implemented.
See fabric/migration/STATUS.md in the source project for exact parity limits.
Original assets and GPL-3.0 license retained; full source remains in the project.
""")
    screenshots = FABRIC / "build/run/clientGameTest/screenshots"
    playtest_screenshots = sorted({*screenshots.glob("*progression*.png"), *screenshots.glob("*camera*.png")})
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
