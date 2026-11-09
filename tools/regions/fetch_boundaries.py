# -*- coding: utf-8 -*-
"""서울·인천 구/군 경계를 OpenStreetMap에서 받아 raw GeoJSON으로 저장한다.
사용: python -I fetch_boundaries.py <out_dir>
 1) Overpass: 시(admin_level=4) 안의 admin_level=6 relation 목록
 2) Nominatim lookup: relation id로 단순화된 폴리곤 받기
"""
import json, sys, time, urllib.request, urllib.parse

UA = {"User-Agent": "kagom-dev-tools/0.1 (kagom prototype)", "Accept-Language": "ko"}
CITIES = ["서울특별시", "인천광역시"]

def get(url, data=None):
    req = urllib.request.Request(url, data=data, headers=UA)
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.loads(r.read().decode("utf-8"))

def main(out):
    result = {}
    for city in CITIES:
        q = f'[out:json][timeout:120];area["name"="{city}"]["admin_level"="4"]->.a;rel(area.a)["boundary"="administrative"]["admin_level"="6"];out tags;'
        d = get("https://overpass-api.de/api/interpreter", urllib.parse.urlencode({"data": q}).encode())
        rels = [(e["id"], e["tags"].get("name"), e["tags"].get("name:en")) for e in d["elements"]]
        print(city, len(rels), [r[1] for r in rels])
        ids = ",".join(f"R{r[0]}" for r in rels)
        time.sleep(1.2)
        look = get("https://nominatim.openstreetmap.org/lookup?" + urllib.parse.urlencode(
            {"osm_ids": ids, "format": "jsonv2", "polygon_geojson": 1, "polygon_threshold": 0.0003}))
        result[city] = [{"osm_id": x["osm_id"], "name": x.get("name"), "geojson": x["geojson"]} for x in look]
        time.sleep(1.2)
    with open(f"{out}/boundaries_raw.json", "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False)

if __name__ == "__main__":
    main(sys.argv[1])
