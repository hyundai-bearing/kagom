"""Convert the Gangneung SGIS GeoJSON into the app's D/R boundary records.

Usage: python build_gangneung.py <HangJeongDong_ver20261001.geojson>
Source and license are recorded in docs/IMPLEMENTATION.md.
"""
import json
import sys
from pathlib import Path

from geo import in_ring, ring_area, rings_of, simplify_ring


OUT = Path(__file__).resolve().parents[2] / "app/src/test/resources/regions/gangneung_districts.txt"
PARENT = "강원/강릉시"


def ring_line(ring):
    previous_x = previous_y = 0
    values = []
    for lon, lat in ring:
        x, y = round(lon * 10_000), round(lat * 10_000)
        values.extend((x - previous_x, y - previous_y))
        previous_x, previous_y = x, y
    return "R|" + ",".join(map(str, values))


def label_point(ring):
    west, south = map(min, zip(*ring))
    east, north = map(max, zip(*ring))
    cx, cy = (west + east) / 2, (south + north) / 2
    candidates = [
        (west + (east - west) * i / 20, south + (north - south) * j / 20)
        for i in range(1, 20)
        for j in range(1, 20)
    ]
    inside = (point for point in candidates if in_ring(*point, ring))
    return min(inside, key=lambda p: (p[0] - cx) ** 2 + (p[1] - cy) ** 2, default=ring[0])


def build(features):
    groups = {}
    dong_count = 0
    for feature in features:
        props = feature["properties"]
        if props["sgg"] != "51150":
            continue
        name = props["adm_nm"].split()[-1]
        key = props["adm_cd2"]
        if name.endswith("동"):
            key = "center"
            dong_count += 1
        region = groups.setdefault(key, {"name": name, "rings": []})
        for polygon in rings_of(feature["geometry"]):
            for raw in polygon:
                if ring_area(raw) < 0.000001:
                    continue
                ring = simplify_ring(raw, 0.00015)
                if len(ring) >= 3:
                    region["rings"].append(ring)
    groups["center"]["name"] = f"시내 {dong_count}개 동"
    yield "# SGIS (공공누리 제1유형), vuski/admdongkor (CC BY 4.0), 2026-10-01"
    for key, region in groups.items():
        rings = region["rings"]
        largest = max(rings, key=lambda r: ring_area([*r, r[0]]))
        lon, lat = label_point(largest)
        yield f"D|{PARENT}/{key}|{region['name']}|{PARENT}|{round(lon * 10_000)}|{round(lat * 10_000)}"
        yield from map(ring_line, rings)


if __name__ == "__main__":
    features = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8-sig"))["features"]
    OUT.write_text("\n".join(build(features)) + "\n", encoding="utf-8")
