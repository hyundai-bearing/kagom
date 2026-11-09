package com.example.roro.core

import kotlin.math.floor

/**
 * 차량 상태 엔진 (spec 4장, 가이드 5.3).
 * 센서 값(PART 1)과 앱이 계산한 정비 시기(PART 2)를 받아 배지·표정·상태 행을 만든다.
 * 순수 함수라 화면 없이 단위 테스트한다.
 */

/** 정비 기록 (앱이 저장) */
data class MaintenanceState(
    /** 가혹 조건 (MNT-01 주기 절반) */
    val severe: Boolean = false,
    /** 순정 추천 오일 사용. false면 주기 절반 */
    val recommendedOil: Boolean = true,
    /** 마지막 오일 교체 시점의 주행거리. null = 아직 기준 없음(첫 주행거리로 잡힘) */
    val oilChangedKm: Float? = null,
    val oilChangedDay: Long? = null,
    /** 완료한 정기 점검 회차 수 */
    val checkupRoundsDone: Int = 0,
    val checkupDoneDay: Long? = null,
    /** [보충했어]로 엔진오일 경고 배지를 숨김 (센서가 NORMAL로 돌아오면 풀림) */
    val engineOilDismissed: Boolean = false,
)

/** MNT-01 계산 결과 */
data class OilChangeInfo(
    val severity: Severity?,
    /** 남은 비율 0..1 (진행 바) */
    val remainingRatio: Float,
    /** 빠른 정비 카드 값: "2,160 km" / "40일" / "교체 시기 지남" */
    val display: String,
    val intervalKm: Int,
    val intervalDays: Int,
)

/** MNT-02 계산 결과 */
data class CheckupInfo(
    val due: Boolean,
    /** 이번 회차 (1 = 1만 km) */
    val round: Int,
    val dueKm: Int,
    /** "10,000 km 도래" / "3,690 km 남음" */
    val display: String,
)

/** 엔진 출력 = 홈 화면이 그리는 전부 */
data class VehicleStatus(
    val badges: List<StatusBadge>,
    val expression: Expression,
    val rows: List<StatusRow>,
    /** 배고픔 단계 0..3 (히스테리시스 다음 계산에 넘긴다) */
    val energyLevel: Int,
    val oilChange: OilChangeInfo?,
    val checkup: CheckupInfo,
    /** SEN-03이 켜져 있어 [보충했어]를 보여 줄지 */
    val engineOilAlert: Boolean,
    /** SEN-04/06: 정비소 안내 블록 */
    val shopGuide: StatusBadge?,
)

object StateEngine {

    /** 배고픔 단계 경계 (≤): 1 관찰, 2 권장, 3 긴급 */
    private val ENTER = intArrayOf(0, 30, 15, 5)
    /** 단계 해제 경계 (≥): 경계 +3%p (33/18/8) */
    private val RECOVER = intArrayOf(0, 33, 18, 8)

    /**
     * SEN-01/02 배고픔 단계 (히스테리시스).
     * 나빠질 때는 바로, 좋아질 때는 각 경계 +3%p 이상 회복해야 한 단계씩 내려간다.
     */
    fun energyLevel(percent: Int?, previous: Int): Int {
        if (percent == null) return 0
        val raw = when {
            percent <= ENTER[3] -> 3
            percent <= ENTER[2] -> 2
            percent <= ENTER[1] -> 1
            else -> 0
        }
        if (raw >= previous) return raw
        var level = previous
        while (level > raw && percent >= RECOVER[level]) level--
        return level
    }

    /** MNT-01 엔진오일 교체 주기 */
    fun oilChange(engine: EngineType, m: MaintenanceState, odoKm: Float?, today: Long): OilChangeInfo? {
        if (engine == EngineType.EV) return null
        val shortened = m.severe || !m.recommendedOil
        val intervalKm = when (engine) {
            EngineType.HYBRID -> if (shortened) 5_000 else 10_000
            else -> if (shortened) 7_500 else 15_000
        }
        val intervalDays = if (shortened) 182 else 365
        val usedKm = if (odoKm != null && m.oilChangedKm != null) (odoKm - m.oilChangedKm).coerceAtLeast(0f) else 0f
        val usedDays = m.oilChangedDay?.let { (today - it).coerceAtLeast(0) } ?: 0L
        val remainingKm = intervalKm - usedKm
        val remainingDays = intervalDays - usedDays
        val kmRatio = remainingKm / intervalKm
        val dayRatio = remainingDays.toFloat() / intervalDays
        val ratio = minOf(kmRatio, dayRatio)
        val severity = when {
            ratio <= 0f -> Severity.RECOMMEND
            ratio <= 0.10f -> Severity.OBSERVE
            else -> null
        }
        val display = when {
            ratio <= 0f -> "교체 시기 지남"
            kmRatio <= dayRatio -> "${fmt(remainingKm.toInt())} km"
            else -> "${remainingDays}일"
        }
        return OilChangeInfo(severity, ratio.coerceIn(0f, 1f), display, intervalKm, intervalDays)
    }

