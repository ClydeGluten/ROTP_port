#!/usr/bin/env python3
"""Deterministically converts the ORIGINAL The World assets into Minecraft 26.x Fabric client sources.

Inputs (read only):
  src/main/java/.../client/render/entity/model/stand/TheWorldModel.java   (geometry)
  src/main/resources/assets/jojo/animations/the_world.animation.json      (idle / punch / kick / barrage / block / timeStop keyframes)
Outputs:
  fabric/src/client/java/com/github/standobyte/jojo/fabric/client/model/TheWorldModel.java
  fabric/src/client/java/com/github/standobyte/jojo/fabric/client/model/TheWorldAnimData.java

Usage: python3 fabric/tools/generate_the_world_model.py

Ripples of the Past is GPL-3.0; outputs are derivatives of the original files and keep that license.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from star_platinum_source import REPO_ROOT, parse_original  # noqa: E402
# Reuse generator helpers from the SP generator without modifying it.
import generate_star_platinum_model as _sp  # noqa: E402
from generate_star_platinum_model import (  # noqa: E402
    gen_layer, gen_lookups, normalize_keyframes,
    parse_channel, parse_look_binding, timeline_instructions,
    jf, LOOK_QUERIES, MODEL_BOUND_BONES,
)

_STAND = "src/main/java/com/github/standobyte/jojo/client/render/entity/model/stand/"
TW_ORIGINAL_MODEL = REPO_ROOT / (_STAND + "TheWorldModel.java")
TW_ORIGINAL_ANIM = REPO_ROOT / "src/main/resources/assets/jojo/animations/the_world.animation.json"

OUT_DIR = REPO_ROOT / "fabric/src/client/java/com/github/standobyte/jojo/fabric/client/model"
TEMPLATE = Path(__file__).resolve().parent / "templates/TheWorldModel.java.tpl"

# (java constant, animation name in json)
TW_CLIPS = [
    ("IDLE",         "idle"),
    ("LIGHT_PUNCH",  "attack1"),
    ("HEAVY_PUNCH",  "heavyPunch"),
    ("KICK",         "kick"),
    ("GUARD",        "block"),
    ("BARRAGE",      "barrage"),
    ("TIME_STOP",    "timeStop"),
    ("TIME_BREAKER", "timeBreaker"),
]

# head bone channels are always model-bound (MoLang query.head_x/y_rotation or arithmetic on them).
_MODEL_BOUND_BONES = MODEL_BOUND_BONES  # == {"head"}

# Simple arithmetic MoLang: "number+number" or "number-number" as produced by Blockbench spin tracks.
_ARITH_RE = re.compile(r'^(-?[\d.]+)\s*([+-])\s*([\d.]+)$')


def _resolve_arith(x):
    """Resolve 'A+B' / 'A-B' arithmetic strings to float, or raise ValueError for anything else."""
    if isinstance(x, (int, float)):
        return float(x)
    m = _ARITH_RE.match(str(x).strip())
    if m:
        a, op, b = float(m.group(1)), m.group(2), float(m.group(3))
        return (a + b) if op == '+' else (a - b)
    raise ValueError("not a simple arithmetic expression: %r" % x)


def _normalize_keyframes_tw(raw):
    """Like normalize_keyframes but resolves simple arithmetic expressions in vectors.

    Vectors with resolvable arithmetic (e.g. '14.7669+360') are kept as numeric channels.
    Vectors with genuine MoLang queries (query.*) are preserved as strings so that
    parse_channel / parse_look_binding can handle them normally.
    """
    kfs = []
    if isinstance(raw, dict) and ("vector" in raw or "post" in raw):
        raw = {"0.0": raw}
    elif isinstance(raw, list):
        raw = {"0.0": raw}
    for t, k in raw.items():
        if isinstance(k, list):
            vec, easing = k, "linear"
        else:
            vec = k.get("vector") or k["post"]["vector"]
            easing = k.get("easing") or k.get("lerp_mode") or "linear"

        resolved = []
        for x in vec:
            if isinstance(x, str) and x.strip().startswith("query."):
                # Keep as string; will be handled by parse_look_binding / MODEL_BOUND_BONES skip.
                resolved.append(x)
            else:
                try:
                    resolved.append(_resolve_arith(x))
                except ValueError:
                    # Unknown MoLang expression that is neither a query nor simple arithmetic.
                    resolved.append(x)  # keep as string → will trigger parse_channel → None
        kfs.append((float(t), resolved, easing))
    kfs.sort(key=lambda e: e[0])
    return kfs


def _parse_channel_tw(bone, kind, raw):
    """parse_channel variant that resolves arithmetic MoLang before checking for strings."""
    kfs = _normalize_keyframes_tw(raw)
    if any(isinstance(x, str) for _, vec, _ in kfs for x in vec):
        return None
    return ([k[0] for k in kfs],
            [[float(x) for x in k[1]] for k in kfs],
            [k[2] for k in kfs])


def gen_anim_data_tw():
    """Generate TheWorldAnimData clip constants.

    Returns (clip_source_list, skipped_channels, look_bound_channels).
    """
    anims = json.loads(TW_ORIGINAL_ANIM.read_text(encoding="utf-8"))["animations"]
    out = []
    skipped = []
    look_bound = []
    for const, name in TW_CLIPS:
        a = anims[name]
        loop = a.get("loop") is True
        phases = {}
        loop_back = -1.0
        for t, code in timeline_instructions(a):
            m = re.search(r"^phase\s*=\s*(WINDUP|PERFORM|RECOVERY);?$", code.strip())
            if m:
                phases[m.group(1)] = t
            m = re.search(r"^phase\.loopBack\s*=\s*(\d+(?:\.\d+)?);?$", code.strip())
            if m:
                loop_back = float(m.group(1))
        if loop_back >= 0 and not ("PERFORM" in phases and "RECOVERY" in phases):
            raise ValueError("%s: phase.loopBack needs both PERFORM and RECOVERY timeline entries" % name)
        phase_starts = [phases.get(k, 0.0) for k in ("WINDUP", "PERFORM", "RECOVERY")] if phases else [0.0, 0.0, 0.0]
        chans = []
        looks = []
        last_key = 0.0
        for bone in sorted(a["bones"]):
            for kind, key in (("rotation", False), ("position", True)):
                if kind not in a["bones"][bone]:
                    continue
                raw = a["bones"][bone][kind]
                last_key = max(last_key, _normalize_keyframes_tw(raw)[-1][0])
                parsed = _parse_channel_tw(bone, kind, raw)
                if parsed is None:
                    if bone in _MODEL_BOUND_BONES:
                        skipped.append("%s/%s.%s" % (name, bone, kind))
                        continue
                    look = parse_look_binding(raw) if not key else None
                    if look is None:
                        raise ValueError("%s/%s.%s uses MoLang the model cannot bind" % (name, bone, kind))
                    constant, pitch, yaw, until = look
                    looks.append('            new LookBinding("%s", new float[] {%s}, %d, %d, %s)'
                                 % (bone, ", ".join(jf(x) for x in constant), pitch, yaw, jf(until)))
                    look_bound.append("%s/%s" % (name, bone))
                    continue
                times, vals, eas = parsed
                chans.append(
                    '            new Channel("%s", %s,\n                new float[] {%s},\n                new float[][] {%s},\n'
                    '                new String[] {%s})'
                    % (bone, "true" if key else "false",
                       ", ".join(jf(t) for t in times),
                       ", ".join("{" + ", ".join(jf(x) for x in v) + "}" for v in vals),
                       ", ".join('"%s"' % e for e in eas)))
        # Clips without animation_length (the static "block" pose) are as long as their last keyframe.
        length = float(a["animation_length"]) if "animation_length" in a else last_key
        out.append('    /** From animation "%s" of the_world.animation.json. */\n'
                   '    public static final Clip %s = new Clip("%s", %s, %s,\n        new float[] {%s}, %s,\n'
                   '        new LookBinding[] {%s},\n        new Channel[] {\n%s\n        });\n'
                   % (name, const, name, jf(length), "true" if loop else "false",
                      ", ".join(jf(p) for p in phase_starts), jf(loop_back),
                      ("\n" + ",\n".join(looks) + "\n        ") if looks else "",
                      ",\n".join(chans)))
    return out, skipped, look_bound


def gen_head_data():
    """Preserve keyframed head look expressions as constant + pitch/yaw weight channels."""
    anims = json.loads(TW_ORIGINAL_ANIM.read_text())["animations"]
    entries = []
    for const, name in TW_CLIPS:
        raw = anims[name]["bones"].get("head", {}).get("rotation")
        if raw is None:
            continue
        frames = _normalize_keyframes_tw(raw)
        components = [[], [], []]
        for t, vector, easing in frames:
            values = [[0.0] * 3 for _ in range(3)]
            for axis, expr in enumerate(vector):
                if isinstance(expr, (float, int)):
                    values[0][axis] = float(expr)
                else:
                    match = re.fullmatch(r"query.head_([xy])_rotation\s*([+-]\s*[\d.]+)?", expr)
                    if not match:
                        raise ValueError("Unbound head expression " + expr)
                    values[0][axis] = float((match[2] or "0").replace(" ", ""))
                    values[1 if match[1] == "x" else 2][axis] = 1.0
            for i in range(3):
                components[i].append(values[i])
        channels = []
        for values in components:
            channels.append('new Channel("head", false, new float[] {%s}, new float[][] {%s}, new String[] {%s})' % (
                ", ".join(jf(t) for t, _, _ in frames),
                ", ".join("{" + ", ".join(jf(v) for v in vector) + "}" for vector in values),
                ", ".join('"%s"' % e for _, _, e in frames)))
        entries.append('            java.util.Map.entry("%s", new Channel[] {%s})' % (name, ",\n                ".join(channels)))
    return "    public static final java.util.Map<String, Channel[]> HEAD = java.util.Map.ofEntries(\n" + ",\n".join(entries) + ");\n"


ANIM_HEADER = """package com.github.standobyte.jojo.fabric.client.model;

