"""
카곰 치장 아이템 3D 모델링 + 렌더 (Blender 5.x, Cycles)

곰 모델(pebble_bear.glb)에 맞춰 치장 아이템 6종과 칭호 메달 3종을 절차적으로 만들고,
앱이 겹쳐 그릴 수 있게 같은 카메라로 렌더한다.

  base_<P0n>_<표정>.webp          스킨 × 표정 기본 캐릭터 (아이템 없음)        614×965
  item_<ITEM>.webp                아이템 레이어. 곰은 그림자만 받는 투명 처리  614×965 (기본 렌더와 같은 카메라)
  thumb_<ITEM>.webp               아이템·칭호 썸네일 (곰 없음)                320×320
  turn/b_<P0n>_<단>_<nn>.webp     돌려보기 몸 (고도 단 × 48방향, 7.5° 간격)     480×754
  turn/i_<ITEM>_<단>_<nn>.webp    돌려보기 아이템 레이어
  motion/<동작>/b_<P0n>_<nn>.webp  터치 반응 동작 프레임 (24 fps, 대기 표정)     480×754 (돌려보기 0단 HOME_YAW 시점)
  motion/<동작>/i_<ITEM>_<nn>.webp 터치 반응 아이템 레이어 (옷이 뼈를 따라 움직인다)

앱은 base 위에 하의 → 상의 → 머리 순서로 item 레이어를 겹친다 (ui/components/KagomCharacter.kt).
base·item은 돌려보기 카메라의 (0단, HOME_YAW) 시점이라 홈에서 밀기 시작하면 돌려보기 프레임으로 그대로 이어진다.

사용법 (모델·텍스처 원본은 tools/closet3d/source/. 경로는 절대 경로로 — Blender는 상대 경로를 C:\\ 기준으로 푼다):
  blender -b --factory-startup -P tools/closet3d/build_closet.py -- \
      --glb <절대경로>/tools/closet3d/source/pebble_bear.glb --skins <절대경로>/tools/closet3d/source/skins \
      --font <절대경로>/app/src/main/res/font/asta_sans.ttf \
      --out <절대경로>/app/src/main/assets/kagom [--what all|base|items|thumbs|turn|motion|preview|motion_preview|clipcheck] \
      [--rows 0,1,2] [--codes P01,P02] [--acts wave,cheer,stomp] [--preview ITM-H-01,ITM-T-02,ITM-B-01]

  --what motion          터치 반응 동작(MOTIONS). 오래 걸리니 --acts·--codes로 나눠 동시에 돌리고, 멈추면 --skip-existing
  --what motion_preview  동작을 곰+옷 진짜 3D로 몇 장씩 찍어 <out>/motion_preview/에 저장 (확인용, 앱에 넣지 않는다)

  --what clipcheck  옷이 몸을 뚫는지 점검: 아이템 정점이 몸 안에 들어간 양을 출력하고(CLIP ...),
                    진짜 3D(곰+옷 함께) / 앱식 합성 / 차이를 8방향으로 나란히 clip_<키>.png에 저장 (--rows로 고도 단 선택)

좌표: 곰 키 1.0, 위 +Z, 정면 -Y (glb 기준).
"""
import argparse
import math
import os
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument("--glb", required=True)
ap.add_argument("--skins", required=True)
ap.add_argument("--font", required=True)
ap.add_argument("--out", required=True)
ap.add_argument("--what", default="all")
ap.add_argument("--preview", default="ITM-H-01,ITM-T-02,ITM-B-01")
ap.add_argument("--samples", type=int, default=96)
ap.add_argument("--turn-samples", type=int, default=40)
ap.add_argument("--codes", default="")              # 스킨 일부만 (예: P01,P02)
ap.add_argument("--turn-items", type=int, default=1)   # 0이면 돌려보기 아이템 레이어 생략
ap.add_argument("--rows", default="")               # 돌려보기 고도 단 일부만 (예: 0 또는 1,2)
ap.add_argument("--skip-existing", action="store_true")   # 이미 있는 파일은 건너뛴다 (멈춘 렌더 이어 하기)
ap.add_argument("--exprs", default="")              # 표정 일부만 (예: blink)
ap.add_argument("--acts", default="")               # 터치 반응 동작 일부만 (예: wave,stomp)
A = ap.parse_args(argv)
os.makedirs(A.out, exist_ok=True)

W, H = 614, 965          # Figma kagomCharacter 렌더 크기
THUMB = 320
TW, TH = 480, 754        # 돌려보기 프레임
TURN_N = 48              # 7.5° 간격. 24(15°)에서 늘렸다: 예전 i번 프레임 = 지금 2i번 (카메라 거리·위치는 같다)
# 돌려보기 고도 단 (도): 0단 = 예전 시점(약 9°), 1·2단 = 위에서 내려다보기 (머리 위 아이템 확인용)
TURN_ELEV = [math.degrees(math.atan(0.16)), 25.0, 40.0]
HOME_YAW = 2             # 홈·보상의 정지 화면 = 0단 2번 프레임 (곰 왼쪽으로 15°). 앱 KagomRenders.HOME_YAW와 같아야 한다

# 터치 반응 (홈, 기어 P + 대기 표정): 이름 → (glb 동작, 없으면 이 스크립트가 만든 동작; 프레임 수 @24 fps).
# 첫·끝 프레임은 기본 자세라 앱에서 기본 렌더와 이어진다. 이름은 앱 TouchAct와 같아야 한다
# 동작 프레임 크기: 팔을 벌리거나 뛰어오르면 돌려보기 화면(480×754) 밖으로 나가서, 같은 카메라·같은 픽셀 크기로
# 좌우 80 px, 위 76 px를 넓혀 찍는다 (곰 위치는 돌려보기 프레임과 같다. 앱 KagomRenders.MOTION_* 와 같아야 한다)
MW, MH = 640, 830
MOTIONS = {
    "wave": ("wave", 38),     # 왼팔 들어 손 흔들기 + 고개 갸웃
    "cheer": ("happy", 22),   # 두 팔 만세 + 깡충 + 귀 쫑긋
    "stomp": (None, 28),      # 발 동동: 왼발·오른발 번갈아 들고 몸·고개·팔이 같이 흔들린다
}

# 스킨: 텍스처, 금속성, 거칠기, 귀 안쪽, 귀 테두리 (spec 3장 + 이전 BearSkin.kt)
SKINS = {
    "P01": ("classic_beige", 0.0, 0.70, "EADDC6", "C2AB8B"),
    "P02": ("matte_mint", 0.0, 0.70, "CFEDE2", "A0CDBE"),
    "P03": ("pastel_lavender", 0.0, 0.70, "EEDDBF", "AA93D2"),
    "P04": ("obsidian_black", 0.0, 0.55, "4E5159", "2C2E33"),
    "P05": ("polar_white", 0.0, 0.70, "DDE1E7", "CDD1D7"),
    "P06": ("chocolate_brown", 0.0, 0.70, "C49A70", "6A3F26"),
    "P07": ("frosty_jelly", 0.0, 0.25, "A8EEDD", "63C9B2"),
    "P08": ("metallic_rose_gold", 0.85, 0.35, "EBC6B8", "C08878"),
}

# 뼈 위치 (glb 조사 결과). 팔은 손끝까지 덮도록 뼈 끝을 조금 늘려 둔다
ARMS = [((0.205, -0.01, 0.48), (0.345, -0.032, 0.235)), ((-0.205, -0.01, 0.48), (-0.345, -0.032, 0.235))]
TAIL = ((0.0, 0.204, 0.199), (0.0, 0.415, 0.282))
BEAR_PARTS = ["bear_body", "ear_inner_L", "ear_inner_R", "eyes", "mouth", "nose"]


# ───────────────────────── 장면 ─────────────────────────

def lin(hexstr, a=1.0):
    def ch(i):
        c = int(hexstr[i:i + 2], 16) / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    return (ch(0), ch(2), ch(4), a)


