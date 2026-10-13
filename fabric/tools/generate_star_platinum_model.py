#!/usr/bin/env python3
"""Deterministically converts the ORIGINAL Star Platinum assets into Minecraft 26.x Fabric client sources.

Inputs (read only):
  src/main/java/.../client/render/entity/model/stand/StarPlatinumModel.java   (geometry)
  src/main/resources/assets/jojo/animations/star_platinum.animation.json      (idle / punch / barrage / block keyframes)
Outputs:
  fabric/src/client/java/com/github/standobyte/jojo/fabric/client/model/StarPlatinumModel.java
  fabric/src/client/java/com/github/standobyte/jojo/fabric/client/model/StarPlatinumAnimData.java

Usage: python3 fabric/tools/generate_star_platinum_model.py

Ripples of the Past is GPL-3.0; outputs are derivatives of the original files and keep that license.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from star_platinum_source import ORIGINAL_ANIM, ORIGINAL_MODEL, REPO_ROOT, parse_original  # noqa: E402

OUT_DIR = REPO_ROOT / "fabric/src/client/java/com/github/standobyte/jojo/fabric/client/model"
TEMPLATE = Path(__file__).resolve().parent / "templates/StarPlatinumModel.java.tpl"

# (java constant, animation name in json)
CLIPS = [
    ("IDLE", "idle"),
    ("LIGHT_PUNCH", "attack1"),
    ("HEAVY_PUNCH", "heavyPunch"),
    ("BARRAGE", "barrage"),
    ("GUARD", "block"),
    ("UPPERCUT", "uppercut"),
    ("STAR_FINGER", "starFinger"),
    ("INHALE", "inhale"),
    ("TIME_STOP", "timeStop"),
]

# The model binds these (and only these) MoLang queries itself: see the template's setupAnim().
LOOK_QUERIES = {"query.head_x_rotation": "pitch", "query.head_y_rotation": "yaw"}
# The head bone is bound by the template for every clip, not through a LookBinding.
MODEL_BOUND_BONES = {"head"}


def hair_sway_names():
    """Parse Collections.addAll(hairToAnimateManually, ...) from the original."""
    text = ORIGINAL_MODEL.read_text(encoding="utf-8")
    m = re.search(r"Collections\.addAll\(hairToAnimateManually,(.*?)\);", text, re.S)
    return [n.strip() for n in m.group(1).split(",")]


def cube_expr(c):
    return ("\n                .texOffs(%d, %d).mirror(%s).addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))"
            % (c.u, c.v, "true" if c.mirror else "false", c.x, c.y, c.z, c.w, c.h, c.d, c.delta))


def gen_layer(parts):
    out = []
    for p in parts:
        if p.name == "root":
            continue
        builder = "CubeListBuilder.create()" + "".join(cube_expr(c) for c in p.cubes)
        pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (*p.pos, *p.rot)
        out.append('        PartDefinition %s = %s.addOrReplaceChild("%s", %s,\n                %s);\n'
                   % (p.name, p.parent, p.name, builder, pose))
    return "\n".join(out)


def is_hair(name):
    return re.fullmatch(r"hair\d*", name) is not None


def gen_lookups(parts, sway):
    by_name = {p.name: p for p in parts}
    lines = ["        this.bones.put(\"root\", root);"]
    for p in parts:
        if p.name == "root":
            continue
        if not is_hair(p.name):
            lines.append('        ModelPart %s = %s.getChild("%s");' % (p.name, p.parent, p.name))
            lines.append('        this.bones.put("%s", %s);' % (p.name, p.name))
    lines.append("")
    for n in sway:
        chain, cur = [], n
        while cur != "root":
            chain.append(cur)
            cur = by_name[cur].parent
        chain.reverse()
        # Start from the closest non-hair ancestor variable ("head") and walk the rest with getChild.
        non_hair = [i for i, c in enumerate(chain) if not is_hair(c)]
        base = chain[non_hair[-1]]
        rest = chain[non_hair[-1] + 1:]
        expr = base + "".join('.getChild("%s")' % r for r in rest)
        lines.append("        this.hairToAnimateManually.add(%s);" % expr)
    return "\n".join(lines)


def jf(v):
    return repr(float(v)) + "F"


def normalize_keyframes(raw):
    """Return [(time, vector, easing)] sorted by time; vector entries may still be MoLang strings."""
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
        kfs.append((float(t), list(vec), easing))
    kfs.sort(key=lambda e: e[0])
    return kfs


def parse_channel(bone, kind, raw):
    """Return (times, values, easings) or None when the channel uses MoLang strings."""
    kfs = normalize_keyframes(raw)
    if any(isinstance(x, str) for _, vec, _ in kfs for x in vec):
        return None
    return [k[0] for k in kfs], [[float(x) for x in k[1]] for k in kfs], [k[2] for k in kfs]


def parse_look_binding(raw):
    """Recognise the narrow MoLang form the model can bind itself.

    Accepted: every keyframe up to the last one that contains a query has the SAME query in the SAME slot(s) (only
    query.head_x_rotation / query.head_y_rotation, at most once each) and identical numeric values in the other
    slots; keyframes after that are all numeric (a blend-out the model does not reproduce, see activeUntil).
    Returns (constant[3], pitch_slot, yaw_slot, active_until) or None for anything else.
    """
    kfs = normalize_keyframes(raw)
    has_query = [any(isinstance(x, str) for x in vec) for _, vec, _ in kfs]
    if not any(has_query):
        return None
    last = max(i for i, h in enumerate(has_query) if h)
    if not all(has_query[:last + 1]):
        return None  # numeric keyframe before a query keyframe: would need real interpolation
    ref = kfs[0][1]
    slots = {"pitch": -1, "yaw": -1}
    for slot, x in enumerate(ref):
        if isinstance(x, str):
            kind = LOOK_QUERIES.get(x)
            if kind is None or slots[kind] != -1:
                return None
            slots[kind] = slot
    constant = [0.0 if isinstance(x, str) else float(x) for x in ref]
    for _, vec, _ in kfs[:last + 1]:
        if len(vec) != 3:
            return None
        for slot, x in enumerate(vec):
            if isinstance(x, str) != isinstance(ref[slot], str) or x != ref[slot]:
                return None
    return constant, slots["pitch"], slots["yaw"], kfs[last][0]


def timeline_instructions(a):
    """Yield (time, instruction string) over the clip's timeline; a time may map to a string or a list."""
    for t, code in (a.get("timeline") or {}).items():
        for instr in (code if isinstance(code, list) else [code]):
            if isinstance(instr, str):
                yield float(t), instr


