"""
glb 여러 개를 같은 카메라·조명으로 렌더해 품질 비교 (Blender 5.x)
사용법: blender -b --factory-startup -P tools/bear3d/compare_render.py -- <out_dir> <a.glb> <b.glb> ...
결과: <out_dir>/<이름>_{full,face,wire}.png
"""
import bpy
import math
import os
import sys
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
out_dir, files = args[0], args[1:]
os.makedirs(out_dir, exist_ok=True)


def setup():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    try:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "OPTIX"
        prefs.get_devices()
        for d in prefs.devices:
            d.use = True
        sc.cycles.device = "GPU"
    except Exception:
        pass
    sc.cycles.samples = 64
    sc.cycles.use_denoising = True
    sc.render.resolution_x = sc.render.resolution_y = 900
    sc.view_settings.view_transform = "Standard"
    w = bpy.data.worlds.new("w")
    sc.world = w
    w.use_nodes = True
    w.node_tree.nodes["Background"].inputs[0].default_value = (0.93, 0.94, 0.96, 1)
    w.node_tree.nodes["Background"].inputs[1].default_value = 0.9
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sc.collection.objects.link(sun)
    sun.rotation_euler = (math.radians(50), 0, math.radians(-30))
    sun.data.energy = 2.2
    return sc


def cam(sc, name, loc, target, lens):
    c = bpy.data.objects.new(name, bpy.data.cameras.new(name))
    sc.collection.objects.link(c)
    c.data.lens = lens
    c.location = loc
    c.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    return c


for path in files:
    sc = setup()
    bpy.ops.import_scene.gltf(filepath=path)
    for o in list(sc.objects):
        if o.name.startswith("Icosphere"):
            bpy.data.objects.remove(o)
    name = os.path.splitext(os.path.basename(path))[0]
    shots = {
        "full": cam(sc, "full", (0.35, -2.6, 0.62), (0, 0, 0.5), 50),
        "face": cam(sc, "face", (0.55, -1.05, 0.86), (0, -0.1, 0.70), 85),   # 볼·귀 실루엣 확대
    }
    for key, c in shots.items():
        sc.camera = c
        sc.render.filepath = os.path.join(out_dir, f"{name}_{key}.png")
        bpy.ops.render.render(write_still=True)
    # 와이어프레임: 몸통에 Wireframe 모디파이어 + 검은 재질 복제본 겹치기
    body = bpy.data.objects.get("bear_body")
    if body:
        wire = body.copy()
        wire.data = body.data.copy()
        sc.collection.objects.link(wire)
        m = wire.modifiers.new("wire", "WIREFRAME")
        m.thickness = 0.0012
        m.use_replace = True
        mat = bpy.data.materials.new("wire")
        mat.use_nodes = True
        mat.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.02, 0.02, 0.02, 1)
        wire.data.materials.clear()
        wire.data.materials.append(mat)
        sc.camera = shots["face"]
        sc.render.filepath = os.path.join(out_dir, f"{name}_wire.png")
        bpy.ops.render.render(write_still=True)
    print("RENDERED", name)
