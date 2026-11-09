package com.example.roro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KoreaMapTest {

    /** 1°×1° 시·도 하나(126~127E, 36~37N), 그 안에 가운데 구멍이 난 시(A)와 구멍 자리를 채운 시(B). 좌표 = 도 × 10000 */
    private val sample = """
        # 주석
        P|도|테스트도|1265000|365000|testdo
        R|1260000,360000,10000,0,0,10000,-10000,0
        U|도/A시|A시|도|1262000|362000|2
        R|1260000,360000,10000,0,0,10000,-10000,0
        R|1264000,364000,2000,0,0,2000,-2000,0
        U|도/B군|B군|도|1265000|365000
        R|1264000,364000,2000,0,0,2000,-2000,0
    """.trimIndent()

    @Test
    fun `차분 좌표를 풀고 구멍은 짝홀 규칙으로 뺀다`() {
        val map = KoreaMap.parse(sample.lineSequence())
        assertEquals(1, map.provinces.size)
        assertEquals(2, map.units.size)
        val a = map.unit("도/A시")!!
        assertEquals(126f, a.bounds.west, 1e-4f); assertEquals(37f, a.bounds.north, 1e-4f)
        assertEquals(126.2f, a.labelLon, 1e-4f)
        assertTrue(a.contains(126.1f, 36.1f))
        assertFalse(a.contains(126.5f, 36.5f))           // 구멍 = B군
        assertEquals("도/B군", map.unitAt(126.5f, 36.5f)?.id)
        assertEquals("도/A시", map.unitAt(126.9f, 36.9f)?.id)
        assertNull(map.unitAt(128f, 36.5f))               // 바다
    }

    @Test
    fun `L3 파일 이름과 읍면동 수를 읽고 없으면 기본값`() {
        val map = KoreaMap.parse(sample.lineSequence())
        assertEquals("testdo", map.province("도")?.file)
        assertEquals(2, map.unit("도/A시")?.areaCount)
        assertEquals(0, map.unit("도/B군")?.areaCount)       // 예전 형식(필드 없음)
        assertEquals(listOf("도/A시", "도/B군"), map.unitsOf("도").map { it.id })
        val areas = KoreaMap.parseAreas("""
            A|도/A시/가동|가동|도/A시|1261000|361000
            R|1260000,360000,4000,0,0,4000,-4000,0
            A|도/A시/나면|나면|도/A시|1268000|368000
            R|1266000,366000,4000,0,0,4000,-4000,0
        """.trimIndent().lineSequence())
        assertEquals(listOf("가동", "나면"), areas.map { it.name })
        assertEquals("도/A시", areas[0].parentId)
        assertTrue(areas[1].contains(126.8f, 36.8f))
    }

    @Test
    fun `한 칸이 화면에 꼭 들어가게 맞춘다`() {
        val b = GeoBounds(126f, 36f, 127f, 37f)
        val cam = MapCamera.fit(b, 1000f, 500f, 0f)
        assertEquals(500f, cam.scale, 1e-3f)                // 세로 1도 = 500px
        assertEquals(MapProjection.y(36.5f), cam.cy, 1e-4f)
        assertEquals(MapProjection.y(37f), cam.toPlaneY(0f, 500f), 1e-4f)
    }

    @Test
    fun `화면 좌표와 평면 좌표는 서로 되돌아온다`() {
        val cam = MapCamera(100f, -37f, 1234f)
        val x = cam.toPlaneX(321f, 800f); val y = cam.toPlaneY(123f, 600f)
        assertEquals(321f, cam.toScreenX(x, 800f), 1e-2f)
        assertEquals(123f, cam.toScreenY(y, 600f), 1e-2f)
    }

    /** 실제 데이터(assets/regions): 칸 수, 대표 지점, 랜드마크가 자기 시·군·구·읍면동 안에 있는지 */
    @Test
    fun `실제 지도 데이터`() {
        val file = File("src/main/assets/regions/korea_map.txt")
        assertTrue("tools/regions/build_korea.py, build_dong.py로 먼저 만들 것", file.exists())
        val map = file.useLines { KoreaMap.parse(it) }
        assertEquals(16, map.provinces.size)
        assertEquals(230, map.units.size)                         // 도 아래 시·군 + 특별·광역시 아래 자치구·군
        assertEquals(map.units.size, map.units.map { it.id }.toSet().size)
        assertTrue(map.provinces.all { it.file != null })
        assertTrue(map.units.all { it.areaCount >= 1 })
        assertEquals(18, map.unitsOf("강원").size)                 // Figma 149:4378: 강원 18개 시·군
        fun at(lon: Double, lat: Double) = map.unitAt(lon.toFloat(), lat.toFloat())?.id
        assertEquals("서울/중구", at(126.978, 37.566))              // 서울시청
        assertEquals("경기/수원시", at(127.029, 37.263))
        assertEquals("강원/강릉시", at(128.876, 37.752))
        assertEquals("전남광주/광주 서구", at(126.851, 35.160))
        assertEquals("제주/서귀포시", at(126.560, 33.253))
        assertEquals("경북/울릉군", at(130.905, 37.484))             // 울릉도
        assertNull(at(130.0, 36.5))                                 // 동해 한가운데

        val gangwon = map.province("강원")!!
        val areas = File("src/main/assets/regions/l3/${gangwon.file}.txt").useLines { KoreaMap.parseAreas(it) }
        val gangneung = areas.filter { it.parentId == Landmarks.GANGNEUNG }
        assertEquals(map.unit(Landmarks.GANGNEUNG)!!.areaCount, gangneung.size)
        // 읍면동 수 = L3 파일의 구획 수 (강원 전체)
        for (u in map.unitsOf("강원")) assertEquals(u.id, u.areaCount, areas.count { it.parentId == u.id })
        // 실제 랜드마크와 지도 예시가 자기 구획 안에 있다
        val spots = Landmarks.all.map { MapSpot(it.id, it.name, it.unitId, it.areaName, it.lat, it.lon, it.rewardItemId, false) } + Examples.mapSpots
        for (s in spots) {
            assertEquals(s.name, s.unitId, at(s.lon, s.lat))
            val area = gangneung.firstOrNull { it.id == s.areaId }
            assertNotNull("${s.areaId} 없음", area)
            assertTrue(s.name, area!!.contains(s.lon.toFloat(), s.lat.toFloat()))
        }
    }
}
