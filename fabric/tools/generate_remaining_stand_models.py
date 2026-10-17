#!/usr/bin/env python3
"""Generate original HG/SC/MR/CD Java and GE BB geometry, never SP substitutes.

Output: build/generated/remainingStandModels/{java,resources}. Generated Java
classes expose createRoot(): a fresh ModelPart container with named `root` child.
Geometry JSON records original names, transforms, UVs, meshes and XRotation limbs.
GE animation JSON normalizes tracks/events, preserving source easing/expressions.
This supplies geometry/data; feature-specific pose playback belongs to renderers.

GPL-3.0 derivative of StandObyte's original models, ParseGenericModel,
MeshModelBox, and MeshVerticesHelper (the latter credits Blockbench/three.js).
"""
import argparse
import hashlib
import math
import re
import struct
from pathlib import Path

from convert_remaining_resources import ROOT, encoded, identifier, json_load, materialize, require, source_path

MANIFEST = ROOT / "fabric/migration/remaining-model-manifest.json"
OUTPUT = ROOT / "fabric/build/generated/remainingStandModels"
BASE = ROOT / "src/main/java/com/github/standobyte/jojo/client/render/entity/model/stand/HumanoidStandModel.java"
NUMBER = r"-?\d+(?:\.\d+)?[fF]?"


def vector(value, length=3):
    require(isinstance(value, (tuple, list)) and len(value) == length and
            all(isinstance(x, (int, float)) and not isinstance(x, bool) and math.isfinite(x) for x in value), f"Invalid vector: {value}")
    return list(value)


def numbers(text, count):
    pieces = [v.strip() for v in text.split(",")]
    require(len(pieces) == count and all(re.fullmatch(NUMBER, v) for v in pieces), f"Unmapped numeric arguments: {text}")
    return [float(v.rstrip("fF")) for v in pieces]


def part(name, parent=None, pos=None, rot=None, limb=False):
    require(re.fullmatch(r"[A-Za-z_]\w*", name), f"Unsupported part name: {name}")
    return dict(name=name, parent=parent, position=pos or [0, 0, 0], rotation=rot or [0, 0, 0],
                x_rotation_limb=limb, cubes=[], meshes=[])


def ordered_parts(parts):
    require(parts and sum(len(p["cubes"]) + len(p["meshes"]) for p in parts.values()) > 0, "No source geometry: empty Java GE shell cannot be converted")
    require([p["name"] for p in parts.values() if p["parent"] is None] == ["root"], "Expected one authored root part")
    result, seen, visiting = [], set(), set()
    def visit(name):
        require(name in parts, f"Missing parent {name}")
        require(name not in visiting, f"Cyclic model hierarchy at {name}")
        if name in seen:
            return
        visiting.add(name)
        if parts[name]["parent"]:
            visit(parts[name]["parent"])
        visiting.remove(name)
        seen.add(name)
        result.append(parts[name])
    for name in parts:
        visit(name)
    return result


