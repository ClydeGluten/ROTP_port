#!/usr/bin/env python3
"""Parser for the ORIGINAL (1.16.5 / ModelRenderer) Star Platinum model.

Shared by generate_star_platinum_model.py and verify_star_platinum_model.py.

Ripples of the Past is GPL-3.0; geometry produced from this parser is a derivative of
src/main/java/com/github/standobyte/jojo/client/render/entity/model/stand/StarPlatinumModel.java
(Blockbench 4.8.3 export) by StandObyte and contributors.
"""
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional

REPO_ROOT = Path(__file__).resolve().parents[2]
_STAND = "src/main/java/com/github/standobyte/jojo/client/render/entity/model/stand/"
ORIGINAL_MODEL = REPO_ROOT / (_STAND + "StarPlatinumModel.java")
ORIGINAL_BASE = REPO_ROOT / (_STAND + "HumanoidStandModel.java")
ORIGINAL_ANIM = REPO_ROOT / "src/main/resources/assets/jojo/animations/star_platinum.animation.json"

NUM = r"(-?\d+(?:\.\d+)?)F"


@dataclass
class Cube:
    u: int
    v: int
    x: str
    y: str
    z: str
    w: str
    h: str
    d: str
    delta: str
    mirror: bool


@dataclass
class Part:
    name: str
    pos: tuple = ("0.0F", "0.0F", "0.0F")
    rot: tuple = ("0.0F", "0.0F", "0.0F")
    parent: Optional[str] = None
    cubes: List[Cube] = field(default_factory=list)


def parse_original(path: Path = ORIGINAL_MODEL):
    """Return (parts in parent-before-child order, (texW, texH)). Float literals keep their original text."""
    text = path.read_text(encoding="utf-8")
    parts = {}
    order = []
    for m in re.finditer(r"^\s*(\w+) = new (?:XRotation)?ModelRenderer\(this\);", text, re.M):
        parts[m.group(1)] = Part(m.group(1))
        order.append(m.group(1))

    sep = r"\s*,\s*"
    for m in re.finditer(r"^\s*(\w+)\.setPos\(\s*" + sep.join([NUM] * 3) + r"\s*\);", text, re.M):
        parts[m.group(1)].pos = tuple(m.group(i) + "F" for i in (2, 3, 4))
    for m in re.finditer(r"^\s*setRotationAngle\((\w+),\s*" + sep.join([NUM] * 3) + r"\s*\);", text, re.M):
        parts[m.group(1)].rot = tuple(m.group(i) + "F" for i in (2, 3, 4))
    for m in re.finditer(r"^\s*(\w+)\.addChild\((\w+)\);", text, re.M):
        parent, child = m.group(1), m.group(2)
        if parts[child].parent is not None:
            raise ValueError("part %s has two parents" % child)
        parts[child].parent = parent
    cube_re = re.compile(
        r"^\s*(\w+)\.texOffs\((\d+),\s*(\d+)\)\.addBox\(" + sep.join([NUM] * 7) + r",\s*(true|false)\);", re.M)
    for m in cube_re.finditer(text):
        g = m.groups()
        parts[g[0]].cubes.append(Cube(int(g[1]), int(g[2]), *[x + "F" for x in g[3:10]], mirror=(g[10] == "true")))
    total_cubes = len(re.findall(r"\.addBox\(", text))
    parsed_cubes = sum(len(p.cubes) for p in parts.values())
    if total_cubes != parsed_cubes:
        raise ValueError("parsed %d of %d addBox calls" % (parsed_cubes, total_cubes))

    base = ORIGINAL_BASE.read_text(encoding="utf-8")
    m = re.search(r"public HumanoidStandModel\(\)\s*\{\s*this\((\d+),\s*(\d+)\);", base)
    if not m:
        raise ValueError("cannot determine default texture size from HumanoidStandModel")
    tex = (int(m.group(1)), int(m.group(2)))

    roots = [n for n in order if parts[n].parent is None]
    if roots != ["root"]:
        raise ValueError("expected a single root part named 'root', got %r" % roots)
    ordered, seen = [], set()

    def visit(n):
        if n in seen:
            return
        if parts[n].parent is not None:
            visit(parts[n].parent)
        seen.add(n)
        ordered.append(parts[n])
    for n in order:
        visit(n)
    return ordered, tex
