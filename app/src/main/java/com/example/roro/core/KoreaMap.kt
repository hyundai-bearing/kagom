package com.example.roro.core

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * 남한 지도 (SCR-05). 데이터: assets/regions/korea_map.txt (tools/regions/build_korea.py가 OSM에서 만듦)
 * 지도 칸은 두 겹이다: 시·도(province)와 그 안의 '시'(unit; 도 아래 시·군, 특별·광역시는 시 전체).
 */

/** 경위도 사각형 */
data class GeoBounds(val west: Float, val south: Float, val east: Float, val north: Float) {
    fun union(o: GeoBounds) = GeoBounds(min(west, o.west), min(south, o.south), max(east, o.east), max(north, o.north))
}

/**
 * 지도 한 칸. [rings] = 경계 고리들 (경도, 위도, 경도, 위도 …). 구멍(다른 칸을 둘러싼 칸)도 고리로 들어 있어
 * 짝홀 규칙으로 안쪽을 판단한다.
 */
class MapRegion(
    val id: String,
    val name: String,
    /** 시·군의 상위 시·도 ID 또는 구획의 상위 시·군 ID. 시·도는 null. */
    val provinceId: String?,
    val labelLon: Float,
    val labelLat: Float,
    val rings: List<FloatArray>,
) {
    val bounds: GeoBounds = run {
        var w = Float.MAX_VALUE; var s = Float.MAX_VALUE; var e = -Float.MAX_VALUE; var n = -Float.MAX_VALUE
        for (r in rings) for (i in r.indices step 2) {
            w = min(w, r[i]); e = max(e, r[i]); s = min(s, r[i + 1]); n = max(n, r[i + 1])
        }
        GeoBounds(w, s, e, n)
    }

    /** 경위도 점이 이 칸 안인지 (짝홀 규칙) */
    fun contains(lon: Float, lat: Float): Boolean {
        if (lon < bounds.west || lon > bounds.east || lat < bounds.south || lat > bounds.north) return false
        var inside = false
        for (r in rings) {
            var j = r.size - 2
            for (i in r.indices step 2) {
                val xi = r[i]; val yi = r[i + 1]; val xj = r[j]; val yj = r[j + 1]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }
}

class KoreaMap(
    val provinces: List<MapRegion>,
    val units: List<MapRegion>,
    val districts: List<MapRegion> = emptyList(),
) {
    val bounds: GeoBounds = provinces.map { it.bounds }.reduce(GeoBounds::union)
    private val unitById = units.associateBy { it.id }
    private val provinceById = provinces.associateBy { it.id }
    private val districtsByUnit = districts.groupBy { it.provinceId }

    fun unit(id: String?): MapRegion? = unitById[id]
    fun province(id: String?): MapRegion? = provinceById[id]
    fun districtsOf(unitId: String): List<MapRegion> = districtsByUnit[unitId].orEmpty()
    fun unitAt(lon: Float, lat: Float): MapRegion? = units.firstOrNull { it.contains(lon, lat) }

    companion object {
        /** korea_map.txt 읽기. 형식은 build_korea.py 머리말 참고 */
        fun parse(lines: Sequence<String>): KoreaMap {
            val provinces = mutableListOf<MapRegion>()
            val units = mutableListOf<MapRegion>()
            val districts = mutableListOf<MapRegion>()
            var head: List<String>? = null
            val rings = mutableListOf<FloatArray>()
            fun flush() {
                val h = head ?: return
                if (h[0] == "P") {
                    provinces += MapRegion(h[1], h[2], null, h[3].toInt() / SCALE, h[4].toInt() / SCALE, rings.toList())
                } else {
                    val region = MapRegion(h[1], h[2], h[3], h[4].toInt() / SCALE, h[5].toInt() / SCALE, rings.toList())
                    if (h[0] == "D") districts += region else units += region
                }
                rings.clear()
            }
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                when (line[0]) {
                    'P', 'U', 'D' -> { flush(); head = line.split('|') }
                    'R' -> rings += decodeRing(line)
                }
            }
            flush()
            require(provinces.isNotEmpty() && units.isNotEmpty()) { "빈 지도 데이터" }
            return KoreaMap(provinces, units, districts)
        }

        private const val SCALE = 10_000f

        private fun decodeRing(line: String): FloatArray {
            val v = line.substring(2).split(',')
            val out = FloatArray(v.size)
            var x = 0; var y = 0
            for (i in v.indices step 2) {
                if (i == 0) { x = v[0].toInt(); y = v[1].toInt() } else { x += v[i].toInt(); y += v[i + 1].toInt() }
                out[i] = x / SCALE; out[i + 1] = y / SCALE
            }
            return out
        }
    }
}

/**
 * 지도 투영: 평면 x = 경도 × cos(36°), y = −위도 (화면 아래로 갈수록 남쪽).
 * 남한 한가운데 위도에서 가로·세로 축척이 맞는다.
 */
object MapProjection {
    val K: Float = cos(Math.toRadians(36.0)).toFloat()
    fun x(lon: Float) = lon * K
    fun y(lat: Float) = -lat
    fun lon(x: Float) = x / K
    fun lat(y: Float) = -y
}

/** 지도 시점: 화면 가운데가 가리키는 평면 좌표 + 배율(평면 1단위 = [scale] px) */
data class MapCamera(val cx: Float, val cy: Float, val scale: Float) {
    fun toScreenX(x: Float, w: Float) = (x - cx) * scale + w / 2
    fun toScreenY(y: Float, h: Float) = (y - cy) * scale + h / 2
    fun toPlaneX(sx: Float, w: Float) = (sx - w / 2) / scale + cx
    fun toPlaneY(sy: Float, h: Float) = (sy - h / 2) / scale + cy
}

/**
 * 탭 확대 규칙 (SCR-05): 처음엔 남한 전체, 탭할 때마다 탭한 곳을 가운데로 [STEP]배씩 두 번까지 확대.
 * 2단계(×9)면 시·군이 구별된다. 그다음 탭은 그 자리의 시를 고른다.
 */
object MapZoom {
    const val STEP = 3f
    const val MAX_LEVEL = 2

    /** 0단계: [b]가 화면([w]×[h], 여백 [pad] px)에 꼭 들어가게 */
    fun fit(b: GeoBounds, w: Float, h: Float, pad: Float): MapCamera {
        val x0 = MapProjection.x(b.west); val x1 = MapProjection.x(b.east)
        val y0 = MapProjection.y(b.north); val y1 = MapProjection.y(b.south)
        val s = min((w - 2 * pad) / (x1 - x0), (h - 2 * pad) / (y1 - y0))
        return MapCamera((x0 + x1) / 2, (y0 + y1) / 2, s)
    }

    /**
     * [level]단계 시점. [focus] = 단계마다 탭한 평면 좌표(가장 마지막 것이 가운데).
     * 화면이 지도 밖으로 나가지 않게 가운데를 당긴다.
     */
    fun camera(b: GeoBounds, w: Float, h: Float, pad: Float, focus: List<Pair<Float, Float>>): MapCamera {
        val base = fit(b, w, h, pad)
        if (focus.isEmpty()) return base
        val s = base.scale * STEP.pow(focus.size)
        val (fx, fy) = focus.last()
        val x0 = MapProjection.x(b.west); val x1 = MapProjection.x(b.east)
        val y0 = MapProjection.y(b.north); val y1 = MapProjection.y(b.south)
        return MapCamera(clampCenter(fx, x0, x1, w / 2 / s), clampCenter(fy, y0, y1, h / 2 / s), s)
    }

    private fun clampCenter(c: Float, lo: Float, hi: Float, half: Float): Float =
        if (hi - lo <= 2 * half) (lo + hi) / 2 else c.coerceIn(lo + half, hi - half)
}