def parse_java(text, class_name):
    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)
    constructors = []
    for match in re.finditer(r"public\s+" + re.escape(class_name) + r"\([^)]*\)\s*\{", text):
        start, depth, end = match.end(), 1, match.end()
        while depth and end < len(text):
            depth += (text[end] == "{") - (text[end] == "}")
            end += 1
        require(depth == 0, "Unclosed Java constructor")
        constructors.append(text[start:end - 1])
    bodies = [body for body in constructors if "new ModelRenderer" in body or "new XRotationModelRenderer" in body]
    require(len(bodies) == 1, f"Expected exactly one geometry constructor for {class_name}, found {len(bodies)}")
    base = BASE.read_text()
    dimensions = re.search(r"public HumanoidStandModel\(\)\s*\{\s*this\((\d+),\s*(\d+)\)", base)
    require(dimensions, "Default texture dimensions unmapped")
    require(re.search(r"convertLimb\(ModelRenderer limbModelPart\)\s*\{\s*return new XRotationModelRenderer\(this\);", base), "convertLimb implementation changed; review adapter")
    texture_size = [int(v) for v in dimensions.groups()]
    # MR's no-arg constructor delegates dimensions through its second constructor.
    for body in constructors:
        match = re.fullmatch(r"\s*this\(RenderType::entityTranslucent,\s*(\d+),\s*(\d+)\);\s*", body)
        if match:
            texture_size = [int(v) for v in match.groups()]
    parts = {}
    for statement in bodies[0].split(";"):
        s = statement.strip()
        if not s:
            continue
        if s in {"super()", "super(renderType, texWidth, texHeight)", "isLayerModel = true"} or re.fullmatch(r"(?:ModelRenderer|XRotationModelRenderer)\s+\w+", s):
            continue
        dimensions_override = re.fullmatch(r"(texWidth|texHeight) = (\d+)", s)
        if dimensions_override:
            value = int(dimensions_override.group(2))
            require(value > 0, "Invalid explicit texture dimension")
            texture_size[0 if dimensions_override.group(1) == "texWidth" else 1] = value
            continue
        m = re.fullmatch(r"(\w+) = (convertLimb\()?new (XRotationModelRenderer|ModelRenderer)\(this\)(\))?", s)
        if m:
            name, wrapper, cls, close = m.groups()
            require(bool(wrapper) == bool(close), "Malformed convertLimb")
            require(name not in parts, f"Duplicate Java part: {name}")
            parts[name] = part(name, limb=bool(wrapper) or cls == "XRotationModelRenderer")
            continue
        m = re.fullmatch(r"(\w+)\.setPos\((.*)\)", s)
        if m:
            parts[m[1]]["position"] = numbers(m[2], 3)
            continue
        m = re.fullmatch(r"setRotationAngle\((\w+),\s*(.*)\)", s)
        if m:
            parts[m[1]]["rotation"] = numbers(m[2], 3)
            continue
        m = re.fullmatch(r"(\w+)\.addChild\((\w+)\)", s)
        if m:
            require(m[1] in parts and m[2] in parts and parts[m[2]]["parent"] is None, f"Invalid parent: {s}")
            parts[m[2]]["parent"] = m[1]
            continue
        m = re.fullmatch(r"(\w+)\.texOffs\((\d+),\s*(\d+)\)\.addBox\((.*),\s*(true|false)\)", s)
        if m:
            values = numbers(m[4], 7)
            require(all(v >= 0 for v in values[3:6]), f"Negative cube size: {s}")
            parts[m[1]]["cubes"].append(dict(uv=[int(m[2]), int(m[3])], origin=values[:3], size=values[3:6], inflate=values[6], mirror=m[5] == "true"))
            continue
        raise ValueError(f"Unmapped Java constructor statement in {class_name}: {s}")
    return {"texture_size": texture_size, "parts": ordered_parts(parts)}


