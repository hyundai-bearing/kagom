package com.example.roro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    @Test fun skinMappingCoversAllEightCombinations() {
        val combos = listOf("AAA" to "P01", "ABA" to "P02", "AAB" to "P03", "BBA" to "P04",
            "BAA" to "P05", "ABB" to "P06", "BBB" to "P07", "BAB" to "P08")
        combos.forEach { (answers, code) ->
            assertEquals(code, Skins.forAnswers(answers.map { Answer.valueOf(it.toString()) })?.code)
        }
        assertNull(Skins.forAnswers(listOf(Answer.A)))
    }

    @Test fun nameRule() {
        assertTrue(Progress.isValidName("포근이"))
        assertTrue(Progress.isValidName("Kagom10"))
        assertFalse(Progress.isValidName("포"))
        assertFalse(Progress.isValidName("열한글자이름입니다요"+"요"))
        assertFalse(Progress.isValidName("포근 이"))
        assertFalse(Progress.isValidName("ㅍㅗ"))
    }

    @Test fun distanceQuestsAchieveAtTarget() {
        val ctx = ProgressContext(drivenKm = 42, visits = emptyList(), checkupsDone = 0)
        val achieved = Progress.newlyAchieved(ctx, emptySet()).map { it.id }
        assertEquals(listOf("QST-01"), achieved)
        assertEquals("42 / 100 km", Progress.questProgress(Quests.byId("QST-02")!!, ctx, emptySet()).countText)
        assertTrue(Progress.newlyAchieved(ctx, setOf("QST-01")).isEmpty())
    }

    @Test fun completedQuestShowsTargetEvenIfSignalResets() {
        val p = Progress.questProgress(Quests.byId("QST-02")!!, ProgressContext(0, emptyList(), 0), setOf("QST-02"))
        assertTrue(p.achieved)
        assertEquals("100 / 100 km", p.countText)
    }

    @Test fun landmarkQuestsCountPlacesAndVisitsSeparately() {
        val visits = listOf(Visit("LM-GN-01", 1), Visit("LM-GN-01", 2), Visit("LM-GN-02", 3))
        val ctx = ProgressContext(0, visits, 0)
        assertEquals(2, Progress.questProgress(Quests.byId("QST-03")!!, ctx, emptySet()).current)
        assertEquals(3, Progress.questProgress(Quests.byId("QST-04")!!, ctx, emptySet()).current)
    }

    @Test fun sameLandmarkSameDayCountsOnce() {
        val v1 = Progress.recordVisit(emptyList(), "LM-GN-01", 10)
        val v2 = Progress.recordVisit(v1, "LM-GN-01", 10)
        val v3 = Progress.recordVisit(v2, "LM-GN-01", 11)
        assertEquals(1, v2.size)
        assertEquals(2, v3.size)
    }

    @Test fun popupQueuePlaysTierUpLast() {
        val tier = PendingPopup.TierUp(2, 1000, 420, 3, 2)
        val reward = PendingPopup.Reward("QST-01", "ITM-B-01", 100, 120, 220)
        val landmark = PendingPopup.Landmark("LM-GN-01", "ITM-H-01", 150, 220, 370, 2, 0)
        val ordered = Progress.ordered(listOf(tier, reward, landmark))
        assertEquals(listOf(reward, landmark, tier), ordered)
    }

    @Test fun enqueueIgnoresDuplicates() {
        val r = PendingPopup.Reward("QST-01", "ITM-B-01", 100, 0, 100)
        assertEquals(1, Progress.enqueue(Progress.enqueue(emptyList(), r), r).size)
    }

    @Test fun tiers() {
        assertEquals(1, Tiers.tierFor(0))
        assertEquals(1, Tiers.tierFor(399))
        assertEquals(2, Tiers.tierFor(400))
        assertEquals(400, Tiers.nextThreshold(120))
        assertEquals(4, Tiers.tierFor(1350))
        assertNull(Tiers.nextThreshold(1350))
    }

    @Test fun geofence() {
        val jd = Landmarks.byId("LM-GN-01")!!
        assertEquals(jd, Progress.landmarkAt(jd.lat + 0.001, jd.lon))   // 약 110 m
        assertNull(Progress.landmarkAt(37.5665, 126.9780))              // 서울
    }
}
