package com.example.roro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherTest {

    @Test fun symbolCodesMapToFiveKinds() {
        assertEquals(Weather.CLEAR, Weather.fromSymbol("clearsky_day"))
        assertEquals(Weather.CLEAR, Weather.fromSymbol("fair_night"))
        assertEquals(Weather.CLEAR, Weather.fromSymbol("partlycloudy_polartwilight"))
        assertEquals(Weather.CLOUDY, Weather.fromSymbol("cloudy"))
        assertEquals(Weather.CLOUDY, Weather.fromSymbol("fog"))
        assertEquals(Weather.RAIN, Weather.fromSymbol("lightrain"))
        assertEquals(Weather.RAIN, Weather.fromSymbol("heavyrainshowers_day"))
        assertEquals(Weather.SNOW, Weather.fromSymbol("lightsnowshowers_night"))
        assertEquals(Weather.SNOW, Weather.fromSymbol("sleet"))
    }

    @Test fun thunderWinsOverRainAndSnow() {
        assertEquals(Weather.THUNDER, Weather.fromSymbol("lightrainshowersandthunder_day"))
        assertEquals(Weather.THUNDER, Weather.fromSymbol("heavysnowandthunder"))
    }

    @Test fun unknownSymbolKeepsDefaultBackground() {
        assertNull(Weather.fromSymbol(null))
        assertNull(Weather.fromSymbol(""))
        assertNull(Weather.fromSymbol("volcanicash_day"))
    }

    @Test fun coordinatesAreSentAtOneDecimal() {
        assertEquals(37.7, Weather.coarse(37.6894), 0.0)
        assertEquals(129.0, Weather.coarse(129.0342), 0.0)
        assertEquals(-0.1, Weather.coarse(-0.06), 0.0)
    }
}
