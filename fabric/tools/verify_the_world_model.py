#!/usr/bin/env python3
"""Independent geometry and keyframe checks against the original The World sources."""
import ast
import json
import re
import unittest
from pathlib import Path
import generate_the_world_model as generator
from star_platinum_source import parse_original
from verify_star_platinum_model import parse_generated, parse_clips


class TheWorldParity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        parts, cls.texture = parse_original(generator.TW_ORIGINAL_MODEL)
        cls.original = {part.name: part for part in parts}
        cls.model = (generator.OUT_DIR / "TheWorldModel.java").read_text()
        cls.generated, cls.order = parse_generated(cls.model)
        cls.data = (generator.OUT_DIR / "TheWorldAnimData.java").read_text()
        cls.clips = parse_clips(cls.data)
        cls.animations = json.loads(generator.TW_ORIGINAL_ANIM.read_text())["animations"]

    def test_geometry(self):
        self.assertEqual(len(self.original), 59)
        self.assertEqual(set(self.generated) | {"root"}, set(self.original))
        count = 0
        for name, part in self.generated.items():
            original = self.original[name]
            self.assertEqual(part["parent"], original.parent, name)
            self.assertEqual(part["pos"], tuple(float(v[:-1]) for v in original.pos), name)
            self.assertEqual(part["rot"], tuple(float(v[:-1]) for v in original.rot), name)
            self.assertEqual(len(part["cubes"]), len(original.cubes), name)
            for actual, cube in zip(part["cubes"], original.cubes):
                self.assertEqual(actual, (cube.u, cube.v, cube.mirror, *[float(v[:-1]) for v in
                                 (cube.x, cube.y, cube.z, cube.w, cube.h, cube.d, cube.delta)]))
                count += 1
        self.assertEqual(count, 68)
        self.assertEqual(self.texture, (128, 128))
        self.assertIn("LayerDefinition.create(mesh, 128, 128)", self.model)

    def test_all_numeric_animation_channels(self):
        self.assertEqual(set(self.clips), {name for name, _ in generator.TW_CLIPS})
        for const, name in generator.TW_CLIPS:
            expected = {}
            for bone, channels in self.animations[name]["bones"].items():
                self.assertIn(bone, self.original)
                for kind, raw in channels.items():
                    if kind not in ("rotation", "position"): continue
                    frames = {"0": raw} if isinstance(raw, list) or "vector" in raw or "post" in raw else raw
                    values = []
                    dynamic = False
                    for time, frame in sorted(frames.items(), key=lambda item: float(item[0])):
                        vector = frame if isinstance(frame, list) else frame.get("vector", frame.get("post", {}).get("vector"))
                        if any(isinstance(v, str) and "query." in v for v in vector): dynamic = True; break
                        # Only numeric arithmetic is accepted; no evaluation of arbitrary expressions.
                        parsed = []
                        for v in vector:
                            if isinstance(v, str):
                                match = re.fullmatch(r'(-?[\d.]+)\s*([+-])\s*([\d.]+)', v)
                                self.assertIsNotNone(match)
                                v = float(match[1]) + (1 if match[2] == '+' else -1) * float(match[3])
                            parsed.append(float(v))
                        values.append((float(time), parsed))
                    if not dynamic: expected[(bone, kind == "position")] = values
            actual = self.clips[const]["channels"]
            self.assertEqual(set(actual), set(expected), name)
            for key, values in expected.items():
                self.assertEqual(len(actual[key]), len(values), (name, key))
                for (at, av), (et, ev) in zip(actual[key], values):
                    self.assertAlmostEqual(at, et, places=4)
                    for a, e in zip(av, ev): self.assertAlmostEqual(a, e, places=4)

    def test_generated_sources_reproduce(self):
        files, _ = generator.render_outputs()
        for name, expected in files.items(): self.assertEqual((generator.OUT_DIR / name).read_text(), expected, name)

    def test_dynamic_head_tracks_preserved(self):
        head_data = generator.gen_head_data()
        for _, name in generator.TW_CLIPS:
            if "rotation" in self.animations[name]["bones"].get("head", {}):
                self.assertIn('Map.entry("' + name + '"', head_data)
        self.assertIn("360.0F", head_data)
        self.assertIn("-15.0F", head_data)
        self.assertIn("channels[i].sample(headTime, sample)", self.model)

    def test_action_phases(self):
        self.assertEqual(self.clips["KICK"]["starts"], [0, 0.5, 0.5417])
        self.assertEqual(self.clips["TIME_BREAKER"]["starts"], [0, 0.25, 0.3])
        self.assertEqual(self.clips["BARRAGE"]["loop_back"], 0)
        self.assertIn("ABILITY_KICK = 1", self.model)
        self.assertIn("ABILITY_TS_HEAVY_PUNCH = 2", self.model)
        self.assertIn("TheWorldAnimData.TIME_BREAKER", self.model)


if __name__ == "__main__": unittest.main(verbosity=2)
