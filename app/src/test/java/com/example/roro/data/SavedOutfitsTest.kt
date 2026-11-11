package com.example.roro.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedOutfitsTest {
    private val first = Equipped(head = "ITM-H-01")
    private val second = Equipped(top = "ITM-T-01")
    private val third = Equipped(bottom = "ITM-B-01")

    @Test
    fun emptySlotsMustBeFilledInOrderAndStopAtThree() {
        assertEquals(emptyList<Equipped>(), saveOutfit(emptyList(), 1, first))
        val one = saveOutfit(emptyList(), 0, first)
        val two = saveOutfit(one, 1, second)
        val three = saveOutfit(two, 2, third)
        assertEquals(listOf(first, second, third), three)
        assertEquals(three, saveOutfit(three, 3, first))
        assertEquals(three, saveOutfit(three, -1, first))
    }

    @Test
    fun savingSelectedSlotReplacesOnlyThatOutfit() {
        val outfits = listOf(first, second, third)
        assertEquals(listOf(first, third, third), saveOutfit(outfits, 1, third))
    }
}