def setup():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    try:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        for kind in ("OPTIX", "CUDA", "HIP", "ONEAPI"):
            try:
                prefs.compute_device_type = kind
                prefs.get_devices()
                gpus = [d for d in prefs.devices if d.type != "CPU"]
                if gpus:
                    for d in prefs.devices:
                        d.use = d.type != "CPU"
                    sc.cycles.device = "GPU"
                    print("DEVICE", kind, [d.name for d in gpus])
                    break
            except Exception:
                continue
    except Exception as e:
        print("GPU 없음 → CPU", e)
    sc.cycles.samples = A.samples
    sc.cycles.use_denoising = True
    # 렌더가 CPU에 묶여 있었다 (GPU 20%): 노이즈 제거(같은 OIDN)를 GPU로, 렌더 사이에 장면 데이터를 다시 만들지 않게
    try:
        sc.cycles.denoising_use_gpu = True
    except AttributeError:
        pass
    sc.render.use_persistent_data = True
    sc.render.film_transparent = True
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    sc.render.image_settings.file_format = "WEBP"
    sc.render.image_settings.color_mode = "RGBA"
    sc.render.image_settings.quality = 92

    w = bpy.data.worlds.new("w")
    sc.world = w
    w.use_nodes = True
    bg = w.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.95, 0.96, 0.98, 1)
    bg.inputs[1].default_value = 0.55

    def area(name, loc, energy, size, color=(1, 1, 1)):
        l = bpy.data.lights.new(name, "AREA")
        l.energy, l.size, l.color = energy, size, color
        o = bpy.data.objects.new(name, l)
        sc.collection.objects.link(o)
        o.location = loc
        o.rotation_euler = (Vector((0, 0, 0.55)) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    area("key", (1.6, -2.2, 2.4), 70, 2.2)
    area("fill", (-2.2, -1.6, 1.0), 25, 2.5, (0.96, 0.98, 1.0))
    area("rim", (0.6, 2.4, 2.0), 40, 1.5)

    bpy.ops.import_scene.gltf(filepath=A.glb)
    for o in list(sc.objects):
        if o.name.startswith("Icosphere"):
            bpy.data.objects.remove(o)
    return sc


def mat(name, hexcol, rough=0.6, metal=0.0, coat=0.0, emit=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = lin(hexcol)
    p.inputs["Roughness"].default_value = rough
    p.inputs["Metallic"].default_value = metal
    if coat:
        p.inputs["Coat Weight"].default_value = coat
    if emit:
        p.inputs["Emission Color"].default_value = lin(hexcol)
        p.inputs["Emission Strength"].default_value = emit
    return m


def band_mat(name, base, bands, rough=0.55):
    """월드 Z 높이로 가로 줄무늬를 넣는 재질. bands = [(z0, z1, hex), ...]"""
    m = mat(name, base, rough)
    nt = m.node_tree
    p = nt.nodes["Principled BSDF"]
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.interpolation = "CONSTANT"
    els = ramp.color_ramp.elements
    els[0].position, els[0].color = 0.0, lin(base)
    els[1].position, els[1].color = 0.999, lin(base)
    for z0, z1, col in bands:
        e = els.new(z0); e.color = lin(col)
        e = els.new(z1); e.color = lin(base)
    nt.links.new(geo.outputs["Position"], sep.inputs[0])
    nt.links.new(sep.outputs["Z"], ramp.inputs[0])
    nt.links.new(ramp.outputs["Color"], p.inputs["Base Color"])
    return m


def link(o):
    bpy.context.scene.collection.objects.link(o)
    return o


# ───────────────────────── 곰 표면 ─────────────────────────

def body_bm():
    body = bpy.data.objects["bear_body"]
    dg = bpy.context.evaluated_depsgraph_get()
    ev = body.evaluated_get(dg)
    me = ev.to_mesh()
    bm = bmesh.new()
    bm.from_mesh(me)
    ev.to_mesh_clear()
    bm.transform(body.matrix_world)
    # glTF는 UV 이음새에서 정점을 나눠 둔다 → 합쳐야 띄울 때 옷이 갈라지지 않는다
    bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=1e-5)
    bm.normal_update()
    return bm


BM = None
BVH = None


def surface(p, off=0.0):
    """p에서 가장 가까운 곰 표면 점 + 법선 방향으로 off만큼"""
    loc, nor, _, _ = BVH.find_nearest(Vector(p))
    return loc + nor * off, nor


def depth(p):
    """곰 표면까지의 부호 거리 (음수 = 몸 안, 팔·손 안도 포함)"""
    loc, nor, _, _ = BVH.find_nearest(Vector(p))
    return (Vector(p) - loc).dot(nor)


def surface_path(keys, off, step=0.02):
    """
    keys를 잇는 선을 step 간격으로 쪼개 하나하나 표면에 붙인 점들.
    점 몇 개만 이으면 볼록한 어깨 위에서 곡선이 몸을 가로질러 들어가 버린다 (멜빵이 어깨에 묻히던 문제).
    """
    keys = [Vector(k) for k in keys]
    pts = []
    for a, b in zip(keys, keys[1:]):
        n = max(1, int((b - a).length / step))
        pts += [a.lerp(b, k / n) for k in range(n)]
    pts.append(keys[-1])
    # 표면에 바로 붙이면 울퉁불퉁한 몸을 따라 끈이 구불거린다 → 밀어내기와 다듬기를 번갈아 해서 매끈하게 감는다
    for it in range(40):
        pts = [surface(p, off)[0] if depth(p) < off * 0.9 else p for p in pts]
        if it < 39:
            pts = [pts[0]] + [pts[j] * 0.5 + (pts[j - 1] + pts[j + 1]) * 0.25 for j in range(1, len(pts) - 1)] + [pts[-1]]
    return pts


def ray_in_plane(center, direction, off):
    """center 바깥에서 center 쪽으로 쏴서 처음 닿는 표면 (같은 평면 안)"""
    d = Vector(direction).normalized()
    origin = Vector(center) + d * 0.8
    loc, nor, _, _ = BVH.ray_cast(origin, -d)
    return (loc + nor * off, nor) if loc else (None, None)


def seg_dist(p, a, b):
    a, b = Vector(a), Vector(b)
    ab = b - a
    t = max(0.0, min(1.0, (p - a).dot(ab) / ab.length_squared))
    return (a + ab * t - p).length


def away_from_limbs(c, arm=0.1, tail=0.1):
    return all(seg_dist(c, *s) > arm for s in ARMS) and seg_dist(c, *TAIL) > tail


CLEAR = 0.002            # 옷과 몸 사이 최소 틈 (2 mm)
TORSO = lambda z: (0.0, -0.01)
LEG_L = lambda z: (0.1, -0.01)
LEG_R = lambda z: (-0.1, -0.01)


def wrap(name, axis, z0, z1, th0, th1, offset, thick, material, valid=lambda p: True, max_r=0.45, segs=180, dz=0.005, arm=0.085):
    """
    몸 안쪽 축(axis)에서 바깥으로 광선을 쏴 표면에 닿은 점들로 옷 격자를 만든다.
    가로줄 = 수평 원이라 밑단·허리선이 깨끗하다. th: 각도(도), 0 = 정면(-Y), +는 곰의 왼쪽(+X).
    팔·꼬리에 닿은 점(valid=False)이 들어간 칸은 비운다 → 팔 구멍.
    """
    closed = (th1 - th0) >= 360
    n_th = segs if closed else max(2, int(segs * (th1 - th0) / 360) + 1)
    n_z = max(2, int((z1 - z0) / dz) + 1)
    bm = bmesh.new()
    grid = []
    for i in range(n_z):
        z = z0 + (z1 - z0) * i / (n_z - 1)
        ax, ay = axis(z)
        row = []
        for j in range(n_th):
            t = math.radians(th0 + (th1 - th0) * j / (n_th if closed else (n_th - 1)))
            d = Vector((math.sin(t), -math.cos(t), 0))
            o = Vector((ax, ay, z))
            hit, nor, _, dist = BVH.ray_cast(o, d)
            if hit is None or dist > max_r or not valid(hit) or not away_from_limbs(hit, arm=arm, tail=0.09):
                row.append(None)
                continue
            n = (nor.normalized() + d).normalized()
            # 옷 안·바깥면이 팔·손 안으로 들어가면 비운다 → 손이 옷 위에 얹힌 것처럼 보인다 (손이 옷을 뚫던 문제)
            if depth(hit + n * offset) < CLEAR or depth(hit + n * (offset + thick)) < CLEAR:
                row.append(None)
                continue
            row.append(bm.verts.new(hit + n * offset))
        grid.append(row)
    cols = n_th if closed else n_th - 1
    for i in range(n_z - 1):
        for j in range(cols):
            jj = (j + 1) % n_th
            q = [grid[i][j], grid[i][jj], grid[i + 1][jj], grid[i + 1][j]]
            if all(q):
                bm.faces.new(q)
    # 떨어져 나온 작은 조각(팔 구멍 근처 등) 지우기
    seen, parts = set(), []
    for f in bm.faces:
        if f in seen:
            continue
        comp, stack = [], [f]
        seen.add(f)
        while stack:
            g = stack.pop()
            comp.append(g)
            for e in g.edges:
                for h in e.link_faces:
                    if h not in seen:
                        seen.add(h)
                        stack.append(h)
        parts.append(comp)
    total = sum(len(p) for p in parts)
    bmesh.ops.delete(bm, geom=[f for p in parts if len(p) < total * 0.05 for f in p], context="FACES")
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context="VERTS")
    # 팔 구멍 가장자리의 톱니(한 칸짜리 돌출) 깎기: 열린 변이 2개 이상인 칸을 두 번 지운다
    for _ in range(2):
        teeth = [f for f in bm.faces if sum(e.is_boundary for e in f.edges) >= 2]
        bmesh.ops.delete(bm, geom=teeth, context="FACES")
        bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context="VERTS")
    edge_verts = [v for v in bm.verts if v.is_boundary]
    for _ in range(12):
        moved = {}
        for v in edge_verts:
            nb = [e.other_vert(v).co for e in v.link_edges if e.is_boundary]
            if len(nb) == 2:
                moved[v] = v.co * 0.5 + (nb[0] + nb[1]) * 0.25
        for v, co in moved.items():
            v.co = co
    # 다듬느라 몸 쪽으로 끌려 들어간 가장자리를 같은 축 광선으로 다시 표면 위(offset)에 올린다
    for v in edge_verts:
        if not v.is_valid:
            continue
        ax, ay = axis(v.co.z)
        o = Vector((ax, ay, v.co.z))
        d = Vector((v.co.x - ax, v.co.y - ay, 0))
        if d.length < 1e-6:
            continue
        d.normalize()
        hit, nor, _, _ = BVH.ray_cast(o, d)
        if hit is None:
            continue
        n = (nor.normalized() + d).normalized()
        p = hit + n * offset
        if depth(p) >= CLEAR and depth(hit + n * (offset + thick)) >= CLEAR:
            v.co = p
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for poly in me.polygons:
        poly.use_smooth = True
    o = link(bpy.data.objects.new(name, me))
    s = o.modifiers.new("solid", "SOLIDIFY")
    s.thickness, s.offset, s.use_even_offset, s.use_rim = thick, 1.0, False, True
    o.data.materials.append(material)
    return o


