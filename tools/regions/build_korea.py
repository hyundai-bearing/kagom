# -*- coding: utf-8 -*-
"""korea_raw.json(fetch_korea.py) + coast_raw.json(fetch_coast.py) → app/src/main/assets/regions/korea_map.txt
사용: python -I -X utf8 build_korea.py <work_dir> [출력 경로]    (미리보기: <work_dir>/korea_preview.png)
     python은 numpy가 있으면 된다 (예: Blender 동봉 python).

- OSM 행정경계는 바다(해상 경계)까지 그려져 있어, 해안선으로 바다를 채운(flood fill) 육지 격자와 겹친 부분만 남긴다.
- 지도 L2 단위 (가이드 v1.2): 도 아래 시·군, 특별·광역시 아래 자치구·군. 아래 단위가 없는 세종은 시 전체 한 칸.
  전남광주통합특별시의 옛 광주 자치구는 '광주 동구'처럼 이름 앞에 '광주'를 붙인다.
- L3 읍·면·동은 build_dong.py가 이 파일을 읽어 따로 만들고, P·U 줄 끝에 L3 파일 이름·읍면동 수를 덧붙인다.
- 출력은 줄 단위 텍스트(앱이 라이브러리 없이 읽음). 좌표는 1e-4도 정수, 고리마다 첫 점 절댓값 + 나머지 차분.
"""
import json, math, os, sys, zlib, struct
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)   # -I는 스크립트 폴더를 빼므로 같은 폴더의 geo.py만 직접 넣는다
from geo import rings_of, simplify_ring
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "regions", "korea_map.txt")

BBOX = (124.4, 32.9, 132.0, 38.8)          # fetch_coast.py와 같게
DX, DY = 0.0015, 0.0012                    # 격자 칸 ≈ 135m × 133m
SEA_SEEDS = [(124.9, 35.0), (130.5, 36.5), (128.0, 33.6), (126.5, 32.95), (131.5, 35.0)]
UNIT_TOL = PROV_TOL = 0.0012               # 단순화 허용 오차(도). 같아야 시·도 경계와 시·군 경계가 겹친다
MIN_CELLS = 30                             # 이보다 작은 섬(≈0.5km²)은 뺀다
SMALL_OK = {"울릉군": 2}                    # 독도는 작아도 남긴다
SCALE = 10000

SHORT = {
    "서울특별시": "서울", "인천광역시": "인천", "경기도": "경기", "강원특별자치도": "강원", "충청북도": "충북",
    "충청남도": "충남", "세종특별자치시": "세종", "대전광역시": "대전", "전북특별자치도": "전북",
    "전남광주통합특별시": "전남광주", "경상북도": "경북", "경상남도": "경남", "대구광역시": "대구",
    "울산광역시": "울산", "부산광역시": "부산", "제주특별자치도": "제주",
}
# (예전에는 특별·광역시를 시 전체 한 칸으로 합쳤다. 가이드 v1.2부터 자치구·군으로 나눈다)
MERGE_GU = {"전남광주통합특별시": "광주"}   # 옛 광주 자치구 이름 앞에 붙일 말
LABEL_AT = {}                               # 자동 라벨 위치가 어색할 때 (id → (lon, lat))


# ───────────── 격자 ─────────────

