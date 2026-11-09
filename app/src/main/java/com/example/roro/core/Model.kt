package com.example.roro.core

/**
 * 카곰 도메인 모델. Android/Compose에 의존하지 않는 순수 값만 둔다 (단위 테스트 대상).
 * 상태 키 이름은 Figma 프레임 이름의 키를 그대로 쓴다 (gear, status, tab, skin, step …).
 */

/** 기어. P가 아니면 입력·정비·저장·모달을 막는다 (가이드 5.2) */
enum class Gear { P, R, N, D;
    val isPark get() = this == P
}

/** 배지 심각도. Figma statusBadge Severity=Observe|Recommend|Urgent|Ok */
enum class Severity(val rank: Int, val label: String) {
    OK(0, "정상"), OBSERVE(1, "관찰"), RECOMMEND(2, "권장"), URGENT(3, "긴급");
}

/**
 * 카곰 표정. label = Figma kagomCharacter Expression 라벨.
 * priority가 클수록 우선 (spec 4장 3) 아픔 > 배고픔3 > 얼룩 > 배고픔1~2 > 피곤함 > 대기)
 */
enum class Expression(val label: String, val priority: Int) {
    IDLE("대기", 0),
    TIRED("피곤함", 1),
    HUNGRY_1("배고픔 1", 2),
    HUNGRY_2("배고픔 2", 2),
    STAIN("얼룩", 3),
    HUNGRY_3("배고픔 3", 4),
    SICK("아픔", 5),
}

/** Pleos `OilLevelState`를 앱 안에서 쓰는 값으로 옮긴 것 (SDK 없이 테스트하려고 따로 둠) */
enum class OilLevel { NORMAL, LOW, HIGH, CRITICALLY_LOW, ERROR }

/** 차 신호 종류 (지원 여부 확인 단위) */
enum class Signal { FUEL, SOC, ENGINE_OIL, BRAKE_FLUID, TRANSMISSION_OIL, ENGINE_WARNING, WASHER, ODOMETER, GEAR }

/** 엔진 종류. 정비 주기(MNT-01)와 연료/배터리 표시가 달라진다 */
enum class EngineType { GASOLINE, HYBRID, EV }

/** 차 신호를 한 번에 모은 값. null = 아직 값 없음 또는 읽기 실패 */
data class VehicleSnapshot(
    val fuelPercent: Int? = null,
    val socPercent: Int? = null,
    val engineOil: OilLevel? = null,
    val brakeFluid: OilLevel? = null,
    val transmissionOil: OilLevel? = null,
    /** 엔진 이상 (Pleos `PowerDownWarningIndicator`: 출력 저하 경고등) */
    val engineWarning: Boolean? = null,
    val washerLow: Boolean? = null,
    val odometerKm: Float? = null,
    val gear: Gear? = null,
    /** 이 차가 지원하지 않는 신호 → 화면에서 "확인 불가" */
    val unsupported: Set<Signal> = emptySet(),
) {
    /** 지원 신호로 엔진 종류를 추정한다: 연료 없음+배터리 있음 = EV, 둘 다 = 하이브리드 */
    val engineType: EngineType
        get() {
            val fuel = Signal.FUEL !in unsupported && fuelPercent != null
            val soc = Signal.SOC !in unsupported && socPercent != null
            return when {
                soc && !fuel -> EngineType.EV
                soc && fuel -> EngineType.HYBRID
                else -> EngineType.GASOLINE
            }
        }
}

/** 상태 ID (spec 4장) */
enum class StatusId(val code: String) {
    SEN_01("SEN-01"), SEN_02("SEN-02"), SEN_03("SEN-03"), SEN_04("SEN-04"),
    SEN_05("SEN-05"), SEN_06("SEN-06"), SEN_07("SEN-07"), MNT_01("MNT-01"), MNT_02("MNT-02"),
}

/** 홈 statusBadgeGroup의 배지 하나 */
data class StatusBadge(val id: StatusId, val severity: Severity, val label: String)

/** 홈 vehicleStatusPanel의 상태 행 (vehicleStatusRow Label · Value · hasBadge) */
data class StatusRow(
    val label: String,
    val value: String,
    val badge: Severity? = null,
    /** 배지 글자. null이면 심각도 이름(관찰/권장/긴급) */
    val badgeLabel: String? = null,
    val unavailable: Boolean = false,
)

/** 아이템 카테고리. tab 키 = Figma tab=head|top|bottom|title */
enum class Category(val tab: String, val label: String, val noun: String) {
    HEAD("head", "머리", "머리 장식"),
    TOP("top", "상의", "상의"),
    BOTTOM("bottom", "하의", "하의"),
    TITLE("title", "칭호", "칭호"),
}

/** 아이템 출처 칩 */
enum class Source(val chip: String) { DEFAULT("기본 제공"), LANDMARK("랜드마크"), QUEST("도전과제"), MAINTENANCE("정비") }

/** 성향 테스트 응답 */
enum class Answer { A, B }