def tube(name, pts, radius, material, cyclic=False):
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth, cu.bevel_resolution, cu.use_fill_caps = radius, 6, True
    sp = cu.splines.new("BEZIER")
    sp.bezier_points.add(len(pts) - 1)
    for bp, p in zip(sp.bezier_points, pts):
        bp.co = Vector(p)
        bp.handle_left_type = bp.handle_right_type = "AUTO"
    sp.use_cyclic_u = cyclic
    o = link(bpy.data.objects.new(name, cu))
    o.data.materials.append(material)
    return o


def ellipsoid(name, loc, scale, material, rot=(0, 0, 0), segs=48):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=segs, v_segments=segs // 2, radius=1.0)
    bm.to_mesh(me)
    bm.free()
    for poly in me.polygons:
        poly.use_smooth = True
    o = link(bpy.data.objects.new(name, me))
    o.location, o.scale, o.rotation_euler = loc, scale, rot
    o.data.materials.append(material)
    return o


def facing(o, normal):
    """오브젝트 로컬 +Z가 normal을 향하게"""
    o.rotation_euler = Vector(normal).to_track_quat("Z", "Y").to_euler()


def rounded_box(name, size, material, bevel=0.01):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bm.to_mesh(me)
    bm.free()
    o = link(bpy.data.objects.new(name, me))
    o.scale = size
    b = o.modifiers.new("bevel", "BEVEL")
    b.width, b.segments, b.affect = bevel, 4, "EDGES"
    o.data.materials.append(material)
    for poly in me.polygons:
        poly.use_smooth = True
    return o


# ───────────────────────── 아이템 ─────────────────────────

def item_sunrise_headband():
    """ITM-H-01 해돋이 머리띠: 귀 앞을 지나는 머리띠 + 위에 반쪽 해와 햇살"""
    band = mat("hb_band", "FF6F59", 0.45, coat=0.3)
    sun = mat("hb_sun", "FFC23D", 0.35, emit=0.25)
    ray = mat("hb_ray", "FF9A3C", 0.4, emit=0.15)
    y0, cz = -0.12, 0.74
    pts = []
    for deg in range(-74, 75, 6):
        t = math.radians(deg)
        # 띄움 = 관 반지름 (0.012였을 때 머리에 5 mm 묻혔다)
        p, _ = ray_in_plane((0, y0, cz), (math.sin(t), 0, math.cos(t)), 0.019)
        if p:
            pts.append(p)
    objs = [tube("hb_band", pts, 0.017, band)]
    top, nor = ray_in_plane((0, y0, cz), (0, 0, 1), 0.019)
    c = top + Vector((0, -0.005, 0.03))
    # 반쪽 원판 (정면을 향함)
    me = bpy.data.meshes.new("hb_sun")
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=48, radius1=0.068, radius2=0.068, depth=0.024)
    bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=(0, 0, 0), plane_no=(0, -1, 0), clear_outer=True)
    bmesh.ops.contextual_create(bm, geom=[e for e in bm.edges if e.is_boundary])
    bm.to_mesh(me)
    bm.free()
    o = link(bpy.data.objects.new("hb_sun", me))
    o.location = c
    o.rotation_euler = (math.radians(90), 0, 0)
    o.data.materials.append(sun)
    bv = o.modifiers.new("bevel", "BEVEL"); bv.width, bv.segments = 0.006, 3
    objs.append(o)
    for i in range(7):
        a = math.radians(10 + i * 160 / 6)
        d = Vector((math.cos(a), 0, math.sin(a)))
        objs.append(ellipsoid(f"hb_ray{i}", c + d * 0.096, (0.009, 0.009, 0.022), ray,
                              rot=Vector((0, 0, 1)).rotation_difference(d).to_euler()))
    return objs


def item_coffee_bean_cap():
    """ITM-H-02 커피콩 모자: 머리 위 커피콩 + 가운데 홈"""
    bean = mat("cb_bean", "6B3B22", 0.32, coat=0.6)
    crease = mat("cb_crease", "2B160B", 0.5)
    top, _ = ray_in_plane((0, -0.02, 0.74), (0, 0, 1), 0.0)
    loc = top + Vector((0, -0.01, 0.018))
    rot = (math.radians(-8), 0, math.radians(18))
    b = ellipsoid("cb_bean", loc, (0.13, 0.092, 0.058), bean, rot)
    bpy.context.view_layer.update()
    m = b.matrix_world
    # 콩 표면(단위 구) 위의 S자 홈
    pts = []
    for i in range(13):
        u = -0.78 + 1.56 * i / 12
        x, y = u, 0.2 * math.sin(math.pi * u / 0.78)
        pts.append(m @ Vector((x, y, math.sqrt(max(0.0, 1 - x * x - y * y)) * 0.985)))
    c = tube("cb_crease", pts, 0.0075, crease)
    # 챙 대신 머리에 닿는 부분을 살짝 감싸는 띠
    ring = mat("cb_ring", "4A2814", 0.5)
    r = ellipsoid("cb_ring", loc + Vector((0, 0, -0.03)), (0.122, 0.088, 0.018), ring, rot)
    return [b, c, r]


