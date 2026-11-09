package com.example.roro.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MapTallyTest {

    private val map = KoreaMap.parse("""
        P|도|테스트도|1265000|365000|testdo
        R|1260000,360000,10000,0,0,10000,-10000,0
        P|섬|섬도|1275000|365000|seom
        R|1270000,360000,10000,0,0,10000,-10000,0
        U|도/A시|A시|도|1262000|362000|21
        R|1260000,360000,5000,0,0,10000,-5000,0
        U|도/B군|B군|도|1267000|365000|3
        R|1265000,360000,5000,0,0,10000,-5000,0
        U|섬/C군|C군|섬|1275000|365000
        R|1270000,360000,10000,0,0,10000,-10000,0
    """.trimIndent().lineSequence())

    private fun spot(id: String, unit: String, area: String, visited: Boolean) =
        MapSpot(id, id, unit, area, 0.0, 0.0, null, visited)

    // 가이드 v1.2 예시: 주문진읍 1/3, 사천면 3/3
    private val spots = listOf(
        spot("주문진항", "도/A시", "주문진읍", true),
        spot("해수욕장", "도/A시", "주문진읍", false),
        spot("사천1", "도/A시", "사천면", true),
        spot("사천2", "도/A시", "사천면", true),
        spot("사천3", "도/A시", "사천면", true),
    )

    @Test
    fun `구획 색은 0이면 미해금, 1~2면 일부, 3이면 모두 해금`() {
        assertEquals(AreaState.LOCKED, Tally(0, 3).state)
        assertEquals(AreaState.PARTIAL, Tally(1, 3).state)
        assertEquals(AreaState.PARTIAL, Tally(2, 3).state)
        assertEquals(AreaState.COMPLETE, Tally(3, 3).state)
        assertEquals(Tally(1, 3), MapTally.area("도/A시/주문진읍", spots))
        assertEquals(AreaState.COMPLETE, MapTally.area("도/A시/사천면", spots).state)
        assertEquals(Tally(0, 3), MapTally.area("도/A시/연곡면", spots))       // 랜드마크가 정해지지 않은 구획도 3곳으로 센다
    }

    @Test
    fun `상위 칸은 하위 해금 수를 합산한다 (구획 수 × 3)`() {
        val a = map.unit("도/A시")!!
        assertEquals(Tally(4, 63), MapTally.unit(a, spots))                   // Figma: 강릉시 4/63
        assertEquals(Tally(0, 9), MapTally.unit(map.unit("도/B군")!!, spots))
        assertEquals(Tally(0, 3), MapTally.unit(map.unit("섬/C군")!!, spots))  // 읍면동 수를 모르면 한 구획
        assertEquals(Tally(4, 72), MapTally.province(map.province("도")!!, map, spots))
        assertEquals(AreaState.PARTIAL, MapTally.province(map.province("도")!!, map, spots).state)   // 강원 4/564 → 일부 해금
        assertEquals(Tally(4, 75), MapTally.country(map, spots))
    }

    @Test
    fun `목록은 해금 많은 순, 같으면 전체가 큰 순`() {
        val ranked = MapTally.ranked(map.units) { MapTally.unit(it, spots) }
        assertEquals(listOf("도/A시", "도/B군", "섬/C군"), ranked.map { it.id })
    }
}
