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
        P|도|테스트도|1265000|365000
        R|1260000,360000,10000,0,0,10000,-10000,0
        U|도/A시|A시|도|1262000|362000
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
    fun `탭할 때마다 3배씩, 화면이 지도 밖으로 나가지 않게 가운데를 당긴다`() {
        val b = GeoBounds(126f, 36f, 127f, 37f)
        val base = MapZoom.fit(b, 1000f, 500f, 0f)
        assertEquals(500f, base.scale, 1e-3f)              // 세로 1도 = 500px
        val l1 = MapZoom.camera(b, 1000f, 500f, 0f, listOf(MapProjection.x(126.5f) to MapProjection.y(36.5f)))
        assertEquals(base.scale * 3, l1.scale, 1e-2f)
        assertEquals(MapProjection.y(36.5f), l1.cy, 1e-4f)
        // 모서리를 탭해도 가운데는 지도 안쪽으로 당겨진다
        val corner = MapZoom.camera(b, 1000f, 500f, 0f, listOf(MapProjection.x(126f) to MapProjection.y(37f), MapProjection.x(126f) to MapProjection.y(37f)))
        assertEquals(base.scale * 9, corner.scale, 1e-1f)
        assertEquals(MapProjection.y(37f), corner.toPlaneY(0f, 500f), 1e-4f)
        assertEquals(MapProjection.x(126f), corner.toPlaneX(0f, 1000f), 1e-4f)
    }

    @Test
    fun `위아래가 가려지면 남은 자리에 꼭 맞추고 그 가운데에 둔다`() {
        val b = GeoBounds(126f, 36f, 127f, 37f)
        // 높이 900에서 위 100(머리줄), 아래 200(접힌 시트)이 가려진다 → 보이는 자리는 100..700
        val camera = MapZoom.fit(b, 2000f, 900f, 0f, top = 100f, bottom = 200f)
        assertEquals(100f, camera.toScreenY(MapProjection.y(37f), 900f), 1e-2f)
        assertEquals(700f, camera.toScreenY(MapProjection.y(36f), 900f), 1e-2f)
        assertEquals(1000f, camera.toScreenX(MapProjection.x(126.5f), 2000f), 1e-2f)
        // 가림이 없으면 전과 같다
        assertEquals(MapZoom.fit(b, 2000f, 900f, 0f), MapZoom.fit(b, 2000f, 900f, 0f, top = 0f, bottom = 0f))
    }

    @Test
    fun `화면 좌표와 평면 좌표는 서로 되돌아온다`() {
        val cam = MapCamera(100f, -37f, 1234f)
        val x = cam.toPlaneX(321f, 800f); val y = cam.toPlaneY(123f, 600f)
        assertEquals(321f, cam.toScreenX(x, 800f), 1e-2f)
        assertEquals(123f, cam.toScreenY(y, 600f), 1e-2f)
    }

    /** 실제 데이터(assets/regions/korea_map.txt): 시 개수와 대표 지점, 랜드마크가 자기 시 안에 있는지 */
    @Test
    fun `실제 지도 데이터`() {
        val file = File("src/main/assets/regions/korea_map.txt")
        assertTrue("tools/regions/build_korea.py로 먼저 만들 것", file.exists())
        val map = file.useLines { KoreaMap.parse(it) }
        assertEquals(16, map.provinces.size)
        assertEquals(161, map.units.size)
        assertEquals(map.units.size, map.units.map { it.id }.toSet().size)
        fun at(lon: Double, lat: Double) = map.unitAt(lon.toFloat(), lat.toFloat())?.id
        assertEquals("서울/서울", at(126.978, 37.566))        // 서울시청
        assertEquals("경기/수원시", at(127.029, 37.263))
        assertEquals("강원/강릉시", at(128.876, 37.752))
        assertEquals("전남광주/광주", at(126.851, 35.160))
        assertEquals("제주/서귀포시", at(126.560, 33.253))
        assertEquals("경북/울릉군", at(130.905, 37.484))       // 울릉도
        assertNull(at(130.0, 36.5))                              // 동해 한가운데
        for (lm in Landmarks.all) {
            assertNotNull("${lm.unitId} 없음", map.unit(lm.unitId))
            assertEquals(lm.name, lm.unitId, at(lm.lon, lm.lat))
        }
    }

    @Test
    fun `강릉 랜드마크는 각각 하나의 실제 구획에 들어간다`() {
        val map = mapWithGangneungDistricts()
        val gangneung = map.districtsOf(Landmarks.GANGNEUNG)
        assertEquals(9, gangneung.size)
        val expected = mapOf(
            "LM-GN-01" to "강동면", "LM-GN-02" to "시내 13개 동", "LM-GN-03" to "주문진읍",
            "LM-GN-04" to "왕산면", "LM-GN-05" to "성산면",
        )
        for ((id, name) in expected) {
            val landmark = Landmarks.byId(id)!!
            val matches = gangneung.filter { it.contains(landmark.lon.toFloat(), landmark.lat.toFloat()) }
            assertEquals(listOf(name), matches.map { it.name })
        }
    }

    @Test
    fun `강릉에서 한 곳을 방문하면 해당 구획만 해금된다`() {
        val map = mapWithGangneungDistricts()
        assertTrue(map.landmarkVisitAreas(Landmarks.all, emptySet()).isEmpty())
        val area = map.landmarkVisitAreas(Landmarks.all, setOf("LM-GN-03")).single()
        assertEquals("주문진읍", area.region.name)
        assertEquals(Landmarks.GANGNEUNG, area.region.provinceId)
        assertTrue(area.fullyVisited)
    }

    @Test
    fun `강릉 랜드마크를 모두 방문하면 그 다섯 구획만 해금된다`() {
        val map = mapWithGangneungDistricts()
        val ids = Landmarks.inUnit(Landmarks.GANGNEUNG).map { it.id }.toSet()
        val areas = map.landmarkVisitAreas(Landmarks.all, ids)
        assertEquals(setOf("주문진읍", "시내 13개 동", "강동면", "왕산면", "성산면"), areas.map { it.region.name }.toSet())
        assertTrue(areas.all { it.fullyVisited })
    }

    @Test
    fun `구획 경계가 없는 경우 시군 경계에 일부 해금을 표시한다`() {
        val map = File("src/main/assets/regions/korea_map.txt").useLines { KoreaMap.parse(it) }
        val area = map.landmarkVisitAreas(Landmarks.all, setOf("LM-GN-03")).single()
        assertEquals(Landmarks.GANGNEUNG, area.region.id)
        assertFalse(area.fullyVisited)
    }

    private fun mapWithGangneungDistricts(): KoreaMap =
        File("src/main/assets/regions/korea_map.txt").useLines { base ->
            File("src/main/assets/regions/gangneung_districts.txt").useLines { districts ->
                KoreaMap.parse(base + districts)
            }
        }
}