def item_red_apron():
    """ITM-T-01 빨간 고무 앞치마: 앞판 + 주머니 + 목·허리 끈 (광택 고무)"""
    red = mat("ap_red", "D7332B", 0.25, coat=0.7)
    dark = mat("ap_pocket", "B8261F", 0.3, coat=0.6)
    strap = mat("ap_strap", "C42A23", 0.35, coat=0.4)
    o = wrap("ap_front", TORSO, 0.14, 0.47, -52, 52, 0.016, 0.01, red)
    pocket = wrap("ap_pocket", TORSO, 0.21, 0.30, -22, 22, 0.03, 0.006, dark)
    objs = [o, pocket]
    # 허리 끈: 등 뒤로 돈다
    pts = []
    for deg in range(-64, 65, 4):
        t = math.radians(deg)
        p, _ = ray_in_plane((0, 0, 0.30), (math.sin(t), math.cos(t), 0), 0.02)
        if p and away_from_limbs(p, arm=0.07):
            pts.append(p)
    if len(pts) > 2:
        objs.append(tube("ap_waist", pts, 0.009, strap))
    # 목 끈: 앞판 위 모서리에서 턱 밑으로
    for sx in (1, -1):
        a, _ = surface((0.17 * sx, -0.2, 0.47), 0.02)
        b, _ = surface((0.11 * sx, -0.14, 0.505), 0.015)
        cpt, _ = surface((0.05 * sx, -0.08, 0.52), 0.01)
        objs.append(tube(f"ap_neck{sx}", [a, b, cpt], 0.008, strap))
    return objs


def item_safety_vest():
    """ITM-T-02 초보운전 안전 조끼: 형광 조끼 + 반사띠 2줄 + "초보" 배지"""
    neon = mat("sv_neon", "D9F23A", 0.55, emit=0.08)
    refl = mat("sv_refl", "C9CDD3", 0.25, metal=0.6)
    # 앞이 트인 조끼 (정면 ±9° 비움)
    objs = [wrap("sv_vest", TORSO, 0.17, 0.505, 9, 351, 0.014, 0.011, neon)]
    for i, (z0, z1) in enumerate([(0.255, 0.285), (0.35, 0.38)]):
        objs.append(wrap(f"sv_stripe{i}", TORSO, z0, z1, 11, 349, 0.027, 0.004, refl))
    # 왼쪽 옆구리 배지 (카메라 쪽, 반사띠 사이 · 멜빵 앞판 바깥)
    p, n = surface((0.175, -0.21, 0.318), 0.03)
    plate = rounded_box("sv_badge", (0.074, 0.044, 0.008), mat("sv_badge", "FFFFFF", 0.4), 0.008)
    plate.location = p
    facing(plate, n)
    font = bpy.data.fonts.load(A.font)
    cu = bpy.data.curves.new("sv_text", "FONT")
    cu.body, cu.font, cu.size, cu.extrude, cu.align_x, cu.align_y = "초보", font, 0.029, 0.0015, "CENTER", "CENTER"
    t = link(bpy.data.objects.new("sv_text", cu))
    t.data.materials.append(mat("sv_text", "2E9E44", 0.5))
    t.location = p + n * 0.006
    facing(t, n)
    objs += [plate, t]
    return objs


def item_denim_overalls():
    """ITM-B-01 데님 멜빵 반바지: 반바지 + 앞판 + 멜빵 + 단추 + 접은 밑단"""
    denim = mat("dn_denim", "3D6CA8", 0.88)
    cuff = mat("dn_cuff", "5C88C0", 0.85)
    gold = mat("dn_button", "D8B25A", 0.3, metal=0.9)
    # 엉덩이 통 + 다리 통 2개 (다리는 z≈0.12 아래에서 갈라진다)
    objs = [wrap("dn_hips", TORSO, 0.125, 0.30, 0, 360, 0.014, 0.012, denim, arm=0.0),
            wrap("dn_bib", TORSO, 0.29, 0.43, -30, 30, 0.014, 0.012, denim)]
    for side, axis in (("L", LEG_L), ("R", LEG_R)):
        objs.append(wrap(f"dn_leg{side}", axis, 0.075, 0.135, 0, 360, 0.014, 0.012, denim, max_r=0.16))
        objs.append(wrap(f"dn_cuff{side}", axis, 0.075, 0.103, 0, 360, 0.024, 0.01, cuff, max_r=0.16))
    for sx in (1, -1):
        pts = surface_path([(0.1 * sx, -0.24, 0.425), (0.14 * sx, -0.16, 0.49), (0.15 * sx, 0.0, 0.52),
                            (0.13 * sx, 0.16, 0.47), (0.1 * sx, 0.22, 0.38), (0.09 * sx, 0.23, 0.30)], 0.026)
        objs.append(tube(f"dn_strap{sx}", pts, 0.011, denim))
        p, n = surface((0.1 * sx, -0.24, 0.41), 0.036)
        btn = ellipsoid(f"dn_btn{sx}", p, (0.016, 0.016, 0.006), gold)
        facing(btn, n)
        objs.append(btn)
    return objs


def item_board_shorts():
    """ITM-B-02 강릉 서퍼 보드숏: 청록 반바지 + 밑단 줄무늬 + 허리띠 + 끈"""
    teal = band_mat("bs_teal", "1FA59B", [(0.062, 0.088, "FF7F50"), (0.088, 0.104, "FFF3D6"), (0.15, 0.162, "FFF3D6")])
    navy = mat("bs_waist", "1E3A5F", 0.6)
    white = mat("bs_cord", "FFFFFF", 0.5)
    objs = [wrap("bs_hips", TORSO, 0.125, 0.29, 0, 360, 0.016, 0.012, teal, arm=0.0),
            wrap("bs_waist", TORSO, 0.262, 0.292, 0, 360, 0.026, 0.01, navy, arm=0.0)]
    for side, axis in (("L", LEG_L), ("R", LEG_R)):
        objs.append(wrap(f"bs_leg{side}", axis, 0.052, 0.135, 0, 360, 0.016, 0.012, teal, max_r=0.16))
    for sx in (1, -1):
        a, _ = surface((0.014 * sx, -0.25, 0.275), 0.034)
        b = a + Vector((0.01 * sx, -0.012, -0.045))
        cpt = b + Vector((0.004 * sx, -0.004, -0.03))
        objs.append(tube(f"bs_cord{sx}", [a, b, cpt], 0.0045, white))
    return objs


def medal(key, ribbon_hex):
    """칭호 메달 (썸네일 전용): 금색 원판 + 별 + 리본 꼬리"""
    gold = mat(f"md_gold_{key}", "E8B84A", 0.28, metal=0.9)
    star_m = mat(f"md_star_{key}", "FFF3C4", 0.3, metal=0.4)
    rib = mat(f"md_rib_{key}", ribbon_hex, 0.55)
    base = Vector((5, 0, 0))   # 곰과 겹치지 않는 곳
    disc = link(bpy.data.objects.new(f"md_disc_{key}", bpy.data.meshes.new(f"md_disc_{key}")))
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=64, radius1=0.1, radius2=0.1, depth=0.022)
    bm.to_mesh(disc.data)
    bm.free()
    disc.location, disc.rotation_euler = base, (math.radians(90), 0, 0)
    disc.data.materials.append(gold)
    bv = disc.modifiers.new("bevel", "BEVEL"); bv.width, bv.segments = 0.008, 4
    # 별
    star = link(bpy.data.objects.new(f"md_star_{key}", bpy.data.meshes.new(f"md_star_{key}")))
    bm = bmesh.new()
    vs = [bm.verts.new((math.cos(math.radians(90 + i * 36)) * (0.058 if i % 2 == 0 else 0.025),
                        math.sin(math.radians(90 + i * 36)) * (0.058 if i % 2 == 0 else 0.025), 0)) for i in range(10)]
    f = bm.faces.new(vs)
    ext = bmesh.ops.extrude_face_region(bm, geom=[f])
    for v in [e for e in ext["geom"] if isinstance(e, bmesh.types.BMVert)]:
        v.co.z += 0.012
    bm.to_mesh(star.data)
    bm.free()
    star.location, star.rotation_euler = base + Vector((0, -0.012, 0)), (math.radians(90), 0, 0)
    star.data.materials.append(star_m)
    objs = [disc, star]
    for sx in (1, -1):
        r = rounded_box(f"md_rib{sx}_{key}", (0.05, 0.008, 0.13), rib, 0.004)
        r.location = base + Vector((0.035 * sx, 0.02, -0.11))
        r.rotation_euler = (0, math.radians(-14 * sx), 0)
        objs.append(r)
    return objs


