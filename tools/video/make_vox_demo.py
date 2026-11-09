"""
로로 앱 10초 시연 영상 (Vox 스타일) 합성 스크립트 — Blender 5.x 동영상 편집기(VSE) 사용

사용법:
  blender -b --factory-startup -P tools/video/make_vox_demo.py -- <clips_dir> <out.mp4> [--stills <dir>]

  clips_dir : states.webm, recover.webm, tap.webm, rotate.webm, skins.webm (에뮬레이터 2560x1440 녹화)
              녹화: adb emu screenrecord start --fps 30 --time-limit N <file.webm>
  --stills  : 영상 대신 확인용 정지 화면 몇 장만 렌더
"""
import bpy
import math
import os
import sys

import numpy as np

args = sys.argv[sys.argv.index("--") + 1:]
CLIPS, OUT = args[0], args[1]
STILLS = args[args.index("--stills") + 1] if "--stills" in args else None

W, H, FPS = 1920, 1080, 30
SRC_W, SRC_H = 2560, 1440

FONT_KO = "C:/Windows/Fonts/malgunbd.ttf"
FONT_EN = "C:/Windows/Fonts/impact.ttf"

INK = (0.07, 0.07, 0.07, 1)
PAPER = (0.937, 0.906, 0.839)          # #EFE7D6
YELLOW = (1.0, 0.82, 0.18, 1)          # 형광펜
RED = (0.90, 0.25, 0.16, 1)
WHITE = (1, 1, 1, 1)

# 원본(2560x1440) 안에서 잘라낼 영역: (left, top, right, bottom) 픽셀
CROP_BEAR = (880, 290, 1680, 1090)
CROP_WIDE = (880, 290, 2380, 1090)

sc = bpy.context.scene
sc.render.resolution_x, sc.render.resolution_y, sc.render.resolution_percentage = W, H, 100
sc.render.fps = FPS
sc.frame_start, sc.frame_end = 1, 300
sc.view_settings.view_transform = "Standard"
sc.view_settings.look = "None"
sed = sc.sequence_editor_create()
strips = sed.strips

fonts = {"ko": bpy.data.fonts.load(FONT_KO), "en": bpy.data.fonts.load(FONT_EN)}
_ch = [0]


def next_channel():
    _ch[0] += 1
    return _ch[0]


def frames(a, b):
    return a, b - a


