# -*- coding: utf-8 -*-
"""남한 시·도(admin_level=4)와 그 아래 시·군·구(admin_level=6) 경계를 OSM에서 받아 raw GeoJSON으로 저장한다.
사용: python -I -X utf8 fetch_korea.py <work_dir>   → <work_dir>/korea_raw.json
 1) Overpass: 시·도 relation과, 시·도마다 그 안의 admin_level=6 relation 목록
 2) Nominatim lookup: relation id로 단순화된 폴리곤 받기 (50개씩)
"""
import json, sys, time, urllib.request, urllib.parse

UA = {"User-Agent": "kagom-dev-tools/0.1 (kagom prototype)", "Accept-Language": "ko"}


def get(url, data=None):
    req = urllib.request.Request(url, data=data, headers=UA)
    with urllib.request.urlopen(req, timeout=300) as r:
        return json.loads(r.read().decode("utf-8"))


def main(out):
    q = ('[out:json][timeout:250];area["ISO3166-1"="KR"]["admin_level"="2"]->.k;'
         'rel(area.k)["boundary"="administrative"]["admin_level"="4"];'
         'foreach{ out ids tags; map_to_area->.p; '
         'rel(area.p)["boundary"="administrative"]["admin_level"="6"]; out ids tags; }')
    d = get("https://overpass-api.de/api/interpreter", urllib.parse.urlencode({"data": q}).encode())
    provinces, cur = [], None
    for e in d["elements"]:
        t = e.get("tags", {})
        if t.get("admin_level") == "4":
            cur = {"id": e["id"], "name": t.get("name"), "tags": t, "children": []}
            provinces.append(cur)
        else:
            cur["children"].append({"id": e["id"], "name": t.get("name"), "tags": t})
    ids = [p["id"] for p in provinces] + [c["id"] for p in provinces for c in p["children"]]
    print("provinces", len(provinces), "units", len(ids) - len(provinces))
    geo = {}
    for i in range(0, len(ids), 50):
        time.sleep(1.2)
        chunk = ",".join(f"R{x}" for x in ids[i:i + 50])
        look = get("https://nominatim.openstreetmap.org/lookup?" + urllib.parse.urlencode(
            {"osm_ids": chunk, "format": "jsonv2", "polygon_geojson": 1, "polygon_threshold": 0.0003}))
        for x in look:
            geo[x["osm_id"]] = x["geojson"]
        print("lookup", i, len(look))
    missing = [x for x in ids if x not in geo]
    if missing:
        print("missing polygons:", missing)
    for p in provinces:
        p["geojson"] = geo.get(p["id"])
        for c in p["children"]:
            c["geojson"] = geo.get(c["id"])
    with open(f"{out}/korea_raw.json", "w", encoding="utf-8") as f:
        json.dump(provinces, f, ensure_ascii=False)


if __name__ == "__main__":
    main(sys.argv[1])