    /** MNT-02 정기 점검 (매 10,000 km 또는 12개월) */
    fun checkup(m: MaintenanceState, odoKm: Float?, today: Long, createdDay: Long): CheckupInfo {
        val odo = odoKm ?: 0f
        val byKm = floor(odo / Checkup.INTERVAL_KM).toInt()
        val round = maxOf(m.checkupRoundsDone + 1, byKm)
        val dueKm = round * Checkup.INTERVAL_KM
        val lastDay = m.checkupDoneDay ?: createdDay
        val dueByDate = today - lastDay >= Checkup.INTERVAL_DAYS
        val due = odoKm != null && odo >= dueKm || dueByDate
        val display = if (due) "${fmt(dueKm)} km 도래" else "${fmt((dueKm - odo).toInt())} km 남음"
        return CheckupInfo(due, round, dueKm, display)
    }

    fun evaluate(
        s: VehicleSnapshot,
        m: MaintenanceState,
        previousEnergyLevel: Int,
        today: Long,
        createdDay: Long,
    ): VehicleStatus {
        val engine = s.engineType
        val badges = mutableListOf<StatusBadge>()
        val expressions = mutableListOf(Expression.IDLE)

        // SEN-01 / SEN-02 배터리·연료
        val isEv = engine == EngineType.EV
        val energySignal = if (isEv) Signal.SOC else Signal.FUEL
        val energyPercent = if (isEv) s.socPercent else s.fuelPercent
        val energyName = if (isEv) "배터리" else "연료"
        val energyAvailable = energySignal !in s.unsupported && energyPercent != null
        val level = if (energyAvailable) energyLevel(energyPercent, previousEnergyLevel) else 0
        val energySeverity = levelSeverity(level)
        if (energySeverity != null) {
            badges += StatusBadge(if (isEv) StatusId.SEN_01 else StatusId.SEN_02, energySeverity, "$energyName ${energyPercent}%")
            expressions += when (level) { 1 -> Expression.HUNGRY_1; 2 -> Expression.HUNGRY_2; else -> Expression.HUNGRY_3 }
        }

        // SEN-03 엔진오일 (내연기관·HEV)
        val engineOilAvailable = !isEv && Signal.ENGINE_OIL !in s.unsupported && s.engineOil != null && s.engineOil != OilLevel.ERROR
        val engineOilSeverity = if (engineOilAvailable) when (s.engineOil) {
            OilLevel.LOW, OilLevel.HIGH -> Severity.RECOMMEND
            OilLevel.CRITICALLY_LOW -> Severity.URGENT
            else -> null
        } else null
        val engineOilBadgeShown = engineOilSeverity != null && !m.engineOilDismissed
        if (engineOilSeverity != null) {
            if (engineOilBadgeShown) badges += StatusBadge(StatusId.SEN_03, engineOilSeverity, when (s.engineOil) {
                OilLevel.HIGH -> "엔진오일 과다"
                OilLevel.CRITICALLY_LOW -> "엔진오일 거의 없음"
                else -> "엔진오일 부족"
            })
            expressions += if (engineOilSeverity == Severity.URGENT) Expression.SICK else Expression.STAIN
        }

        // SEN-04 브레이크액 (LOW·거의 없음 모두 즉시 긴급)
        val brakeAvailable = Signal.BRAKE_FLUID !in s.unsupported && s.brakeFluid != null && s.brakeFluid != OilLevel.ERROR
        val brakeLow = brakeAvailable && (s.brakeFluid == OilLevel.LOW || s.brakeFluid == OilLevel.CRITICALLY_LOW)
        var shopGuide: StatusBadge? = null
        if (brakeLow) {
            val b = StatusBadge(StatusId.SEN_04, Severity.URGENT, "브레이크액 부족")
            badges += b; shopGuide = b
            expressions += Expression.SICK
        }

        // SEN-05 미션오일
        if (Signal.TRANSMISSION_OIL !in s.unsupported) when (s.transmissionOil) {
            OilLevel.LOW -> { badges += StatusBadge(StatusId.SEN_05, Severity.RECOMMEND, "미션오일 부족"); expressions += Expression.STAIN }
            OilLevel.CRITICALLY_LOW -> { badges += StatusBadge(StatusId.SEN_05, Severity.URGENT, "미션오일 거의 없음"); expressions += Expression.SICK }
            else -> Unit
        }

        // SEN-06 엔진 이상 (출력 저하 경고등)
        if (!isEv && Signal.ENGINE_WARNING !in s.unsupported && s.engineWarning == true) {
            val b = StatusBadge(StatusId.SEN_06, Severity.URGENT, "엔진 경고등")
            badges += b; if (shopGuide == null) shopGuide = b
            expressions += Expression.SICK
        }

        // SEN-07 워셔액 (표정 변화 없음, 배지만)
        val washerAvailable = Signal.WASHER !in s.unsupported && s.washerLow != null
        if (washerAvailable && s.washerLow == true) badges += StatusBadge(StatusId.SEN_07, Severity.RECOMMEND, "워셔액 부족")

        // MNT-01 오일 교체 시기
        val oil = oilChange(engine, m, s.odometerKm, today)
        if (oil?.severity != null) {
            badges += StatusBadge(StatusId.MNT_01, oil.severity, "엔진오일 교체")
            expressions += Expression.STAIN
        }

        // MNT-02 정기 점검
        val checkup = checkup(m, s.odometerKm, today, createdDay)
        if (checkup.due) {
            badges += StatusBadge(StatusId.MNT_02, Severity.RECOMMEND, "정기 점검")
            expressions += Expression.TIRED
        }

        val rows = listOf(
            if (energyAvailable) StatusRow(energyName, "${energyPercent}%", energySeverity)
            else StatusRow(energyName, "확인 불가", unavailable = true),
            if (Signal.ODOMETER !in s.unsupported && s.odometerKm != null)
                StatusRow("주행거리", "${fmt(s.odometerKm.toInt())} km", if (checkup.due) Severity.RECOMMEND else null, if (checkup.due) "점검 도래" else null)
            else StatusRow("주행거리", "확인 불가", unavailable = true),
            when {
                isEv -> StatusRow("엔진오일", "해당 없음", unavailable = true)
                !engineOilAvailable -> StatusRow("엔진오일", "확인 불가", unavailable = true)
                else -> StatusRow("엔진오일", oilText(s.engineOil), engineOilSeverity.takeIf { engineOilBadgeShown })
            },
            if (brakeAvailable) StatusRow("브레이크액", if (brakeLow) fluidText(s.brakeFluid) else "정상", if (brakeLow) Severity.URGENT else null)
            else StatusRow("브레이크액", "확인 불가", unavailable = true),
            if (washerAvailable) StatusRow("워셔액", if (s.washerLow == true) "부족" else "정상", if (s.washerLow == true) Severity.RECOMMEND else null)
            else StatusRow("워셔액", "확인 불가", unavailable = true),
        )

        return VehicleStatus(
            badges = badges.sortedWith(compareByDescending<StatusBadge> { it.severity.rank }.thenBy { it.id.ordinal }),
            expression = pickExpression(expressions),
            rows = rows,
            energyLevel = level,
            oilChange = oil,
            checkup = checkup,
            engineOilAlert = engineOilSeverity != null,
            shopGuide = shopGuide,
        )
    }

    /** 여러 상태가 겹치면 가장 위급한 표정 1개 (spec 4장 3) */
    fun pickExpression(candidates: Collection<Expression>): Expression =
        candidates.maxWithOrNull(compareBy<Expression> { it.priority }.thenBy { it.ordinal }) ?: Expression.IDLE

    private fun levelSeverity(level: Int) = when (level) {
        1 -> Severity.OBSERVE; 2 -> Severity.RECOMMEND; 3 -> Severity.URGENT; else -> null
    }

    private fun oilText(l: OilLevel?) = when (l) {
        OilLevel.LOW -> "LOW"
        OilLevel.HIGH -> "HIGH"
        OilLevel.CRITICALLY_LOW -> "거의 없음"
        else -> "정상"
    }

    private fun fluidText(l: OilLevel?) = if (l == OilLevel.CRITICALLY_LOW) "거의 없음" else "부족"

    fun fmt(n: Int): String = "%,d".format(n)
}
