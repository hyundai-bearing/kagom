package com.example.roro.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class OilCalendarTest {
    @Test fun simulatedDateMovesRemainingDaysAndExpiry() {
        val changed = 20_000L
        assertEquals("365일 남음", oilCalendarDisplay(changed, changed))
        assertEquals("65일 남음", oilCalendarDisplay(changed, changed + 300))
        assertEquals("1일 남음", oilCalendarDisplay(changed, changed + 364))
        assertEquals("교체 시기 지남", oilCalendarDisplay(changed, changed + 365))
        assertEquals("교체 시기 지남", oilCalendarDisplay(changed, changed + 400))
        assertEquals(0f, oilCalendarRatio(changed, changed + 400), 0f)
    }

    @Test fun replacementAndDateResetStayWithinOneYear() {
        val simulatedToday = 20_300L
        assertEquals("365일 남음", oilCalendarDisplay(simulatedToday, simulatedToday))
        assertEquals("335일 남음", oilCalendarDisplay(simulatedToday, simulatedToday + 30))
        assertEquals("365일 남음", oilCalendarDisplay(simulatedToday, simulatedToday - 300))
        assertEquals(1f, oilCalendarRatio(simulatedToday, simulatedToday - 300), 0f)
        assertEquals("기록 후 안내", oilCalendarDisplay(null, simulatedToday))
        assertEquals(0f, oilCalendarRatio(null, simulatedToday), 0f)
    }
}
