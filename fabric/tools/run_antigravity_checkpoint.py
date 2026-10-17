#!/usr/bin/env python3
"""User-authorized, bounded Antigravity-only continuation and durable checkpoint."""
import datetime
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
JOB = ROOT / ".planning/async-jobs/antigravity-rest-port.json"
LOGS = ROOT / ".codex/antigravity-rest-port"
MODEL = "gemini-4-argon-high"
BRANCH = "port/fabric-26.3"
PROMPT = """Continue the original-mod Fabric port as the sole AntigravityCLI implementation agent.
The user explicitly forbids further Codex implementation and authorizes only this bounded
Antigravity continuation, then updating the private repository/GSD and stopping.
Read .planning/phases/05-remaining-content-and-compatibility/.continue-here.md first,
then the linked current plans and WIP handoffs. Do actual implementation, not just advice.
Preserve original source behavior and all existing changes. src/ is read-only; never touch
fabric/run saves. Pinned Minecraft26.3/Java25. Own only fabric/ and .planning/; you are not
alone historically, so never revert others' work. No Codex or other external AI fallback.
All earlier built-in workers stopped. You own the single Gradle lane. Tests stay minimal:
compile after a coherent batch; meaningful grouped runtime/client checks for delivery.
First complete MR/shared shard presentation and tracking integration acceptance. Then
continue coherent remaining roadmap modules if time permits. Incomplete WIP snapshots
must stay inactive until missing implementation is completed. Never claim full port done.
Do not alter drafts/latest unless a new artifact passes its actual delivery checks.
Work in a bounded chunk of about25 minutes, at most30 minutes. Before returning, update
.planning/STATE.md, phase .continue-here.md and exact affected plan summaries with changes,
verification and remaining gaps. Keep .planning/HANDOFF.json accurate. Do not edit the
controller or .planning/async-jobs/antigravity-rest-port.json. The controller owns commits
and pushes, so leave all changes saved and do not launch detached/background AI workers.
Use original local source for reverse engineering; no invented fallback gameplay.
If quota/auth fails or a blocker prevents meaningful progress, report it and stop.
"""


def stamp():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def command(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, text=True, capture_output=True, timeout=90, **kwargs)


def save(data):
    JOB.parent.mkdir(parents=True, exist_ok=True)
    temporary = JOB.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n")
    temporary.replace(JOB)


def checkpoint(data):
    # Never publish changes to the authoritative original source or switch remotes.
    if command(["git", "diff", "HEAD", "--", "src"]).stdout.strip():
        data["checkpoint_error"] = "Original src changed; automatic commit/push withheld."
        save(data)
        return
    save(data)
    command(["git", "add", "--", "fabric", ".planning"])
    if command(["git", "diff", "--cached", "--quiet"]).returncode:
        result = command(["git", "commit", "-m", "chore: checkpoint bounded Antigravity port work"])
        if result.returncode:
            data["checkpoint_error"] = result.stderr[-1500:]
            save(data)
            return
    result = command(["git", "push", "origin", f"HEAD:refs/heads/{BRANCH}"])
    (LOGS / "last-push.log").write_text(result.stdout + result.stderr)
    if result.returncode:
        data["push_error"] = result.stderr[-1500:]
        save(data)


