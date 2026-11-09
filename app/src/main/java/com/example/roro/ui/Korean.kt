package com.example.roro.ui

/** 받침 유무로 조사를 고른다: "포근이".josa("이", "가") = "포근이가", "카곰".josa("이", "가") = "카곰이" */
fun String.josa(withBatchim: String, withoutBatchim: String): String {
    val last = lastOrNull() ?: return this
    val batchim = when {
        last in '가'..'힣' -> (last - '가') % 28 != 0
        last.isDigit() -> numberBatchim(this)
        else -> false
    }
    return this + if (batchim) withBatchim else withoutBatchim
}

/** 숫자 읽기 끝소리 받침 (일·삼·육·칠·팔·십·백·천·영 = 받침 있음) */
private fun numberBatchim(s: String): Boolean {
    val digits = s.takeLastWhile { it.isDigit() || it == ',' }.filter { it.isDigit() }
    val n = digits.toLongOrNull() ?: return false
    if (n == 0L) return true
    val d = (n % 10).toInt()
    return if (d != 0) d in setOf(1, 3, 6, 7, 8) else true
}