def gen_anim_data():
    anims = json.loads(ORIGINAL_ANIM.read_text(encoding="utf-8"))["animations"]
    out = []
    skipped = []
    look_bound = []
    for const, name in CLIPS:
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
                last_key = max(last_key, normalize_keyframes(raw)[-1][0])
                parsed = parse_channel(bone, kind, raw)
                if parsed is None:
                    if bone in MODEL_BOUND_BONES:
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
        # Clips without animation_length (the static "block" pose) are as long as their last keyframe (0).
        length = float(a["animation_length"]) if "animation_length" in a else last_key
        out.append('    /** From animation "%s" of star_platinum.animation.json. */\n'
                   '    public static final Clip %s = new Clip("%s", %s, %s,\n        new float[] {%s}, %s,\n'
                   '        new LookBinding[] {%s},\n        new Channel[] {\n%s\n        });\n'
                   % (name, const, name, jf(length), "true" if loop else "false",
                      ", ".join(jf(p) for p in phase_starts), jf(loop_back),
                      ("\n" + ",\n".join(looks) + "\n        ") if looks else "",
                      ",\n".join(chans)))
    return out, skipped, look_bound


ANIM_HEADER = """package com.github.standobyte.jojo.fabric.client.model;

import com.github.standobyte.jojo.fabric.client.model.StarPlatinumAnimClip.Channel;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumAnimClip.Clip;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumAnimClip.LookBinding;

/*
 * GENERATED by fabric/tools/generate_star_platinum_model.py from
 * assets/jojo/animations/star_platinum.animation.json -- do not edit by hand.
 * Ripples of the Past (c) StandObyte and contributors, GPL-3.0.
 *
 * MoLang: only query.head_x_rotation / query.head_y_rotation occur. The "head" bone channels (omitted here: %s)
 * are bound by StarPlatinumModel for every clip; other bones keyed by these queries become LookBinding entries
 * (bound at runtime: %s). No other MoLang is evaluated.
 */
public final class StarPlatinumAnimData {
    private StarPlatinumAnimData() {}

"""


def render_outputs():
    """Return ({file name: generated source}, summary line) without touching the disk."""
    parts, (tw, th) = parse_original()
    sway = hair_sway_names()
    tpl = TEMPLATE.read_text(encoding="utf-8")
    model = (tpl.replace("@@LAYER@@", gen_layer(parts))
                .replace("@@LOOKUPS@@", gen_lookups(parts, sway))
                .replace("@@TEX_W@@", str(tw)).replace("@@TEX_H@@", str(th)))
    anim, skipped, look_bound = gen_anim_data()
    anim_src = (ANIM_HEADER % (", ".join(skipped), ", ".join(look_bound))) + "\n".join(anim) + "}\n"
    summary = ("generated %d parts, %d cubes, texture %dx%d, %d hair-sway parts, %d clips"
               % (len(parts), sum(len(p.cubes) for p in parts), tw, th, len(sway), len(CLIPS)))
    return {"StarPlatinumModel.java": model, "StarPlatinumAnimData.java": anim_src}, summary


def main():
    outputs, summary = render_outputs()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, text in outputs.items():
        (OUT_DIR / name).write_text(text, encoding="utf-8", newline="\n")
    print(summary)


if __name__ == "__main__":
    main()
