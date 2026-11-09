package com.example.roro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateEngineTest {

    private val today = 20_000L
    private val normal = VehicleSnapshot(
        fuelPercent = 72, engineOil = OilLevel.NORMAL, brakeFluid = OilLevel.NORMAL,
        transmissionOil = OilLevel.NORMAL, engineWarning = false, washerLow = false,
        odometerKm = 1_240f, gear = Gear.P, unsupported = setOf(Signal.SOC),
    )
    private val fresh = MaintenanceState(oilChangedKm = 1_240f, oilChangedDay = today)

    private fun eval(s: VehicleSnapshot = normal, m: MaintenanceState = fresh, prev: Int = 0) =
        StateEngine.evaluate(s, m, prev, today, createdDay = today)

    // ── SEN-01/02 단계 + 히스테리시스 ──

    @Test fun energyEntersEachLevelAtThreshold() {
        assertEquals(0, StateEngine.energyLevel(31, 0))
        assertEquals(1, StateEngine.energyLevel(30, 0))
        assertEquals(2, StateEngine.energyLevel(15, 0))
        assertEquals(3, StateEngine.energyLevel(5, 0))
    }

    @Test fun energyRecoversOnlyAfterPlusThreePoints() {
        assertEquals(3, StateEngine.energyLevel(7, 3))   // 8% 미만이면 긴급 유지
        assertEquals(2, StateEngine.energyLevel(8, 3))   // 8% 도달 → 권장
        assertEquals(2, StateEngine.energyLevel(17, 2))  // 18% 미만 유지
        assertEquals(1, StateEngine.energyLevel(18, 2))
        assertEquals(1, StateEngine.energyLevel(32, 1))
        assertEquals(0, StateEngine.energyLevel(33, 1))
        assertEquals(0, StateEngine.energyLevel(40, 3))  // 한 번에 크게 회복하면 해제
    }

    @Test fun energyWorsensImmediately() {
        assertEquals(3, StateEngine.energyLevel(4, 1))
    }

    @Test fun fuelBadgeAndHungryExpression() {
        val r = eval(normal.copy(fuelPercent = 12))
        assertEquals(Expression.HUNGRY_2, r.expression)
        assertEquals(listOf(StatusBadge(StatusId.SEN_02, Severity.RECOMMEND, "연료 12%")), r.badges)
        assertEquals(Severity.RECOMMEND, r.rows[0].badge)
    }

    @Test fun evUsesBatteryAndHidesEngineOil() {
        val ev = normal.copy(fuelPercent = null, socPercent = 4, unsupported = setOf(Signal.FUEL))
        val r = eval(ev)
        assertEquals(EngineType.EV, ev.engineType)
        assertEquals("배터리", r.rows[0].label)
        assertEquals(Expression.HUNGRY_3, r.expression)
        assertEquals("배터리 4%", r.badges.single().label)
        assertNull(r.oilChange)
        assertTrue(r.rows[2].unavailable)
    }

    // ── SEN-03 ~ 07 ──

    @Test fun engineOilLowIsStainAndDismissHidesOnlyBadge() {
        val low = normal.copy(engineOil = OilLevel.LOW)
        val r = eval(low)
        assertEquals(Expression.STAIN, r.expression)
        assertEquals(StatusId.SEN_03, r.badges.single().id)
        assertTrue(r.engineOilAlert)

        val dismissed = eval(low, fresh.copy(engineOilDismissed = true))
        assertTrue(dismissed.badges.isEmpty())
        assertEquals(Expression.STAIN, dismissed.expression)  // 표정은 유지
        assertNull(dismissed.rows[2].badge)
    }

    @Test fun engineOilCriticalIsUrgentSick() {
        val r = eval(normal.copy(engineOil = OilLevel.CRITICALLY_LOW))
        assertEquals(Expression.SICK, r.expression)
        assertEquals(Severity.URGENT, r.badges.single().severity)
    }

    @Test fun brakeFluidLowIsUrgentWithShopGuide() {
        val r = eval(normal.copy(brakeFluid = OilLevel.LOW))
        assertEquals(Expression.SICK, r.expression)
        assertEquals(StatusId.SEN_04, r.shopGuide?.id)
        assertEquals("부족", r.rows[3].value)
    }

    @Test fun washerShowsBadgeWithoutExpression() {
        val r = eval(normal.copy(washerLow = true))
        assertEquals(Expression.IDLE, r.expression)
        assertEquals(Severity.RECOMMEND, r.badges.single().severity)
    }

    @Test fun engineWarningIsUrgent() {
        assertEquals(Expression.SICK, eval(normal.copy(engineWarning = true)).expression)
    }

    @Test fun transmissionOilLevels() {
        assertEquals(Expression.STAIN, eval(normal.copy(transmissionOil = OilLevel.LOW)).expression)
        assertEquals(Expression.SICK, eval(normal.copy(transmissionOil = OilLevel.CRITICALLY_LOW)).expression)
    }

    @Test fun unsupportedSignalsShowUnavailableRows() {
        val r = eval(VehicleSnapshot(unsupported = Signal.entries.toSet()))
        assertTrue(r.rows.all { it.unavailable })
        assertTrue(r.badges.isEmpty())
        assertEquals(Expression.IDLE, r.expression)
    }

    // ── 표정 우선순위 ──

    @Test fun expressionPriority() {
        val p = StateEngine::pickExpression
        assertEquals(Expression.SICK, p(Expression.entries))
        assertEquals(Expression.HUNGRY_3, p(listOf(Expression.HUNGRY_3, Expression.STAIN, Expression.TIRED)))
        assertEquals(Expression.STAIN, p(listOf(Expression.HUNGRY_2, Expression.STAIN)))
        assertEquals(Expression.HUNGRY_1, p(listOf(Expression.HUNGRY_1, Expression.TIRED)))
        assertEquals(Expression.TIRED, p(listOf(Expression.TIRED, Expression.IDLE)))
    }

    @Test fun badgesSortedBySeverityAndAllShown() {
        val r = eval(normal.copy(fuelPercent = 28, brakeFluid = OilLevel.LOW, washerLow = true))
        assertEquals(listOf(Severity.URGENT, Severity.RECOMMEND, Severity.OBSERVE), r.badges.map { it.severity })
        assertEquals(Expression.SICK, r.expression)
    }

    // ── MNT-01 / MNT-02 ──

    @Test fun oilChangeObserveAndOverdue() {
        val m = MaintenanceState(oilChangedKm = 0f, oilChangedDay = today)
        assertNull(StateEngine.oilChange(EngineType.GASOLINE, m, 13_000f, today)!!.severity)
        assertEquals(Severity.OBSERVE, StateEngine.oilChange(EngineType.GASOLINE, m, 13_600f, today)!!.severity)
        val over = StateEngine.oilChange(EngineType.GASOLINE, m, 15_000f, today)!!
        assertEquals(Severity.RECOMMEND, over.severity)
        assertEquals("교체 시기 지남", over.display)
    }

    @Test fun oilChangeIntervalHalvesForSevereOrNonGenuineOil() {
        val base = MaintenanceState(oilChangedKm = 0f, oilChangedDay = today)
        assertEquals(15_000, StateEngine.oilChange(EngineType.GASOLINE, base, 0f, today)!!.intervalKm)
        assertEquals(10_000, StateEngine.oilChange(EngineType.HYBRID, base, 0f, today)!!.intervalKm)
        assertEquals(7_500, StateEngine.oilChange(EngineType.GASOLINE, base.copy(severe = true), 0f, today)!!.intervalKm)
        val hev = StateEngine.oilChange(EngineType.HYBRID, base.copy(recommendedOil = false), 0f, today)!!
        assertEquals(5_000, hev.intervalKm)
        assertEquals(182, hev.intervalDays)
    }

    @Test fun oilChangeByElapsedMonths() {
        val m = MaintenanceState(oilChangedKm = 0f, oilChangedDay = today - 366)
        assertEquals(Severity.RECOMMEND, StateEngine.oilChange(EngineType.GASOLINE, m, 100f, today)!!.severity)
    }

    @Test fun checkupDueEvery10000Km() {
        val m = MaintenanceState()
        assertFalse(StateEngine.checkup(m, 9_840f, today, today).due)
        val due = StateEngine.checkup(m, 10_020f, today, today)
        assertTrue(due.due)
        assertEquals(1, due.round)
        assertEquals("10,000 km 도래", due.display)
        val after = StateEngine.checkup(m.copy(checkupRoundsDone = 1, checkupDoneDay = today), 10_020f, today, today)
        assertFalse(after.due)
        assertEquals(2, after.round)
        assertEquals("9,980 km 남음", after.display)
    }

    @Test fun checkupDueAfter12Months() {
        assertTrue(StateEngine.checkup(MaintenanceState(), 500f, today, today - 365).due)
    }

    @Test fun checkupDueGivesTiredAndOdometerRowBadge() {
        val r = eval(normal.copy(odometerKm = 10_020f), fresh.copy(oilChangedKm = 10_000f))
        assertEquals(Expression.TIRED, r.expression)
        assertEquals("점검 도래", r.rows[1].badgeLabel)
    }
}
