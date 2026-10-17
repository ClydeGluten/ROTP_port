#!/usr/bin/env python3
"""Bake original ParseGeckoModel/CustomIconItem logo transforms into textured quads.

No image edits: all source UVs, per-face omissions, pivots, rotations and inflation
are retained. The modern special item renderer consumes these exact vertices.
"""
from pathlib import Path
import json
import math

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/main/resources/assets/jojo/geo/misc/mod_logo.geo.json'
TARGET = ROOT / 'fabric/src/main/resources/assets/jojo/geometry/mod_logo_mesh.json'

def identity():
    return [[float(i == j) for j in range(4)] for i in range(4)]

def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]

def translation(x, y, z):
    result = identity()
    result[0][3], result[1][3], result[2][3] = x, y, z
    return result

def scale(x, y, z):
    result = identity()
    result[0][0], result[1][1], result[2][2] = x, y, z
    return result

def rotation(axis, degrees):
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    result = identity()
    first, second = {'x': (1, 2), 'y': (2, 0), 'z': (0, 1)}[axis]
    result[first][first], result[second][second] = c, c
    result[first][second], result[second][first] = -s, s
    return result

def apply(matrix, vertex, w=1):
    return [sum(matrix[i][j] * [*vertex, w][j] for j in range(4)) for i in range(3)]

def compose_rotations(angles):
    result = identity()
    for axis, angle in zip('zyx', reversed(angles)):
        result = multiply(result, rotation(axis, angle))
    return result

def main():
    geometry = json.loads(SOURCE.read_text())['minecraft:geometry'][0]
    bones = {bone['name']: bone for bone in geometry['bones']}
    width = geometry['description']['texture_width']
    height = geometry['description']['texture_height']
    matrices = {}
    def bone_matrix(bone):
        if bone['name'] in matrices:
            return matrices[bone['name']]
        pivot = bone['pivot']
        if 'parent' in bone:
            parent = bones[bone['parent']]
            p = parent['pivot']
            matrix = multiply(bone_matrix(parent), translation(pivot[0]-p[0], -pivot[1]+p[1], pivot[2]-p[2]))
        else:
            matrix = translation(pivot[0], 24-pivot[1], pivot[2])
        matrix = multiply(matrix, compose_rotations(bone.get('rotation', [0, 0, 0])))
        matrices[bone['name']] = matrix
        return matrix
    # CustomModelItemISTER then CustomIconItem.ModLogoModel exact pose operations.
    pose = multiply(multiply(multiply(scale(-1, -1, 1), translation(-.5, -1.5, .5)), scale(.75, .75, .75)), translation(0, .5, 0))
    normal_pose = multiply(multiply(multiply(scale(-1, -1, 1), rotation('y', -45)), rotation('x', -45)), rotation('z', 45))
    vertices = []
    faces = {
        'down': ([5, 4, 0, 1], [0, -1, 0]), 'up': ([2, 3, 7, 6], [0, 1, 0]),
        'west': ([0, 4, 7, 3], [-1, 0, 0]), 'north': ([1, 0, 3, 2], [0, 0, -1]),
        'east': ([5, 1, 2, 6], [1, 0, 0]), 'south': ([4, 5, 6, 7], [0, 0, 1]),
    }
    opposite = {'up': 'down', 'down': 'up', 'west': 'east', 'east': 'west', 'north': 'north', 'south': 'south'}
    for bone in bones.values():
        matrix = bone_matrix(bone)
        for cube in bone.get('cubes', []):
            p, o, size = bone['pivot'], cube['origin'], cube['size']
            inflate = cube.get('inflate', 0)
            x0, y0, z0 = o[0]-p[0]-inflate, -(o[1]-p[1])-size[1]-inflate, o[2]-p[2]-inflate
            x1, y1, z1 = x0+size[0]+2*inflate, y0+size[1]+2*inflate, z0+size[2]+2*inflate
            mirror = cube.get('mirror', False)
            if mirror and isinstance(cube['uv'], list):
                x0, x1 = x1, x0
            points = [[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]]
            uv = cube['uv']
            if isinstance(uv, list):
                u,v=uv; sx,sy,sz=map(int,size)
                rectangles={'down':[u+sz,v,u+sz+sx,v+sz], 'up':[u+sz+sx,v+sz,u+sz+sx*2,v],
                            'west':[u,v+sz,u+sz,v+sz+sy], 'north':[u+sz,v+sz,u+sz+sx,v+sz+sy],
                            'east':[u+sz+sx,v+sz,u+sz*2+sx,v+sz+sy], 'south':[u+sz*2+sx,v+sz,u+sz*2+sx*2,v+sz+sy]}
            else:
                rectangles={}
                for face in faces:
                    value=uv.get(opposite[face])
                    if value:
                        u,v=value['uv']; du,dv=value['uv_size'];rectangles[face]=[u,v,u+du,v+dv]
            for face,(indices,normal) in faces.items():
                if face not in rectangles:
                    continue
                u0,v0,u1,v1=rectangles[face]
                texture=[[u1/width,v0/height],[u0/width,v0/height],[u0/width,v1/height],[u1/width,v1/height]]
                pairs=list(zip(indices,texture))
                if mirror and isinstance(uv,list):
                    pairs.reverse();normal=[-normal[0],normal[1],normal[2]]
                normal=apply(normal_pose,apply(matrix,normal,0),0)
                for index,texture in pairs:
                    position=apply(matrix,points[index]);position=apply(pose,[v/16 for v in position])
                    vertices.append([round(v,8) for v in [*position,*texture,*normal]])
    TARGET.parent.mkdir(parents=True,exist_ok=True)
    TARGET.write_text(json.dumps({'source':'jojo:geo/misc/mod_logo.geo.json','vertices':vertices},separators=(',',':'))+'\n')
    print(f'Preserved {len(vertices)//4} original logo quads.')

if __name__ == '__main__':
    main()
