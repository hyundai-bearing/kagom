package com.example.roro.core

/**
 * 도전과제 진행 · 보상 지급 · 팝업 큐 (spec 2장·5장, 가이드 5.2).
 * 순수 로직만 둔다. 저장은 data/KagomStore.
 */

/** 랜드마크 방문 1회 (같은 곳은 하루 1회만 센다) */
data class Visit(val landmarkId: String, val day: Long)

/** 진행 계산에 필요한 값 */
data class ProgressContext(
    /** 카곰을 만든 뒤 달린 거리 (km) */
    val drivenKm: Int,
    val visits: List<Visit>,
    val checkupsDone: Int,
) {
    val distinctPlaces get() = visits.map { it.landmarkId }.distinct().size
    val totalVisits get() = visits.size
}

data class QuestProgress(val quest: Quest, val current: Int, val achieved: Boolean) {
    val ratio get() = (current.toFloat() / quest.target).coerceIn(0f, 1f)
    /** "42 / 100 km" · "1 / 3" */
    val countText get() = buildString {
        append("${current.coerceAtMost(quest.target)} / ${quest.target}")
        if (quest.unit.isNotEmpty()) append(" ${quest.unit}")
    }
}

/** 팝업 큐 항목. gear → P 전환 때 순서대로 재생한다 */
sealed interface PendingPopup {
    val key: String

    /** POP-02 도전과제 보상 (언박싱 → 공개) */
    data class Reward(
        val questId: String,
        val itemId: String,
        val exp: Int,
        val expBefore: Int,
        val expAfter: Int,
        /** 언박싱을 이미 봤으면 공개 단계부터 */
        val opened: Boolean = false,
    ) : PendingPopup { override val key get() = "reward:$questId" }

    /** POP-03 랜드마크 해금 */
    data class Landmark(
        val landmarkId: String,
        val itemId: String,
        val exp: Int,
        val expBefore: Int,
        val expAfter: Int,
        val visitedPlaces: Int,
        val day: Long,
    ) : PendingPopup { override val key get() = "landmark:$landmarkId" }

    /** POP-04 티어 승급 */
    data class TierUp(
        val tier: Int,
        val points: Int,
        val totalExp: Int,
        val ownedItems: Int,
        val visitedPlaces: Int,
    ) : PendingPopup { override val key get() = "tier:$tier" }
}

object Progress {

    fun questProgress(q: Quest, ctx: ProgressContext, completed: Set<String>): QuestProgress {
        val current = when (q.kind) {
            QuestKind.DISTANCE -> ctx.drivenKm
            QuestKind.LANDMARK_PLACES -> ctx.distinctPlaces
            QuestKind.LANDMARK_VISITS -> ctx.totalVisits
            QuestKind.CHECKUP -> ctx.checkupsDone
        }
        // 보상을 받은 과제는 목표값으로 보인다 (주행거리 신호가 다시 0부터 와도 "0 / 100"이 되지 않게)
        val done = q.id in completed
        return QuestProgress(q, if (done) maxOf(current, q.target) else current, done || current >= q.target)
    }

    fun all(ctx: ProgressContext, completed: Set<String>) = Quests.all.map { questProgress(it, ctx, completed) }

    /** 이번에 새로 달성한 도전과제 (아직 보상을 받지 않은 것) */
    fun newlyAchieved(ctx: ProgressContext, completed: Set<String>): List<Quest> =
        Quests.all.filter { it.id !in completed && questProgress(it, ctx, completed).achieved }

    /**
     * 큐 재생 순서: 보상(POP-02)·해금(POP-03)은 달성 순서대로, 티어 승급(POP-04)은 맨 뒤.
     */
    fun ordered(queue: List<PendingPopup>): List<PendingPopup> =
        queue.filter { it !is PendingPopup.TierUp } + queue.filterIsInstance<PendingPopup.TierUp>().sortedBy { it.tier }

    /**
     * 달성했지만 보상 상자(POP-02 언박싱)를 아직 열지 않은 도전과제. 도전과제 카드는 이 동안 보상을 ?로 둔다
     * (주행 중 달성 → 정차해서 상자를 열 때까지)
     */
    fun boxedQuests(queue: List<PendingPopup>): Set<String> =
        queue.filterIsInstance<PendingPopup.Reward>().filterNot { it.opened }.map { it.questId }.toSet()

    /** 같은 key는 한 번만 */
    fun enqueue(queue: List<PendingPopup>, item: PendingPopup): List<PendingPopup> =
        if (queue.any { it.key == item.key }) queue else queue + item

    /** 랜드마크 방문 기록. 같은 곳을 같은 날 다시 들어가면 세지 않는다 */
    fun recordVisit(visits: List<Visit>, landmarkId: String, day: Long): List<Visit> =
        if (visits.any { it.landmarkId == landmarkId && it.day == day }) visits else visits + Visit(landmarkId, day)

    /** 위경도 거리 (m, haversine) */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    /** 지금 위치가 들어가 있는 랜드마크 */
    fun landmarkAt(lat: Double, lon: Double): Landmark? =
        Landmarks.all.firstOrNull { distanceM(lat, lon, it.lat, it.lon) <= Landmarks.RADIUS_M }

    /** 카곰 이름 규칙 (kagomNameInput): 2~10자 한글/영문/숫자 */
    fun isValidName(name: String) = Regex("^[가-힣a-zA-Z0-9]{2,10}$").matches(name)
}