def main():
    os.chdir(ROOT)
    LOGS.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    deadline = started + 5 * 3600
    data = {"version": 1, "job_id": "antigravity-rest-port", "backend": "AntigravityCLI",
            "model": MODEL, "status": "running", "pid": os.getpid(), "started_at": stamp(),
            "deadline_utc": datetime.datetime.fromtimestamp(time.time() + 5 * 3600,
                datetime.timezone.utc).isoformat(), "maximum_seconds": 18000,
            "logs": str(LOGS), "codex_fallback": False, "turn": 0,
            "verification_command": "cat .planning/async-jobs/antigravity-rest-port.json",
            "resume_command": "Read phase5 .continue-here.md; do not start another job while running."}
    save(data)
    conversation = None
    child = None
    reason = "time budget reached"
    try:
        # Check the explicitly named private origin; gh otherwise defaults to public upstream.
        repo = command(["gh", "repo", "view", "ClydeGluten/ROTP_port", "--json", "isPrivate"])
        if repo.returncode or not json.loads(repo.stdout).get("isPrivate"):
            raise RuntimeError("Private origin cannot be verified; no unattended run started.")
        for turn in range(1, 13):
            remaining = deadline - time.monotonic()
            if remaining < 120:
                break
            data.update(turn=turn, updated_at=stamp())
            save(data)
            wrap = remaining < 1800
            prompt = PROMPT + f"\nHard run deadline: {data['deadline_utc']}. Remaining seconds: {int(remaining)}."
            if wrap:
                prompt += "\nFINAL CHUNK: stop features now, save WIP, update GSD to paused and return."
            args = [str(Path.home() / ".local/bin/agy"), "--model", MODEL,
                    "--mode", "auto", "--dangerously-skip-permissions",
                    "--output-format", "json", "--print-timeout", "35m",
                    "--log-file", str(LOGS / f"turn-{turn}.debug.log"), "--print", prompt]
            if conversation:
                args += ["--conversation", conversation]
            output = LOGS / f"turn-{turn}.json"
            errors = LOGS / f"turn-{turn}.stderr.log"
            with output.open("w") as out, errors.open("w") as err:
                child = subprocess.Popen(args, cwd=ROOT, stdout=out, stderr=err, start_new_session=True)
                data["child_pid"] = child.pid
                save(data)
                try:
                    child.wait(timeout=min(2160, max(1, remaining - 90)))
                except subprocess.TimeoutExpired:
                    reason = "Antigravity turn timed out; partial changes preserved"
                finally:
                    try:
                        os.killpg(child.pid, signal.SIGTERM)
                    except ProcessLookupError:
                        pass
                    try:
                        child.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(child.pid, signal.SIGKILL)
                        child.wait()
            raw = output.read_text(errors="replace")
            error_text = errors.read_text(errors="replace")
            try:
                result = json.loads(raw)
                conversation = result.get("conversation_id", conversation)
            except ValueError:
                result = {}
            data.update(conversation_id=conversation, last_returncode=child.returncode,
                        last_output=str(output), updated_at=stamp())
            combined = raw + error_text
            if re.search(r"RESOURCE_EXHAUSTED|Individual quota reached|not logged into Antigravity|UNAUTHENTICATED", combined, re.I):
                reason = "Antigravity quota or authentication limit reached"
                break
            checkpoint(data)
            if wrap:
                reason = "bounded continuation checkpointed"
                break
            if child.returncode or not conversation or result.get("status") not in ("SUCCESS", "success"):
                reason = "Antigravity stopped or returned an incomplete result; no automatic retry"
                break
        else:
            reason = "bounded turn limit reached"
    except Exception as exc:
        reason = f"controller stopped: {type(exc).__name__}: {exc}"
    finally:
        if child is not None:
            try:
                os.killpg(child.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        data.update(status="stopped", stop_reason=reason, stopped_at=stamp(), child_pid=None)
        state = ROOT / ".planning/STATE.md"
        text = re.sub(r"(?m)^status: .*$", "status: Paused at user request", state.read_text(), count=1)
        text = text.replace("Status: Antigravity-only continuation; Codex paused", "Status: Paused at user request")
        text += f"\nAntigravity controller stopped at {data['stopped_at']}: {reason}. No Codex fallback. See async job manifest and logs; incomplete changes remain WIP.\n"
        state.write_text(text)
        handoff = ROOT / ".planning/HANDOFF.json"
        current = json.loads(handoff.read_text())
        current.update(status="paused", timestamp=stamp())
        current["async_jobs"] = [{"manifest": str(JOB.relative_to(ROOT)), "job_id": data["job_id"],
                                  "backend": "AntigravityCLI", "status": "stopped", "reason": reason}]
        current["next_action"] = "Read the last Antigravity output and phase handoff, inspect actual git status, then resume only at user request."
        measured = command(["git", "status", "--porcelain"]).stdout.splitlines()
        current.update(uncommitted_files=measured[:50], uncommitted_files_elided=max(0, len(measured)-50),
                       uncommitted_files_measured_at=stamp())
        handoff.write_text(json.dumps(current, indent=2) + "\n")
        phase = ROOT / ".planning/phases/05-remaining-content-and-compatibility/.continue-here.md"
        phase.write_text(re.sub(r"(?m)^status: .*$", "status: paused", phase.read_text(), count=1) + f"\n## Controller stop\n\n{data['stopped_at']}: {reason}. Inspect `{data.get('last_output', LOGS)}` before resuming. No background AI continuation remains.\n")
        checkpoint(data)


if __name__ == "__main__":
    main()