import com.github.standobyte.jojo.fabric.client.model.StarPlatinumAnimClip.Channel;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumAnimClip.Clip;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumAnimClip.LookBinding;

/*
 * GENERATED by fabric/tools/generate_the_world_model.py from
 * assets/jojo/animations/the_world.animation.json -- do not edit by hand.
 * Ripples of the Past (c) StandObyte and contributors, GPL-3.0.
 *
 * MoLang: only query.head_x_rotation / query.head_y_rotation occur (plus arithmetic wrappers resolved
 * to numeric during generation). The "head" bone channels (omitted here: %s) are bound by TheWorldModel
 * for every clip; other bones keyed by these queries become LookBinding entries (bound at runtime: %s).
 * No other MoLang is evaluated.
 */
public final class TheWorldAnimData {
    private TheWorldAnimData() {}

"""


def render_outputs():
    """Return ({file name: generated source}, summary line) without touching the disk."""
    parts, (tw, th) = parse_original(TW_ORIGINAL_MODEL)
    tpl = TEMPLATE.read_text(encoding="utf-8")
    model = (tpl.replace("@@LAYER@@", gen_layer(parts))
                .replace("@@LOOKUPS@@", gen_lookups(parts, []))  # no hair sway for TW
                .replace("@@TEX_W@@", str(tw)).replace("@@TEX_H@@", str(th)))
    anim, skipped, look_bound = gen_anim_data_tw()
    anim_src = (ANIM_HEADER % (", ".join(skipped), ", ".join(look_bound))) + "\n".join(anim) + gen_head_data() + "}\n"
    summary = ("generated %d parts, %d cubes, texture %dx%d, %d clips"
               % (len(parts), sum(len(p.cubes) for p in parts), tw, th, len(TW_CLIPS)))
    return {"TheWorldModel.java": model, "TheWorldAnimData.java": anim_src}, summary


def main():
    outputs, summary = render_outputs()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, text in outputs.items():
        (OUT_DIR / name).write_text(text, encoding="utf-8", newline="\n")
    print(summary)


if __name__ == "__main__":
    main()
