package com.example.roro.data

import android.content.Context
import com.example.roro.core.KoreaMap

/** assets/regions/korea_map.txt를 한 번만 읽어 둔다. 메인 스레드에서 부르지 말 것 (≈1MB 텍스트) */
object KoreaMapAsset {
    const val PATH = "regions/korea_map.txt"
    const val DISTRICTS_PATH = "regions/gangneung_districts.txt"

    @Volatile private var cached: KoreaMap? = null

    fun load(context: Context): KoreaMap = cached ?: synchronized(this) {
        cached ?: context.assets.open(PATH).bufferedReader().useLines { map ->
            context.assets.open(DISTRICTS_PATH).bufferedReader().useLines { districts ->
                KoreaMap.parse(map + districts)
            }
        }.also { cached = it }
    }
}
