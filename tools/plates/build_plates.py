"""
칭호 번호판 바탕 만들기 (2026-11-13).

사용자가 준 번호판 시트(source/title_sheet_light.png, title_sheet_dark.png = "설레는 첫 차 오너" 시트)에는
날짜 "— 261110 —"과 칭호 이름이 그림으로 박혀 있다. 앱이 날짜와 이름을 직접 쓰도록 글자를 지운 바탕을 만든다.

  - 시트 한 장에 번호판 4장이 세로로 있다. 밝은 시트 = P05·P01·P02·P03, 어두운 시트 = P04·P06·P07·P08 (위에서부터).
  - 글자 자리: 곰 얼굴과 세로줄 오른쪽에서 바탕보다 확연히 진하거나(어두운 판은 밝은) 화소를 글자로 보고 조금 넓힌다.
  - 지우기: 글자 화소를 같은 줄의 양옆 바탕 화소로 이어 메우고(줄마다 선형), 메운 곳만 세로로 살짝 흐려 줄무늬를 없앤다.
  - 결과: app/src/main/res/drawable-nodpi/title_plate_<p0n>.webp (번호판 하나씩)
  - 함께 출력하는 것: 판마다 글자 색(원래 글자 화소의 중앙값)과 날짜 줄·이름 줄의 가운데 자리(번호판 왼쪽 위 기준 px).
    앱(ui/theme/Tokens.kt KagomColor.TitlePlate, ui/components/ClosetTitlePlate.kt)에 이 값을 옮겨 적는다.

사용 (레포 루트에서, 레포 밖 작업 폴더 없이):
  uv run --no-project --with pillow --with numpy python -I -X utf8 tools/plates/build_plates.py
"""
import os

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")

# 시트 안 번호판 자리 (ui/components/ClosetTitlePlate.kt 예전 잘라 쓰기 값과 같다)
SHEETS = {
    "light": dict(file="title_sheet_light.png", left=112, top=82, step=303, w=974, h=270, skins=["P05", "P01", "P02", "P03"]),
    "dark": dict(file="title_sheet_dark.png", left=106, top=70, step=309, w=988, h=278, skins=["P04", "P06", "P07", "P08"]),
}
# 글자를 찾는 범위: 세로줄(x≈275) 오른쪽, 번호판 테두리 안쪽
TEXT_X0, TEXT_X1_PAD, TEXT_Y0, TEXT_Y1_PAD = 300, 70, 36, 40


def lum(a):
    return a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114


def blank(plate):
    a = np.asarray(plate.convert("RGB")).astype(np.float32)
    h, w, _ = a.shape
    x1, y1 = w - TEXT_X1_PAD, h - TEXT_Y1_PAD
    region = a[TEXT_Y0:y1, TEXT_X0:x1]
    L = lum(region)
    # 바탕 밝기: 줄마다 중앙값 (글자는 한 줄에서 절반을 넘지 않는다)
    base = np.median(L, axis=1, keepdims=True)
    dark_text = np.median(L) > 128
    diff = (base - L) if dark_text else (L - base)
    mask = diff > 28
    ys, xs = np.nonzero(mask)
    ink = np.median(region[mask], axis=0)
    # 날짜 줄과 이름 줄: 글자 화소가 있는 줄을 위아래 두 덩어리로 나눈다
    rows = np.nonzero(mask.any(axis=1))[0]
    gaps = np.nonzero(np.diff(rows) > 6)[0]
    split_at = rows[gaps[0]] if len(gaps) else rows[len(rows) // 2]
    lines = []
    for sel in (ys <= split_at, ys > split_at):
        lines.append(dict(cx=float((xs[sel].min() + xs[sel].max()) / 2 + TEXT_X0), cy=float((ys[sel].min() + ys[sel].max()) / 2 + TEXT_Y0),
                          height=int(ys[sel].max() - ys[sel].min() + 1), width=int(xs[sel].max() - xs[sel].min() + 1)))
    # 글자 가장자리의 흐린 화소와 그림자까지 덮게 넓힌다
    full = np.zeros((h, w), bool)
    full[TEXT_Y0:y1, TEXT_X0:x1] = mask
    m = Image.fromarray((full * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(11))
    full = np.asarray(m) > 0
    out = a.copy()
    for y in range(h):
        row = full[y]
        if not row.any():
            continue
        x = 0
        while x < w:
            if not row[x]:
                x += 1
                continue
            s = x
            while x < w and row[x]:
                x += 1
            l, r = max(0, s - 1), min(w - 1, x)
            # 양옆 몇 화소의 평균으로 이어 메운다 (한 화소만 쓰면 잡티가 줄로 늘어난다)
            lc = a[y, max(0, l - 3):l + 1].mean(axis=0)
            rc = a[y, r:min(w, r + 4)].mean(axis=0)
            t = np.linspace(0, 1, x - s)[:, None]
            out[y, s:x] = lc * (1 - t) + rc * t
    # 메운 곳만 세로로 흐린다
    blurred = np.asarray(Image.fromarray(out.clip(0, 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(3))).astype(np.float32)
    soft = np.asarray(Image.fromarray((full * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2))).astype(np.float32)[..., None] / 255
    out = out * (1 - soft) + blurred * soft
    return Image.fromarray(out.clip(0, 255).astype(np.uint8)), ink, lines


def main():
    for kind, s in SHEETS.items():
        sheet = Image.open(os.path.join(HERE, "source", s["file"]))
        for row, skin in enumerate(s["skins"]):
            top = s["top"] + row * s["step"]
            plate = sheet.crop((s["left"], top, s["left"] + s["w"], top + s["h"]))
            img, ink, lines = blank(plate)
            path = os.path.join(OUT, f"title_plate_{skin.lower()}.webp")
            img.save(path, "WEBP", quality=92, method=6)
            hexink = "%02X%02X%02X" % tuple(int(round(c)) for c in ink)
            print(f"{skin} {kind} size={s['w']}x{s['h']} ink=#{hexink} "
                  f"date=({lines[0]['cx']:.0f},{lines[0]['cy']:.0f}) h={lines[0]['height']} w={lines[0]['width']} "
                  f"name=({lines[1]['cx']:.0f},{lines[1]['cy']:.0f}) h={lines[1]['height']} w={lines[1]['width']} "
                  f"-> {os.path.relpath(path, ROOT)} {os.path.getsize(path)}B")


main()
