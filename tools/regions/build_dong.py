# -*- coding: utf-8 -*-
"""지도 L3 읍·면·동 (가이드 v1.2 SCR-05 3단계 지도).
dong_raw.json(fetch_dong.py) + korea_map.txt(build_korea.py) → assets/regions/l3/<시·도>.txt, korea_map.txt 갱신

사용 (shapely 2.1 이상 필요):
  uv run --no-project --with "shapely>=2.1" --with numpy python -I -X utf8 tools/regions/build_dong.py <work_dir>

- 읍·면·동마다 안쪽 한 점(representative point)이 든 시·군·구에 넣는다 (OSM 관계 소속보다 확실하다).
- 시·군·구 경계(이미 해안선으로 바다를 걷어 낸 것)와 겹치는 부분만 남겨 바다를 지운다.
- 이웃 구획과 맞닿은 선이 어긋나지 않게 coverage_simplify로 한꺼번에 단순화한다.
- 읍·면·동 자료가 없는 시·군·구는 시·군·구 전체를 구획 하나로 둔다. 자료가 빠진 자리는 가장 길게 맞닿은 이웃 구획에 붙인다.
- korea_map.txt: P 줄 끝에 L3 파일 이름, U 줄 끝에 읍·면·동 수를 붙인다 (앱이 L3를 읽지 않고도 '해금 n / N'을 센다).
출력 형식은 korea_map.txt와 같다: A|구획 id|이름|시·군·구 id|라벨 경도|라벨 위도 + R 줄(첫 점 절댓값, 나머지 차분).
"""
import json, os, sys
import shapely
from shapely.geometry import shape, Polygon, MultiPolygon
from shapely.ops import polylabel
from shapely.strtree import STRtree

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
MAP = os.path.join(ROOT, "app", "src", "main", "assets", "regions", "korea_map.txt")
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "regions", "l3")
SCALE = 10000
TOL = 0.00025          # 단순화 허용 오차(도) ≈ 25 m. 강릉 L3에서 1 px보다 작다
MIN_AREA = 3e-7        # 이보다 작은 조각(≈3,000 m²)은 버린다 (해안 자투리)
ROMAN = {"서울": "seoul", "부산": "busan", "대구": "daegu", "인천": "incheon", "광주": "gwangju", "대전": "daejeon",
         "울산": "ulsan", "세종": "sejong", "경기": "gyeonggi", "강원": "gangwon", "충북": "chungbuk", "충남": "chungnam",
         "전북": "jeonbuk", "전남": "jeonnam", "전남광주": "jeonnam_gwangju", "경북": "gyeongbuk", "경남": "gyeongnam", "제주": "jeju"}


def decode_ring(line):
    v = [int(x) for x in line[2:].split(",")]
    pts, x, y = [], 0, 0
    for i in range(0, len(v), 2):
        if i == 0: x, y = v[0], v[1]
        else: x += v[i]; y += v[i + 1]
        pts.append((x / SCALE, y / SCALE))
    return pts


def enc(v): return int(round(v * SCALE))


def ring_line(coords):
    pts = [(enc(x), enc(y)) for x, y in coords]
    if len(pts) > 1 and pts[0] == pts[-1]: pts = pts[:-1]       # 닫는 점은 앱이 알아서 잇는다
    dedup = [pts[0]] + [p for i, p in enumerate(pts[1:], 1) if p != pts[i - 1]]
    out = [dedup[0][0], dedup[0][1]]
    for (ax, ay), (bx, by) in zip(dedup, dedup[1:]):
        out += [bx - ax, by - ay]
    return "R|" + ",".join(map(str, out))


def read_map(path):
    """korea_map.txt → [(헤더 필드, 고리들)] 순서 그대로"""
    blocks, head, rings = [], None, []
    for line in open(path, encoding="utf-8").read().splitlines():
        if not line or line[0] == "#": continue
        if line[0] in "PU":
            if head: blocks.append((head, rings))
            head, rings = line.split("|"), []
        elif line[0] == "R":
            rings.append(decode_ring(line))
    if head: blocks.append((head, rings))
    return blocks


def even_odd(rings):
    """앱과 같은 짝홀 규칙으로 고리들을 한 도형으로"""
    polys = [Polygon(r).buffer(0) for r in rings if len(r) >= 3]
    g = polys[0]
    for p in polys[1:]: g = g.symmetric_difference(p)
    return g.buffer(0)


def polygons(g):
    if g.is_empty: return []
    if isinstance(g, Polygon): return [g]
    if isinstance(g, MultiPolygon): return list(g.geoms)
    return [p for p in getattr(g, "geoms", []) if isinstance(p, Polygon)]


def ring_lines(g):
    out = []
    for p in polygons(g):
        if p.area < MIN_AREA / 4: continue
        out.append(ring_line(p.exterior.coords))
        out += [ring_line(h.coords) for h in p.interiors if Polygon(h).area > MIN_AREA / 4]
    return out


