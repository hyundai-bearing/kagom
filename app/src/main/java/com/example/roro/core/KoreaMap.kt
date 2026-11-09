package com.example.roro.core

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/*
 * 남한 지도 (SCR-05, 가이드 v1.2 3단계). 데이터: tools/regions가 OSM에서 만든다.
 *   L1 시·도 (province)      assets/regions/korea_map.txt  P 줄
 *   L2 시·군·구 (unit)       assets/regions/korea_map.txt  U 줄 (도 아래 시·군, 특별·광역시 아래 자치구·군)
 *   L3 읍·면·동 (area, 구획)  assets/regions/l3/<시·도 파일>.txt  A 줄 (시·도마다 따로, 들어갈 때만 읽는다)
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
    /** 위 단계 칸 id (unit → 시·도, area → 시·군·구, province → null) */
    val parentId: String?,
    val labelLon: Float,
    val labelLat: Float,
    val rings: List<FloatArray>,
    /** unit: 안에 든 읍·면·동 수 (구획 수). 데이터에 없으면 0 */
    val areaCount: Int = 0,
    /** province: L3 파일 이름 (assets/regions/l3/<file>.txt) */
    val file: String? = null,
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

class KoreaMap(val provinces: List<MapRegion>, val units: List<MapRegion>) {
    val bounds: GeoBounds = provinces.map { it.bounds }.reduce(GeoBounds::union)
    private val unitById = units.associateBy { it.id }
    private val provinceById = provinces.associateBy { it.id }
    private val unitsByProvince = units.groupBy { it.parentId }

    fun unit(id: String?): MapRegion? = unitById[id]
    fun province(id: String?): MapRegion? = provinceById[id]
    fun unitsOf(provinceId: String?): List<MapRegion> = unitsByProvince[provinceId].orEmpty()
    fun unitAt(lon: Float, lat: Float): MapRegion? = units.firstOrNull { it.contains(lon, lat) }
    fun provinceAt(lon: Float, lat: Float): MapRegion? = provinces.firstOrNull { it.contains(lon, lat) }

    /** 여러 칸을 다 담는 사각형 */
    fun boundsOf(regions: List<MapRegion>): GeoBounds = regions.map { it.bounds }.reduceOrNull(GeoBounds::union) ?: bounds

    companion object {
        /** korea_map.txt 읽기 (P·U 줄). 형식은 build_korea.py·build_dong.py 머리말 참고 */
        fun parse(lines: Sequence<String>): KoreaMap {
            val provinces = mutableListOf<MapRegion>(); val units = mutableListOf<MapRegion>()
            readBlocks(lines) { h, rings ->
                if (h[0] == "P") provinces += MapRegion(h[1], h[2], null, h[3].toInt() / SCALE, h[4].toInt() / SCALE, rings,
                    file = h.getOrNull(5)?.takeIf { it.isNotBlank() })
                else if (h[0] == "U") units += MapRegion(h[1], h[2], h[3], h[4].toInt() / SCALE, h[5].toInt() / SCALE, rings,
                    areaCount = h.getOrNull(6)?.toIntOrNull() ?: 0)
            }
            require(provinces.isNotEmpty() && units.isNotEmpty()) { "빈 지도 데이터" }
            return KoreaMap(provinces, units)
        }

        /** L3 파일 읽기 (A 줄: 구획 id|이름|시·군·구 id|라벨 경도|라벨 위도) */
        fun parseAreas(lines: Sequence<String>): List<MapRegion> {
            val areas = mutableListOf<MapRegion>()
            readBlocks(lines) { h, rings ->
                if (h[0] == "A") areas += MapRegion(h[1], h[2], h[3], h[4].toInt() / SCALE, h[5].toInt() / SCALE, rings)
            }
            return areas
        }

        private fun readBlocks(lines: Sequence<String>, emit: (List<String>, List<FloatArray>) -> Unit) {
            var head: List<String>? = null
            val rings = mutableListOf<FloatArray>()
            fun flush() { head?.let { emit(it, rings.toList()) }; rings.clear() }
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                when (line[0]) {
                    'P', 'U', 'A' -> { flush(); head = line.split('|') }
                    'R' -> rings += decodeRing(line)
                }
            }
            flush()
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

    companion object {
        /** [b]가 화면([w]×[h], 여백 [pad] px)에 꼭 들어가게 */
        fun fit(b: GeoBounds, w: Float, h: Float, pad: Float): MapCamera {
            val x0 = MapProjection.x(b.west); val x1 = MapProjection.x(b.east)
            val y0 = MapProjection.y(b.north); val y1 = MapProjection.y(b.south)
            val s = min((w - 2 * pad) / (x1 - x0), (h - 2 * pad) / (y1 - y0))
            return MapCamera((x0 + x1) / 2, (y0 + y1) / 2, s)
        }
    }
}