class Grid:
    def __init__(self):
        self.x0, self.y0, self.x1, self.y1 = BBOX
        self.W = int(round((self.x1 - self.x0) / DX)); self.H = int(round((self.y1 - self.y0) / DY))

    def fx(self, lon): return (np.asarray(lon) - self.x0) / DX
    def fy(self, lat): return (np.asarray(lat) - self.y0) / DY

    def draw_polyline(self, mask, pts):
        """8-연결 선 (4-연결 flood fill이 못 새어 나가게)."""
        p = np.asarray(pts, float)
        if len(p) < 2: return
        x = self.fx(p[:, 0]); y = self.fy(p[:, 1])
        dx = np.diff(x); dy = np.diff(y)
        n = (np.ceil(np.maximum(np.abs(dx), np.abs(dy))) + 2).astype(int)
        seg = np.repeat(np.arange(len(n)), n)
        t = (np.arange(n.sum()) - np.repeat(np.cumsum(n) - n, n)) / np.repeat(n - 1, n)
        ii = np.floor(x[:-1][seg] + dx[seg] * t).astype(int); jj = np.floor(y[:-1][seg] + dy[seg] * t).astype(int)
        ok = (ii >= 0) & (ii < self.W) & (jj >= 0) & (jj < self.H)
        mask[jj[ok], ii[ok]] = True

    def rasterize(self, polys):
        """폴리곤들(구멍 포함, 짝홀 규칙) → (마스크 창, j0, i0). 창은 폴리곤 bbox."""
        edges = []
        for poly in polys:
            for ring in poly:
                r = np.asarray(ring, float)[:, :2]
                edges.append(np.column_stack([self.fx(r[:-1, 0]), self.fy(r[:-1, 1]), self.fx(r[1:, 0]), self.fy(r[1:, 1])]))
        E = np.vstack(edges)
        i0 = max(0, int(np.floor(min(E[:, 0].min(), E[:, 2].min()))) - 1); i1 = min(self.W, int(np.ceil(max(E[:, 0].max(), E[:, 2].max()))) + 1)
        j0 = max(0, int(np.floor(min(E[:, 1].min(), E[:, 3].min()))) - 1); j1 = min(self.H, int(np.ceil(max(E[:, 1].max(), E[:, 3].max()))) + 1)
        m = np.zeros((j1 - j0, i1 - i0), bool)
        xs = np.arange(i0, i1) + 0.5
        for j in range(j0, j1):
            y = j + 0.5
            sel = (E[:, 1] > y) != (E[:, 3] > y)
            if not sel.any(): continue
            e = E[sel]
            cx = np.sort(e[:, 0] + (y - e[:, 1]) * (e[:, 2] - e[:, 0]) / (e[:, 3] - e[:, 1]))
            row = m[j - j0]
            for k in range(0, len(cx) - 1, 2):
                row[(xs >= cx[k]) & (xs < cx[k + 1])] = True
        return m, j0, i0


def flood_sea(blocked, seeds):
    """줄 단위(span) flood fill, 4-연결."""
    H, W = blocked.shape
    filled = np.zeros_like(blocked)
    stack = list(seeds)
    while stack:
        i, j = stack.pop()
        if filled[j, i] or blocked[j, i]: continue
        free = ~(blocked[j] | filled[j])
        left = np.flatnonzero(~free[:i]); l = left[-1] + 1 if len(left) else 0
        right = np.flatnonzero(~free[i:]); r = i + right[0] if len(right) else W
        filled[j, l:r] = True
        for nj in (j - 1, j + 1):
            if 0 <= nj < H:
                f = ~(blocked[nj, l:r] | filled[nj, l:r])
                if f.any():
                    starts = np.flatnonzero(f & ~np.concatenate([[False], f[:-1]]))
                    stack.extend((l + s, nj) for s in starts)
    return filled


def land_mask(grid, coast):
    blocked = np.zeros((grid.H, grid.W), bool)
    for w in coast["elements"]:
        grid.draw_polyline(blocked, w["geometry"])
    seeds = []
    for lon, lat in SEA_SEEDS:
        i, j = int(grid.fx(lon)), int(grid.fy(lat))
        if blocked[j, i]: raise SystemExit(f"바다 시작점이 해안선 위: {lon},{lat}")
        seeds.append((i, j))
    sea = flood_sea(blocked, seeds)
    land = ~sea
    print("land cells", int(land.sum()), "of", land.size, flush=True)
    return land


# ───────────── 윤곽 ─────────────

def outlines(mask, j0, i0, min_cells):
    """마스크 윤곽 → 고리들 [(부호 있는 넓이, [(i, j) 격자 꼭짓점])]. 바깥 고리는 +, 구멍은 −."""
    p = np.pad(mask, 1)
    inside = p[1:-1, 1:-1]
    nxt = {}
    def add(a, b): nxt.setdefault(a, []).append(b)
    J, I = np.nonzero(inside & ~p[:-2, 1:-1])
    for j, i in zip(J.tolist(), I.tolist()): add((i, j), (i + 1, j))
    J, I = np.nonzero(inside & ~p[2:, 1:-1])
    for j, i in zip(J.tolist(), I.tolist()): add((i + 1, j + 1), (i, j + 1))
    J, I = np.nonzero(inside & ~p[1:-1, 2:])
    for j, i in zip(J.tolist(), I.tolist()): add((i + 1, j), (i + 1, j + 1))
    J, I = np.nonzero(inside & ~p[1:-1, :-2])
    for j, i in zip(J.tolist(), I.tolist()): add((i, j + 1), (i, j))
    loops = []
    while nxt:
        start = next(iter(nxt)); cur = start; prev_dir = None; loop = [start]
        while True:
            outs = nxt[cur]
            if len(outs) == 1 or prev_dir is None:
                b = outs.pop(0)
            else:   # 대각선으로만 붙은 칸: 오른쪽으로 꺾어 따로 떼어 낸다
                dx, dy = prev_dir
                right = (dy, -dx)
                b = min(outs, key=lambda o: 0 if (o[0] - cur[0], o[1] - cur[1]) == right else 1)
                outs.remove(b)
            if not outs: del nxt[cur]
            prev_dir = (b[0] - cur[0], b[1] - cur[1])
            cur = b
            if cur == start: break
            loop.append(cur)
        n = len(loop)
        area = sum(loop[k][0] * loop[(k + 1) % n][1] - loop[(k + 1) % n][0] * loop[k][1] for k in range(n)) / 2
        if abs(area) >= min_cells:
            loops.append((area, [(i0 + x, j0 + y) for x, y in loop]))
    return loops


