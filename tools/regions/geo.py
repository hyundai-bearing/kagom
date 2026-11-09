# -*- coding: utf-8 -*-
"""경계 계산 도우미 (외부 라이브러리 없이)."""

def rings_of(geojson):
    """Polygon/MultiPolygon → [polygon[ring[[lon,lat]]]]"""
    return geojson["coordinates"] if geojson["type"] == "MultiPolygon" else [geojson["coordinates"]]

def in_ring(x, y, ring):
    inside = False
    j = len(ring) - 1
    for i in range(len(ring)):
        xi, yi = ring[i][0], ring[i][1]; xj, yj = ring[j][0], ring[j][1]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside

def point_in_unit(x, y, polys):
    for poly in polys:
        if in_ring(x, y, poly[0]) and not any(in_ring(x, y, h) for h in poly[1:]):
            return True
    return False

def simplify_ring(ring, tol):
    """닫힌 고리 단순화: 시작점에서 가장 먼 점으로 둘로 나눠 각각 Douglas-Peucker. 결과는 닫지 않은 점 목록."""
    pts = ring[:-1] if len(ring) > 1 and ring[0] == ring[-1] else list(ring)
    if len(pts) < 4: return pts
    x0, y0 = pts[0]
    far = max(range(len(pts)), key=lambda i: (pts[i][0] - x0) ** 2 + (pts[i][1] - y0) ** 2)
    a = simplify(pts[:far + 1], tol); b = simplify(pts[far:] + [pts[0]], tol)
    return a[:-1] + b[:-1]

def simplify(points, tol):
    """Douglas-Peucker (경위도 그대로, 위도 37도 근처라 경도 축소는 무시할 만함)."""
    if len(points) < 3: return points
    keep = [False] * len(points); keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = points[a]; bx, by = points[b]
        dx, dy = bx - ax, by - ay; L = (dx * dx + dy * dy) ** 0.5 or 1e-12
        best, idx = 0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / L
            if d > best: best, idx = d, i
        if best > tol:
            keep[idx] = True; stack += [(a, idx), (idx, b)]
    return [p for p, k in zip(points, keep) if k]

def ring_area(ring):
    s = 0
    for i in range(len(ring) - 1):
        s += ring[i][0] * ring[i + 1][1] - ring[i + 1][0] * ring[i][1]
    return abs(s) / 2
