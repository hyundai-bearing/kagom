package com.example.roro.core

/**
 * 마스터 데이터 (spec 3장·5장, 가이드 5.4). 문구는 Figma 화면 문구를 우선한다.
 * 스킨 색(HEX)은 디자인 토큰 쪽(ui/theme/Tokens.kt KagomSkinPalette)에 있다.
 */

// ───────────── 스킨 / 성격 (SCR-02) ─────────────

data class Skin(
    val code: String,            // P01..P08 = Figma skin=P0n
    val answers: String,         // "AAB"
    val personality: String,     // 부여 성격 명칭
    val intro: String,           // 한 줄 소개
    val skinName: String,        // 매핑 스킨명
    val material: String,        // 재질 라벨
)

object Skins {
    val all = listOf(
        Skin("P01", "AAA", "포근한 감성 크루저", "여유롭고 따뜻한 안전운전 평화주의자", "Classic Beige", "무광"),
        Skin("P02", "ABA", "산뜻한 바람의 탐험가", "어디든 가볍게 떠나는 청량한 드라이버", "Matte Mint", "무광"),
        Skin("P03", "AAB", "낭만 가득 몽상가", "노을 진 도로를 사랑하는 감성파", "Pastel Lavender", "무광"),
        Skin("P04", "BBA", "시크한 나이트라이더", "도심의 밤을 정교하게 달리는 카리스마", "Obsidian Black", "약간 광택"),
        Skin("P05", "BAA", "깔끔한 완벽주의 파일럿", "반듯하고 정돈된 주행을 즐기는 스마트파", "Polar White", "무광"),
        Skin("P06", "ABB", "든든한 오프로드 캠퍼", "거친 길도 믿음직하게 달리는 모험가", "Chocolate Brown", "무광"),
        Skin("P07", "BBB", "통통 튀는 에너지 부스터", "차 안을 축제로 만드는 하이텐션 메이커", "Frosty Jelly", "광택"),
        Skin("P08", "BAB", "빛나는 도심의 주인공", "우아하고 세련된 취향을 가진 트렌드세터", "Metallic Rose Gold", "금속 광택"),
    )

    fun byCode(code: String?): Skin = all.firstOrNull { it.code == code } ?: all[2]

    /** 응답 조합(2×2×2) → 스킨. 3개가 아니면 null */
    fun forAnswers(answers: List<Answer>): Skin? {
        if (answers.size != 3) return null
        val key = answers.joinToString("") { it.name }
        return all.firstOrNull { it.answers == key }
    }
}

// ───────────── 성향 테스트 (SCR-01) ─────────────

data class Choice(val key: Answer, val text: String, val tag: String)
data class Question(val text: String, val a: Choice, val b: Choice)

object Questions {
    val all = listOf(
        Question(
            "첫 차와 함께 떠나는 첫 드라이브, 어디로 향할까요?",
            Choice(Answer.A, "탁 트인 해안도로와 자연 속 힐링 코스", "Nature / Relax"),
            Choice(Answer.B, "반짝이는 야경과 세련된 도심 핫플레이스", "City / Trendy"),
        ),
        Question(
            "선호하는 드라이빙 텐션과 분위기는?",
            Choice(Answer.A, "잔잔한 음악과 여유롭고 포근하게", "Warm / Calm"),
            Choice(Answer.B, "리드미컬한 비트와 경쾌하고 쿨하게", "Cool / Active"),
        ),
        Question(
            "내 차 안에 두고 싶은 나만의 감성 포인트는?",
            Choice(Answer.A, "질리지 않는 클래식하고 담백한 미니멀 무드", "Minimal / Classic"),
            Choice(Answer.B, "시선을 사로잡는 톡톡 튀고 특별한 개성", "Unique / Vivid"),
        ),
    )
}

// ───────────── 치장 아이템 & 칭호 (SCR-06) ─────────────

data class Item(
    val id: String,
    val name: String,
    val category: Category,
    /** 썸네일 자리 글자 (실 에셋이 없을 때의 플레이스홀더, 가이드 8.3) */
    val mark: String,
    val source: Source,
    /** 획득처 ID: LM-GN-0n 또는 QST-0n */
    val sourceId: String? = null,
    /** 잠긴 셀 캡션 */
    val condition: String = "",
    /** 획득 안내 패널 본문 */
    val guide: String = "",
    /** 보상 공개 때 카곰 말풍선 */
    val line: String = "",
)