# ───────────────────────── 표정 ─────────────────────────

def teardrop(name, loc, size, material, tilt=(0, 0, 0)):
    """끝이 위로 뾰족한 물방울 (땀·침)"""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=32, v_segments=16, radius=1.0)
    for v in bm.verts:
        if v.co.z > 0:
            k = (1 - v.co.z) ** 1.3
            v.co.x *= k
            v.co.y *= k
            v.co.z *= 1.7
    bm.to_mesh(me)
    bm.free()
    for poly in me.polygons:
        poly.use_smooth = True
    o = link(bpy.data.objects.new(name, me))
    o.location, o.scale, o.rotation_euler = loc, size, tilt
    o.data.materials.append(material)
    return o


def decal(name, center, radius, material, squash=0.8, lift=0.004, thick=0.005, spin=0.0):
    """표면에 납작하게 붙은 타원 (얼룩·홍조)"""
    p, n = surface(center, lift)
    o = ellipsoid(name, p, (radius, radius * squash, thick), material, segs=32)
    facing(o, n)
    o.rotation_euler.rotate_axis("Z", spin)
    return o


def zletter(name, loc, size, material, font):
    cu = bpy.data.curves.new(name, "FONT")
    cu.body, cu.font, cu.size, cu.extrude, cu.align_x, cu.align_y = "Z", font, size, size * 0.12, "CENTER", "CENTER"
    cu.bevel_depth = size * 0.03
    o = link(bpy.data.objects.new(name, cu))
    o.data.materials.append(material)
    o.location = loc
    o.rotation_euler = (math.radians(90), math.radians(-12), math.radians(8))
    return o


def build_expressions():
    """
    표정 = 눈·입 셰이프 키 + 소품. 몸·머리 자세는 바꾸지 않는다 (치장 레이어가 그대로 맞도록).
    반환: {표정 키: (셰이프 키 값, 소품 목록)}
    """
    font = bpy.data.fonts.load(A.font)
    sweat_m = mat("fx_sweat", "A8DDF7", 0.05, coat=1.0)
    drool_m = mat("fx_drool", "D6F1FF", 0.05, coat=1.0)
    oil_m = mat("fx_oil", "3A2A1F", 0.18, coat=0.9)
    oil2_m = mat("fx_oil2", "5A4232", 0.3, coat=0.6)
    z_m = mat("fx_z", "6F7DB8", 0.4)
    blush_m = mat("fx_blush", "FF6B6B", 0.6)
    p = blush_m.node_tree.nodes["Principled BSDF"]
    p.inputs["Alpha"].default_value = 0.32
    tape_m = mat("fx_tape", "F2C59B", 0.55)
    pad_m = mat("fx_pad", "FFF3E4", 0.6)

    def drool(name, scale):
        return teardrop(name, (0.036, -0.297, 0.62 - scale * 0.6), (scale * 0.55, scale * 0.45, scale), drool_m)

    def sweat(name):
        s, n = surface((0.235, -0.2, 0.73), 0.03)
        return teardrop(name, s, (0.016, 0.012, 0.026), sweat_m, tilt=(0, math.radians(-14), 0))

    def oil_spot(prefix, c, r):
        return [decal(f"{prefix}_a", c, r, oil_m, 0.75, spin=0.4),
                decal(f"{prefix}_b", (c[0] + r * 0.9, c[1], c[2] - r * 0.5), r * 0.45, oil_m, 0.9, lift=0.005),
                decal(f"{prefix}_c", (c[0] - r * 0.7, c[1], c[2] + r * 0.6), r * 0.3, oil2_m, 1.0, lift=0.0055)]

    def bandaid(name, c, angle):
        s, n = surface(c, 0.008)
        tape = rounded_box(f"{name}_tape", (0.09, 0.032, 0.006), tape_m, 0.012)
        pad = rounded_box(f"{name}_pad", (0.03, 0.022, 0.006), pad_m, 0.006)
        tape.location, pad.location = s, s + n * 0.003
        for o in (tape, pad):
            facing(o, n)
            o.rotation_euler.rotate_axis("Z", math.radians(angle))
        return [tape, pad]

    zs = [zletter("fx_z1", (0.215, -0.24, 0.935), 0.05, z_m, font),
          zletter("fx_z2", (0.262, -0.22, 0.992), 0.066, z_m, font),
          zletter("fx_z3", (0.315, -0.2, 1.052), 0.082, z_m, font)]
    blush = [decal("fx_blush_L", (0.165, -0.235, 0.625), 0.046, blush_m, 0.62, lift=0.003),
             decal("fx_blush_R", (-0.165, -0.235, 0.625), 0.046, blush_m, 0.62, lift=0.003)]

    return {
        "idle": ({}, []),
        "blink": ({"eyes:blink": 1.0}, []),          # 대기 표정에서 눈만 감은 것 (앱의 깜빡임용, Expression에는 없음)
        "hungry1": ({"eyes:wide": 0.6, "mouth:open": 0.8}, [drool("fx_drool1", 0.016)]),
        "hungry2": ({"eyes:sleepy": 0.35, "mouth:open": 1.0}, [drool("fx_drool2", 0.024)]),
        "hungry3": ({"eyes:sleepy": 0.75, "mouth:frown": 1.0}, [drool("fx_drool3", 0.026), sweat("fx_sweat3")]),
        "stain": ({"eyes:wide": 0.45, "mouth:flat": 1.0},
                  oil_spot("fx_oil_cheek", (0.2, -0.2, 0.6), 0.032) + oil_spot("fx_oil_belly", (-0.06, -0.26, 0.26), 0.04)
                  + oil_spot("fx_oil_arm", (0.31, -0.1, 0.31), 0.026)),
        "tired": ({"eyes:sleepy": 1.0, "mouth:flat": 0.6}, zs),
        "sick": ({"eyes:sleepy": 0.55, "eyes:blink": 0.2, "mouth:frown": 1.0},
                 blush + bandaid("fx_band", (-0.16, -0.21, 0.84), 32) + [sweat("fx_sweat_sick")]),
    }


def set_expression(keys):
    for obj in ("eyes", "mouth"):
        for k in bpy.data.objects[obj].data.shape_keys.key_blocks[1:]:
            k.value = keys.get(f"{obj}:{k.name}", 0.0)


# ───────────────────────── 렌더 ─────────────────────────

def bbox(objs):
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    pts = []
    for o in objs:
        ev = o.evaluated_get(dg)
        try:
            me = ev.to_mesh()
        except RuntimeError:
            continue
        pts += [ev.matrix_world @ v.co for v in me.vertices]
        ev.to_mesh_clear()
    mn = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    mx = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    return mn, mx