def to_rings(grid, loops, tol):
    rings = []
    for area, loop in loops:
        pts = [(grid.x0 + x * DX, grid.y0 + y * DY) for x, y in loop]
        s = simplify_ring(pts + [pts[0]], tol)
        if len(s) >= 3: rings.append(s)
    return rings


def label_point(mask, j0, i0):
    """가장 안쪽 칸(침식을 끝까지 버틴 칸 중 무게중심에 가까운 것)."""
    m = mask.copy(); last = m
    while m.any():
        last = m
        e = m.copy()
        e[1:] &= m[:-1]; e[:-1] &= m[1:]; e[:, 1:] &= m[:, :-1]; e[:, :-1] &= m[:, 1:]
        e[0, :] = e[-1, :] = False; e[:, 0] = e[:, -1] = False
        m = e
    J, I = np.nonzero(last)
    cj, ci = J.mean(), I.mean()
    k = int(np.argmin((J - cj) ** 2 + (I - ci) ** 2))
    return BBOX[0] + (i0 + I[k] + 0.5) * DX, BBOX[1] + (j0 + J[k] + 0.5) * DY


# ───────────── 조립 ─────────────

def union(masks):
    j0 = min(m[1] for m in masks); i0 = min(m[2] for m in masks)
    j1 = max(m[1] + m[0].shape[0] for m in masks); i1 = max(m[2] + m[0].shape[1] for m in masks)
    u = np.zeros((j1 - j0, i1 - i0), bool)
    for m, mj, mi in masks:
        u[mj - j0:mj - j0 + m.shape[0], mi - i0:mi - i0 + m.shape[1]] |= m
    return u, j0, i0


def clip_land(grid, land, polys):
    m, j0, i0 = grid.rasterize(polys)
    return m & land[j0:j0 + m.shape[0], i0:i0 + m.shape[1]], j0, i0


def enc(v): return int(round(v * SCALE))


def ring_line(ring):
    out = [enc(ring[0][0]), enc(ring[0][1])]
    px, py = out
    for x, y in ring[1:]:
        ix, iy = enc(x), enc(y)
        out += [ix - px, iy - py]; px, py = ix, iy
    return "R|" + ",".join(map(str, out))


