package com.example.roro.data

/** 빈 슬롯은 순서대로만 채우고, 기존 슬롯은 덮어쓸 수 있다. */
internal fun saveOutfit(outfits: List<Equipped>, index: Int, outfit: Equipped): List<Equipped> {
    if (outfits.size > 3 || index !in 0..2 || index > outfits.size) return outfits
    return if (index == outfits.size) outfits + outfit else outfits.toMutableList().apply { this[index] = outfit }
}