def label_of(g):
    big = max(polygons(g), key=lambda p: p.area)
    pt = polylabel(big, tolerance=0.0003)
    return pt.x, pt.y


def main(work):
    blocks = read_map(MAP)
    provinces = [h for h, _ in blocks if h[0] == "P"]
    units = [(h, even_odd(r)) for h, r in blocks if h[0] == "U"]
    tree = STRtree([g for _, g in units])

    dongs = json.load(open(f"{work}/dong_raw.json", encoding="utf-8"))
    per_unit = {h[1]: [] for h, _ in units}
    skipped = 0
    for d in dongs:
        if not d.get("geojson") or not d.get("name"): skipped += 1; continue
        g = shape(d["geojson"]).buffer(0)
        if g.is_empty: skipped += 1; continue
        rp = g.representative_point()
        hits = [i for i in tree.query(rp, predicate="intersects")]
        i = hits[0] if hits else int(tree.nearest(rp))
        uh, ug = units[i]
        clip = g.intersection(ug).buffer(0)
        if clip.area < MIN_AREA: skipped += 1; continue
        per_unit[uh[1]].append((d["name"], clip))
    print("dongs", len(dongs), "used", sum(len(v) for v in per_unit.values()), "skipped", skipped, flush=True)

    lines_by_prov = {p[1]: [] for p in provinces}
    counts = {}
    merged = 0
    for uh, ug in units:
        uid, uname, prov = uh[1], uh[2], uh[3]
        items = per_unit[uid] or [(uname, ug)]
        # OSM에 없는 읍·면·동 자리(예: 강릉 홍제동)는 구멍이 되지 않게 가장 길게 맞닿은 이웃 구획에 붙인다
        if per_unit[uid]:
            rest = ug.difference(shapely.union_all([g for _, g in items])).buffer(0)
            for piece in polygons(rest):
                if piece.area < MIN_AREA: continue
                near = piece.buffer(0.0005)
                shared = [g.intersection(near).area for _, g in items]
                k = max(range(len(items)), key=lambda i: shared[i])
                if shared[k] <= 0: continue
                items[k] = (items[k][0], items[k][1].union(piece).buffer(0))
                merged += 1
        # 같은 이름이 둘이면 뒤에 번호
        seen = {}
        named = []
        for name, g in sorted(items, key=lambda t: t[0]):
            seen[name] = seen.get(name, 0) + 1
            named.append((name if seen[name] == 1 else f"{name} {seen[name]}", g))
        geoms = [g for _, g in named]
        try:
            simp = list(shapely.coverage_simplify(geoms, TOL))
        except Exception as ex:
            print("coverage_simplify 실패 → 하나씩", uid, ex)
            simp = [shapely.simplify(g, TOL, preserve_topology=True) for g in geoms]
        n = 0
        for (name, g0), g in zip(named, simp):
            g = g.buffer(0)
            if g.is_empty: g = g0
            lx, ly = label_of(g)
            lines_by_prov[prov].append(f"A|{uid}/{name}|{name}|{uid}|{enc(lx)}|{enc(ly)}")
            lines_by_prov[prov] += ring_lines(g)
            n += 1
        counts[uid] = n

    os.makedirs(OUT_DIR, exist_ok=True)
    files = {}
    for idx, p in enumerate(provinces):
        files[p[1]] = ROMAN.get(p[1], f"p{idx}")
        path = os.path.join(OUT_DIR, files[p[1]] + ".txt")
        head = ["# 카곰 지도 L3: " + p[2] + " 읍·면·동 (tools/regions/build_dong.py가 만듦, 손으로 고치지 말 것)",
                "# 경계 © OpenStreetMap contributors (ODbL)",
                "# A|구획 id|이름|시·군·구 id|라벨 경도|라벨 위도 + R 줄 (korea_map.txt와 같은 좌표 형식)"]
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(head + lines_by_prov[p[1]]) + "\n")
        print(f"{p[1]}: {sum(1 for l in lines_by_prov[p[1]] if l[0] == 'A')}구획 {os.path.getsize(path)}B")

    # korea_map.txt: P 줄에 L3 파일 이름, U 줄에 읍·면·동 수 (이미 붙어 있으면 바꾼다)
    out = []
    for line in open(MAP, encoding="utf-8").read().splitlines():
        if line.startswith("P|"):
            f = line.split("|")[:5]; out.append("|".join(f + [files[f[1]]]))
        elif line.startswith("U|"):
            f = line.split("|")[:6]; out.append("|".join(f + [str(counts[f[1]])]))
        elif line.startswith("# P|"):
            out.append("# P|시·도 id|이름|라벨 경도|라벨 위도|L3 파일 ,  U|시·군·구 id|이름|시·도 id|라벨 경도|라벨 위도|읍·면·동 수")
        else:
            out.append(line)
    with open(MAP, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    total = sum(counts.values())
    print("units", len(units), "areas", total, "landmark slots", total * 3, "merged gaps", merged)


if __name__ == "__main__":
    main(sys.argv[1])
