"""
곰 몸통 메시 단순화 → glb 내보내기 (Blender 5.x). 원본 .blend 는 수정·저장하지 않는다.

사용법:
  blender -b <pebble_bear_rigged.blend> -P tools/bear3d/decimate_bear.py -- <out.glb> [ratio=0.25]

- bear_body 에만 Decimate(Collapse) 적용. 뼈대 가중치·UV는 Blender가 보간해서 유지
- 눈·입(표정 morph), 코, 귀 부품, 애니메이션은 그대로
"""
import bpy
import sys

args = sys.argv[sys.argv.index("--") + 1:]
out = args[0]
ratio = float(args[1]) if len(args) > 1 else 0.25

body = bpy.data.objects["bear_body"]
before = (len(body.data.vertices), len(body.data.polygons))

bpy.ops.object.select_all(action="DESELECT")
bpy.context.view_layer.objects.active = body
body.select_set(True)
dec = body.modifiers.new("decimate", "DECIMATE")
dec.decimate_type = "COLLAPSE"
dec.ratio = ratio
dec.use_collapse_triangulate = False
# 뼈대(Armature) 변형보다 먼저 적용되도록 맨 앞으로
bpy.ops.object.modifier_move_to_index(modifier=dec.name, index=0)
bpy.ops.object.modifier_apply(modifier=dec.name)
after = (len(body.data.vertices), len(body.data.polygons))
print(f"DECIMATE verts {before[0]} -> {after[0]}, polys {before[1]} -> {after[1]}")

bpy.ops.export_scene.gltf(
    filepath=out,
    export_format="GLB",
    use_selection=False,
    export_cameras=False,
    export_lights=False,
    export_apply=False,          # 뼈대 모디파이어는 적용하지 않음 (리깅 유지)
    export_animations=True,
    export_animation_mode="ACTIONS",
    export_morph=True,
    export_skins=True,
    export_yup=True,
)
print("EXPORTED", out)