def main(work, out=OUT):
    raw = json.load(open(f"{work}/korea_raw.json", encoding="utf-8"))
    coast = json.load(open(f"{work}/coast_raw.json", encoding="utf-8"))
    grid = Grid()
    land = land_mask(grid, coast)
    provinces, units = [], []
    for p in raw:
        full = p["name"]; short = SHORT[full]
        # 지도 L2 (가이드 v1.2 SCR-05): 도는 시·군, 특별·광역시는 자치구·군. 아래 단위가 없는 시(세종)는 시 전체 한 칸.
        # 전남광주통합특별시의 옛 광주 자치구는 이름 앞에 '광주'를 붙인다 (도 안에서 동구·서구를 알아보게)
        kids = [c for c in p["children"] if c.get("geojson")]
        if not kids:
            parts = [(short, [p["geojson"]])]
        else:
            prefix = MERGE_GU.get(full)
            parts = [((f"{prefix} {c['name']}" if prefix and c["name"].endswith("구") else c["name"]), [c["geojson"]]) for c in kids]
        masks = []
        for name, geos in parts:
            polys = [poly for g in geos for poly in rings_of(g)]
            m = clip_land(grid, land, polys)
            if not m[0].any(): raise SystemExit(f"{full} {name}: 육지 없음")
            masks.append(m)
            uid = f"{short}/{name}"
            loops = outlines(*m, SMALL_OK.get(name, MIN_CELLS))
            rings = to_rings(grid, loops, UNIT_TOL)
            lx, ly = LABEL_AT.get(uid) or label_point(*m)
            units.append({"id": uid, "name": name, "province": short, "label": (lx, ly), "rings": rings})
        pm = union(masks)
        rings = to_rings(grid, outlines(*pm, MIN_CELLS), PROV_TOL)
        lx, ly = LABEL_AT.get(short) or label_point(*pm)
        provinces.append({"id": short, "name": full, "label": (lx, ly), "rings": rings})
        print(f"{short}: {len(parts)}칸", flush=True)

    lines = [
        "# 카곰 지도: 남한 시·도 / 시·군 경계 (tools/regions/build_korea.py가 만듦, 손으로 고치지 말 것)",
        "# 경계 © OpenStreetMap contributors (ODbL)",
        f"# 좌표 = 경도·위도 × {SCALE} 정수. R 줄 = 고리 (첫 점 절댓값, 나머지는 앞 점과의 차분)",
        "# P|시·도 id|이름|라벨 경도|라벨 위도 ,  U|시 id|이름|시·도 id|라벨 경도|라벨 위도",
    ]
    for p in provinces:
        lines.append(f"P|{p['id']}|{p['name']}|{enc(p['label'][0])}|{enc(p['label'][1])}")
        lines += [ring_line(r) for r in p["rings"]]
    for u in units:
        lines.append(f"U|{u['id']}|{u['name']}|{u['province']}|{enc(u['label'][0])}|{enc(u['label'][1])}")
        lines += [ring_line(r) for r in u["rings"]]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    pts = sum(len(r) for u in units for r in u["rings"]) + sum(len(r) for p in provinces for r in p["rings"])
    print(f"provinces={len(provinces)} units={len(units)} points={pts} size={os.path.getsize(out)}B")
    write_preview(provinces, units, f"{work}/korea_preview.png")


# ───────────── 미리보기 PNG ─────────────

def write_preview(provinces, units, path, W=1400):
    x0, y0, x1, y1 = 124.5, 33.0, 131.95, 38.7
    c = math.cos(math.radians(36)); k = W / ((x1 - x0) * c); H = int((y1 - y0) * k)
    img = np.zeros((H, W, 3), np.uint8); img[:] = (195, 221, 226)
    P = lambda lon, lat: ((lon - x0) * c * k, (y1 - lat) * k)

    def fill(rings, col):
        E = []
        for r in rings:
            q = [P(*pt) for pt in r] + [P(*r[0])]
            E += [(a[0], a[1], b[0], b[1]) for a, b in zip(q, q[1:])]
        E = np.array(E); xs = np.arange(W) + 0.5
        for j in range(max(0, int(E[:, [1, 3]].min())), min(H, int(E[:, [1, 3]].max()) + 1)):
            y = j + 0.5; sel = (E[:, 1] > y) != (E[:, 3] > y)
            if not sel.any(): continue
            e = E[sel]; cx = np.sort(e[:, 0] + (y - e[:, 1]) * (e[:, 2] - e[:, 0]) / (e[:, 3] - e[:, 1]))
            for kk in range(0, len(cx) - 1, 2): img[j, (xs >= cx[kk]) & (xs < cx[kk + 1])] = col

    def stroke(rings, col):
        for r in rings:
            q = [P(*pt) for pt in r] + [P(*r[0])]
            for a, b in zip(q, q[1:]):
                n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) + 1
                ii = np.linspace(a[0], b[0], n).astype(int); jj = np.linspace(a[1], b[1], n).astype(int)
                ok = (ii >= 0) & (ii < W) & (jj >= 0) & (jj < H); img[jj[ok], ii[ok]] = col

    rng = np.random.default_rng(3)
    for u in units: fill(u["rings"], tuple(int(v) for v in rng.integers(170, 240, 3)))
    for u in units: stroke(u["rings"], (140, 140, 140))
    for p in provinces: stroke(p["rings"], (30, 30, 30))
    for u in units:
        x, y = P(*u["label"]); img[max(0, int(y) - 1):int(y) + 2, max(0, int(x) - 1):int(x) + 2] = (200, 30, 30)
    raw = b"".join(b"\x00" + img[y].tobytes() for y in range(H))
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0))
                           + ch(b"IDAT", zlib.compress(raw, 6)) + ch(b"IEND", b""))


if __name__ == "__main__":
    main(sys.argv[1], *sys.argv[2:3])
