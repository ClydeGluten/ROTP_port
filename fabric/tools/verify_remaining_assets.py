#!/usr/bin/env python3
"""Focused original-source geometry/resource checks. No game or Gradle launch.

--check: source conversions and fixture contracts, without requiring generation.
--check-output: additionally require exact current generated files, no stale files.
These checks do not certify a renderer, animation playback or equipment gameplay.
"""
import argparse
import json
import re

import convert_remaining_resources as resources
import generate_remaining_stand_models as geometry

FIXTURES = resources.ROOT / "fabric/migration/fixtures/remaining-model-cases.json"
require = resources.require


def verify():
    resources.check_fixtures()
    outputs, models = geometry.render_outputs()
    require(outputs == geometry.render_outputs()[0], "Model conversion is not deterministic")
    fixture = resources.json_load(FIXTURES)
    require(set(models) == set(fixture["models"]), "Model fixture coverage differs from manifest")
    for name, expected in fixture["models"].items():
        model = models[name]
        parts = {p["name"]: p for p in model["parts"]}
        cubes = [c for p in parts.values() for c in p["cubes"]]
        meshes = [m for p in parts.values() for m in p["meshes"]]
        actual = dict(parts=len(parts), cubes=len(cubes), meshes=len(meshes), mirrors=sum(c["mirror"] for c in cubes), limbs=sum(p["x_rotation_limb"] for p in parts.values()))
        for key, value in actual.items():
            require(value == expected[key], f"{name}: {key} count {value}, expected {expected[key]}")
        require(parts["root"]["position"] == expected["root_position"], f"{name}: source root pivot lost")
        require(parts["head"]["position"] == expected["head_position"] and parts["head"]["parent"] == "root", f"{name}: source head hierarchy changed")
        for key, value in expected["head_cube"].items():
            require(parts["head"]["cubes"][0][key] == value, f"{name}: head cube {key} changed")
        if "part" in expected:
            p = expected["part"]
            for key, value in p.items():
                require(parts[p["name"]][key] == value, f"{name}: {p['name']} {key} changed")
        if "mesh_faces" in expected:
            require(sum(len(m["polygons"]) for m in meshes) == expected["mesh_faces"], "GE mesh polygons dropped")
            require(parts["head"]["cubes"][0]["faces"]["north"] == expected["head_north_uv"], "GE per-face UV changed")
        # Every source cube/mesh becomes Java geometry, not merely JSON metadata.
        entry = next(e for e in resources.json_load(geometry.MANIFEST)["models"] if e["id"] == name)
        generated = next(raw.decode() for path, raw in outputs.items() if path.endswith("/"+entry["class"]+".java"))
        require(len(re.findall(r'parts.put\("', generated)) == len(parts), f"{name}: generated hierarchy incomplete")
        require(generated.count("cubes.add(new ModelPart.Cube(") == sum("faces" not in c for c in cubes), f"{name}: generated Java cube count mismatch")
        require(generated.count("cubes.add(face(") == sum(len(c["faces"]) for c in cubes if "faces" in c) + sum(len(m["polygons"]) for m in meshes), f"{name}: generated polygon count mismatch")
    animation = json.loads(outputs["resources/assets/jojo/converted_animations/gold_experience.json"])
    expected = fixture["animation"]
    require(len(animation["clips"]) == expected["clips"], "GE animation clips dropped")
    idle = animation["clips"]["idle"]
    require(idle["length"] == expected["idle_length"], "GE idle length changed")
    arm = next(t for t in idle["tracks"] if t["part"] == "rightArm" and t["channel"] == "rotation")
    require(arm["frames"][0]["vector"] == expected["idle_right_arm_first"], "GE authored animation changed")
    require(expected["barrage_event"] in animation["clips"]["barrage"]["events"], "GE timeline event dropped")
    model_manifest = resources.json_load(geometry.MANIFEST)
    ge_entry = next(e for e in model_manifest["models"] if e["id"] == "gold_experience")
    for case in fixture["reject"]:
        try:
            if case == "empty_ge_java":
                path = resources.source_path("src/main/java/com/github/standobyte/jojo/client/render/entity/model/stand/GoldExperienceModel.java")
                geometry.parse_java(path.read_text(), path.stem)
            elif case == "unknown_java_statement":
                path = resources.source_path(model_manifest["models"][0]["source"])
                geometry.parse_java(path.read_text().replace("super();", "super(); root.unsupported();", 1), path.stem)
            elif case in {"unknown_bb_element", "rescaled_bb_cube"}:
                data = resources.json_load(resources.source_path(ge_entry["source"]))
                data["elements"][0]["type" if case == "unknown_bb_element" else "rescale"] = "unsupported" if case == "unknown_bb_element" else True
                geometry.parse_bb(data)
            else:
                data = resources.json_load(resources.source_path(ge_entry["animation"]))
                bones = data["animations"]["idle"]["bones"]
                if case == "unknown_animation_bone":
                    bones["missing_part"] = bones.pop("rightArm")
                elif case == "unknown_animation_channel":
                    bones["rightArm"]["unsupported"] = bones["rightArm"].pop("rotation")
                elif case == "unknown_animation_expression":
                    bones["head"]["rotation"]["vector"][0] = "query.unsupported"
                else:
                    raise AssertionError(f"Unknown fixture: {case}")
                geometry.animations(data, {p["name"] for p in models["gold_experience"]["parts"]})
        except ValueError:
            continue
        raise ValueError(f"Unsupported source construct accepted: {case}")
    try:
        resources.validate_references({"assets/jojo/models/item/missing.json": resources.encoded({"textures": {"layer0": "jojo:item/does_not_exist"}})}, [])
    except ValueError:
        pass
    else:
        raise ValueError("Missing source texture accepted")
    return outputs, models


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--check-output", action="store_true")
    args = parser.parse_args()
    try:
        outputs, models = verify()
        if args.check_output:
            resources.materialize(outputs, geometry.OUTPUT, check=True)
            resources.materialize(resources.render_outputs(), resources.OUTPUT, check=True)
        print(f"Remaining asset checks passed: {len(models)} distinct source models, GE meshes/16 clips, exact Blade Hat resource; runtime visual checks remain separate")
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f"Remaining asset check failed: {error}\n")


if __name__ == "__main__":
    main()
