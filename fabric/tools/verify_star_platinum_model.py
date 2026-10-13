#!/usr/bin/env python3
"""Structural parity check: original 1.16 StarPlatinumModel.java  vs  generated Fabric StarPlatinumModel.java.

The original is parsed by star_platinum_source.py (ModelRenderer calls); the generated file is parsed here by an
independent regex over the 26.x API calls (addOrReplaceChild / CubeListBuilder / PartPose / CubeDeformation).
Compares part names, hierarchy, pose offsets/rotations, cube counts and every cube's UV, mirror, size, origin and
dilation numerically, plus texture size, hair-sway list and animation bone/keyframe data against the json.

A second suite (StarPlatinumCombatPoses) checks the barrage / block clips, the MoLang look bindings, the render-state
phase contract, the mode dispatch in the model, and that the generator reproduces the checked-in Java byte for byte.

Run: python3 fabric/tools/verify_star_platinum_model.py   (exit code 0 == parity)
"""
import json
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from star_platinum_source import ORIGINAL_ANIM, ORIGINAL_MODEL, REPO_ROOT, parse_original  # noqa: E402
import generate_star_platinum_model as generator  # noqa: E402

GEN_DIR = REPO_ROOT / "fabric/src/client/java/com/github/standobyte/jojo/fabric/client/model"
F = r"(-?\d+(?:\.\d+)?)F"
SEP = r"\s*,\s*"
QUERIES = {"query.head_x_rotation", "query.head_y_rotation"}
CLIP_CONSTS = (
    ("IDLE", "idle"),
    ("LIGHT_PUNCH", "attack1"),
    ("HEAVY_PUNCH", "heavyPunch"),
    ("BARRAGE", "barrage"),
    ("GUARD", "block"),
    ("UPPERCUT", "uppercut"),
    ("STAR_FINGER", "starFinger"),
    ("INHALE", "inhale"),
    ("TIME_STOP", "timeStop"),
)
PHASE_VALUES = {
    "PHASE_IDLE": 0,
    "PHASE_WINDUP": 1,
    "PHASE_PERFORM": 2,
    "PHASE_RECOVERY": 3,
    "PHASE_BARRAGE": 4,
    "PHASE_GUARD": 5,
    "PHASE_GUARD_BROKEN": 6,
    "PHASE_STAR_FINGER": 7,
    "PHASE_INHALE": 8,
    "PHASE_ZOOM": 9,
    "PHASE_TIME_STOP": 10,
    "PHASE_TIME_STOP_CHARGE": 10,
}


def parse_generated(text):
    parts = {}
    order = []
    for m in re.finditer(
            r'PartDefinition (\w+) = (\w+)\.addOrReplaceChild\("(\w+)",\s*CubeListBuilder\.create\(\)(.*?),\s*'
            r'PartPose\.offsetAndRotation\(' + SEP.join([F] * 6) + r'\)\);', text, re.S):
        var, parent, name, cubes = m.group(1), m.group(2), m.group(3), m.group(4)
        assert var == name, "variable/part name mismatch %s/%s" % (var, name)
        assert name not in parts, "duplicate part " + name
        cube_list = []
        for c in re.finditer(r'\.texOffs\((\d+),\s*(\d+)\)\.mirror\((true|false)\)\.addBox\(' + SEP.join([F] * 6)
                             + r',\s*new CubeDeformation\(' + F + r'\)\)', cubes):
            g = c.groups()
            cube_list.append((int(g[0]), int(g[1]), g[2] == "true", *[float(x) for x in g[3:10]]))
        assert len(re.findall(r"\.addBox\(", cubes)) == len(cube_list), "unparsed cube in " + name
        parts[name] = {"parent": parent, "pos": tuple(float(m.group(i)) for i in (5, 6, 7)),
                       "rot": tuple(float(m.group(i)) for i in (8, 9, 10)), "cubes": cube_list}
        order.append(name)
    return parts, order