def make_camera(sc, name, direction, target, lens, w, h, objs, fill):
    """direction 쪽에서 target을 보고, objs가 화면의 fill 비율을 채우도록 거리 조절"""
    from bpy_extras.object_utils import world_to_camera_view
    c = link(bpy.data.objects.new(name, bpy.data.cameras.new(name)))
    c.data.lens = lens
    c.data.sensor_fit = "AUTO"
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.camera = c
    d = Vector(direction).normalized()
    # 실제 정점(일부 표본)이 화면에 차지하는 범위로 맞춘다 (상자 모서리 기준보다 여백이 작다)
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    corners = []
    for o in objs:
        ev = o.evaluated_get(dg)
        try:
            me = ev.to_mesh()
        except RuntimeError:
            continue
        step = max(1, len(me.vertices) // 1500)
        corners += [ev.matrix_world @ me.vertices[i].co for i in range(0, len(me.vertices), step)]
        ev.to_mesh_clear()
    t = Vector(target)
    dist = 3.0
    for _ in range(30):
        c.location = t + d * dist
        c.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
        bpy.context.view_layer.update()
        vs = [world_to_camera_view(sc, c, p) for p in corners]
        span = max(max(abs(v.x - 0.5) for v in vs) * 2, max(abs(v.y - 0.5) for v in vs) * 2)
        dist *= span / fill
    # 세로 가운데 맞춤
    vs = [world_to_camera_view(sc, c, p) for p in corners]
    cy = (min(v.y for v in vs) + max(v.y for v in vs)) / 2
    cx = (min(v.x for v in vs) + max(v.x for v in vs)) / 2
    c.data.shift_y = (cy - 0.5) * (h / max(w, h))
    c.data.shift_x = (cx - 0.5) * (w / max(w, h))
    return c


def render(sc, path):
    # --skip-existing: 중간에 멈춘 렌더를 이어서 (빈 파일은 다시 만든다)
    if A.skip_existing and os.path.exists(path) and os.path.getsize(path) > 0:
        return
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print("WROTE", path)


def set_skin(code):
    tex, metal, rough, ear_in, ear_back = SKINS[code]
    body = bpy.data.objects["bear_body"]
    m = body.data.materials[0]
    for n in m.node_tree.nodes:
        if n.type == "TEX_IMAGE":
            n.image = bpy.data.images.load(os.path.join(A.skins, f"{tex}.png"), check_existing=True)
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Metallic"].default_value = metal
    p.inputs["Roughness"].default_value = rough
    p.inputs["Coat Weight"].default_value = 0.5 if code == "P07" else 0.0
    bpy.data.materials["ear_inner"].node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = lin(ear_in)
    bpy.data.materials["ear_back"].node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = lin(ear_back)


def show_only(objs_visible, bear_mode):
    """bear_mode: normal | catcher(그림자만 받음) | holdout(보이지 않는 몸) | hidden"""
    vis = set(o.name for o in objs_visible)
    for o in bpy.context.scene.objects:
        if o.type in ("MESH", "CURVE", "FONT"):
            if o.name in BEAR_PARTS:
                o.hide_render = bear_mode == "hidden"
                o.is_shadow_catcher = bear_mode == "catcher"
                o.is_holdout = bear_mode == "holdout"
            else:
                o.hide_render = o.name not in vis


# 아이템 레이어에는 옷이 몸에 드리우는 그림자가 같이 들어가고, 그 그림자는 몸 재질을 따라간다.
# 돌려보기·동작 레이어를 몸 렌더가 끝난 스킨(P08 금속) 그대로 찍었더니 턱 밑에 반사 무늬 같은 진한 그림자가 생겨
# 정지 레이어(P03)와 달랐다 (움직이면 목이 일그러져 보이던 문제) → 레이어는 늘 이 무광 스킨으로 찍는다
LAYER_SKIN = "P03"


def render_layer(sc, objs, path):
    set_skin(LAYER_SKIN)
    show_only(objs, "catcher")
    render(sc, path)


class Rig:
    """
    돌려보기 카메라: yaw(곰 축 Z 둘레, 조명도 같이 돈다) → pitch(곰 가운데를 지나는 X축) → 카메라.
    yaw를 돌리면 곰이 제자리에서 도는 것과 같고, pitch 단을 올리면 위에서 내려다본다 (조명은 그대로).
    모든 단·방향이 같은 거리를 쓰고 곰 가운데가 늘 화면 가운데라, 단을 바꿔도 크기·위치가 튀지 않는다.
    기본 렌더(base·item, 홈·보상 화면)도 이 카메라의 (0단, HOME_YAW)로 찍어 돌려보기 프레임과 똑같이 겹친다.
    """

    def __init__(self, sc, objs, n_fit=8, fill=0.94):
        from bpy_extras.object_utils import world_to_camera_view
        self.sc = sc
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        pts = []
        for o in objs:
            ev = o.evaluated_get(dg)
            try:
                me = ev.to_mesh()
            except RuntimeError:
                continue
            step = max(1, len(me.vertices) // 120)
            pts += [ev.matrix_world @ me.vertices[i].co for i in range(0, len(me.vertices), step)]
            ev.to_mesh_clear()
        zc = (min(p.z for p in pts) + max(p.z for p in pts)) / 2
        self.yaw = link(bpy.data.objects.new("turn_rig", None))
        self.pitch = link(bpy.data.objects.new("turn_pitch", None))
        self.pitch.parent = self.yaw
        self.pitch.location = (0, 0, zc)
        self.cam = link(bpy.data.objects.new("turn_cam", bpy.data.cameras.new("turn_cam")))
        self.cam.parent = self.pitch
        self.cam.data.lens = 70
        self.cam.rotation_euler = (math.radians(90), 0, 0)      # 피벗(+Y 쪽)을 본다
        for name in ("key", "fill", "rim"):
            bpy.data.objects[name].parent = self.yaw
        dist = 3.0
        for _ in range(14):
            self.cam.location = (0, -dist, 0)
            worst = 0.0
            for row in range(len(TURN_ELEV)):
                for k in range(n_fit):
                    self.view(row, k * TURN_N // n_fit, TW, TH)
                    bpy.context.view_layer.update()
                    vs = [world_to_camera_view(sc, self.cam, p) for p in pts]
                    worst = max(worst, max(abs(v.x - 0.5) for v in vs) * 2, max(abs(v.y - 0.5) for v in vs) * 2)
            dist *= worst / fill
        self.cam.location = (0, -dist, 0)
        print(f"RIG dist={dist:.3f} zc={zc:.3f} elev={TURN_ELEV}")

    def view(self, row, i, w, h):
        """row = 고도 단(TURN_ELEV), i = 방향 프레임(TURN_N, +는 카메라가 곰의 왼쪽(+X)으로)"""
        self.sc.camera = self.cam
        self.sc.render.resolution_x, self.sc.render.resolution_y = w, h
        self.yaw.rotation_euler = (0, 0, 2 * math.pi * i / TURN_N)
        self.pitch.rotation_euler = (-math.radians(TURN_ELEV[row]), 0, 0)


def signed_depths(objs):
    """아이템 정점마다 곰 표면까지의 부호 거리 (음수 = 몸 안). [(거리, 좌표)]"""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    out = []
    for o in objs:
        ev = o.evaluated_get(dg)
        try:
            me = ev.to_mesh()
        except RuntimeError:
            continue
        for v in me.vertices:
            p = ev.matrix_world @ v.co
            loc, nor, _, _ = BVH.find_nearest(p)
            out.append(((p - loc).dot(nor), p.copy()))
        ev.to_mesh_clear()
    return out


def clipcheck(sc, bear, items, rig):
    """
    몸 뚫림 점검: (1) 아이템 정점이 몸 안에 들어간 양, (2) 진짜 3D(곰+옷 한 번에) vs 앱식 합성(기본 위에 레이어)
    을 8방향으로 나란히 렌더해 A.out/clip_<키>.png 로 저장. 조합(상의+하의 등)도 같이 본다.
    """
    import numpy as np
    for key, objs in items.items():
        d = signed_depths(objs)
        inside = [(s, p) for s, p in d if s < 0.0]
        near = [s for s, _ in d if 0.0 <= s < 0.004]
        line = f"CLIP {key}: verts={len(d)} inside={len(inside)} (<4mm={len(near)})"
        if inside:
            worst = min(inside, key=lambda t: t[0])
            zs = [p.z for _, p in inside]
            xs = [p.x for _, p in inside]
            line += f" worst={worst[0] * 1000:.1f}mm at ({worst[1].x:.3f},{worst[1].y:.3f},{worst[1].z:.3f}) z∈[{min(zs):.3f},{max(zs):.3f}] x∈[{min(xs):.3f},{max(xs):.3f}]"
        print(line)

    combos = {k: [k] for k in items}
    combos.update({"T01+B01": ["ITM-T-01", "ITM-B-01"], "T02+B01": ["ITM-T-02", "ITM-B-01"],
                   "T01+B02": ["ITM-T-01", "ITM-B-02"], "T02+B02": ["ITM-T-02", "ITM-B-02"],
                   "H01+T02+B01": ["ITM-H-01", "ITM-T-02", "ITM-B-01"]})
    sc.render.resolution_percentage = 50
    sc.cycles.samples = 24
    set_skin("P04")          # 어두운 몸이라 옷과 구분이 잘 된다
    set_expression({})
    tmp = os.path.join(A.out, "_clip_tmp.png")
    sc.render.image_settings.file_format = "PNG"

    def shot():
        render(sc, tmp)
        img = bpy.data.images.load(tmp, check_existing=False)
        a = np.array(img.pixels[:], dtype=np.float32).reshape(img.size[1], img.size[0], 4)
        bpy.data.images.remove(img)
        return a

    def over(top, under):
        ta = top[..., 3:4]
        out = top.copy()
        out[..., :3] = top[..., :3] * ta + under[..., :3] * under[..., 3:4] * (1 - ta)
        out[..., 3:4] = ta + under[..., 3:4] * (1 - ta)
        out[..., :3] /= np.maximum(out[..., 3:4], 1e-6)
        return out

    def flat(a):
        bg = np.array([0.93, 0.93, 0.95, 1.0], dtype=np.float32)
        o = a[..., :3] * a[..., 3:4] + bg[:3] * (1 - a[..., 3:4])
        return np.concatenate([o, np.ones_like(a[..., :1])], axis=-1)

    angles = [k * TURN_N // 8 for k in range(8)]   # 45° 간격
    crow = int(A.rows.split(",")[0]) if A.rows else 0   # 볼 고도 단
    base, layers = {}, {k: {} for k in items}
    for i in angles:
        rig.view(crow, i, TW, TH)
        show_only([], "normal")
        base[i] = shot()
        for k, objs in items.items():
            show_only(objs, "catcher")
            layers[k][i] = shot()
    for name, keys in combos.items():
        objs = [o for k in keys for o in items[k]]
        cols = []
        for i in angles:
            rig.view(crow, i, TW, TH)
            show_only(objs, "normal")
            true = shot()
            comp = base[i]
            for k in sorted(keys, key=lambda k: "BTH".index(k[4])):   # 하의 → 상의 → 머리 (앱과 같은 순서)
                comp = over(layers[k][i], comp)
            diff = np.abs(flat(true)[..., :3] - flat(comp)[..., :3]).max(axis=-1, keepdims=True)
            heat = np.concatenate([np.clip(diff * 4, 0, 1), np.zeros_like(diff), np.zeros_like(diff), np.ones_like(diff)], axis=-1)
            cols.append(np.concatenate([flat(heat), flat(comp), flat(true)], axis=0))   # 이미지 좌표는 아래→위
        sheet = np.concatenate(cols, axis=1)
        h, w = sheet.shape[:2]
        img = bpy.data.images.new(f"clip_{name}", w, h, alpha=True)
        img.pixels[:] = sheet.ravel()
        img.filepath_raw = os.path.join(A.out, f"clip_{name}.png")
        img.file_format = "PNG"
        img.save()
        print("WROTE", img.filepath_raw)
    os.remove(tmp)


# ───────────────────────── 터치 반응 동작 ─────────────────────────

def bind_to_rig(objs, allowed, fallback):
    """
    옷을 곰 뼈대에 붙인다 (기본 자세에서): 모디파이어까지 반영한 메시로 굳힌 뒤, 몸 표면에서 가장 가까운 곳의
    뼈 가중치를 옮겨 받고 Armature 모디파이어를 단다. 그러면 머리·몸·다리를 움직일 때 옷이 같이 움직인다.
    [allowed] 뼈만 남기고 다시 합이 1이 되게 맞춘다 (팔 가중치를 받으면 팔을 들 때 조끼 자락이 부채처럼 늘어났다).
    가중치가 하나도 없는 정점은 [fallback] 뼈에 붙인다.
    굳힌 메시는 렌더 모양이 같다 (원래 오브젝트는 지운다). 새 오브젝트 목록을 돌려준다.
    """
    rig, body = bpy.data.objects["bear_rig"], bpy.data.objects["bear_body"]
    out = []
    for o in objs:
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        me = bpy.data.meshes.new_from_object(o.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
        n = link(bpy.data.objects.new(o.name, me))
        n.matrix_world = o.matrix_world.copy()
        bpy.data.objects.remove(o)
        n.name = n.name.split(".")[0]
        for g in body.vertex_groups:
            n.vertex_groups.new(name=g.name)
        dt = n.modifiers.new("bones", "DATA_TRANSFER")
        dt.object = body
        dt.use_vert_data = True
        dt.data_types_verts = {"VGROUP_WEIGHTS"}
        dt.vert_mapping = "POLYINTERP_NEAREST"
        dt.layers_vgroup_select_src = "ALL"
        dt.layers_vgroup_select_dst = "NAME"
        with bpy.context.temp_override(object=n, active_object=n, selected_objects=[n]):
            bpy.ops.object.modifier_apply(modifier=dt.name)
        names = [g.name for g in n.vertex_groups]
        weights = [[(names[g.group], g.weight) for g in v.groups if names[g.group] in allowed and g.weight > 1e-4]
                   for v in n.data.vertices]
        for g in list(n.vertex_groups):
            n.vertex_groups.remove(g)
        groups = {a: n.vertex_groups.new(name=a) for a in allowed}
        for i, ws in enumerate(weights):
            total = sum(w for _, w in ws)
            if total <= 1e-4:
                groups[fallback].add([i], 1.0, "REPLACE")
            else:
                for a, w in ws:
                    groups[a].add([i], w / total, "REPLACE")
        n.modifiers.new("rig", "ARMATURE").object = rig
        out.append(n)
    return out


def world_rot(pb, axis, deg):
    """뼈를 월드 축 [axis] 둘레로 [deg]도 돌리는 pose 회전 (뼈 머리 기준, 리그 = 월드 좌표)"""
    from mathutils import Quaternion
    r = pb.bone.matrix_local.to_3x3()
    return (r.inverted() @ Quaternion(Vector(axis), math.radians(deg)).to_matrix() @ r).to_quaternion()


def pose_rest():
    rig = bpy.data.objects["bear_rig"]
    if rig.animation_data:
        rig.animation_data.action = None
    for pb in rig.pose.bones:
        pb.rotation_quaternion = (1, 0, 0, 0)
        pb.location = (0, 0, 0)
        pb.scale = (1, 1, 1)
    set_expression({})
    bpy.context.view_layer.update()


def smooth(t):
    t = min(max(t, 0.0), 1.0)
    return t * t * (3 - 2 * t)


def pose_motion(act, nn, n):
    """[act]의 nn번째 프레임 자세 (0번·끝 = 기본 자세). 표정은 대기 + 동작 중 웃는 눈"""
    pose_rest()
    rig = bpy.data.objects["bear_rig"]
    src, _ = MOTIONS[act]
    t = nn / (n - 1)
    env = smooth(t / 0.2) * smooth((1 - t) / 0.2)     # 처음·끝은 기본 자세로
    if src:
        action = bpy.data.actions[src]
        rig.animation_data.action = action
        if hasattr(rig.animation_data, "action_slot") and rig.animation_data.action_slot is None and len(action.slots):
            rig.animation_data.action_slot = action.slots[0]
        bpy.context.scene.frame_set(1 + nn)
        keys = {"eyes:happy": env, "mouth:open": 0.5 * env if act == "cheer" else 0.0}
    else:
        # 발 동동: 네 걸음(왼·오·왼·오). 든 발은 앞으로 살짝 들리고, 몸은 디딘 발 쪽으로 기울고, 팔은 반대로 흔든다
        pb = rig.pose.bones
        k, u = divmod(t * 4, 1.0)
        k = min(int(k), 3)
        lift = math.sin(math.pi * u)
        side = 1 if k % 2 == 0 else -1          # 1 = 왼발(+X) 들기
        leg = pb["leg_L" if side == 1 else "leg_R"]
        leg.location = (0, -0.07 * lift, 0)       # 뼈가 아래를 향하므로 -Y = 위로
        leg.rotation_quaternion = world_rot(leg, (1, 0, 0), -28 * lift)
        pb["root"].location = (0, 0.018 * lift, 0)
        pb["spine"].rotation_quaternion = world_rot(pb["spine"], (0, 1, 0), -8 * side * lift)
        pb["head"].rotation_quaternion = world_rot(pb["head"], (0, 1, 0), 7 * side * lift) @ world_rot(pb["head"], (1, 0, 0), 6 * lift)
        swing = {"arm_L": -26 if side == -1 else 14, "arm_R": -26 if side == 1 else 14}
        for name, a in swing.items():
            pb[name].rotation_quaternion = world_rot(pb[name], (1, 0, 0), a * lift)
        for name in ("ear_L", "ear_R"):
            pb[name].rotation_quaternion = world_rot(pb[name], (0, 1, 0), -10 * side * lift)
        keys = {"eyes:happy": env}
    set_expression(keys)
    bpy.context.view_layer.update()


def motion_view(sc, rig):
    """돌려보기 (0단, HOME_YAW) 카메라 그대로, 화면만 MW×MH로 넓힌다 (위로 MH-TH, 좌우로 (MW-TW)/2).
    AUTO 맞춤은 긴 쪽(세로 TH)에 센서 폭을 쓰므로, 세로 맞춤 + 센서를 MH/TH배 → 한 픽셀이 보는 각이 같다"""
    rig.view(0, HOME_YAW, MW, MH)
    cam = rig.cam.data
    cam.sensor_fit = "VERTICAL"
    cam.sensor_height = cam.sensor_width * MH / TH
    cam.shift_y = (MH - TH) / 2 / MH


def unmotion_view(rig):
    cam = rig.cam.data
    cam.sensor_fit, cam.shift_y = "AUTO", 0.0


def render_motion(sc, rig, items, codes, acts):
    """터치 반응 프레임: motion/<동작>/b_<스킨>_<nn>, motion/<동작>/i_<아이템>_<nn> (MW×MH, motion_view)"""
    motion_view(sc, rig)
    sc.cycles.samples = A.turn_samples
    sc.render.image_settings.quality = 80
    for act in acts:
        _, n = MOTIONS[act]
        out = os.path.join(A.out, "motion", act)
        os.makedirs(out, exist_ok=True)
        for nn in range(n):
            pose_motion(act, nn, n)
            for code in codes:
                set_skin(code)
                show_only([], "normal")
                render(sc, os.path.join(out, f"b_{code}_{nn:02d}.webp"))
            for key, objs in items.items():
                render_layer(sc, objs, os.path.join(out, f"i_{key}_{nn:02d}.webp"))
    pose_rest()
    unmotion_view(rig)


def motion_preview(sc, rig, items, acts):
    """확인용: 곰+옷을 진짜 3D로 함께 (앱 합성이 아님) 동작마다 7장. 0번 앞에 돌려보기 프레임(rest_turn)도 찍어 위치 비교"""
    out = os.path.join(A.out, "motion_preview")
    os.makedirs(out, exist_ok=True)
    rig.view(0, HOME_YAW, TW, TH)
    sc.cycles.samples = 16
    set_skin("P03")
    show_only([], "normal")
    sc.render.image_settings.file_format = "PNG"
    sc.render.filepath = os.path.join(out, "rest_turn.png")
    bpy.ops.render.render(write_still=True)
    motion_view(sc, rig)
    sc.render.filepath = os.path.join(out, "rest_motion.png")   # 가운데를 잘라 내면 rest_turn과 같아야 한다
    bpy.ops.render.render(write_still=True)
    sel = [o for k in A.preview.split(",") for o in items.get(k, [])]
    for act in acts:
        _, n = MOTIONS[act]
        for nn in sorted({round(j * (n - 1) / 6) for j in range(7)}):
            pose_motion(act, nn, n)
            show_only(sel, "normal")
            sc.render.filepath = os.path.join(out, f"{act}_{nn:02d}.png")
            bpy.ops.render.render(write_still=True)
            print("WROTE", sc.render.filepath)
    pose_rest()
    unmotion_view(rig)


def main():
    global BM, BVH
    sc = setup()
    BM = body_bm()
    BVH = BVHTree.FromBMesh(BM)

    items = {
        "ITM-H-01": item_sunrise_headband(),
        "ITM-H-02": item_coffee_bean_cap(),
        "ITM-T-01": item_red_apron(),
        "ITM-T-02": item_safety_vest(),
        "ITM-B-01": item_denim_overalls(),
        "ITM-B-02": item_board_shorts(),
    }
    medals = {"TTL-00": medal("TTL-00", "3FC1A5"), "TTL-01": medal("TTL-01", "3A7BD5"), "TTL-02": medal("TTL-02", "F2994A")}
    exprs = build_expressions()

    bear = [bpy.data.objects[n] for n in BEAR_PARTS]
    worn = [o for objs in items.values() for o in objs]
    # 카메라: 곰 + 모든 아이템이 모든 방향·고도에서 다 들어오게 (레이어 정렬이 같아야 하므로 한 번만 정한다)
    rig = Rig(sc, bear + worn)
    rows = [int(r) for r in A.rows.split(",")] if A.rows else list(range(len(TURN_ELEV)))
    codes = [c for c in A.codes.split(",") if c in SKINS] if A.codes else list(SKINS)

    what = A.what
    if what in ("motion", "motion_preview"):
        # 옷을 뼈대에 붙이는 건 동작 렌더 때만 (다른 렌더는 기본 자세라 결과가 같지만 굳이 바꾸지 않는다)
        # 머리 아이템 = 머리에 고정, 상의 = 몸통만, 하의 = 몸통 + 다리 (팔은 옷 밖에 있으니 옷이 팔을 따라가지 않는다)
        follow = {"H": ({"head"}, "head"), "T": ({"root", "spine"}, "spine"), "B": ({"root", "spine", "leg_L", "leg_R"}, "spine")}
        items = {k: bind_to_rig(objs, *follow[k.split("-")[1]]) for k, objs in items.items()}
        acts = [a for a in A.acts.split(",") if a in MOTIONS] if A.acts else list(MOTIONS)
        if what == "motion_preview":
            motion_preview(sc, rig, items, acts)
        else:
            render_motion(sc, rig, items, codes, acts)
        return

    if what == "preview":
        rig.view(0, HOME_YAW, W, H)
        set_skin("P03")
        sel = [o for k in A.preview.split(",") for o in items.get(k, [])]
        show_only(sel, "normal")
        render(sc, os.path.join(A.out, "preview.png"))
        make_camera(sc, "side", (1.0, -0.35, 0.1), (0, 0, 0.53), 70, W, H, bear + worn, 0.94)
        render(sc, os.path.join(A.out, "preview_side.png"))
        return

    if what == "clipcheck":
        clipcheck(sc, bear, items, rig)
        return

    if what == "expr_preview":
        set_skin("P03")
        for key, (keys, props) in exprs.items():
            set_expression(keys)
            show_only(props, "normal")
            render(sc, os.path.join(A.out, f"expr_{key}.webp"))
        return

    if what in ("all", "base"):
        rig.view(0, HOME_YAW, W, H)
        only = set(A.exprs.split(",")) if A.exprs else None
        for code in codes:
            set_skin(code)
            for key, (keys, props) in exprs.items():
                if only and key not in only:
                    continue
                set_expression(keys)
                show_only(props, "normal")
                render(sc, os.path.join(A.out, f"base_{code}_{key}.webp"))
        set_expression({})

    if what in ("all", "items"):
        rig.view(0, HOME_YAW, W, H)
        set_expression({})
        for key, objs in items.items():
            render_layer(sc, objs, os.path.join(A.out, f"item_{key}.webp"))

    if what in ("all", "thumbs"):
        # 옷은 보이지 않는 몸이 입은 모양으로 (곰 = holdout), 메달은 곰 없이
        for key, objs in list(items.items()) + list(medals.items()):
            mn, mx = bbox(objs)
            worn_item = key.startswith("ITM")
            c = make_camera(sc, f"th_{key}", (0.35, -1.0, 0.18) if worn_item else (0.3, -1.0, 0.1),
                            (mn + mx) / 2, 60, THUMB, THUMB, objs, 0.86)
            show_only(objs, "holdout" if worn_item else "hidden")
            render(sc, os.path.join(A.out, f"thumb_{key}.webp"))

    if what in ("all", "turn"):
        # 돌려보기 프레임: 대기 표정, 고도 단 × TURN_N 방향.
        # 파일 = turn/b_<스킨>_<단>_<nn>, turn/i_<아이템>_<단>_<nn>
        out = os.path.join(A.out, "turn")
        os.makedirs(out, exist_ok=True)
        set_expression({})
        sc.cycles.samples = A.turn_samples
        sc.render.image_settings.quality = 80
        for row in rows:
            for i in range(TURN_N):
                rig.view(row, i, TW, TH)
                for code in codes:
                    set_skin(code)
                    show_only([], "normal")
                    render(sc, os.path.join(out, f"b_{code}_{row}_{i:02d}.webp"))
                if A.turn_items:
                    for key, objs in items.items():
                        render_layer(sc, objs, os.path.join(out, f"i_{key}_{row}_{i:02d}.webp"))


main()
print("DONE")