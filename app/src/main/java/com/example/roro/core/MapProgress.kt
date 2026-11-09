package com.example.roro.core

/*
 * SCR-05 해금 집계 (가이드 v1.2 2.1).
 * 구획(L3 읍·면·동)마다 랜드마크 3곳. 상위 칸은 아래 칸을 합산해 "n / N"으로 보여 주고 같은 4종 규칙을 따른다:
 *   미해금(0) · 일부 해금(1 ~ N-1) · 모두 해금(N) · 선택 중(화면 상태, 다른 상태와 겹침)
 * 실제 랜드마크가 정해지지 않은 자리도 N에 센다 (지도에서는 '준비 중'으로 보인다).
 */

enum class AreaState { LOCKED, PARTIAL, COMPLETE }

data class Tally(val unlocked: Int, val total: Int) {
    val state: AreaState
        get() = when {
            unlocked <= 0 -> AreaState.LOCKED
            unlocked >= total -> AreaState.COMPLETE
            else -> AreaState.PARTIAL
        }

    operator fun plus(o: Tally) = Tally(unlocked + o.unlocked, total + o.total)

    companion object { val ZERO = Tally(0, 0) }
}

/**
 * 지도 위 랜드마크 한 곳. 실제 랜드마크(Landmarks)와 시연용 예시(Examples)를 같은 모양으로 다룬다.
 * [areaName] = 속한 읍·면·동 이름 → 구획 id = "<시·군·구 id>/<읍·면·동 이름>"
 */
data class MapSpot(
    val id: String,
    val name: String,
    val unitId: String,
    val areaName: String,
    val lat: Double,
    val lon: Double,
    val rewardItemId: String?,
    val visited: Boolean,
    /** 방문했고 해금 팝업을 아직 안 봤음 (핀이 깜빡인다) */
    val unlocking: Boolean = false,
    val example: Boolean = false,
) {
    val areaId: String get() = "$unitId/$areaName"
}

object MapTally {
    const val PER_AREA = 3

    fun area(areaId: String, spots: List<MapSpot>) =
        Tally(spots.count { it.areaId == areaId && it.visited }.coerceAtMost(PER_AREA), PER_AREA)

    /** 시·군·구: 구획 수 × 3 (구획 수를 모르면 시·군·구 하나를 구획 하나로 본다) */
    fun unit(unit: MapRegion, spots: List<MapSpot>) =
        Tally(spots.count { it.unitId == unit.id && it.visited }, PER_AREA * maxOf(1, unit.areaCount))

    fun province(province: MapRegion, map: KoreaMap, spots: List<MapSpot>) =
        map.unitsOf(province.id).fold(Tally.ZERO) { acc, u -> acc + unit(u, spots) }

    fun country(map: KoreaMap, spots: List<MapSpot>) =
        map.provinces.fold(Tally.ZERO) { acc, p -> acc + province(p, map, spots) }

    /** 목록 순서: 해금 많은 칸 먼저, 같으면 전체가 큰 칸 먼저 (Figma SCR-05 사이드 패널) */
    fun <T> ranked(items: List<T>, tally: (T) -> Tally): List<T> =
        items.sortedWith(compareByDescending<T> { tally(it).unlocked }.thenByDescending { tally(it).total })
}