def subtract(a, b):
    return [x - y for x, y in zip(a, b)]


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def mesh_face(vertices):
    """Original MeshVerticesHelper sorting and MeshModelBox normal/triangle padding."""
    require(3 <= len(vertices) <= 4, f"Unsupported mesh polygon with {len(vertices)} vertices")
    def magic(base1, base2, top, check):
        start = subtract(base2, base1)
        denominator = dot(start, start)
        require(denominator > 0, "Degenerate mesh edge")
        t = dot(start, subtract(top, base1)) / denominator
        normal = [start[i] * t + base1[i] - top[i] for i in range(3)]
        return dot(normal, check) - dot(base2, normal) > 0
    if len(vertices) == 4:
        p = [v[:3] for v in vertices]
        if magic(p[1], p[2], p[0], p[3]):
            vertices = [vertices[2], vertices[0], vertices[1], vertices[3]]
        elif magic(p[0], p[1], p[2], p[3]):
            vertices = [vertices[0], vertices[2], vertices[1], vertices[3]]
    a, b = subtract(vertices[1][:3], vertices[0][:3]), subtract(vertices[2][:3], vertices[0][:3])
    normal = [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
    magnitude = math.sqrt(dot(normal, normal))
    require(magnitude > 1e-12, "Degenerate mesh polygon")
    return {"vertices": vertices + ([vertices[-1]] if len(vertices) == 3 else []), "normal": [n/magnitude for n in normal]}


def parse_bb(data):
    require(data.get("meta", {}).get("model_format") == "free", "Unsupported BB model format")
    resolution = data["resolution"]
    size = [resolution["width"], resolution["height"]]
    require(all(isinstance(x, int) and x > 0 for x in size), "Invalid BB texture dimensions")
    elements = {}
    for element in data["elements"]:
        require(element["uuid"] not in elements, "Duplicate BB element UUID")
        require(element["type"] in {"cube", "mesh"}, f"Unmapped BB element: {element['type']}")
        common = {"name", "uuid", "type", "export", "visibility", "origin", "rotation", "render_order", "allow_mirror_modeling", "locked", "color", "faces"}
        fields = {"box_uv", "rescale", "from", "to", "autouv", "inflate", "uv_offset", "mirror_uv", "light_emission"} if element["type"] == "cube" else {"vertices"}
        require(set(element) <= common | fields, f"Unmapped BB element fields: {set(element) - common - fields}")
        require(element.get("light_emission", 0) == 0, "BB light emission needs explicit rendering support")
        require(not element.get("rescale") and element.get("render_order", "default") == "default", "Unmapped BB rescale/render order")
        elements[element["uuid"]] = element
    parts, used = {}, set()
    def add_group(group, parent=None, parent_origin=None):
        require(set(group) <= {"name", "origin", "rotation", "uuid", "children", "export", "mirror_uv", "visibility", "autouv", "color", "isOpen", "locked", "selected"}, "Unmapped BB group fields")
        if not group.get("export", True):
            return
        require(group.get("visibility", True), "Hidden BB group needs explicit visibility adapter")
        name = group["name"]
        require(name not in parts, f"Duplicate BB part {name}")
        origin = vector(group.get("origin", [0, 0, 0]))
        relative = subtract(origin, parent_origin or [0, 0, 0])
        position = [-relative[0], -relative[1] + (24 if parent is None else 0), relative[2]]
        rotation = [v * math.pi/180 * sign for v, sign in zip(vector(group.get("rotation", [0, 0, 0])), [-1, -1, 1])]
        current = part(name, parent, position, rotation)
        parts[name] = current
        rotated = 0
        for child in group.get("children", []):
            if isinstance(child, dict):
                add_group(child, name, origin)
                continue
            require(child in elements and child not in used, f"Missing/duplicate BB element reference {child}")
            used.add(child)
            element = elements[child]
            if not element.get("export", True):
                continue
            if any(vector(element.get("rotation", [0, 0, 0]))):
                # The source loader materializes rotated elements as named child bones.
                generated = f"{name}_r{rotated}"
                rotated += 1
                copied = dict(element, rotation=[0, 0, 0])
                elements[child] = copied
                used.remove(child)
                add_group(dict(name=generated, origin=element.get("origin", [0, 0, 0]), rotation=element["rotation"], children=[child]), name, origin)
                continue
            if element["type"] == "cube":
                require(not element.get("rescale"), "Unsupported rescaled cube")
                start, end = vector(element["from"]), vector(element["to"])
                dimensions = subtract(end, start)
                require(all(x >= 0 for x in dimensions), "Inverted BB cube")
                faces = {}
                for direction, face in element["faces"].items():
                    require(direction in {"north", "south", "east", "west", "up", "down"} and set(face) <= {"uv", "texture"}, f"Unmapped cube face {direction}")
                    require(face.get("texture") in {None, 0}, "Multi-texture BB cubes unsupported")
                    faces[direction] = vector(face["uv"], 4)
                require(faces, "BB cube without faces")
                current["cubes"].append(dict(origin=[origin[0]-end[0], origin[1]-end[1], start[2]-origin[2]], size=dimensions,
                                             inflate=element.get("inflate", 0), uv=element.get("uv_offset", [0, 0]),
                                             mirror=element.get("mirror_uv", False), faces=faces, source_uuid=child))
            else:
                element_origin = vector(element.get("origin", [0, 0, 0]))
                converted = {}
                for key, value in element["vertices"].items():
                    translated = subtract([x+y for x,y in zip(vector(value), element_origin)], origin)
                    converted[key] = [-translated[0], -translated[1], translated[2]]
                polygons = []
                for face in element["faces"].values():
                    require(set(face) <= {"vertices", "uv", "texture"} and face.get("texture") in {None, 0}, "Unmapped mesh face")
                    ids = list(dict.fromkeys(face["vertices"]))
                    if len(ids) <= 2:
                        continue  # Original loader skips mesh edges, not polygons.
                    vertices = [converted[key] + vector(face["uv"][key], 2) for key in ids]
                    polygons.append(mesh_face(vertices))
                require(polygons, "Mesh has no faces")
                current["meshes"].append(dict(source_uuid=child, polygons=polygons))
    for group in data["outliner"]:
        require(isinstance(group, dict), "Unparented BB element unsupported")
        add_group(group)
    require(used == {key for key, e in elements.items() if e.get("export", True)}, "Unmapped/unattached BB elements")
    return {"texture_size": size, "parts": ordered_parts(parts)}


def animations(data, part_names):
    require(set(data) <= {"format_version", "geckolib_format_version", "animations"}, "Unmapped animation document fields")
    result = {}
    easing = {"linear", "easeInCubic", "easeOutSine", "easeInSine", "easeInOutSine"}
    queries = {"query.head_x_rotation", "query.head_y_rotation"}
    for name, clip in data["animations"].items():
        require(set(clip) <= {"loop", "animation_length", "bones", "timeline"}, f"Unmapped clip fields: {name}")
        length = clip.get("animation_length", 0)
        require(isinstance(length, (int, float)) and math.isfinite(length) and length >= 0, "Invalid animation duration")
        require(clip.get("loop", False) in (True, False, "hold_on_last_frame"), "Unmapped loop mode")
        tracks = []
        for bone, channels in clip.get("bones", {}).items():
            require(bone in part_names, f"Animation references unknown part {bone}")
            for channel, raw in channels.items():
                require(channel in {"rotation", "position"}, f"Unmapped animation channel {channel}")
                if isinstance(raw, list):
                    raw = {"0.0": {"vector": raw}}
                elif "vector" in raw:
                    raw = {"0.0": raw}
                frames = []
                for time, frame in sorted(raw.items(), key=lambda x: float(x[0])):
                    require(0 <= float(time) <= length + 1e-6, f"Keyframe outside clip {name}")
                    require(isinstance(frame, dict) and set(frame) <= {"vector", "easing"}, f"Unmapped keyframe {frame}")
                    value = frame["vector"]
                    require(isinstance(value, list) and len(value) == 3 and all((isinstance(v, (int, float)) and math.isfinite(v)) or v in queries for v in value), "Unmapped animation expression")
                    require(frame.get("easing", "linear") in easing, "Unmapped easing")
                    frames.append(dict(time=float(time), vector=value, easing=frame.get("easing", "linear")))
                tracks.append(dict(part=bone, channel=channel, frames=frames))
        events = []
        for time, statements in sorted(clip.get("timeline", {}).items(), key=lambda x: float(x[0])):
            require(0 <= float(time) <= length + 1e-6, "Timeline outside clip")
            for statement in statements if isinstance(statements, list) else [statements]:
                match = re.fullmatch(r"(phase|phase\.loopBack|barrage)\s*=\s*(\w+);", statement)
                require(match and ((match[1] == "phase" and match[2] in {"WINDUP", "PERFORM", "RECOVERY"}) or
                                  (match[1] == "phase.loopBack" and match[2].isdigit()) or
                                  (match[1] == "barrage" and match[2] == "TWO_HANDED")), f"Unmapped timeline statement: {statement}")
                events.append(dict(time=float(time), property=match[1], value=match[2]))
        result[name] = dict(length=length, loop=clip.get("loop", False), tracks=tracks, events=events)
    require(result, "Empty animation set")
    return {"version": 1, "coordinate_system": "original GeckoStandAnimator: rotation(x,y,z) degrees to radians, position(x,-y,z); additive to bind pose", "clips": result}


def java_float(value):
    require(math.isfinite(value), "Non-finite geometry")
    return (format(float(value), ".10g") + (".0" if float(value).is_integer() and "e" not in format(float(value), ".10g") else "") + "F")


def java_vector(values):
    return ", ".join(java_float(v) for v in values)


def cube_faces(cube):
    """ParseGenericModel.ElementCube.makeCube: per-face UV orientation, X/Y reflection."""
    lo = [v - cube["inflate"] for v in cube["origin"]]
    hi = [v + size + cube["inflate"] for v, size in zip(cube["origin"], cube["size"])]
    x0,y0,z0 = lo; x1,y1,z1 = hi
    vertices = {
        "down": [[x1,y0,z1],[x0,y0,z1],[x0,y0,z0],[x1,y0,z0]],
        "up": [[x1,y1,z0],[x0,y1,z0],[x0,y1,z1],[x1,y1,z1]],
        "west": [[x0,y0,z0],[x0,y0,z1],[x0,y1,z1],[x0,y1,z0]],
        "north": [[x1,y0,z0],[x0,y0,z0],[x0,y1,z0],[x1,y1,z0]],
        "east": [[x1,y0,z1],[x1,y0,z0],[x1,y1,z0],[x1,y1,z1]],
        "south": [[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]]}
    opposite = {"up":"down", "down":"up", "east":"west", "west":"east", "north":"north", "south":"south"}
    normals = {"down":[0,-1,0], "up":[0,1,0], "west":[-1,0,0], "east":[1,0,0], "north":[0,0,-1], "south":[0,0,1]}
    result = []
    for direction, points in vertices.items():
        if opposite[direction] not in cube["faces"]:
            continue
        uv = cube["faces"][opposite[direction]]
        u0,v0,u1,v1 = (uv[2],uv[3],uv[0],uv[1]) if direction in {"up","down"} else uv
        result.append(dict(vertices=[p+[u,v] for p,(u,v) in zip(points, [(u1,v0),(u0,v0),(u0,v1),(u1,v1)])], normal=normals[direction]))
    return result


def java_source(model, class_name, package):
    tw, th = model["texture_size"]
    lines = [f"package {package};", "", "// Generated from original GPL-3.0 model geometry. Do not edit.",
             "import java.util.ArrayList;", "import java.util.LinkedHashMap;", "import java.util.List;", "import java.util.Map;", "import java.util.Set;",
             "import net.minecraft.client.model.geom.ModelPart;", "import net.minecraft.client.model.geom.PartPose;", "import net.minecraft.core.Direction;", "import org.joml.Vector3f;", "",
             f"public final class {class_name} {{", f"    private {class_name}() {{}}", f"    public static final int TEXTURE_WIDTH = {tw}, TEXTURE_HEIGHT = {th};", "",
             "    // Original XRotationModelRenderer secondary rotations are applied by feature pose code.",
             "    public static final Set<String> X_ROTATION_LIMBS = Set.of(" + ", ".join('"'+p["name"]+'"' for p in model["parts"] if p["x_rotation_limb"]) + ");", "",
             "    public static ModelPart createRoot() {", "        Map<String, ModelPart> parts = new LinkedHashMap<>();", "        Map<String, Map<String, ModelPart>> children = new LinkedHashMap<>();"]
    for index, p in enumerate(model["parts"]):
        lines += [f'        children.put("{p["name"]}", new LinkedHashMap<>());', f'        ModelPart part{index} = new ModelPart(cubes{index}(), children.get("{p["name"]}"));',
                  f'        part{index}.setInitialPose(PartPose.offsetAndRotation({java_vector(p["position"]+p["rotation"])}));',
                  f'        part{index}.resetPose();', f'        parts.put("{p["name"]}", part{index});']
        if p["parent"]:
            lines.append(f'        children.get("{p["parent"]}").put("{p["name"]}", part{index});')
    lines += ['        return new ModelPart(List.of(), Map.of("root", parts.get("root")));', "    }"]
    has_polygons = False
    for index, p in enumerate(model["parts"]):
        lines += [f"    private static List<ModelPart.Cube> cubes{index}() {{", "        List<ModelPart.Cube> cubes = new ArrayList<>();"]
        polygons = []
        for c in p["cubes"]:
            if "faces" in c:
                polygons.extend(cube_faces(c))
            else:
                lines.append(f'        cubes.add(new ModelPart.Cube({c["uv"][0]}, {c["uv"][1]}, {java_vector(c["origin"]+c["size"]+[c["inflate"]]*3)}, {str(c["mirror"]).lower()}, {tw}.0F, {th}.0F, Set.of(Direction.values())));')
        for mesh in p["meshes"]:
            polygons.extend(mesh["polygons"])
        for polygon in polygons:
            has_polygons = True
            lines.append("        cubes.add(face(new ModelPart.Vertex[] {")
            for vertex in polygon["vertices"]:
                lines.append(f"            new ModelPart.Vertex({java_vector(vertex[:3]+[vertex[3]/tw,vertex[4]/th])}),")
            lines += [f'        }}, new Vector3f({java_vector(polygon["normal"])})));']
        lines += ["        return cubes;", "    }"]
    if has_polygons:
        lines += ["    private static ModelPart.Cube face(ModelPart.Vertex[] vertices, Vector3f normal) {",
                  "        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;",
                  "        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;",
                  "        for (ModelPart.Vertex vertex : vertices) {",
                  "            minX = Math.min(minX, vertex.x()); minY = Math.min(minY, vertex.y()); minZ = Math.min(minZ, vertex.z());",
                  "            maxX = Math.max(maxX, vertex.x()); maxY = Math.max(maxY, vertex.y()); maxZ = Math.max(maxZ, vertex.z());",
                  "        }",
                  "        ModelPart.Cube cube = new ModelPart.Cube(0, 0, minX, minY, minZ, maxX-minX, maxY-minY, maxZ-minZ, 0, 0, 0, false, TEXTURE_WIDTH, TEXTURE_HEIGHT, Set.of(Direction.NORTH));",
                  "        cube.polygons[0] = new ModelPart.Polygon(vertices, normal);",
                  "        return cube;", "    }"]
    lines += ["}", ""]
    return "\n".join(lines).encode()


def render_outputs(manifest=None):
    manifest = json_load(MANIFEST) if manifest is None else manifest
    require(manifest.get("version") == 1 and manifest.get("target") == "26.3", "Unknown model manifest version")
    package = manifest["package"]
    require(re.fullmatch(r"[a-z_]\w*(?:\.[a-z_]\w*)+", package), "Invalid Java package")
    outputs, models, classes = {}, {}, set()
    for entry in manifest["models"]:
        name, cls = entry["id"], entry["class"]
        require(re.fullmatch(r"[a-z_]+", name) and re.fullmatch(r"[A-Z]\w*", cls), "Invalid model ID/class")
        require(name not in models and cls not in classes, "Duplicate model ID/class")
        require(entry["format"] in {"java", "bbmodel"}, "Unmapped source model format")
        path = source_path(entry["source"])
        model = parse_java(path.read_text(), path.stem) if entry["format"] == "java" else parse_bb(json_load(path))
        ns, texture = identifier(entry["texture"])
        texture_path = ROOT / f"src/main/resources/assets/{ns}/textures/{texture}.png"
        require(texture_path.is_file(), f"Missing model texture {texture_path}")
        header = texture_path.read_bytes()[:24]
        require(header[:8] == b"\x89PNG\r\n\x1a\n", "Model texture is not PNG")
        pixel_size = list(struct.unpack(">II", header[16:24]))
        tw, th = model["texture_size"]
        require(pixel_size[0] >= tw and pixel_size[0] % tw == 0 and pixel_size[0] * th == pixel_size[1] * tw,
                "Model texture must be an integer-resolution version of its UV canvas")
        model["texture_pixel_size"] = pixel_size
        model.update(version=1, id=name, source=entry["source"], source_sha256=hashlib.sha256(path.read_bytes()).hexdigest(), texture=entry["texture"])
        models[name] = model
        classes.add(cls)
        outputs[f"java/{package.replace('.', '/')}/{cls}.java"] = java_source(model, cls, package)
        outputs[f"resources/assets/jojo/converted_models/{name}.json"] = encoded(model)
        if "animation" in entry:
            animation_path = source_path(entry["animation"])
            converted = animations(json_load(animation_path), {p["name"] for p in model["parts"]})
            converted.update(source=entry["animation"], source_sha256=hashlib.sha256(animation_path.read_bytes()).hexdigest())
            outputs[f"resources/assets/jojo/converted_animations/{name}.json"] = encoded(converted)
    require(models, "Empty model manifest")
    return dict(sorted(outputs.items())), models


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--check-output", action="store_true")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    try:
        outputs, models = render_outputs()
        if args.check_output or not args.check:
            materialize(outputs, args.output, check=args.check_output)
        print(f"Remaining models: {len(models)} source models, {len(outputs)} validated outputs")
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f"Model conversion failed: {error}\n")


if __name__ == "__main__":
    main()
