package com.example.roro.ui.components

/** 옷장 페이지가 많아도 현재 위치와 양 끝을 좁은 화면에서 볼 수 있게 한다. null은 생략 표시다. */
internal fun paginationEntries(current: Int, count: Int): List<Int?> {
    require(count > 0 && current in 0 until count)
    if (count <= 5) return (0 until count).toList()

    val pages = sortedSetOf(0, current, count - 1)
    if (current == 0) pages.add(1)
    if (current == count - 1) pages.add(count - 2)
    val result = mutableListOf<Int?>()
    for (page in pages) {
        val previous = result.lastOrNull()
        if (previous != null) {
            when (page - previous) {
                2 -> result.add(previous + 1)
                in 3..Int.MAX_VALUE -> result.add(null)
            }
        }
        result.add(page)
    }
    return result
}
