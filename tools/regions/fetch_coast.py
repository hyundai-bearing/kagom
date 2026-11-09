# -*- coding: utf-8 -*-
"""남한 주변 해안선(natural=coastline)을 Overpass에서 타일로 나눠 받는다. 행정경계는 바다까지 그려져 있어
build_korea.py가 이 해안선으로 바다를 걷어 낸다.
사용: python -I -X utf8 fetch_coast.py <work_dir>   → <work_dir>/coast_raw.json ({"elements": [{id, geometry}]})
"""
import json, os, sys, time, urllib.request, urllib.parse

UA = {"User-Agent": "kagom-dev-tools/0.1 (kagom prototype)"}
SERVERS = ["https://maps.mail.ru/osm/tools/overpass/api/interpreter", "https://overpass-api.de/api/interpreter",
           "https://overpass.private.coffee/api/interpreter"]
BBOX = (124.4, 32.9, 132.0, 38.8)   # 서, 남, 동, 북
STEP = 2.0


def fetch_tile(s, w, n, e):
    q = f'[out:json][timeout:180];way["natural"="coastline"]({s},{w},{n},{e});out skel geom qt;'
    last = None
    for attempt in range(6):
        url = SERVERS[attempt % len(SERVERS)]
        try:
            req = urllib.request.Request(url, urllib.parse.urlencode({"data": q}).encode(), headers=UA)
            with urllib.request.urlopen(req, timeout=240) as r:
                return json.loads(r.read().decode("utf-8"))["elements"]
        except Exception as ex:  # 504/429 등: 다른 서버로 다시
            last = ex
            time.sleep(5 + attempt * 5)
    raise SystemExit(f"tile {s},{w} 실패: {last}")


def main(work):
    path = f"{work}/coast_raw.json"
    ways = {}
    tiles_done = set()
    if os.path.exists(path + ".partial"):
        part = json.load(open(path + ".partial", encoding="utf-8"))
        ways = {int(k): v for k, v in part["ways"].items()}; tiles_done = {tuple(t) for t in part["tiles"]}
    w0, s0, e0, n0 = BBOX
    lat = s0
    while lat < n0 - 1e-9:
        lon = w0
        while lon < e0 - 1e-9:
            t = (round(lat, 3), round(lon, 3))
            if t not in tiles_done:
                els = fetch_tile(lat, lon, min(lat + STEP, n0), min(lon + STEP, e0))
                for el in els:
                    if el.get("geometry"):
                        ways[el["id"]] = [[p["lon"], p["lat"]] for p in el["geometry"]]
                tiles_done.add(t)
                json.dump({"ways": ways, "tiles": sorted(tiles_done)}, open(path + ".partial", "w", encoding="utf-8"))
                print("tile", t, len(els), "total ways", len(ways), flush=True)
                time.sleep(2)
            lon += STEP
        lat += STEP
    json.dump({"elements": [{"id": k, "geometry": v} for k, v in ways.items()]}, open(path, "w", encoding="utf-8"))
    os.remove(path + ".partial")
    print("ways", len(ways), "points", sum(len(v) for v in ways.values()))


if __name__ == "__main__":
    main(sys.argv[1])