# ---------------------------------------------------------------- 종이 배경
def paper_texture(path):
    rng = np.random.default_rng(7)
    small = rng.normal(0, 1, (H // 24 + 2, W // 24 + 2))
    blot = np.kron(small, np.ones((24, 24)))[:H, :W]
    for _ in range(3):  # 부드럽게
        blot = (blot + np.roll(blot, 12, 0) + np.roll(blot, -12, 0) + np.roll(blot, 12, 1) + np.roll(blot, -12, 1)) / 5
    grain = rng.normal(0, 1, (H, W))
    fibers = np.abs(np.sin(np.linspace(0, 900, W))[None, :] * rng.normal(0, 1, (H, 1))) * 0.15
    lum = 1 + blot * 0.018 + grain * 0.022 - fibers * 0.03
    img = np.zeros((H, W, 4), dtype=np.float32)
    for c in range(3):
        img[..., c] = np.clip(PAPER[c] * lum, 0, 1)
    img[..., 3] = 1
    # 가장자리 살짝 어둡게 (비네팅)
    yy, xx = np.mgrid[0:H, 0:W]
    v = 1 - 0.10 * (((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    img[..., :3] *= v[..., None]
    im = bpy.data.images.new("paper", W, H, alpha=False)
    im.pixels.foreach_set(img.ravel())
    im.filepath_raw = path
    im.file_format = "PNG"
    im.save()
    return path


# ---------------------------------------------------------------- 도우미
def rect(start, end, cx, cy, w, h, color, rot=0.0, channel=None):
    """화면 중심 기준 (cx, cy) 위치에 w x h 사각형"""
    s = strips.new_effect("rect", "COLOR", channel or next_channel(), frame_start=start, length=end - start)
    s.color = color[:3]
    s.transform.scale_x, s.transform.scale_y = w / W, h / H
    s.transform.offset_x, s.transform.offset_y = cx, cy
    s.transform.rotation = rot
    s.blend_type = "ALPHA_OVER"
    return s


def text(start, end, txt, x, y, size, color=INK, font="ko", anchor=("LEFT", "CENTER"),
         box=None, margin=0.012, pop=True, channel=None):
    """(x, y): 화면 왼쪽 아래 기준 픽셀"""
    s = strips.new_effect("txt", "TEXT", channel or next_channel(), frame_start=start, length=end - start)
    s.text = txt
    s.font = fonts[font]
    s.font_size = size
    s.color = color
    s.location = (x / W, y / H)
    s.anchor_x, s.anchor_y = anchor
    if box:
        s.use_box = True
        s.box_color = box
        s.box_margin = margin
    s.blend_type = "ALPHA_OVER"
    if pop:  # 살짝 커졌다가 자리잡으며 나타남
        s.transform.origin = (x / W, y / H)
        for f, sc_, a in ((start, 1.18, 0.0), (start + 4, 0.97, 1.0), (start + 7, 1.0, 1.0)):
            s.transform.scale_x = s.transform.scale_y = sc_
            s.blend_alpha = a
            s.transform.keyframe_insert("scale_x", frame=f)
            s.transform.keyframe_insert("scale_y", frame=f)
            s.keyframe_insert("blend_alpha", frame=f)
    return s


def highlight(start, x_left, y, w, h, wipe=8, end=None, color=YELLOW):
    """형광펜이 왼쪽에서 오른쪽으로 그어지는 효과"""
    s = strips.new_effect("hl", "COLOR", next_channel(), frame_start=start, length=(end or 300) - start)
    s.color = color[:3]
    s.blend_type = "ALPHA_OVER"
    s.transform.scale_y = h / H
    s.transform.offset_y = y - H / 2
    for f, ww in ((start, 1.0), (start + wipe, w)):
        s.transform.scale_x = ww / W
        s.transform.offset_x = x_left - W / 2 + ww / 2
        s.transform.keyframe_insert("scale_x", frame=f)
        s.transform.keyframe_insert("offset_x", frame=f)
    return s


def clip_card(start, end, clip, src_sec, crop, cx, cy, scale, rot_deg=0.0, border=8, push=0.03):
    """앱 녹화의 crop 영역을 카드처럼 배치 (그림자 + 검은 테두리 + 흰 바탕)"""
    l, t, r, b = crop
    cw, chh = (r - l) * scale, (b - t) * scale
    rot = math.radians(rot_deg)
    rect(start, end, cx + 18, cy - 18, cw + border * 2, chh + border * 2, INK, rot)           # 그림자
    rect(start, end, cx, cy, cw + border * 2, chh + border * 2, INK, rot)                    # 테두리
    rect(start, end, cx, cy, cw, chh, (0.97, 0.973, 0.98, 1), rot)                           # 앱 배경색

    m = strips.new_movie("clip", os.path.join(CLIPS, clip + ".webm"), next_channel(), start)
    off = int(round(src_sec * FPS))
    m.frame_start = start - off
    m.frame_offset_start = off
    m.frame_offset_end = max(0, m.frame_duration - off - (end - start))
    m.crop.min_x, m.crop.max_x = l, SRC_W - r
    m.crop.max_y, m.crop.min_y = t, SRC_H - b
    pcx, pcy = (l + r) / 2, (t + b) / 2
    m.transform.origin = (pcx / SRC_W, 1 - pcy / SRC_H)
    m.transform.offset_x = cx - (pcx - SRC_W / 2)
    m.transform.offset_y = cy - (SRC_H / 2 - pcy)
    m.transform.rotation = rot
    m.blend_type = "ALPHA_OVER"
    # 천천히 밀고 들어가기 (push-in)
    for f, k in ((start, 1.0), (end, 1.0 + push)):
        m.transform.scale_x = m.transform.scale_y = scale * k
        m.transform.keyframe_insert("scale_x", frame=f)
        m.transform.keyframe_insert("scale_y", frame=f)
    return m


# ---------------------------------------------------------------- 타임라인 (30fps, 300프레임 = 10초)
paper = paper_texture(os.path.join(bpy.app.tempdir or os.environ.get("TEMP", "."), "roro_paper.png"))
bg = strips.new_image("paper", paper, next_channel(), 1)
bg.frame_final_end = 301

# 상단 태그 (영상 내내)
text(1, 282, "CAR AMATE  ·  PLEOS VEHICLE SDK", 70, 1020, 30, RED, "en", pop=False)

# S1 제목 (1~48): 왼쪽 문구 + 오른쪽 곰 카드
text(4, 48, "배터리가 줄면,", 110, 640, 112)
highlight(12, 100, 478, 660, 120, end=48)
text(10, 48, "곰도 지친다.", 110, 480, 112)
text(18, 48, "차량 상태를 표정으로 보여주는 캐릭터", 112, 360, 40, (0.25, 0.25, 0.25, 1))
clip_card(1, 48, "tap", 0.2, CROP_BEAR, 470, -20, 0.62, rot_deg=3)

# S2 배터리 단계 (48~150): 넓은 카드 + 큰 퍼센트 도장
text(48, 150, "배터리에 따라 표정이 바뀐다", 110, 955, 64, INK, box=YELLOW, margin=0.01)
beats = [
    (48, 69, "states", 0.45, "80%", (0.18, 0.62, 0.27, 1)),
    (69, 90, "states", 2.65, "25%", YELLOW),
    (90, 111, "states", 5.0, "10%", (0.94, 0.55, 0.18, 1)),
    (111, 132, "states", 7.0, "3%", RED),
    (132, 150, "recover", 1.95, "충전!", (0.18, 0.62, 0.27, 1)),  # 충전 후 손 흔들기
]
for a, b, clip, src, label, col in beats:
    clip_card(a, b, clip, src, CROP_BEAR, 470, -20, 0.8, rot_deg=-1.5, push=0.02)
    fg = INK if col == YELLOW else WHITE
    text(a, b, label, 130, 560, 210 if "%" in label else 170, fg, "en" if "%" in label else "ko", box=col, margin=0.018)

# S3 탭 반응 (150~198)
text(152, 198, "터치하면", 110, 640, 112)
highlight(158, 100, 478, 560, 120, end=198)
text(156, 198, "반응하고", 110, 480, 112)
clip_card(150, 198, "tap", 1.2, CROP_BEAR, 470, -20, 0.8, rot_deg=-2)

# S4 회전 (198~243)
text(200, 243, "손가락으로", 110, 640, 112)
highlight(206, 100, 478, 560, 120, end=243)
text(204, 243, "돌려 보고", 110, 480, 112)
clip_card(198, 243, "rotate", 1.65, CROP_BEAR, 470, -20, 0.8, rot_deg=2)

# S5 스킨 (243~282): 빠른 컷
text(245, 282, "스킨", 110, 640, 112)
highlight(249, 100, 478, 300, 120, end=282)
text(247, 282, "8종", 110, 480, 112, font="ko")
cuts = [2.1, 2.75, 3.15, 3.85, 4.85, 5.75]
for i, t0 in enumerate(cuts):
    a = 243 + i * 6
    b = 282 if i == len(cuts) - 1 else a + 6
    clip_card(a, b, "skins", t0, CROP_BEAR, 470, -20, 0.8, rot_deg=-1, push=0.0)

# S6 엔딩 (282~300)
highlight(282, 640, 600, 640, 230, wipe=5, end=301)
text(284, 301, "로로", W / 2, 600, 200, INK, anchor=("CENTER", "CENTER"))
text(286, 301, "차량 상태가 캐릭터가 된다", W / 2, 400, 54, INK, anchor=("CENTER", "CENTER"))
text(288, 301, "Pebble × Pleos Vehicle SDK", W / 2, 320, 34, RED, "en", anchor=("CENTER", "CENTER"))

# ---------------------------------------------------------------- 렌더
if STILLS:
    os.makedirs(STILLS, exist_ok=True)
    sc.render.image_settings.file_format = "PNG"
    for f in (20, 45, 58, 80, 100, 122, 140, 175, 220, 260, 295):
        sc.frame_set(f)
        sc.render.filepath = os.path.join(STILLS, f"f{f:03d}.png")
        bpy.ops.render.render(write_still=True)
    print("STILLS DONE")
else:
    ims = sc.render.image_settings
    try:
        ims.media_type = "VIDEO"
    except Exception:
        pass
    ims.file_format = "FFMPEG"
    sc.render.ffmpeg.format = "MPEG4"
    sc.render.ffmpeg.codec = "H264"
    sc.render.ffmpeg.constant_rate_factor = "HIGH"
    sc.render.ffmpeg.ffmpeg_preset = "GOOD"
    sc.render.filepath = os.path.abspath(OUT)
    bpy.ops.render.render(animation=True)
    print("VIDEO DONE", OUT)
