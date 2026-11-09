# -*- coding: utf-8 -*-
"""남한 읍·면·동(admin_level=8) 경계를 OSM에서 받아 raw GeoJSON으로 저장한다 (지도 L3 구획).
사용: python -I -X utf8 fetch_dong.py <work_dir>   → <work_dir>/dong_raw.json  ([{id, name, tags, geojson}])
 1) Overpass: 남한 안의 admin_level=8 relation id·이름 (어느 시·군·구 소속인지는 build_dong.py가 좌표로 정한다)
 2) Nominatim lookup: relation id로 단순화된 폴리곤 받기 (50개씩, 1초 간격). 중간에 멈추면 이어서 받는다
"""
import json, os, sys, time, urllib.request, urllib.parse

UA = {"User-Agent": "kagom-dev-tools/0.1 (bearing-test prototype)", "Accept-Language": "ko"}
OVERPASS = ["https://maps.mail.ru/osm/tools/overpass/api/interpreter", "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"]
THRESHOLD = 0.0001   # Nominatim 단순화(도, ≈10 m). build_dong.py에서 다시 단순화한다


def get(url, data=None, timeout=300):
    req = urllib.request.Request(url, data=data, headers=UA)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def list_dongs():
    # admin_level=8 + 레벨 태그가 빠진 읍·면·동 (예: 강릉 성산면은 admin_level이 없다)
    q = ('[out:json][timeout:300];area["ISO3166-1"="KR"]["admin_level"="2"]->.k;'
         '(rel(area.k)["boundary"="administrative"]["admin_level"="8"];'
         'rel(area.k)["boundary"="administrative"][!"admin_level"]["name"~"(읍|면|동)$"];);out ids tags;')
    last = None
    for attempt in range(6):
        try:
            d = get(OVERPASS[attempt % len(OVERPASS)], urllib.parse.urlencode({"data": q}).encode())
            return [{"id": e["id"], "name": e.get("tags", {}).get("name"), "tags": e.get("tags", {})} for e in d["elements"]]
        except Exception as ex:  # 504/429: 다른 서버로 다시
            last = ex
            time.sleep(5 + attempt * 5)
    raise SystemExit(f"Overpass 실패: {last}")


def main(work):
    path = f"{work}/dong_raw.json"
    part = path + ".partial"
    if os.path.exists(part):
        state = json.load(open(part, encoding="utf-8"))
    else:
        # 이미 받은 것이 있으면 폴리곤은 다시 받지 않는다 (목록만 새로 받아 빠진 것만 채운다)
        old = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else []
        geo = {str(d["id"]): d["geojson"] for d in old if d.get("geojson")}
        state = {"dongs": list_dongs(), "geo": geo}
        json.dump(state, open(part, "w", encoding="utf-8"), ensure_ascii=False)
    dongs, geo = state["dongs"], state["geo"]
    print("dongs", len(dongs), "have polygons", len(geo), flush=True)
    todo = [d["id"] for d in dongs if str(d["id"]) not in geo]
    for i in range(0, len(todo), 50):
        chunk = ",".join(f"R{x}" for x in todo[i:i + 50])
        for attempt in range(5):
            try:
                look = get("https://nominatim.openstreetmap.org/lookup?" + urllib.parse.urlencode(
                    {"osm_ids": chunk, "format": "jsonv2", "polygon_geojson": 1, "polygon_threshold": THRESHOLD}), timeout=120)
                break
            except Exception as ex:
                print("retry", i, ex, flush=True)
                time.sleep(10 + attempt * 10)
        else:
            raise SystemExit(f"Nominatim 실패: chunk {i}")
        for x in look:
            geo[str(x["osm_id"])] = x["geojson"]
        json.dump(state, open(part, "w", encoding="utf-8"), ensure_ascii=False)
        print("lookup", i + len(todo[i:i + 50]), "/", len(todo), flush=True)
        time.sleep(1.1)
    missing = [d["id"] for d in dongs if str(d["id"]) not in geo]
    if missing:
        print("missing polygons:", len(missing), missing[:20])
    for d in dongs:
        d["geojson"] = geo.get(str(d["id"]))
    json.dump(dongs, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    os.remove(part)
    print("done", len(dongs))


if __name__ == "__main__":
    main(sys.argv[1])
