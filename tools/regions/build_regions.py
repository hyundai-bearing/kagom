# -*- coding: utf-8 -*-
"""geocoded.json + boundaries_raw.json → app/src/main/assets/regions/regions.json (+ 미리보기 HTML)
사용: python -E -s build_regions.py <work_dir>
- 지도용 경계는 Douglas-Peucker로 단순화하고, 아주 작은 섬은 뺀다.
- 같은 구 안에서 랜드마크끼리 너무 가까우면(한 번 정차로 둘 다 인정될 수 있으면) 경고한다.
"""
import json, math, os, sys
from geo import rings_of, simplify_ring, ring_area

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "regions", "regions.json")
CITY_IDS = {"서울특별시": ("seoul", "서울"), "인천광역시": ("incheon", "인천")}
DEFAULT_R = 400
TOL = 0.0010          # 단순화 허용 오차(도) ≈ 100m
MIN_AREA = 0.00004    # 이보다 작은 섬은 지도에서 뺌(≈ 0.4km²)
# 옹진군은 백령도까지 뻗어 지도가 너무 넓어진다 → 차로 갈 수 있는 영흥·선재 부근만 그린다
CLIP = {"옹진군": (126.40, 37.20, 126.56, 37.30)}
# 해안선으로 바다를 잘라 낼 도시: (격자 범위, 바다 시작점들)
LAND_CLIP = {"인천광역시": ((126.0, 37.15, 126.85, 37.90), [(126.05, 37.40), (126.30, 37.20), (126.05, 37.80), (126.25, 37.55)])}

def meters(a, b):
    R = 6371000; p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp = p2 - p1; dl = math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))

def unit_id(city_id, name):
    return f"{city_id}-{name}"

def main(work):
    raw = json.load(open(f"{work}/boundaries_raw.json", encoding="utf-8"))
    geo = json.load(open(f"{work}/geocoded.json", encoding="utf-8"))
    src = json.load(open(f"{HERE}/landmarks_src.json", encoding="utf-8"))
    cities, warnings = [], []
    for city, units in src.items():
        if city.startswith("_"): continue
        cid, cname = CITY_IDS[city]
        by_name = {u["name"]: u["geojson"] for u in raw[city]}
        grid = None
        if city in LAND_CLIP:
            from landclip import LandGrid
            bbox, seeds = LAND_CLIP[city]
            grid = LandGrid(json.load(open(f"{work}/coastline_raw.json", encoding="utf-8")), bbox, sea_seeds=seeds)
        out_units = []
        for unit, lms in units.items():
            shapes = []
            polys = rings_of(by_name[unit])
            if grid is not None:
                land_rings = grid.outlines(grid.rasterize(by_name[unit]) & grid.land)
                polys = [[[list(p) for p in r] + [list(r[0])]] for r in land_rings]
            for poly in polys:
                outer = poly[0]
                if ring_area(outer) < MIN_AREA: continue
                if unit in CLIP:
                    x0, y0, x1, y1 = CLIP[unit]
                    if not any(x0 <= p[0] <= x1 and y0 <= p[1] <= y1 for p in outer): continue
                s = outer[:-1] if grid is not None else simplify_ring([(round(p[0], 4), round(p[1], 4)) for p in outer], TOL)
                shapes.append([c for p in s for c in p])
            if not shapes: raise SystemExit(f"{unit}: 그릴 경계가 없음")
            out_lms = []
            for i, lm in enumerate(lms):
                hit = geo[f"{city}|{unit}|{lm['name']}"]["hit"]
                if not hit: raise SystemExit(f"{unit} {lm['name']}: 좌표 없음 (geocode_landmarks.py 먼저)")
                out_lms.append({"id": f"{unit_id(cid, unit)}-{i + 1}", "name": lm["name"],
                                "lat": round(hit["lat"], 5), "lon": round(hit["lon"], 5), "r": lm.get("r", DEFAULT_R)})
            for i in range(len(out_lms)):
                for j in range(i + 1, len(out_lms)):
                    a, b = out_lms[i], out_lms[j]
                    d = meters((a["lat"], a["lon"]), (b["lat"], b["lon"]))
                    if d < max(a["r"], b["r"]) + 100:
                        warnings.append(f"{unit}: {a['name']}↔{b['name']} {d:.0f}m (반경 {a['r']}/{b['r']})")
            out_units.append({"id": unit_id(cid, unit), "name": unit, "shapes": shapes, "landmarks": out_lms})
        cities.append({"id": cid, "name": cname, "units": out_units})
    data = {"version": 1, "source": "경계·좌표 © OpenStreetMap contributors (ODbL)", "cities": cities}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    write_preview(data, f"{work}/preview.html")
    open(f"{work}/build_report.txt", "w", encoding="utf-8").write(
        f"size={os.path.getsize(OUT)}B units={sum(len(c['units']) for c in cities)} pts={sum(len(s)//2 for c in cities for u in c['units'] for s in u['shapes'])}\n" + "\n".join(warnings))

def write_preview(data, path):
    parts = []
    for c in data["cities"]:
        xs = [s[i] for u in c["units"] for s in u["shapes"] for i in range(0, len(s), 2)]
        ys = [s[i] for u in c["units"] for s in u["shapes"] for i in range(1, len(s), 2)]
        x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
        k = 560 / max((x1 - x0) * math.cos(math.radians(37.5)), y1 - y0)
        P = lambda x, y: ((x - x0) * math.cos(math.radians(37.5)) * k + 20, (y1 - y) * k + 20)
        svg = []
        for u in c["units"]:
            for s in u["shapes"]:
                pts = " ".join("%.1f,%.1f" % P(s[i], s[i + 1]) for i in range(0, len(s), 2))
                svg.append(f'<polygon points="{pts}" fill="#dfe6ee" stroke="#567" stroke-width="1"/>')
            for lm in u["landmarks"]:
                x, y = P(lm["lon"], lm["lat"])
                svg.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="3" fill="#e5533d"/>')
        parts.append(f'<h3>{c["name"]}</h3><svg width="600" height="600">{"".join(svg)}</svg>')
    open(path, "w", encoding="utf-8").write('<meta charset="utf-8"><body style="display:flex;gap:16px">' + "".join(f"<div>{p}</div>" for p in parts))

if __name__ == "__main__":
    main(sys.argv[1])