class StarPlatinumParity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orig_list, cls.orig_tex = parse_original()
        cls.orig = {p.name: p for p in cls.orig_list}
        cls.gen_text = (GEN_DIR / "StarPlatinumModel.java").read_text(encoding="utf-8")
        cls.gen, cls.gen_order = parse_generated(cls.gen_text)

    def test_original_sanity(self):
        self.assertEqual(self.orig_tex, (128, 128))
        self.assertEqual(len(self.orig_list), 74)
        self.assertEqual(sum(len(p.cubes) for p in self.orig_list), 107)

    def test_part_names(self):
        self.assertEqual(set(self.gen) | {"root"}, set(self.orig))
        self.assertEqual(len(self.gen), len(self.orig) - 1)

    def test_hierarchy(self):
        for name, g in self.gen.items():
            self.assertEqual(g["parent"], self.orig[name].parent, name)
        pos = {n: i for i, n in enumerate(self.gen_order)}
        for name, g in self.gen.items():
            if g["parent"] != "root":
                self.assertLess(pos[g["parent"]], pos[name], name)

    def test_poses(self):
        for name, g in self.gen.items():
            o = self.orig[name]
            self.assertEqual(g["pos"], tuple(float(v[:-1]) for v in o.pos), name + " pos")
            self.assertEqual(g["rot"], tuple(float(v[:-1]) for v in o.rot), name + " rot")
        r = self.orig["root"]
        self.assertTrue(all(float(v[:-1]) == 0 for v in r.pos + r.rot) and not r.cubes,
                        "root must be identity+empty to map onto the mesh root")

    def test_cube_counts_and_values(self):
        total = 0
        for name, g in self.gen.items():
            o = self.orig[name]
            self.assertEqual(len(g["cubes"]), len(o.cubes), name + " cube count")
            for i, (gc, oc) in enumerate(zip(g["cubes"], o.cubes)):
                exp = (oc.u, oc.v, oc.mirror, *[float(v[:-1]) for v in (oc.x, oc.y, oc.z, oc.w, oc.h, oc.d, oc.delta)])
                self.assertEqual(gc, exp, "%s cube %d" % (name, i))
                total += 1
        self.assertEqual(total, 107)

    def test_texture_size(self):
        m = re.search(r"LayerDefinition\.create\(mesh,\s*(\d+),\s*(\d+)\)", self.gen_text)
        self.assertEqual((int(m.group(1)), int(m.group(2))), self.orig_tex)

    def test_class_shape(self):
        t = self.gen_text
        self.assertIn("public class StarPlatinumModel extends EntityModel<StarPlatinumRenderState>", t)
        self.assertIn("public StarPlatinumModel(ModelPart root)", t)
        self.assertIn("public static LayerDefinition createBodyLayer()", t)
        self.assertIn("GPL-3.0", t)
        self.assertIn("Blockbench 4.8.3", t)

    def test_hair_sway_list(self):
        src = ORIGINAL_MODEL.read_text(encoding="utf-8")
        names = [n.strip() for n in re.search(r"Collections\.addAll\(hairToAnimateManually,(.*?)\);", src, re.S).group(1).split(",")]
        chains = re.findall(r"this\.hairToAnimateManually\.add\((.*?)\);", self.gen_text)
        self.assertEqual(len(chains), len(names))
        for name, chain in zip(names, chains):
            path = re.findall(r'getChild\("(\w+)"\)', chain)
            self.assertEqual(path[-1], name)
            cur = name
            for step in reversed(path):
                self.assertEqual(step, cur)
                cur = self.orig[cur].parent
            self.assertEqual(chain.split(".")[0], cur)

    def test_animation_data(self):
        anims = json.loads(ORIGINAL_ANIM.read_text(encoding="utf-8"))["animations"]
        data = (GEN_DIR / "StarPlatinumAnimData.java").read_text(encoding="utf-8")
        for const, name in CLIP_CONSTS:
            block = re.search(r"Clip " + const + r" = new Clip\(.*?\n        \}\);", data, re.S).group(0)
            chans = re.findall(r'new Channel\("(\w+)", (true|false),\n\s*new float\[\] \{(.*?)\},\n\s*new float\[\]\[\] \{(.*?)\},\n\s*new String', block)
            expected = {}
            for bone, v in anims[name]["bones"].items():
                self.assertIn(bone, self.orig, "animation bone %s missing from model" % bone)
                for kind in ("rotation", "position"):
                    if kind not in v:
                        continue
                    raw = v[kind]
                    frames = {"0.0": raw} if ("vector" in raw or isinstance(raw, list)) else raw
                    if any(isinstance(x, str) for fr in frames.values() for x in (fr["vector"] if isinstance(fr, dict) and "vector" in fr else (fr["post"]["vector"] if isinstance(fr, dict) and "post" in fr else fr))):
                        continue
                    expected[(bone, kind == "position")] = sorted(
                        (float(t), [float(x) for x in (fr["vector"] if isinstance(fr, dict) and "vector" in fr else (fr["post"]["vector"] if isinstance(fr, dict) and "post" in fr else fr))]) for t, fr in frames.items())
            got = {}
            for bone, pos, times, vals in chans:
                ts = [float(x[:-1]) for x in times.split(", ")]
                vs = [[float(x) for x in re.findall(F, vec)] for vec in re.findall(r"\{(.*?)\}", vals)]
                got[(bone, pos == "true")] = list(zip(ts, vs))
            self.assertEqual(set(got), set(expected), name)
            for k in expected:
                self.assertEqual(len(got[k]), len(expected[k]), (name, k))
                for (gt, gv), (et, ev) in zip(got[k], expected[k]):
                    self.assertAlmostEqual(gt, et, places=4)
                    for a, b in zip(gv, ev):
                        self.assertAlmostEqual(a, b, places=4)


