package com.example.roro.core

/**
 * Figma 예시 데이터 (가이드 v1.2 9장: "SCR-06 grid=overflow 행의 아이템 03~12도 예시 데이터").
 * 시연 패널의 '예시 데이터' 스위치를 켤 때만 보인다 (페이지 넘김 등 디자인 상태를 보여 주기 위한 것).
 * 렌더가 없어 입어 볼 수는 없고, 썸네일 자리에는 Mark 글자(03 …)가 나온다.
 */
object Examples {
    const val PREFIX = "EX-"

    private fun head(n: Int, landmark: Boolean) = Item(
        id = PREFIX + "H-%02d".format(n),
        name = "예시 머리 아이템 %02d".format(n),
        category = Category.HEAD,
        mark = "%02d".format(n),
        source = if (landmark) Source.LANDMARK else Source.QUEST,
        sourceId = null,
        condition = if (landmark) "예시 · 랜드마크 방문 시 획득" else "예시 · 도전과제 보상",
        guide = "Figma 예시 데이터예요. 실제 획득처는 아직 정해지지 않았어요.",
    )

    /**
     * Figma 140:4103 순서·출처 그대로 (03 랜드마크, 04 도전과제 …).
     * 13·14는 Figma 첫 페이지 밖이라 보이지 않지만, 실제 2개 + 예시 12개 = 14개여야 Full 2페이지(140:4103)·Split 5페이지(140:4330)가 맞는다
     */
    val items: List<Item> = listOf(
        3 to true, 4 to false, 5 to false, 6 to true, 7 to false, 8 to true, 9 to false, 10 to true, 11 to false, 12 to true,
        13 to false, 14 to true,
    ).map { (n, landmark) -> head(n, landmark) }

    /** 보유로 보이는 것 (Figma: 04 · 06 · 09 · 12) */
    val owned: Set<String> = setOf("EX-H-04", "EX-H-06", "EX-H-09", "EX-H-12")

    fun of(category: Category) = items.filter { it.category == category }
    fun isExample(id: String?) = id?.startsWith(PREFIX) == true
    fun item(id: String?) = items.firstOrNull { it.id == id }
}
