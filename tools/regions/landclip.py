# -*- coding: utf-8 -*-
"""OSM 행정경계에는 바다(해상 경계)가 들어 있어 섬 지역이 바다까지 넓게 그려진다.
해안선(natural=coastline)으로 바다를 채워(flood fill) 육지 마스크를 만들고, 경계 ∩ 육지의 윤곽을 다시 그린다.
"""
from collections import deque
import numpy as np
from geo import rings_of, simplify_ring

class LandGrid:
    def __init__(self, coast_json, bbox, dx=0.0008, dy=0.0006, sea_seeds=()):
        self.x0, self.y0, self.x1, self.y1 = bbox
        self.dx, self.dy = dx, dy
        self.W = int(round((self.x1 - self.x0) / dx)); self.H = int(round((self.y1 - self.y0) / dy))
        coast = np.zeros((self.H, self.W), bool)
        for e in coast_json["elements"]:
            g = e.get("geometry") or []
            for a, b in zip(g, g[1:]):
                i0, j0 = self.cell(a["lon"], a["lat"]); i1, j1 = self.cell(b["lon"], b["lat"])
                n = max(abs(i1 - i0), abs(j1 - j0)) + 1
                ii = np.rint(np.linspace(i0, i1, n)).astype(int); jj = np.rint(np.linspace(j0, j1, n)).astype(int)
                ok = (ii >= 0) & (ii < self.W) & (jj >= 0) & (jj < self.H)
                coast[jj[ok], ii[ok]] = True
        sea = np.zeros_like(coast)
        q = deque()
        for lon, lat in sea_seeds:
            i, j = self.cell(lon, lat)
            if coast[j, i]: raise ValueError(f"바다 시작점이 해안선 위: {lon},{lat}")
            sea[j, i] = True; q.append((i, j))
        W, H = self.W, self.H
        while q:
            i, j = q.popleft()
            for ni, nj in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)):
                if 0 <= ni < W and 0 <= nj < H and not sea[nj, ni] and not coast[nj, ni]:
                    sea[nj, ni] = True; q.append((ni, nj))
        self.land = ~sea

    def cell(self, lon, lat):
        return int((lon - self.x0) / self.dx), int((lat - self.y0) / self.dy)

    def rasterize(self, geojson):
        """폴리곤(구멍 포함)을 칸 중심 기준으로 채운다 (짝홀 규칙)."""
        m = np.zeros((self.H, self.W), bool)
        edges = []
        for poly in rings_of(geojson):
            for ring in poly:
                for a, b in zip(ring, ring[1:]):
                    edges.append((a[0], a[1], b[0], b[1]))
        E = np.array(edges)
        xs = self.x0 + (np.arange(self.W) + 0.5) * self.dx
        for j in range(self.H):
            y = self.y0 + (j + 0.5) * self.dy
            sel = (E[:, 1] > y) != (E[:, 3] > y)
            if not sel.any(): continue
            e = E[sel]
            cx = np.sort(e[:, 0] + (y - e[:, 1]) * (e[:, 2] - e[:, 0]) / (e[:, 3] - e[:, 1]))
            for k in range(0, len(cx) - 1, 2):
                m[j, (xs >= cx[k]) & (xs < cx[k + 1])] = True
        return m

    def outlines(self, mask, min_cells=40, tol=0.0008):
        """마스크 윤곽 → 바깥 고리들(경위도, 단순화). 안쪽에 오른손을 대고 도는 방식으로 칸 경계를 잇는다."""
        p = np.pad(mask, 1)
        inside = p[1:-1, 1:-1]
        nxt = {}
        def add(a, b): nxt.setdefault(a, []).append(b)
        J, I = np.nonzero(inside & ~p[:-2, 1:-1])   # 아래가 밖
        for j, i in zip(J, I): add((i, j), (i + 1, j))
        J, I = np.nonzero(inside & ~p[2:, 1:-1])    # 위가 밖
        for j, i in zip(J, I): add((i + 1, j + 1), (i, j + 1))
        J, I = np.nonzero(inside & ~p[1:-1, 2:])    # 오른쪽이 밖
        for j, i in zip(J, I): add((i + 1, j), (i + 1, j + 1))
        J, I = np.nonzero(inside & ~p[1:-1, :-2])   # 왼쪽이 밖
        for j, i in zip(J, I): add((i, j + 1), (i, j))
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
            area = sum(loop[k][0] * loop[(k + 1) % len(loop)][1] - loop[(k + 1) % len(loop)][0] * loop[k][1] for k in range(len(loop))) / 2
            if area >= min_cells:   # 바깥 고리만(반시계 = 양수), 작은 섬은 뺀다
                pts = [(round(self.x0 + x * self.dx, 4), round(self.y0 + y * self.dy, 4)) for x, y in loop]
                loops.append(simplify_ring(pts + [pts[0]], tol))
        return loops