def json_keyframes(raw):
    frames = {"0.0": raw} if "vector" in raw else raw
    return sorted((float(t), fr["vector"]) for t, fr in frames.items())


def parse_clips(data):
    """Parse every generated Clip: metadata, look bindings and channels (independent of the generator code)."""
    clips = {}
    for m in re.finditer(r'Clip (\w+) = new Clip\("(\w+)", ' + F + r', (true|false),\n\s*new float\[\] \{(.*?)\}, ' + F
                         + r',\n\s*new LookBinding\[\] \{(.*?)\},\n\s*new Channel\[\] \{(.*?)\n        \}\);', data, re.S):
        const, name, length, loop, starts, loop_back, looks, chans = m.groups()
        looks_parsed = {}
        for lm in re.finditer(r'new LookBinding\("(\w+)", new float\[\] \{(.*?)\}, (-?\d+), (-?\d+), ' + F + r'\)', looks):
            looks_parsed[lm.group(1)] = ([float(x) for x in re.findall(F, lm.group(2))], int(lm.group(3)),
                                         int(lm.group(4)), float(lm.group(5)))
        channels = {}
        for cm in re.finditer(r'new Channel\("(\w+)", (true|false),\n\s*new float\[\] \{(.*?)\},\n\s*new float\[\]\[\] \{(.*?)\},\n\s*new String',
                              chans):
            ts = [float(x[:-1]) for x in cm.group(3).split(", ")]
            vs = [[float(x) for x in re.findall(F, vec)] for vec in re.findall(r"\{(.*?)\}", cm.group(4))]
            channels[(cm.group(1), cm.group(2) == "true")] = list(zip(ts, vs))
        clips[const] = {"name": name, "length": float(length), "loop": loop == "true",
                        "starts": [float(x[:-1]) for x in starts.split(", ")], "loop_back": float(loop_back),
                        "looks": looks_parsed, "channels": channels}
    return clips


def method_body(text, signature):
    start = text.index(signature)
    return text[start:text.index("\n    }\n", start)]