object Items {
    const val DEFAULT_TITLE = "TTL-00"

    val all = listOf(
        Item("ITM-H-01", "해돋이 머리띠", Category.HEAD, "H", Source.LANDMARK, "LM-GN-01",
            "정동진 방문 시 획득", "강릉 정동진에 방문하면 안개가 걷히면서 바로 받을 수 있어요.",
            "해돋이 보러 온 거예요? 머리띠 쓰고 사진 찍어요!"),
        Item("ITM-H-02", "커피콩 모자", Category.HEAD, "C", Source.LANDMARK, "LM-GN-02",
            "커피거리 방문 시 획득", "강릉 커피거리에 방문하면 안개가 걷히면서 바로 받을 수 있어요.",
            "커피 향이 솔솔 나요! 모자 쓰고 한 잔 할까요?"),
        Item("ITM-T-01", "빨간 고무 앞치마", Category.TOP, "A", Source.LANDMARK, "LM-GN-03",
            "주문진항 방문 시 획득", "강릉 주문진항에 방문하면 안개가 걷히면서 바로 받을 수 있어요.",
            "바다 냄새가 나요! 앞치마 입고 항구 구경 가요!"),
        Item("ITM-T-02", "초보운전 안전 조끼", Category.TOP, "V", Source.QUEST, "QST-02",
            "누적 100 km 주행 시 획득", "누적 100 km를 달리면 도전과제 보상으로 받을 수 있어요.",
            "안전 조끼 입으면 든든해요. 오늘도 안전 운전해요!"),
        Item("ITM-B-01", "데님 멜빵 반바지", Category.BOTTOM, "D", Source.QUEST, "QST-01",
            "10 km 주행 시 획득", "10 km를 달리면 도전과제 보상으로 받을 수 있어요.",
            "이거 입으면 더 귀여워질 것 같아요! 바로 입혀 줄래요?"),
        Item("ITM-B-02", "강릉 서퍼 보드숏", Category.BOTTOM, "S", Source.QUEST, "QST-03",
            "랜드마크 3곳 방문 시 획득", "강릉 랜드마크 3곳을 모두 방문하면 받을 수 있어요.",
            "파도 타러 가요! 보드숏 입혀 줄래요?"),
        Item(DEFAULT_TITLE, "설레는 첫 차 오너", Category.TITLE, "T", Source.DEFAULT, null,
            "기본 제공", "카곰을 만들면 기본으로 받는 칭호예요.", ""),
        Item("TTL-01", "동해안 정복자", Category.TITLE, "T", Source.QUEST, "QST-04",
            "랜드마크 10회 방문", "랜드마크를 10회 방문하면 받을 수 있어요.",
            "동해안은 이제 우리 무대예요!"),
        Item("TTL-02", "성실한 카곰 집사", Category.TITLE, "T", Source.MAINTENANCE, "QST-05",
            "정기 점검 1회 완료 시 획득", "정비 시트에서 정기 점검을 한 번 완료하면 받을 수 있어요.",
            "꼼꼼하게 챙겨 줘서 고마워요!"),
    )

    fun byId(id: String?): Item? = all.firstOrNull { it.id == id }
    fun of(category: Category) = all.filter { it.category == category }
}

// ───────────── 도전과제 (SCR-04) ─────────────

enum class QuestKind { DISTANCE, LANDMARK_PLACES, LANDMARK_VISITS, CHECKUP }

data class Quest(
    val id: String,
    val title: String,
    /** true = 누적형 (progressBar + 카운트 표시, showGauge) */
    val cumulative: Boolean,
    val kind: QuestKind,
    val target: Int,
    val unit: String,
    val rewardItemId: String,
    val exp: Int,
    /** 주행 중 토스트 앞부분 (예: "10 km 달성!") */
    val toast: String,
    /** 보상 모달 제목 앞말 (주행 보상 / 도전과제 보상 / 정비 보상) */
    val rewardKind: String,
    /** 보상 공개 부제 뒷말 */
    val flavor: String,
)

