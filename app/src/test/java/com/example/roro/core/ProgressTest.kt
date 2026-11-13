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

    @Test fun questListShowsAchievedFirstNewestOnTop() {
        // Figma 6:2085: 첫 드라이브(11.08) → 성실한 카곰 집사(11.07) → 진행 중. Figma는 5개(달성률 40%)이고 QST-06이 더해져 6개다
        val visits = listOf(Visit("LM-GN-01", 1), Visit("LM-GN-02", 2), Visit("LM-GN-01", 3), Visit("LM-GN-02", 4))
        val all = Progress.all(ProgressContext(64, visits, 1), setOf("QST-01", "QST-05"))
        val doneAt = mapOf("QST-05" to 1_000L, "QST-01" to 2_000L)
        assertEquals(listOf("QST-01", "QST-05", "QST-02", "QST-03", "QST-04", "QST-06"), Progress.listed(all, doneAt, QuestFilter.ALL).map { it.quest.id })
        assertEquals(listOf("QST-01", "QST-05"), Progress.listed(all, doneAt, QuestFilter.DONE).map { it.quest.id })
        assertEquals(listOf("QST-02", "QST-03", "QST-04", "QST-06"), Progress.listed(all, doneAt, QuestFilter.TODO).map { it.quest.id })
        assertEquals(33, Progress.achievedPercent(all))
        assertEquals(listOf("64 / 100 km", "2 / 3곳", "4 / 10회", "64 / 300 km"), Progress.listed(all, doneAt, QuestFilter.TODO).map { it.countText })
        // 달성 시각을 모르는 예전 기록은 시각이 있는 것 아래
        assertEquals(listOf("QST-05", "QST-01"), Progress.listed(all, mapOf("QST-05" to 1_000L), QuestFilter.DONE).map { it.quest.id })
    }

    @Test fun drivenDistanceCarriesOverToNewVehicle() {
        assertEquals(42, Progress.drivenKm(1_282f, 1_240f, 0f))
        assertEquals(0, Progress.drivenKm(null, 1_240f, 0f))
        // 새 차: 이전 차에서 1,240 km, 새 차 기준값이 잡히기 전과 뒤
        assertEquals(1_240, Progress.drivenKm(35f, null, 1_240f))
        assertEquals(1_250, Progress.drivenKm(45f, 35f, 1_240f))
        // 주행거리 신호가 기준값보다 작게 와도 줄지 않는다
        assertEquals(1_240, Progress.drivenKm(10f, 35f, 1_240f))
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

    @Test fun defaultItemsAreOwnedButNotCountedAsEarned() {
        // 기본 칭호·기본 배경 3종은 처음부터 보유이고, 획득 아이템 수에는 세지 않는다
        assertEquals(setOf(Items.DEFAULT_TITLE, Items.WEATHER_BACKGROUND, "BG-01", "BG-02"), Items.defaults)
        assertEquals(Items.WEATHER_BACKGROUND, Items.DEFAULT_BACKGROUND)
        assertEquals(setOf("ITM-B-01"), Items.earned(Items.defaults + "ITM-B-01"))
        assertEquals(Category.BACKGROUND, Items.byId(Items.DEFAULT_BACKGROUND)?.category)
        // 칭호·배경은 벗을 수 없다
        assertEquals(listOf(Category.TITLE, Category.BACKGROUND), Category.entries.filter { it.alwaysEquipped })
    }

    @Test fun backgroundQuestRewardsBackgroundAt300Km() {
        val q = Quests.byId("QST-06")!!
        assertFalse(Progress.questProgress(q, ProgressContext(299, emptyList(), 0), emptySet()).achieved)
        val done = setOf("QST-01", "QST-02")
        assertEquals(listOf("QST-06"), Progress.newlyAchieved(ProgressContext(300, emptyList(), 0), done).map { it.id })
        val reward = Items.byId(q.rewardItemId)!!
        assertEquals(Category.BACKGROUND, reward.category)
        // 보상 배경은 기본 제공이 아니다 → 달성 전에는 잠겨 있다
        assertFalse(reward.id in Items.defaults)
    }

    @Test fun everyQuestRewardIsAnItemThatPointsBackToItsQuest() {
        Quests.all.forEach { q -> assertEquals(q.id, Items.byId(q.rewardItemId)?.sourceId) }
    }
}
