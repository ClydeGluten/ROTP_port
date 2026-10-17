#!/usr/bin/env python3
"""Bake the original Tommy gun geometry using ParseGeckoModel's cube and UV rules."""
from pathlib import Path
import copy
import json
from generate_hamon_logo import identity, multiply, translation, scale, apply, compose_rotations

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/main/resources/assets/jojo/geo/misc/tommy_gun.geo.json'
TARGET = ROOT / 'fabric/src/main/resources/assets/jojo/geometry/tommy_gun_mesh.json'


def main():
    geometry = json.loads(SOURCE.read_text())['minecraft:geometry'][0]
    bones = {bone['name']: copy.deepcopy(bone) for bone in geometry['bones']}
    # ParseGeckoModel.convertRotated promotes rotated cubes to child bones.
    for bone in list(bones.values()):
        keep = []
        for index, cube in enumerate(bone.get('cubes', [])):
            if any(cube.get('rotation', [])):
                name = bone['name'] + '_r' + str(index)
                bones[name] = dict(name=name, parent=bone['name'], pivot=cube.get('pivot', [0, 0, 0]),
                                   rotation=cube.pop('rotation'), cubes=[cube])
            else:
                keep.append(cube)
        bone['cubes'] = keep
    width, height = geometry['description']['texture_width'], geometry['description']['texture_height']
    matrices = {}

    def bone_matrix(bone):
        if bone['name'] in matrices:
            return matrices[bone['name']]
        p = bone['pivot']
        if 'parent' in bone:
            parent = bones[bone['parent']]
            q = parent['pivot']
            matrix = multiply(bone_matrix(parent), translation(p[0]-q[0], -p[1]+q[1], p[2]-q[2]))
        else:
            matrix = translation(p[0], 24-p[1], p[2])
        matrices[bone['name']] = multiply(matrix, compose_rotations(bone.get('rotation', [0, 0, 0])))
        return matrices[bone['name']]

    # CustomModelItemISTER: scale(-1,-1,1), then translate(-.5,-1.5,.5).
    pose = multiply(scale(-1, -1, 1), translation(-.5, -1.5, .5))
    faces = {'down': ([5,4,0,1], [0,-1,0]), 'up': ([2,3,7,6], [0,1,0]),
             'west': ([0,4,7,3], [-1,0,0]), 'north': ([1,0,3,2], [0,0,-1]),
             'east': ([5,1,2,6], [1,0,0]), 'south': ([4,5,6,7], [0,0,1])}
    opposite = {'up':'down','down':'up','west':'east','east':'west','north':'north','south':'south'}
    output = {'source':'jojo:geo/misc/tommy_gun.geo.json','body':[],'fire':[]}
    for bone in bones.values():
        matrix = bone_matrix(bone)
        root = bone
        while 'parent' in root:
            root = bones[root['parent']]
        vertices = output['fire' if root['name'] == 'fire' else 'body']
        for cube in bone.get('cubes', []):
            p, o, size = bone['pivot'], cube['origin'], cube['size']
            inflate = cube.get('inflate', 0)
            x0,y0,z0 = o[0]-p[0]-inflate, -(o[1]-p[1])-size[1]-inflate, o[2]-p[2]-inflate
            x1,y1,z1 = x0+size[0]+2*inflate,y0+size[1]+2*inflate,z0+size[2]+2*inflate
            mirror = cube.get('mirror',False)
            uv = cube['uv']
            if mirror and isinstance(uv,list):
                x0,x1=x1,x0
            points=[[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]]
            if isinstance(uv,list):
                u,v=uv; sx,sy,sz=map(int,size)
                rectangles={'down':[u+sz,v,u+sz+sx,v+sz],'up':[u+sz+sx,v+sz,u+sz+sx*2,v],
                            'west':[u,v+sz,u+sz,v+sz+sy],'north':[u+sz,v+sz,u+sz+sx,v+sz+sy],
                            'east':[u+sz+sx,v+sz,u+sz*2+sx,v+sz+sy],'south':[u+sz*2+sx,v+sz,u+sz*2+sx*2,v+sz+sy]}
            else:
                rectangles={}
                for face in faces:
                    value=uv.get(opposite[face])
                    if value:
                        u,v=value['uv']; du,dv=value['uv_size']; rectangles[face]=[u,v,u+du,v+dv]
            for face,(indices,normal) in faces.items():
                if face not in rectangles:
                    continue
                u0,v0,u1,v1=rectangles[face]
                pairs=list(zip(indices,[[u1/width,v0/height],[u0/width,v0/height],[u0/width,v1/height],[u1/width,v1/height]]))
                if mirror and isinstance(uv,list):
                    pairs.reverse(); normal=[-normal[0],normal[1],normal[2]]
                normal=apply(pose,apply(matrix,normal,0),0)
                for index,texture in pairs:
                    position=apply(matrix,points[index]); position=apply(pose,[v/16 for v in position])
                    vertices.append([round(v,8) for v in [*position,*texture,*normal]])
    TARGET.parent.mkdir(parents=True,exist_ok=True)
    TARGET.write_text(json.dumps(output,separators=(',',':'))+'\n')
    print(f'Tommy gun: {len(output["body"])//4} body quads, {len(output["fire"])//4} fire quads')


if __name__ == '__main__':
    main()