object Quests {
    val all = listOf(
        Quest("QST-01", "10 km 주행", false, QuestKind.DISTANCE, 10, "km", "ITM-B-01", 100,
            "10 km 달성!", "주행", "첫 드라이브를 무사히 마쳤어요"),
        Quest("QST-02", "누적 100 km 주행", true, QuestKind.DISTANCE, 100, "km", "ITM-T-02", 150,
            "누적 100 km 달성!", "주행", "벌써 100 km를 함께 달렸어요"),
        Quest("QST-03", "랜드마크 3곳 방문", true, QuestKind.LANDMARK_PLACES, 3, "", "ITM-B-02", 150,
            "랜드마크 3곳 달성!", "도전과제", "강릉 랜드마크를 모두 둘러봤어요"),
        Quest("QST-04", "랜드마크 10회 방문", true, QuestKind.LANDMARK_VISITS, 10, "", "TTL-01", 300,
            "랜드마크 10회 달성!", "도전과제", "동해안 구석구석을 누볐어요"),
        Quest("QST-05", "정기 점검 1회 완료", false, QuestKind.CHECKUP, 1, "", "TTL-02", 200,
            "정기 점검 완료!", "정비", "차를 꼼꼼하게 챙겼어요"),
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id }
    fun rewarding(itemId: String) = all.firstOrNull { it.rewardItemId == itemId }
}

// ───────────── 랜드마크 (SCR-05) ─────────────

enum class LandmarkGlyph { SUNRISE, COFFEE, BOAT }

data class Landmark(
    val id: String,
    val name: String,
    val rewardItemId: String,
    val exp: Int,
    val lat: Double,
    val lon: Double,
    val glyph: LandmarkGlyph,
    /** 지도에서 이 랜드마크가 속한 시·군·구 (KoreaMap unit id = "시·도/시·군·구") */
    val unitId: String,
    /** 속한 읍·면·동 (지도 L3 구획 이름, 가이드 v1.2). 구획 id = "<unitId>/<areaName>" */
    val areaName: String,
) {
    val areaId: String get() = "$unitId/$areaName"
}

object Landmarks {
    /** 방문 판정 반경 (m) */
    const val RADIUS_M = 400.0

    /** 강릉시 (지도 unit id) */
    const val GANGNEUNG = "강원/강릉시"

    /** 위도 순(북→남) = 지도 위→아래 순 */
    val all = listOf(
        Landmark("LM-GN-03", "주문진항", "ITM-T-01", 150, 37.8930, 128.8312, LandmarkGlyph.BOAT, Landmarks.GANGNEUNG, "주문진읍"),
        Landmark("LM-GN-02", "커피거리", "ITM-H-02", 150, 37.7722, 128.9476, LandmarkGlyph.COFFEE, Landmarks.GANGNEUNG, "송정동"),
        Landmark("LM-GN-01", "정동진", "ITM-H-01", 150, 37.6910, 129.0343, LandmarkGlyph.SUNRISE, Landmarks.GANGNEUNG, "강동면"),
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id }
    fun forItem(itemId: String) = all.firstOrNull { it.rewardItemId == itemId }
    fun inUnit(unitId: String) = all.filter { it.unitId == unitId }
}

// ───────────── 티어 (POP-04) ─────────────

/**
 * 티어 기준 EXP. 스펙에 수치가 없어 디자인(티어 1 → 2 = 400 EXP)에 맞춘 가정값.
 * 전체 보상 EXP 합계(도전과제 900 + 랜드마크 450 = 1,350)로 티어 4까지 닿도록 잡았다.
 */
object Tiers {
    val thresholds = listOf(0, 400, 900, 1350)
    /** 승급 1회당 블루멤버스 포인트 (디자인 예시 +1,000 P) */
    const val POINTS_PER_TIER = 1_000

    fun tierFor(exp: Int): Int = thresholds.indexOfLast { exp >= it } + 1

    /** 다음 티어 기준 EXP. 최고 티어면 null */
    fun nextThreshold(exp: Int): Int? = thresholds.getOrNull(tierFor(exp))

    fun label(tier: Int) = "티어 $tier"
}

// ───────────── 정기 점검 체크리스트 (POP-05) ─────────────

object Checkup {
    const val INTERVAL_KM = 10_000
    const val INTERVAL_DAYS = 365

    /** 회차마다 같은 3개 항목 (Figma POP-05) */
    val items = listOf("엔진룸 누유", "타이어 마모", "램프와 배터리")

    fun roundLabel(round: Int) = "${round}만 km 점검"
}
