# -*- coding: utf-8 -*-
"""landmarks_src.json의 랜드마크를 OSM Nominatim으로 좌표화하고, 그 구/군 경계 안인지 검사한다.
사용: python -I geocode_landmarks.py <work_dir>   (work_dir에 boundaries_raw.json 필요, geocoded.json 생성)
"""
import json, os, sys, time, urllib.request, urllib.parse
from geo import rings_of, point_in_unit

UA = {"User-Agent": "kagom-dev-tools/0.1 (bearing-test prototype)", "Accept-Language": "ko"}
HERE = os.path.dirname(os.path.abspath(__file__))

def search(q, bbox):
    x0, y0, x1, y1 = bbox
    params = {"q": q, "format": "jsonv2", "limit": 5, "viewbox": f"{x0},{y1},{x1},{y0}", "bounded": 1}
    req = urllib.request.Request("https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode(params), headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))

def main(work):
    raw = json.load(open(f"{work}/boundaries_raw.json", encoding="utf-8"))
    src = json.load(open(f"{HERE}/landmarks_src.json", encoding="utf-8"))
    cache_path = f"{work}/geocoded.json"
    cache = json.load(open(cache_path, encoding="utf-8")) if os.path.exists(cache_path) else {}
    report = []
    for city, units in src.items():
        if city.startswith("_"): continue
        geo_by_name = {u["name"]: u["geojson"] for u in raw[city]}
        for unit, lms in units.items():
            rings = rings_of(geo_by_name[unit])
            xs = [p[0] for poly in rings for p in poly[0]]; ys = [p[1] for poly in rings for p in poly[0]]
            bbox = (min(xs) - 0.01, min(ys) - 0.01, max(xs) + 0.01, max(ys) + 0.01)
            for lm in lms:
                key = f"{city}|{unit}|{lm['name']}"
                q = lm.get("q", lm["name"])
                if key not in cache or cache[key].get("q") != q:
                    res = search(q, bbox); time.sleep(1.1)
                    cache[key] = {"q": q, "results": [{"lat": float(r["lat"]), "lon": float(r["lon"]), "name": r.get("name"), "type": f"{r.get('category')}/{r.get('type')}", "display": r.get("display_name")} for r in res]}
                hit = next((r for r in cache[key]["results"] if point_in_unit(r["lon"], r["lat"], rings)), None)
                if hit:
                    report.append(f"OK   {unit} {lm['name']} -> {hit['name']} ({hit['type']}) {hit['lat']:.5f},{hit['lon']:.5f}")
                else:
                    first = cache[key]["results"][0] if cache[key]["results"] else None
                    report.append(f"FAIL {unit} {lm['name']} q={q} -> " + (f"{first['display']} {first['lat']:.5f},{first['lon']:.5f}" if first else "결과 없음"))
                cache[key]["hit"] = hit
    json.dump(cache, open(cache_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    open(f"{work}/geocode_report.txt", "w", encoding="utf-8").write("\n".join(report))
    print("fail:", sum(1 for r in report if r.startswith("FAIL")), "/", len(report))

if __name__ == "__main__":
    main(sys.argv[1])