class StarPlatinumCombatPoses(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.anims = json.loads(ORIGINAL_ANIM.read_text(encoding="utf-8"))["animations"]
        cls.data = (GEN_DIR / "StarPlatinumAnimData.java").read_text(encoding="utf-8")
        cls.model = (GEN_DIR / "StarPlatinumModel.java").read_text(encoding="utf-8")
        cls.state = (GEN_DIR / "StarPlatinumRenderState.java").read_text(encoding="utf-8")
        cls.clips = parse_clips(cls.data)
        cls.orig_list, _ = parse_original()
        cls.orig = {p.name for p in cls.orig_list}

    def test_all_clips_present(self):
        self.assertEqual(set(self.clips), {c for c, _ in CLIP_CONSTS})
        for const, name in CLIP_CONSTS:
            self.assertEqual(self.clips[const]["name"], name)

    def test_generated_sources_reproduce(self):
        outputs, _ = generator.render_outputs()
        for name, text in outputs.items():
            self.assertEqual((GEN_DIR / name).read_text(encoding="utf-8"), text,
                             name + " is stale: run python3 fabric/tools/generate_star_platinum_model.py")

    def test_clip_metadata_matches_json(self):
        for const, name in CLIP_CONSTS:
            a, c = self.anims[name], self.clips[const]
            last_key = max(json_keyframes(r)[-1][0] for v in a["bones"].values() for r in v.values())
            self.assertAlmostEqual(c["length"], float(a.get("animation_length", last_key)), places=4, msg=name)
            self.assertEqual(c["loop"], a.get("loop") is True, name)
            phases, loop_back = {}, -1.0
            for t, code in (a.get("timeline") or {}).items():
                for instr in (code if isinstance(code, list) else [code]):
                    pm = re.fullmatch(r"phase = (WINDUP|PERFORM|RECOVERY);", instr)
                    if pm:
                        phases[pm.group(1)] = float(t)
                    lm = re.fullmatch(r"phase\.loopBack = ([\d.]+);", instr)
                    if lm:
                        loop_back = float(lm.group(1))
            expected = [phases.get(k, 0.0) for k in ("WINDUP", "PERFORM", "RECOVERY")]
            for got, exp in zip(c["starts"], expected):
                self.assertAlmostEqual(got, exp, places=4, msg=name)
            self.assertAlmostEqual(c["loop_back"], loop_back, places=4, msg=name)

    def test_only_known_molang_and_every_channel_is_accounted_for(self):
        for name, a in self.anims.items():
            for bone, v in a["bones"].items():
                for kind, raw in v.items():
                    for _, vec in json_keyframes(raw):
                        for x in vec:
                            if isinstance(x, str):
                                self.assertIn(x, QUERIES, "%s/%s.%s uses unsupported MoLang %r" % (name, bone, kind, x))
        for const, name in CLIP_CONSTS:
            c = self.clips[const]
            molang_bones = {b for b, v in self.anims[name]["bones"].items()
                            for raw in v.values() if any(isinstance(x, str) for _, vec in json_keyframes(raw) for x in vec)}
            # head is bound by the model for every clip; everything else MoLang must be a LookBinding, not a keyframe channel
            self.assertEqual(set(c["looks"]), molang_bones - {"head"}, name)
            for bone in molang_bones:
                self.assertNotIn((bone, False), c["channels"], "%s/%s MoLang channel leaked into keyframes" % (name, bone))
        self.assertIn('bones.get("head")', self.model)
        self.assertRegex(self.model, r"head\.xRot \+= state\.xRot")
        self.assertRegex(self.model, r"head\.yRot \+= state\.yRot")

    def test_barrage_look_bindings(self):
        looks = self.clips["BARRAGE"]["looks"]
        self.assertEqual(set(looks), {"leftArmXRot", "rightArmXRot"})
        for bone, (constant, pitch, yaw, until) in looks.items():
            kfs = json_keyframes(self.anims["barrage"]["bones"][bone]["rotation"])
            q = [(t, vec) for t, vec in kfs if any(isinstance(x, str) for x in vec)]
            self.assertEqual(pitch, q[0][1].index("query.head_x_rotation"), bone)
            self.assertEqual(yaw, -1, bone)  # arms follow the look PITCH only
            self.assertAlmostEqual(until, q[-1][0], places=4, msg=bone)
            self.assertEqual(constant, [0.0, 0.0, 0.0], bone)
            self.assertIn(bone, self.orig)
            self.assertIn('this.bones.put("%s"' % bone, self.model)
        # the head channel (pitch+yaw) is NOT a look binding, it is handled by the model
        self.assertNotIn("head", looks)

    def test_barrage_loops_seamlessly_with_real_motion(self):
        c = self.clips["BARRAGE"]
        self.assertFalse(c["loop"], "the clip itself is hold_on_last_frame; looping is the PERFORM loopBack")
        start, perform, recovery = c["starts"]
        self.assertEqual((c["loop_back"], perform, recovery), (0.0, 0.0, 6.0))
        self.assertGreater(c["length"], recovery)
        animated = 0
        for key, kfs in c["channels"].items():
            if len(kfs) == 1:
                continue  # a single keyframe is a constant: trivially seamless
            before = [v for t, v in kfs if abs(t - perform) < 1e-4]
            after = [v for t, v in kfs if abs(t - recovery) < 1e-4]
            self.assertEqual((len(before), len(after)), (1, 1), "%s needs keyframes at loop start and end" % (key,))
            for a, b in zip(before[0], after[0]):
                self.assertAlmostEqual(a, b, places=4, msg="loop seam at %s" % (key,))
            if len({tuple(v) for t, v in kfs if t <= recovery}) > 1:
                animated += 1
        for bone in ("leftArm", "rightArm", "leftForeArm", "rightForeArm", "upperPart"):
            self.assertGreater(len({tuple(v) for t, v in c["channels"][(bone, False)]}), 1, bone + " must animate")
        self.assertGreaterEqual(animated, 5)
        # the loop uses the original's LOOP_BACK formula
        self.assertRegex(self.model, r"wrapLoopBack\(seconds\)")
        clip_src = (GEN_DIR / "StarPlatinumAnimClip.java").read_text(encoding="utf-8")
        self.assertRegex(clip_src, r"phaseStarts\[1\] \+ Math\.max\(0, seconds\) % loopLength")
        self.assertRegex(clip_src, r"loopLength = phaseStarts\[2\] - loopBackTo")

    def test_guard_is_the_static_original_block_pose(self):
        c = self.clips["GUARD"]
        self.assertEqual((c["length"], c["loop"], c["loop_back"], c["looks"]), (0.0, False, -1.0, {}))
        self.assertEqual(c["starts"], [0.0, 0.0, 0.0])
        self.assertTrue(c["channels"])
        for key, kfs in c["channels"].items():
            self.assertEqual(len(kfs), 1, "block pose must be a single static keyframe: %s" % (key,))
            self.assertIn(key[0], self.orig)
        pose = {b: kfs[0][1] for (b, pos), kfs in c["channels"].items() if not pos}
        self.assertEqual(pose["leftArm"], [-90.0, 0.0, 45.0])
        self.assertEqual(pose["rightArm"], [-90.0, 0.0, -45.0])
        self.assertEqual(pose["leftForeArm"], [0.0, 0.0, 60.0])
        self.assertEqual(pose["rightForeArm"], [0.0, 0.0, -60.0])

    def test_render_state_phase_contract(self):
        found = {n: int(v) for n, v in re.findall(r"public static final int (PHASE_\w+) = (\d+);", self.state)}
        self.assertEqual(found, PHASE_VALUES)
        abilities = {n: int(v) for n, v in re.findall(r"public static final int (ABILITY_\w+) = (\d+);", self.state)}
        self.assertEqual(abilities, {"ABILITY_NORMAL": 0, "ABILITY_UPPERCUT": 1})
        for field in ("actionPhase", "abilityKind", "heavyAttack", "actionTicks", "actionDuration"):
            self.assertIn(field + ";", self.state)
        self.assertIn("STAR_FINGER_MAX_REACH = 3.6F;", self.state)
        self.assertIn("STAR_FINGER_EXTEND_SPEED = 0.3F;", self.state)
        self.assertIn("STAR_FINGER_RETRACT_SPEED = 0.9F;", self.state)
        self.assertIn("STAR_FINGER_EXTEND_TICKS = 12;", self.state)
        self.assertIn("STAR_FINGER_HOLD_TICKS = 4;", self.state)
        self.assertIn("STAR_FINGER_RETRACT_TICKS = 4;", self.state)
        self.assertIn("STAR_FINGER_TOTAL_TICKS = 20;", self.state)
        self.assertIn("getStarFingerReach()", self.state)

    def test_model_mode_dispatch(self):
        sw = method_body(self.model, "switch (state.actionPhase)")
        self.assertRegex(sw, r"PHASE_WINDUP,\s*StarPlatinumRenderState\.PHASE_PERFORM,\s*StarPlatinumRenderState\.PHASE_RECOVERY\s*->\s*applyPunch\(state\)")
        for const, method in (("PHASE_BARRAGE", "applyBarrage"), ("PHASE_GUARD", "applyGuard"),
                              ("PHASE_GUARD_BROKEN", "applyGuardBroken"),
                              ("PHASE_STAR_FINGER", "applyStarFinger"),
                              ("PHASE_INHALE", "applyInhale"),
                              ("PHASE_ZOOM", "applyZoom"),
                              ("PHASE_TIME_STOP", "applyTimeStop")):
            self.assertRegex(sw, r"StarPlatinumRenderState\.%s\s*->\s*%s\(state\)" % (const, method))
        self.assertRegex(sw, r"default\s*->\s*\{\s*\}")
        self.assertNotIn("PHASE_IDLE", sw)
        punch = method_body(self.model, "private void applyPunch(")
        self.assertIn("state.abilityKind == StarPlatinumRenderState.ABILITY_UPPERCUT", punch)
        self.assertIn("StarPlatinumAnimData.UPPERCUT", punch)
        self.assertIn("state.heavyAttack ? StarPlatinumAnimData.HEAVY_PUNCH : StarPlatinumAnimData.LIGHT_PUNCH", punch)
        for method, clip in (("applyBarrage", "BARRAGE"), ("applyGuard", "GUARD"),
                             ("applyStarFinger", "STAR_FINGER"), ("applyInhale", "INHALE"),
                             ("applyTimeStop", "TIME_STOP")):
            body = method_body(self.model, "private void %s(" % method)
            self.assertIn("StarPlatinumAnimData." + clip, body)
            for other in ("LIGHT_PUNCH", "HEAVY_PUNCH"):
                self.assertNotIn(other, body, method + " must not use punch data")
        broken = method_body(self.model, "private void applyGuardBroken(")
        for forbidden in ("StarPlatinumAnimData", "applyClip", "heavyAttack"):
            self.assertNotIn(forbidden, broken, "guard broken must not reuse a clip / punch animation")
        self.assertIn("GUARD_BROKEN_LEAN", broken)
        self.assertIn('bones.get("upperPart")', broken)
        zoom = method_body(self.model, "private void applyZoom(")
        for forbidden in ("StarPlatinumAnimData", "applyClip"):
            self.assertNotIn(forbidden, zoom, "zoom has no json clip, stays as sensible idle gaze")
        # the idle base layer is still applied first
        self.assertLess(self.model.index("applyClip(StarPlatinumAnimData.IDLE"), self.model.index("switch (state.actionPhase)"))

    def test_uppercut_dispatch_and_clip(self):
        up = self.clips["UPPERCUT"]
        self.assertEqual(up["name"], "uppercut")
        self.assertAlmostEqual(up["length"], 1.1, places=4)
        self.assertEqual(up["starts"], [0.0, 0.45, 0.5])
        self.assertFalse(up["loop"])
        self.assertEqual(up["loop_back"], -1.0)
        self.assertTrue(len(up["channels"]) > 0)
        punch = method_body(self.model, "private void applyPunch(")
        self.assertRegex(punch, r"state\.abilityKind\s*==\s*StarPlatinumRenderState\.ABILITY_UPPERCUT\s*\?\s*StarPlatinumAnimData\.UPPERCUT")

    def test_star_finger_pose_and_reach(self):
        sf = self.clips["STAR_FINGER"]
        self.assertEqual(sf["name"], "starFinger")
        self.assertEqual((sf["length"], sf["loop"], sf["loop_back"], sf["looks"]), (0.0, False, -1.0, {}))
        self.assertEqual(sf["starts"], [0.0, 0.0, 0.0])
        pose = {b: kfs[0][1] for (b, pos), kfs in sf["channels"].items() if not pos}
        self.assertEqual(pose["rightArm"], [-62.5, 0.0, 90.0])
        self.assertEqual(pose["leftArm"], [0.0, 0.0, -45.0])
        self.assertEqual(pose["body"], [0.0, -27.5, 0.0])

    def test_inhale_pose(self):
        inhale = self.clips["INHALE"]
        self.assertEqual(inhale["name"], "inhale")
        self.assertEqual((inhale["length"], inhale["loop"], inhale["loop_back"], inhale["looks"]), (0.0, False, -1.0, {}))
        self.assertEqual(inhale["starts"], [0.0, 0.0, 0.0])
        self.assertTrue(len(inhale["channels"]) > 0)
        for (bone, is_pos), kfs in inhale["channels"].items():
            self.assertEqual(len(kfs), 1, "inhale pose must be static: %s" % (bone,))
            self.assertIn(bone, self.orig)

    def test_zoom_sensible_idle_gaze(self):
        self.assertNotIn("ZOOM", self.clips)
        zoom = method_body(self.model, "private void applyZoom(")
        self.assertNotIn("applyClip", zoom)
        self.assertIn('bones.get("head")', self.model)
        self.assertRegex(self.model, r"head\.xRot \+= state\.xRot")
        self.assertRegex(self.model, r"head\.yRot \+= state\.yRot")

    def test_time_stop_charge(self):
        ts = self.clips["TIME_STOP"]
        self.assertEqual(ts["name"], "timeStop")
        self.assertAlmostEqual(ts["length"], 2.5, places=4)
        self.assertEqual(ts["starts"], [0.0, 2.0, 0.0])
        self.assertFalse(ts["loop"])
        self.assertTrue(any(is_pos for _, is_pos in ts["channels"].keys()), "timeStop must contain position channels")
        body = method_body(self.model, "private void applyTimeStop(")
        self.assertIn("StarPlatinumAnimData.TIME_STOP", body)
        self.assertIn("clip.phaseStarts()[1]", body)

    def test_original_geometry_preserved_74_parts_107_cubes(self):
        self.assertEqual(len(self.orig_list), 74)
        total_cubes = sum(len(p.cubes) for p in self.orig_list)
        self.assertEqual(total_cubes, 107)


if __name__ == "__main__":
    unittest.main(verbosity=2)
